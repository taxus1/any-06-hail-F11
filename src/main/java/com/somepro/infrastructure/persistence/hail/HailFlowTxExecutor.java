package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.model.EffectReport;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.hail.converter.AmmoRecordPoConverter;
import com.somepro.infrastructure.persistence.hail.converter.AmmoStockPoConverter;
import com.somepro.infrastructure.persistence.hail.converter.EffectReportPoConverter;
import com.somepro.infrastructure.persistence.hail.converter.FireOrderPoConverter;
import com.somepro.infrastructure.persistence.hail.po.AirspaceApplyPO;
import com.somepro.infrastructure.persistence.hail.po.AmmoRecordPO;
import com.somepro.infrastructure.persistence.hail.po.AmmoStockPO;
import com.somepro.infrastructure.persistence.hail.po.EffectReportPO;
import com.somepro.infrastructure.persistence.hail.po.FireOrderPO;
import com.somepro.infrastructure.persistence.hail.po.LauncherPO;
import com.somepro.infrastructure.persistence.hail.po.OperationSitePO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 作业主线跨表事务执行器（基础设施层）。
 *
 * 独立成一个 bean 而不是写在某个仓储适配器里，有两个原因：
 * 1. @Transactional 依赖 Spring 代理，同类内部自调用会绕过代理、事务静默失效；
 *    由另一个 bean 调用本类的 public 方法，代理必然生效。
 * 2. 一次业务动作跨多张表（库存 / 流水 / 指令 / 装备 / 效果），不属于任一单表仓储。
 *
 * 本类只做「同事务多写 + 并发冲突检测」，不做业务校验（在应用层 / 领域对象上完成）。
 * 调用方负责把它包在 blocking(...) 里跑在 boundedElastic 线程上（见 HailOperationFlowPortAdapter）。
 *
 * 占用类规则（一空域一单、同点时段不撞车）的权威把关在这里：开单事务第一步先
 * SELECT ... FOR UPDATE 锁住作业点行，同一作业点的开单串行，锁内复查的结果才可信 ——
 * 两个人同一瞬间抢同一段时辰，也只成一家。
 */
@Component
public class HailFlowTxExecutor {

    private final AmmoStockMapper stockMapper;
    private final AmmoRecordMapper recordMapper;
    private final FireOrderMapper orderMapper;
    private final LauncherMapper launcherMapper;
    private final EffectReportMapper reportMapper;
    private final OperationSiteMapper siteMapper;
    private final AirspaceApplyMapper applyMapper;

    public HailFlowTxExecutor(AmmoStockMapper stockMapper,
                              AmmoRecordMapper recordMapper,
                              FireOrderMapper orderMapper,
                              LauncherMapper launcherMapper,
                              EffectReportMapper reportMapper,
                              OperationSiteMapper siteMapper,
                              AirspaceApplyMapper applyMapper) {
        this.stockMapper = stockMapper;
        this.recordMapper = recordMapper;
        this.orderMapper = orderMapper;
        this.launcherMapper = launcherMapper;
        this.reportMapper = reportMapper;
        this.siteMapper = siteMapper;
        this.applyMapper = applyMapper;
    }

    /**
     * 入库事务：库存 upsert（累加 / 新建，并发撞 uk_stock 退化为累加）+ IN 流水，同生共死。
     */
    @Transactional(rollbackFor = Exception.class)
    public AmmoStock doInboundWithRecord(AmmoStock incoming) {
        AmmoStockPO stock = upsertStock(incoming);
        insertRecord(AmmoRecordPoConverter.toPo(AmmoRecord.inbound(
                incoming.getSiteId(), incoming.getAmmoType(), incoming.getBatchNo(),
                incoming.getQuantity())));
        return AmmoStockPoConverter.toDomain(stockMapper.selectById(stock.getId()));
    }

    /**
     * 开单事务：站点行锁 + 占用复查 → 划出计划发数 → OUT 流水 → 装备作业中 → 插指令。
     * 任一步失败整笔回滚；指令编号撞 uk_order_no 原样抛 DuplicateKeyException 由应用层换号重试。
     */
    @Transactional(rollbackFor = Exception.class)
    public FireOrder doIssueOrder(FireOrder order, String batchNo) {
        String operator = AuditContextHolder.getOperator();
        LocalDateTime now = LocalDateTime.now();

        // 1) 作业点行锁：同一作业点的开单串行化。锁必须加在任何普通 SELECT 之前，
        //    否则 RR 下快照提前建立，锁内复查看不到并发刚插入的指令
        OperationSitePO site = siteMapper.selectForUpdate(order.getSiteId());
        if (site == null) {
            throw new BizException("作业点不存在：siteId=" + order.getSiteId());
        }

        // 2) 空域复检（持锁后读到的是最新已提交数据）：必须已批，且作业时段完整落在批复时段内
        AirspaceApplyPO apply = applyMapper.selectById(order.getApplyId());
        if (apply == null) {
            throw new BizException("空域申请不存在：id=" + order.getApplyId());
        }
        if (!"APPROVED".equals(apply.getStatus())) {
            throw new BizException("只有已批（APPROVED）空域才能开作业指令，当前空域状态：" + apply.getStatus());
        }
        if (order.getStartTime().isBefore(apply.getPlanStart())
                || order.getEndTime().isAfter(apply.getPlanEnd())) {
            throw new BizException("作业时段 " + order.getStartTime() + " ~ " + order.getEndTime()
                    + " 没完整落在空域 " + apply.getApplyNo() + " 批复的时段 "
                    + apply.getPlanStart() + " ~ " + apply.getPlanEnd() + " 里头，起早了、拖晚了都不行");
        }

        // 3) 一张空域同时只能有一张没打完的单子在外面
        FireOrderPO openOnApply = orderMapper.selectOpenByApplyId(apply.getId());
        if (openOnApply != null) {
            throw new BizException("空域 " + apply.getApplyNo() + " 下还有没打完的指令 "
                    + openOnApply.getOrderNo() + "，一张空域同时只能有一张单子在外，等它打完回报或作废后再开");
        }

        // 4) 同一作业点时段撞车：哪怕只撞一分钟也挡回去，并说明撞的是哪一张
        FireOrderPO conflict = orderMapper.selectFirstOverlappingOpen(
                order.getSiteId(), order.getStartTime(), order.getEndTime());
        if (conflict != null) {
            throw new BizException("这段时辰已被先开出的指令 " + conflict.getOrderNo() + "（"
                    + conflict.getStartTime() + " ~ " + conflict.getEndTime()
                    + "）占住了，哪怕只撞一分钟也不能再开");
        }

        // 5) 结存划出（带 quantity >= ? 守卫的原子扣减，防超领 / 防并发丢更新）
        AmmoStockPO stock = stockMapper.selectUnique(
                order.getSiteId(), order.getAmmoType(), batchNo);
        if (stock == null) {
            throw new BizException("该作业点没有这批弹：" + order.getAmmoType() + " / " + batchNo);
        }
        if (stockMapper.deductQuantity(stock.getId(), order.getPlanRounds(), operator, now) == 0) {
            throw new BizException("结存不足，无法划出 " + order.getPlanRounds()
                    + " 发（当前结存见库存）：" + order.getAmmoType() + " / " + batchNo);
        }

        // 6) 领用流水：批次落在流水上（指令表无批次列），change_qty 为负
        insertRecord(AmmoRecordPoConverter.toPo(AmmoRecord.out(
                order.getSiteId(), order.getAmmoType(), batchNo,
                order.getPlanRounds(), order.getOrderNo())));

        // 7) 装备置作业中（只有待命装备能被领用，避免一台装备同时执行两条指令）
        boolean claimed = launcherMapper.update(null, Wrappers.<LauncherPO>lambdaUpdate()
                .set(LauncherPO::getStatus, "IN_USE")
                .set(LauncherPO::getUpdateBy, operator)
                .set(LauncherPO::getUpdateTime, now)
                .eq(LauncherPO::getId, order.getLauncherId())
                .eq(LauncherPO::getStatus, "READY")) > 0;
        if (!claimed) {
            throw new BizException("装备当前不在待命状态，不能执行作业指令");
        }

        // 8) 插入指令（与扣弹同事务；撞 uk_order_no 会让整笔回滚，扣的弹自动还回）
        FireOrderPO po = FireOrderPoConverter.toPo(order);
        po.setId(IdUtil.getSnowflakeNextId());
        orderMapper.insert(po);
        return FireOrderPoConverter.toDomain(po);
    }

    /**
     * 作废事务：条件置 VOID → 计划发数原封退回结存 + RETURN 流水 → 装备回待命。
     * 条件更新翻不动时查现状：已是 VOID 说明并发先作废了，原样返回（幂等，绝不再退一遍弹）。
     */
    @Transactional(rollbackFor = Exception.class)
    public FireOrder doVoidOrder(FireOrder order, String batchNo) {
        String operator = AuditContextHolder.getOperator();
        LocalDateTime now = LocalDateTime.now();

        // 1) 条件作废：只有没打完的（ISSUED / EXECUTING）翻得动；0 行 = 并发已抢先
        int flipped = orderMapper.voidIfOpen(order.getId(), order.getVoidReason(), operator, now);
        if (flipped == 0) {
            FireOrderPO current = orderMapper.selectById(order.getId());
            if (current != null && "VOID".equals(current.getStatus())) {
                // 另一事务已作废：退弹与装备释放它都办妥了，这里原样返回，绝不再退一遍弹
                return FireOrderPoConverter.toDomain(current);
            }
            throw new BizException("指令已完成回报，不能作废：" + order.getOrderNo());
        }

        // 2) 计划发数原封退回结存，并记一笔 RETURN（发数为正，ref=指令编号）
        AmmoStockPO stock = stockMapper.selectUnique(
                order.getSiteId(), order.getAmmoType(), batchNo);
        if (stock == null) {
            throw new BizException("退弹找不到库存批次：" + order.getAmmoType() + " / " + batchNo);
        }
        stockMapper.addQuantity(stock.getId(), order.getPlanRounds(), null, null, operator, now);
        insertRecord(AmmoRecordPoConverter.toPo(AmmoRecord.returned(
                order.getSiteId(), order.getAmmoType(), batchNo,
                order.getPlanRounds(), order.getOrderNo())));

        // 3) 装备回待命（幂等：只在仍作业中时改），时段随 VOID 一并腾出
        launcherMapper.update(null, Wrappers.<LauncherPO>lambdaUpdate()
                .set(LauncherPO::getStatus, "READY")
                .set(LauncherPO::getUpdateBy, operator)
                .set(LauncherPO::getUpdateTime, now)
                .eq(LauncherPO::getId, order.getLauncherId())
                .eq(LauncherPO::getStatus, "IN_USE"));

        return FireOrderPoConverter.toDomain(orderMapper.selectById(order.getId()));
    }

    /**
     * 回报事务：退弹回结存 + RETURN 流水 → 装备回待命 → 条件完结指令（防重复回报）。
     */
    @Transactional(rollbackFor = Exception.class)
    public FireOrder doReportFire(FireOrder order, String batchNo, int returnedRounds) {
        String operator = AuditContextHolder.getOperator();
        LocalDateTime now = LocalDateTime.now();
        // 1) 没打完的退回结存，并记一笔 RETURN（发数为正）
        if (returnedRounds > 0) {
            AmmoStockPO stock = stockMapper.selectUnique(
                    order.getSiteId(), order.getAmmoType(), batchNo);
            if (stock == null) {
                throw new BizException("退弹找不到库存批次：" + order.getAmmoType() + " / " + batchNo);
            }
            stockMapper.addQuantity(stock.getId(), returnedRounds, null, null, operator, now);
            insertRecord(AmmoRecordPoConverter.toPo(AmmoRecord.returned(
                    order.getSiteId(), order.getAmmoType(), batchNo,
                    returnedRounds, order.getOrderNo())));
        }

        // 2) 装备回待命（幂等：只在仍作业中时改）
        launcherMapper.update(null, Wrappers.<LauncherPO>lambdaUpdate()
                .set(LauncherPO::getStatus, "READY")
                .set(LauncherPO::getUpdateBy, operator)
                .set(LauncherPO::getUpdateTime, now)
                .eq(LauncherPO::getId, order.getLauncherId())
                .eq(LauncherPO::getStatus, "IN_USE"));

        // 3) 条件完结：非 DONE / VOID 才更新成功；0 行 = 已被并发回报或已作废 → 退弹整笔回滚
        if (orderMapper.completeIfOpen(order.getId(), order.getUsedRounds(),
                order.getStartTime(), order.getEndTime(), operator, now) == 0) {
            throw new BizException("指令已完成或已作废，不能重复回报：" + order.getOrderNo());
        }

        FireOrderPO po = orderMapper.selectById(order.getId());
        return FireOrderPoConverter.toDomain(po);
    }

    /**
     * 效果上报事务：一条指令一份，撞 uk_order 抛 DuplicateKeyException 由应用层翻译。
     */
    @Transactional(rollbackFor = Exception.class)
    public EffectReport doSubmitReport(EffectReport report) {
        EffectReportPO po = EffectReportPoConverter.toPo(report);
        po.setId(IdUtil.getSnowflakeNextId());
        reportMapper.insert(po);
        return EffectReportPoConverter.toDomain(po);
    }

    // ---------------------------------------------------------------------
    // 内联的库存 upsert（与原 AmmoStockRepositoryImpl#doInbound 同一套语义）：
    // 先查唯一行 → 有则原子累加，没有则 insert，并发撞 uk_stock 退化为累加。
    // ---------------------------------------------------------------------
    private AmmoStockPO upsertStock(AmmoStock incoming) {
        String operator = AuditContextHolder.getOperator();
        LocalDateTime now = LocalDateTime.now();
        AmmoStockPO existing = stockMapper.selectUnique(
                incoming.getSiteId(), incoming.getAmmoType(), incoming.getBatchNo());
        if (existing != null) {
            stockMapper.addQuantity(existing.getId(), incoming.getQuantity(),
                    incoming.getProduceDate(), incoming.getExpireDate(), operator, now);
            return stockMapper.selectById(existing.getId());
        }
        AmmoStockPO po = AmmoStockPoConverter.toPo(incoming);
        po.setId(IdUtil.getSnowflakeNextId());
        try {
            stockMapper.insert(po);
            return po;
        } catch (DuplicateKeyException e) {
            AmmoStockPO winner = stockMapper.selectUnique(
                    incoming.getSiteId(), incoming.getAmmoType(), incoming.getBatchNo());
            if (winner == null) {
                throw e;
            }
            stockMapper.addQuantity(winner.getId(), incoming.getQuantity(),
                    incoming.getProduceDate(), incoming.getExpireDate(), operator, now);
            return stockMapper.selectById(winner.getId());
        }
    }

    private void insertRecord(AmmoRecordPO po) {
        po.setId(IdUtil.getSnowflakeNextId());
        recordMapper.insert(po);
    }
}
