package com.somepro.application.hail.port;

import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.model.EffectReport;
import com.somepro.domain.hail.model.FireOrder;
import reactor.core.publisher.Mono;

/**
 * 作业主线跨表事务编排端口（应用层定义，基础设施层实现）。
 *
 * 「开单划弹、回报退弹、入库记账」都是一次业务动作写多张表，必须落在同一个数据库事务里，
 * 否则会出现「指令开了但库存没划走」「退弹回了但流水没记」的账实不一致。
 *
 * 实现方（HailOperationFlowPortAdapter）只负责把这些多写包进一个 @Transactional 阻塞方法
 * 再经 blocking(...) 桥接到响应式链路；业务校验在应用层与领域对象上完成。
 */
public interface HailOperationFlowPort {

    /**
     * 入库事务：同点 + 同弹型 + 同批次累加（或新建）库存，同时落一笔 IN 入库流水。
     * 返回落库后的最新库存。
     */
    Mono<AmmoStock> inboundWithRecord(AmmoStock incoming);

    /**
     * 开单事务（同生共死的四步）：
     * 先对作业点行加锁把同一作业点的开单串行化（同一瞬间抢同一段时辰只成一家），
     * 锁内复查空域已批、时段落在批复内、一空域一张未完结单、同点时段不撞车，
     * 再原子划出计划发数（结存不足整笔回滚）→ 记一笔 OUT 领用流水（带批次、ref=指令编号）
     * → 执行装备置作业中 → 插入作业指令。
     *
     * @param order    已由领域工厂组装、尚未落库的指令（id 为空、状态 ISSUED，带作业时段）
     * @param batchNo  出弹批次（t_fire_order 无批次列，批次记在 OUT 流水上）
     */
    Mono<FireOrder> issueOrder(FireOrder order, String batchNo);

    /**
     * 作废事务（发都没打的单子收摊）：
     * 条件更新把指令置 VOID（只有 ISSUED / EXECUTING 翻得动，翻不动说明并发已抢先）
     * → 计划发数原封退回结存并记 RETURN 流水 → 装备回待命。
     * 已被并发作废的，直接返回库里现状（幂等，绝不再退一遍弹）。
     *
     * @param order   已经过领域行为置 VOID 的指令
     * @param batchNo 当初领用的批次（从该指令 OUT 流水上取）
     */
    Mono<FireOrder> voidOrder(FireOrder order, String batchNo);

    /**
     * 回报事务：指令已由领域行为算出实际 / 退回发数并置 DONE ——
     * 退回的弹原子加回结存并记 RETURN 流水（有退弹才记）→ 装备回待命 → 条件更新指令（防重复回报）。
     *
     * @param order   已完成回报状态变更的指令
     * @param batchNo 当初领用的批次（从该指令 OUT 流水上取）
     */
    Mono<FireOrder> reportFire(FireOrder order, String batchNo);

    /**
     * 效果上报事务：插入效果上报（一条指令一份，uk_order 兜底，冲突抛 DuplicateKeyException）。
     */
    Mono<EffectReport> submitReport(EffectReport report);
}
