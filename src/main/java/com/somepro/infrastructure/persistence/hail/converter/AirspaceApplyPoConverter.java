package com.somepro.infrastructure.persistence.hail.converter;

import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.model.AirspaceStatus;
import com.somepro.domain.hail.model.Purpose;
import com.somepro.infrastructure.persistence.hail.po.AirspaceApplyPO;

/**
 * AirspaceApplyPO（表）↔ AirspaceApply（领域）转换器（基础设施层）。
 * purpose / status 两列存枚举名，这里负责与领域枚举互转。
 */
public final class AirspaceApplyPoConverter {

    private AirspaceApplyPoConverter() {
    }

    public static AirspaceApplyPO toPo(AirspaceApply d) {
        AirspaceApplyPO po = new AirspaceApplyPO();
        po.setId(d.getId());
        po.setApplyNo(d.getApplyNo());
        po.setSiteId(d.getSiteId());
        po.setPurpose(d.getPurpose() == null ? null : d.getPurpose().name());
        po.setPlanStart(d.getPlanStart());
        po.setPlanEnd(d.getPlanEnd());
        po.setMaxAltitude(d.getMaxAltitude());
        po.setStatus(d.getStatus() == null ? null : d.getStatus().name());
        po.setRejectReason(d.getRejectReason());
        po.setApproveTime(d.getApproveTime());
        po.setDelFlag(d.getDelFlag());
        po.setCreateBy(d.getCreateBy());
        po.setCreateTime(d.getCreateTime());
        po.setUpdateBy(d.getUpdateBy());
        po.setUpdateTime(d.getUpdateTime());
        return po;
    }

    public static AirspaceApply toDomain(AirspaceApplyPO po) {
        AirspaceApply d = new AirspaceApply();
        d.setId(po.getId());
        d.setApplyNo(po.getApplyNo());
        d.setSiteId(po.getSiteId());
        d.setPurpose(po.getPurpose() == null ? null : Purpose.valueOf(po.getPurpose()));
        d.setPlanStart(po.getPlanStart());
        d.setPlanEnd(po.getPlanEnd());
        d.setMaxAltitude(po.getMaxAltitude());
        d.setStatus(po.getStatus() == null ? null : AirspaceStatus.valueOf(po.getStatus()));
        d.setRejectReason(po.getRejectReason());
        d.setApproveTime(po.getApproveTime());
        d.setDelFlag(po.getDelFlag());
        d.setCreateBy(po.getCreateBy());
        d.setCreateTime(po.getCreateTime());
        d.setUpdateBy(po.getUpdateBy());
        d.setUpdateTime(po.getUpdateTime());
        return d;
    }
}
