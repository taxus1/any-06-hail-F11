package com.somepro.infrastructure.persistence.hail.converter;

import com.somepro.domain.hail.model.EffectReport;
import com.somepro.infrastructure.persistence.hail.po.EffectReportPO;

/**
 * EffectReportPO（表）↔ EffectReport（领域）转换器（基础设施层）。
 */
public final class EffectReportPoConverter {

    private EffectReportPoConverter() {
    }

    public static EffectReportPO toPo(EffectReport d) {
        EffectReportPO po = new EffectReportPO();
        po.setId(d.getId());
        po.setOrderId(d.getOrderId());
        po.setReportTime(d.getReportTime());
        po.setRainfallMm(d.getRainfallMm());
        po.setHailSizeMm(d.getHailSizeMm());
        po.setAreaKm2(d.getAreaKm2());
        po.setRemark(d.getRemark());
        po.setDelFlag(d.getDelFlag());
        po.setCreateBy(d.getCreateBy());
        po.setCreateTime(d.getCreateTime());
        po.setUpdateBy(d.getUpdateBy());
        po.setUpdateTime(d.getUpdateTime());
        return po;
    }

    public static EffectReport toDomain(EffectReportPO po) {
        EffectReport d = new EffectReport();
        d.setId(po.getId());
        d.setOrderId(po.getOrderId());
        d.setReportTime(po.getReportTime());
        d.setRainfallMm(po.getRainfallMm());
        d.setHailSizeMm(po.getHailSizeMm());
        d.setAreaKm2(po.getAreaKm2());
        d.setRemark(po.getRemark());
        d.setDelFlag(po.getDelFlag());
        d.setCreateBy(po.getCreateBy());
        d.setCreateTime(po.getCreateTime());
        d.setUpdateBy(po.getUpdateBy());
        d.setUpdateTime(po.getUpdateTime());
        return d;
    }
}
