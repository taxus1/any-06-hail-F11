package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.FireOrderAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.FireOrderIssueRequest;
import com.somepro.interfaces.rest.hail.vo.FireOrderVO;
import com.somepro.interfaces.rest.hail.vo.FireOrderVoidRequest;
import com.somepro.interfaces.rest.hail.vo.FireReportRequest;
import com.somepro.interfaces.rest.hail.vo.HailPageVO;
import com.somepro.interfaces.rest.hail.vo.SiteHourSlotVO;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 作业指令接口（用户接口层）。
 *
 * - POST /api/hail/orders                下达指令（只有已批空域能开；作业时段得落在批复时段内、
 *                                        同点不撞车、一空域一张单；开单即划弹并记 OUT 流水）
 * - POST /api/hail/orders/{id}/report    作业回报（实际发数；没打完的退回结存并记 RETURN）
 * - POST /api/hail/orders/{id}/void      作废（发都没打的单子收摊：计划发数原封退回、
 *                                        装备与时段腾出；重复点幂等，不再退弹）
 * - GET  /api/hail/orders/occupancy      按日看占用：挑个作业点、说个日子，
 *                                        24 个钟头各被哪条空域、哪台装备占着，空槽标空
 * - GET  /api/hail/orders/{id}           按 id 看单条
 * - GET  /api/hail/orders                分页翻看，可按作业点 / 状态过滤
 */
@RestController
@RequestMapping("/api/hail/orders")
public class FireOrderController {

    private final FireOrderAppService appService;

    public FireOrderController(FireOrderAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Mono<Result<FireOrderVO>> issue(@Valid @RequestBody FireOrderIssueRequest req) {
        return appService.issue(req.applyId(), req.launcherId(), req.ammoType(),
                        req.batchNo(), req.planRounds(), req.planStart(), req.planEnd())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @PostMapping("/{id}/report")
    public Mono<Result<FireOrderVO>> report(@PathVariable Long id,
                                            @Valid @RequestBody FireReportRequest req) {
        return appService.reportFire(id, req.usedRounds(), req.startTime(), req.endTime())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @PostMapping("/{id}/void")
    public Mono<Result<FireOrderVO>> voidOrder(@PathVariable Long id,
                                               @Valid @RequestBody FireOrderVoidRequest req) {
        return appService.voidOrder(id, req.reason())
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/occupancy")
    public Mono<Result<List<SiteHourSlotVO>>> occupancy(
            @RequestParam Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return appService.dailyOccupancy(siteId, day)
                .map(slots -> slots.stream().map(HailVoConverter::toVo).collect(Collectors.toList()))
                .map(Result::ok);
    }

    @GetMapping("/{id}")
    public Mono<Result<FireOrderVO>> get(@PathVariable Long id) {
        return appService.getById(id)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping
    public Mono<Result<HailPageVO<FireOrderVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String status) {
        return appService.page(pageNum, pageSize, siteId, status)
                .map(page -> HailVoConverter.toPageVo(page, HailVoConverter::toVo))
                .map(Result::ok);
    }
}
