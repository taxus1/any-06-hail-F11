package com.somepro.domain.hail.model;

import java.util.List;

/**
 * 一个作业点某一天里的一个小时槽（领域值对象，只读视图）。
 *
 * 一天固定 24 槽，hour 取 0~23，表示 [hour:00, hour+1:00) 这段时辰；
 * 没被占上的槽 occupants 为空（对外标「空」），一槽被多张单占着时全列出。
 */
public record SiteHourSlot(

        /** 小时，0~23。 */
        int hour,

        /** 这一小时里的占用明细；空 = 没被占上。 */
        List<HourOccupant> occupants) {

    public SiteHourSlot {
        occupants = occupants == null ? List.of() : List.copyOf(occupants);
    }

    /** 这一小时有没有被占上。 */
    public boolean occupied() {
        return !occupants.isEmpty();
    }
}
