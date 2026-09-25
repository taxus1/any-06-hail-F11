package com.somepro.domain.hail.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 作业点某一天的 24 个钟头占用一览（领域只读值对象，不可变）。
 *
 * 「挑个作业点、说个日子」的查法返回它：从 00:00 到次日 00:00 共 24 个整点格子，
 * 每个钟头标出被哪张空域、哪台装备占着；没占上的钟头也一个不落下，标空。
 *
 * @param siteId   作业点 id
 * @param siteCode 作业点编号（如 YY-013）
 * @param day      查的日子
 * @param slots    24 个钟头格子，按时间正序，固定 24 格
 */
public record SiteDayOccupancy(Long siteId,
                               String siteCode,
                               LocalDate day,
                               List<OccupancySlot> slots) {
}
