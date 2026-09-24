package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.LauncherAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.HailPageVO;
import com.somepro.interfaces.rest.hail.vo.LauncherRegisterRequest;
import com.somepro.interfaces.rest.hail.vo.LauncherVO;
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
 * 发射装备台账接口（用户接口层）。
 *
 * - POST /api/hail/launchers       登记装备（必须挂在某个真实作业点上）
 * - GET  /api/hail/launchers/{id}  按 id 看单台装备
 * - GET  /api/hail/launchers       分页翻看，可按作业点 siteId / 状态 status 过滤
 */
@RestController
@RequestMapping("/api/hail/launchers")
public class LauncherController {

    private final LauncherAppService appService;

    public LauncherController(LauncherAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Mono<Result<LauncherVO>> register(@Valid @RequestBody LauncherRegisterRequest req) {
        return appService.register(req.launcherCode(), req.siteId(), req.model(),
                        req.barrelCount(), req.status(), req.checkDate())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<LauncherVO>> get(@PathVariable Long id) {
        return appService.getById(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping
    public Mono<Result<HailPageVO<LauncherVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return appService.page(pageNum, pageSize, siteId, status, keyword)
                .map(page -> HailVoConverter.toPageVo(page, HailVoConverter::toVo))
                .map(Result::ok);
    }
}
