package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 弹药库存仓储端口（领域层定义，基础设施层实现）。
 *
 * 同点 + 同弹型 + 同批次的库存只有一条，再次入库走 {@link #inbound}，由其内部决定新增还是累加。
 */
public interface AmmoStockRepository {

    /** 按「作业点 + 弹型 + 批次」查当前未删除的库存；没有则返回空信号。 */
    Mono<AmmoStock> findUnique(Long siteId, String ammoType, String batchNo);

    Mono<AmmoStock> findById(Long id);

    /**
     * 入库（幂等累加语义）：
     * 传入的是一条经领域校验、尚未落库（id 为空）的库存对象，其 quantity 即本次入库发数。
     * 已有同点 + 同弹型 + 同批次记录就在原记录上原子累加发数，没有则新建一条；
     * 并发下撞唯一索引时退化为累加，绝不另起第二条。返回落库后的最新库存。
     */
    Mono<AmmoStock> inbound(AmmoStock incoming);

    /**
     * 分页翻看库存。
     *
     * @param siteId   只看某个作业点，null 表示不限
     * @param ammoType 弹型精确过滤，null 表示不限
     * @param batchNo  批次号模糊匹配，null 表示不限
     */
    Mono<PageResult<AmmoStock>> page(int pageNum, int pageSize, Long siteId, String ammoType, String batchNo);
}
