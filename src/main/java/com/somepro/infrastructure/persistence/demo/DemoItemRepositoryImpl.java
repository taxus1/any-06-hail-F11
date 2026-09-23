package com.somepro.infrastructure.persistence.demo;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.demo.model.DemoItem;
import com.somepro.domain.demo.repository.DemoItemRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.demo.converter.DemoItemPoConverter;
import com.somepro.infrastructure.persistence.demo.po.DemoItemPO;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 仓储适配器：用 MyBatis-Plus 实现领域仓储端口（基础设施层）。
 *
 * ⚠️ 关键约定 1 —— 响应式 × 阻塞：MyBatis-Plus 是阻塞（JDBC）API，而本项目是 WebFlux 响应式链路，
 * 在 Netty event-loop 线程上执行 JDBC 会阻塞整个事件循环。所有 DB 调用都必须经由 {@link #blocking} 桥接。
 *
 * ⚠️ 关键约定 2 —— PO / 领域隔离：Mapper 只认 {@link DemoItemPO}，领域层只认 {@link DemoItem}，
 * 两者在本类里经 {@link DemoItemPoConverter} 互转。不要让 PO 泄到领域层或接口层。
 *
 * 其它约定：
 * - ID 由应用侧分配（雪花），PO 上为 IdType.INPUT。
 * - 软删除交给 @TableLogic：查询自动带 del_flag = 0，deleteById() 自动改写为置 1，不手写条件。
 * - 分页统一用 PageHelper.startPage()，不要用 MyBatis-Plus 的 IPage。
 */
@Repository
public class DemoItemRepositoryImpl implements DemoItemRepository {

    private final DemoItemMapper demoItemMapper;

    public DemoItemRepositoryImpl(DemoItemMapper demoItemMapper) {
        this.demoItemMapper = demoItemMapper;
    }

    @Override
    public Mono<DemoItem> save(DemoItem item) {
        return blocking(() -> {
            DemoItemPO po = DemoItemPoConverter.toPo(item);
            if (po.getId() == null) {
                po.setId(IdUtil.getSnowflakeNextId());
                demoItemMapper.insert(po);
            } else {
                demoItemMapper.updateById(po);
            }
            // insert 后框架会回填 id 与审计字段，转回领域对象一并返回
            return DemoItemPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<DemoItem> findById(Long id) {
        return blocking(() -> {
            DemoItemPO po = demoItemMapper.selectById(id);
            // 返回 null 时 Mono.fromCallable 会自动转成空信号
            return po == null ? null : DemoItemPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<DemoItem>> page(int pageNum, int pageSize, String name) {
        return this.<PageResult<DemoItem>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<DemoItemPO> wrapper = Wrappers.<DemoItemPO>lambdaQuery();
                if (name != null && !name.isBlank()) {
                    wrapper.like(DemoItemPO::getName, name);
                }
                List<DemoItemPO> rows = demoItemMapper.selectList(wrapper);
                // 命中分页插件时返回的是 com.github.pagehelper.Page，可直接取总数
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<DemoItem> content = rows.stream()
                        .map(DemoItemPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                // 分页插件靠 ThreadLocal 传递分页参数，必须清理，否则污染线程池里的下一次调用
                PageHelper.clearPage();
            }
        });
    }

    @Override
    public Mono<Void> softDelete(Long id) {
        return blocking(() -> {
            // @TableLogic 会把它翻译成 UPDATE t_demo_item SET del_flag = 1 WHERE id = ? AND del_flag = 0
            demoItemMapper.deleteById(id);
            return Boolean.TRUE;
        }).then();
    }

    /**
     * 阻塞 DB 调用 → 响应式链路的桥接器。
     *
     * 1. 先在响应式线程上从 Reactor Context 取操作人（切线程后就取不到了）
     * 2. 再切到 boundedElastic 执行 JDBC
     * 3. 把操作人放进 AuditContextHolder，供 MetaObjectHandler 填充 createBy / updateBy
     */
    private <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(ctx -> {
            String operator = ReactiveOperatorContext.getOperator(ctx);
            return Mono.fromCallable(() -> {
                AuditContextHolder.setOperator(operator);
                try {
                    return supplier.get();
                } finally {
                    AuditContextHolder.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }
}
