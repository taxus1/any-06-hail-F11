package com.somepro.infrastructure.persistence.hail.po;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 占用查法的联表只读投影 PO（基础设施层）。
 *
 * 不是一张表的形状：由 t_fire_order 关联 t_airspace_apply / t_launcher 查出，
 * 用下划线列名 + map-underscore-to-camel-case 自动映射，不带 @TableName、不走逻辑删除。
 */
@Getter
@Setter
public class OrderOccupancyPO {

    private Long orderId;
    private String orderNo;
    private Long applyId;
    private String applyNo;
    private Long launcherId;
    private String launcherCode;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private LocalDateTime orderCreateTime;
}
