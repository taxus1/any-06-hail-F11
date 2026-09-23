package com.somepro.infrastructure.persistence.audit;

/**
 * 响应式上下文 → 阻塞线程 的操作人桥接器（基础设施层）。
 *
 * 背景：MyBatis-Plus 的 MetaObjectHandler 在 JDBC 调用线程（boundedElastic）上执行，
 * 读不到 Reactor Context。所以仓储适配器在切线程之前先从 Reactor Context 取出操作人，
 * 放进这里（ThreadLocal），MetaObjectHandler 再取出来填充 createBy / updateBy。
 *
 * 约束：必须在 finally 里 clear()，否则会污染线程池里的下一位租户。
 */
public final class AuditContextHolder {

    private static final ThreadLocal<String> OPERATOR = new ThreadLocal<>();

    private AuditContextHolder() {
    }

    public static void setOperator(String operator) {
        OPERATOR.set(operator);
    }

    /** 取当前操作人；未设置时回落到 "system"，与 OperatorWebFilter 的默认值一致。 */
    public static String getOperator() {
        String value = OPERATOR.get();
        return (value == null || value.isBlank()) ? "system" : value;
    }

    public static void clear() {
        OPERATOR.remove();
    }
}
