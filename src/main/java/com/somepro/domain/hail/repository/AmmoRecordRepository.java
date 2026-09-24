package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 弹药出入库流水仓储端口（领域层定义，基础设施层实现）。
 *
 * 流水只追加不改写；多写事务（开单 OUT、回报 RETURN、入库 IN）由
 * {@code HailOperationFlowPort} 在一个事务里和库存变动一起落，不走本端口 save。
 * 本端口只负责查询 / 分页翻看流水账。
 */
public interface AmmoRecordRepository {

    Mono<AmmoRecord> save(AmmoRecord record);

    /**
     * 查某条指令在某作业点领用（OUT）的那笔流水，回报退弹时据此找回领用批次。
     * 没有返回空信号。
     */
    Mono<AmmoRecord> findOutRecord(Long siteId, String orderNo);

    /**
     * 分页翻看流水，可按作业点、弹型、业务类型过滤（按 id 倒序，新流水在前）。
     *
     * @param bizType 业务类型（枚举名），null 表示不限；非法值由调用方先收敛
     */
    Mono<PageResult<AmmoRecord>> page(int pageNum, int pageSize,
                                      Long siteId, String ammoType, String bizType);
}
