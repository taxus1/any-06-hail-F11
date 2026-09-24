package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 空域驳回请求体：驳回必须写清原因。
 */
public record AirspaceRejectRequest(
        @NotBlank(message = "驳回原因不能为空")
        String reason) implements Serializable {
}
