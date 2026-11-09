package com.restaurantmanager.core.hr.contract;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.common.HrSequenceService;
import com.restaurantmanager.core.hr.document.HrDocumentService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employment contracts. Salary figures are only returned to holders of {@code hr.compensation.view}; audit entries record
 * that pay changed, never the amounts.
 */
@Service
public class ContractService {
    private static final Logger log = LoggerFactory.getLogger(ContractService.class);
    static final String SEQUENCE = "CONTRACT";

    private final ContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final HrDocumentService documentService;
    private final HrAccessService accessService;
    private final HrSequenceService sequenceService;
    private final HrAuditService audit;
    private final ZoneId zone;
    private final String defaultCurrency;

    public ContractService(ContractRepository contractRepository, EmployeeRepository employeeRepository, EmployeeService employeeService,
                           HrDocumentService documentService, HrAccessService accessService, HrSequenceService sequenceService,
                           HrAuditService audit, @Value("${app.timezone:Africa/Accra}") String timeZone,
                           @Value("${payments.default-currency:GHS}") String defaultCurrency) {
        this.contractRepository = contractRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.documentService = documentService;
        this.accessService = accessService;
        this.sequenceService = sequenceService;
        this.audit = audit;
        this.zone = ZoneId.of(timeZone);
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional(readOnly = true)
    public List<ContractView> list(UserPrincipal principal, UUID employeeId) {
        HrAccess access = accessService.require(principal, HrPermission.CONTRACT_VIEW);
        employeeService.loadInScope(employeeId, access);
        return contractRepository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(employeeId).stream()
                .map(c -> toView(c, access, null))
                .toList();
    }

    /** Active contracts ending, or with probation ending, within the next {@code days} days (plus overdue end dates). */
    @Transactional(readOnly = true)
    public List<ContractView> due(UserPrincipal principal, int days) {
        HrAccess access = accessService.require(principal, HrPermission.CONTRACT_VIEW);
        LocalDate today = LocalDate.now(zone);
        List<ContractEntity> rows = contractRepository.findActiveWithDatesDue(today, today.plusDays(Math.min(Math.max(days, 1), 365)));
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(rows.stream().map(ContractEntity::getEmployeeId).distinct().toList())
                .stream().collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        return rows.stream()
                .filter(c -> employees.containsKey(c.getEmployeeId()) && access.canSeeBranch(employees.get(c.getEmployeeId()).primaryBranchId()))
                .map(c -> toView(c, access, employees.get(c.getEmployeeId()).fullName()))
                .toList();
    }

    @Transactional
    public ContractView create(UserPrincipal principal, UUID employeeId, ContractRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.CONTRACT_MANAGE);
        EmployeeEntity employee = employeeService.loadInScope(employeeId, access);
        ContractEntity contract = new ContractEntity();
        contract.setEmployeeId(employee.getId());
        contract.setContractNumber(sequenceService.next(SEQUENCE, "CTR"));
        contract.setCreatedBy(access.userId());
        apply(contract, request, access, employee);
        ContractEntity saved = contractRepository.save(contract);
        audit.log(access.userId(), AuditAction.HR_CONTRACT_CREATED, "Employee", employeeId,
                Map.of("contractId", saved.getId().toString(), "contractNumber", saved.getContractNumber()));
        return toView(saved, access, null);
    }

    @Transactional
    public ContractView update(UserPrincipal principal, UUID contractId, ContractRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.CONTRACT_MANAGE);
        ContractEntity contract = loadInScope(contractId, access);
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new ApiException(409, "Only draft contracts can be edited. Renew or end the contract instead.");
        }
        String payBefore = payKey(contract);
        apply(contract, request, access, employeeRepository.findById(contract.getEmployeeId()).orElseThrow());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("contractId", contractId.toString());
        meta.put("payChanged", !payBefore.equals(payKey(contract)));
        audit.log(access.userId(), AuditAction.HR_CONTRACT_UPDATED, "Employee", contract.getEmployeeId(), meta);
        return toView(contractRepository.saveAndFlush(contract), access, null);
    }

    @Transactional
    public ContractView activate(UserPrincipal principal, UUID contractId) {
        HrAccess access = accessService.require(principal, HrPermission.CONTRACT_MANAGE);
        ContractEntity contract = loadInScope(contractId, access);
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new ApiException(409, "Only draft contracts can be activated");
        }
        if (contract.getEndDate() != null && contract.getEndDate().isBefore(LocalDate.now(zone))) {
            throw new ApiException(400, "This contract has already ended");
        }
        contractRepository.findFirstByEmployeeIdAndStatus(contract.getEmployeeId(), ContractStatus.ACTIVE).ifPresent(active -> {
            if (!active.getId().equals(contract.getPreviousContractId())) {
                throw new ApiException(409, "The employee already has an active contract (" + active.getContractNumber()
                        + "). End it or create this contract as a renewal.");
            }
            active.setStatus(ContractStatus.RENEWED);
            contractRepository.saveAndFlush(active);
            logStatus(access, active, ContractStatus.ACTIVE, ContractStatus.RENEWED, null);
        });
        if (contract.getPreviousContractId() != null) {
            contractRepository.findById(contract.getPreviousContractId())
                    .filter(previous -> previous.getStatus() == ContractStatus.EXPIRED)
                    .ifPresent(previous -> {
                        previous.setStatus(ContractStatus.RENEWED);
                        logStatus(access, previous, ContractStatus.EXPIRED, ContractStatus.RENEWED, null);
                    });
        }
        contract.setStatus(ContractStatus.ACTIVE);
        logStatus(access, contract, ContractStatus.DRAFT, ContractStatus.ACTIVE, null);
        return toView(contractRepository.saveAndFlush(contract), access, null);
    }

    @Transactional
    public ContractView renew(UserPrincipal principal, UUID contractId, ContractRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.CONTRACT_MANAGE);
        ContractEntity previous = loadInScope(contractId, access);
        if (previous.getStatus() != ContractStatus.ACTIVE && previous.getStatus() != ContractStatus.EXPIRED) {
            throw new ApiException(409, "Only active or expired contracts can be renewed");
        }
        if (contractRepository.existsByPreviousContractIdAndStatusIn(previous.getId(), List.of(ContractStatus.DRAFT, ContractStatus.ACTIVE))) {
            throw new ApiException(409, "A renewal of this contract already exists");
        }
        if (previous.getEndDate() != null && request.startDate().isBefore(previous.getStartDate())) {
            throw new ApiException(400, "A renewal cannot start before the original contract");
        }
        EmployeeEntity employee = employeeRepository.findById(previous.getEmployeeId()).orElseThrow();
        ContractEntity renewal = new ContractEntity();
        renewal.setEmployeeId(previous.getEmployeeId());
        renewal.setContractNumber(sequenceService.next(SEQUENCE, "CTR"));
        renewal.setPreviousContractId(previous.getId());
        renewal.setCreatedBy(access.userId());
        apply(renewal, request, access, employee);
        ContractEntity saved = contractRepository.save(renewal);
        audit.log(access.userId(), AuditAction.HR_CONTRACT_CREATED, "Employee", previous.getEmployeeId(),
                Map.of("contractId", saved.getId().toString(), "contractNumber", saved.getContractNumber(),
                        "renewalOf", previous.getContractNumber()));
        return toView(saved, access, null);
    }

    @Transactional
    public ContractView end(UserPrincipal principal, UUID contractId, EndRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.CONTRACT_MANAGE);
        ContractEntity contract = loadInScope(contractId, access);
        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.DRAFT) {
            throw new ApiException(409, "Only draft or active contracts can be ended");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(400, "A reason is required");
        }
        ContractStatus from = contract.getStatus();
        if (from == ContractStatus.ACTIVE) {
            LocalDate endDate = request.endDate() != null ? request.endDate() : LocalDate.now(zone);
            if (endDate.isBefore(contract.getStartDate())) {
                throw new ApiException(400, "End date cannot be before the start date");
            }
            contract.setEndDate(endDate);
        }
        contract.setStatus(ContractStatus.ENDED);
        contract.setEndReason(request.reason().trim());
        logStatus(access, contract, from, ContractStatus.ENDED, request.reason().trim());
        return toView(contractRepository.saveAndFlush(contract), access, null);
    }

    /** Daily: active contracts whose end date has passed become EXPIRED. The employee's status is left unchanged. */
    @Scheduled(cron = "${hr.contracts.expiry-cron:0 15 1 * * *}", zone = "${app.timezone:Africa/Accra}")
    @Transactional
    public int expireEndedContracts() {
        List<ContractEntity> rows = contractRepository.findActiveEndedBefore(LocalDate.now(zone));
        rows.forEach(c -> {
            c.setStatus(ContractStatus.EXPIRED);
            audit.log(null, AuditAction.HR_CONTRACT_STATUS_CHANGED, "Employee", c.getEmployeeId(),
                    Map.of("contractId", c.getId().toString(), "from", "ACTIVE", "to", "EXPIRED", "source", "scheduler"));
        });
        if (!rows.isEmpty()) {
            log.info("Marked {} employment contract(s) as expired", rows.size());
        }
        return rows.size();
    }

    // ------------------------------------------------------------ helpers

    private void apply(ContractEntity contract, ContractRequest request, HrAccess access, EmployeeEntity employee) {
        boolean paySupplied = request.salaryType() != null || request.salaryAmount() != null;
        if (paySupplied && !access.has(HrPermission.COMPENSATION_VIEW)) {
            throw new ApiException(403, "You do not have permission to set pay");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ApiException(400, "End date cannot be before the start date");
        }
        if (request.probationEndDate() != null && (request.probationEndDate().isBefore(request.startDate())
                || (request.endDate() != null && request.probationEndDate().isAfter(request.endDate())))) {
            throw new ApiException(400, "Probation must end within the contract period");
        }
        if ((request.salaryType() == null) != (request.salaryAmount() == null)) {
            throw new ApiException(400, "Provide both the pay basis and the amount, or neither");
        }
        if (request.contractType() != ContractType.PERMANENT && request.contractType() != ContractType.PROBATION
                && request.contractType() != ContractType.PART_TIME && request.endDate() == null) {
            throw new ApiException(400, "A " + request.contractType().name().toLowerCase(Locale.ROOT).replace('_', '-') + " contract needs an end date");
        }
        UUID branchId = request.branchId() != null ? request.branchId() : employee.primaryBranchId();
        access.assertBranch(branchId);
        if (request.documentId() != null) {
            documentService.assertBelongsTo(request.documentId(), employee.getId());
        }
        contract.setContractType(request.contractType());
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setProbationEndDate(request.probationEndDate());
        if (access.has(HrPermission.COMPENSATION_VIEW)) {
            contract.setSalaryType(request.salaryType());
            contract.setSalaryAmount(request.salaryAmount() == null ? null : request.salaryAmount().setScale(2, RoundingMode.HALF_UP));
            contract.setCurrency(request.salaryAmount() == null ? null
                    : (request.currency() == null || request.currency().isBlank() ? defaultCurrency : request.currency().toUpperCase(Locale.ROOT)));
        }
        contract.setWeeklyHours(request.weeklyHours());
        contract.setTerms(request.terms() == null || request.terms().isBlank() ? null : request.terms().trim());
        contract.setBranchId(branchId);
        contract.setDocumentId(request.documentId());
    }

    private ContractEntity loadInScope(UUID contractId, HrAccess access) {
        ContractEntity contract = contractRepository.findById(contractId).orElseThrow(() -> new ApiException(404, "Contract not found"));
        try {
            employeeService.loadInScope(contract.getEmployeeId(), access);
        } catch (ApiException ex) {
            throw new ApiException(404, "Contract not found");
        }
        return contract;
    }

    private void logStatus(HrAccess access, ContractEntity contract, ContractStatus from, ContractStatus to, String reason) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("contractId", contract.getId().toString());
        meta.put("contractNumber", contract.getContractNumber());
        meta.put("from", from.name());
        meta.put("to", to.name());
        if (reason != null) {
            meta.put("reason", reason);
        }
        audit.log(access.userId(), AuditAction.HR_CONTRACT_STATUS_CHANGED, "Employee", contract.getEmployeeId(), meta);
    }

    private static String payKey(ContractEntity c) {
        return c.getSalaryType() + "|" + (c.getSalaryAmount() == null ? null : c.getSalaryAmount().stripTrailingZeros().toPlainString());
    }

    private static ContractView toView(ContractEntity c, HrAccess access, String employeeName) {
        boolean showPay = access.has(HrPermission.COMPENSATION_VIEW);
        return new ContractView(c.getId(), c.getContractNumber(), c.getEmployeeId(), employeeName, c.getContractType(), c.getStatus(),
                c.getStartDate(), c.getEndDate(), c.getProbationEndDate(),
                showPay ? c.getSalaryType() : null, showPay ? c.getSalaryAmount() : null, showPay ? c.getCurrency() : null, showPay,
                c.getWeeklyHours(), c.getTerms(), c.getBranchId(), c.getPreviousContractId(), c.getDocumentId(), c.getEndReason(),
                c.getVersion(), c.getCreatedAt());
    }

    // ------------------------------------------------------------ DTOs

    public record ContractRequest(
            @NotNull ContractType contractType,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            LocalDate probationEndDate,
            SalaryType salaryType,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal salaryAmount,
            @Pattern(regexp = "^$|[A-Za-z]{3}", message = "Use a 3-letter currency code") String currency,
            @DecimalMin(value = "0.5") @DecimalMax("168") BigDecimal weeklyHours,
            @Size(max = 20000) String terms,
            UUID branchId,
            UUID documentId) {}

    public record EndRequest(LocalDate endDate, @Size(max = 500) String reason) {}

    public record ContractView(UUID id, String contractNumber, UUID employeeId, String employeeName, ContractType contractType,
                               ContractStatus status, LocalDate startDate, LocalDate endDate, LocalDate probationEndDate,
                               SalaryType salaryType, BigDecimal salaryAmount, String currency, boolean payVisible,
                               BigDecimal weeklyHours, String terms, UUID branchId, UUID previousContractId, UUID documentId,
                               String endReason, long version, Instant createdAt) {}
}
