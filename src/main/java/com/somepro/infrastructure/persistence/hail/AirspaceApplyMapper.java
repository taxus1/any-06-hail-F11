package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.AirspaceApplyPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 空域申请 Mapper（基础设施层）。
 *
 * 除 BaseMapper 外，{@link #selectMaxNoByPrefix} 支撑编号按年自增（KQ-yyyy-NNNN）：
 * 序号固定 4 位、左侧补零，字典序最大即数字最大。
 *
 * 阻塞 JDBC API，只能在 boundedElastic 线程的 blocking(...) / 事务方法里调用。
 */
@Mapper
public interface AirspaceApplyMapper extends BaseMapper<AirspaceApplyPO> {

    /** 查某年编号前缀（如 KQ-2026-）下当前最大编号；没有数据返回 null。调用方负责转义下划线。 */
    @Select("SELECT MAX(apply_no) FROM t_airspace_apply "
            + "WHERE apply_no LIKE #{prefixPattern} ESCAPE '/' AND del_flag = 0")
    String selectMaxNoByPrefix(@Param("prefixPattern") String prefixPattern);
}
