package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.FireOrderPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

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
}
