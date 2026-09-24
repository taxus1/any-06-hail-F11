package com.somepro.infrastructure.persistence.hail.converter;

import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.infrastructure.persistence.hail.po.AmmoStockPO;

/**
 * AmmoStockPO（表）↔ AmmoStock（领域）转换器（基础设施层）。
 * 库存没有状态枚举，日期直接搬运。
 */
public final class AmmoStockPoConverter {

    private AmmoStockPoConverter() {
    }

    public static AmmoStockPO toPo(AmmoStock d) {
        AmmoStockPO po = new AmmoStockPO();
        po.setId(d.getId());
        po.setSiteId(d.getSiteId());
        po.setAmmoType(d.getAmmoType());
        po.setBatchNo(d.getBatchNo());
        po.setQuantity(d.getQuantity());
        po.setProduceDate(d.getProduceDate());
        po.setExpireDate(d.getExpireDate());
        po.setDelFlag(d.getDelFlag());
        po.setCreateBy(d.getCreateBy());
        po.setCreateTime(d.getCreateTime());
        po.setUpdateBy(d.getUpdateBy());
        po.setUpdateTime(d.getUpdateTime());
        return po;
    }

    public static AmmoStock toDomain(AmmoStockPO po) {
        AmmoStock d = new AmmoStock();
        d.setId(po.getId());
        d.setSiteId(po.getSiteId());
        d.setAmmoType(po.getAmmoType());
        d.setBatchNo(po.getBatchNo());
        d.setQuantity(po.getQuantity());
        d.setProduceDate(po.getProduceDate());
        d.setExpireDate(po.getExpireDate());
        d.setDelFlag(po.getDelFlag());
        d.setCreateBy(po.getCreateBy());
        d.setCreateTime(po.getCreateTime());
        d.setUpdateBy(po.getUpdateBy());
        d.setUpdateTime(po.getUpdateTime());
        return d;
    }
}
