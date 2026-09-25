package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * 空域申请仓储端口（领域层定义，基础设施层实现）。
 *
 * 申请编号按年自增（KQ-yyyy-NNNN），{@link #nextApplyNo} 查当年最大序号 +1，
 * 并发漏网由 uk_apply_no 抛 DuplicateKeyException，应用层换号重试。
 */
public interface AirspaceApplyRepository {

    Mono<AirspaceApply> save(AirspaceApply apply);

    Mono<AirspaceApply> findById(Long id);

    Mono<AirspaceApply> findByNo(String applyNo);

    /** 按 id 批量查（占用投影拼装用）；空集合进、空列表出，不走库。 */
    Mono<List<AirspaceApply>> findByIds(Collection<Long> ids);

    /** 生成某年的下一个申请编号（KQ-yyyy-NNNN），查当年最大序号 +1。 */
    Mono<String> nextApplyNo(int year);

    /**
     * 分页翻看空域申请。
     *
     * @param siteId 只看某个作业点，null 表示不限
     * @param status 状态过滤（枚举名），null 表示不限；非法值由调用方先收敛
     */
    Mono<PageResult<AirspaceApply>> page(int pageNum, int pageSize, Long siteId, String status);
}
