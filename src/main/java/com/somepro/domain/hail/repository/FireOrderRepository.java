package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 作业指令仓储端口（领域层定义，基础设施层实现）。
 *
 * 注意：开单（划库存 + OUT 流水 + 插指令）与回报（退弹 + RETURN 流水 + 完结指令）
 * 都是跨表多写，统一走 {@code HailOperationFlowPort} 事务编排，不经过本端口的 save，
 * 以保证「账与单发数一致」。本端口只负责单表的查、改与分页。
 */
public interface FireOrderRepository {

    Mono<FireOrder> save(FireOrder order);

    Mono<FireOrder> findById(Long id);

    Mono<FireOrder> findByNo(String orderNo);

    /** 生成某年的下一个指令编号（ZY-yyyy-NNNN），查当年最大序号 +1。 */
    Mono<String> nextOrderNo(int year);

    /**
     * 分页翻看作业指令。
     *
     * @param siteId 只看某个作业点，null 表示不限
     * @param status 状态过滤（枚举名），null 表示不限；非法值由调用方先收敛
     */
    Mono<PageResult<FireOrder>> page(int pageNum, int pageSize, Long siteId, String status);
}
