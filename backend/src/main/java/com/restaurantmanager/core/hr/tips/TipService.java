package com.restaurantmanager.core.hr.tips;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.common.HrSequenceService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.payroll.PayrollRunEntity;
import com.restaurantmanager.core.hr.payroll.PayrollRunRepository;
import com.restaurantmanager.core.hr.payroll.PayrollRunStatus;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.workforce.TimeEntryEntity;
import com.restaurantmanager.core.workforce.TimeEntryRepository;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tips and service charge: sharing rules, pools built from paid orders at a branch, and per-employee shares that payroll
 * pays once a pool is approved. Draft → Calculated → Approved (by someone else) → Paid (when every share's payroll run is
 * finalized). Pools never change after approval except by returning them to draft before payroll has used them.
 */
@Service
public class TipService {
    static final int MAX_PERIOD_DAYS = 31;
    private static final List<PaymentStatus> COLLECTED = List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);

    private final TipRuleRepository ruleRepository;
    private final TipPoolRepository poolRepository;
    private final TipAllocationRepository allocationRepository;
    private final PaymentRepository paymentRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final BranchRepository branchRepository;
    private final HrSequenceService sequences;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final ObjectMapper objectMapper;
    private final ZoneId zone;

    public TipService(TipRuleRepository ruleRepository, TipPoolRepository poolRepository, TipAllocationRepository allocationRepository,
                      PaymentRepository paymentRepository, TimeEntryRepository timeEntryRepository, EmployeeRepository employeeRepository,
                      PayrollRunRepository payrollRunRepository, BranchRepository branchRepository, HrSequenceService sequences,
                      HrAccessService accessService, HrAuditService audit, ObjectMapper objectMapper,
                      @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.ruleRepository = ruleRepository;
        this.poolRepository = poolRepository;
        this.allocationRepository = allocationRepository;
        this.paymentRepository = paymentRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.employeeRepository = employeeRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.branchRepository = branchRepository;
        this.sequences = sequences;
        this.accessService = accessService;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.zone = ZoneId.of(timeZone);
    }

    static HrPermission permissionFor(TipSource source) {
        return source == TipSource.TIPS ? HrPermission.TIPS_MANAGE : HrPermission.SERVICE_CHARGE_MANAGE;
    }

    private HrAccess requireViewer(UserPrincipal principal) {
        return accessService.requireAny(principal, HrPermission.TIPS_MANAGE, HrPermission.SERVICE_CHARGE_MANAGE, HrPermission.PAYROLL_VIEW,
                HrPermission.PAYROLL_PROCESS);
    }

    // ================================================================ rules

    @Transactional(readOnly = true)
    public List<RuleView> rules(UserPrincipal principal) {
        HrAccess access = requireViewer(principal);
        return ruleRepository.findAllByOrderBySourceAscEffectiveFromDesc().stream()
                .filter(r -> r.getBranchId() == null || access.canSeeBranch(r.getBranchId()))
                .map(this::toView).toList();
    }

    @Transactional
    public RuleView addRule(UserPrincipal principal, RuleRequest r) {
        HrAccess access = accessService.require(principal, permissionFor(r.source()));
        if (r.branchId() == null) {
            if (!access.allBranches()) {
                throw new ApiException(403, "Only central HR can set organisation-wide sharing rules");
            }
        } else {
            access.assertBranch(r.branchId());
            if (!branchRepository.existsById(r.branchId())) {
                throw new ApiException(404, "Branch not found");
            }
        }
        boolean clash = ruleRepository.findBySourceOrderByEffectiveFromDesc(r.source()).stream()
                .anyMatch(x -> Objects.equals(x.getBranchId(), r.branchId()) && x.getEffectiveFrom().equals(r.effectiveFrom()));
        if (clash) {
            throw new ApiException(409, "A sharing rule for this scope already takes effect on that date");
        }
        Map<String, BigDecimal> points = r.positionPoints() == null ? Map.of() : r.positionPoints();
        if (points.values().stream().anyMatch(v -> v == null || v.signum() < 0 || v.compareTo(BigDecimal.valueOf(1000)) > 0)) {
            throw new ApiException(400, "Points must be between 0 and 1000");
        }
        for (String key : points.keySet()) {
            try {
                UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(400, "Unknown position " + key);
            }
        }
        TipRuleEntity rule = new TipRuleEntity();
        rule.setBranchId(r.branchId());
        rule.setSource(r.source());
        rule.setMethod(r.method());
        rule.setHousePercent(r.housePercent() == null ? BigDecimal.ZERO : r.housePercent());
        rule.setTaxable(r.taxable());
        rule.setMinMinutes(r.minMinutes() == null ? 0 : r.minMinutes());
        rule.setPositionPoints(points.isEmpty() ? null : json(points));
        rule.setEffectiveFrom(r.effectiveFrom());
        rule.setNote(blank(r.note()));
        rule.setCreatedBy(access.userId());
        ruleRepository.save(rule);
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "TipRule", rule.getId(),
                Map.of("rule", "tip-sharing", "source", r.source().name(), "branchId", String.valueOf(r.branchId()), "effectiveFrom", r.effectiveFrom().toString()));
        return toView(rule);
    }

    /** The branch rule in force on {@code date}, else the organisation rule, else an even split by hours with nothing kept by the house. */
    RuleSnapshot resolveRule(UUID branchId, TipSource source, LocalDate date) {
        List<TipRuleEntity> rows = ruleRepository.findBySourceOrderByEffectiveFromDesc(source).stream()
                .filter(r -> !r.getEffectiveFrom().isAfter(date)).toList();
        Optional<TipRuleEntity> rule = rows.stream().filter(r -> branchId.equals(r.getBranchId())).findFirst()
                .or(() -> rows.stream().filter(r -> r.getBranchId() == null).findFirst());
        return rule.map(r -> new RuleSnapshot(r.getId(), r.getEffectiveFrom(), r.getBranchId() == null ? "ORGANISATION" : "BRANCH", r.getMethod(),
                        r.getHousePercent(), r.isTaxable(), r.getMinMinutes(), points(r.getPositionPoints())))
                .orElse(new RuleSnapshot(null, null, "DEFAULT", TipMethod.HOURS, BigDecimal.ZERO, true, 0, Map.of()));
    }

    // ================================================================ pools

    @Transactional
    public PoolDetail create(UserPrincipal principal, CreatePoolRequest r) {
        HrAccess access = accessService.require(principal, permissionFor(r.source()));
        access.assertBranch(r.branchId());
        if (!branchRepository.existsById(r.branchId())) {
            throw new ApiException(404, "Branch not found");
        }
        if (r.periodEnd().isBefore(r.periodStart()) || ChronoUnit.DAYS.between(r.periodStart(), r.periodEnd()) >= MAX_PERIOD_DAYS) {
            throw new ApiException(400, "A pool covers 1 to " + MAX_PERIOD_DAYS + " days");
        }
        if (!r.periodEnd().isBefore(LocalDate.now(zone))) {
            throw new ApiException(400, "Pools can only cover days that have ended");
        }
        if (!poolRepository.findOverlapping(r.branchId(), r.source(), r.periodStart(), r.periodEnd(), TipPoolStatus.CANCELLED).isEmpty()) {
            throw new ApiException(409, "Another " + label(r.source()) + " pool at this branch covers part of this period");
        }
        TipPoolEntity pool = new TipPoolEntity();
        pool.setPoolNumber(sequences.next(r.source() == TipSource.TIPS ? "tip_pool" : "service_charge_pool", r.source() == TipSource.TIPS ? "TIP" : "SVC"));
        pool.setBranchId(r.branchId());
        pool.setSource(r.source());
        pool.setPeriodStart(r.periodStart());
        pool.setPeriodEnd(r.periodEnd());
        pool.setCreatedBy(access.userId());
        poolRepository.save(pool);
        log(access, pool, "created", null);
        return detail(pool, access);
    }

    @Transactional(readOnly = true)
    public List<PoolSummary> list(UserPrincipal principal, UUID branchId) {
        HrAccess access = requireViewer(principal);
        List<TipPoolEntity> rows;
        if (branchId != null) {
            access.assertBranch(branchId);
            rows = poolRepository.findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(List.of(branchId));
        } else if (access.allBranches()) {
            rows = poolRepository.findAllByOrderByPeriodStartDescCreatedAtDesc();
        } else {
            rows = access.branchIds().isEmpty() ? List.of() : poolRepository.findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(access.branchIds());
        }
        return rows.stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public PoolDetail get(UserPrincipal principal, UUID id) {
        HrAccess access = requireViewer(principal);
        return detail(load(id, access), access);
    }

    @Transactional
    public PoolDetail calculate(UserPrincipal principal, UUID id) {
        HrAccess access = requireViewer(principal);
        TipPoolEntity pool = load(id, access);
        requireManage(access, pool);
        if (pool.getStatus() != TipPoolStatus.DRAFT && pool.getStatus() != TipPoolStatus.CALCULATED) {
            throw new ApiException(409, "Only draft or calculated pools can be (re)calculated");
        }
        Instant from = pool.getPeriodStart().atStartOfDay(zone).toInstant();
        Instant to = pool.getPeriodEnd().plusDays(1).atStartOfDay(zone).toInstant();

        // collected: orders at the branch whose first successful payment falls in the period, unless fully refunded or cancelled
        Map<UUID, OrderEntity> candidates = new LinkedHashMap<>();
        for (PaymentEntity p : paymentRepository.findPaidBetween(COLLECTED, from, to)) {
            OrderEntity o = p.getOrder();
            if (o.getBranch() != null && pool.getBranchId().equals(o.getBranch().getId()) && o.getStatus() != OrderStatus.CANCELLED) {
                candidates.putIfAbsent(o.getId(), o);
            }
        }
        BigDecimal collected = BigDecimal.ZERO;
        int orders = 0;
        for (OrderEntity o : candidates.values()) {
            List<PaymentEntity> payments = paymentRepository.findByOrderIdAndStatusIn(o.getId(), COLLECTED);
            Instant first = payments.stream().map(PaymentEntity::getPaidAt).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
            BigDecimal paid = payments.stream().map(PaymentEntity::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal refunded = payments.stream().map(PaymentEntity::getRefundedAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (first == null || first.isBefore(from) || !first.isBefore(to) || (paid.signum() > 0 && refunded.compareTo(paid) >= 0)) {
                continue;
            }
            BigDecimal amount = pool.getSource() == TipSource.TIPS ? o.getTipAmount() : o.getServiceCharge();
            if (amount != null && amount.signum() > 0) {
                collected = collected.add(amount);
                orders++;
            }
        }

        RuleSnapshot rule = resolveRule(pool.getBranchId(), pool.getSource(), pool.getPeriodEnd());
        TipCalculator.Split split = TipCalculator.split(collected, rule.housePercent());

        // participants: everyone with closed time entries at this branch in the period
        Map<UUID, Integer> minutes = new LinkedHashMap<>();
        for (TimeEntryEntity e : timeEntryRepository.findAtBranches(from, to, List.of(pool.getBranchId()))) {
            if (e.getEmployeeId() == null || e.getClockOutAt() == null || e.getClockInAt().isBefore(from)) {
                continue;
            }
            int paidMinutes = (int) Math.max(0, Duration.between(e.getClockInAt(), e.getClockOutAt()).toMinutes() - e.getBreakMinutes());
            minutes.merge(e.getEmployeeId(), paidMinutes, Integer::sum);
        }
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(minutes.keySet()).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        List<TipCalculator.Participant> participants = minutes.entrySet().stream()
                .filter(m -> employees.containsKey(m.getKey()))
                .sorted(Comparator.comparing(m -> employees.get(m.getKey()).fullName()))
                .map(m -> new TipCalculator.Participant(m.getKey(), m.getValue(), pointsOf(rule, employees.get(m.getKey()))))
                .toList();
        Map<UUID, BigDecimal> shares = TipCalculator.allocate(split.distributable(), participants, rule.method(), rule.minMinutes());

        allocationRepository.deleteByPoolId(pool.getId());
        allocationRepository.flush();
        List<TipAllocationEntity> rows = new ArrayList<>();
        for (TipCalculator.Participant p : participants) {
            TipAllocationEntity a = new TipAllocationEntity();
            a.setPoolId(pool.getId());
            a.setEmployeeId(p.employeeId());
            a.setMinutes(p.minutes());
            a.setPoints(p.points());
            a.setAmount(shares.getOrDefault(p.employeeId(), BigDecimal.ZERO));
            rows.add(a);
        }
        allocationRepository.saveAll(rows);
        BigDecimal allocated = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        pool.setOrderCount(orders);
        pool.setCollectedAmount(collected.setScale(2, java.math.RoundingMode.HALF_UP));
        // anything that could not be shared (no eligible participants) stays with the house and is shown as such
        pool.setHouseAmount(split.house().add(split.distributable().subtract(allocated)));
        pool.setDistributableAmount(allocated);
        pool.setRuleSnapshot(json(rule));
        pool.setStatus(TipPoolStatus.CALCULATED);
        pool.setCalculatedBy(access.userId());
        pool.setCalculatedAt(Instant.now());
        pool.setApprovedBy(null);
        pool.setApprovedAt(null);
        log(access, pool, "calculated", Map.of("orders", orders, "participants", participants.size()));
        return detail(poolRepository.saveAndFlush(pool), access);
    }

    private static BigDecimal pointsOf(RuleSnapshot rule, EmployeeEntity e) {
        if (rule.positionPoints().isEmpty()) {
            return BigDecimal.ONE;
        }
        if (e.getPosition() == null) {
            return BigDecimal.ZERO;
        }
        return rule.positionPoints().getOrDefault(e.getPosition().getId().toString(), BigDecimal.ZERO);
    }

    @Transactional
    public PoolDetail approve(UserPrincipal principal, UUID id) {
        HrAccess access = requireViewer(principal);
        TipPoolEntity pool = load(id, access);
        requireManage(access, pool);
        if (pool.getStatus() != TipPoolStatus.CALCULATED) {
            throw new ApiException(409, "Calculate the pool before approving it");
        }
        if (access.userId().equals(pool.getCalculatedBy())) {
            throw new ApiException(403, "Someone other than the person who calculated this pool must approve it");
        }
        boolean shareholder = employeeRepository.findByUserId(access.userId())
                .map(me -> allocationRepository.findByPoolIdOrderByAmountDesc(pool.getId()).stream()
                        .anyMatch(a -> a.getEmployeeId().equals(me.getId()) && a.getAmount().signum() > 0))
                .orElse(false);
        if (shareholder) {
            throw new ApiException(403, "You have a share in this pool; someone else must approve it");
        }
        pool.setStatus(TipPoolStatus.APPROVED);
        pool.setApprovedBy(access.userId());
        pool.setApprovedAt(Instant.now());
        log(access, pool, "approved", null);
        return detail(poolRepository.saveAndFlush(pool), access);
    }

    @Transactional
    public PoolDetail reopen(UserPrincipal principal, UUID id, ReasonRequest r) {
        HrAccess access = requireViewer(principal);
        TipPoolEntity pool = load(id, access);
        requireManage(access, pool);
        if (pool.getStatus() != TipPoolStatus.CALCULATED && pool.getStatus() != TipPoolStatus.APPROVED) {
            throw new ApiException(409, "Only calculated or approved pools can return to draft");
        }
        requireNotInPayroll(pool);
        pool.setStatus(TipPoolStatus.DRAFT);
        pool.setApprovedBy(null);
        pool.setApprovedAt(null);
        log(access, pool, "reopened", Map.of("reason", r.reason().trim()));
        return detail(poolRepository.saveAndFlush(pool), access);
    }

    @Transactional
    public PoolDetail cancel(UserPrincipal principal, UUID id, ReasonRequest r) {
        HrAccess access = requireViewer(principal);
        TipPoolEntity pool = load(id, access);
        requireManage(access, pool);
        if (pool.getStatus() == TipPoolStatus.PAID || pool.getStatus() == TipPoolStatus.CANCELLED) {
            throw new ApiException(409, "Paid or cancelled pools cannot be cancelled");
        }
        requireNotInPayroll(pool);
        pool.setStatus(TipPoolStatus.CANCELLED);
        pool.setCancelReason(r.reason().trim());
        log(access, pool, "cancelled", Map.of("reason", r.reason().trim()));
        return detail(poolRepository.saveAndFlush(pool), access);
    }

    /**
     * A pool cannot change while a payroll run with settled figures (calculated or later) pays from it. Shares held by a run
     * that is back in draft are released: that run must be recalculated before approval anyway.
     */
    private void requireNotInPayroll(TipPoolEntity pool) {
        List<TipAllocationEntity> held = allocationRepository.findByPoolIdOrderByAmountDesc(pool.getId()).stream()
                .filter(a -> a.getPayrollRunId() != null).toList();
        if (held.isEmpty()) {
            return;
        }
        Map<UUID, PayrollRunStatus> runs = payrollRunRepository.findAllById(held.stream().map(TipAllocationEntity::getPayrollRunId).distinct().toList())
                .stream().collect(Collectors.toMap(PayrollRunEntity::getId, PayrollRunEntity::getStatus));
        if (held.stream().anyMatch(a -> runs.get(a.getPayrollRunId()) != PayrollRunStatus.DRAFT)) {
            throw new ApiException(409, "A payroll run already includes this pool; return that run to draft or cancel it first");
        }
        held.forEach(a -> a.setPayrollRunId(null));
    }

    @Transactional(readOnly = true)
    public List<MyShare> mine(UserPrincipal principal) {
        EmployeeEntity me = employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
        List<TipAllocationEntity> rows = allocationRepository.findByEmployeeId(me.getId());
        Map<UUID, TipPoolEntity> pools = poolRepository.findAllById(rows.stream().map(TipAllocationEntity::getPoolId).distinct().toList()).stream()
                .filter(p -> p.getStatus() == TipPoolStatus.APPROVED || p.getStatus() == TipPoolStatus.PAID)
                .collect(Collectors.toMap(TipPoolEntity::getId, Function.identity()));
        Map<UUID, String> branches = branchNames();
        return rows.stream().filter(a -> pools.containsKey(a.getPoolId()))
                .map(a -> {
                    TipPoolEntity p = pools.get(a.getPoolId());
                    return new MyShare(a.getId(), p.getPoolNumber(), p.getSource(), branches.get(p.getBranchId()), p.getPeriodStart(), p.getPeriodEnd(),
                            a.getMinutes(), a.getAmount(), p.getStatus(), a.getPayrollRunId() != null);
                })
                .sorted(Comparator.comparing(MyShare::periodStart).reversed())
                .toList();
    }

    // ================================================================ payroll hand-off

    /**
     * Approved shares for an employee in pools ending inside [from, to] that no other payroll run has taken. Read only:
     * call {@link #reserve} for the ones actually paid.
     */
    @Transactional(readOnly = true)
    public List<PayrollShare> sharesFor(UUID employeeId, UUID runId, LocalDate from, LocalDate to) {
        Map<UUID, TipPoolEntity> pools = poolRepository.findEndingBetween(EnumSet.of(TipPoolStatus.APPROVED, TipPoolStatus.PAID), from, to).stream()
                .collect(Collectors.toMap(TipPoolEntity::getId, Function.identity()));
        if (pools.isEmpty()) {
            return List.of();
        }
        return allocationRepository.findByPoolIdIn(pools.keySet()).stream()
                .filter(a -> a.getEmployeeId().equals(employeeId) && a.getAmount().signum() > 0)
                .filter(a -> a.getPayrollRunId() == null || a.getPayrollRunId().equals(runId))
                .map(a -> {
                    TipPoolEntity p = pools.get(a.getPoolId());
                    RuleSnapshot rule = snapshot(p.getRuleSnapshot());
                    return new PayrollShare(a.getId(), p.getSource(), p.getPoolNumber(), a.getAmount(), rule == null || rule.taxable());
                })
                .sorted(Comparator.comparing(PayrollShare::poolNumber))
                .toList();
    }

    @Transactional
    public void reserve(Collection<UUID> allocationIds, UUID runId) {
        allocationRepository.findAllById(allocationIds).forEach(a -> a.setPayrollRunId(runId));
    }

    @Transactional
    public void release(UUID runId) {
        allocationRepository.releaseRun(runId);
        allocationRepository.flush();
    }

    /** After a payroll run is finalized: pools whose every share sits in a finalized or paid run become PAID. */
    @Transactional
    public void payrollFinalized(UUID runId) {
        Set<UUID> poolIds = allocationRepository.findByPayrollRunId(runId).stream().map(TipAllocationEntity::getPoolId).collect(Collectors.toSet());
        for (TipPoolEntity pool : poolRepository.findAllById(poolIds)) {
            List<TipAllocationEntity> shares = allocationRepository.findByPoolIdOrderByAmountDesc(pool.getId()).stream()
                    .filter(a -> a.getAmount().signum() > 0).toList();
            Set<UUID> runs = shares.stream().map(TipAllocationEntity::getPayrollRunId).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<UUID> done = payrollRunRepository.findAllById(runs).stream()
                    .filter(r -> r.getStatus() == PayrollRunStatus.FINALIZED || r.getStatus() == PayrollRunStatus.PAID)
                    .map(PayrollRunEntity::getId).collect(Collectors.toSet());
            if (pool.getStatus() == TipPoolStatus.APPROVED && shares.stream().allMatch(a -> a.getPayrollRunId() != null && done.contains(a.getPayrollRunId()))) {
                pool.setStatus(TipPoolStatus.PAID);
            }
        }
    }

    // ================================================================ helpers

    private TipPoolEntity load(UUID id, HrAccess access) {
        TipPoolEntity pool = poolRepository.findById(id).orElseThrow(() -> new ApiException(404, "Pool not found"));
        if (!access.canSeeBranch(pool.getBranchId())) {
            throw new ApiException(404, "Pool not found");
        }
        return pool;
    }

    private static void requireManage(HrAccess access, TipPoolEntity pool) {
        if (!access.has(permissionFor(pool.getSource()))) {
            throw new ApiException(403, "You do not have permission to manage " + label(pool.getSource()));
        }
    }

    private void log(HrAccess access, TipPoolEntity pool, String operation, Map<String, ?> extra) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", operation);
        meta.put("poolNumber", pool.getPoolNumber());
        meta.put("source", pool.getSource().name());
        meta.put("from", pool.getPeriodStart().toString());
        meta.put("to", pool.getPeriodEnd().toString());
        if (extra != null) meta.putAll(extra);
        audit.log(access.userId(), AuditAction.HR_TIPS_CHANGED, "TipPool", pool.getId(), meta);
    }

    private PoolDetail detail(TipPoolEntity pool, HrAccess access) {
        List<TipAllocationEntity> rows = allocationRepository.findByPoolIdOrderByAmountDesc(pool.getId());
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(rows.stream().map(TipAllocationEntity::getEmployeeId).toList()).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        List<AllocationView> allocations = rows.stream().map(a -> {
            EmployeeEntity e = employees.get(a.getEmployeeId());
            return new AllocationView(a.getId(), a.getEmployeeId(), e == null ? null : e.fullName(),
                    e == null || e.getPosition() == null ? null : e.getPosition().getTitle(), a.getMinutes(), a.getPoints(), a.getAmount(),
                    a.getPayrollRunId() != null);
        }).toList();
        return new PoolDetail(summary(pool), snapshot(pool.getRuleSnapshot()), allocations,
                pool.getCalculatedBy() != null && pool.getCalculatedBy().equals(access.userId()));
    }

    private PoolSummary summary(TipPoolEntity p) {
        return new PoolSummary(p.getId(), p.getPoolNumber(), p.getBranchId(), branchNames().get(p.getBranchId()), p.getSource(), p.getPeriodStart(),
                p.getPeriodEnd(), p.getStatus(), p.getOrderCount(), p.getCollectedAmount(), p.getHouseAmount(), p.getDistributableAmount(),
                p.getCalculatedAt(), p.getApprovedAt(), p.getCancelReason(), p.getVersion());
    }

    private RuleView toView(TipRuleEntity r) {
        return new RuleView(r.getId(), r.getBranchId(), r.getSource(), r.getMethod(), r.getHousePercent(), r.isTaxable(), r.getMinMinutes(),
                points(r.getPositionPoints()), r.getEffectiveFrom(), r.getNote(), r.getCreatedAt());
    }

    private Map<String, BigDecimal> points(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, BigDecimal>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored position points are unreadable", e);
        }
    }

    private RuleSnapshot snapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RuleSnapshot.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<UUID, String> branchNames() {
        Map<UUID, String> names = new HashMap<>();
        branchRepository.findAll().forEach(b -> names.put(b.getId(), b.getName()));
        return names;
    }

    static String label(TipSource source) {
        return source == TipSource.TIPS ? "tips" : "service charge";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ================================================================ DTOs

    public record RuleSnapshot(UUID ruleId, LocalDate effectiveFrom, String scope, TipMethod method, BigDecimal housePercent, boolean taxable,
                               int minMinutes, Map<String, BigDecimal> positionPoints) {}

    public record RuleRequest(UUID branchId, @NotNull TipSource source, @NotNull TipMethod method,
                              @DecimalMin("0") @DecimalMax("100") BigDecimal housePercent, boolean taxable,
                              @Min(0) @Max(1440) Integer minMinutes, Map<String, BigDecimal> positionPoints,
                              @NotNull LocalDate effectiveFrom, @Size(max = 300) String note) {}

    public record RuleView(UUID id, UUID branchId, TipSource source, TipMethod method, BigDecimal housePercent, boolean taxable, int minMinutes,
                           Map<String, BigDecimal> positionPoints, LocalDate effectiveFrom, String note, Instant createdAt) {}

    public record CreatePoolRequest(@NotNull UUID branchId, @NotNull TipSource source, @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd) {}

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

    public record PoolSummary(UUID id, String poolNumber, UUID branchId, String branchName, TipSource source, LocalDate periodStart,
                              LocalDate periodEnd, TipPoolStatus status, int orderCount, BigDecimal collectedAmount, BigDecimal houseAmount,
                              BigDecimal distributableAmount, Instant calculatedAt, Instant approvedAt, String cancelReason, long version) {}

    public record AllocationView(UUID id, UUID employeeId, String employeeName, String positionTitle, int minutes, BigDecimal points,
                                 BigDecimal amount, boolean inPayroll) {}

    public record PoolDetail(PoolSummary summary, RuleSnapshot rule, List<AllocationView> allocations, boolean calculatedByMe) {}

    public record MyShare(UUID id, String poolNumber, TipSource source, String branchName, LocalDate periodStart, LocalDate periodEnd, int minutes,
                          BigDecimal amount, TipPoolStatus status, boolean inPayroll) {}

    public record PayrollShare(UUID allocationId, TipSource source, String poolNumber, BigDecimal amount, boolean taxable) {}
}
