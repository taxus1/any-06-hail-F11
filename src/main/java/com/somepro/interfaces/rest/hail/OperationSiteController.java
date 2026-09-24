package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.OperationSiteAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.HailPageVO;
import com.somepro.interfaces.rest.hail.vo.OperationSiteVO;
import com.somepro.interfaces.rest.hail.vo.SiteRegisterRequest;
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
 * 作业点档案接口（用户接口层）：只做协议适配与 VO 转换，业务编排交给应用层。
 *
 * - POST /api/hail/sites           登记作业点（编号唯一）
 * - GET  /api/hail/sites/{id}      按 id 看单个点位
 * - GET  /api/hail/sites           分页翻看，keyword 同时匹配编号与名称
 */
@RestController
@RequestMapping("/api/hail/sites")
public class OperationSiteController {

    private final OperationSiteAppService appService;

    public OperationSiteController(OperationSiteAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Mono<Result<OperationSiteVO>> register(@Valid @RequestBody SiteRegisterRequest req) {
        return appService.register(req.siteCode(), req.siteName(), req.county(),
                        req.altitudeM(), req.contactName(), req.contactPhone(), req.status())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<OperationSiteVO>> get(@PathVariable Long id) {
        return appService.getById(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping
    public Mono<Result<HailPageVO<OperationSiteVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String county) {
        return appService.page(pageNum, pageSize, keyword, county)
                .map(page -> HailVoConverter.toPageVo(page, HailVoConverter::toVo))
                .map(Result::ok);
    }
}
