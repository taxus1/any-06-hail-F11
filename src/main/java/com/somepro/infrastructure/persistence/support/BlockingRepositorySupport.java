package com.somepro.infrastructure.persistence.support;

import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

/**
 * 仓储适配器共用的「阻塞 JDBC → 响应式」桥接基类（基础设施层）。
 *
 * 把 DemoItemRepositoryImpl 里那段桥接逻辑收敛到一处，三个防雹仓储直接复用，
 * 顺序仍是硬红线：先 {@code deferContextual} 从 Reactor Context 取操作人，
 * 再 {@code subscribeOn(boundedElastic)} 切线程执行 JDBC，顺序反了审计会静默退化成 system。
 *
 * 用法：仓储适配器 extends 本类，所有 Mapper 调用都包在 {@code blocking(...)} 里。
 */
public abstract class BlockingRepositorySupport {

    protected <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(ctx -> {
            String operator = ReactiveOperatorContext.getOperator(ctx);
            return Mono.fromCallable(() -> {
                AuditContextHolder.setOperator(operator);
                try {
                    return supplier.get();
                } finally {
                    AuditContextHolder.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }
}
