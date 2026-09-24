package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.hail.repository.AmmoRecordRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.AmmoRecordPoConverter;
import com.somepro.infrastructure.persistence.hail.po.AmmoRecordPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 弹药出入库流水仓储适配器（基础设施层）。
 *
 * 流水由 {@code HailFlowTxExecutor} 在库存事务里追加；本端口只负责追加兜底与分页翻看。
 */
@Repository
public class AmmoRecordRepositoryImpl extends BlockingRepositorySupport implements AmmoRecordRepository {

    private final AmmoRecordMapper mapper;

    public AmmoRecordRepositoryImpl(AmmoRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<AmmoRecord> save(AmmoRecord record) {
        return blocking(() -> {
            AmmoRecordPO po = AmmoRecordPoConverter.toPo(record);
            if (po.getId() == null) {
                po.setId(cn.hutool.core.util.IdUtil.getSnowflakeNextId());
                mapper.insert(po);
            } else {
                mapper.updateById(po);
            }
            return AmmoRecordPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<AmmoRecord> findOutRecord(Long siteId, String orderNo) {
        return blocking(() -> {
            AmmoRecordPO po = mapper.selectOutRecord(siteId, orderNo);
            return po == null ? null : AmmoRecordPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<AmmoRecord>> page(int pageNum, int pageSize,
                                             Long siteId, String ammoType, String bizType) {
        return this.<PageResult<AmmoRecord>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<AmmoRecordPO> wrapper = Wrappers.<AmmoRecordPO>lambdaQuery()
                        .eq(siteId != null, AmmoRecordPO::getSiteId, siteId)
                        .eq(ammoType != null && !ammoType.isBlank(),
                                AmmoRecordPO::getAmmoType, ammoType == null ? null : ammoType.trim())
                        .eq(bizType != null && !bizType.isBlank(),
                                AmmoRecordPO::getBizType, bizType == null ? null : bizType.trim())
                        // 流水账：最新的在前
                        .orderByDesc(AmmoRecordPO::getId);
                List<AmmoRecordPO> rows = mapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<AmmoRecord> content = rows.stream()
                        .map(AmmoRecordPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
