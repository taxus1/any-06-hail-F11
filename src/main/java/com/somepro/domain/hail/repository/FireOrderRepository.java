package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作业指令仓储端口（领域层定义，基础设施层实现）。
 *
 * 注意：开单（划库存 + OUT 流水 + 插指令）、回报（退弹 + RETURN 流水 + 完结指令）
 * 与作废（退全弹 + RETURN 流水 + 置 VOID）都是跨表多写，统一走
 * {@code HailOperationFlowPort} 事务编排，不经过本端口的 save，
 * 以保证「账与单发数一致」。本端口只负责单表的查、改与分页。
 */
public interface FireOrderRepository {

    Mono<FireOrder> save(FireOrder order);

    Mono<FireOrder> findById(Long id);

    Mono<FireOrder> findByNo(String orderNo);

    /** 生成某年的下一个指令编号（ZY-yyyy-NNNN），查当年最大序号 +1。 */
    Mono<String> nextOrderNo(int year);

    /**
     * 查某条空域申请下还没打完（ISSUED / EXECUTING）的那张单子：
     * 一张空域同时只能有一张没打完的单子在外面，开单前据此预检；查不到返回空信号。
     */
    Mono<FireOrder> findOpenByApplyId(Long applyId);

    /**
     * 查同一作业点里与 [start, end) 时段相撞的未完结指令（哪怕只撞一分钟也算撞，
     * 首尾相接不算）；撞了就把先开出来的那张返回，开单时好告诉人家撞的是哪一张。
     */
    Mono<FireOrder> findFirstOverlappingOpen(Long siteId, LocalDateTime start, LocalDateTime end);

    /**
     * 查某作业点在 [from, to) 内有占用时段的指令（开着的占计划时段、打完的占实际时段，
     * 作废的不占），供「按天看每钟头被谁占着」投影。
     */
    Mono<List<FireOrder>> findOccupying(Long siteId, LocalDateTime from, LocalDateTime to);

    /**
     * 分页翻看作业指令。
     *
     * @param siteId 只看某个作业点，null 表示不限
     * @param status 状态过滤（枚举名），null 表示不限；非法值由调用方先收敛
     */
    Mono<PageResult<FireOrder>> page(int pageNum, int pageSize, Long siteId, String status);
}
