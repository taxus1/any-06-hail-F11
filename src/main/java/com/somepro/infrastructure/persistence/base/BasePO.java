package com.somepro.infrastructure.persistence.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 持久化对象（PO）公共基类（基础设施层）。
 *
 * 与领域基类 {@code com.somepro.domain.shared.model.BaseEntity} 的分工：
 * - 这里是「表」的形状，带 MyBatis-Plus 注解，允许依赖框架；
 * - 领域基类是「业务」的形状，**不带任何框架注解**，领域层因此不依赖基础设施。
 * 两者由各模块的 Converter 互转。
 *
 * - 审计字段由 {@code AutoFillMetaObjectHandler} 经 FieldFill 自动填充，业务代码不要 set；
 *   操作人来自 Reactor Context（OperatorWebFilter 写入 → 仓储适配器搬进 AuditContextHolder）。
 * - delFlag 上加 @TableLogic：查询自动追加 del_flag = 0，deleteById() 自动改写为置 1。
 */
@Getter
@Setter
public abstract class BasePO implements Serializable {

    /** 逻辑删除：0 正常 / 1 已删除。加了 @TableLogic 后无需手写 del_flag 条件。 */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag = 0;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
