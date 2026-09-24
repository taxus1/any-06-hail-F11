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

    public HailFlowTxExecutor(AmmoStockMapper stockMapper,
                              AmmoRecordMapper recordMapper,
                              FireOrderMapper orderMapper,
                              LauncherMapper launcherMapper,
                              EffectReportMapper reportMapper) {
        this.stockMapper = stockMapper;
        this.recordMapper = recordMapper;
        this.orderMapper = orderMapper;
        this.launcherMapper = launcherMapper;
        this.reportMapper = reportMapper;
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
     * 开单事务：划出计划发数 → OUT 流水 → 装备作业中 → 插指令。
     * 任一步失败整笔回滚；指令编号撞 uk_order_no 原样抛 DuplicateKeyException 由应用层换号重试。
     */
    @Transactional(rollbackFor = Exception.class)
    public FireOrder doIssueOrder(FireOrder order, String batchNo) {
        String operator = AuditContextHolder.getOperator();
        LocalDateTime now = LocalDateTime.now();
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

        // 3) 装备置作业中（只有待命装备能被领用，避免一台装备同时执行两条指令）
        boolean claimed = launcherMapper.update(null, Wrappers.<LauncherPO>lambdaUpdate()
                .set(LauncherPO::getStatus, "IN_USE")
                .set(LauncherPO::getUpdateBy, operator)
                .set(LauncherPO::getUpdateTime, now)
                .eq(LauncherPO::getId, order.getLauncherId())
                .eq(LauncherPO::getStatus, "READY")) > 0;
        if (!claimed) {
            throw new BizException("装备当前不在待命状态，不能执行作业指令");
        }

        // 4) 插入指令（与扣弹同事务；撞 uk_order_no 会让整笔回滚，扣的弹自动还回）
        FireOrderPO po = FireOrderPoConverter.toPo(order);
        po.setId(IdUtil.getSnowflakeNextId());
        orderMapper.insert(po);
        return FireOrderPoConverter.toDomain(po);
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
