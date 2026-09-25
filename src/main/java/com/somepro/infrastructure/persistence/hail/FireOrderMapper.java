package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.FireOrderPO;
import com.somepro.infrastructure.persistence.hail.po.OrderOccupancyPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作业指令 Mapper（基础设施层）。
 *
 * 关键是 {@link #completeIfOpen}：回报完结用条件更新（非 DONE / VOID 才能更新成功），
 * 受影响 0 行说明已被并发回报 / 已作废，调用方抛错回滚退弹，保证一条指令只回报一次。
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
     * 开单冲突排查（调用方必须已先锁住作业点行，把同点并发开单串行化）：
     * 查该作业点所有没完结（ISSUED / EXECUTING）单子里，所占空域时段与 [winStart, winEnd) 有重叠、
     * 或挂在同一条空域申请上的单子，按开单先后（create_time、id）取最早的一张。
     * 时段用空域批复时段（join t_airspace_apply）：哪怕只撞一分钟也算撞；
     * 同一条空域申请即使时段不撞也返回（一张空域同时只允许一张没打完的单子在外面）。
     */
    @Select("SELECT o.* FROM t_fire_order o "
            + "JOIN t_airspace_apply a ON a.id = o.apply_id AND a.del_flag = 0 "
            + "WHERE o.site_id = #{siteId} AND o.del_flag = 0 "
            + "AND o.status IN ('ISSUED', 'EXECUTING') "
            + "AND (o.apply_id = #{applyId} "
            + "     OR (a.plan_start < #{winEnd} AND a.plan_end > #{winStart})) "
            + "ORDER BY o.create_time ASC, o.id ASC LIMIT 1")
    FireOrderPO selectOpenBlocker(@Param("siteId") Long siteId,
                                  @Param("applyId") Long applyId,
                                  @Param("winStart") LocalDateTime winStart,
                                  @Param("winEnd") LocalDateTime winEnd);

    /**
     * 条件作废：只有没完结（ISSUED / EXECUTING）且一发都没打（used_rounds = 0）的单子更新得动。
     * 返回 1 = 本次真的作废（调用方接着退弹、腾装备）；0 = 已作废 / 已回报 / 已打过弹，
     * 调用方整笔回滚，退弹绝不发生 —— 连点两遍作废也只能退一次弹。
     */
    @Update("UPDATE t_fire_order SET status = 'VOID', void_reason = #{reason}, "
            + "update_by = #{operator}, update_time = #{now} "
            + "WHERE id = #{id} AND status IN ('ISSUED', 'EXECUTING') "
            + "AND used_rounds = 0 AND del_flag = 0")
    int voidIfFresh(@Param("id") Long id,
                    @Param("reason") String reason,
                    @Param("operator") String operator,
                    @Param("now") LocalDateTime now);

    /**
     * 占用查法：某作业点与 [dayStart, nextDayStart) 有交集的在途单子（ISSUED / EXECUTING），
     * 带上所挂空域编号 / 批复时段与装备编号，按开单先后正序（先开单的先占格）。
     */
    @Select("SELECT o.id AS order_id, o.order_no AS order_no, o.apply_id AS apply_id, "
            + "a.apply_no AS apply_no, o.launcher_id AS launcher_id, l.launcher_code AS launcher_code, "
            + "a.plan_start AS window_start, a.plan_end AS window_end, o.create_time AS order_create_time "
            + "FROM t_fire_order o "
            + "JOIN t_airspace_apply a ON a.id = o.apply_id AND a.del_flag = 0 "
            + "LEFT JOIN t_launcher l ON l.id = o.launcher_id AND l.del_flag = 0 "
            + "WHERE o.site_id = #{siteId} AND o.del_flag = 0 "
            + "AND o.status IN ('ISSUED', 'EXECUTING') "
            + "AND a.plan_start < #{dayEnd} AND a.plan_end > #{dayStart} "
            + "ORDER BY o.create_time ASC, o.id ASC")
    List<OrderOccupancyPO> selectActiveOccupancies(@Param("siteId") Long siteId,
                                                   @Param("dayStart") LocalDateTime dayStart,
                                                   @Param("dayEnd") LocalDateTime dayEnd);
}
