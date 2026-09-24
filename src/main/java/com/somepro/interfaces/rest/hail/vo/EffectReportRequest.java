package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 效果上报请求体（用户接口层，不可变 record）。
 * 降雨量 / 冰雹粒径按毫米填，影响面积按平方公里填；增雨无冰雹时粒径填 0。
 */
public record EffectReportRequest(
        @NotNull(message = "过程降雨量不能为空（毫米，无降雨填 0）")
        @PositiveOrZero(message = "过程降雨量不能为负")
        BigDecimal rainfallMm,

        @NotNull(message = "最大冰雹粒径不能为空（毫米，无冰雹填 0）")
        @PositiveOrZero(message = "冰雹粒径不能为负")
        BigDecimal hailSizeMm,

        @NotNull(message = "影响面积不能为空（平方公里）")
        @PositiveOrZero(message = "影响面积不能为负")
        BigDecimal areaKm2,

        LocalDateTime reportTime,

        String remark) implements Serializable {
}
