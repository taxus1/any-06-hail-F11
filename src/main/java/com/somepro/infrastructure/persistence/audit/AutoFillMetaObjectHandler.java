package com.somepro.infrastructure.persistence.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 审计字段自动填充（基础设施层）。
 *
 * 操作人不在这里从 Reactor Context 直接取 —— MetaObjectHandler 跑在 JDBC 调用线程上，
 * 读不到 Reactor Context。改由仓储适配器在切线程之前取出操作人放进
 * {@link AuditContextHolder}，这里读出来填 createBy / updateBy。
 *
 * 业务代码不要手动 set 审计字段；也不要因为「没传操作人」就去掉 OperatorWebFilter，
 * 那样审计会静默退化成 "system"。
 */
@Component
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        String operator = AuditContextHolder.getOperator();
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createBy", String.class, operator);
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateBy", String.class, operator);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String operator = AuditContextHolder.getOperator();
        this.strictUpdateFill(metaObject, "updateBy", String.class, operator);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
