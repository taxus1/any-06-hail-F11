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
     * 按 id 查并加行锁（SELECT ... FOR UPDATE），必须在事务里调用。
     * 开单事务第一步先锁住作业点：同一作业点的开单因此串行，
     * 「两个人同一瞬间抢同一段时辰」只成一家。
     */
    @Select("SELECT * FROM t_operation_site WHERE id = #{id} AND del_flag = 0 FOR UPDATE")
    OperationSitePO selectForUpdate(@Param("id") Long id);
}
