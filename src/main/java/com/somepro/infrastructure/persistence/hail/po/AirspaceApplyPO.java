package com.somepro.infrastructure.persistence.hail.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * t_airspace_apply 表的持久化对象（PO，基础设施层）。
 *
 * 字段与列一一对应，不放业务规则（流转规则在领域对象 AirspaceApply）。
 */
@Getter
@Setter
@TableName("t_airspace_apply")
public class AirspaceApplyPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("apply_no")
    private String applyNo;

    @TableField("site_id")
    private Long siteId;

    /** HAIL / RAIN，存枚举名。 */
    @TableField("purpose")
    private String purpose;

    @TableField("plan_start")
    private LocalDateTime planStart;

    @TableField("plan_end")
    private LocalDateTime planEnd;

    @TableField("max_altitude")
    private Integer maxAltitude;

    /** PENDING / APPROVED / REJECTED / CANCELLED / EXPIRED，存枚举名。 */
    @TableField("status")
    private String status;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("approve_time")
    private LocalDateTime approveTime;
}
