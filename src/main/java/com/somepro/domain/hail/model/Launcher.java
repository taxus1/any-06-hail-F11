package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 发射装备台账聚合根（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 LauncherPO）。
 *
 * 关键不变量：
 * - 装备编号（launcherCode，如 ZB-0007）全局唯一且非空，库表 uk_launcher_code 兜底。
 * - 一台装备必须挂在某个作业点上（siteId 非空），不允许「没主的装备」；
 *   该作业点必须真实存在，由应用层查作业点仓储确认（表里可能有早先数据，以库为准）。
 * - 管数给了就必须为正。
 * - 状态只能取 {@link LauncherStatus} 四个值。
 */
@Getter
@Setter
public class Launcher extends BaseEntity {

    private Long id;

    /** 装备编号，全局唯一，如 ZB-0007。 */
    private String launcherCode;

    /** 归属作业点 id（t_operation_site.id），必填。 */
    private Long siteId;

    /** 装备型号。 */
    private String model;

    /** 发射管数。 */
    private Integer barrelCount;

    /** 待命 READY / 作业中 IN_USE / 检修 MAINTENANCE / 退役 RETIRED。 */
    private LauncherStatus status;

    /** 最近检验日期。 */
    private LocalDate checkDate;

    /** 工厂方法：登记一台新装备，归属作业点必填。 */
    public static Launcher register(String launcherCode, Long siteId, String model,
                                    Integer barrelCount, String statusRaw, LocalDate checkDate) {
        Launcher launcher = new Launcher();
        launcher.changeCode(launcherCode);
        launcher.assignTo(siteId);
        launcher.setModel(model == null ? null : model.trim());
        launcher.changeBarrelCount(barrelCount);
        launcher.changeStatus(statusRaw);
        launcher.setCheckDate(checkDate);
        return launcher;
    }

    /** 领域行为：改编号并校验非空（唯一性由仓储 + 唯一索引保证）。 */
    public void changeCode(String launcherCode) {
        if (launcherCode == null || launcherCode.isBlank()) {
            throw new BizException("装备编号不能为空");
        }
        this.launcherCode = launcherCode.trim();
    }

    /** 领域行为：装备必须挂在某个作业点上，不允许没主。 */
    public void assignTo(Long siteId) {
        if (siteId == null) {
            throw new BizException("装备必须归属一个作业点，siteId 不能为空");
        }
        this.siteId = siteId;
    }

    /** 领域行为：管数为可选项，给了就必须为正。 */
    public void changeBarrelCount(Integer barrelCount) {
        if (barrelCount != null && barrelCount <= 0) {
            throw new BizException("发射管数必须为正整数");
        }
        this.barrelCount = barrelCount;
    }

    /** 领域行为：状态切换，非法值直接拒。 */
    public void changeStatus(String statusRaw) {
        this.status = LauncherStatus.of(statusRaw);
    }
}
