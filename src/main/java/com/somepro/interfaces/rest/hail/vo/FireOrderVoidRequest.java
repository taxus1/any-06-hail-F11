package com.somepro.interfaces.rest.hail.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 作废作业指令请求体（用户接口层，不可变 record）。
 *
 * 只有发都没打的单子（ISSUED / EXECUTING）能作废：计划发数原封退回结存，
 * 装备与时段一并腾出。作废必须写明原因；重复作废幂等，原样退回不再退弹。
 */
public record FireOrderVoidRequest(
        @NotBlank(message = "作废必须写明原因")
        @Size(max = 255, message = "作废原因最长 255 位")
        String reason) implements Serializable {
}
