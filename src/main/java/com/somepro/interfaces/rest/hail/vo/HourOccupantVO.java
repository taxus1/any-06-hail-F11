package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 某个小时槽里的一笔占用（对外 VO，用户接口层，不可变 record）。
 */
public record HourOccupantVO(String orderNo,
                             Long applyId,
                             String applyNo,
                             Long launcherId,
                             String launcherCode,
                             LocalDateTime windowStart,
                             LocalDateTime windowEnd,
                             String orderStatus) implements Serializable {
}
