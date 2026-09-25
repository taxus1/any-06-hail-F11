package com.somepro.infrastructure.persistence.hail;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.hail.po.AirspaceApplyPO;
import com.somepro.infrastructure.persistence.hail.po.FireOrderPO;
import com.somepro.infrastructure.persistence.hail.po.LauncherPO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 作业主线事务执行器的单测：用 Mockito 顶掉全部 Mapper，不连库、不起 Spring / 事务，
 * 专测 {@link HailFlowTxExecutor} 里「行锁串行化 + 占单排查 + 条件作废 + 同生共死多写」的分支。
 *
 * 覆盖的硬规则：
 * - 开单先锁作业点；挂的空域必须已批且同点。
 * - 同一条空域已有在途单（不许拆单）/ 不同空域时段撞车，分别给出点名撞谁的报错。
 * - 装备不待命（被没完结单子用着）直接挡。
 * - 作废只认条件更新成功：已打过弹 / 已作废都退不了弹（幂等由 0 行更新兜底）。
 * - 作废成功才退弹 + 腾装备；顺序先条件作废后退弹，失败整笔回滚。
 */
class HailFlowTxExecutorTest {

    private final AmmoStockMapper stockMapper = mock(AmmoStockMapper.class);
    private final AmmoRecordMapper recordMapper = mock(AmmoRecordMapper.class);
    private final FireOrderMapper orderMapper = mock(FireOrderMapper.class);
    private final LauncherMapper launcherMapper = mock(LauncherMapper.class);
    private final EffectReportMapper reportMapper = mock(EffectReportMapper.class);
    private final AirspaceApplyMapper applyMapper = mock(AirspaceApplyMapper.class);
    private final OperationSiteMapper siteMapper = mock(OperationSiteMapper.class);

    private final HailFlowTxExecutor executor = new HailFlowTxExecutor(
            stockMapper, recordMapper, orderMapper, launcherMapper,
            reportMapper, applyMapper, siteMapper);

    private static final LocalDateTime WIN_START =
            LocalDateTime.of(2026, 10, 1, 14, 0);
    private static final LocalDateTime WIN_END =
            LocalDateTime.of(2026, 10, 1, 16, 0);

    @BeforeAll
    static void initTableInfo() {
        // 不起 SqlSessionFactory：手动把 PO 的表信息灌进 MyBatis-Plus lambda 缓存，
        // LambdaUpdateWrapper 才能解析 LauncherPO::getStatus 之类的列名。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, LauncherPO.class);
    }

    @BeforeEach
    void setUp() {
        AuditContextHolder.setOperator("tester");
    }

    @AfterEach
    void tearDown() {
        AuditContextHolder.clear();
    }

    private FireOrder freshOrder() {
        FireOrder order = FireOrder.issue("ZY-2026-0009", 10L, 1L, 5L, "BL-1A", 10);
        order.setId(900L);
        return order;
    }

    private AirspaceApplyPO approvedApply() {
        AirspaceApplyPO apply = new AirspaceApplyPO();
        apply.setId(10L);
        apply.setApplyNo("KQ-2026-0101");
        apply.setSiteId(1L);
        apply.setStatus("APPROVED");
        apply.setPlanStart(WIN_START);
        apply.setPlanEnd(WIN_END);
        return apply;
    }

    private void stubLockAndApply() {
        when(siteMapper.selectIdForUpdate(1L)).thenReturn(1L);
        when(applyMapper.selectById(10L)).thenReturn(approvedApply());
    }

    // ---------------- 开单 ----------------

    @Test
    void issue_siteLockMissing_throwsBeforeAnyWrite() {
        when(siteMapper.selectIdForUpdate(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> executor.doIssueOrder(freshOrder(), "B1"));
        assert ex.getMessage().contains("作业点不存在");
        verify(orderMapper, never()).insert(any(FireOrderPO.class));
        verify(launcherMapper, never()).update(any(), any());
    }

    @Test
    void issue_airspaceNotApproved_throws() {
        when(siteMapper.selectIdForUpdate(1L)).thenReturn(1L);
        AirspaceApplyPO pending = approvedApply();
        pending.setStatus("PENDING");
        when(applyMapper.selectById(10L)).thenReturn(pending);

        BizException ex = assertThrows(BizException.class,
                () -> executor.doIssueOrder(freshOrder(), "B1"));
        assert ex.getMessage().contains("已批");
        verify(orderMapper, never()).insert(any(FireOrderPO.class));
    }

    @Test
    void issue_sameAirspaceAlreadyOpen_blockedWithBlockerOrderNo() {
        stubLockAndApply();
        FireOrderPO blocker = new FireOrderPO();
        blocker.setId(800L);
        blocker.setOrderNo("ZY-2026-0008");
        blocker.setApplyId(10L);
        when(orderMapper.selectOpenBlocker(eq(1L), eq(10L), eq(WIN_START), eq(WIN_END)))
                .thenReturn(blocker);

        BizException ex = assertThrows(BizException.class,
                () -> executor.doIssueOrder(freshOrder(), "B1"));
        // 一张空域不许拆两张单：报出撞的是哪张单
        assert ex.getMessage().contains("ZY-2026-0008");
        assert ex.getMessage().contains("一张空域同时只能开一张单");
        verify(orderMapper, never()).insert(any(FireOrderPO.class));
    }

    @Test
    void issue_otherAirspaceOverlap_blockedAndNamesWhichAirspace() {
        stubLockAndApply();
        FireOrderPO blocker = new FireOrderPO();
        blocker.setId(800L);
        blocker.setOrderNo("ZY-2026-0008");
        blocker.setApplyId(11L);
        when(orderMapper.selectOpenBlocker(eq(1L), eq(10L), eq(WIN_START), eq(WIN_END)))
                .thenReturn(blocker);
        AirspaceApplyPO blockerApply = new AirspaceApplyPO();
        blockerApply.setId(11L);
        blockerApply.setApplyNo("KQ-2026-0088");
        when(orderMapper.selectById(800L)).thenReturn(blocker);
        when(applyMapper.selectById(11L)).thenReturn(blockerApply);

        BizException ex = assertThrows(BizException.class,
                () -> executor.doIssueOrder(freshOrder(), "B1"));
        // 两张空域时段撞车：报出撞的是哪张指令、哪张空域
        assert ex.getMessage().contains("ZY-2026-0008");
        assert ex.getMessage().contains("KQ-2026-0088");
        verify(orderMapper, never()).insert(any(FireOrderPO.class));
    }

    @Test
    void issue_launcherBusy_throwsAndRollsBackAmmo() {
        stubLockAndApply();
        when(orderMapper.selectOpenBlocker(any(), any(), any(), any())).thenReturn(null);
        when(stockMapper.selectUnique(1L, "BL-1A", "B1")).thenReturn(new com.somepro.infrastructure.persistence.hail.po.AmmoStockPO());
        when(stockMapper.deductQuantity(any(), anyInt(), anyString(), any())).thenReturn(1);
        // 装备被别的没完结单子用着（条件置 IN_USE 更新 0 行）
        when(launcherMapper.update(any(), ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LauncherPO>>any()))
                .thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> executor.doIssueOrder(freshOrder(), "B1"));
        assert ex.getMessage().contains("装备");
        // 指令没插进去（真实事务里前面扣的弹会随回滚还回）
        verify(orderMapper, never()).insert(any(FireOrderPO.class));
    }

    // ---------------- 作废 ----------------

    @Test
    void void_alreadyFired_blockedNoReturn() {
        FireOrder order = freshOrder();
        order.voidOut("收摊");
        when(orderMapper.voidIfFresh(eq(900L), any(), any(), any())).thenReturn(0);
        FireOrderPO current = new FireOrderPO();
        current.setId(900L);
        current.setStatus("ISSUED");
        current.setUsedRounds(3);
        when(orderMapper.selectById(900L)).thenReturn(current);

        BizException ex = assertThrows(BizException.class,
                () -> executor.doVoidOrder(order, "B1"));
        assert ex.getMessage().contains("已实弹发射");
        // 条件作废没成功，一发弹都不许退回
        verify(stockMapper, never()).addQuantity(any(), anyInt(), any(), any(), any(), any());
        verify(recordMapper, never()).insert(any(com.somepro.infrastructure.persistence.hail.po.AmmoRecordPO.class));
    }

    @Test
    void void_concurrentVoidLostRace_blockedNoReturn() {
        FireOrder order = freshOrder();
        order.voidOut("收摊");
        when(orderMapper.voidIfFresh(eq(900L), any(), any(), any())).thenReturn(0);
        FireOrderPO current = new FireOrderPO();
        current.setId(900L);
        current.setStatus("VOID");
        current.setUsedRounds(0);
        when(orderMapper.selectById(900L)).thenReturn(current);

        assertThrows(BizException.class, () -> executor.doVoidOrder(order, "B1"));
        // 第二遍作废绝不能再退一遍弹
        verify(stockMapper, never()).addQuantity(any(), anyInt(), any(), any(), any(), any());
        verify(recordMapper, never()).insert(any(com.somepro.infrastructure.persistence.hail.po.AmmoRecordPO.class));
    }

    @Test
    void void_fresh_returnsAllAmmoAndReleasesLauncher() {
        FireOrder order = freshOrder();
        order.voidOut("天气转好");
        when(orderMapper.voidIfFresh(eq(900L), eq("天气转好"), any(), any())).thenReturn(1);
        com.somepro.infrastructure.persistence.hail.po.AmmoStockPO stock =
                new com.somepro.infrastructure.persistence.hail.po.AmmoStockPO();
        stock.setId(77L);
        when(stockMapper.selectUnique(1L, "BL-1A", "B1")).thenReturn(stock);
        FireOrderPO after = new FireOrderPO();
        after.setId(900L);
        after.setOrderNo("ZY-2026-0009");
        after.setStatus("VOID");
        after.setUsedRounds(0);
        when(orderMapper.selectById(900L)).thenReturn(after);

        FireOrder result = executor.doVoidOrder(order, "B1");

        assert result.getStatus().name().equals("VOID");
        // 划走的 10 发原封退回结存
        verify(stockMapper).addQuantity(eq(77L), eq(10), any(), any(), any(), any());
        // 装备回待命（腾出装备 / 时段）
        verify(launcherMapper).update(any(), ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LauncherPO>>any());
    }
}
