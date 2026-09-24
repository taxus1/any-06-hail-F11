package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作业指令对外对象（VO，用户接口层，不可变 record）。
 */
public record FireOrderVO(Long id,
                          String orderNo,
                          Long applyId,
                          Long siteId,
                          Long launcherId,
                          String ammoType,
                          Integer planRounds,
                          Integer usedRounds,
                          String status,
                          LocalDateTime startTime,
                          LocalDateTime endTime,
                          LocalDateTime createTime) implements Serializable {
}
