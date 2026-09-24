package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.hail.model.OperationSite;
import com.somepro.domain.hail.repository.OperationSiteRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.OperationSitePoConverter;
import com.somepro.infrastructure.persistence.hail.po.OperationSitePO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 作业点档案仓储适配器：MyBatis-Plus 实现领域端口（基础设施层）。
 *
 * - 所有 JDBC 调用都走 {@link BlockingRepositorySupport#blocking}，绝不上 Netty event-loop。
 * - del_flag 交给 @TableLogic（selectList/selectById 自动带 del_flag = 0），手写 SQL 才显式带。
 * - 编号唯一：应用层先 {@code findByCode} 判重，并发漏网时由库表 uk_site_code 抛
 *   {@link DuplicateKeyException}，应用层把它翻译成业务报错。
 * - 分页用 PageHelper，try/finally 里 clearPage 防 ThreadLocal 污染。
 */
@Repository
public class OperationSiteRepositoryImpl extends BlockingRepositorySupport
        implements OperationSiteRepository {

    private final OperationSiteMapper mapper;

    public OperationSiteRepositoryImpl(OperationSiteMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<OperationSite> save(OperationSite site) {
        return blocking(() -> {
            OperationSitePO po = OperationSitePoConverter.toPo(site);
            if (po.getId() == null) {
                po.setId(IdUtil.getSnowflakeNextId());
                mapper.insert(po);
            } else {
                mapper.updateById(po);
            }
            // insert/update 后 id 与审计字段已回填，转回领域对象返回
            return OperationSitePoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<OperationSite> findById(Long id) {
        return blocking(() -> {
            OperationSitePO po = mapper.selectById(id);
            return po == null ? null : OperationSitePoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<OperationSite> findByCode(String siteCode) {
        return blocking(() -> {
            OperationSitePO po = mapper.selectByCode(siteCode);
            return po == null ? null : OperationSitePoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<OperationSite>> page(int pageNum, int pageSize, String keyword, String county) {
        return this.<PageResult<OperationSite>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<OperationSitePO> wrapper = Wrappers.<OperationSitePO>lambdaQuery()
                        .eq(county != null && !county.isBlank(),
                                OperationSitePO::getCounty, county == null ? null : county.trim())
                        // 一个关键字同时模糊匹配编号与名称，满足「按名称或者编号翻着看」
                        .and(keyword != null && !keyword.isBlank(), w -> {
                            String kw = keyword.trim();
                            w.like(OperationSitePO::getSiteCode, kw)
                                    .or().like(OperationSitePO::getSiteName, kw);
                        })
                        .orderByAsc(OperationSitePO::getId);
                List<OperationSitePO> rows = mapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<OperationSite> content = rows.stream()
                        .map(OperationSitePoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                // 分页参数靠 ThreadLocal 传递，必须清理，否则污染线程池下一次调用
                PageHelper.clearPage();
            }
        });
    }
}
