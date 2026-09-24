package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.AmmoRecordAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.AmmoRecordVO;
import com.somepro.interfaces.rest.hail.vo.HailPageVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 弹药出入库流水接口（用户接口层，只读账）。
 *
 * - GET /api/hail/ammo-records 分页翻看流水，可按作业点 / 弹型 / 业务类型过滤；
 *   changeQty 正入负出，bizType IN 入库 / OUT 领用 / RETURN 退回 / SCRAP 报废。
 */
@RestController
@RequestMapping("/api/hail/ammo-records")
public class AmmoRecordController {

    private final AmmoRecordAppService appService;

    public AmmoRecordController(AmmoRecordAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    public Mono<Result<HailPageVO<AmmoRecordVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String ammoType,
            @RequestParam(required = false) String bizType) {
        return appService.page(pageNum, pageSize, siteId, ammoType, bizType)
                .map(page -> HailVoConverter.toPageVo(page, HailVoConverter::toVo))
                .map(Result::ok);
    }
}
