package com.somepro.application.hail;

import com.somepro.domain.hail.model.AmmoBizType;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.hail.repository.AmmoRecordRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 弹药出入库流水应用服务：流水账只翻看（追加由入库 / 开单 / 回报事务负责）。
 */
@Service
public class AmmoRecordAppService {

    private final AmmoRecordRepository recordRepository;

    public AmmoRecordAppService(AmmoRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /** 分页翻看流水，可按作业点、弹型、业务类型（IN/OUT/RETURN/SCRAP）过滤。 */
    public Mono<PageResult<AmmoRecord>> page(int pageNum, int pageSize,
                                             Long siteId, String ammoType, String bizType) {
        String normalized = null;
        if (bizType != null && !bizType.isBlank()) {
            normalized = AmmoBizType.of(bizType).name();
        }
        return recordRepository.page(pageNum, pageSize, siteId, ammoType, normalized);
    }
}
