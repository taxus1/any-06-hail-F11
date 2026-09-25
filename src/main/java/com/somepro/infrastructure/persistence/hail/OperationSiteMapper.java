package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.OperationSitePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 作业点档案 Mapper（基础设施层）。
 *
 * BaseMapper 已提供常用 CRUD；这里只额外加一条「按编号查」—— 编号是业务键，
 * 登记时要靠它判重（表中可能已有早先数据，必须查库）。
 *
 * 阻塞 JDBC API，只能在 boundedElastic 线程的 blocking(...) 里调用。
 */
@Mapper
public interface OperationSiteMapper extends BaseMapper<OperationSitePO> {

    /**
     * 按编号查未删除的作业点。@TableLogic 对手写 SQL 不生效，这里显式带 del_flag = 0。
     */
    @Select("SELECT * FROM t_operation_site WHERE site_code = #{siteCode} AND del_flag = 0 LIMIT 1")
    OperationSitePO selectByCode(@Param("siteCode") String siteCode);

    /**
     * 主键行锁：开单事务一开始先把作业点行锁住。
     * 同一作业点上「两张空域同时段撞车」「同一张空域拆两单」「两台单子点同一台装备」的并发竞争
     * 全靠它把同点开单串行化 —— 两人同一瞬间抢同一段时辰，先拿到行锁的成，后到的看见占单被挡回。
     * 必须在事务内调用（FOR UPDATE 随事务提交释放）。
     */
    @Select("SELECT id FROM t_operation_site WHERE id = #{siteId} AND del_flag = 0 FOR UPDATE")
    Long selectIdForUpdate(@Param("siteId") Long siteId);
}
