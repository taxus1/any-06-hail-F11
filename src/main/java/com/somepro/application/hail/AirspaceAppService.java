package com.somepro.application.hail;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.model.AirspaceStatus;
import com.somepro.domain.hail.model.OperationSite;
import com.somepro.domain.hail.model.SiteStatus;
import com.somepro.domain.hail.repository.AirspaceApplyRepository;
import com.somepro.domain.hail.repository.OperationSiteRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 空域申请应用服务：编排「申报、批复（批准 / 驳回）、翻看」用例。
 *
 * - 新报上来一律 PENDING；编号 KQ-yyyy-NNNN 按年自增，并发撞号由 uk_apply_no 兜底，换号重试。
 * - 申报的作业点必须真实在册（ACTIVE）。
 * - 只有 PENDING 能批复：批准置 APPROVED 记批复时刻；驳回置 REJECTED 且原因必填（领域守卫）。
 */
@Service
public class AirspaceAppService {

    /** 并发撞号时最多换几次编号再落库。 */
    private static final int NO_RETRY_LIMIT = 3;

    private final AirspaceApplyRepository applyRepository;
    private final OperationSiteRepository siteRepository;
    private final Clock clock;

    public AirspaceAppService(AirspaceApplyRepository applyRepository,
                              OperationSiteRepository siteRepository) {
        this(applyRepository, siteRepository, Clock.systemDefaultZone());
    }

    AirspaceAppService(AirspaceApplyRepository applyRepository,
                       OperationSiteRepository siteRepository, Clock clock) {
        this.applyRepository = applyRepository;
        this.siteRepository = siteRepository;
        this.clock = clock;
    }

    /** 新报一条空域申请：校验作业点在册，分配当年序号，初始待批。 */
    public Mono<AirspaceApply> submit(Long siteId, String purpose,
                                      LocalDateTime planStart, LocalDateTime planEnd,
                                      Integer maxAltitude) {
        // 领域内容校验在查库前同步 fast-fail（编号还没取，先组装待编号的暂态申请）
        AirspaceApply draft = AirspaceApply.prepare(siteId, purpose, planStart, planEnd, maxAltitude);
        return siteRepository.findById(siteId)
                .switchIfEmpty(Mono.error(new BizException("作业点不存在：siteId=" + siteId)))
                .flatMap(this::ensureActive)
                .flatMap(site -> saveWithNo(draft));
    }

    /** 批准：只有待批件能批。 */
    public Mono<AirspaceApply> approve(Long id) {
        return loadPending(id)
                .flatMap(apply -> {
                    apply.approve(LocalDateTime.now(clock));
                    return applyRepository.save(apply);
                });
    }

    /** 驳回：只有待批件能驳，原因必须写清。 */
    public Mono<AirspaceApply> reject(Long id, String reason) {
        return loadPending(id)
                .flatMap(apply -> {
                    apply.reject(reason, LocalDateTime.now(clock));
                    return applyRepository.save(apply);
                });
    }

    public Mono<AirspaceApply> getById(Long id) {
        return applyRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("空域申请不存在：id=" + id)));
    }

    /** 分页翻看，可按作业点、状态过滤；状态给了就必须是合法枚举。 */
    public Mono<PageResult<AirspaceApply>> page(int pageNum, int pageSize, Long siteId, String status) {
        String normalized = null;
        if (status != null && !status.isBlank()) {
            normalized = AirspaceStatus.of(status).name();
        }
        return applyRepository.page(pageNum, pageSize, siteId, normalized);
    }

    private Mono<OperationSite> ensureActive(OperationSite site) {
        if (site.getStatus() != SiteStatus.ACTIVE) {
            return Mono.error(new BizException(
                    "作业点不在册（" + site.getStatus() + "），不能申报空域：" + site.getSiteCode()));
        }
        return Mono.just(site);
    }

    /** 取当年序号落库；并发撞 uk_apply_no 时换号重试，而不是把技术异常抛给调用方。 */
    private Mono<AirspaceApply> saveWithNo(AirspaceApply draft) {
        return applyRepository.nextApplyNo(LocalDateTime.now(clock).getYear())
                .flatMap(no -> {
                    draft.changeNo(no);
                    return saveOne(draft, 1);
                });
    }

    private Mono<AirspaceApply> saveOne(AirspaceApply draft, int attempt) {
        return applyRepository.save(draft)
                .onErrorResume(DuplicateKeyException.class, e -> {
                    if (attempt >= NO_RETRY_LIMIT) {
                        return Mono.error(new BizException("空域申请编号冲突，请稍后重试：" + draft.getApplyNo()));
                    }
                    return applyRepository.nextApplyNo(LocalDateTime.now(clock).getYear())
                            .flatMap(nextNo -> {
                                draft.changeNo(nextNo);
                                return saveOne(draft, attempt + 1);
                            });
                });
    }

    private Mono<AirspaceApply> loadPending(Long id) {
        return getById(id)
                .handle((apply, sink) -> {
                    if (apply.getStatus() != AirspaceStatus.PENDING) {
                        sink.error(new BizException(
                                "只有待批的空域申请能批复，当前状态：" + apply.getStatus()));
                        return;
                    }
                    sink.next(apply);
                });
    }
}
