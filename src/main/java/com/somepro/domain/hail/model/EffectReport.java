package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作业效果上报聚合根（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 EffectReportPO）。
 *
 * 约定（一条指令一份，库表 uk_order 兜底）：
 * - 必须挂在一条已完成（DONE）回报的作业指令上（跨聚合校验在应用层）。
 * - 三项指标都按物理单位填：降雨量 / 冰雹粒径毫米，影响面积平方公里；
 *   都不能为负（增雨作业没有冰雹，粒径填 0）。上报时刻给了就用，没给取当前。
 */
@Getter
@Setter
public class EffectReport extends BaseEntity {

    private Long id;

    /** 作业指令 id（t_fire_order.id），一条指令一份。 */
    private Long orderId;

    /** 上报时刻。 */
    private LocalDateTime reportTime;

    /** 过程降雨量（毫米）。 */
    private BigDecimal rainfallMm;

    /** 最大冰雹粒径（毫米）。 */
    private BigDecimal hailSizeMm;

    /** 影响面积（平方公里）。 */
    private BigDecimal areaKm2;

    /** 备注。 */
    private String remark;

    /** 工厂方法：为一条指令提交效果上报。 */
    public static EffectReport submit(Long orderId, LocalDateTime reportTime,
                                      BigDecimal rainfallMm, BigDecimal hailSizeMm,
                                      BigDecimal areaKm2, String remark) {
        EffectReport report = new EffectReport();
        report.linkOrder(orderId);
        report.fillMetrics(rainfallMm, hailSizeMm, areaKm2);
        report.reportTime = reportTime;
        report.remark = remark == null || remark.isBlank() ? null : remark.trim();
        return report;
    }

    public void linkOrder(Long orderId) {
        if (orderId == null) {
            throw new BizException("效果上报必须关联作业指令，orderId 不能为空");
        }
        this.orderId = orderId;
    }

    /** 领域行为：三项指标都按毫米 / 平方公里填，允许填 0，但不能为负。 */
    public void fillMetrics(BigDecimal rainfallMm, BigDecimal hailSizeMm, BigDecimal areaKm2) {
        this.rainfallMm = checkMetric(rainfallMm, "过程降雨量（毫米）");
        this.hailSizeMm = checkMetric(hailSizeMm, "最大冰雹粒径（毫米）");
        this.areaKm2 = checkMetric(areaKm2, "影响面积（平方公里）");
    }

    private BigDecimal checkMetric(BigDecimal value, String label) {
        if (value == null) {
            throw new BizException(label + "不能为空");
        }
        if (value.signum() < 0) {
            throw new BizException(label + "不能为负");
        }
        return value;
    }
}
