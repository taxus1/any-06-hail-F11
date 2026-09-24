package com.somepro.infrastructure.persistence.hail.converter;

import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.OrderStatus;
import com.somepro.infrastructure.persistence.hail.po.FireOrderPO;

/**
 * FireOrderPO（表）↔ FireOrder（领域）转换器（基础设施层）。
 * status 存枚举名；批次不在本表（落在 t_ammo_record 流水），无需搬运。
 */
public final class FireOrderPoConverter {

    private FireOrderPoConverter() {
    }

    public static FireOrderPO toPo(FireOrder d) {
        FireOrderPO po = new FireOrderPO();
        po.setId(d.getId());
        po.setOrderNo(d.getOrderNo());
        po.setApplyId(d.getApplyId());
        po.setSiteId(d.getSiteId());
        po.setLauncherId(d.getLauncherId());
        po.setAmmoType(d.getAmmoType());
        po.setPlanRounds(d.getPlanRounds());
        po.setUsedRounds(d.getUsedRounds());
        po.setStatus(d.getStatus() == null ? null : d.getStatus().name());
        po.setStartTime(d.getStartTime());
        po.setEndTime(d.getEndTime());
        po.setVoidReason(d.getVoidReason());
        po.setDelFlag(d.getDelFlag());
        po.setCreateBy(d.getCreateBy());
        po.setCreateTime(d.getCreateTime());
        po.setUpdateBy(d.getUpdateBy());
        po.setUpdateTime(d.getUpdateTime());
        return po;
    }

    public static FireOrder toDomain(FireOrderPO po) {
        FireOrder d = new FireOrder();
        d.setId(po.getId());
        d.setOrderNo(po.getOrderNo());
        d.setApplyId(po.getApplyId());
        d.setSiteId(po.getSiteId());
        d.setLauncherId(po.getLauncherId());
        d.setAmmoType(po.getAmmoType());
        d.setPlanRounds(po.getPlanRounds());
        d.setUsedRounds(po.getUsedRounds());
        d.setStatus(po.getStatus() == null ? null : OrderStatus.valueOf(po.getStatus()));
        d.setStartTime(po.getStartTime());
        d.setEndTime(po.getEndTime());
        d.setVoidReason(po.getVoidReason());
        d.setDelFlag(po.getDelFlag());
        d.setCreateBy(po.getCreateBy());
        d.setCreateTime(po.getCreateTime());
        d.setUpdateBy(po.getUpdateBy());
        d.setUpdateTime(po.getUpdateTime());
        return d;
    }
}
