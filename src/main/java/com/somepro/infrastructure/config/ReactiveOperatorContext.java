package com.somepro.infrastructure.config;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * 响应式操作人上下文键（基础设施层）。
 *
 * WebFlux 下没有可跨线程的 ThreadLocal，操作人通过 Reactor Context 在调用链上透传。
 * OperatorWebFilter 把当前登录用户写入 Context；仓储适配器在切到 JDBC 线程之前取出它，
 * 搬进 {@code AuditContextHolder}，由 AutoFillMetaObjectHandler 填充 createBy / updateBy。
 */
public final class ReactiveOperatorContext {

    /** Reactor Context 里存放操作人（登录用户名）的 key。 */
    public static final String OPERATOR = "OPERATOR";

    private ReactiveOperatorContext() {
    }

    public static Context withOperator(String operator) {
        return Context.of(OPERATOR, operator);
    }

    public static String getOperator(ContextView ctx) {
        return ctx.getOrDefault(OPERATOR, "system");
    }
}
