package com.somepro.infrastructure.persistence.hail.converter;

import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.model.LauncherStatus;
import com.somepro.infrastructure.persistence.hail.po.LauncherPO;

/**
 * LauncherPO（表）↔ Launcher（领域）转换器（基础设施层）。
 */
public final class LauncherPoConverter {

    private LauncherPoConverter() {
    }

    public static LauncherPO toPo(Launcher d) {
        LauncherPO po = new LauncherPO();
        po.setId(d.getId());
        po.setLauncherCode(d.getLauncherCode());
        po.setSiteId(d.getSiteId());
        po.setModel(d.getModel());
        po.setBarrelCount(d.getBarrelCount());
        po.setStatus(d.getStatus() == null ? null : d.getStatus().name());
        po.setCheckDate(d.getCheckDate());
        po.setDelFlag(d.getDelFlag());
        po.setCreateBy(d.getCreateBy());
        po.setCreateTime(d.getCreateTime());
        po.setUpdateBy(d.getUpdateBy());
        po.setUpdateTime(d.getUpdateTime());
        return po;
    }

    public static Launcher toDomain(LauncherPO po) {
        Launcher d = new Launcher();
        d.setId(po.getId());
        d.setLauncherCode(po.getLauncherCode());
        d.setSiteId(po.getSiteId());
        d.setModel(po.getModel());
        d.setBarrelCount(po.getBarrelCount());
        d.setStatus(po.getStatus() == null ? null : LauncherStatus.valueOf(po.getStatus()));
        d.setCheckDate(po.getCheckDate());
        d.setDelFlag(po.getDelFlag());
        d.setCreateBy(po.getCreateBy());
        d.setCreateTime(po.getCreateTime());
        d.setUpdateBy(po.getUpdateBy());
        d.setUpdateTime(po.getUpdateTime());
        return d;
    }
}
