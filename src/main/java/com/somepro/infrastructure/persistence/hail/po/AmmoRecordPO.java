package com.somepro.infrastructure.persistence.hail.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

/**
 * t_ammo_record 表的持久化对象（PO，基础设施层）。
 *
 * 字段与列一一对应。流水只追加不改写；change_qty 正入负出。
 */
@Getter
@Setter
@TableName("t_ammo_record")
public class AmmoRecordPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("site_id")
    private Long siteId;

    @TableField("ammo_type")
    private String ammoType;

    @TableField("batch_no")
    private String batchNo;

    /** 变动发数：正入负出。 */
    @TableField("change_qty")
    private Integer changeQty;

    /** IN / OUT / RETURN / SCRAP，存枚举名。 */
    @TableField("biz_type")
    private String bizType;

    @TableField("ref_no")
    private String refNo;
}
