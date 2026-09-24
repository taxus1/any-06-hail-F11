package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;

/**
 * 作业指令状态（领域枚举）。
 *
 * ISSUED 已下达 / EXECUTING 作业中 / DONE 已完成 / VOID 已作废。
 */
public enum OrderStatus {

    ISSUED,
    EXECUTING,
    DONE,
    VOID;

    /** 把库里 / 接口传入的状态字符串解析成枚举；非法值给明确业务报错。 */
    public static OrderStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("作业指令状态不能为空：ISSUED / EXECUTING / DONE / VOID");
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("非法作业指令状态：" + raw
                    + "，只允许 ISSUED / EXECUTING / DONE / VOID");
        }
    }
}
