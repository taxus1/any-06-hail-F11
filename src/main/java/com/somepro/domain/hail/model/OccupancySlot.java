package com.somepro.domain.hail.model;

import java.time.LocalDateTime;

/**
 * 作业点某日「一个钟头被谁占着」的格子（领域只读值对象，不可变）。
 *
 * 占用口径：一张没完结（ISSUED / EXECUTING）的单子在外面，它挂的空域时段就把作业点占着 ——
 * 只要格子区间与该单子的空域批复时段有重叠（哪怕一分钟），这一格就算它的。
 * 已完成 / 已作废的单子不占格子。
 *
 * @param slotStart  格子起点（整点）
 * @param slotEnd    格子终点（下一整点，半开区间）
 * @param occupied   这一格有没有被占
 * @param applyNo    占格空域的申请编号（如 KQ-2026-0101），空格子为 null
 * @param launcherCode 占格装备的编号（如 ZB-0007），空格子为 null
 * @param orderNo    占格指令编号（如 ZY-2026-0101），空格子为 null
 */
public record OccupancySlot(LocalDateTime slotStart,
                            LocalDateTime slotEnd,
                            boolean occupied,
                            String applyNo,
                            String launcherCode,
                            String orderNo) {

    /** 空格子：这一个钟头没占上。 */
    public static OccupancySlot empty(LocalDateTime slotStart, LocalDateTime slotEnd) {
        return new OccupancySlot(slotStart, slotEnd, false, null, null, null);
    }

    /** 占用格子：记下是哪张空域、哪台装备、哪张单子占的。 */
    public static OccupancySlot occupied(LocalDateTime slotStart, LocalDateTime slotEnd,
                                         String applyNo, String launcherCode, String orderNo) {
        return new OccupancySlot(slotStart, slotEnd, true, applyNo, launcherCode, orderNo);
    }
}
