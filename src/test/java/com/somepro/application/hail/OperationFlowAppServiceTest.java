package com.somepro.application.hail;

import com.somepro.application.hail.port.HailOperationFlowPort;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.model.AmmoRecord;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.model.EffectReport;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.model.OccupancySlot;
import com.somepro.domain.hail.model.OperationSite;
import com.somepro.domain.hail.model.OrderOccupancy;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 作业主线四块用例的应用层单测：空域申报批复、开单划弹、回报退弹、效果上报。
 * 不连库、不起 Spring，用 Mockito 顶掉仓储 / 事务端口，专测编排与跨聚合规则。
 */
class OperationFlowAppServiceTest {

    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-09-24T08:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private final AirspaceApplyRepositoryPort airspaceRepo = mock(AirspaceApplyRepositoryPort.class);
    private final FireOrderRepositoryPort orderRepo = mock(FireOrderRepositoryPort.class);
    private final OperationSiteRepositoryPort siteRepo = mock(OperationSiteRepositoryPort.class);
    private final LauncherRepositoryPort launcherRepo = mock(LauncherRepositoryPort.class);
    private final AmmoStockRepositoryPort stockRepo = mock(AmmoStockRepositoryPort.class);
    private final AmmoRecordRepositoryPort recordRepo = mock(AmmoRecordRepositoryPort.class);
    private final EffectReportRepositoryPort reportRepo = mock(EffectReportRepositoryPort.class);
    private final HailOperationFlowPort flowPort = mock(HailOperationFlowPort.class);

    private final AirspaceAppService airspaceApp =
            new AirspaceAppService(airspaceRepo, siteRepo, FIXED);
    private final FireOrderAppService orderApp = new FireOrderAppService(
            orderRepo, airspaceRepo, siteRepo, launcherRepo, stockRepo, recordRepo, flowPort, FIXED);
    private final EffectReportAppService reportApp =
            new EffectReportAppService(reportRepo, orderRepo, flowPort, FIXED);

    // ---------------- 空域 ----------------

    @Test
    void submitAirspace_success_generatesNoAndIsPending() {
        when(siteRepo.findById(1L)).thenReturn(Mono.just(activeSite(1L, "YY-013")));
        when(airspaceRepo.nextApplyNo(2026)).thenReturn(Mono.just("KQ-2026-0101"));
        when(airspaceRepo.save(any(AirspaceApply.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 14, 0);
        LocalDateTime end = start.plusHours(2);
        StepVerifier.create(airspaceApp.submit(1L, "hail", start, end, 6000))
                .assertNext(a -> {
                    assert "KQ-2026-0101".equals(a.getApplyNo());
                    assert a.getStatus().name().equals("PENDING");
                    assert a.getPurpose().name().equals("HAIL");
                })
                .verifyComplete();
    }

    @Test
    void submitAirspace_rejectsSuspendedSite() {
        OperationSite suspended = OperationSite.register("YY-099", "封存点", null,
                null, null, null, "SUSPENDED");
        suspended.setId(2L);
        when(siteRepo.findById(2L)).thenReturn(Mono.just(suspended));

        StepVerifier.create(airspaceApp.submit(2L, "RAIN",
                        LocalDateTime.of(2026, 10, 1, 14, 0),
                        LocalDateTime.of(2026, 10, 1, 16, 0), null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("不在册"))
                .verify();
        verify(airspaceRepo, never()).save(any());
    }

    @Test
    void submitAirspace_rejectsEndBeforeStart() {
        // 时刻先后在领域工厂阶段同步 fast-fail
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                airspaceApp.submit(1L, "RAIN",
                        LocalDateTime.of(2026, 10, 1, 16, 0),
                        LocalDateTime.of(2026, 10, 1, 14, 0), null));
    }

    @Test
    void approveAirspace_success() {
        AirspaceApply pending = pendingApply(10L, 1L);
        when(airspaceRepo.findById(10L)).thenReturn(Mono.just(pending));
        when(airspaceRepo.save(any(AirspaceApply.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(airspaceApp.approve(10L))
                .assertNext(a -> {
                    assert a.getStatus().name().equals("APPROVED");
                    assert a.getApproveTime() != null;
                })
                .verifyComplete();
    }

    @Test
    void approveAirspace_rejectsAlreadyApproved() {
        AirspaceApply approved = pendingApply(10L, 1L);
        approved.approve(LocalDateTime.now(FIXED));
        when(airspaceRepo.findById(10L)).thenReturn(Mono.just(approved));

        StepVerifier.create(airspaceApp.approve(10L))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("只有待批"))
                .verify();
    }

    @Test
    void rejectAirspace_requiresReason() {
        when(airspaceRepo.findById(10L)).thenReturn(Mono.just(pendingApply(10L, 1L)));

        StepVerifier.create(airspaceApp.reject(10L, "  "))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("驳回必须写明原因"))
                .verify();
        verify(airspaceRepo, never()).save(any());
    }

    @Test
    void submitAirspace_retriesNoOnDuplicate() {
        when(siteRepo.findById(1L)).thenReturn(Mono.just(activeSite(1L, "YY-013")));
        when(airspaceRepo.nextApplyNo(2026))
                .thenReturn(Mono.just("KQ-2026-0001"), Mono.just("KQ-2026-0002"));
        when(airspaceRepo.save(any(AirspaceApply.class)))
                .thenAnswer(inv -> {
                    AirspaceApply a = inv.getArgument(0);
                    if ("KQ-2026-0001".equals(a.getApplyNo())) {
                        return Mono.error(new DuplicateKeyException("uk_apply_no"));
                    }
                    return Mono.just(a);
                });

        StepVerifier.create(airspaceApp.submit(1L, "RAIN",
                        LocalDateTime.of(2026, 10, 1, 14, 0),
                        LocalDateTime.of(2026, 10, 1, 16, 0), null))
                .assertNext(a -> {
                    assert "KQ-2026-0002".equals(a.getApplyNo());
                })
                .verifyComplete();
    }

    // ---------------- 开单 ----------------

    @Test
    void issueOrder_rejectsPendingAirspace() {
        when(airspaceRepo.findById(10L)).thenReturn(Mono.just(pendingApply(10L, 1L)));

        StepVerifier.create(orderApp.issue(10L, 5L, "BL-1A", "B1", 10))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("已批"))
                .verify();
        verify(flowPort, never()).issueOrder(any(), any());
    }

    @Test
    void issueOrder_rejectsLauncherOfAnotherSite() {
        when(airspaceRepo.findById(10L)).thenReturn(Mono.just(approvedApply(10L, 1L)));
        when(siteRepo.findById(1L)).thenReturn(Mono.just(activeSite(1L, "YY-013")));
        Launcher other = Launcher.register("ZB-0008", 2L, "QF-12", 12, "READY", null);
        other.setId(5L);
        when(launcherRepo.findById(5L)).thenReturn(Mono.just(other));

        StepVerifier.create(orderApp.issue(10L, 5L, "BL-1A", "B1", 10))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("不归属"))
                .verify();
        verify(flowPort, never()).issueOrder(any(), any());
    }

    @Test
    void issueOrder_rejectsBusyLauncher() {
        when(airspaceRepo.findById(10L)).thenReturn(Mono.just(approvedApply(10L, 1L)));
        when(siteRepo.findById(1L)).thenReturn(Mono.just(activeSite(1L, "YY-013")));
        Launcher busy = Launcher.register("ZB-0007", 1L, "QF-12", 12, "IN_USE", null);
        busy.setId(5L);
        when(launcherRepo.findById(5L)).thenReturn(Mono.just(busy));

        StepVerifier.create(orderApp.issue(10L, 5L, "BL-1A", "B1", 10))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("待命"))
                .verify();
    }

    @Test
    void issueOrder_rejectsInsufficientStock() {
        prepareIssuePrerequisites();
        when(stockRepo.findUnique(1L, "BL-1A", "B1")).thenReturn(Mono.just(
                AmmoStock.newStock(1L, "BL-1A", "B1", 8, null, LocalDate.of(2028, 1, 1))));

        StepVerifier.create(orderApp.issue(10L, 5L, "BL-1A", "B1", 10))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("结存不足"))
                .verify();
        verify(flowPort, never()).issueOrder(any(), any());
    }

    @Test
    void issueOrder_rejectsExpiredBatch() {
        prepareIssuePrerequisites();
        when(stockRepo.findUnique(1L, "BL-1A", "B1")).thenReturn(Mono.just(
                AmmoStock.newStock(1L, "BL-1A", "B1", 100, null, LocalDate.of(2026, 9, 1))));

        StepVerifier.create(orderApp.issue(10L, 5L, "BL-1A", "B1", 10))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("过有效期"))
                .verify();
    }

    @Test
    void issueOrder_success_goesThroughFlowPortWithOrderNo() {
        prepareIssuePrerequisites();
        when(stockRepo.findUnique(1L, "BL-1A", "B1")).thenReturn(Mono.just(
                AmmoStock.newStock(1L, "BL-1A", "B1", 100, null, LocalDate.of(2028, 1, 1))));
        when(orderRepo.nextOrderNo(2026)).thenReturn(Mono.just("ZY-2026-0101"));
        when(flowPort.issueOrder(any(FireOrder.class), eq("B1")))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(orderApp.issue(10L, 5L, "BL-1A", "B1", 10))
                .assertNext(o -> {
                    assert "ZY-2026-0101".equals(o.getOrderNo());
                    assert o.getPlanRounds() == 10;
                    assert o.getUsedRounds() == 0;
                    assert o.getStatus().name().equals("ISSUED");
                    assert o.getApplyId() == 10L && o.getSiteId() == 1L && o.getLauncherId() == 5L;
                })
                .verifyComplete();
        verify(flowPort).issueOrder(org.mockito.ArgumentMatchers.argThat(
                o -> "BL-1A".equals(o.getAmmoType()) && o.getPlanRounds() == 10), eq("B1"));
    }

    @Test
    void issueOrder_retriesNoOnDuplicate() {
        prepareIssuePrerequisites();
        when(stockRepo.findUnique(1L, "BL-1A", "B1")).thenReturn(Mono.just(
                AmmoStock.newStock(1L, "BL-1A", "B1", 100, null, LocalDate.of(2028, 1, 1))));
        when(orderRepo.nextOrderNo(2026))
                .thenReturn(Mono.just("ZY-2026-0001"), Mono.just("ZY-2026-0002"));
        when(flowPort.issueOrder(any(FireOrder.class), eq("B1"))).thenAnswer(inv -> {
            FireOrder o = inv.getArgument(0);
            if ("ZY-2026-0001".equals(o.getOrderNo())) {
                return Mono.error(new DuplicateKeyException("uk_order_no"));
            }
            return Mono.just(o);
        });

        StepVerifier.create(orderApp.issue(10L, 5L, "BL-1A", "B1", 10))
                .assertNext(o -> {
                    assert "ZY-2026-0002".equals(o.getOrderNo());
                })
                .verifyComplete();
    }

    // ---------------- 回报 ----------------

    /** 回报用实际时刻：落在测试空域（2026-10-01 14:00–16:00）批复时段内。 */
    private static final LocalDateTime IN_WINDOW_START =
            LocalDateTime.of(2026, 10, 1, 14, 30);
    private static final LocalDateTime IN_WINDOW_END =
            LocalDateTime.of(2026, 10, 1, 15, 0);

    /** 给回报链路补齐「查指令挂的空域」桩（时段边界校验要读空域批复时段）。 */
    private void stubReportPrerequisites(FireOrder order) {
        when(orderRepo.findById(order.getId())).thenReturn(Mono.just(order));
        when(airspaceRepo.findById(10L)).thenReturn(Mono.just(approvedApply(10L, 1L)));
        when(recordRepo.findOutRecord(1L, "ZY-2026-0101"))
                .thenReturn(Mono.just(AmmoRecord.out(1L, "BL-1A", "B1", 10, "ZY-2026-0101")));
    }

    @Test
    void reportFire_partialUsed_returnsRemainderViaFlowPort() {
        FireOrder order = issuedOrder(20L, 10);
        stubReportPrerequisites(order);
        when(flowPort.reportFire(any(FireOrder.class), eq("B1")))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(orderApp.reportFire(20L, 7, IN_WINDOW_START, IN_WINDOW_END))
                .assertNext(o -> {
                    assert o.getUsedRounds() == 7;
                    assert o.getStatus().name().equals("DONE");
                    // plan - used = 3 发要退回，退回数由适配器据 usedRounds 算，这里校验指令状态
                    assert o.getPlanRounds() - o.getUsedRounds() == 3;
                })
                .verifyComplete();
        verify(flowPort).reportFire(org.mockito.ArgumentMatchers.argThat(
                o -> o.getStatus().name().equals("DONE")), eq("B1"));
    }

    @Test
    void reportFire_allUsed_noReturnNeeded() {
        FireOrder order = issuedOrder(20L, 10);
        stubReportPrerequisites(order);
        when(flowPort.reportFire(any(FireOrder.class), eq("B1")))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(orderApp.reportFire(20L, 10, IN_WINDOW_START, IN_WINDOW_END))
                .assertNext(o -> {
                    assert o.getUsedRounds() == 10;
                })
                .verifyComplete();
    }

    @Test
    void reportFire_rejectsUsedOverPlan() {
        FireOrder order = issuedOrder(20L, 10);
        stubReportPrerequisites(order);

        StepVerifier.create(orderApp.reportFire(20L, 11, IN_WINDOW_START, IN_WINDOW_END))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("超过计划发数"))
                .verify();
        verify(flowPort, never()).reportFire(any(), any());
    }

    @Test
    void reportFire_rejectsDoubleReport() {
        FireOrder done = issuedOrder(20L, 10);
        done.reportFire(10, IN_WINDOW_START, IN_WINDOW_END);
        when(orderRepo.findById(20L)).thenReturn(Mono.just(done));
        when(airspaceRepo.findById(10L)).thenReturn(Mono.just(approvedApply(10L, 1L)));
        when(recordRepo.findOutRecord(1L, "ZY-2026-0101"))
                .thenReturn(Mono.just(AmmoRecord.out(1L, "BL-1A", "B1", 10, "ZY-2026-0101")));

        StepVerifier.create(orderApp.reportFire(20L, 5, IN_WINDOW_START, IN_WINDOW_END))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("不能重复回报"))
                .verify();
    }

    @Test
    void reportFire_rejectsStartBeforeAirspaceWindow() {
        FireOrder order = issuedOrder(20L, 10);
        stubReportPrerequisites(order);

        // 空域 14:00 才开始，13:59 开打 = 起早了
        StepVerifier.create(orderApp.reportFire(20L, 1,
                        LocalDateTime.of(2026, 10, 1, 13, 59), IN_WINDOW_END))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("起早了"))
                .verify();
        verify(flowPort, never()).reportFire(any(), any());
    }

    @Test
    void reportFire_rejectsEndAfterAirspaceWindow() {
        FireOrder order = issuedOrder(20L, 10);
        stubReportPrerequisites(order);

        // 空域 16:00 收尾，打到 16:01 = 拖晚了
        StepVerifier.create(orderApp.reportFire(20L, 1,
                        IN_WINDOW_START, LocalDateTime.of(2026, 10, 1, 16, 1)))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("拖晚了"))
                .verify();
        verify(flowPort, never()).reportFire(any(), any());
    }

    @Test
    void reportFire_boundaryTouchAllowed() {
        FireOrder order = issuedOrder(20L, 10);
        stubReportPrerequisites(order);
        when(flowPort.reportFire(any(FireOrder.class), eq("B1")))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // 作业完整落在时段里：恰在 14:00 起、16:00 收（边界点允许）
        StepVerifier.create(orderApp.reportFire(20L, 2,
                        LocalDateTime.of(2026, 10, 1, 14, 0),
                        LocalDateTime.of(2026, 10, 1, 16, 0)))
                .expectNextCount(1)
                .verifyComplete();
    }

    // ---------------- 作废 ----------------

    @Test
    void voidOrder_freshNoFire_delegatesToFlowPort() {
        FireOrder order = issuedOrder(20L, 10);
        when(orderRepo.findById(20L)).thenReturn(Mono.just(order));
        when(recordRepo.findOutRecord(1L, "ZY-2026-0101"))
                .thenReturn(Mono.just(AmmoRecord.out(1L, "BL-1A", "B1", 10, "ZY-2026-0101")));
        when(flowPort.voidOrder(any(FireOrder.class), eq("B1")))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(orderApp.voidOrder(20L, "天气转好，收摊"))
                .assertNext(o -> {
                    assert o.getStatus().name().equals("VOID");
                    assert "天气转好，收摊".equals(o.getVoidReason());
                })
                .verifyComplete();
        verify(flowPort).voidOrder(org.mockito.ArgumentMatchers.argThat(
                o -> o.getStatus().name().equals("VOID")), eq("B1"));
    }

    @Test
    void voidOrder_alreadyVoid_returnsAsIsWithoutTouchingFlowPort() {
        // 连点第二遍：单子已是 VOID，原样返回，绝不允许再进退弹事务（不能再退一遍弹）
        FireOrder order = issuedOrder(20L, 10);
        order.voidOut("收摊");
        when(orderRepo.findById(20L)).thenReturn(Mono.just(order));

        StepVerifier.create(orderApp.voidOrder(20L, "收摊"))
                .assertNext(o -> {
                    assert o.getStatus().name().equals("VOID");
                    assert "收摊".equals(o.getVoidReason());
                })
                .verifyComplete();
        verify(flowPort, never()).voidOrder(any(), any());
        verify(recordRepo, never()).findOutRecord(any(), any());
    }

    @Test
    void voidOrder_rejectsDone() {
        FireOrder done = issuedOrder(20L, 10);
        done.reportFire(0, IN_WINDOW_START, IN_WINDOW_END);
        when(orderRepo.findById(20L)).thenReturn(Mono.just(done));

        StepVerifier.create(orderApp.voidOrder(20L, null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("不能作废"))
                .verify();
        verify(flowPort, never()).voidOrder(any(), any());
    }

    // ---------------- 占用查法 ----------------

    @Test
    void siteDay_marksOccupiedAndEmptyHours() {
        when(siteRepo.findById(1L)).thenReturn(Mono.just(activeSite(1L, "YY-013")));
        // 在途单子挂的空域 09:00–11:00：09、10 两个整点格子被占，其余标空
        OrderOccupancy occupant = new OrderOccupancy(
                20L, "ZY-2026-0101", 10L, "KQ-2026-0101", 5L, "ZB-0007",
                LocalDateTime.of(2026, 10, 1, 9, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0),
                LocalDateTime.of(2026, 9, 30, 8, 0));
        when(orderRepo.findActiveOccupancies(eq(1L),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 2, 0, 0))))
                .thenReturn(Mono.just(java.util.List.of(occupant)));

        StepVerifier.create(orderApp.siteDay(1L, LocalDate.of(2026, 10, 1)))
                .assertNext(day -> {
                    assert day.slots().size() == 24;
                    assert day.siteCode().equals("YY-013");
                    assert !day.slots().get(8).occupied();          // 08:00 空
                    assert "KQ-2026-0101".equals(day.slots().get(9).applyNo());
                    assert "ZB-0007".equals(day.slots().get(9).launcherCode());
                    assert "ZY-2026-0101".equals(day.slots().get(10).orderNo());
                    assert !day.slots().get(11).occupied();         // 11:00–12:00 已腾出
                    long occupiedCount = day.slots().stream().filter(OccupancySlot::occupied).count();
                    assert occupiedCount == 2;
                })
                .verifyComplete();
    }

    @Test
    void siteDay_oneMinuteOverlapStillOccupies() {
        when(siteRepo.findById(1L)).thenReturn(Mono.just(activeSite(1L, "YY-013")));
        // 空域 09:59–10:00，与 09:00 格子只撞一分钟，也算占
        OrderOccupancy occupant = new OrderOccupancy(
                20L, "ZY-2026-0101", 10L, "KQ-2026-0101", 5L, "ZB-0007",
                LocalDateTime.of(2026, 10, 1, 9, 59),
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 9, 30, 8, 0));
        when(orderRepo.findActiveOccupancies(any(), any(), any()))
                .thenReturn(Mono.just(java.util.List.of(occupant)));

        StepVerifier.create(orderApp.siteDay(1L, LocalDate.of(2026, 10, 1)))
                .assertNext(day -> {
                    assert day.slots().get(9).occupied();
                    assert day.slots().get(8).occupied() == false;
                    assert day.slots().get(10).occupied() == false; // 边界相接（10:00）不占下一格
                })
                .verifyComplete();
    }

    // ---------------- 效果 ----------------

    @Test
    void submitEffect_success_whenOrderDone() {
        FireOrder done = issuedOrder(20L, 10);
        done.reportFire(7, null, null);
        when(orderRepo.findById(20L)).thenReturn(Mono.just(done));
        when(reportRepo.findByOrderId(20L)).thenReturn(Mono.empty());
        when(flowPort.submitReport(any(EffectReport.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(reportApp.submit(20L, null, new BigDecimal("12.50"),
                        new BigDecimal("0.00"), new BigDecimal("36.80"), "增雨明显"))
                .assertNext(r -> {
                    assert r.getRainfallMm().compareTo(new BigDecimal("12.50")) == 0;
                    assert r.getAreaKm2().compareTo(new BigDecimal("36.80")) == 0;
                    assert r.getReportTime() != null;
                })
                .verifyComplete();
    }

    @Test
    void submitEffect_rejectsBeforeOrderDone() {
        when(orderRepo.findById(20L)).thenReturn(Mono.just(issuedOrder(20L, 10)));

        StepVerifier.create(reportApp.submit(20L, null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("完成回报"))
                .verify();
        verify(flowPort, never()).submitReport(any());
    }

    @Test
    void submitEffect_rejectsDuplicate() {
        FireOrder done = issuedOrder(20L, 10);
        done.reportFire(10, null, null);
        when(orderRepo.findById(20L)).thenReturn(Mono.just(done));
        when(reportRepo.findByOrderId(20L)).thenReturn(Mono.just(
                EffectReport.submit(20L, LocalDateTime.now(FIXED), BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ONE, null)));

        StepVerifier.create(reportApp.submit(20L, null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, null))
                .expectErrorMatches(e -> e instanceof BizException && e.getMessage().contains("一条指令只能报一份"))
                .verify();
        verify(flowPort, never()).submitReport(any());
    }

    @Test
    void submitEffect_rejectsNegativeMetric() {
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () ->
                EffectReport.submit(20L, null, new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ONE, null));
    }

    // ---------------- helpers ----------------

    private void prepareIssuePrerequisites() {
        when(airspaceRepo.findById(10L)).thenReturn(Mono.just(approvedApply(10L, 1L)));
        when(siteRepo.findById(1L)).thenReturn(Mono.just(activeSite(1L, "YY-013")));
        Launcher ready = Launcher.register("ZB-0007", 1L, "QF-12", 12, "READY",
                LocalDate.of(2026, 9, 1));
        ready.setId(5L);
        when(launcherRepo.findById(5L)).thenReturn(Mono.just(ready));
    }

    private OperationSite activeSite(Long id, String code) {
        OperationSite site = OperationSite.register(code, "南山点", "海林县", 820,
                "张三", "13800000000", "ACTIVE");
        site.setId(id);
        return site;
    }

    private AirspaceApply pendingApply(Long id, Long siteId) {
        AirspaceApply apply = AirspaceApply.submit("KQ-2026-0101", siteId, "HAIL",
                LocalDateTime.of(2026, 10, 1, 14, 0),
                LocalDateTime.of(2026, 10, 1, 16, 0), 6000);
        apply.setId(id);
        return apply;
    }

    private AirspaceApply approvedApply(Long id, Long siteId) {
        AirspaceApply apply = pendingApply(id, siteId);
        apply.approve(LocalDateTime.now(FIXED));
        return apply;
    }

    private FireOrder issuedOrder(Long id, int planRounds) {
        FireOrder order = FireOrder.issue(
                "ZY-2026-0101", 10L, 1L, 5L, "BL-1A", planRounds);
        order.setId(id);
        return order;
    }

    // 测试内别名，缩短 new 泛型 mock 的写法
    interface AirspaceApplyRepositoryPort extends com.somepro.domain.hail.repository.AirspaceApplyRepository {
    }

    interface FireOrderRepositoryPort extends com.somepro.domain.hail.repository.FireOrderRepository {
    }

    interface OperationSiteRepositoryPort extends com.somepro.domain.hail.repository.OperationSiteRepository {
    }

    interface LauncherRepositoryPort extends com.somepro.domain.hail.repository.LauncherRepository {
    }

    interface AmmoStockRepositoryPort extends com.somepro.domain.hail.repository.AmmoStockRepository {
    }

    interface AmmoRecordRepositoryPort extends com.somepro.domain.hail.repository.AmmoRecordRepository {
    }

    interface EffectReportRepositoryPort extends com.somepro.domain.hail.repository.EffectReportRepository {
    }
}
