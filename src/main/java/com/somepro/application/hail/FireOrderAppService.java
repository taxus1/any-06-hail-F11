package com.somepro.application.hail;

import com.somepro.application.hail.port.HailOperationFlowPort;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hail.model.AirspaceApply;
import com.somepro.domain.hail.model.AmmoStock;
import com.somepro.domain.hail.model.FireOrder;
import com.somepro.domain.hail.model.HourOccupant;
import com.somepro.domain.hail.model.Launcher;
import com.somepro.domain.hail.model.LauncherStatus;
import com.somepro.domain.hail.model.OrderStatus;
import com.somepro.domain.hail.model.SiteHourSlot;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作业指令应用服务：编排「开单（划弹）、回报（退弹）、作废（收摊）、翻看、按日看占用」用例。
 *
 * 开单（只有已批空域能开）：
 *   校验空域 APPROVED 且作业点一致 → 作业时段完整落在空域批复时段内（起早了、拖晚了都不行）
 *   → 作业点在册 → 装备待命且归属该作业点 → 该作业点该弹型该批次库存够且在有效期
 *   → 一张空域同时只能有一张没打完的单子 → 同一作业点时段不撞车（哪怕一分钟也不行）
 *   → 分配指令编号 ZY-yyyy-NNNN
 *   → 一个事务里：站点行锁串行化并复查上述占用规则 → 从结存划走计划发数
 *     + 记 OUT 领用流水（批次落流水）+ 装备作业中 + 插指令。
 *
 * 回报：
 *   实际打几发报上来（0 ≤ used ≤ plan），没用完的在同一事务里退回结存并记 RETURN，
 *   装备回待命，指令置 DONE；重复回报由条件更新拦截。
 *
 * 作废（发都没打的单子收摊）：
 *   没打完的（ISSUED / EXECUTING）才能作废，计划发数原封退回结存并记 RETURN，
 *   装备与时段一并腾出；已 VOID 的再点一遍原样退回（幂等，绝不再退一遍弹），
 *   已 DONE 的不能作废。
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

    /**
     * 下达作业指令。
     *
     * @param planStart 作业开始时刻：不得早于空域批复开始（起早了不行）
     * @param planEnd   作业结束时刻：不得晚于空域批复结束（拖晚了不行）
     */
    public Mono<FireOrder> issue(Long applyId, Long launcherId, String ammoType,
                                 String batchNo, int planRounds,
                                 LocalDateTime planStart, LocalDateTime planEnd) {
        // 入参基础校验在查库前同步 fast-fail（跨聚合校验在下面的链路里）
        if (applyId == null) {
            return Mono.error(new BizException("空域申请 applyId 不能为空"));
        }
        if (batchNo == null || batchNo.isBlank()) {
            return Mono.error(new BizException("出弹批次不能为空"));
        }
        if (planStart == null || planEnd == null) {
            return Mono.error(new BizException("作业起止时刻不能为空"));
        }
        if (!planEnd.isAfter(planStart)) {
            return Mono.error(new BizException("作业结束时刻必须晚于开始时刻"));
        }
        return applyRepository.findById(applyId)
                .switchIfEmpty(Mono.error(new BizException("空域申请不存在：id=" + applyId)))
                .flatMap(apply -> {
                    if (!apply.isApproved()) {
                        return Mono.error(new BizException(
                                "只有已批（APPROVED）空域才能开作业指令，当前空域状态：" + apply.getStatus()));
                    }
                    // 时段边界：作业得完整落在空域批下来的时段里头，起早了、拖晚了都不行
                    if (planStart.isBefore(apply.getPlanStart()) || planEnd.isAfter(apply.getPlanEnd())) {
                        return Mono.error(new BizException(
                                "作业时段 " + planStart + " ~ " + planEnd + " 没完整落在空域 "
                                        + apply.getApplyNo() + " 批复的时段 " + apply.getPlanStart()
                                        + " ~ " + apply.getPlanEnd() + " 里头，起早了、拖晚了都不行"));
                    }
                    // defer：后面每一段都等前置校验通过后才求值，避免装配期就调到未满足的依赖
                    return Mono.defer(() -> checkSite(apply.getSiteId()))
                            .then(Mono.defer(() -> checkLauncher(launcherId, apply.getSiteId())))
                            .then(Mono.defer(() -> checkStock(
                                    apply.getSiteId(), ammoType, batchNo, planRounds)))
                            .then(Mono.defer(() -> checkApplyHasNoOpenOrder(apply)))
                            .then(Mono.defer(() -> checkSlotNotTaken(
                                    apply.getSiteId(), planStart, planEnd)))
                            .then(Mono.defer(() -> issueWithNo(
                                    apply, launcherId, ammoType, batchNo, planRounds,
                                    planStart, planEnd)));
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
                .flatMap(order -> findOutBatch(order).flatMap(batchNo -> {
                    LocalDateTime now = LocalDateTime.now(clock);
                    // 领域行为算退回数并置 DONE（含 used 区间、时刻先后、重复回报校验）
                    order.reportFire(usedRounds,
                            startTime == null ? now : startTime,
                            endTime == null ? now : endTime);
                    return flowPort.reportFire(order, batchNo);
                }));
    }

    /**
     * 作废一张发都没打的单子：计划发数原封退回结存、记 RETURN 流水、装备与时段腾出。
     *
     * 幂等：已经是 VOID 的单子再点一遍，原样退回，绝不再退一遍弹。
     */
    public Mono<FireOrder> voidOrder(Long orderId, String reason) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BizException("作业指令不存在：id=" + orderId)))
                .flatMap(order -> {
                    if (order.getStatus() == OrderStatus.VOID) {
                        // 连着点：第二遍原样退回，不碰库存、不碰装备
                        return Mono.just(order);
                    }
                    // 领域行为置 VOID（已 DONE 的在此被拒；原因必填）
                    order.voidOrder(reason);
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
     * 按日看占用：挑个作业点、说个日子，把这一天 24 个钟头各被哪条空域、哪台装备占着列出来，
     * 没占上的钟头也照样列出（ occupants 为空，对外标「空」）。
     *
     * 开着的单子占计划时段，打完的单子占实际时段，作废的单子不占。
     */
    public Mono<List<SiteHourSlot>> dailyOccupancy(Long siteId, LocalDate day) {
        if (siteId == null) {
            return Mono.error(new BizException("作业点 siteId 不能为空"));
        }
        if (day == null) {
            return Mono.error(new BizException("日子 day 不能为空"));
        }
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        return siteRepository.findById(siteId)
                .switchIfEmpty(Mono.error(new BizException("作业点不存在：siteId=" + siteId)))
                .flatMap(site -> orderRepository.findOccupying(siteId, dayStart, dayEnd))
                .flatMap(orders -> {
                    if (orders.isEmpty()) {
                        return Mono.just(assembleSlots(day, orders, Map.of(), Map.of()));
                    }
                    Set<Long> applyIds = orders.stream().map(FireOrder::getApplyId)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    Set<Long> launcherIds = orders.stream().map(FireOrder::getLauncherId)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    Mono<Map<Long, AirspaceApply>> applies = applyRepository.findByIds(applyIds)
                            .map(list -> list.stream().collect(Collectors.toMap(
                                    AirspaceApply::getId, Function.identity())));
                    Mono<Map<Long, Launcher>> launchers = launcherRepository.findByIds(launcherIds)
                            .map(list -> list.stream().collect(Collectors.toMap(
                                    Launcher::getId, Function.identity())));
                    return Mono.zip(applies, launchers)
                            .map(t -> assembleSlots(day, orders, t.getT1(), t.getT2()));
                });
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

    /** 一张空域同时只能有一张没打完的单子在外面，不许拆成好几张把时段全占满。 */
    private Mono<Void> checkApplyHasNoOpenOrder(AirspaceApply apply) {
        return orderRepository.findOpenByApplyId(apply.getId())
                .flatMap(open -> Mono.<Void>error(new BizException(
                        "空域 " + apply.getApplyNo() + " 下还有没打完的指令 " + open.getOrderNo()
                                + "，一张空域同时只能有一张单子在外，等它打完回报或作废后再开")))
                .then();
    }

    /** 同一作业点时段撞车预检：哪怕只撞一分钟也挡回去，并说明撞的是哪一张。 */
    private Mono<Void> checkSlotNotTaken(Long siteId, LocalDateTime planStart, LocalDateTime planEnd) {
        return orderRepository.findFirstOverlappingOpen(siteId, planStart, planEnd)
                .flatMap(conflict -> Mono.<Void>error(new BizException(
                        "这段时辰已被先开出的指令 " + conflict.getOrderNo() + "（"
                                + conflict.getStartTime() + " ~ " + conflict.getEndTime()
                                + "）占住了，哪怕只撞一分钟也不能再开")))
                .then();
    }

    private Mono<String> findOutBatch(FireOrder order) {
        return recordRepository.findOutRecord(order.getSiteId(), order.getOrderNo())
                .map(record -> record.getBatchNo())
                .switchIfEmpty(Mono.error(new BizException(
                        "找不到指令 " + order.getOrderNo() + " 的领用流水，无法退弹（账实异常）")));
    }

    private Mono<FireOrder> issueWithNo(AirspaceApply apply, Long launcherId, String ammoType,
                                        String batchNo, int planRounds,
                                        LocalDateTime planStart, LocalDateTime planEnd) {
        return orderRepository.nextOrderNo(LocalDateTime.now(clock).getYear())
                .flatMap(no -> issueOne(no, apply, launcherId, ammoType, batchNo, planRounds,
                        planStart, planEnd, 1));
    }

    private Mono<FireOrder> issueOne(String no, AirspaceApply apply, Long launcherId, String ammoType,
                                     String batchNo, int planRounds,
                                     LocalDateTime planStart, LocalDateTime planEnd, int attempt) {
        FireOrder order = FireOrder.issue(
                no, apply.getId(), apply.getSiteId(), launcherId, ammoType, planRounds,
                planStart, planEnd);
        return flowPort.issueOrder(order, batchNo.trim())
                .onErrorResume(DuplicateKeyException.class, e -> {
                    if (attempt >= NO_RETRY_LIMIT) {
                        return Mono.error(new BizException("作业指令编号冲突，请稍后重试：" + no));
                    }
                    return orderRepository.nextOrderNo(LocalDateTime.now(clock).getYear())
                            .flatMap(nextNo -> issueOne(nextNo, apply, launcherId, ammoType,
                                    batchNo, planRounds, planStart, planEnd, attempt + 1));
                });
    }

    /** 把当天有占用的指令按小时投影成 24 个槽；时段与槽 [h:00, h+1:00) 有交叠就算占上。 */
    private List<SiteHourSlot> assembleSlots(LocalDate day, List<FireOrder> orders,
                                             Map<Long, AirspaceApply> applies,
                                             Map<Long, Launcher> launchers) {
        List<SiteHourSlot> slots = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            LocalDateTime slotStart = day.atTime(h, 0);
            LocalDateTime slotEnd = slotStart.plusHours(1);
            List<HourOccupant> occupants = new ArrayList<>();
            for (FireOrder order : orders) {
                if (order.getStartTime() == null || order.getEndTime() == null) {
                    continue;
                }
                if (order.getStartTime().isBefore(slotEnd) && order.getEndTime().isAfter(slotStart)) {
                    AirspaceApply apply = applies.get(order.getApplyId());
                    Launcher launcher = launchers.get(order.getLauncherId());
                    occupants.add(new HourOccupant(order.getOrderNo(),
                            order.getApplyId(), apply == null ? null : apply.getApplyNo(),
                            order.getLauncherId(), launcher == null ? null : launcher.getLauncherCode(),
                            order.getStartTime(), order.getEndTime(),
                            order.getStatus() == null ? null : order.getStatus().name()));
                }
            }
            slots.add(new SiteHourSlot(h, occupants));
        }
        return slots;
    }
}
