package com.somepro.application.hail;

import com.somepro.application.hail.port.HailOperationFlowPort;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.repository.AmmoStockRepository;
import com.somepro.domain.hail.repository.OperationSiteRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * 弹药应用服务：编排「入库、翻看」用例（应用层）。
 *
 * 入库是一次跨两表的业务动作：库存累加 + 一笔 IN 入库流水，必须同事务，
 * 所以写操作统一走 {@link HailOperationFlowPort#inboundWithRecord}；本服务只负责
 * 先确认作业点真实存在、用领域工厂构造经校验的入库对象。
 */
@Service
public class AmmoStockAppService {

    private final AmmoStockRepository ammoStockRepository;
    private final OperationSiteRepository siteRepository;
    private final HailOperationFlowPort flowPort;

    public AmmoStockAppService(AmmoStockRepository ammoStockRepository,
                               OperationSiteRepository siteRepository,
                               HailOperationFlowPort flowPort) {
        this.ammoStockRepository = ammoStockRepository;
        this.siteRepository = siteRepository;
        this.flowPort = flowPort;
    }

    /**
     * 弹药入库：同点 + 同弹型 + 同批次累加原记录，并在流水账上记一笔 IN。
     *
     * @param inboundQty 本次入库发数，必须为正
     */
    public Mono<AmmoStock> inbound(Long siteId, String ammoType, String batchNo, int inboundQty,
                                   LocalDate produceDate, LocalDate expireDate) {
        AmmoStock incoming = AmmoStock.newStock(
                siteId, ammoType, batchNo, inboundQty, produceDate, expireDate);
        return siteRepository.findById(incoming.getSiteId())
                .switchIfEmpty(Mono.error(new BizException(
                        "作业点不存在：siteId=" + incoming.getSiteId())))
                .flatMap(site -> flowPort.inboundWithRecord(incoming));
    }

    public Mono<AmmoStock> getById(Long id) {
        return ammoStockRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("库存记录不存在：id=" + id)));
    }

    /** 分页翻看库存，可按作业点、弹型、批次号过滤。 */
    public Mono<PageResult<AmmoStock>> page(int pageNum, int pageSize,
                                            Long siteId, String ammoType, String batchNo) {
        return ammoStockRepository.page(pageNum, pageSize, siteId, ammoType, batchNo);
    }
}
