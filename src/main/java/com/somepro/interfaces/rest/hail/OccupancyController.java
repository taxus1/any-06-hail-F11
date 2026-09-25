package com.somepro.interfaces.rest.hail;

import com.somepro.application.hail.FireOrderAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hail.converter.HailVoConverter;
import com.somepro.interfaces.rest.hail.vo.SiteDayOccupancyVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * 占用查法接口（用户接口层）。
 *
 * GET /api/hail/occupancy/site-day?siteId=..&day=yyyy-MM-dd
 * 挑个作业点、说个日子，返回这一天 24 个钟头每个整点被哪张空域、哪台装备占着；
 * 没占上的钟头也不落下，标空（occupied=false）。
 */
@RestController
@RequestMapping("/api/hail/occupancy")
public class OccupancyController {

    private final FireOrderAppService appService;

    public OccupancyController(FireOrderAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/site-day")
    public Mono<Result<SiteDayOccupancyVO>> siteDay(
            @RequestParam Long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return appService.siteDay(siteId, day)
                .map(HailVoConverter::toVo)
                .map(Result::ok);
    }
}
