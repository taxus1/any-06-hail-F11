package com.somepro.infrastructure.persistence.demo.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

/**
 * t_demo_item 表的持久化对象（PO，基础设施层）。
 *
 * 只描述「表长什么样」：字段与列一一对应，不放任何业务规则（规则在领域对象 DemoItem）。
 * 与领域对象的互转见 DemoItemPoConverter。
 *
 * ID 策略 IdType.INPUT：由应用层用雪花算法分配后传入，与仓储适配器里的 IdUtil 一致。
 */
@Getter
@Setter
@TableName("t_demo_item")
public class DemoItemPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("score")
    private Integer score;
}
