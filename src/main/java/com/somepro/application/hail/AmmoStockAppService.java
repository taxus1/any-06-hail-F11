package com.somepro.application.hail;

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
 * 入库规则（核心）：同一作业点 + 同一种弹型 + 同一个批次，再入一次不另起一条，
 * 累加到原记录上 —— 去重与原子累加的具体落库在 {@code AmmoStockRepository#inbound}。
 * 这里负责：先确认作业点真实存在，再用领域工厂构造经校验的入库对象，交给仓储决定新增/累加。
 */
@Service
public class AmmoStockAppService {

    private final AmmoStockRepository ammoStockRepository;
    private final OperationSiteRepository siteRepository;

    public AmmoStockAppService(AmmoStockRepository ammoStockRepository,
                               OperationSiteRepository siteRepository) {
        this.ammoStockRepository = ammoStockRepository;
        this.siteRepository = siteRepository;
    }

    /**
     * 弹药入库。
     *
     * @param inboundQty 本次入库发数，必须为正
     */
    public Mono<AmmoStock> inbound(Long siteId, String ammoType, String batchNo, int inboundQty,
                                   LocalDate produceDate, LocalDate expireDate) {
        // 先校验必填/正数/日期先后等领域不变量（得到一条尚未落库的暂态库存，其数量即本次入库量）
        AmmoStock incoming = AmmoStock.newStock(
                siteId, ammoType, batchNo, inboundQty, produceDate, expireDate);
        // 弹药总得挂在某个真实作业点上（库存档案以库里为准，可能有早先数据）
        return siteRepository.findById(incoming.getSiteId())
                .switchIfEmpty(Mono.error(new BizException(
                        "作业点不存在：siteId=" + incoming.getSiteId())))
                .flatMap(site -> ammoStockRepository.inbound(incoming));
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
