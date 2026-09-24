package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 登记发射装备请求体（用户接口层，不可变 record）。
 *
 * 挂在哪个作业点必填（装备不能没主）；状态由领域 {@code LauncherStatus.of} 收敛。
 */
public record LauncherRegisterRequest(
        @NotBlank(message = "装备编号不能为空")
        @Size(max = 32, message = "装备编号最长 32 位")
        String launcherCode,

        @NotNull(message = "装备必须归属一个作业点，siteId 不能为空")
        Long siteId,

        @Size(max = 64, message = "装备型号最长 64 位")
        String model,

        @Positive(message = "发射管数必须为正整数")
        Integer barrelCount,

        @NotBlank(message = "状态不能为空：READY / IN_USE / MAINTENANCE / RETIRED")
        String status,

        LocalDate checkDate) implements Serializable {
}
