package com.restaurantmanager.core.hr.recruitment;

import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.common.HrSequenceService;
import com.restaurantmanager.core.hr.contract.ContractService;
import com.restaurantmanager.core.hr.contract.ContractType;
import com.restaurantmanager.core.hr.contract.SalaryType;
import com.restaurantmanager.core.hr.document.DocumentType;
import com.restaurantmanager.core.hr.document.HrDocumentService;
import com.restaurantmanager.core.hr.document.HrDocumentStorage;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.hr.employee.EmploymentType;
import com.restaurantmanager.core.hr.org.PositionEntity;
import com.restaurantmanager.core.hr.org.PositionRepository;
import com.restaurantmanager.core.notification.push.PushNotificationService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import jakarta.validation.Valid;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vacancies, the hiring pipeline, interviews, offers and hiring. Everything is limited to the vacancy's branch; pay
 * figures (ranges and offers) need {@code hr.compensation.view} to see or set.
 */
@Service
public class RecruitmentService {
    private static final Set<ContractType> OPEN_ENDED = EnumSet.of(ContractType.PERMANENT, ContractType.PROBATION, ContractType.PART_TIME);
    private static final Set<VacancyStatus> ACTIVE = EnumSet.of(VacancyStatus.OPEN, VacancyStatus.ON_HOLD);

    private final VacancyRepository vacancyRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final InterviewRepository interviewRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final CandidateService candidateService;
    private final PositionRepository positionRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final EmployeeService employeeService;
    private final ContractService contractService;
    private final HrDocumentService documentService;
    private final HrDocumentStorage storage;
    private final HrSequenceService sequences;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final PushNotificationService push;
    private final ZoneId zone;

    public RecruitmentService(VacancyRepository vacancyRepository, ApplicationRepository applicationRepository, ApplicationEventRepository eventRepository,
                              InterviewRepository interviewRepository, CandidateRepository candidateRepository,
                              CandidateDocumentRepository candidateDocumentRepository, CandidateService candidateService,
                              PositionRepository positionRepository, BranchRepository branchRepository, UserRepository userRepository,
                              EmployeeService employeeService, ContractService contractService, HrDocumentService documentService,
                              HrDocumentStorage storage, HrSequenceService sequences, HrAccessService accessService, HrAuditService audit,
                              PushNotificationService push, @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.vacancyRepository = vacancyRepository;
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.interviewRepository = interviewRepository;
        this.candidateRepository = candidateRepository;
        this.candidateDocumentRepository = candidateDocumentRepository;
        this.candidateService = candidateService;
        this.positionRepository = positionRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.employeeService = employeeService;
        this.contractService = contractService;
        this.documentService = documentService;
        this.storage = storage;
        this.sequences = sequences;
        this.accessService = accessService;
        this.audit = audit;
        this.push = push;
        this.zone = ZoneId.of(timeZone);
    }

    // ================================================================ vacancies

    @Transactional(readOnly = true)
    public List<VacancySummary> vacancies(UserPrincipal principal, UUID branchId, VacancyStatus status) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        List<VacancyEntity> rows;
        if (branchId != null) {
            access.assertBranch(branchId);
            rows = vacancyRepository.findByBranchIdInOrderByCreatedAtDesc(List.of(branchId));
        } else if (access.allBranches()) {
            rows = vacancyRepository.findAllByOrderByCreatedAtDesc();
        } else {
            rows = access.branchIds().isEmpty() ? List.of() : vacancyRepository.findByBranchIdInOrderByCreatedAtDesc(access.branchIds());
        }
        rows = rows.stream().filter(v -> status == null || v.getStatus() == status).toList();
        Map<UUID, List<ApplicationEntity>> apps = applicationRepository.findByVacancyIdIn(rows.stream().map(VacancyEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(ApplicationEntity::getVacancyId));
        Lookups lookups = lookups();
        return rows.stream().map(v -> summary(v, apps.getOrDefault(v.getId(), List.of()), access, lookups)).toList();
    }

    @Transactional
    public VacancyDetail createVacancy(UserPrincipal principal, VacancyRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        access.assertBranch(r.branchId());
        if (!branchRepository.existsById(r.branchId())) {
            throw new ApiException(404, "Branch not found");
        }
        VacancyEntity v = new VacancyEntity();
        v.setVacancyNumber(sequences.next("vacancy", "VAC"));
        v.setBranchId(r.branchId());
        apply(v, r, access);
        v.setCreatedBy(access.userId());
        vacancyRepository.save(v);
        log(access, "Vacancy", v.getId(), "vacancy-created", Map.of("vacancyNumber", v.getVacancyNumber()));
        return vacancyDetail(v, access);
    }

    @Transactional
    public VacancyDetail updateVacancy(UserPrincipal principal, UUID id, VacancyRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        VacancyEntity v = loadVacancy(id, access);
        if (v.getStatus() == VacancyStatus.CANCELLED || v.getStatus() == VacancyStatus.CLOSED) {
            throw new ApiException(409, "Reopen the vacancy before editing it");
        }
        if (!v.getBranchId().equals(r.branchId())) {
            throw new ApiException(400, "A vacancy cannot move to another branch; close it and open a new one");
        }
        if (r.openings() < v.getHiredCount()) {
            throw new ApiException(400, v.getHiredCount() + " people were already hired for this vacancy");
        }
        apply(v, r, access);
        if (v.getStatus() == VacancyStatus.FILLED && v.getHiredCount() < v.getOpenings()) {
            v.setStatus(VacancyStatus.OPEN);
            v.setClosedAt(null);
        }
        log(access, "Vacancy", v.getId(), "vacancy-updated", null);
        return vacancyDetail(vacancyRepository.saveAndFlush(v), access);
    }

    private void apply(VacancyEntity v, VacancyRequest r, HrAccess access) {
        if (r.positionId() != null) {
            PositionEntity position = positionRepository.findById(r.positionId()).orElseThrow(() -> new ApiException(404, "Position not found"));
            if (!position.isActive()) {
                throw new ApiException(400, "That position is archived");
            }
        }
        if (r.hiringManagerId() != null) {
            UserEntity manager = userRepository.findById(r.hiringManagerId()).filter(UserEntity::isActive)
                    .orElseThrow(() -> new ApiException(404, "Hiring manager not found"));
            if (manager.getRole() == Role.CUSTOMER) {
                throw new ApiException(400, "The hiring manager must be a staff account");
            }
        }
        boolean pay = r.salaryMin() != null || r.salaryMax() != null;
        if (pay && !access.has(HrPermission.COMPENSATION_VIEW)) {
            throw new ApiException(403, "Setting a pay range needs permission to see pay");
        }
        if (r.salaryMin() != null && r.salaryMax() != null && r.salaryMax().compareTo(r.salaryMin()) < 0) {
            throw new ApiException(400, "The maximum pay is below the minimum");
        }
        v.setPositionId(r.positionId());
        v.setTitle(r.title().trim());
        v.setDescription(blank(r.description()));
        v.setRequirements(blank(r.requirements()));
        v.setEmploymentType(r.employmentType());
        v.setOpenings(r.openings());
        if (access.has(HrPermission.COMPENSATION_VIEW)) {
            v.setSalaryMin(r.salaryMin());
            v.setSalaryMax(r.salaryMax());
        }
        v.setClosingDate(r.closingDate());
        v.setHiringManagerId(r.hiringManagerId());
    }

    /**
     * Draft → Open; Open ⇄ On hold; Open/On hold → Closed or Cancelled; Closed/Filled → Open (reopen). Closing or cancelling
     * rejects every open application with the reason given (accepted offers must be hired or withdrawn first).
     */
    @Transactional
    public VacancyDetail changeVacancyStatus(UserPrincipal principal, UUID id, VacancyStatusRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        VacancyEntity v = loadVacancy(id, access);
        VacancyStatus from = v.getStatus();
        VacancyStatus to = r.status();
        boolean allowed = switch (to) {
            case OPEN -> from == VacancyStatus.DRAFT || from == VacancyStatus.ON_HOLD || from == VacancyStatus.CLOSED
                    || (from == VacancyStatus.FILLED && v.getHiredCount() < v.getOpenings());
            case ON_HOLD -> from == VacancyStatus.OPEN;
            case CLOSED -> from == VacancyStatus.OPEN || from == VacancyStatus.ON_HOLD || from == VacancyStatus.FILLED;
            case CANCELLED -> from == VacancyStatus.DRAFT || from == VacancyStatus.OPEN || from == VacancyStatus.ON_HOLD;
            case DRAFT, FILLED -> false;
        };
        if (!allowed) {
            throw new ApiException(409, "A " + from.name().toLowerCase().replace('_', ' ') + " vacancy cannot become "
                    + to.name().toLowerCase().replace('_', ' '));
        }
        String reason = blank(r.reason());
        if ((to == VacancyStatus.CLOSED || to == VacancyStatus.CANCELLED) && reason == null) {
            throw new ApiException(400, "Give a reason");
        }
        if (to == VacancyStatus.CLOSED || to == VacancyStatus.CANCELLED) {
            List<ApplicationEntity> open = applicationRepository.findByVacancyIdOrderByCreatedAtAsc(v.getId()).stream()
                    .filter(a -> !a.getStage().isClosed()).toList();
            if (open.stream().anyMatch(a -> a.getOfferStatus() == OfferStatus.ACCEPTED)) {
                throw new ApiException(409, "A candidate has accepted an offer; hire them or withdraw the application first");
            }
            open.forEach(a -> moveTo(a, ApplicationStage.REJECTED, "Vacancy " + to.name().toLowerCase() + ": " + reason, access.userId()));
            v.setClosedAt(Instant.now());
            v.setCloseReason(reason);
        }
        if (to == VacancyStatus.OPEN) {
            if (v.getOpenedAt() == null) v.setOpenedAt(Instant.now());
            v.setClosedAt(null);
            v.setCloseReason(null);
        }
        v.setStatus(to);
        log(access, "Vacancy", v.getId(), "vacancy-" + to.name().toLowerCase(), reason == null ? null : Map.of("reason", reason));
        return vacancyDetail(vacancyRepository.saveAndFlush(v), access);
    }

    @Transactional(readOnly = true)
    public VacancyDetail getVacancy(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        return vacancyDetail(loadVacancy(id, access), access);
    }

    // ================================================================ applications

    @Transactional
    public ApplicationDetail apply(UserPrincipal principal, UUID vacancyId, ApplyRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        VacancyEntity v = loadVacancy(vacancyId, access);
        if (v.getStatus() != VacancyStatus.OPEN) {
            throw new ApiException(409, "Applications can only be added to open vacancies");
        }
        CandidateEntity candidate;
        if (r.candidateId() != null) {
            candidate = candidateService.loadVisible(r.candidateId(), access);
            if (candidate.getAnonymisedAt() != null) {
                throw new ApiException(409, "This candidate's personal data was removed");
            }
        } else if (r.candidate() != null) {
            candidate = candidateService.createEntity(access, r.candidate());
        } else {
            throw new ApiException(400, "Choose an existing candidate or enter a new one");
        }
        if (applicationRepository.existsByVacancyIdAndCandidateId(v.getId(), candidate.getId())) {
            throw new ApiException(409, candidate.fullName() + " has already applied for this vacancy");
        }
        ApplicationEntity a = new ApplicationEntity();
        a.setVacancyId(v.getId());
        a.setCandidateId(candidate.getId());
        a.setCreatedBy(access.userId());
        applicationRepository.saveAndFlush(a);
        eventRepository.save(ApplicationEventEntity.of(a.getId(), null, ApplicationStage.APPLIED, blank(r.note()), access.userId()));
        log(access, "Vacancy", v.getId(), "application-added", Map.of("applicationId", a.getId().toString()));
        return applicationDetail(a, access);
    }

    @Transactional(readOnly = true)
    public ApplicationDetail getApplication(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        return applicationDetail(loadApplication(id, access), access);
    }

    @Transactional
    public ApplicationDetail move(UserPrincipal principal, UUID id, MoveRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        ApplicationEntity a = loadApplication(id, access);
        VacancyEntity v = vacancyRepository.findById(a.getVacancyId()).orElseThrow();
        String refusal = a.getStage().refusal(r.stage());
        if (refusal != null) {
            throw new ApiException(409, refusal);
        }
        String note = blank(r.note());
        if (r.stage() == ApplicationStage.REJECTED && note == null) {
            throw new ApiException(400, "Give a reason for rejecting");
        }
        if (!r.stage().isClosed() && !ACTIVE.contains(v.getStatus())) {
            throw new ApiException(409, "The vacancy is " + v.getStatus().name().toLowerCase().replace('_', ' ') + "; reopen it first");
        }
        moveTo(a, r.stage(), note, access.userId());
        log(access, "Vacancy", v.getId(), "application-moved", Map.of("applicationId", a.getId().toString(), "stage", r.stage().name()));
        return applicationDetail(applicationRepository.saveAndFlush(a), access);
    }

    private void moveTo(ApplicationEntity a, ApplicationStage to, String note, UUID actorId) {
        eventRepository.save(ApplicationEventEntity.of(a.getId(), a.getStage(), to, note, actorId));
        a.setStage(to);
        a.setRejectReason(to == ApplicationStage.REJECTED ? note : null);
        if (to.isClosed() && a.getOfferStatus() == OfferStatus.SENT) {
            a.setOfferStatus(null);
        }
        interviewRepository.findByApplicationIdOrderByScheduledAtAsc(a.getId()).stream()
                .filter(i -> to.isClosed() && i.getOutcome() == InterviewOutcome.SCHEDULED)
                .forEach(i -> i.setOutcome(InterviewOutcome.CANCELLED));
    }

    // ================================================================ interviews

    @Transactional
    public ApplicationDetail scheduleInterview(UserPrincipal principal, UUID applicationId, InterviewRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        ApplicationEntity a = loadApplication(applicationId, access);
        if (a.getStage().isClosed()) {
            throw new ApiException(409, "This application is closed");
        }
        UserEntity interviewer = null;
        if (r.interviewerId() != null) {
            interviewer = userRepository.findById(r.interviewerId()).filter(UserEntity::isActive)
                    .orElseThrow(() -> new ApiException(404, "Interviewer not found"));
            if (interviewer.getRole() == Role.CUSTOMER) {
                throw new ApiException(400, "The interviewer must be a staff account");
            }
        }
        InterviewEntity i = new InterviewEntity();
        i.setApplicationId(a.getId());
        i.setKind(r.kind());
        i.setScheduledAt(r.scheduledAt());
        i.setDurationMinutes(r.durationMinutes() == null ? (r.kind() == InterviewKind.TRIAL_SHIFT ? 240 : 30) : r.durationMinutes());
        i.setInterviewerId(r.interviewerId());
        i.setLocation(blank(r.location()));
        i.setCreatedBy(access.userId());
        interviewRepository.save(i);
        if (r.kind() == InterviewKind.TRIAL_SHIFT && a.getStage().ordinal() < ApplicationStage.TRIAL_SHIFT.ordinal()) {
            moveTo(a, ApplicationStage.TRIAL_SHIFT, "Trial shift scheduled", access.userId());
        } else if (a.getStage().ordinal() < ApplicationStage.INTERVIEW.ordinal()) {
            moveTo(a, ApplicationStage.INTERVIEW, "Interview scheduled", access.userId());
        }
        if (interviewer != null && !interviewer.getId().equals(access.userId())) {
            push.notifyUsers(List.of(interviewer.getId()), r.kind() == InterviewKind.TRIAL_SHIFT ? "Trial shift to supervise" : "Interview scheduled",
                    "You are the interviewer on " + r.scheduledAt().atZone(zone).toLocalDate() + ".", Map.of("type", "INTERVIEW_SCHEDULED"));
        }
        log(access, "Application", a.getId(), "interview-scheduled", Map.of("interviewId", i.getId().toString(), "kind", r.kind().name()));
        return applicationDetail(applicationRepository.saveAndFlush(a), access);
    }

    /** The interviewer, or anyone who manages recruitment for the branch, records how it went. */
    @Transactional
    public InterviewView recordInterview(UserPrincipal principal, UUID interviewId, InterviewResult r) {
        HrAccess access = accessService.access(principal);
        InterviewEntity i = interviewRepository.findById(interviewId).orElseThrow(() -> new ApiException(404, "Interview not found"));
        ApplicationEntity a = applicationRepository.findById(i.getApplicationId()).orElseThrow();
        VacancyEntity v = vacancyRepository.findById(a.getVacancyId()).orElseThrow();
        boolean manager = access.has(HrPermission.RECRUITMENT_MANAGE) && access.canSeeBranch(v.getBranchId());
        if (!manager && !access.userId().equals(i.getInterviewerId())) {
            throw new ApiException(404, "Interview not found");
        }
        if (r.outcome() == InterviewOutcome.SCHEDULED) {
            throw new ApiException(400, "Choose how the interview went");
        }
        if ((r.outcome() == InterviewOutcome.PASSED || r.outcome() == InterviewOutcome.FAILED) && r.score() == null) {
            throw new ApiException(400, "Give a score from 1 to 5");
        }
        i.setOutcome(r.outcome());
        i.setScore(r.outcome() == InterviewOutcome.PASSED || r.outcome() == InterviewOutcome.FAILED ? r.score() : null);
        i.setFeedback(blank(r.feedback()));
        log(access, "Application", a.getId(), "interview-recorded", Map.of("interviewId", i.getId().toString(), "outcome", r.outcome().name()));
        return interviewView(interviewRepository.saveAndFlush(i), lookups().users());
    }

    /** Interviews and trial shifts where the signed-in user is the interviewer (any staff member). */
    @Transactional(readOnly = true)
    public List<MyInterview> myInterviews(UserPrincipal principal) {
        HrAccess access = accessService.access(principal);
        Instant from = Instant.now().minus(14, ChronoUnit.DAYS);
        List<InterviewEntity> rows = interviewRepository.findByInterviewerIdAndScheduledAtBetweenOrderByScheduledAtAsc(access.userId(), from,
                Instant.now().plus(60, ChronoUnit.DAYS));
        Map<UUID, ApplicationEntity> apps = applicationRepository.findAllById(rows.stream().map(InterviewEntity::getApplicationId).distinct().toList())
                .stream().collect(Collectors.toMap(ApplicationEntity::getId, Function.identity()));
        Map<UUID, CandidateEntity> candidates = candidateRepository.findAllById(apps.values().stream().map(ApplicationEntity::getCandidateId).distinct().toList())
                .stream().collect(Collectors.toMap(CandidateEntity::getId, Function.identity()));
        Map<UUID, VacancyEntity> vacancies = vacancyRepository.findAllById(apps.values().stream().map(ApplicationEntity::getVacancyId).distinct().toList())
                .stream().collect(Collectors.toMap(VacancyEntity::getId, Function.identity()));
        return rows.stream().map(i -> {
            ApplicationEntity a = apps.get(i.getApplicationId());
            CandidateEntity c = candidates.get(a.getCandidateId());
            VacancyEntity v = vacancies.get(a.getVacancyId());
            return new MyInterview(i.getId(), c.fullName(), v.getTitle(), i.getKind(), i.getScheduledAt(), i.getDurationMinutes(), i.getLocation(),
                    i.getOutcome(), i.getScore(), i.getFeedback());
        }).toList();
    }

    // ================================================================ offers and hiring

    @Transactional
    public ApplicationDetail offer(UserPrincipal principal, UUID id, OfferRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        ApplicationEntity a = loadApplication(id, access);
        if (a.getStage() != ApplicationStage.OFFER) {
            throw new ApiException(409, "Move the application to the offer stage first");
        }
        if ((r.salaryType() == null) != (r.salaryAmount() == null)) {
            throw new ApiException(400, "Give both the pay basis and the amount, or neither");
        }
        if (r.salaryAmount() != null && !access.has(HrPermission.COMPENSATION_VIEW)) {
            throw new ApiException(403, "Offering pay needs permission to see pay");
        }
        if (r.salaryAmount() == null && a.getOfferSalaryAmount() != null && !access.has(HrPermission.COMPENSATION_VIEW)) {
            throw new ApiException(403, "This offer includes pay; someone who can see pay must change it");
        }
        a.setOfferContractType(r.contractType());
        a.setOfferSalaryType(r.salaryType());
        a.setOfferSalaryAmount(r.salaryAmount());
        a.setOfferWeeklyHours(r.weeklyHours());
        a.setOfferStartDate(r.startDate());
        a.setOfferStatus(OfferStatus.SENT);
        a.setOfferSentAt(Instant.now());
        a.setOfferRespondedAt(null);
        eventRepository.save(ApplicationEventEntity.of(a.getId(), a.getStage(), a.getStage(), "Offer sent", access.userId()));
        log(access, "Application", a.getId(), "offer-sent", null);
        return applicationDetail(applicationRepository.saveAndFlush(a), access);
    }

    @Transactional
    public ApplicationDetail offerResponse(UserPrincipal principal, UUID id, OfferResponse r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        ApplicationEntity a = loadApplication(id, access);
        if (a.getStage() != ApplicationStage.OFFER || a.getOfferStatus() != OfferStatus.SENT) {
            throw new ApiException(409, "There is no offer waiting for an answer");
        }
        a.setOfferStatus(r.accepted() ? OfferStatus.ACCEPTED : OfferStatus.DECLINED);
        a.setOfferRespondedAt(Instant.now());
        eventRepository.save(ApplicationEventEntity.of(a.getId(), a.getStage(), a.getStage(),
                (r.accepted() ? "Offer accepted" : "Offer declined") + (blank(r.note()) == null ? "" : ": " + r.note().trim()), access.userId()));
        log(access, "Application", a.getId(), r.accepted() ? "offer-accepted" : "offer-declined", null);
        return applicationDetail(applicationRepository.saveAndFlush(a), access);
    }

    /**
     * Creates the employee from an accepted offer: home branch, position and employment type from the vacancy, start date
     * from the offer. With contract rights and an open-ended contract type, a draft contract with the offered terms is added;
     * the candidate's documents are copied to the employee record. When every opening is filled, the vacancy is marked filled
     * and remaining open applications are closed.
     */
    @Transactional
    public HireResult hire(UserPrincipal principal, UUID id, HireRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE, HrPermission.EMPLOYEE_CREATE);
        ApplicationEntity a = loadApplication(id, access);
        if (a.getStage() != ApplicationStage.OFFER || a.getOfferStatus() != OfferStatus.ACCEPTED) {
            throw new ApiException(409, "Only candidates who accepted an offer can be hired");
        }
        VacancyEntity v = vacancyRepository.findById(a.getVacancyId()).orElseThrow();
        if (!ACTIVE.contains(v.getStatus())) {
            throw new ApiException(409, "The vacancy is " + v.getStatus().name().toLowerCase().replace('_', ' '));
        }
        CandidateEntity c = candidateRepository.findById(a.getCandidateId()).orElseThrow();
        LocalDate hireDate = r != null && r.hireDate() != null ? r.hireDate() : a.getOfferStartDate() != null ? a.getOfferStartDate() : LocalDate.now(zone);

        EmployeeService.EmployeeDetail employee = employeeService.create(principal, new EmployeeService.EmployeeRequest(
                r == null ? null : blank(r.employeeNumber()), null, c.getFirstName(), null, c.getLastName(), null, null, c.getEmail(), c.getPhone(),
                null, null, v.getPositionId(), v.getEmploymentType(), hireDate, null, v.getBranchId(), null, null, List.of(), null));
        UUID employeeId = employee.summary().id();

        String contractNote;
        if (a.getOfferContractType() == null) {
            contractNote = "No contract terms were offered; create the contract from the employee profile";
        } else if (!access.has(HrPermission.CONTRACT_MANAGE)) {
            contractNote = "Someone who manages contracts must create the contract";
        } else if (!OPEN_ENDED.contains(a.getOfferContractType())) {
            contractNote = "This contract type needs an end date; create it from the employee profile";
        } else {
            contractService.create(principal, employeeId, new ContractService.ContractRequest(a.getOfferContractType(), hireDate, null, null,
                    a.getOfferSalaryType(), a.getOfferSalaryAmount(), null, a.getOfferWeeklyHours(), null, null, null));
            contractNote = null;
        }

        int copied = 0;
        for (CandidateDocumentEntity d : candidateDocumentRepository.findByCandidateIdOrderByCreatedAtDesc(c.getId())) {
            // copy the bytes: the candidate's file may later be removed under data protection, the employee's must stay
            HrDocumentService.StoredFile file = documentService.storeFile(storage.read(d.getStorageKey()), d.getFileName());
            documentService.attach(employeeId, access.userId(), file, DocumentType.CV, d.getTitle(), null, "From recruitment " + v.getVacancyNumber());
            copied++;
        }

        eventRepository.save(ApplicationEventEntity.of(a.getId(), a.getStage(), ApplicationStage.HIRED, "Hired as " + employee.summary().employeeNumber(),
                access.userId()));
        a.setStage(ApplicationStage.HIRED);
        a.setEmployeeId(employeeId);
        a.setHiredAt(Instant.now());
        v.setHiredCount(v.getHiredCount() + 1);
        if (v.getHiredCount() >= v.getOpenings()) {
            v.setStatus(VacancyStatus.FILLED);
            v.setClosedAt(Instant.now());
            applicationRepository.findByVacancyIdOrderByCreatedAtAsc(v.getId()).stream()
                    .filter(x -> !x.getId().equals(a.getId()) && !x.getStage().isClosed())
                    .forEach(x -> moveTo(x, ApplicationStage.REJECTED, "Position filled", access.userId()));
        }
        log(access, "Vacancy", v.getId(), "hired", Map.of("applicationId", a.getId().toString(), "employeeId", employeeId.toString()));
        return new HireResult(employeeId, employee.summary().employeeNumber(), contractNote == null, contractNote, copied);
    }

    // ================================================================ helpers

    private VacancyEntity loadVacancy(UUID id, HrAccess access) {
        VacancyEntity v = vacancyRepository.findById(id).orElseThrow(() -> new ApiException(404, "Vacancy not found"));
        if (!access.canSeeBranch(v.getBranchId())) {
            throw new ApiException(404, "Vacancy not found");
        }
        return v;
    }

    private ApplicationEntity loadApplication(UUID id, HrAccess access) {
        ApplicationEntity a = applicationRepository.findById(id).orElseThrow(() -> new ApiException(404, "Application not found"));
        try {
            loadVacancy(a.getVacancyId(), access);
        } catch (ApiException ex) {
            throw new ApiException(404, "Application not found");
        }
        return a;
    }

    private void log(HrAccess access, String entity, UUID id, String operation, Map<String, ?> extra) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", operation);
        if (extra != null) meta.putAll(extra);
        audit.log(access.userId(), AuditAction.HR_RECRUITMENT_CHANGED, entity, id, meta);
    }

    private record Lookups(Map<UUID, String> branches, Map<UUID, String> positions, Map<UUID, String> users) {}

    private Lookups lookups() {
        Map<UUID, String> branches = new HashMap<>();
        branchRepository.findAll().forEach(b -> branches.put(b.getId(), b.getName()));
        Map<UUID, String> positions = new HashMap<>();
        positionRepository.findAll().forEach(p -> positions.put(p.getId(), p.getTitle()));
        Map<UUID, String> users = new HashMap<>();
        userRepository.findByRoleInAndActiveTrueOrderByNameAsc(List.of(Role.ADMIN, Role.MANAGER, Role.CASHIER, Role.RIDER))
                .forEach(u -> users.put(u.getId(), u.getName()));
        return new Lookups(branches, positions, users);
    }

    private VacancySummary summary(VacancyEntity v, List<ApplicationEntity> apps, HrAccess access, Lookups l) {
        Map<ApplicationStage, Long> pipeline = new EnumMap<>(ApplicationStage.class);
        for (ApplicationStage s : ApplicationStage.values()) pipeline.put(s, 0L);
        apps.forEach(a -> pipeline.merge(a.getStage(), 1L, Long::sum));
        boolean pay = access.has(HrPermission.COMPENSATION_VIEW);
        long daysOpen = v.getOpenedAt() == null ? 0 : ChronoUnit.DAYS.between(v.getOpenedAt(), v.getClosedAt() != null ? v.getClosedAt() : Instant.now());
        return new VacancySummary(v.getId(), v.getVacancyNumber(), v.getBranchId(), l.branches().get(v.getBranchId()), v.getPositionId(),
                v.getPositionId() == null ? null : l.positions().get(v.getPositionId()), v.getTitle(), v.getEmploymentType(), v.getOpenings(),
                v.getHiredCount(), pay ? v.getSalaryMin() : null, pay ? v.getSalaryMax() : null, pay, v.getStatus(), v.getClosingDate(),
                v.getHiringManagerId(), v.getHiringManagerId() == null ? null : l.users().get(v.getHiringManagerId()), v.getOpenedAt(), v.getClosedAt(),
                v.getCloseReason(), daysOpen, pipeline, v.getVersion());
    }

    private VacancyDetail vacancyDetail(VacancyEntity v, HrAccess access) {
        List<ApplicationEntity> apps = applicationRepository.findByVacancyIdOrderByCreatedAtAsc(v.getId());
        Map<UUID, CandidateEntity> candidates = candidateRepository.findAllById(apps.stream().map(ApplicationEntity::getCandidateId).toList()).stream()
                .collect(Collectors.toMap(CandidateEntity::getId, Function.identity()));
        Map<UUID, List<InterviewEntity>> interviews = interviewRepository.findByApplicationIdIn(apps.stream().map(ApplicationEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(InterviewEntity::getApplicationId));
        List<ApplicationRow> rows = apps.stream().map(a -> {
            CandidateEntity c = candidates.get(a.getCandidateId());
            List<InterviewEntity> mine = interviews.getOrDefault(a.getId(), List.of());
            OptionalDouble avg = mine.stream().filter(i -> i.getScore() != null).mapToInt(InterviewEntity::getScore).average();
            Instant next = mine.stream().filter(i -> i.getOutcome() == InterviewOutcome.SCHEDULED).map(InterviewEntity::getScheduledAt)
                    .min(Comparator.naturalOrder()).orElse(null);
            return new ApplicationRow(a.getId(), a.getCandidateId(), c.fullName(), c.getPhone(), c.getSource(), a.getStage(), a.getOfferStatus(),
                    avg.isPresent() ? Math.round(avg.getAsDouble() * 10) / 10.0 : null, next, a.getEmployeeId(), a.getCreatedAt());
        }).toList();
        return new VacancyDetail(summary(v, apps, access, lookups()), v.getDescription(), v.getRequirements(), rows);
    }

    private ApplicationDetail applicationDetail(ApplicationEntity a, HrAccess access) {
        VacancyEntity v = vacancyRepository.findById(a.getVacancyId()).orElseThrow();
        CandidateEntity c = candidateRepository.findById(a.getCandidateId()).orElseThrow();
        Lookups l = lookups();
        List<StageEvent> events = eventRepository.findByApplicationIdOrderByCreatedAtAsc(a.getId()).stream()
                .map(e -> new StageEvent(e.getFromStage(), e.getToStage(), e.getNote(), e.getActorId() == null ? null : l.users().get(e.getActorId()),
                        e.getCreatedAt())).toList();
        List<InterviewView> interviews = interviewRepository.findByApplicationIdOrderByScheduledAtAsc(a.getId()).stream()
                .map(i -> interviewView(i, l.users())).toList();
        boolean pay = access.has(HrPermission.COMPENSATION_VIEW);
        Offer offer = a.getOfferStatus() == null && a.getOfferContractType() == null ? null
                : new Offer(a.getOfferContractType(), pay ? a.getOfferSalaryType() : null, pay ? a.getOfferSalaryAmount() : null,
                a.getOfferSalaryAmount() != null && !pay, a.getOfferWeeklyHours(), a.getOfferStartDate(), a.getOfferStatus(), a.getOfferSentAt(),
                a.getOfferRespondedAt());
        List<CandidateService.DocumentView> documents = candidateDocumentRepository.findByCandidateIdOrderByCreatedAtDesc(c.getId()).stream()
                .map(d -> new CandidateService.DocumentView(d.getId(), d.getTitle(), d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getCreatedAt()))
                .toList();
        return new ApplicationDetail(a.getId(), a.getStage(), a.getRejectReason(),
                CandidateService.summary(c, applicationRepository.findByCandidateIdOrderByCreatedAtDesc(c.getId())), c.getNotes(),
                new VacancyRef(v.getId(), v.getVacancyNumber(), v.getTitle(), v.getStatus(), v.getBranchId(), l.branches().get(v.getBranchId())),
                events, interviews, offer, documents, a.getEmployeeId(), a.getHiredAt(), a.getCreatedAt(), pay,
                access.has(HrPermission.EMPLOYEE_CREATE), access.has(HrPermission.CONTRACT_MANAGE));
    }

    private static InterviewView interviewView(InterviewEntity i, Map<UUID, String> users) {
        return new InterviewView(i.getId(), i.getKind(), i.getScheduledAt(), i.getDurationMinutes(), i.getInterviewerId(),
                i.getInterviewerId() == null ? null : users.get(i.getInterviewerId()), i.getLocation(), i.getOutcome(), i.getScore(), i.getFeedback());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ================================================================ DTOs

    public record VacancyRequest(@NotNull UUID branchId, UUID positionId, @NotBlank @Size(max = 160) String title,
                                 @Size(max = 10000) String description, @Size(max = 10000) String requirements,
                                 @NotNull EmploymentType employmentType, @Min(1) @Max(200) int openings,
                                 @DecimalMin("0") BigDecimal salaryMin, @DecimalMin("0") BigDecimal salaryMax, LocalDate closingDate,
                                 UUID hiringManagerId) {}

    public record VacancyStatusRequest(@NotNull VacancyStatus status, @Size(max = 500) String reason) {}

    public record ApplyRequest(UUID candidateId, @Valid CandidateService.CandidateRequest candidate, @Size(max = 500) String note) {}

    public record MoveRequest(@NotNull ApplicationStage stage, @Size(max = 500) String note) {}

    public record InterviewRequest(@NotNull InterviewKind kind, @NotNull Instant scheduledAt, @Min(5) @Max(720) Integer durationMinutes,
                                   UUID interviewerId, @Size(max = 200) String location) {}

    public record InterviewResult(@NotNull InterviewOutcome outcome, @Min(1) @Max(5) Integer score, @Size(max = 2000) String feedback) {}

    public record OfferRequest(ContractType contractType, SalaryType salaryType, @DecimalMin("0") BigDecimal salaryAmount,
                               @DecimalMin("0.5") @DecimalMax("168") BigDecimal weeklyHours, @NotNull LocalDate startDate) {}

    public record OfferResponse(boolean accepted, @Size(max = 500) String note) {}

    public record HireRequest(LocalDate hireDate, @Size(max = 30) String employeeNumber) {}

    public record HireResult(UUID employeeId, String employeeNumber, boolean contractCreated, String contractNote, int documentsCopied) {}

    public record VacancySummary(UUID id, String vacancyNumber, UUID branchId, String branchName, UUID positionId, String positionTitle, String title,
                                 EmploymentType employmentType, int openings, int hiredCount, BigDecimal salaryMin, BigDecimal salaryMax,
                                 boolean payVisible, VacancyStatus status, LocalDate closingDate, UUID hiringManagerId, String hiringManagerName,
                                 Instant openedAt, Instant closedAt, String closeReason, long daysOpen, Map<ApplicationStage, Long> pipeline,
                                 long version) {}

    public record ApplicationRow(UUID id, UUID candidateId, String candidateName, String phone, CandidateSource source, ApplicationStage stage,
                                 OfferStatus offerStatus, Double averageScore, Instant nextInterviewAt, UUID employeeId, Instant appliedAt) {}

    public record VacancyDetail(VacancySummary summary, String description, String requirements, List<ApplicationRow> applications) {}

    public record VacancyRef(UUID id, String vacancyNumber, String title, VacancyStatus status, UUID branchId, String branchName) {}

    public record StageEvent(ApplicationStage fromStage, ApplicationStage toStage, String note, String actorName, Instant at) {}

    public record InterviewView(UUID id, InterviewKind kind, Instant scheduledAt, int durationMinutes, UUID interviewerId, String interviewerName,
                                String location, InterviewOutcome outcome, Integer score, String feedback) {}

    public record Offer(ContractType contractType, SalaryType salaryType, BigDecimal salaryAmount, boolean payHidden, BigDecimal weeklyHours,
                        LocalDate startDate, OfferStatus status, Instant sentAt, Instant respondedAt) {}

    public record ApplicationDetail(UUID id, ApplicationStage stage, String rejectReason, CandidateService.CandidateSummary candidate,
                                    String candidateNotes, VacancyRef vacancy, List<StageEvent> events, List<InterviewView> interviews, Offer offer,
                                    List<CandidateService.DocumentView> documents, UUID employeeId, Instant hiredAt, Instant appliedAt,
                                    boolean payVisible, boolean canHire, boolean canCreateContract) {}

    public record MyInterview(UUID id, String candidateName, String vacancyTitle, InterviewKind kind, Instant scheduledAt, int durationMinutes,
                              String location, InterviewOutcome outcome, Integer score, String feedback) {}
}
