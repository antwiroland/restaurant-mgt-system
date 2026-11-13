package com.restaurantmanager.core.hr.payroll;

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
import com.restaurantmanager.core.hr.employee.EmploymentType;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Effective-dated payroll deduction and contribution rules. Nothing is preset: statutory rates are configured by HR. */
@Service
public class PayrollRuleService {
    private final PayrollRuleRepository repository;
    private final BranchRepository branchRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final ObjectMapper objectMapper;

    public PayrollRuleService(PayrollRuleRepository repository, BranchRepository branchRepository, HrAccessService accessService,
                              HrAuditService audit, ObjectMapper objectMapper) {
        this.repository = repository;
        this.branchRepository = branchRepository;
        this.accessService = accessService;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RuleView> list(UserPrincipal principal) {
        HrAccess access = accessService.requireAny(principal, HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS, HrPermission.SETTINGS_MANAGE);
        return repository.findAllByOrderByDisplayOrderAscCodeAscEffectiveFromDesc().stream()
                .filter(r -> r.getBranchId() == null || access.canSeeBranch(r.getBranchId()))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public RuleView add(UserPrincipal principal, RuleRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        if (r.branchId() == null) {
            if (!access.allBranches()) {
                throw new ApiException(403, "Only central HR can set organisation-wide payroll rules");
            }
        } else {
            access.assertBranch(r.branchId());
            if (!branchRepository.existsById(r.branchId())) {
                throw new ApiException(404, "Branch not found");
            }
        }
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        boolean clash = repository.findAll().stream().anyMatch(x -> x.getCode().equals(code) && Objects.equals(x.getBranchId(), r.branchId())
                && x.getEffectiveFrom().equals(r.effectiveFrom()));
        if (clash) {
            throw new ApiException(409, "A rule with this code already takes effect on that date for this scope");
        }
        PayrollRuleEntity rule = new PayrollRuleEntity();
        rule.setCode(code);
        rule.setBranchId(r.branchId());
        rule.setEffectiveFrom(r.effectiveFrom());
        rule.setActive(r.active() == null || r.active());
        rule.setNote(blank(r.note()));
        rule.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
        if (rule.isActive()) {
            if (r.name() == null || r.name().isBlank() || r.kind() == null || r.method() == null || r.base() == null) {
                throw new ApiException(400, "Name, kind, method and base are required");
            }
            if (r.preTax() && (r.kind() != PayrollRuleKind.EMPLOYEE_DEDUCTION || r.base() == PayrollRuleBase.TAXABLE)) {
                throw new ApiException(400, "Only employee deductions on basic or gross pay can be taken before tax");
            }
            switch (r.method()) {
                case PERCENT -> {
                    if (r.ratePercent() == null) throw new ApiException(400, "Give the percentage");
                }
                case FIXED -> {
                    if (r.fixedAmount() == null) throw new ApiException(400, "Give the fixed amount");
                }
                case BRACKETS -> {
                    String problem = PayrollCalculator.bracketProblem(r.brackets());
                    if (problem != null) throw new ApiException(400, problem);
                }
            }
            rule.setName(r.name().trim());
            rule.setKind(r.kind());
            rule.setMethod(r.method());
            rule.setBase(r.base());
            rule.setRatePercent(r.method() == PayrollRuleMethod.PERCENT ? r.ratePercent() : null);
            rule.setFixedAmount(r.method() == PayrollRuleMethod.FIXED ? r.fixedAmount() : null);
            rule.setCapAmount(r.capAmount());
            rule.setBrackets(r.method() == PayrollRuleMethod.BRACKETS ? json(r.brackets()) : null);
            rule.setPreTax(r.preTax());
            rule.setEmploymentTypes(r.employmentTypes() == null || r.employmentTypes().isEmpty() ? null
                    : r.employmentTypes().stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        } else {
            // an end marker: copies the identity of the rule it ends
            PayrollRuleEntity previous = repository.findAll().stream()
                    .filter(x -> x.getCode().equals(code) && Objects.equals(x.getBranchId(), r.branchId()) && x.isActive())
                    .max(Comparator.comparing(PayrollRuleEntity::getEffectiveFrom))
                    .orElseThrow(() -> new ApiException(404, "There is no rule with this code to end"));
            rule.setName(previous.getName());
            rule.setKind(previous.getKind());
            rule.setMethod(previous.getMethod());
            rule.setBase(previous.getBase());
        }
        rule.setReportAs(reportAs(r.reportAs(), rule.getKind()));
        rule.setCreatedBy(access.userId());
        repository.save(rule);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("rule", "payroll");
        meta.put("code", code);
        meta.put("branchId", String.valueOf(r.branchId()));
        meta.put("effectiveFrom", r.effectiveFrom().toString());
        meta.put("active", rule.isActive());
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "PayrollRule", rule.getId(), meta);
        return toView(rule);
    }

    /** SSNIT_EMPLOYEE, SSNIT_EMPLOYER, PAYE or TIER2; must fit the rule's kind. */
    static final Set<String> REPORT_AS = Set.of("SSNIT_EMPLOYEE", "SSNIT_EMPLOYER", "PAYE", "TIER2");

    private static String reportAs(String raw, PayrollRuleKind kind) {
        String value = blank(raw);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!REPORT_AS.contains(value)) {
            throw new ApiException(400, "Report as must be SSNIT_EMPLOYEE, SSNIT_EMPLOYER, PAYE or TIER2");
        }
        boolean employerKind = kind == PayrollRuleKind.EMPLOYER_CONTRIBUTION;
        if (value.equals("SSNIT_EMPLOYER") != employerKind && !value.equals("TIER2")) {
            throw new ApiException(400, value.equals("SSNIT_EMPLOYER") ? "Only employer contributions are filed as SSNIT (employer)"
                    : "Employer contributions cannot be filed as " + value);
        }
        return value;
    }

    /** Rule code to what it is filed as (the latest row per code decides). */
    @Transactional(readOnly = true)
    public Map<String, String> reportCodes() {
        Map<String, String> codes = new java.util.HashMap<>();
        for (PayrollRuleEntity r : repository.findAllByOrderByDisplayOrderAscCodeAscEffectiveFromDesc()) {
            if (r.getReportAs() != null) {
                codes.putIfAbsent(r.getCode(), r.getReportAs());
            }
        }
        return codes;
    }

    /**
     * Adds Ghana's statutory payroll rules organisation-wide: SSNIT 5.5% (employee, before tax), SSNIT 13% (employer) on
     * basic pay, and monthly PAYE bands. Codes that already exist are left alone. The rates are a starting point that
     * finance must check against the current SSNIT and GRA rates.
     */
    @Transactional
    public List<RuleView> addGhanaPreset(UserPrincipal principal, LocalDate effectiveFrom) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        if (!access.allBranches()) {
            throw new ApiException(403, "Only central HR can set organisation-wide payroll rules");
        }
        LocalDate from = effectiveFrom == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : effectiveFrom;
        Set<String> existing = repository.findAll().stream().map(PayrollRuleEntity::getCode).collect(Collectors.toSet());
        List<RuleView> added = new ArrayList<>();
        String note = "Ghana preset: check against current SSNIT and GRA rates";
        if (!existing.contains("SSNIT_EE")) {
            added.add(add(principal, new RuleRequest("SSNIT_EE", "SSNIT (employee 5.5%)", PayrollRuleKind.EMPLOYEE_DEDUCTION, PayrollRuleMethod.PERCENT,
                    PayrollRuleBase.BASIC, new BigDecimal("5.5"), null, null, null, true, null, null, from, true, 1, note, "SSNIT_EMPLOYEE")));
        }
        if (!existing.contains("PAYE")) {
            List<PayrollCalculator.Bracket> bands = List.of(
                    new PayrollCalculator.Bracket(new BigDecimal("490"), BigDecimal.ZERO),
                    new PayrollCalculator.Bracket(new BigDecimal("600"), new BigDecimal("5")),
                    new PayrollCalculator.Bracket(new BigDecimal("730"), new BigDecimal("10")),
                    new PayrollCalculator.Bracket(new BigDecimal("3896.67"), new BigDecimal("17.5")),
                    new PayrollCalculator.Bracket(new BigDecimal("19896.67"), new BigDecimal("25")),
                    new PayrollCalculator.Bracket(new BigDecimal("50416.67"), new BigDecimal("30")),
                    new PayrollCalculator.Bracket(null, new BigDecimal("35")));
            added.add(add(principal, new RuleRequest("PAYE", "Income tax (PAYE)", PayrollRuleKind.EMPLOYEE_DEDUCTION, PayrollRuleMethod.BRACKETS,
                    PayrollRuleBase.TAXABLE, null, null, null, bands, false, null, null, from, true, 2, note, "PAYE")));
        }
        if (!existing.contains("SSNIT_ER")) {
            added.add(add(principal, new RuleRequest("SSNIT_ER", "SSNIT (employer 13%)", PayrollRuleKind.EMPLOYER_CONTRIBUTION, PayrollRuleMethod.PERCENT,
                    PayrollRuleBase.BASIC, new BigDecimal("13"), null, null, null, false, null, null, from, true, 10, note, "SSNIT_EMPLOYER")));
        }
        return added;
    }

    /**
     * Rules that apply to an employee on {@code date}: per code, the latest row in force at the branch, else the latest
     * organisation row; an ending row (inactive) removes the code; employment-type limits are then applied.
     */
    @Transactional(readOnly = true)
    public List<PayrollCalculator.RuleSpec> resolve(UUID branchId, EmploymentType employmentType, LocalDate date) {
        Map<String, List<PayrollRuleEntity>> byCode = repository.findAllByOrderByDisplayOrderAscCodeAscEffectiveFromDesc().stream()
                .filter(r -> !r.getEffectiveFrom().isAfter(date))
                .filter(r -> r.getBranchId() == null || r.getBranchId().equals(branchId))
                .collect(Collectors.groupingBy(PayrollRuleEntity::getCode, LinkedHashMap::new, Collectors.toList()));
        List<PayrollRuleEntity> chosen = new ArrayList<>();
        for (List<PayrollRuleEntity> rows : byCode.values()) {
            Optional<PayrollRuleEntity> branchRow = rows.stream().filter(r -> r.getBranchId() != null).max(Comparator.comparing(PayrollRuleEntity::getEffectiveFrom));
            Optional<PayrollRuleEntity> pick = branchRow.isPresent() ? branchRow
                    : rows.stream().filter(r -> r.getBranchId() == null).max(Comparator.comparing(PayrollRuleEntity::getEffectiveFrom));
            pick.filter(PayrollRuleEntity::isActive)
                    .filter(r -> r.getEmploymentTypes() == null || employmentType == null
                            || Set.of(r.getEmploymentTypes().split(",")).contains(employmentType.name()))
                    .ifPresent(chosen::add);
        }
        chosen.sort(Comparator.comparingInt(PayrollRuleEntity::getDisplayOrder).thenComparing(PayrollRuleEntity::getCode));
        return chosen.stream().map(r -> new PayrollCalculator.RuleSpec(r.getCode(), r.getName(), r.getKind(), r.getMethod(), r.getBase(),
                r.getRatePercent(), r.getFixedAmount(), r.getCapAmount(), brackets(r.getBrackets()), r.isPreTax())).toList();
    }

    private List<PayrollCalculator.Bracket> brackets(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PayrollCalculator.Bracket>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored payroll brackets are unreadable", e);
        }
    }

    private String json(List<PayrollCalculator.Bracket> brackets) {
        try {
            return objectMapper.writeValueAsString(brackets);
        } catch (JsonProcessingException e) {
            throw new ApiException(400, "Invalid bands");
        }
    }

    private RuleView toView(PayrollRuleEntity r) {
        List<EmploymentType> types = r.getEmploymentTypes() == null ? List.of()
                : java.util.Arrays.stream(r.getEmploymentTypes().split(",")).map(EmploymentType::valueOf).toList();
        return new RuleView(r.getId(), r.getCode(), r.getName(), r.getKind(), r.getMethod(), r.getBase(), r.getRatePercent(), r.getFixedAmount(),
                r.getCapAmount(), brackets(r.getBrackets()), r.isPreTax(), types, r.getBranchId(), r.getEffectiveFrom(), r.isActive(),
                r.getDisplayOrder(), r.getNote(), r.getCreatedAt(), r.getReportAs());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record RuleRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_]{2,30}", message = "Use 2–30 letters, digits or underscores") String code,
                              @Size(max = 120) String name, PayrollRuleKind kind, PayrollRuleMethod method, PayrollRuleBase base,
                              @DecimalMin("0") @DecimalMax("100") BigDecimal ratePercent,
                              @DecimalMin("0") BigDecimal fixedAmount, @DecimalMin("0") BigDecimal capAmount,
                              @Valid @Size(max = 20) List<PayrollCalculator.Bracket> brackets,
                              boolean preTax, List<EmploymentType> employmentTypes, UUID branchId,
                              @NotNull LocalDate effectiveFrom, Boolean active, @Min(0) @Max(999) Integer displayOrder,
                              @Size(max = 300) String note,
                              /** SSNIT_EMPLOYEE, SSNIT_EMPLOYER, PAYE or TIER2: how the rule is filed (returns, year to date). */
                              String reportAs) {}

    public record RuleView(UUID id, String code, String name, PayrollRuleKind kind, PayrollRuleMethod method, PayrollRuleBase base,
                           BigDecimal ratePercent, BigDecimal fixedAmount, BigDecimal capAmount, List<PayrollCalculator.Bracket> brackets,
                           boolean preTax, List<EmploymentType> employmentTypes, UUID branchId, LocalDate effectiveFrom, boolean active,
                           int displayOrder, String note, Instant createdAt, String reportAs) {}
}
