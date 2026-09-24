package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发射装备对外对象（VO，用户接口层，不可变 record）。
 * 带作业点 id，便于前端关联到点位；不含 delFlag / 审计人等内部字段。
 */
public record LauncherVO(Long id,
                         String launcherCode,
                         Long siteId,
                         String model,
                         Integer barrelCount,
                         String status,
                         LocalDate checkDate,
                         LocalDateTime createTime) implements Serializable {
}
