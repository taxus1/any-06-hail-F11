package com.somepro.domain.hail.repository;

import com.somepro.domain.hail.model.OperationSite;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 作业点档案仓储端口（领域层定义，基础设施层实现）。
 *
 * 注意：t_operation_site 里可能已经躺着早先录入的数据，所以唯一性、归属校验都必须经此端口查库，
 * 不能只看本次请求。
 */
public interface OperationSiteRepository {

    Mono<OperationSite> save(OperationSite site);

    Mono<OperationSite> findById(Long id);

    /** 按编号查未删除的作业点；查不到返回空信号。 */
    Mono<OperationSite> findByCode(String siteCode);

    /**
     * 分页翻看作业点。
     *
     * @param keyword 关键字，同时模糊匹配编号（site_code）与名称（site_name），
     *                null / 空串表示不加条件
     */
    Mono<PageResult<OperationSite>> page(int pageNum, int pageSize, String keyword, String county);
}
