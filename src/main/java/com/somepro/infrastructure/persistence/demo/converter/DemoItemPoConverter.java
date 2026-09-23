package com.somepro.infrastructure.persistence.demo.converter;

import com.somepro.domain.demo.model.DemoItem;
import com.somepro.infrastructure.persistence.demo.po.DemoItemPO;

/**
 * DemoItemPO（表）↔ DemoItem（领域）转换器（基础设施层）。
 *
 * 这是 PO 与领域模型之间**唯一**的转换入口：仓储适配器进去转 PO 落库、出来转回领域对象，
 * 领域层和接口层都不应看到 DemoItemPO。
 *
 * 注意审计字段（createBy/createTime/updateBy/updateTime）与 delFlag 也一并搬运：
 * - 新建时领域对象这些字段为 null，落库由 MetaObjectHandler 填充，回写后领域对象才拿得到；
 * - 更新时把已有值带过去，配合 @TableLogic 保证只改未删除的行。
 */
public final class DemoItemPoConverter {

    private DemoItemPoConverter() {
    }

    public static DemoItemPO toPo(DemoItem domain) {
        DemoItemPO po = new DemoItemPO();
        po.setId(domain.getId());
        po.setName(domain.getName());
        po.setScore(domain.getScore());
        po.setDelFlag(domain.getDelFlag());
        po.setCreateBy(domain.getCreateBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateBy(domain.getUpdateBy());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static DemoItem toDomain(DemoItemPO po) {
        DemoItem domain = new DemoItem();
        domain.setId(po.getId());
        domain.setName(po.getName());
        domain.setScore(po.getScore());
        domain.setDelFlag(po.getDelFlag());
        domain.setCreateBy(po.getCreateBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateBy(po.getUpdateBy());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
