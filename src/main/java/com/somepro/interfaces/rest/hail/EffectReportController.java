package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.EffectReportAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.EffectReportRequest;
import com.somepro.interfaces.rest.hail.vo.EffectReportVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 作业效果上报接口（用户接口层）。一条指令一份。
 *
 * - POST /api/hail/orders/{id}/effects   按指令上报效果（指令须已完成回报）
 * - GET  /api/hail/effects/{id}          按效果记录 id 看
 * - GET  /api/hail/orders/{id}/effect    按指令查效果
 */
@RestController
@RequestMapping("/api/hail")
public class EffectReportController {

    private final EffectReportAppService appService;

    public EffectReportController(EffectReportAppService appService) {
        this.appService = appService;
    }

    @PostMapping("/orders/{id}/effects")
    public Mono<Result<EffectReportVO>> submit(@PathVariable("id") Long orderId,
                                               @Valid @RequestBody EffectReportRequest req) {
        return appService.submit(orderId, req.reportTime(), req.rainfallMm(),
                        req.hailSizeMm(), req.areaKm2(), req.remark())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/effects/{id}")
    public Mono<Result<EffectReportVO>> get(@PathVariable Long id) {
        return appService.getById(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/orders/{id}/effect")
    public Mono<Result<EffectReportVO>> getByOrder(@PathVariable("id") Long orderId) {
        return appService.getByOrderId(orderId)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }
}
