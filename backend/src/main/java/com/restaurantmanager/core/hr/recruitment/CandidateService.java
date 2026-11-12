package com.restaurantmanager.core.hr.recruitment;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.document.HrDocumentService;
import com.restaurantmanager.core.hr.document.HrDocumentStorage;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Candidates: one record per person (matched by phone or email), their private documents, and anonymisation once no
 * application is open. A candidate is visible to central HR, to whoever registered them, and to anyone who can see a
 * branch they applied to.
 */
@Service
public class CandidateService {
    private final CandidateRepository candidateRepository;
    private final CandidateDocumentRepository documentRepository;
    private final ApplicationRepository applicationRepository;
    private final VacancyRepository vacancyRepository;
    private final EmployeeRepository employeeRepository;
    private final HrDocumentService documentService;
    private final HrDocumentStorage storage;
    private final HrAccessService accessService;
    private final HrAuditService audit;

    public CandidateService(CandidateRepository candidateRepository, CandidateDocumentRepository documentRepository,
                            ApplicationRepository applicationRepository, VacancyRepository vacancyRepository, EmployeeRepository employeeRepository,
                            HrDocumentService documentService, HrDocumentStorage storage, HrAccessService accessService, HrAuditService audit) {
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
        this.applicationRepository = applicationRepository;
        this.vacancyRepository = vacancyRepository;
        this.employeeRepository = employeeRepository;
        this.documentService = documentService;
        this.storage = storage;
        this.accessService = accessService;
        this.audit = audit;
    }

    // ------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<CandidateSummary> search(UserPrincipal principal, String q) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        List<CandidateEntity> rows = q == null || q.isBlank() ? candidateRepository.findTop200ByOrderByCreatedAtDesc()
                : candidateRepository.search("%" + q.trim().toLowerCase(Locale.ROOT) + "%");
        Map<UUID, List<ApplicationEntity>> apps = applicationRepository.findByCandidateIdIn(rows.stream().map(CandidateEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(ApplicationEntity::getCandidateId));
        Map<UUID, UUID> vacancyBranch = vacancyBranches(apps.values().stream().flatMap(List::stream).map(ApplicationEntity::getVacancyId).toList());
        return rows.stream()
                .filter(c -> visible(access, c, apps.getOrDefault(c.getId(), List.of()), vacancyBranch))
                .limit(200)
                .map(c -> summary(c, apps.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CandidateDetail get(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        CandidateEntity c = loadVisible(id, access);
        return detail(c);
    }

    // ------------------------------------------------------------ changes

    @Transactional
    public CandidateDetail create(UserPrincipal principal, CandidateRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        return detail(createEntity(access, r));
    }

    /** Registers a candidate after checking for an existing person with the same phone or email. */
    CandidateEntity createEntity(HrAccess access, CandidateRequest r) {
        String phone = blank(r.phone());
        String email = blank(r.email());
        if (phone == null && email == null) {
            throw new ApiException(400, "Give a phone number or an email address so the candidate can be contacted");
        }
        duplicate(phone, email, null);
        CandidateEntity c = new CandidateEntity();
        apply(c, r);
        c.setCreatedBy(access.userId());
        candidateRepository.save(c);
        audit.log(access.userId(), AuditAction.HR_RECRUITMENT_CHANGED, "Candidate", c.getId(), Map.of("operation", "candidate-registered"));
        return c;
    }

    @Transactional
    public CandidateDetail update(UserPrincipal principal, UUID id, CandidateRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        CandidateEntity c = loadVisible(id, access);
        if (c.getAnonymisedAt() != null) {
            throw new ApiException(409, "This candidate's personal data was removed");
        }
        if (blank(r.phone()) == null && blank(r.email()) == null) {
            throw new ApiException(400, "Give a phone number or an email address so the candidate can be contacted");
        }
        duplicate(blank(r.phone()), blank(r.email()), c.getId());
        apply(c, r);
        audit.log(access.userId(), AuditAction.HR_RECRUITMENT_CHANGED, "Candidate", c.getId(), Map.of("operation", "candidate-updated"));
        return detail(candidateRepository.saveAndFlush(c));
    }

    private void duplicate(String phone, String email, UUID self) {
        CandidateEntity existing = null;
        if (phone != null) {
            existing = candidateRepository.findByPhone(phone).stream().filter(x -> !x.getId().equals(self) && x.getAnonymisedAt() == null).findFirst().orElse(null);
        }
        if (existing == null && email != null) {
            existing = candidateRepository.findByEmail(email).stream().filter(x -> !x.getId().equals(self) && x.getAnonymisedAt() == null).findFirst().orElse(null);
        }
        if (existing != null) {
            throw new ApiException(409, "A candidate with this phone number or email already exists: " + existing.fullName()
                    + ". Add the application to the existing candidate.");
        }
    }

    private void apply(CandidateEntity c, CandidateRequest r) {
        if (r.referredByEmployeeId() != null && !employeeRepository.existsById(r.referredByEmployeeId())) {
            throw new ApiException(400, "Referring employee not found");
        }
        c.setFirstName(r.firstName().trim());
        c.setLastName(blank(r.lastName()));
        c.setPhone(blank(r.phone()));
        c.setEmail(blank(r.email()) == null ? null : r.email().trim().toLowerCase(Locale.ROOT));
        c.setSource(r.source());
        c.setReferredByEmployeeId(r.source() == CandidateSource.REFERRAL ? r.referredByEmployeeId() : null);
        c.setNotes(blank(r.notes()));
    }

    @Transactional
    public CandidateDetail uploadDocument(UserPrincipal principal, UUID id, byte[] content, String fileName, String title) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        CandidateEntity c = loadVisible(id, access);
        if (c.getAnonymisedAt() != null) {
            throw new ApiException(409, "This candidate's personal data was removed");
        }
        if (title == null || title.isBlank() || title.length() > 160) {
            throw new ApiException(400, "A title of up to 160 characters is required");
        }
        HrDocumentService.StoredFile file = documentService.storeFile(content, fileName);
        CandidateDocumentEntity d = new CandidateDocumentEntity();
        d.setCandidateId(c.getId());
        d.setTitle(title.trim());
        d.setFileName(file.fileName());
        d.setContentType(file.contentType());
        d.setSizeBytes(file.sizeBytes());
        d.setSha256(file.sha256());
        d.setStorageKey(file.storageKey());
        d.setUploadedBy(access.userId());
        documentRepository.save(d);
        audit.log(access.userId(), AuditAction.HR_RECRUITMENT_CHANGED, "Candidate", c.getId(),
                Map.of("operation", "document-uploaded", "documentId", d.getId().toString()));
        return detail(c);
    }

    @Transactional
    public HrDocumentService.DownloadedDocument download(UserPrincipal principal, UUID documentId) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        CandidateDocumentEntity d = documentRepository.findById(documentId).orElseThrow(() -> new ApiException(404, "Document not found"));
        try {
            loadVisible(d.getCandidateId(), access);
        } catch (ApiException ex) {
            throw new ApiException(404, "Document not found");
        }
        audit.log(access.userId(), AuditAction.HR_RECRUITMENT_CHANGED, "Candidate", d.getCandidateId(),
                Map.of("operation", "document-downloaded", "documentId", documentId.toString()));
        return new HrDocumentService.DownloadedDocument(d.getFileName(), d.getContentType(), storage.read(d.getStorageKey()));
    }

    /**
     * Removes personal data (name, contact details, notes, documents) once no application is open. The row and the
     * application history stay, so recruitment statistics remain correct. Employee records of hired candidates are untouched.
     */
    @Transactional
    public CandidateDetail anonymise(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.RECRUITMENT_MANAGE);
        CandidateEntity c = loadVisible(id, access);
        if (c.getAnonymisedAt() != null) {
            return detail(c);
        }
        if (applicationRepository.findByCandidateIdOrderByCreatedAtDesc(c.getId()).stream().anyMatch(a -> !a.getStage().isClosed())) {
            throw new ApiException(409, "Close (reject or withdraw) every open application first");
        }
        List<CandidateDocumentEntity> docs = documentRepository.findByCandidateIdOrderByCreatedAtDesc(c.getId());
        documentRepository.deleteAll(docs);
        docs.forEach(d -> storage.deleteQuietly(d.getStorageKey()));
        c.setFirstName("Removed");
        c.setLastName(null);
        c.setPhone(null);
        c.setEmail(null);
        c.setNotes(null);
        c.setReferredByEmployeeId(null);
        c.setAnonymisedAt(Instant.now());
        audit.log(access.userId(), AuditAction.HR_RECRUITMENT_CHANGED, "Candidate", c.getId(),
                Map.of("operation", "anonymised", "documentsRemoved", docs.size()));
        return detail(candidateRepository.saveAndFlush(c));
    }

    // ------------------------------------------------------------ helpers

    CandidateEntity loadVisible(UUID id, HrAccess access) {
        CandidateEntity c = candidateRepository.findById(id).orElseThrow(() -> new ApiException(404, "Candidate not found"));
        List<ApplicationEntity> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(id);
        if (!visible(access, c, apps, vacancyBranches(apps.stream().map(ApplicationEntity::getVacancyId).toList()))) {
            throw new ApiException(404, "Candidate not found");
        }
        return c;
    }

    private static boolean visible(HrAccess access, CandidateEntity c, List<ApplicationEntity> apps, Map<UUID, UUID> vacancyBranch) {
        return access.allBranches() || access.userId().equals(c.getCreatedBy())
                || apps.stream().anyMatch(a -> access.canSeeBranch(vacancyBranch.get(a.getVacancyId())));
    }

    private Map<UUID, UUID> vacancyBranches(List<UUID> vacancyIds) {
        Map<UUID, UUID> map = new HashMap<>();
        vacancyRepository.findAllById(vacancyIds.stream().distinct().toList()).forEach(v -> map.put(v.getId(), v.getBranchId()));
        return map;
    }

    private CandidateDetail detail(CandidateEntity c) {
        List<ApplicationEntity> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(c.getId());
        Map<UUID, VacancyEntity> vacancies = new HashMap<>();
        vacancyRepository.findAllById(apps.stream().map(ApplicationEntity::getVacancyId).distinct().toList()).forEach(v -> vacancies.put(v.getId(), v));
        List<CandidateApplication> applications = apps.stream().map(a -> {
            VacancyEntity v = vacancies.get(a.getVacancyId());
            return new CandidateApplication(a.getId(), a.getVacancyId(), v == null ? null : v.getTitle(), v == null ? null : v.getVacancyNumber(),
                    a.getStage(), a.getCreatedAt());
        }).toList();
        List<DocumentView> documents = documentRepository.findByCandidateIdOrderByCreatedAtDesc(c.getId()).stream()
                .map(d -> new DocumentView(d.getId(), d.getTitle(), d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getCreatedAt())).toList();
        return new CandidateDetail(summary(c, apps), c.getNotes(), c.getReferredByEmployeeId(),
                c.getReferredByEmployeeId() == null ? null : employeeRepository.findById(c.getReferredByEmployeeId()).map(e -> e.fullName()).orElse(null),
                applications, documents);
    }

    static CandidateSummary summary(CandidateEntity c, List<ApplicationEntity> apps) {
        long open = apps.stream().filter(a -> !a.getStage().isClosed()).count();
        return new CandidateSummary(c.getId(), c.fullName(), c.getPhone(), c.getEmail(), c.getSource(), (int) open, apps.size(),
                c.getAnonymisedAt() != null, c.getCreatedAt());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ------------------------------------------------------------ DTOs

    public record CandidateRequest(@NotBlank @Size(max = 80) String firstName, @Size(max = 80) String lastName,
                                   @Size(max = 20) @Pattern(regexp = "^$|[+0-9 ()-]{6,20}", message = "Enter a valid phone number") String phone,
                                   @Email @Size(max = 255) String email, @NotNull CandidateSource source, UUID referredByEmployeeId,
                                   @Size(max = 2000) String notes) {}

    public record CandidateSummary(UUID id, String fullName, String phone, String email, CandidateSource source, int openApplications,
                                   int applications, boolean anonymised, Instant createdAt) {}

    public record CandidateApplication(UUID id, UUID vacancyId, String vacancyTitle, String vacancyNumber, ApplicationStage stage, Instant appliedAt) {}

    public record DocumentView(UUID id, String title, String fileName, String contentType, long sizeBytes, Instant uploadedAt) {}

    public record CandidateDetail(CandidateSummary summary, String notes, UUID referredByEmployeeId, String referredByName,
                                  List<CandidateApplication> applications, List<DocumentView> documents) {}
}
