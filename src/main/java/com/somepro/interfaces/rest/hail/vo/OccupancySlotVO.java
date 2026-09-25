package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 占用查法的单个钟头格子（VO，不可变 record）。
 *
 * @param slotStart    整点起点
 * @param slotEnd      整点终点
 * @param occupied     是否被占
 * @param applyNo      占格空域申请编号，空格子为 null
 * @param launcherCode 占格装备编号，空格子为 null
 * @param orderNo      占格指令编号，空格子为 null
 */
public record OccupancySlotVO(LocalDateTime slotStart,
                              LocalDateTime slotEnd,
                              boolean occupied,
                              String applyNo,
                              String launcherCode,
                              String orderNo) implements Serializable {
}
