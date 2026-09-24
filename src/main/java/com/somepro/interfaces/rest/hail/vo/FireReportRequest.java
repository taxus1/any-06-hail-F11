package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作业回报请求体：实际打了几发报上来；没打完的（plan - used）自动退回结存。
 * 起止时刻可空，后端缺省取当前时刻。
 */
public record FireReportRequest(
        @NotNull(message = "实际用弹发数不能为空（没用上填 0）")
        @PositiveOrZero(message = "实际用弹发数不能为负")
        Integer usedRounds,

        LocalDateTime startTime,

        LocalDateTime endTime) implements Serializable {
}
