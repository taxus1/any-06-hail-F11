package com.somepro.application.hail;

import com.somepro.application.hail.port.HailOperationFlowPort;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.model.OperationSite;
import com.somepro.domain.hail.repository.AmmoStockRepository;
import com.somepro.domain.hail.repository.LauncherRepository;
import com.somepro.domain.hail.repository.OperationSiteRepository;
import com.somepro.domain.shared.model.PageResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 应用层编排单测：不连库、不起 Spring，用 Mockito 顶掉仓储端口，
 * 专测跨聚合规则（编号查重、装备必须有主、入库累加语义）与枚举校验。
 */
class HailAppServiceTest {

    private final OperationSiteRepository siteRepo = mock(OperationSiteRepository.class);
    private final LauncherRepository launcherRepo = mock(LauncherRepository.class);
    private final AmmoStockRepository ammoRepo = mock(AmmoStockRepository.class);
    private final HailOperationFlowPort flowPort = mock(HailOperationFlowPort.class);

    private final OperationSiteAppService siteApp = new OperationSiteAppService(siteRepo);
    private final LauncherAppService launcherApp = new LauncherAppService(launcherRepo, siteRepo);
    private final AmmoStockAppService ammoApp = new AmmoStockAppService(ammoRepo, siteRepo, flowPort);

    @Test
    void registerSite_success_whenCodeFree() {
        when(siteRepo.findByCode("YY-013")).thenReturn(Mono.empty());
        when(siteRepo.save(any(OperationSite.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(siteApp.register("YY-013", "南山西点", "海林县", 820, "张三", "13800000000", "ACTIVE"))
                .assertNext(s -> {
                    assert "YY-013".equals(s.getSiteCode());
                    assert s.getStatus().name().equals("ACTIVE");
                })
                .verifyComplete();
        verify(siteRepo).save(any(OperationSite.class));
    }

    @Test
    void registerSite_rejectsDuplicateCode_fromExistingRows() {
        OperationSite existing = OperationSite.register("YY-013", "旧点", null, null, null, null, "ACTIVE");
        when(siteRepo.findByCode("YY-013")).thenReturn(Mono.just(existing));

        StepVerifier.create(siteApp.register("YY-013", "新点", null, null, null, null, "ACTIVE"))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("编号已存在"))
                .verify();
        verify(siteRepo, never()).save(any());
    }

    @Test
    void registerSite_rejectsIllegalStatus() {
        // 非法状态在领域工厂组装阶段即 fast-fail（同步抛 BizException）
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                siteApp.register("YY-014", "点", null, null, null, null, "ARCHIVED"));
        verify(siteRepo, never()).save(any());
    }

    @Test
    void registerLauncher_rejectsOrphanSite() {
        when(siteRepo.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(launcherApp.register("ZB-0007", 99L, "QF-12", 12, "READY", null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("归属作业点不存在"))
                .verify();
        verify(launcherRepo, never()).save(any());
    }

    @Test
    void registerLauncher_rejectsNullSite() {
        // siteId 空在领域工厂阶段即拒
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                launcherApp.register("ZB-0008", null, "QF-12", 12, "READY", null));
    }

    @Test
    void registerLauncher_success_whenSiteExistsAndCodeFree() {
        OperationSite site = OperationSite.register("YY-013", "点", null, null, null, null, "ACTIVE");
        site.setId(1L);
        when(siteRepo.findById(1L)).thenReturn(Mono.just(site));
        when(launcherRepo.findByCode("ZB-0007")).thenReturn(Mono.empty());
        when(launcherRepo.save(any(Launcher.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(launcherApp.register("ZB-0007", 1L, "QF-12", 12, "ready", LocalDate.of(2026, 9, 1)))
                .assertNext(l -> {
                    assert l.getSiteId() == 1L;
                    // 小写状态也应被枚举归一化
                    assert l.getStatus().name().equals("READY");
                    assert l.getCheckDate().equals(LocalDate.of(2026, 9, 1));
                })
                .verifyComplete();
    }

    @Test
    void registerLauncher_rejectsDuplicateCode() {
        OperationSite site = OperationSite.register("YY-013", "点", null, null, null, null, "ACTIVE");
        site.setId(1L);
        when(siteRepo.findById(1L)).thenReturn(Mono.just(site));
        Launcher existing = Launcher.register("ZB-0007", 1L, "x", 1, "READY", null);
        when(launcherRepo.findByCode("ZB-0007")).thenReturn(Mono.just(existing));

        StepVerifier.create(launcherApp.register("ZB-0007", 1L, "QF-12", 12, "READY", null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("装备编号已存在"))
                .verify();
        verify(launcherRepo, never()).save(any());
    }

    @Test
    void inbound_rejectsUnknownSite() {
        when(siteRepo.findById(anyLong())).thenReturn(Mono.empty());
        StepVerifier.create(ammoApp.inbound(404L, "BL-1A", "B1", 10, null, null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("作业点不存在"))
                .verify();
        verify(flowPort, never()).inboundWithRecord(any());
    }

    @Test
    void inbound_rejectsNonPositiveQty() {
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                ammoApp.inbound(1L, "BL-1A", "B1", 0, null, null));
    }

    @Test
    void inbound_rejectsExpireBeforeProduce() {
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                ammoApp.inbound(1L, "BL-1A", "B1", 10,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1)));
    }

    @Test
    void inbound_delegatesToFlowPort_whenSiteExists() {
        OperationSite site = OperationSite.register("YY-013", "点", null, null, null, null, "ACTIVE");
        site.setId(1L);
        when(siteRepo.findById(1L)).thenReturn(Mono.just(site));
        // 入库累加 + IN 流水由事务端口完成，返回结存 150
        AmmoStock merged = AmmoStock.newStock(1L, "BL-1A", "B2026-01", 150,
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        merged.setId(100L);
        when(flowPort.inboundWithRecord(any(AmmoStock.class))).thenReturn(Mono.just(merged));

        StepVerifier.create(ammoApp.inbound(1L, "BL-1A", "B2026-01", 50,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1)))
                .assertNext(s -> {
                    assert s.getId() == 100L;
                    assert s.getQuantity() == 150;
                })
                .verifyComplete();
        // 暂态入库对象携带的是本次入库量 50，累加与流水由事务端口完成
        verify(flowPort).inboundWithRecord(org.mockito.ArgumentMatchers.argThat(
                a -> a.getQuantity() == 50 && a.getAmmoType().equals("BL-1A")));
    }

    @Test
    void launcherPage_rejectsIllegalStatus() {
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                launcherApp.page(1, 20, null, "BROKEN", null));
        verify(launcherRepo, never()).page(anyInt(), anyInt(), any(), anyString(), any());
    }

    @Test
    void launcherPage_passesNormalizedStatus() {
        PageResult<Launcher> emptyPage = new PageResult<>(java.util.List.of(), 0, 1, 20);
        when(launcherRepo.page(eq(1), eq(20), eq(null), eq("READY"), eq(null)))
                .thenReturn(Mono.just(emptyPage));
        // 验证小写状态被枚举归一化为 READY 后再透传给仓储
        StepVerifier.create(launcherApp.page(1, 20, null, "ready", null))
                .expectNext(emptyPage)
                .verifyComplete();
        verify(launcherRepo).page(1, 20, null, "READY", null);
    }
}
