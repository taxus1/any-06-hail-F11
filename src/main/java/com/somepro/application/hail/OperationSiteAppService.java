package com.somepro.application.hail;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.OperationSite;
import com.somepro.domain.hail.repository.OperationSiteRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 作业点应用服务：编排「登记、翻看」用例（应用层）。
 *
 * 出入参都是领域对象 / 基本类型，不认识 PO、VO。
 * 编号唯一性在落库前先查库（表里可能有早先数据），并发漏网由唯一索引 + DuplicateKeyException 兜底。
 */
@Service
public class OperationSiteAppService {

    private final OperationSiteRepository siteRepository;

    public OperationSiteAppService(OperationSiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    /** 登记作业点。编号全局唯一，重复给明确业务报错。 */
    public Mono<OperationSite> register(String siteCode, String siteName, String county,
                                        Integer altitudeM, String contactName, String contactPhone,
                                        String status) {
        // 工厂方法集中校验必填、枚举等不变量
        OperationSite site = OperationSite.register(
                siteCode, siteName, county, altitudeM, contactName, contactPhone, status);
        // 查重必须查库：t_operation_site 里可能已有早先录入的数据
        return siteRepository.findByCode(site.getSiteCode())
                .flatMap(existing -> Mono.<OperationSite>error(
                        new BizException("作业点编号已存在：" + existing.getSiteCode())))
                .switchIfEmpty(Mono.defer(() -> siteRepository.save(site)))
                // 并发下两个请求同时查重通过，库表 uk_site_code 兜底
                .onErrorResume(DuplicateKeyException.class, e ->
                        Mono.error(new BizException("作业点编号已存在：" + site.getSiteCode())));
    }

    public Mono<OperationSite> getById(Long id) {
        return siteRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("作业点不存在：id=" + id)));
    }

    /** 按名称或者编号关键字分页翻看。 */
    public Mono<PageResult<OperationSite>> page(int pageNum, int pageSize, String keyword, String county) {
        return siteRepository.page(pageNum, pageSize, keyword, county);
    }
}
