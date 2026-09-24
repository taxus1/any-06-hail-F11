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
 * t_ammo_stock 表的持久化对象（PO，基础设施层）。
 *
 * 字段与列一一对应，不放业务规则（累加规则在领域对象 AmmoStock、并发安全由仓储 SQL 保证）。
 */
@Getter
@Setter
@TableName("t_ammo_stock")
public class AmmoStockPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("site_id")
    private Long siteId;

    @TableField("ammo_type")
    private String ammoType;

    @TableField("batch_no")
    private String batchNo;

    @TableField("quantity")
    private Integer quantity;

    @TableField("produce_date")
    private LocalDate produceDate;

    @TableField("expire_date")
    private LocalDate expireDate;
}
