package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作业效果上报对外对象（VO，用户接口层，不可变 record）。
 */
public record EffectReportVO(Long id,
                             Long orderId,
                             LocalDateTime reportTime,
                             BigDecimal rainfallMm,
                             BigDecimal hailSizeMm,
                             BigDecimal areaKm2,
                             String remark,
                             LocalDateTime createTime) implements Serializable {
}
