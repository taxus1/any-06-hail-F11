package com.somepro.domain.demo.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * demo 限界上下文的聚合根（示例，可删）。
 *
 * 纯领域对象：只描述业务与不变量，**不带任何持久化注解**（表映射在基础设施层的 DemoItemPO）。
 * 落库与读取由 DemoItemRepositoryImpl 经 DemoItemPoConverter 转换。
 *
 * ID 由应用层用雪花算法分配（见仓储适配器），不由数据库自增。
 */
@Getter
@Setter
public class DemoItem extends BaseEntity {

    private Long id;

    private String name;

    private Integer score;

    /** 工厂方法：领域内构造聚合并保证初始不变量。 */
    public static DemoItem create(String name, Integer score) {
        DemoItem item = new DemoItem();
        item.rename(name);
        item.setScore(score);
        return item;
    }

    /** 领域行为：改名并校验不变量。 */
    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException("名称不能为空");
        }
        this.name = name.trim();
    }
}
