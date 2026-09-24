package com.somepro.application.hail;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.repository.LauncherRepository;
import com.somepro.domain.hail.repository.OperationSiteRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * 发射装备应用服务：编排「登记、翻看」用例（应用层）。
 *
 * 两条跨聚合规则在应用层落：
 * - 装备不能没主：siteId 必填且必须能在作业点档案里查到（含早先数据，以库为准）。
 * - 装备编号全局唯一：先查库判重，并发漏网由 uk_launcher_code + DuplicateKeyException 兜底。
 */
@Service
public class LauncherAppService {

    private final LauncherRepository launcherRepository;
    private final OperationSiteRepository siteRepository;

    public LauncherAppService(LauncherRepository launcherRepository,
                              OperationSiteRepository siteRepository) {
        this.launcherRepository = launcherRepository;
        this.siteRepository = siteRepository;
    }

    /** 登记一台装备，必须挂在一个真实存在的作业点上。 */
    public Mono<Launcher> register(String launcherCode, Long siteId, String model,
                                   Integer barrelCount, String status, LocalDate checkDate) {
        Launcher launcher = Launcher.register(
                launcherCode, siteId, model, barrelCount, status, checkDate);
        // 装备总得有主：归属作业点必须存在（findById 自带 del_flag = 0）
        return siteRepository.findById(launcher.getSiteId())
                .switchIfEmpty(Mono.error(new BizException(
                        "归属作业点不存在：siteId=" + launcher.getSiteId())))
                // 编号查重查库，台账里可能有早先录入的装备
                .flatMap(site -> launcherRepository.findByCode(launcher.getLauncherCode())
                        .flatMap(existing -> Mono.<Launcher>error(
                                new BizException("装备编号已存在：" + existing.getLauncherCode())))
                        .switchIfEmpty(Mono.defer(() -> launcherRepository.save(launcher))))
                .onErrorResume(DuplicateKeyException.class, e ->
                        Mono.error(new BizException("装备编号已存在：" + launcher.getLauncherCode())));
    }

    public Mono<Launcher> getById(Long id) {
        return launcherRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("装备不存在：id=" + id)));
    }

    /** 分页翻看装备，可按作业点、状态过滤；状态给了就必须是合法枚举。 */
    public Mono<PageResult<Launcher>> page(int pageNum, int pageSize,
                                           Long siteId, String status, String keyword) {
        // 提前收敛非法状态，避免静默查成空页让人摸不着头脑；of 会对非法值抛 BizException
        String normalized = null;
        if (status != null && !status.isBlank()) {
            normalized = com.somepro.domain.hail.model.LauncherStatus.of(status).name();
        }
        return launcherRepository.page(pageNum, pageSize, siteId, normalized, keyword);
    }
}
