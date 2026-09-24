package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.somepro.domain.hail.model.EffectReport;
import com.somepro.domain.hail.repository.EffectReportRepository;
import com.somepro.infrastructure.persistence.hail.converter.EffectReportPoConverter;
import com.somepro.infrastructure.persistence.hail.po.EffectReportPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 作业效果上报仓储适配器（基础设施层）。
 * 插入走 {@code HailFlowTxExecutor}（事务 + uk_order 兜底）；本端口负责查。
 */
@Repository
public class EffectReportRepositoryImpl extends BlockingRepositorySupport implements EffectReportRepository {

    private final EffectReportMapper mapper;

    public EffectReportRepositoryImpl(EffectReportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<EffectReport> save(EffectReport report) {
        return blocking(() -> {
            EffectReportPO po = EffectReportPoConverter.toPo(report);
            if (po.getId() == null) {
                po.setId(IdUtil.getSnowflakeNextId());
                mapper.insert(po);
            } else {
                mapper.updateById(po);
            }
            return EffectReportPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<EffectReport> findById(Long id) {
        return blocking(() -> {
            EffectReportPO po = mapper.selectById(id);
            return po == null ? null : EffectReportPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<EffectReport> findByOrderId(Long orderId) {
        return blocking(() -> {
            EffectReportPO po = mapper.selectByOrderId(orderId);
            return po == null ? null : EffectReportPoConverter.toDomain(po);
        });
    }
}
