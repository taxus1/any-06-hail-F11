package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 弹药出入库流水（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 AmmoRecordPO）。
 *
 * 约定：
 * - changeQty 正数入库、负数出库；与 bizType 的对应在工厂方法里锁死：
 *   IN / RETURN 为正，OUT / SCRAP 为负，调用方只传「发数」正数即可。
 * - 流水只追加不改写，是库存账实核对的依据；开单（OUT）、回报退弹（RETURN）、入库（IN）都落一笔。
 * - refNo 记关联单据：领用 / 退回记指令编号，入库可空。
 */
@Getter
@Setter
public class AmmoRecord extends BaseEntity {

    private Long id;

    /** 作业点 id（t_operation_site.id）。 */
    private Long siteId;

    /** 弹型。 */
    private String ammoType;

    /** 批次号。 */
    private String batchNo;

    /** 变动发数：正入负出。 */
    private Integer changeQty;

    /** IN 入库 / OUT 领用 / RETURN 退回 / SCRAP 报废。 */
    private AmmoBizType bizType;

    /** 关联单据号（入库单号或指令编号）。 */
    private String refNo;

    /** 入库流水：发数为正，refNo 可空。 */
    public static AmmoRecord inbound(Long siteId, String ammoType, String batchNo, int qty) {
        return of(siteId, ammoType, batchNo, qty, AmmoBizType.IN, null);
    }

    /** 领用流水：开单划弹，发数为负，refNo 记指令编号。 */
    public static AmmoRecord out(Long siteId, String ammoType, String batchNo, int qty, String orderNo) {
        return of(siteId, ammoType, batchNo, qty, AmmoBizType.OUT, orderNo);
    }

    /** 退回流水：没打完的退回结存，发数为正，refNo 记指令编号。 */
    public static AmmoRecord returned(Long siteId, String ammoType, String batchNo, int qty, String orderNo) {
        return of(siteId, ammoType, batchNo, qty, AmmoBizType.RETURN, orderNo);
    }

    private static AmmoRecord of(Long siteId, String ammoType, String batchNo, int qty,
                                 AmmoBizType bizType, String refNo) {
        if (siteId == null) {
            throw new BizException("弹药流水必须挂在作业点上，siteId 不能为空");
        }
        if (ammoType == null || ammoType.isBlank()) {
            throw new BizException("弹型不能为空，如 BL-1A");
        }
        if (qty <= 0) {
            throw new BizException("流水发数必须为正整数，出入方向由业务类型决定");
        }
        if (batchNo == null || batchNo.isBlank()) {
            throw new BizException("批次号不能为空");
        }
        AmmoRecord record = new AmmoRecord();
        record.siteId = siteId;
        record.ammoType = ammoType.trim();
        record.batchNo = batchNo.trim();
        record.bizType = bizType;
        // 入库 / 退回记正，领用 / 报废记负
        record.changeQty = (bizType == AmmoBizType.IN || bizType == AmmoBizType.RETURN) ? qty : -qty;
        record.refNo = refNo == null || refNo.isBlank() ? null : refNo.trim();
        return record;
    }
}
