package com.somepro.infrastructure.persistence.hail.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * t_launcher 表的持久化对象（PO，基础设施层）。
 *
 * 字段与列一一对应，不放业务规则（规则在领域对象 Launcher）。
 */
@Getter
@Setter
@TableName("t_launcher")
public class LauncherPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("launcher_code")
    private String launcherCode;

    @TableField("site_id")
    private Long siteId;

    @TableField("model")
    private String model;

    @TableField("barrel_count")
    private Integer barrelCount;

    /** READY / IN_USE / MAINTENANCE / RETIRED，存枚举名。 */
    @TableField("status")
    private String status;

    @TableField("check_date")
    private LocalDate checkDate;
}
