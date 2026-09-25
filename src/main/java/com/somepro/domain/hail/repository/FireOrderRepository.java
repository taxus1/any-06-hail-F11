package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.OrderOccupancy;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 查某作业点在给定区间内「占着时段」的在途指令（ISSUED / EXECUTING）占用投影。
     * 时段用的是单子所挂空域的批复时段：与 [from, to) 有重叠（哪怕一分钟）就算命中。
     * 按开单先后（create_time、id）正序返回，调用方落格时先开单的先占。
     */
    Mono<List<OrderOccupancy>> findActiveOccupancies(Long siteId,
                                                     LocalDateTime from,
                                                     LocalDateTime to);
}
