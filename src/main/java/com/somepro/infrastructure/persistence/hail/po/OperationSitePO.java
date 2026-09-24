package com.somepro.infrastructure.persistence.hail.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

/**
 * t_operation_site 表的持久化对象（PO，基础设施层）。
 *
 * 字段与列一一对应，不放业务规则（规则在领域对象 OperationSite）。
 * ID 策略 IdType.INPUT：应用层雪花分配。
 */
@Getter
@Setter
@TableName("t_operation_site")
public class OperationSitePO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("site_code")
    private String siteCode;

    @TableField("site_name")
    private String siteName;

    @TableField("county")
    private String county;

    @TableField("altitude_m")
    private Integer altitudeM;

    @TableField("contact_name")
    private String contactName;

    @TableField("contact_phone")
    private String contactPhone;

    /** ACTIVE / SUSPENDED / CLOSED，存枚举名。 */
    @TableField("status")
    private String status;
}
