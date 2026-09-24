package com.somepro.infrastructure.persistence.hail;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.repository.AmmoStockRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.AmmoStockPoConverter;
import com.somepro.infrastructure.persistence.hail.po.AmmoStockPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 弹药库存仓储适配器（基础设施层，只读）。
 *
 * 写操作（入库 / 划出 / 退回）都与出入库流水同事务，统一走
 * {@code HailFlowTxExecutor} 直接操作 Mapper；这里只提供查询。
 */
@Repository
public class AmmoStockRepositoryImpl extends BlockingRepositorySupport implements AmmoStockRepository {

    private final AmmoStockMapper mapper;

    public AmmoStockRepositoryImpl(AmmoStockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<AmmoStock> findUnique(Long siteId, String ammoType, String batchNo) {
        return blocking(() -> {
            AmmoStockPO po = mapper.selectUnique(siteId, ammoType, batchNo);
            return po == null ? null : AmmoStockPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<AmmoStock> findById(Long id) {
        return blocking(() -> {
            AmmoStockPO po = mapper.selectById(id);
            return po == null ? null : AmmoStockPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<AmmoStock>> page(int pageNum, int pageSize, Long siteId,
                                            String ammoType, String batchNo) {
        return this.<PageResult<AmmoStock>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<AmmoStockPO> wrapper = Wrappers.<AmmoStockPO>lambdaQuery()
                        .eq(siteId != null, AmmoStockPO::getSiteId, siteId)
                        .eq(ammoType != null && !ammoType.isBlank(),
                                AmmoStockPO::getAmmoType, ammoType == null ? null : ammoType.trim())
                        .like(batchNo != null && !batchNo.isBlank(),
                                AmmoStockPO::getBatchNo, batchNo == null ? null : batchNo.trim())
                        .orderByAsc(AmmoStockPO::getId);
                List<AmmoStockPO> rows = mapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<AmmoStock> content = rows.stream()
                        .map(AmmoStockPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
