package com.somepro.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 响应式 Redis 统一配置（基础设施层）。
 * 约定：key 用 String，value 用 JSON。所有模块从容器取同一个 ReactiveRedisTemplate，
 * 不要自己 new，避免序列化方式不一致导致跨模块读不出对方写的数据。
 */
@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory, ObjectMapper objectMapper) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        // newSerializationContext 声明了 <K, V> 两个类型变量，显式类型实参必须写全两个
        RedisSerializationContext<String, Object> ctx = RedisSerializationContext
                .<String, Object>newSerializationContext(keySerializer)
                .hashKey(keySerializer)
                .value(valueSerializer)
                .hashValue(valueSerializer)
                .build();
        return new ReactiveRedisTemplate<>(factory, ctx);
    }

    // ⚠️ 这里刻意**不**自己定义 ObjectMapper bean。
    // 一旦定义，就会顶掉 Spring Boot 自动配置的 ObjectMapper（它的自动配置是
    // @ConditionalOnMissingBean），导致 application.yml 里的 spring.jackson.*
    // （date-format / time-zone / default-property-inclusion）全部失效。
    // 上面的 reactiveRedisTemplate 直接注入容器里那个 ObjectMapper 即可：
    // Spring Boot 3 默认已注册 JavaTimeModule 且日期序列化为 ISO 字符串，行为一致。
}
