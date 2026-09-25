package com.somepro.interfaces.rest.hail.converter;

import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.model.EffectReport;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.model.OperationSite;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.domain.hail.model.OccupancySlot;
import com.somepro.domain.hail.model.SiteDayOccupancy;
import com.somepro.interfaces.rest.hail.vo.AirspaceApplyVO;
import com.somepro.interfaces.rest.hail.vo.AmmoRecordVO;
import com.somepro.interfaces.rest.hail.vo.AmmoStockVO;
import com.somepro.interfaces.rest.hail.vo.EffectReportVO;
import com.somepro.interfaces.rest.hail.vo.FireOrderVO;
import com.somepro.interfaces.rest.hail.vo.HailPageVO;
import com.somepro.interfaces.rest.hail.vo.LauncherVO;
import com.somepro.interfaces.rest.hail.vo.OccupancySlotVO;
import com.somepro.interfaces.rest.hail.vo.OperationSiteVO;
import com.somepro.interfaces.rest.hail.vo.SiteDayOccupancyVO;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 领域对象 → 对外 VO 转换器（用户接口层）。
 *
 * Controller 不许直接把领域对象塞进 Result 返回，统一经本类转换，
 * 避免 delFlag / createBy / updateBy 等内部字段被无意识序列化出去。
 */
public final class HailVoConverter {

    private HailVoConverter() {
    }

    public static OperationSiteVO toVo(OperationSite s) {
        return new OperationSiteVO(s.getId(), s.getSiteCode(), s.getSiteName(), s.getCounty(),
                s.getAltitudeM(), s.getContactName(), s.getContactPhone(),
                s.getStatus() == null ? null : s.getStatus().name(), s.getCreateTime());
    }

    public static LauncherVO toVo(Launcher l) {
        return new LauncherVO(l.getId(), l.getLauncherCode(), l.getSiteId(), l.getModel(),
                l.getBarrelCount(), l.getStatus() == null ? null : l.getStatus().name(),
                l.getCheckDate(), l.getCreateTime());
    }

    public static AmmoStockVO toVo(AmmoStock a) {
        return new AmmoStockVO(a.getId(), a.getSiteId(), a.getAmmoType(), a.getBatchNo(),
                a.getQuantity(), a.getProduceDate(), a.getExpireDate(), a.getCreateTime());
    }

    public static AirspaceApplyVO toVo(AirspaceApply a) {
        return new AirspaceApplyVO(a.getId(), a.getApplyNo(), a.getSiteId(),
                a.getPurpose() == null ? null : a.getPurpose().name(),
                a.getPlanStart(), a.getPlanEnd(), a.getMaxAltitude(),
                a.getStatus() == null ? null : a.getStatus().name(),
                a.getRejectReason(), a.getApproveTime(), a.getCreateTime());
    }

    public static FireOrderVO toVo(FireOrder o) {
        return new FireOrderVO(o.getId(), o.getOrderNo(), o.getApplyId(), o.getSiteId(),
                o.getLauncherId(), o.getAmmoType(), o.getPlanRounds(), o.getUsedRounds(),
                o.getStatus() == null ? null : o.getStatus().name(),
                o.getStartTime(), o.getEndTime(), o.getVoidReason(), o.getCreateTime());
    }

    /** 作业点某日占用一览：领域值对象 → 对外 VO，空格子也照实带出去。 */
    public static SiteDayOccupancyVO toVo(SiteDayOccupancy day) {
        List<OccupancySlotVO> slots = day.slots().stream()
                .map(HailVoConverter::toVo)
                .collect(Collectors.toList());
        return new SiteDayOccupancyVO(day.siteId(), day.siteCode(), day.day(), slots);
    }

    public static OccupancySlotVO toVo(OccupancySlot s) {
        return new OccupancySlotVO(s.slotStart(), s.slotEnd(), s.occupied(),
                s.applyNo(), s.launcherCode(), s.orderNo());
    }

    public static AmmoRecordVO toVo(AmmoRecord r) {
        return new AmmoRecordVO(r.getId(), r.getSiteId(), r.getAmmoType(), r.getBatchNo(),
                r.getChangeQty(), r.getBizType() == null ? null : r.getBizType().name(),
                r.getRefNo(), r.getCreateTime());
    }

    public static EffectReportVO toVo(EffectReport r) {
        return new EffectReportVO(r.getId(), r.getOrderId(), r.getReportTime(),
                r.getRainfallMm(), r.getHailSizeMm(), r.getAreaKm2(), r.getRemark(),
                r.getCreateTime());
    }

    /** 领域分页结果 → 对外分页 VO 的通用转换。 */
    public static <D, V> HailPageVO<V> toPageVo(PageResult<D> page, Function<D, V> mapper) {
        List<V> content = page.content().stream().map(mapper).collect(Collectors.toList());
        return new HailPageVO<>(content, page.total(), page.pageNum(), page.pageSize(),
                page.totalPages());
    }
}
