package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 作业指令聚合根（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 FireOrderPO）。
 *
 * 关键不变量与流转规则：
 * - 指令编号（orderNo，如 ZY-2026-0101）全局唯一且非空，库表 uk_order_no 兜底。
 * - 必须挂在一条 APPROVED 的空域申请上；作业点、装备、弹型、计划发数在开单时定死。
 * - 计划发数必须为正；装备归属作业点要与指令作业点一致（跨聚合校验在应用层）。
 * - 开单时弹药已从结存划走（OUT 领用流水在同一事务里记账），
 *   所以指令上的计划发数不允许再改。
 * - 回报实际发数：0 ≤ used ≤ plan；没用完的（plan - used）由应用层退回结存（RETURN 流水），
 *   回报后指令置 DONE，一条指令只能回报一次。
 *
 * 注意：批次号不在本表（表上没有该列），领用批次记在 t_ammo_record 的 OUT 流水上。
 */
@Getter
@Setter
public class FireOrder extends BaseEntity {

    private Long id;

    /** 指令编号，全局唯一，如 ZY-2026-0101。 */
    private String orderNo;

    /** 空域申请 id（t_airspace_apply.id），必须是已批空域。 */
    private Long applyId;

    /** 作业点 id（t_operation_site.id）。 */
    private Long siteId;

    /** 执行装备 id（t_launcher.id）。 */
    private Long launcherId;

    /** 弹型。 */
    private String ammoType;

    /** 计划用弹发数（开单时即从结存划走）。 */
    private Integer planRounds;

    /** 实际用弹发数（回报时填）。 */
    private Integer usedRounds;

    /** ISSUED 已下达 / EXECUTING 作业中 / DONE 已完成 / VOID 已作废。 */
    private OrderStatus status;

    /** 实际作业开始时刻。 */
    private LocalDateTime startTime;

    /** 实际作业结束时刻。 */
    private LocalDateTime endTime;

    /** 作废原因。 */
    private String voidReason;

    /** 工厂方法：下达一条新作业指令，初始 ISSUED，实际发数为 0。 */
    public static FireOrder issue(String orderNo, Long applyId, Long siteId, Long launcherId,
                                  String ammoType, int planRounds) {
        FireOrder order = new FireOrder();
        order.changeNo(orderNo);
        order.linkApply(applyId);
        order.assignTo(siteId);
        order.useLauncher(launcherId);
        order.specifyAmmoType(ammoType);
        order.specifyPlanRounds(planRounds);
        order.usedRounds = 0;
        order.status = OrderStatus.ISSUED;
        return order;
    }

    /**
     * 领域行为：回报实际打了几发。
     *
     * @param usedRounds 实际发数，0 ≤ used ≤ plan，没用完的由应用层退回结存
     * @param startTime  实际开始时刻，可空
     * @param endTime    实际结束时刻，可空
     * @return 应退回结存的发数（planRounds - usedRounds，可能为 0）
     */
    public int reportFire(int usedRounds, LocalDateTime startTime, LocalDateTime endTime) {
        if (status == OrderStatus.DONE) {
            throw new BizException("指令已完成回报，不能重复回报：" + orderNo);
        }
        if (status == OrderStatus.VOID) {
            throw new BizException("指令已作废，不能回报：" + orderNo);
        }
        if (usedRounds < 0) {
            throw new BizException("实际用弹发数不能为负");
        }
        if (usedRounds > planRounds) {
            throw new BizException("实际用弹 " + usedRounds + " 发超过计划发数 " + planRounds + " 发");
        }
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new BizException("实际结束时刻不能早于开始时刻");
        }
        int returned = planRounds - usedRounds;
        this.usedRounds = usedRounds;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = OrderStatus.DONE;
        return returned;
    }

    public void changeNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException("作业指令编号不能为空");
        }
        this.orderNo = orderNo.trim();
    }

    public void linkApply(Long applyId) {
        if (applyId == null) {
            throw new BizException("作业指令必须关联一条空域申请，applyId 不能为空");
        }
        this.applyId = applyId;
    }

    public void assignTo(Long siteId) {
        if (siteId == null) {
            throw new BizException("作业指令必须指定作业点，siteId 不能为空");
        }
        this.siteId = siteId;
    }

    public void useLauncher(Long launcherId) {
        if (launcherId == null) {
            throw new BizException("作业指令必须指定执行装备，launcherId 不能为空");
        }
        this.launcherId = launcherId;
    }

    public void specifyAmmoType(String ammoType) {
        if (ammoType == null || ammoType.isBlank()) {
            throw new BizException("弹型不能为空，如 BL-1A");
        }
        this.ammoType = ammoType.trim();
    }

    public void specifyPlanRounds(int planRounds) {
        if (planRounds <= 0) {
            throw new BizException("计划用弹发数必须为正整数");
        }
        this.planRounds = planRounds;
    }
}
