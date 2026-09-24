package com.somepro.infrastructure.persistence.hail.converter;

import com.somepro.domain.hail.model.OperationSite;
import com.somepro.domain.hail.model.SiteStatus;
import com.somepro.infrastructure.persistence.hail.po.OperationSitePO;

/**
 * OperationSitePO（表）↔ OperationSite（领域）转换器（基础设施层）。
 * PO 与领域模型之间唯一的转换入口；审计字段与 delFlag 一并搬运。
 */
public final class OperationSitePoConverter {

    private OperationSitePoConverter() {
    }

    public static OperationSitePO toPo(OperationSite d) {
        OperationSitePO po = new OperationSitePO();
        po.setId(d.getId());
        po.setSiteCode(d.getSiteCode());
        po.setSiteName(d.getSiteName());
        po.setCounty(d.getCounty());
        po.setAltitudeM(d.getAltitudeM());
        po.setContactName(d.getContactName());
        po.setContactPhone(d.getContactPhone());
        po.setStatus(d.getStatus() == null ? null : d.getStatus().name());
        po.setDelFlag(d.getDelFlag());
        po.setCreateBy(d.getCreateBy());
        po.setCreateTime(d.getCreateTime());
        po.setUpdateBy(d.getUpdateBy());
        po.setUpdateTime(d.getUpdateTime());
        return po;
    }

    public static OperationSite toDomain(OperationSitePO po) {
        OperationSite d = new OperationSite();
        d.setId(po.getId());
        d.setSiteCode(po.getSiteCode());
        d.setSiteName(po.getSiteName());
        d.setCounty(po.getCounty());
        d.setAltitudeM(po.getAltitudeM());
        d.setContactName(po.getContactName());
        d.setContactPhone(po.getContactPhone());
        // 库里可能有早先数据；状态值理论上合法，非法值交由枚举收敛并暴露问题，不静默吞。
        d.setStatus(po.getStatus() == null ? null : SiteStatus.valueOf(po.getStatus()));
        d.setDelFlag(po.getDelFlag());
        d.setCreateBy(po.getCreateBy());
        d.setCreateTime(po.getCreateTime());
        d.setUpdateBy(po.getUpdateBy());
        d.setUpdateTime(po.getUpdateTime());
        return d;
    }
}
