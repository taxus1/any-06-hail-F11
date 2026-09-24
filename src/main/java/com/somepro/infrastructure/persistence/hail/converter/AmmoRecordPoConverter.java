package com.somepro.infrastructure.persistence.hail.converter;

import com.somepro.domain.hail.model.AmmoBizType;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.infrastructure.persistence.hail.po.AmmoRecordPO;

/**
 * AmmoRecordPO（表）↔ AmmoRecord（领域）转换器（基础设施层）。
 * change_qty 正入负出，biz_type 存枚举名。
 */
public final class AmmoRecordPoConverter {

    private AmmoRecordPoConverter() {
    }

    public static AmmoRecordPO toPo(AmmoRecord d) {
        AmmoRecordPO po = new AmmoRecordPO();
        po.setId(d.getId());
        po.setSiteId(d.getSiteId());
        po.setAmmoType(d.getAmmoType());
        po.setBatchNo(d.getBatchNo());
        po.setChangeQty(d.getChangeQty());
        po.setBizType(d.getBizType() == null ? null : d.getBizType().name());
        po.setRefNo(d.getRefNo());
        po.setDelFlag(d.getDelFlag());
        po.setCreateBy(d.getCreateBy());
        po.setCreateTime(d.getCreateTime());
        po.setUpdateBy(d.getUpdateBy());
        po.setUpdateTime(d.getUpdateTime());
        return po;
    }

    public static AmmoRecord toDomain(AmmoRecordPO po) {
        AmmoRecord d = new AmmoRecord();
        d.setId(po.getId());
        d.setSiteId(po.getSiteId());
        d.setAmmoType(po.getAmmoType());
        d.setBatchNo(po.getBatchNo());
        d.setChangeQty(po.getChangeQty());
        d.setBizType(po.getBizType() == null ? null : AmmoBizType.valueOf(po.getBizType()));
        d.setRefNo(po.getRefNo());
        d.setDelFlag(po.getDelFlag());
        d.setCreateBy(po.getCreateBy());
        d.setCreateTime(po.getCreateTime());
        d.setUpdateBy(po.getUpdateBy());
        d.setUpdateTime(po.getUpdateTime());
        return d;
    }
}
