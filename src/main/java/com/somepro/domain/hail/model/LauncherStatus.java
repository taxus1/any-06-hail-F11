package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;

/**
 * 发射装备状态（领域枚举）。
 *
 * READY 待命 / IN_USE 作业中 / MAINTENANCE 检修 / RETIRED 退役。
 */
public enum LauncherStatus {

    READY,
    IN_USE,
    MAINTENANCE,
    RETIRED;

    /** 把接口传入的状态字符串解析成枚举；非法值给明确业务报错，不静默默认。 */
    public static LauncherStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("装备状态不能为空：READY / IN_USE / MAINTENANCE / RETIRED");
        }
        try {
            return LauncherStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("非法装备状态：" + raw
                    + "，只允许 READY / IN_USE / MAINTENANCE / RETIRED");
        }
    }
}
