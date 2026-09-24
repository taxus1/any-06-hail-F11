package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.AmmoRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 弹药出入库流水 Mapper（基础设施层）。
 *
 * {@link #selectOutRecord} 让回报退弹时能找到当初开单领用的批次
 * （批次不在指令表上，落在 OUT 流水里）。
 */
@Mapper
public interface AmmoRecordMapper extends BaseMapper<AmmoRecordPO> {

    /**
     * 查某条指令在某作业点领用（OUT）的那笔流水，取批次用。
     * 手写 SQL 显式带 del_flag = 0；按 id 倒序取最新一笔。
     */
    @Select("SELECT * FROM t_ammo_record "
            + "WHERE site_id = #{siteId} AND biz_type = 'OUT' AND ref_no = #{orderNo} AND del_flag = 0 "
            + "ORDER BY id DESC LIMIT 1")
    AmmoRecordPO selectOutRecord(@Param("siteId") Long siteId,
                                 @Param("orderNo") String orderNo);
}
