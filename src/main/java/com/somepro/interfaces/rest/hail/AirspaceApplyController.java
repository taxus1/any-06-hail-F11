package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.AirspaceAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.AirspaceApplyRequest;
import com.somepro.interfaces.rest.hail.vo.AirspaceApplyVO;
import com.somepro.interfaces.rest.hail.vo.AirspaceRejectRequest;
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
 * 空域申请接口（用户接口层）。
 *
 * - POST /api/hail/airspaces                 新报空域申请（初始 PENDING，编号后端分配）
 * - POST /api/hail/airspaces/{id}/approve    批准（批下来的空域才能开作业指令）
 * - POST /api/hail/airspaces/{id}/reject     驳回（必须带原因）
 * - GET  /api/hail/airspaces/{id}            按 id 看单条
 * - GET  /api/hail/airspaces                 分页翻看，可按作业点 / 状态过滤
 */
@RestController
@RequestMapping("/api/hail/airspaces")
public class AirspaceApplyController {

    private final AirspaceAppService appService;

    public AirspaceApplyController(AirspaceAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Mono<Result<AirspaceApplyVO>> submit(@Valid @RequestBody AirspaceApplyRequest req) {
        return appService.submit(req.siteId(), req.purpose(), req.planStart(),
                        req.planEnd(), req.maxAltitude())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @PostMapping("/{id}/approve")
    public Mono<Result<AirspaceApplyVO>> approve(@PathVariable Long id) {
        return appService.approve(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @PostMapping("/{id}/reject")
    public Mono<Result<AirspaceApplyVO>> reject(@PathVariable Long id,
                                                @Valid @RequestBody AirspaceRejectRequest req) {
        return appService.reject(id, req.reason())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<AirspaceApplyVO>> get(@PathVariable Long id) {
        return appService.getById(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping
    public Mono<Result<HailPageVO<AirspaceApplyVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String status) {
        return appService.page(pageNum, pageSize, siteId, status)
                .map(page -> HailVoConverter.toPageVo(page, HailVoConverter::toVo))
                .map(Result::ok);
    }
}
