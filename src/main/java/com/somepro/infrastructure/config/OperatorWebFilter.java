package com.somepro.infrastructure.config;

import org.springframework.core.Ordered;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * 把当前登录用户写入 Reactor Context（key = ReactiveOperatorContext.OPERATOR），
 * 供下游 MetaObjectHandler 审计填充 createBy / updateBy（基础设施层）。
 *
 * 必须在 Spring Security 的上下文过滤器之后执行，故用最低优先级（后执行）。
 * 业务代码不要自己从请求参数里取操作人，也不要手动 set 审计字段。
 */
@Component
@org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE)
public class OperatorWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // ⚠️ 务必先解析出操作人，再「只调用一次」chain.filter(exchange)。
        //
        // 反面写法（原实现）会把请求整条链跑两遍：
        //   .flatMap(auth -> chain.filter(exchange).contextWrite(...))
        //   .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)...))
        // 因为 chain.filter() 返回的是 Mono<Void> —— 它永远不发射元素、只完成，
        // 所以在 switchIfEmpty 看来「源是空的」，必然触发第二次 chain.filter()。
        // 后果：一次 HTTP 请求写两遍库（第二遍 Context 里没有操作人，审计落成 "system"），
        // 且第二次写响应时抛 UnsupportedOperationException（响应已提交）。
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .map(this::resolveOperator)
                // SecurityContext 为空 / authentication 为 null 时，map 不会产生元素，这里兜底为 system
                .defaultIfEmpty("system")
                .flatMap(operator -> chain.filter(exchange)
                        .contextWrite(Context.of(ReactiveOperatorContext.OPERATOR, operator)));
    }

    private String resolveOperator(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        return auth.getName();
    }
}
