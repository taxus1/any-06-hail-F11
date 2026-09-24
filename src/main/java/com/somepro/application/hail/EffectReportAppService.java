package com.somepro.application.hail;

import com.somepro.application.hail.port.HailOperationFlowPort;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.EffectReport;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.OrderStatus;
import com.somepro.domain.hail.repository.EffectReportRepository;
import com.somepro.domain.hail.repository.FireOrderRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 作业效果上报应用服务：编排「上报、翻看」用例。
 *
 * 一条指令一份（uk_order）；必须等指令回报完成（DONE）后才能报效果。
 * 降雨量、冰雹粒径按毫米填，影响面积按平方公里填，都不能为负（领域工厂校验）。
 */
@Service
public class EffectReportAppService {

    private final EffectReportRepository reportRepository;
    private final FireOrderRepository orderRepository;
    private final HailOperationFlowPort flowPort;
    private final Clock clock;

    public EffectReportAppService(EffectReportRepository reportRepository,
                                  FireOrderRepository orderRepository,
                                  HailOperationFlowPort flowPort) {
        this(reportRepository, orderRepository, flowPort, Clock.systemDefaultZone());
    }

    EffectReportAppService(EffectReportRepository reportRepository,
                           FireOrderRepository orderRepository,
                           HailOperationFlowPort flowPort, Clock clock) {
        this.reportRepository = reportRepository;
        this.orderRepository = orderRepository;
        this.flowPort = flowPort;
        this.clock = clock;
    }

    /** 为一条指令提交效果上报。reportTime 不传取当前时刻。 */
    public Mono<EffectReport> submit(Long orderId, LocalDateTime reportTime,
                                     BigDecimal rainfallMm, BigDecimal hailSizeMm,
                                     BigDecimal areaKm2, String remark) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BizException("作业指令不存在：id=" + orderId)))
                .flatMap(this::ensureDone)
                .flatMap(order -> reportRepository.findByOrderId(orderId)
                        .flatMap(existing -> Mono.<EffectReport>error(new BizException(
                                "该指令已存在效果上报，一条指令只能报一份：id=" + orderId)))
                        .switchIfEmpty(Mono.defer(() -> doSubmit(orderId, reportTime,
                                rainfallMm, hailSizeMm, areaKm2, remark))));
    }

    public Mono<EffectReport> getById(Long id) {
        return reportRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("效果上报不存在：id=" + id)));
    }

    /** 按指令查效果上报。 */
    public Mono<EffectReport> getByOrderId(Long orderId) {
        return reportRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new BizException("该指令暂无效果上报：orderId=" + orderId)));
    }

    private Mono<FireOrder> ensureDone(FireOrder order) {
        if (order.getStatus() != OrderStatus.DONE) {
            return Mono.error(new BizException(
                    "指令完成回报后才能报效果，当前状态：" + order.getStatus()));
        }
        return Mono.just(order);
    }

    private Mono<EffectReport> doSubmit(Long orderId, LocalDateTime reportTime,
                                        BigDecimal rainfallMm, BigDecimal hailSizeMm,
                                        BigDecimal areaKm2, String remark) {
        EffectReport report = EffectReport.submit(orderId,
                reportTime == null ? LocalDateTime.now(clock) : reportTime,
                rainfallMm, hailSizeMm, areaKm2, remark);
        return flowPort.submitReport(report)
                // 并发下两次提交都过了查重，uk_order 兜底
                .onErrorResume(DuplicateKeyException.class, e ->
                        Mono.error(new BizException("该指令已存在效果上报，一条指令只能报一份：id=" + orderId)));
    }
}
