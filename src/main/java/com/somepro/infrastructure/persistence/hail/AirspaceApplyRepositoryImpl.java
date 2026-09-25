package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.repository.AirspaceApplyRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.AirspaceApplyPoConverter;
import com.somepro.infrastructure.persistence.hail.po.AirspaceApplyPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空域申请仓储适配器（基础设施层）。
 *
 * - 编号按年自增：{@link #nextApplyNo} 取「KQ-yyyy-」前缀下最大序号 +1（序号固定 4 位左补零，
 *   字典序最大即数字最大）；并发撞号由 uk_apply_no 抛 DuplicateKeyException，应用层换号重试。
 * - 分页用 PageHelper，try/finally 里 clearPage 防 ThreadLocal 污染。
 */
@Repository
public class AirspaceApplyRepositoryImpl extends BlockingRepositorySupport implements AirspaceApplyRepository {

    private static final String NO_PREFIX = "KQ";

    private final AirspaceApplyMapper mapper;

    public AirspaceApplyRepositoryImpl(AirspaceApplyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<AirspaceApply> save(AirspaceApply apply) {
        return blocking(() -> {
            AirspaceApplyPO po = AirspaceApplyPoConverter.toPo(apply);
            if (po.getId() == null) {
                po.setId(IdUtil.getSnowflakeNextId());
                mapper.insert(po);
            } else {
                mapper.updateById(po);
            }
            return AirspaceApplyPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<AirspaceApply> findById(Long id) {
        return blocking(() -> {
            AirspaceApplyPO po = mapper.selectById(id);
            return po == null ? null : AirspaceApplyPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<AirspaceApply> findByNo(String applyNo) {
        return blocking(() -> {
            AirspaceApplyPO po = mapper.selectOne(
                    Wrappers.<AirspaceApplyPO>lambdaQuery().eq(AirspaceApplyPO::getApplyNo, applyNo));
            return po == null ? null : AirspaceApplyPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<List<AirspaceApply>> findByIds(Collection<Long> ids) {
        return blocking(() -> {
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return mapper.selectBatchIds(ids).stream()
                    .map(AirspaceApplyPoConverter::toDomain)
                    .collect(Collectors.toList());
        });
    }

    @Override
    public Mono<String> nextApplyNo(int year) {
        return blocking(() -> {
            String prefix = NO_PREFIX + "-" + year + "-";
            // 编号里的下划线在 LIKE 中是通配符，转义后再拼 %，避免误匹配
            String escaped = prefix.replace("_", "/_");
            String max = mapper.selectMaxNoByPrefix(escaped + "%");
            int seq = 1;
            if (max != null && max.length() >= 4) {
                seq = Integer.parseInt(max.substring(max.length() - 4)) + 1;
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    public Mono<PageResult<AirspaceApply>> page(int pageNum, int pageSize, Long siteId, String status) {
        return this.<PageResult<AirspaceApply>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<AirspaceApplyPO> wrapper = Wrappers.<AirspaceApplyPO>lambdaQuery()
                        .eq(siteId != null, AirspaceApplyPO::getSiteId, siteId)
                        .eq(status != null && !status.isBlank(),
                                AirspaceApplyPO::getStatus, status == null ? null : status.trim())
                        .orderByDesc(AirspaceApplyPO::getId);
                List<AirspaceApplyPO> rows = mapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<AirspaceApply> content = rows.stream()
                        .map(AirspaceApplyPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
