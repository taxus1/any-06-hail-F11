package com.somepro.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
// 注意：WebFlux 必须用 reactive 包下的 CORS 类型；servlet 包（org.springframework.web.cors.CorsConfigurationSource）
// 与 ServerHttpSecurity#cors 不兼容，会导致 lambda 推断失败。
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 响应式 Spring Security 配置（基础设施层）。
 * - 全路由需认证：formLogin 登录后拿 SESSION cookie，验收测试可登录取 cookie 后调用。
 * - CORS 允许跨域：scaffold 默认放行全部来源（allowedOriginPatterns="*"），生产按需要收紧。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            CorsConfigurationSource corsConfigurationSource) {
        http
            // 用参数注入而不是直接调用 corsConfigurationSource()：后者依赖 @Configuration 的
            // CGLIB 代理才能拿到单例，一旦有人改成 proxyBeanMethods=false 就会悄悄 new 出多个实例
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(ex -> ex.anyExchange().authenticated())
            // 用 withDefaults() 而非 `form -> form`：Customizer#customize 返回 void，
            // 值表达式的 lambda 无法匹配 void 函数式接口（方法引用可以，lambda 不行）。
            .formLogin(Customizer.withDefaults())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(),
                HttpMethod.PUT.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public MapReactiveUserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("admin")
                .password("{noop}admin123")
                .roles("USER")
                .build();
        return new MapReactiveUserDetailsService(user);
    }
}
