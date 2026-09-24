package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.AmmoStockAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.AmmoInboundRequest;
import com.somepro.interfaces.rest.hail.vo.AmmoStockVO;
import com.somepro.interfaces.rest.hail.vo.HailPageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 弹药库存接口（用户接口层）。
 *
 * - POST /api/hail/ammo/inbound    弹药入库；同点 + 同弹型 + 同批次再入一次累加原记录
 * - GET  /api/hail/ammo/{id}       按 id 看单条库存
 * - GET  /api/hail/ammo            分页翻看，可按作业点 / 弹型 / 批次号过滤
 */
@RestController
@RequestMapping("/api/hail/ammo")
public class AmmoStockController {

    private final AmmoStockAppService appService;

    public AmmoStockController(AmmoStockAppService appService) {
        this.appService = appService;
    }

    @PostMapping("/inbound")
    public Mono<Result<AmmoStockVO>> inbound(@Valid @RequestBody AmmoInboundRequest req) {
        return appService.inbound(req.siteId(), req.ammoType(), req.batchNo(),
                        req.quantity(), req.produceDate(), req.expireDate())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<AmmoStockVO>> get(@PathVariable Long id) {
        return appService.getById(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping
    public Mono<Result<HailPageVO<AmmoStockVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String ammoType,
            @RequestParam(required = false) String batchNo) {
        return appService.page(pageNum, pageSize, siteId, ammoType, batchNo)
                .map(page -> HailVoConverter.toPageVo(page, HailVoConverter::toVo))
                .map(Result::ok);
    }
}
