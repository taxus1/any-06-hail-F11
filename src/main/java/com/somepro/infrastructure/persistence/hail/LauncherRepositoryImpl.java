package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.repository.LauncherRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.LauncherPoConverter;
import com.somepro.infrastructure.persistence.hail.po.LauncherPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 发射装备台账仓储适配器（基础设施层）。
 *
 * JDBC 全部走 blocking(...)；编号唯一性靠应用层 findByCode 判重 + 库表 uk_launcher_code 兜底。
 */
@Repository
public class LauncherRepositoryImpl extends BlockingRepositorySupport implements LauncherRepository {

    private final LauncherMapper mapper;

    public LauncherRepositoryImpl(LauncherMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Launcher> save(Launcher launcher) {
        return blocking(() -> {
            LauncherPO po = LauncherPoConverter.toPo(launcher);
            if (po.getId() == null) {
                po.setId(IdUtil.getSnowflakeNextId());
                mapper.insert(po);
            } else {
                mapper.updateById(po);
            }
            return LauncherPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<Launcher> findById(Long id) {
        return blocking(() -> {
            LauncherPO po = mapper.selectById(id);
            return po == null ? null : LauncherPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<Launcher> findByCode(String launcherCode) {
        return blocking(() -> {
            LauncherPO po = mapper.selectByCode(launcherCode);
            return po == null ? null : LauncherPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<Launcher>> page(int pageNum, int pageSize, Long siteId,
                                           String status, String keyword) {
        return this.<PageResult<Launcher>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<LauncherPO> wrapper = Wrappers.<LauncherPO>lambdaQuery()
                        .eq(siteId != null, LauncherPO::getSiteId, siteId)
                        .eq(status != null && !status.isBlank(),
                                LauncherPO::getStatus, status == null ? null : status.trim())
                        .and(keyword != null && !keyword.isBlank(), w -> {
                            String kw = keyword.trim();
                            // 翻看装备：按编号或型号模糊匹配
                            w.like(LauncherPO::getLauncherCode, kw)
                                    .or().like(LauncherPO::getModel, kw);
                        })
                        .orderByAsc(LauncherPO::getId);
                List<LauncherPO> rows = mapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<Launcher> content = rows.stream()
                        .map(LauncherPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
