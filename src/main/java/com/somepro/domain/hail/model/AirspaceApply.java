package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 空域申请聚合根（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 AirspaceApplyPO）。
 *
 * 关键不变量与流转规则：
 * - 申请编号（applyNo，如 KQ-2026-0101）全局唯一且非空，库表 uk_apply_no 兜底。
 * - 必须挂在真实作业点上；必须写清目的（HAIL 防雹 / RAIN 增雨）。
 * - 拟作业起止时刻必填且结束不得早于开始；请求高度给了就不能为负。
 * - 新报上来一律 PENDING 待批；批复只允许发生在 PENDING 上：
 *     批准 → APPROVED 并记批复时刻；驳回 → REJECTED 且必须写清驳回原因。
 * - 只有 APPROVED 的空域才能开作业指令（由应用层判断）。
 */
@Getter
@Setter
public class AirspaceApply extends BaseEntity {

    private Long id;

    /** 申请编号，全局唯一，如 KQ-2026-0101。 */
    private String applyNo;

    /** 作业点 id（t_operation_site.id），必填。 */
    private Long siteId;

    /** HAIL 防雹 / RAIN 增雨。 */
    private Purpose purpose;

    /** 拟作业开始时刻。 */
    private LocalDateTime planStart;

    /** 拟作业结束时刻。 */
    private LocalDateTime planEnd;

    /** 请求高度上限（米）。 */
    private Integer maxAltitude;

    /** PENDING / APPROVED / REJECTED / CANCELLED / EXPIRED。 */
    private AirspaceStatus status;

    /** 驳回原因（驳回时必填）。 */
    private String rejectReason;

    /** 批复时刻。 */
    private LocalDateTime approveTime;

    /** 工厂方法：新报一条空域申请，初始一律待批。 */
    public static AirspaceApply submit(String applyNo, Long siteId, String purposeRaw,
                                       LocalDateTime planStart, LocalDateTime planEnd,
                                       Integer maxAltitude) {
        AirspaceApply apply = prepare(siteId, purposeRaw, planStart, planEnd, maxAltitude);
        apply.changeNo(applyNo);
        return apply;
    }

    /**
     * 工厂方法：先组装申报内容（校验在此同步 fast-fail），编号还没取到 ——
     * 编号按年生成、需要查库，应用层取到号后再 {@link #changeNo(String)} 补上。
     */
    public static AirspaceApply prepare(Long siteId, String purposeRaw,
                                        LocalDateTime planStart, LocalDateTime planEnd,
                                        Integer maxAltitude) {
        AirspaceApply apply = new AirspaceApply();
        apply.assignTo(siteId);
        apply.changePurpose(purposeRaw);
        apply.changePlanWindow(planStart, planEnd);
        apply.changeMaxAltitude(maxAltitude);
        apply.status = AirspaceStatus.PENDING;
        return apply;
    }

    /** 领域行为：批复通过。只有待批件能批，记下批复时刻。 */
    public void approve(LocalDateTime now) {
        if (status != AirspaceStatus.PENDING) {
            throw new BizException("只有待批的空域申请能批复，当前状态：" + status);
        }
        if (now == null) {
            throw new BizException("批复时刻不能为空");
        }
        this.status = AirspaceStatus.APPROVED;
        this.approveTime = now;
        this.rejectReason = null;
    }

    /** 领域行为：驳回。只有待批件能驳，且驳回原因必须写清。 */
    public void reject(String reason, LocalDateTime now) {
        if (status != AirspaceStatus.PENDING) {
            throw new BizException("只有待批的空域申请能驳回，当前状态：" + status);
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException("驳回必须写明原因");
        }
        if (now == null) {
            throw new BizException("批复时刻不能为空");
        }
        this.status = AirspaceStatus.REJECTED;
        this.rejectReason = reason.trim();
        this.approveTime = now;
    }

    /** 批下来的空域才能开作业指令。 */
    public boolean isApproved() {
        return status == AirspaceStatus.APPROVED;
    }

    public void changeNo(String applyNo) {
        if (applyNo == null || applyNo.isBlank()) {
            throw new BizException("空域申请编号不能为空");
        }
        this.applyNo = applyNo.trim();
    }

    public void assignTo(Long siteId) {
        if (siteId == null) {
            throw new BizException("空域申请必须指定作业点，siteId 不能为空");
        }
        this.siteId = siteId;
    }

    public void changePurpose(String purposeRaw) {
        this.purpose = Purpose.of(purposeRaw);
    }

    public void changePlanWindow(LocalDateTime planStart, LocalDateTime planEnd) {
        if (planStart == null || planEnd == null) {
            throw new BizException("拟作业起止时刻不能为空");
        }
        if (planEnd.isBefore(planStart)) {
            throw new BizException("拟作业结束时刻不能早于开始时刻");
        }
        this.planStart = planStart;
        this.planEnd = planEnd;
    }

    public void changeMaxAltitude(Integer maxAltitude) {
        if (maxAltitude != null && maxAltitude <= 0) {
            throw new BizException("请求高度必须为正数（米）");
        }
        this.maxAltitude = maxAltitude;
    }
}
