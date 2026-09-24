package com.somepro.infrastructure.persistence.hail.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * t_effect_report 表的持久化对象（PO，基础设施层）。
 *
 * 字段与列一一对应。降雨量 / 冰雹粒径毫米，影响面积平方公里。
 */
@Getter
@Setter
@TableName("t_effect_report")
public class EffectReportPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("report_time")
    private LocalDateTime reportTime;

    @TableField("rainfall_mm")
    private BigDecimal rainfallMm;

    @TableField("hail_size_mm")
    private BigDecimal hailSizeMm;

    @TableField("area_km2")
    private BigDecimal areaKm2;

    @TableField("remark")
    private String remark;
}
