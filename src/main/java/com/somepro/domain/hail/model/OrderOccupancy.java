package com.somepro.domain.hail.model;

import java.time.LocalDateTime;

/**
 * 一条在途（没完结）作业指令的占用投影（领域只读值对象，不可变）。
 *
 * 占用查法用它：在途单子挂的空域批复时段 [windowStart, windowEnd) 占着该作业点，
 * 装备与空域编号一并带出来，按小时落格时直接可读，不必逐格回查多张表。
 *
 * @param orderId       指令 id
 * @param orderNo       指令编号
 * @param applyId       空域申请 id
 * @param applyNo       空域申请编号（如 KQ-2026-0101）
 * @param launcherId    执行装备 id
 * @param launcherCode  装备编号（如 ZB-0007）
 * @param windowStart   所占空域批复时段起点（t_airspace_apply.plan_start）
 * @param windowEnd     所占空域批复时段终点（t_airspace_apply.plan_end，半开）
 * @param orderCreateTime 指令创建时刻（同一钟头被多张单子覆盖时，先开单的占格，用它打破并列）
 */
public record OrderOccupancy(Long orderId,
                             String orderNo,
                             Long applyId,
                             String applyNo,
                             Long launcherId,
                             String launcherCode,
                             LocalDateTime windowStart,
                             LocalDateTime windowEnd,
                             LocalDateTime orderCreateTime) {
}
