package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 登记作业点请求体（用户接口层，不可变 record）。
 *
 * 状态不在此用枚举强校验：统一由领域 {@code SiteStatus.of} 收敛，
 * 非法值返回中文业务提示（ACTIVE / SUSPENDED / CLOSED）。
 */
public record SiteRegisterRequest(
        @NotBlank(message = "作业点编号不能为空")
        @Size(max = 32, message = "作业点编号最长 32 位")
        String siteCode,

        @NotBlank(message = "作业点名称不能为空")
        @Size(max = 128, message = "作业点名称最长 128 位")
        String siteName,

        @Size(max = 64, message = "县区最长 64 位")
        String county,

        @PositiveOrZero(message = "海拔不能为负数")
        Integer altitudeM,

        @Size(max = 64, message = "值守人姓名最长 64 位")
        String contactName,

        @Size(max = 32, message = "值守电话最长 32 位")
        String contactPhone,

        @NotNull(message = "状态不能为空：ACTIVE / SUSPENDED / CLOSED")
        String status) implements Serializable {
}
