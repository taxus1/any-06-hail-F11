package com.somepro.domain.shared.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 领域共享模型：所有聚合根的公共基类（纯领域，无任何框架注解）。
 *
 * ⚠️ 这里刻意不出现 MyBatis-Plus / JPA 之类的持久化注解 —— **领域层不依赖基础设施**。
 * 表映射注解一律放在基础设施层的 {@code BasePO} 上，两者由各模块的 Converter 互转。
 *
 * 字段语义：
 * - delFlag：0 正常 / 1 已删除。落库时由 BasePO 上的 @TableLogic 兜底（查询自动过滤、删除自动置 1），
 *   领域侧把它当作「是否已删除」的状态读取即可，不要自己去拼 SQL 条件。
 * - 审计字段（createBy/createTime/updateBy/updateTime）：由基础设施自动填充后回写到领域对象，
 *   操作人来自 Reactor Context，业务代码不要手动 set。
 */
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    private Integer delFlag = 0;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
