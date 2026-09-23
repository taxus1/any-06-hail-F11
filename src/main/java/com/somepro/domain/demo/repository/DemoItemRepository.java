package com.somepro.domain.demo.repository;

import com.somepro.domain.demo.model.DemoItem;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * demo 聚合的仓储端口：由领域层定义，基础设施层实现（端口-适配器）。
 *
 * 分页返回自定义的 {@link PageResult} 而非 Spring Data 的 {@code Page} —— 后者是框架类型，
 * 会让领域层反向依赖基础设施。
 *
 * 注：返回 {@code Mono} 是响应式栈的必然妥协（Reactor 是本项目的基础运行时），
 * 除此之外领域层不依赖任何框架类型。
 */
public interface DemoItemRepository {

    Mono<DemoItem> save(DemoItem item);

    Mono<DemoItem> findById(Long id);

    Mono<PageResult<DemoItem>> page(int pageNum, int pageSize, String name);

    Mono<Void> softDelete(Long id);
}
