package com.somepro.application.hail;

import com.somepro.application.hail.port.HailOperationFlowPort;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.model.LauncherStatus;
import com.somepro.domain.hail.model.OccupancySlot;
import com.somepro.domain.hail.model.OrderOccupancy;
import com.somepro.domain.hail.model.OrderStatus;
import com.somepro.domain.hail.model.SiteDayOccupancy;
import com.somepro.domain.hail.model.SiteStatus;
import com.somepro.domain.hail.repository.AirspaceApplyRepository;
import com.somepro.domain.hail.repository.AmmoRecordRepository;
import com.somepro.domain.hail.repository.AmmoStockRepository;
import com.somepro.domain.hail.repository.FireOrderRepository;
import com.somepro.domain.hail.repository.LauncherRepository;
import com.somepro.domain.hail.repository.OperationSiteRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 作业指令应用服务：编排「开单（划弹）、回报（退弹）、作废（收摊）、占用查法」用例。
 *
 * 开单（只有已批空域能开）：
 *   校验空域 APPROVED 且作业点一致 → 作业点在册 → 装备待命且归属该作业点
 *   → 该作业点该弹型该批次库存够且在有效期 → 分配指令编号 ZY-yyyy-NNNN
 *   → 一个事务里（先锁作业点行串行化）：空域时段撞车 / 同空域已有在途单复查
 *   → 从结存划走计划发数 + 记 OUT 领用流水（批次落流水）+ 装备作业中 + 插指令。
 *
 * 回报：
 *   实际打几发报上来（0 ≤ used ≤ plan），没用完的在同一事务里退回结存并记 RETURN，
 *   装备回待命，指令置 DONE；重复回报由条件更新拦截。
 *   时段边界：实际起 / 止时刻必须完整落在空域批复时段内，起早了拖晚了都不许完成。
 *
 * 作废（一发都没打才许收摊）：
 *   ISSUED / EXECUTING 且 usedRounds=0 的单子可作废；事务里条件更新成功后，
 *   开单划走的计划发数原封退回结存并记 RETURN，装备回待命，所占空域时段腾出，置 VOID。
 *   作废幂等：已是 VOID 的单子再作废原样返回，不进退弹事务，连点两遍绝不扣 / 退两次弹。
 */
@Service
public class FireOrderAppService {

    private static final int NO_RETRY_LIMIT = 3;

    private final FireOrderRepository orderRepository;
    private final AirspaceApplyRepository applyRepository;
    private final OperationSiteRepository siteRepository;
    private final LauncherRepository launcherRepository;
    private final AmmoStockRepository stockRepository;
    private final AmmoRecordRepository recordRepository;
    private final HailOperationFlowPort flowPort;
    private final Clock clock;

    public FireOrderAppService(FireOrderRepository orderRepository,
                               AirspaceApplyRepository applyRepository,
                               OperationSiteRepository siteRepository,
                               LauncherRepository launcherRepository,
                               AmmoStockRepository stockRepository,
                               AmmoRecordRepository recordRepository,
                               HailOperationFlowPort flowPort) {
        this(orderRepository, applyRepository, siteRepository, launcherRepository,
                stockRepository, recordRepository, flowPort, Clock.systemDefaultZone());
    }

    FireOrderAppService(FireOrderRepository orderRepository,
                        AirspaceApplyRepository applyRepository,
                        OperationSiteRepository siteRepository,
                        LauncherRepository launcherRepository,
                        AmmoStockRepository stockRepository,
                        AmmoRecordRepository recordRepository,
                        HailOperationFlowPort flowPort, Clock clock) {
        this.orderRepository = orderRepository;
        this.applyRepository = applyRepository;
        this.siteRepository = siteRepository;
        this.launcherRepository = launcherRepository;
        this.stockRepository = stockRepository;
        this.recordRepository = recordRepository;
        this.flowPort = flowPort;
        this.clock = clock;
    }

    /** 下达作业指令。 */
    public Mono<FireOrder> issue(Long applyId, Long launcherId, String ammoType,
                                 String batchNo, int planRounds) {
        // 入参基础校验在查库前同步 fast-fail（跨聚合校验在下面的链路里）
        if (applyId == null) {
            return Mono.error(new BizException("空域申请 applyId 不能为空"));
        }
        if (batchNo == null || batchNo.isBlank()) {
            return Mono.error(new BizException("出弹批次不能为空"));
        }
        return applyRepository.findById(applyId)
                .switchIfEmpty(Mono.error(new BizException("空域申请不存在：id=" + applyId)))
                .flatMap(apply -> {
                    if (!apply.isApproved()) {
                        return Mono.error(new BizException(
                                "只有已批（APPROVED）空域才能开作业指令，当前空域状态：" + apply.getStatus()));
                    }
                    // defer：后面每一段都等前置校验通过后才求值，避免装配期就调到未满足的依赖
                    return Mono.defer(() -> checkSite(apply.getSiteId()))
                            .then(Mono.defer(() -> checkLauncher(launcherId, apply.getSiteId())))
                            .then(Mono.defer(() -> checkStock(
                                    apply.getSiteId(), ammoType, batchNo, planRounds)))
                            .then(Mono.defer(() ->
                                    issueWithNo(apply, launcherId, ammoType, batchNo, planRounds)));
                });
    }

    /**
     * 回报实际发数。
     *
     * @param usedRounds 实际打了几发，[0, planRounds]；剩余自动退回结存
     * @param startTime  实际开始时刻，可空（为空用当前时刻）
     * @param endTime    实际结束时刻，可空（为空用当前时刻）
     */
    public Mono<FireOrder> reportFire(Long orderId, int usedRounds,
                                      LocalDateTime startTime, LocalDateTime endTime) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BizException("作业指令不存在：id=" + orderId)))
                .flatMap(order -> applyRepository.findById(order.getApplyId())
                        .switchIfEmpty(Mono.error(new BizException(
                                "指令挂的空域申请不存在：applyId=" + order.getApplyId())))
                        .flatMap(apply -> findOutBatch(order).flatMap(batchNo -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            LocalDateTime actualStart = startTime == null ? now : startTime;
                            LocalDateTime actualEnd = endTime == null ? now : endTime;
                            // 领域行为算退回数并置 DONE（含 used 区间、时刻先后、重复回报校验）
                            order.reportFire(usedRounds, actualStart, actualEnd);
                            // 时段边界：作业必须完整落在空域批复时段里，起早了、拖晚了都挡下
                            order.ensureWithinAirspace(apply.getPlanStart(), apply.getPlanEnd());
                            return flowPort.reportFire(order, batchNo);
                        })));
    }

    /**
     * 作废（收摊）：一发都没打的在途单子才能作废，原划走的弹原封退回结存，装备和时段腾出来。
     *
     * 幂等：单子已经是 VOID，原样返回当前状态，不再进退弹事务 —— 连点两遍第二遍什么账都不动，
     * 绝不可能再退一遍弹。
     */
    public Mono<FireOrder> voidOrder(Long orderId, String reason) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BizException("作业指令不存在：id=" + orderId)))
                .flatMap(order -> {
                    if (order.getStatus() == OrderStatus.VOID) {
                        return Mono.just(order);
                    }
                    // 领域守卫：已 DONE 不许作废；打过弹（usedRounds>0）不许作废；置 VOID
                    order.voidOut(reason);
                    return findOutBatch(order)
                            .flatMap(batchNo -> flowPort.voidOrder(order, batchNo));
                });
    }

    public Mono<FireOrder> getById(Long id) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("作业指令不存在：id=" + id)));
    }

    /** 分页翻看指令，可按作业点、状态过滤；状态给了就必须是合法枚举。 */
    public Mono<PageResult<FireOrder>> page(int pageNum, int pageSize, Long siteId, String status) {
        String normalized = null;
        if (status != null && !status.isBlank()) {
            normalized = OrderStatus.of(status).name();
        }
        return orderRepository.page(pageNum, pageSize, siteId, normalized);
    }

    /**
     * 占用查法：挑个作业点、说个日子，把这一天 00:00–24:00 每个整点格子被哪张空域、
     * 哪台装备占着列出来；没占上的钟头也一个不落下，标空。
     *
     * 占用口径：在途（ISSUED / EXECUTING）单子挂的空域批复时段与格子有重叠（哪怕一分钟）即占，
     * 同一格子被多张单子覆盖时，先开单（create_time、id 更早）的占 —— 开单冲突在库里已拦住，
     * 正常不会出现，这里只是把「先开出去的单子占住时辰」落成确定性结果。
     */
    public Mono<SiteDayOccupancy> siteDay(Long siteId, LocalDate day) {
        if (siteId == null) {
            return Mono.error(new BizException("作业点 siteId 不能为空"));
        }
        if (day == null) {
            return Mono.error(new BizException("查占用的日子不能为空"));
        }
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        // 查询只读：作业点存在即可（封存 / 撤销点也允许翻看历史占用），不要求在册
        return siteRepository.findById(siteId)
                .switchIfEmpty(Mono.error(new BizException("作业点不存在：siteId=" + siteId)))
                .flatMap(site -> orderRepository.findActiveOccupancies(siteId, dayStart, dayEnd)
                        .map(orders -> new SiteDayOccupancy(siteId, site.getSiteCode(),
                                day, buildSlots(dayStart, orders))));
    }

    /** 24 个整点格子逐个落：与在途单子时段有重叠就算占，按开单先后先到先占，其余标空。 */
    private List<OccupancySlot> buildSlots(LocalDateTime dayStart,
                                           List<OrderOccupancy> orders) {
        List<OccupancySlot> slots = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            LocalDateTime hourStart = dayStart.plusHours(h);
            LocalDateTime hourEnd = hourStart.plusHours(1);
            OccupancySlot slot = orders.stream()
                    .filter(o -> overlaps(hourStart, hourEnd, o.windowStart(), o.windowEnd()))
                    .findFirst()
                    .map(o -> OccupancySlot.occupied(hourStart, hourEnd,
                            o.applyNo(), o.launcherCode(), o.orderNo()))
                    .orElseGet(() -> OccupancySlot.empty(hourStart, hourEnd));
            slots.add(slot);
        }
        return slots;
    }

    /** 半开区间重叠：[s1, e1) 与 [s2, e2) 有公共部分（边界相接不算，撞一分钟也算）。 */
    private boolean overlaps(LocalDateTime s1, LocalDateTime e1,
                             LocalDateTime s2, LocalDateTime e2) {
        return s1.isBefore(e2) && s2.isBefore(e1);
    }

    // ------------------------------------------------------------------

    private Mono<Void> checkSite(Long siteId) {
        return siteRepository.findById(siteId)
                .switchIfEmpty(Mono.error(new BizException("作业点不存在：siteId=" + siteId)))
                .flatMap(site -> site.getStatus() == SiteStatus.ACTIVE
                        ? Mono.<Void>empty()
                        : Mono.error(new BizException(
                                "作业点不在册（" + site.getStatus() + "），不能下达作业指令")));
    }

    private Mono<Void> checkLauncher(Long launcherId, Long siteId) {
        return launcherRepository.findById(launcherId)
                .switchIfEmpty(Mono.error(new BizException("装备不存在：id=" + launcherId)))
                .flatMap(launcher -> {
                    if (!siteId.equals(launcher.getSiteId())) {
                        return Mono.error(new BizException(
                                "装备不归属该作业点，不能跨点执行"));
                    }
                    if (launcher.getStatus() != LauncherStatus.READY) {
                        return Mono.error(new BizException(
                                "装备当前状态 " + launcher.getStatus() + "，只有待命 READY 装备能执行作业"));
                    }
                    return Mono.<Void>empty();
                });
    }

    private Mono<AmmoStock> checkStock(Long siteId, String ammoType, String batchNo, int qty) {
        if (ammoType == null || ammoType.isBlank()) {
            return Mono.error(new BizException("弹型不能为空，如 BL-1A"));
        }
        if (batchNo == null || batchNo.isBlank()) {
            return Mono.error(new BizException("出弹批次不能为空"));
        }
        if (qty <= 0) {
            return Mono.error(new BizException("计划用弹发数必须为正整数"));
        }
        return stockRepository.findUnique(siteId, ammoType.trim(), batchNo.trim())
                .switchIfEmpty(Mono.error(new BizException(
                        "该作业点没有这批弹：" + ammoType + " / " + batchNo)))
                .flatMap(stock -> {
                    if (stock.getQuantity() == null || stock.getQuantity() < qty) {
                        return Mono.error(new BizException(
                                "结存不足：计划 " + qty + " 发，当前结存 "
                                        + (stock.getQuantity() == null ? 0 : stock.getQuantity()) + " 发"));
                    }
                    if (stock.getExpireDate() != null
                            && stock.getExpireDate().isBefore(LocalDate.now(clock))) {
                        return Mono.error(new BizException(
                                "该批次弹药已过有效期（" + stock.getExpireDate() + "），不能领用"));
                    }
                    return Mono.just(stock);
                });
    }

    private Mono<String> findOutBatch(FireOrder order) {
        return recordRepository.findOutRecord(order.getSiteId(), order.getOrderNo())
                .map(record -> record.getBatchNo())
                .switchIfEmpty(Mono.error(new BizException(
                        "找不到指令 " + order.getOrderNo() + " 的领用流水，无法退弹（账实异常）")));
    }

    private Mono<FireOrder> issueWithNo(AirspaceApply apply, Long launcherId, String ammoType,
                                        String batchNo, int planRounds) {
        return orderRepository.nextOrderNo(LocalDateTime.now(clock).getYear())
                .flatMap(no -> issueOne(no, apply, launcherId, ammoType, batchNo, planRounds, 1));
    }

    private Mono<FireOrder> issueOne(String no, AirspaceApply apply, Long launcherId, String ammoType,
                                     String batchNo, int planRounds, int attempt) {
        FireOrder order = FireOrder.issue(
                no, apply.getId(), apply.getSiteId(), launcherId, ammoType, planRounds);
        return flowPort.issueOrder(order, batchNo.trim())
                .onErrorResume(DuplicateKeyException.class, e -> {
                    if (attempt >= NO_RETRY_LIMIT) {
                        return Mono.error(new BizException("作业指令编号冲突，请稍后重试：" + no));
                    }
                    return orderRepository.nextOrderNo(LocalDateTime.now(clock).getYear())
                            .flatMap(nextNo -> issueOne(nextNo, apply, launcherId, ammoType,
                                    batchNo, planRounds, attempt + 1));
                });
    }
}
