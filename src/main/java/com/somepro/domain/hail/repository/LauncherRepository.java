package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * 发射装备台账仓储端口（领域层定义，基础设施层实现）。
 */
public interface LauncherRepository {

    Mono<Launcher> save(Launcher launcher);

    Mono<Launcher> findById(Long id);

    /** 按装备编号查未删除的装备；查不到返回空信号。 */
    Mono<Launcher> findByCode(String launcherCode);

    /** 按 id 批量查（占用投影拼装用）；空集合进、空列表出，不走库。 */
    Mono<List<Launcher>> findByIds(Collection<Long> ids);

    /**
     * 分页翻看装备。
     *
     * @param siteId 只看挂在某个作业点下的装备，null 表示不限
     * @param status 状态过滤，null 表示不限；非法值由调用方先收敛
     */
    Mono<PageResult<Launcher>> page(int pageNum, int pageSize, Long siteId, String status, String keyword);
}
