package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.EffectReport;
import reactor.core.publisher.Mono;

/**
 * 作业效果上报仓储端口（领域层定义，基础设施层实现）。
 *
 * 一条指令一份（uk_order）：提交由 {@code HailOperationFlowPort#submitReport} 落库，
 * 重复提交在应用层先查，并发漏网由唯一索引抛 DuplicateKeyException 兜底。
 */
public interface EffectReportRepository {

    Mono<EffectReport> save(EffectReport report);

    Mono<EffectReport> findById(Long id);

    /** 按指令 id 查效果上报；没有返回空信号。 */
    Mono<EffectReport> findByOrderId(Long orderId);
}
