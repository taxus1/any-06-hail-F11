package com.somepro.domain.hail.model;

import java.time.LocalDateTime;

/**
 * 某个小时槽里的一笔占用（领域值对象，只读视图）。
 *
 * 由作业指令的占用时段（开着的单占计划时段、打完的单占实际时段）投影而来，
 * 捎上对外可读的空域编号与装备编号；不是聚合根，没有生命周期。
 */
public record HourOccupant(

        /** 占着这段时辰的指令编号。 */
        String orderNo,

        /** 空域申请 id。 */
        Long applyId,

        /** 空域申请编号，如 KQ-2026-0101。 */
        String applyNo,

        /** 执行装备 id。 */
        Long launcherId,

        /** 装备编号，如 ZB-0007。 */
        String launcherCode,

        /** 占用窗口起点（开着的单为计划开始，打完的单为实际开始）。 */
        LocalDateTime windowStart,

        /** 占用窗口终点。 */
        LocalDateTime windowEnd,

        /** 指令状态（枚举名）：ISSUED / EXECUTING / DONE。 */
        String orderStatus) {
}
