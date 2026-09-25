package com.somepro.infrastructure.persistence.hail;

import com.somepro.application.hail.port.HailOperationFlowPort;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.model.EffectReport;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.infrastructure.persistence.support.BlockingRepositorySupport;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * {@link HailOperationFlowPort} 的适配器（基础设施层）。
 *
 * 把事务执行器的阻塞 JDBC 多写，经 {@code blocking(...)} 桥接到响应式链路：
 * 先从 Reactor Context 取操作人放进 AuditContextHolder（审计填充靠它），再切到 boundedElastic
 * 在同一线程上跑完整个 @Transactional 方法 —— 事务绑定线程，绝不能再中途切线程。
 */
@Component
public class HailOperationFlowPortAdapter extends BlockingRepositorySupport
        implements HailOperationFlowPort {

    private final HailFlowTxExecutor executor;

    public HailOperationFlowPortAdapter(HailFlowTxExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Mono<AmmoStock> inboundWithRecord(AmmoStock incoming) {
        return blocking(() -> executor.doInboundWithRecord(incoming));
    }

    @Override
    public Mono<FireOrder> issueOrder(FireOrder order, String batchNo) {
        return blocking(() -> executor.doIssueOrder(order, batchNo));
    }

    @Override
    public Mono<FireOrder> voidOrder(FireOrder order, String batchNo) {
        return blocking(() -> executor.doVoidOrder(order, batchNo));
    }

    @Override
    public Mono<FireOrder> reportFire(FireOrder order, String batchNo) {
        return blocking(() -> {
            int returned = order.getPlanRounds() - order.getUsedRounds();
            return executor.doReportFire(order, batchNo, returned);
        });
    }

    @Override
    public Mono<EffectReport> submitReport(EffectReport report) {
        return blocking(() -> executor.doSubmitReport(report));
    }
}
