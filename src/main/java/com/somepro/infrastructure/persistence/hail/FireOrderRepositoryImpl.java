package com.somepro.infrastructure.persistence.hail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.repository.FireOrderRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.hail.converter.FireOrderPoConverter;
import com.somepro.infrastructure.persistence.hail.po.FireOrderPO;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 作业指令仓储适配器（基础设施层）。
 *
 * 指令的「插入（开单）」与「完结（回报）」都伴随库存与流水多写，
 * 统一走 {@code HailFlowTxExecutor} 事务；本端口只负责单表查 / 改（批复无关的常规更新）与分页、编号。
 */
@Repository
public class FireOrderRepositoryImpl extends BlockingRepositorySupport implements FireOrderRepository {

    private static final String NO_PREFIX = "ZY";

    private final FireOrderMapper mapper;

    public FireOrderRepositoryImpl(FireOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<FireOrder> save(FireOrder order) {
        return blocking(() -> {
            FireOrderPO po = FireOrderPoConverter.toPo(order);
            if (po.getId() == null) {
                po.setId(IdUtil.getSnowflakeNextId());
                mapper.insert(po);
            } else {
                mapper.updateById(po);
            }
            return FireOrderPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<FireOrder> findById(Long id) {
        return blocking(() -> {
            FireOrderPO po = mapper.selectById(id);
            return po == null ? null : FireOrderPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<FireOrder> findByNo(String orderNo) {
        return blocking(() -> {
            FireOrderPO po = mapper.selectOne(
                    Wrappers.<FireOrderPO>lambdaQuery().eq(FireOrderPO::getOrderNo, orderNo));
            return po == null ? null : FireOrderPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<String> nextOrderNo(int year) {
        return blocking(() -> {
            String prefix = NO_PREFIX + "-" + year + "-";
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
    public Mono<PageResult<FireOrder>> page(int pageNum, int pageSize, Long siteId, String status) {
        return this.<PageResult<FireOrder>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<FireOrderPO> wrapper = Wrappers.<FireOrderPO>lambdaQuery()
                        .eq(siteId != null, FireOrderPO::getSiteId, siteId)
                        .eq(status != null && !status.isBlank(),
                                FireOrderPO::getStatus, status == null ? null : status.trim())
                        .orderByDesc(FireOrderPO::getId);
                List<FireOrderPO> rows = mapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<FireOrder> content = rows.stream()
                        .map(FireOrderPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
