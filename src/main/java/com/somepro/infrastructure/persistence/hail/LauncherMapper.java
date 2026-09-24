package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.LauncherPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 发射装备台账 Mapper（基础设施层）。
 *
 * 额外提供按编号查（判重用）与库存式原子加管数不需要，这里只加 selectByCode。
 * 阻塞 JDBC API，只能在 boundedElastic 线程的 blocking(...) 里调用。
 */
@Mapper
public interface LauncherMapper extends BaseMapper<LauncherPO> {

    /** 按装备编号查未删除记录（手写 SQL 不享受 @TableLogic，显式带 del_flag = 0）。 */
    @Select("SELECT * FROM t_launcher WHERE launcher_code = #{launcherCode} AND del_flag = 0 LIMIT 1")
    LauncherPO selectByCode(@Param("launcherCode") String launcherCode);
}
