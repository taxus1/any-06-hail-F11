package com.somepro.application.demo.port;

import reactor.core.publisher.Mono;

/**
 * 缓存端口：由应用层定义，基础设施层用响应式 Redis 实现（端口-适配器）。
 */
public interface DemoCachePort {

    Mono<String> setAndGet(String key, String value);
}
