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
 * t_fire_order 表的持久化对象（PO，基础设施层）。
 *
 * 字段与列一一对应，不放业务规则（流转规则在领域对象 FireOrder）。
 * 注意本表没有批次列：领用批次记在 t_ammo_record 的 OUT 流水上。
 */
@Getter
@Setter
@TableName("t_fire_order")
public class FireOrderPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("apply_id")
    private Long applyId;

    @TableField("site_id")
    private Long siteId;

    @TableField("launcher_id")
    private Long launcherId;

    @TableField("ammo_type")
    private String ammoType;

    @TableField("plan_rounds")
    private Integer planRounds;

    @TableField("used_rounds")
    private Integer usedRounds;

    /** ISSUED / EXECUTING / DONE / VOID，存枚举名。 */
    @TableField("status")
    private String status;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("void_reason")
    private String voidReason;
}
