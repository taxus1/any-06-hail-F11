package com.somepro.infrastructure.cache;

import com.somepro.application.demo.port.DemoCachePort;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 缓存适配器：用响应式 Redis 实现应用层缓存端口（基础设施层）。
 */
@Component
public class RedisCacheAdapter implements DemoCachePort {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public RedisCacheAdapter(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<String> setAndGet(String key, String value) {
        return redisTemplate.opsForValue()
                .set(key, value, Duration.ofSeconds(30))
                .then(redisTemplate.opsForValue().get(key))
                // 取不到就发空信号，不要返回字符串 "null" —— 那会让调用方误以为真的存了这个值
                .flatMap(v -> v == null ? Mono.empty() : Mono.just(v.toString()));
    }
}
