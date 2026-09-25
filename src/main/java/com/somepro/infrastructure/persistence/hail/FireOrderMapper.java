package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.FireOrderPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作业指令 Mapper（基础设施层）。
 *
 * 关键是 {@link #completeIfOpen} 与 {@link #voidIfOpen}：回报完结 / 作废都用条件更新
 * （只有没打完的单子才翻得动状态），受影响 0 行说明已被并发抢先，调用方抛错或按幂等返回，
 * 保证一条指令只回报一次、只作废退弹一次。
 *
 * 阻塞 JDBC API，只能在 boundedElastic 线程的 blocking(...) / 事务方法里调用。
 */
@Mapper
public interface FireOrderMapper extends BaseMapper<FireOrderPO> {

    /** 查某年编号前缀（如 ZY-2026-）下当前最大编号；没有数据返回 null。调用方负责转义下划线。 */
    @Select("SELECT MAX(order_no) FROM t_fire_order "
            + "WHERE order_no LIKE #{prefixPattern} ESCAPE '/' AND del_flag = 0")
    String selectMaxNoByPrefix(@Param("prefixPattern") String prefixPattern);

    /**
     * 条件完结指令：只有非 DONE / VOID 的指令能更新成功，返回受影响行数（0 表示重复回报 / 已作废）。
     * 手写 SQL 不经 MetaObjectHandler，审计字段在此显式更新。
     */
    @Update("UPDATE t_fire_order SET used_rounds = #{usedRounds}, status = 'DONE', "
            + "start_time = COALESCE(#{startTime}, start_time), "
            + "end_time = COALESCE(#{endTime}, end_time), "
            + "update_by = #{operator}, update_time = #{now} "
            + "WHERE id = #{id} AND status <> 'DONE' AND status <> 'VOID' AND del_flag = 0")
    int completeIfOpen(@Param("id") Long id,
                       @Param("usedRounds") int usedRounds,
                       @Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime,
                       @Param("operator") String operator,
                       @Param("now") LocalDateTime now);

    /**
     * 条件作废指令：只有没打完的（ISSUED / EXECUTING）能翻 VOID，返回受影响行数
     * （0 表示已被并发回报完结或已作废）。手写 SQL 不经 MetaObjectHandler，审计字段在此显式更新。
     */
    @Update("UPDATE t_fire_order SET status = 'VOID', void_reason = #{reason}, "
            + "update_by = #{operator}, update_time = #{now} "
            + "WHERE id = #{id} AND status IN ('ISSUED', 'EXECUTING') AND del_flag = 0")
    int voidIfOpen(@Param("id") Long id,
                   @Param("reason") String reason,
                   @Param("operator") String operator,
                   @Param("now") LocalDateTime now);

    /** 查某条空域申请下还没打完（ISSUED / EXECUTING）的单子；没有返回 null。 */
    @Select("SELECT * FROM t_fire_order WHERE apply_id = #{applyId} "
            + "AND status IN ('ISSUED', 'EXECUTING') AND del_flag = 0 "
            + "ORDER BY id DESC LIMIT 1")
    FireOrderPO selectOpenByApplyId(@Param("applyId") Long applyId);

    /**
     * 查同一作业点里与 [start, end) 相撞的未完结指令：严格区间相交
     * （start_time &lt; end 且 end_time &gt; start），哪怕只撞一分钟也算撞，首尾相接不算。
     */
    @Select("SELECT * FROM t_fire_order WHERE site_id = #{siteId} "
            + "AND status IN ('ISSUED', 'EXECUTING') "
            + "AND start_time IS NOT NULL AND end_time IS NOT NULL "
            + "AND start_time < #{end} AND end_time > #{start} AND del_flag = 0 "
            + "ORDER BY id LIMIT 1")
    FireOrderPO selectFirstOverlappingOpen(@Param("siteId") Long siteId,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    /**
     * 查某作业点在 [from, to) 内有占用时段的指令：开着的（ISSUED / EXECUTING）占计划时段、
     * 打完的（DONE）占实际时段，作废（VOID）的不占；供按日逐小时占用投影。
     */
    @Select("SELECT * FROM t_fire_order WHERE site_id = #{siteId} "
            + "AND status IN ('ISSUED', 'EXECUTING', 'DONE') "
            + "AND start_time IS NOT NULL AND end_time IS NOT NULL "
            + "AND start_time < #{to} AND end_time > #{from} AND del_flag = 0 "
            + "ORDER BY start_time, id")
    List<FireOrderPO> selectOccupying(@Param("siteId") Long siteId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);
}
