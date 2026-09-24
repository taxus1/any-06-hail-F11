package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;

/**
 * 弹药出入库业务类型（领域枚举）。
 *
 * IN 入库 / OUT 领用 / RETURN 退回 / SCRAP 报废。
 * 流水发数正数入库、负数出库（OUT 记负）。
 */
public enum AmmoBizType {

    IN,
    OUT,
    RETURN,
    SCRAP;

    /** 把库里 / 接口传入的类型字符串解析成枚举；非法值给明确业务报错。 */
    public static AmmoBizType of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("出入库类型不能为空：IN 入库 / OUT 领用 / RETURN 退回 / SCRAP 报废");
        }
        try {
            return AmmoBizType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("非法出入库类型：" + raw
                    + "，只允许 IN / OUT / RETURN / SCRAP");
        }
    }
}
