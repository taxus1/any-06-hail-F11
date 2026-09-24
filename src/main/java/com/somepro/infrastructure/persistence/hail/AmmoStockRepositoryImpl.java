package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.repository.AmmoStockRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.AmmoStockPoConverter;
import com.somepro.infrastructure.persistence.hail.po.AmmoStockPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 弹药库存仓储适配器（基础设施层）。
 *
 * 入库的核心难点是「同点 + 同弹型 + 同批次不另起一条，累加原记录」且要扛并发：
 * 1. 先 {@code selectUnique}：查到就用 {@code UPDATE ... SET quantity = quantity + ?} 原子自加，
 *    避免「读出来改回去」的丢失更新；
 * 2. 查不到就 insert，若并发下另一线程已抢先插入（撞 uk_stock 抛
 *    {@link DuplicateKeyException}），退化为对刚插入那条再做一次原子累加，绝不产生第二条。
 *
 * 整个入库是一次阻塞调用，运行在 boundedElastic 线程上；不额外加 @Transactional ——
 * 原子更新与「insert 或累加」的去重由单语句 + 唯一索引保证，没有多表写需要包成一个事务。
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
    public Mono<AmmoStock> inbound(AmmoStock incoming) {
        return blocking(() -> doInbound(incoming));
    }

    private AmmoStock doInbound(AmmoStock incoming) {
        // 1) 先按业务唯一键查现有库存（含早先录入的数据，必须以库里为准）
        AmmoStockPO existing = mapper.selectUnique(
                incoming.getSiteId(), incoming.getAmmoType(), incoming.getBatchNo());
        if (existing != null) {
            // 2a) 已有：原子累加到原记录，不另起一条
            addQty(existing.getId(), incoming);
            return AmmoStockPoConverter.toDomain(mapper.selectById(existing.getId()));
        }
        // 2b) 没有：新建一条
        AmmoStockPO po = AmmoStockPoConverter.toPo(incoming);
        po.setId(IdUtil.getSnowflakeNextId());
        try {
            mapper.insert(po);
            return AmmoStockPoConverter.toDomain(po);
        } catch (DuplicateKeyException e) {
            // 3) 并发兜底：别人抢先插了同点 + 同弹型 + 同批次，退化为累加那条
            AmmoStockPO winner = mapper.selectUnique(
                    incoming.getSiteId(), incoming.getAmmoType(), incoming.getBatchNo());
            if (winner == null) {
                // 理论上不会发生（唯一键冲突说明行存在），稳妥起见抛出由全局异常收口
                throw e;
            }
            addQty(winner.getId(), incoming);
            return AmmoStockPoConverter.toDomain(mapper.selectById(winner.getId()));
        }
    }

    private void addQty(Long id, AmmoStock incoming) {
        int affected = mapper.addQuantity(id, incoming.getQuantity(),
                incoming.getProduceDate(), incoming.getExpireDate());
        if (affected == 0) {
            // 行在查询后被逻辑删除等极端情况：明确报错由全局异常收口，不静默吞掉
            throw new BizException("库存记录已失效，请重新查询后再入库");
        }
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
