package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.EffectReportPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 作业效果上报 Mapper（基础设施层）。
 * 一条指令一份，重复提交靠 uk_order 兜底，常规翻看走本方法。
 */
@Mapper
public interface EffectReportMapper extends BaseMapper<EffectReportPO> {

    /** 按指令 id 查效果上报（显式带 del_flag = 0）；没有返回 null。 */
    @Select("SELECT * FROM t_effect_report WHERE order_id = #{orderId} AND del_flag = 0 LIMIT 1")
    EffectReportPO selectByOrderId(@Param("orderId") Long orderId);
}
