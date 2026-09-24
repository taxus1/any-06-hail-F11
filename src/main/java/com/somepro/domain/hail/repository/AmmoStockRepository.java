package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 弹药库存仓储端口（领域层定义，基础设施层实现）。
 *
 * 写操作（入库累加、开单划出、回报退回）都涉及「库存 + 流水」多表同事务，
 * 统一走 {@code HailOperationFlowPort}；本端口只负责读：查唯一批次与分页翻看。
 */
public interface AmmoStockRepository {

    /** 按「作业点 + 弹型 + 批次」查当前未删除的库存；没有则返回空信号。 */
    Mono<AmmoStock> findUnique(Long siteId, String ammoType, String batchNo);

    Mono<AmmoStock> findById(Long id);

    /**
     * 分页翻看库存。
     *
     * @param siteId   只看某个作业点，null 表示不限
     * @param ammoType 弹型精确过滤，null 表示不限
     * @param batchNo  批次号模糊匹配，null 表示不限
     */
    Mono<PageResult<AmmoStock>> page(int pageNum, int pageSize, Long siteId, String ammoType, String batchNo);
}
