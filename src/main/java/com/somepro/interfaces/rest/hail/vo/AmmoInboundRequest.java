package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 弹药入库请求体（用户接口层，不可变 record）。
 *
 * 同点 + 同弹型 + 同批次再入一次，后端累加原记录，调用方仍按「入库」语义提交即可。
 */
public record AmmoInboundRequest(
        @NotNull(message = "作业点 siteId 不能为空")
        Long siteId,

        @NotBlank(message = "弹型不能为空，如 BL-1A")
        @Size(max = 32, message = "弹型最长 32 位")
        String ammoType,

        @NotBlank(message = "批次号不能为空")
        @Size(max = 32, message = "批次号最长 32 位")
        String batchNo,

        @NotNull(message = "入库发数不能为空")
        @Positive(message = "入库发数必须为正整数")
        Integer quantity,

        LocalDate produceDate,

        LocalDate expireDate) implements Serializable {
}
