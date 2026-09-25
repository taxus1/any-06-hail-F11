package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 一个作业点某一天里一个小时槽的占用情况（对外 VO，用户接口层，不可变 record）。
 *
 * 一天固定 24 槽；没被占上的槽 occupied=false、occupants 为空 —— 即「标个空」。
 */
public record SiteHourSlotVO(int hour,
                             String slot,
                             boolean occupied,
                             List<HourOccupantVO> occupants) implements Serializable {
}
