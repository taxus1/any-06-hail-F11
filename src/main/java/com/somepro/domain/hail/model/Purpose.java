package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;

/**
 * 作业目的（领域枚举）。
 *
 * HAIL 防雹 / RAIN 增雨。
 * 空域申请必须写清目的，库里存枚举名字符串。
 */
public enum Purpose {

    HAIL,
    RAIN;

    /** 把接口传入的目的字符串解析成枚举；非法值给明确业务报错，不静默默认。 */
    public static Purpose of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("作业目的不能为空：HAIL 防雹 / RAIN 增雨");
        }
        try {
            return Purpose.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("非法作业目的：" + raw + "，只允许 HAIL 防雹 / RAIN 增雨");
        }
    }
}
