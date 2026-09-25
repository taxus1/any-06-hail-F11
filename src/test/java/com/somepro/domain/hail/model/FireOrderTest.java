package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

/**
 * 作业指令领域规则单测：作废守卫 / 作废幂等 / 空域时段边界。
 * 不连库、不起 Spring，纯领域对象断言。
 */
class FireOrderTest {

    private static final LocalDateTime AIR_START =
            LocalDateTime.of(2026, 10, 1, 14, 0);
    private static final LocalDateTime AIR_END =
            LocalDateTime.of(2026, 10, 1, 16, 0);

    private FireOrder freshOrder() {
        FireOrder order = FireOrder.issue(
                "ZY-2026-0101", 10L, 1L, 5L, "BL-1A", 10);
        order.setId(20L);
        return order;
    }

    @Test
    void voidOut_fresh_becomesVoidWithReason() {
        FireOrder order = freshOrder();
        order.voidOut("天气转好");
        assert order.getStatus() == OrderStatus.VOID;
        assert "天气转好".equals(order.getVoidReason());
    }

    @Test
    void voidOut_alreadyVoid_isIdempotent() {
        FireOrder order = freshOrder();
        order.voidOut("第一遍");
        // 第二遍原样放行、不抛错；原因保持第一遍，真正退弹与否由应用层据状态决定
        order.voidOut("第二遍");
        assert order.getStatus() == OrderStatus.VOID;
        assert "第一遍".equals(order.getVoidReason());
    }

    @Test
    void voidOut_done_rejected() {
        FireOrder order = freshOrder();
        order.reportFire(0, AIR_START, AIR_START.plusHours(1));
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> order.voidOut(null));
    }

    @Test
    void ensureWithinAirspace_rejectsEarlyStartAndLateEnd() {
        FireOrder early = freshOrder();
        early.reportFire(1, AIR_START.minusMinutes(1), AIR_START.plusMinutes(30));
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> early.ensureWithinAirspace(AIR_START, AIR_END));

        FireOrder late = freshOrder();
        late.reportFire(1, AIR_START.plusHours(1), AIR_END.plusMinutes(1));
        org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> late.ensureWithinAirspace(AIR_START, AIR_END));
    }

    @Test
    void ensureWithinAirspace_exactBoundaryPasses() {
        FireOrder order = freshOrder();
        order.reportFire(2, AIR_START, AIR_END);
        order.ensureWithinAirspace(AIR_START, AIR_END);
        assert order.getStatus() == OrderStatus.DONE;
    }
}
