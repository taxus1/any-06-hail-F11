package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hail.po.AmmoStockPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 弹药库存 Mapper（基础设施层）。
 *
 * 除 BaseMapper 外，关键是 {@link #addQuantity}：用数据库原子自加完成「同点 + 同弹型 + 同批次
 * 再次入库累加到原记录」，避免「先查后写」在并发下丢更新。
 *
 * 阻塞 JDBC API，只能在 boundedElastic 线程的 blocking(...) 里调用。
 */
@Mapper
public interface AmmoStockMapper extends BaseMapper<AmmoStockPO> {

    /** 按「作业点 + 弹型 + 批次」查未删除的唯一库存（手写 SQL 显式带 del_flag = 0）。 */
    @Select("SELECT * FROM t_ammo_stock "
            + "WHERE site_id = #{siteId} AND ammo_type = #{ammoType} AND batch_no = #{batchNo} "
            + "AND del_flag = 0 LIMIT 1")
    AmmoStockPO selectUnique(@Param("siteId") Long siteId,
                             @Param("ammoType") String ammoType,
                             @Param("batchNo") String batchNo);

    /**
     * 原子累加发数并顺带校准日期（同批次日期理应一致，非空才覆盖）。
     * 只影响未删除行，返回受影响行数（0 表示这条库存还不存在，调用方改走 insert）。
     * 手写 SQL 不经 MetaObjectHandler，update_by / update_time 在此显式更新。
     */
    @Update("UPDATE t_ammo_stock SET quantity = quantity + #{delta}, "
            + "produce_date = COALESCE(#{produceDate}, produce_date), "
            + "expire_date = COALESCE(#{expireDate}, expire_date), "
            + "update_by = #{operator}, update_time = #{now} "
            + "WHERE id = #{id} AND del_flag = 0")
    int addQuantity(@Param("id") Long id,
                    @Param("delta") int delta,
                    @Param("produceDate") java.time.LocalDate produceDate,
                    @Param("expireDate") java.time.LocalDate expireDate,
                    @Param("operator") String operator,
                    @Param("now") java.time.LocalDateTime now);

    /**
     * 原子划出 qty 发：只有结存 ≥ qty 才扣得动（{@code quantity >= #{qty}}），
     * 防「先查后扣」超领与并发丢更新，返回受影响行数（0 = 结存不足或行失效）。
     */
    @Update("UPDATE t_ammo_stock SET quantity = quantity - #{qty}, "
            + "update_by = #{operator}, update_time = #{now} "
            + "WHERE id = #{id} AND quantity >= #{qty} AND del_flag = 0")
    int deductQuantity(@Param("id") Long id,
                       @Param("qty") int qty,
                       @Param("operator") String operator,
                       @Param("now") java.time.LocalDateTime now);
}
