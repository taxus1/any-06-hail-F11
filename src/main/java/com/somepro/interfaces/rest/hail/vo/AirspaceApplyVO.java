package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 空域申请对外对象（VO，用户接口层，不可变 record）。
 */
public record AirspaceApplyVO(Long id,
                              String applyNo,
                              Long siteId,
                              String purpose,
                              LocalDateTime planStart,
                              LocalDateTime planEnd,
                              Integer maxAltitude,
                              String status,
                              String rejectReason,
                              LocalDateTime approveTime,
                              LocalDateTime createTime) implements Serializable {
}
