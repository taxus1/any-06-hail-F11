package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 作业点某日 24 个钟头占用一览（VO，不可变 record）。
 * 空格子也一个不落地返回（occupied=false），一共 24 格，按时间正序。
 */
public record SiteDayOccupancyVO(Long siteId,
                                 String siteCode,
                                 LocalDate day,
                                 List<OccupancySlotVO> slots) implements Serializable {
}
