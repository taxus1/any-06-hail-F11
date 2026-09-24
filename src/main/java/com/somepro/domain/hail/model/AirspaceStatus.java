package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;

/**
 * 空域申请状态（领域枚举）。
 *
 * PENDING 待批 / APPROVED 已批 / REJECTED 已驳 / CANCELLED 已撤 / EXPIRED 已失效。
 * 只有 APPROVED 的空域才能下达作业指令。
 */
public enum AirspaceStatus {

    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    /** 把库里 / 接口传入的状态字符串解析成枚举；非法值给明确业务报错。 */
    public static AirspaceStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("空域状态不能为空：PENDING / APPROVED / REJECTED / CANCELLED / EXPIRED");
        }
        try {
            return AirspaceStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("非法空域状态：" + raw
                    + "，只允许 PENDING / APPROVED / REJECTED / CANCELLED / EXPIRED");
        }
    }
}
