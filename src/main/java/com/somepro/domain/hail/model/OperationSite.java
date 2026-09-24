package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 作业点档案聚合根（防雹增雨限界上下文）。
 *
 * 纯领域对象：只描述业务与不变量，不带任何持久化注解（表映射在基础设施层的 OperationSitePO）。
 *
 * 关键不变量：
 * - 编号（siteCode，如 YY-013）全局唯一且不能为空。唯一性在应用层 + 库表 uk_site_code 双保险，
 *   表里可能已有早先录入的数据，所以查重必须走库，不能只看本次会话。
 * - 名称（siteName）不能为空。
 * - 状态只能取 {@link SiteStatus} 三个值。
 */
@Getter
@Setter
public class OperationSite extends BaseEntity {

    private Long id;

    /** 作业点编号，全局唯一，如 YY-013。 */
    private String siteCode;

    /** 作业点名称。 */
    private String siteName;

    /** 所属县区。 */
    private String county;

    /** 海拔（米）。 */
    private Integer altitudeM;

    /** 值守人姓名。 */
    private String contactName;

    /** 值守人电话。 */
    private String contactPhone;

    /** 在册 ACTIVE / 封存 SUSPENDED / 撤销 CLOSED。 */
    private SiteStatus status;

    /** 工厂方法：登记一个新作业点，集中保证初始不变量。 */
    public static OperationSite register(String siteCode, String siteName, String county,
                                         Integer altitudeM, String contactName, String contactPhone,
                                         String statusRaw) {
        OperationSite site = new OperationSite();
        site.changeCode(siteCode);
        site.rename(siteName);
        site.setCounty(trimToNull(county));
        site.changeAltitude(altitudeM);
        site.setContactName(trimToNull(contactName));
        site.setContactPhone(trimToNull(contactPhone));
        site.changeStatus(statusRaw);
        return site;
    }

    /** 领域行为：改编号并校验非空（唯一性由仓储 + 唯一索引保证）。 */
    public void changeCode(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) {
            throw new BizException("作业点编号不能为空");
        }
        this.siteCode = siteCode.trim();
    }

    /** 领域行为：改名并校验非空。 */
    public void rename(String siteName) {
        if (siteName == null || siteName.isBlank()) {
            throw new BizException("作业点名称不能为空");
        }
        this.siteName = siteName.trim();
    }

    /** 领域行为：海拔为可选项，给了就不能是负数。 */
    public void changeAltitude(Integer altitudeM) {
        if (altitudeM != null && altitudeM < 0) {
            throw new BizException("海拔不能为负数");
        }
        this.altitudeM = altitudeM;
    }

    /** 领域行为：状态切换，非法值直接拒。 */
    public void changeStatus(String statusRaw) {
        this.status = SiteStatus.of(statusRaw);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
