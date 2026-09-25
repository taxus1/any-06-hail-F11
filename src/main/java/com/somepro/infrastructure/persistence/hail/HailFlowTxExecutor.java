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
 */
@Component
public class HailFlowTxExecutor {

    private final AmmoStockMapper stockMapper;
    private final AmmoRecordMapper recordMapper;
    private final FireOrderMapper orderMapper;
    private final LauncherMapper launcherMapper;
    private final EffectReportMapper reportMapper;
    private final AirspaceApplyMapper applyMapper;
    private final OperationSiteMapper siteMapper;

    public HailFlowTxExecutor(AmmoStockMapper stockMapper,
                              AmmoRecordMapper recordMapper,
                              FireOrderMapper orderMapper,
                              LauncherMapper launcherMapper,
                              EffectReportMapper reportMapper,
                              AirspaceApplyMapper applyMapper,
                              OperationSiteMapper siteMapper) {
        this.stockMapper = stockMapper;
        this.recordMapper = recordMapper;
        this.orderMapper = orderMapper;
        this.launcherMapper = launcherMapper;
        this.reportMapper = reportMapper;
        this.applyMapper = applyMapper;
        this.siteMapper = siteMapper;
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
     * 开单事务：锁作业点 → 查占单（空域时段撞车 / 同一空域拆单）→ 划出计划发数 → OUT 流水
     * → 装备作业中 → 插指令。
     * 任一步失败整笔回滚；指令编号撞 uk_order_no 原样抛 DuplicateKeyException 由应用层换号重试。
     */
    @Transactional(rollbackFor = Exception.class)
    public FireOrder doIssueOrder(FireOrder order, String batchNo) {
        String operator = AuditContextHolder.getOperator();
        LocalDateTime now = LocalDateTime.now();

        // 0) 先锁作业点行：把同点开单串行化。两人同一瞬间抢同一段时辰，
        //    后到的在这把行锁上排队，等先到的单子落库后再查占单，必能看见并被挡回。
        if (siteMapper.selectIdForUpdate(order.getSiteId()) == null) {
            throw new BizException("作业点不存在：siteId=" + order.getSiteId());
        }

        // 0.1) 权威复查空域：挂的必须是本作业点已批空域，时段以库为准，不信应用层读到的快照
        AirspaceApplyPO apply = applyMapper.selectById(order.getApplyId());
        if (apply == null) {
            throw new BizException("空域申请不存在：id=" + order.getApplyId());
        }
        if (!"APPROVED".equals(apply.getStatus())) {
            throw new BizException("只有已批（APPROVED）空域才能开作业指令，当前空域状态："
                    + apply.getStatus());
        }
        if (!order.getSiteId().equals(apply.getSiteId())) {
            throw new BizException("空域不属于该作业点，不能跨点开单");
        }

        // 0.2) 占单排查：同一空域已有在途单子（不许一张空域拆多张单占满时段）→ 挡回；
        //      不同空域但批复时段在同一作业点撞上（哪怕一分钟）→ 挡回，并告诉人家撞的是哪张单
        FireOrderPO blocker = orderMapper.selectOpenBlocker(
                order.getSiteId(), order.getApplyId(), apply.getPlanStart(), apply.getPlanEnd());
        if (blocker != null) {
            if (order.getApplyId().equals(blocker.getApplyId())) {
                throw new BizException("空域 " + apply.getApplyNo() + " 已有一张没打完的单子 "
                        + blocker.getOrderNo() + " 在外面，一张空域同时只能开一张单，"
                        + "等它回报或作废后再开");
            }
            throw new BizException("该作业点时段已被先开的单子占住：撞的是指令 "
                    + blocker.getOrderNo() + "（空域 " + blockerOrderApplyNo(blocker.getId())
                    + "），本次开单挡回");
        }

        // 1) 结存划出（带 quantity >= ? 守卫的原子扣减，防超领 / 防并发丢更新）
        AmmoStockPO stock = stockMapper.selectUnique(
                order.getSiteId(), order.getAmmoType(), batchNo);
        if (stock == null) {
            throw new BizException("该作业点没有这批弹：" + order.getAmmoType() + " / " + batchNo);
        }
        if (stockMapper.deductQuantity(stock.getId(), order.getPlanRounds(), operator, now) == 0) {
            throw new BizException("结存不足，无法划出 " + order.getPlanRounds()
                    + " 发（当前结存见库存）：" + order.getAmmoType() + " / " + batchNo);
        }

        // 2) 领用流水：批次落在流水上（指令表无批次列），change_qty 为负
        insertRecord(AmmoRecordPoConverter.toPo(AmmoRecord.out(
                order.getSiteId(), order.getAmmoType(), batchNo,
                order.getPlanRounds(), order.getOrderNo())));

        // 3) 装备置作业中（只有待命装备能被领用；一台装备被没完结的单子用着，
        //    别的单子哪怕时段不撞也点不动它）
        boolean claimed = launcherMapper.update(null, Wrappers.<LauncherPO>lambdaUpdate()
                .set(LauncherPO::getStatus, "IN_USE")
                .set(LauncherPO::getUpdateBy, operator)
                .set(LauncherPO::getUpdateTime, now)
                .eq(LauncherPO::getId, order.getLauncherId())
                .eq(LauncherPO::getStatus, "READY")) > 0;
        if (!claimed) {
            throw new BizException("装备当前不在待命状态，已被别的没完结单子用着，不能执行作业指令");
        }

        // 4) 插入指令（与扣弹同事务；撞 uk_order_no 会让整笔回滚，扣的弹自动还回）
        FireOrderPO po = FireOrderPoConverter.toPo(order);
        po.setId(IdUtil.getSnowflakeNextId());
        orderMapper.insert(po);
        return FireOrderPoConverter.toDomain(po);
    }

    /**
     * 作废事务：条件作废成功（没完结且一发没打）→ 计划发数原封退回结存并记 RETURN
     * → 装备回待命（腾出装备与空域时段）。
     * 条件更新 0 行 = 已被并发作废 / 已回报 / 已打过弹：抛错回滚，退弹绝不发生，
     * 因此连点两遍作废也只退一次弹（第一遍成功，第二遍由应用层 VOID 直返，根本不进事务）。
     */
    @Transactional(rollbackFor = Exception.class)
    public FireOrder doVoidOrder(FireOrder order, String batchNo) {
        String operator = AuditContextHolder.getOperator();
        LocalDateTime now = LocalDateTime.now();

        // 0) 条件作废：只有 ISSUED / EXECUTING 且 used_rounds = 0 更新得动
        int affected = orderMapper.voidIfFresh(order.getId(), order.getVoidReason(), operator, now);
        if (affected == 0) {
            FireOrderPO current = orderMapper.selectById(order.getId());
            String state = current == null ? "不存在" : current.getStatus();
            int used = current == null || current.getUsedRounds() == null ? 0 : current.getUsedRounds();
            if (used > 0) {
                throw new BizException("已实弹发射 " + used + " 发的指令不能作废：" + order.getOrderNo());
            }
            throw new BizException("指令当前状态 " + state + "，不能作废（可能已被并发回报 / 作废）："
                    + order.getOrderNo());
        }

        // 1) 原划走的弹原封退回结存，记一笔 RETURN（发数为正）
        AmmoStockPO stock = stockMapper.selectUnique(
                order.getSiteId(), order.getAmmoType(), batchNo);
        if (stock == null) {
            throw new BizException("退弹找不到库存批次：" + order.getAmmoType() + " / " + batchNo);
        }
        stockMapper.addQuantity(stock.getId(), order.getPlanRounds(), null, null, operator, now);
        insertRecord(AmmoRecordPoConverter.toPo(AmmoRecord.returned(
                order.getSiteId(), order.getAmmoType(), batchNo,
                order.getPlanRounds(), order.getOrderNo())));

        // 2) 装备回待命，装备与所占空域时段都腾出来给下一张单（幂等：只在仍作业中时改）
        launcherMapper.update(null, Wrappers.<LauncherPO>lambdaUpdate()
                .set(LauncherPO::getStatus, "READY")
                .set(LauncherPO::getUpdateBy, operator)
                .set(LauncherPO::getUpdateTime, now)
                .eq(LauncherPO::getId, order.getLauncherId())
                .eq(LauncherPO::getStatus, "IN_USE"));

        return FireOrderPoConverter.toDomain(orderMapper.selectById(order.getId()));
    }

    /** 报错文案里带上撞单挂的空域申请编号，让人家知道撞的是哪张空域。 */
    private String blockerOrderApplyNo(Long blockerOrderId) {
        FireOrderPO blockerOrder = orderMapper.selectById(blockerOrderId);
        if (blockerOrder == null) {
            return "未知空域";
        }
        AirspaceApplyPO blockerApply = applyMapper.selectById(blockerOrder.getApplyId());
        return blockerApply == null ? "未知空域" : blockerApply.getApplyNo();
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
