package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;

/**
 * 作业点状态（领域枚举）。
 *
 * ACTIVE 在册 / SUSPENDED 封存 / CLOSED 撤销。
 * 库里存的是枚举名字符串（见建表 SQL 注释），这里负责把外部传入的字符串收敛成合法枚举。
 */
public enum SiteStatus {

    ACTIVE,
    SUSPENDED,
    CLOSED;

    /** 把接口传入的状态字符串解析成枚举；非法值给明确业务报错，不静默默认。 */
    public static SiteStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("作业点状态不能为空：ACTIVE / SUSPENDED / CLOSED");
        }
        try {
            return SiteStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("非法作业点状态：" + raw + "，只允许 ACTIVE / SUSPENDED / CLOSED");
        }
    }
}
