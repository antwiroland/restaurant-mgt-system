package com.restaurantmanager.core.hr.document;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HrDocumentService {
    private final EmployeeDocumentRepository repository;
    private final HrDocumentStorage storage;
    private final EmployeeService employeeService;
    private final HrAccessService accessService;
    private final HrAuditService audit;

    public HrDocumentService(EmployeeDocumentRepository repository, HrDocumentStorage storage, EmployeeService employeeService,
                             HrAccessService accessService, HrAuditService audit) {
        this.repository = repository;
        this.storage = storage;
        this.employeeService = employeeService;
        this.accessService = accessService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list(UserPrincipal principal, UUID employeeId, boolean includeArchived) {
        HrAccess access = accessService.require(principal, HrPermission.DOCUMENT_VIEW);
        employeeService.loadInScope(employeeId, access);
        List<EmployeeDocumentEntity> rows = includeArchived
                ? repository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                : repository.findByEmployeeIdAndArchivedFalseOrderByCreatedAtDesc(employeeId);
        return rows.stream().map(HrDocumentService::toView).toList();
    }

    @Transactional
    public DocumentView upload(UserPrincipal principal, UUID employeeId, UploadRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.DOCUMENT_MANAGE);
        employeeService.loadInScope(employeeId, access);
        return store(employeeId, access.userId(), request);
    }

    /**
     * Validates and stores a document for an employee. Callers are responsible for authorisation; used by {@link #upload}
     * and by features (leave) whose own rules decide who may attach a document to an employee's record.
     */
    @Transactional
    public DocumentView store(UUID employeeId, UUID actorId, UploadRequest request) {
        byte[] content = request.content();
        if (content == null || content.length == 0) {
            throw new ApiException(400, "The file is empty");
        }
        if (content.length > DocumentFileType.MAX_BYTES) {
            throw new ApiException(413, "Documents must be 5 MB or smaller");
        }
        DocumentFileType type = DocumentFileType.detect(content, request.originalFileName())
                .orElseThrow(() -> new ApiException(400, "Only PDF, PNG, JPEG or DOCX files can be uploaded"));
        if (request.title() == null || request.title().isBlank() || request.title().length() > 160) {
            throw new ApiException(400, "A title of up to 160 characters is required");
        }
        if (request.notes() != null && request.notes().length() > 500) {
            throw new ApiException(400, "Notes must be 500 characters or fewer");
        }
        String key = storage.store(content);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.deleteQuietly(key);
                    }
                }
            });
        }
        EmployeeDocumentEntity document = new EmployeeDocumentEntity();
        document.setEmployeeId(employeeId);
        document.setDocumentType(request.documentType() == null ? DocumentType.OTHER : request.documentType());
        document.setTitle(request.title().trim());
        document.setFileName(safeFileName(request.originalFileName(), type));
        document.setContentType(type.contentType());
        document.setSizeBytes(content.length);
        document.setSha256(sha256(content));
        document.setStorageKey(key);
        document.setExpiresOn(request.expiresOn());
        document.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        document.setUploadedBy(actorId);
        EmployeeDocumentEntity saved = repository.save(document);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("documentId", saved.getId().toString());
        meta.put("documentType", saved.getDocumentType().name());
        meta.put("title", saved.getTitle());
        audit.log(actorId, AuditAction.HR_DOCUMENT_UPLOADED, "Employee", employeeId, meta);
        return toView(saved);
    }

    @Transactional
    public DownloadedDocument download(UserPrincipal principal, UUID documentId) {
        HrAccess access = accessService.require(principal, HrPermission.DOCUMENT_VIEW);
        EmployeeDocumentEntity document = loadInScope(documentId, access);
        byte[] content = storage.read(document.getStorageKey());
        audit.log(access.userId(), AuditAction.HR_DOCUMENT_DOWNLOADED, "Employee", document.getEmployeeId(),
                Map.of("documentId", documentId.toString()));
        return new DownloadedDocument(document.getFileName(), document.getContentType(), content);
    }

    @Transactional
    public DocumentView archive(UserPrincipal principal, UUID documentId) {
        HrAccess access = accessService.require(principal, HrPermission.DOCUMENT_MANAGE);
        EmployeeDocumentEntity document = loadInScope(documentId, access);
        if (!document.isArchived()) {
            document.setArchived(true);
            audit.log(access.userId(), AuditAction.HR_DOCUMENT_ARCHIVED, "Employee", document.getEmployeeId(),
                    Map.of("documentId", documentId.toString()));
        }
        return toView(document);
    }

    /** For other HR services (contracts) that attach a document: verifies it belongs to the employee. */
    @Transactional(readOnly = true)
    public void assertBelongsTo(UUID documentId, UUID employeeId) {
        EmployeeDocumentEntity document = repository.findById(documentId).orElseThrow(() -> new ApiException(404, "Document not found"));
        if (!document.getEmployeeId().equals(employeeId) || document.isArchived()) {
            throw new ApiException(400, "The document must be an active document of the same employee");
        }
    }

    private EmployeeDocumentEntity loadInScope(UUID documentId, HrAccess access) {
        EmployeeDocumentEntity document = repository.findById(documentId).orElseThrow(() -> new ApiException(404, "Document not found"));
        try {
            employeeService.loadInScope(document.getEmployeeId(), access);
        } catch (ApiException ex) {
            throw new ApiException(404, "Document not found");
        }
        return document;
    }

    static String safeFileName(String original, DocumentFileType type) {
        String base = original == null ? "" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        if (base.isBlank()) {
            base = "document";
        }
        if (base.length() > 150) {
            base = base.substring(0, 150);
        }
        return base + "." + type.extension();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DocumentView toView(EmployeeDocumentEntity d) {
        return new DocumentView(d.getId(), d.getEmployeeId(), d.getDocumentType(), d.getTitle(), d.getFileName(), d.getContentType(),
                d.getSizeBytes(), d.getExpiresOn(), d.getNotes(), d.isArchived(), d.getCreatedAt());
    }

    public record UploadRequest(byte[] content, String originalFileName, DocumentType documentType, String title,
                                LocalDate expiresOn, String notes) {}

    public record DocumentView(UUID id, UUID employeeId, DocumentType documentType, String title, String fileName,
                               String contentType, long sizeBytes, LocalDate expiresOn, String notes, boolean archived,
                               Instant uploadedAt) {}

    public record DownloadedDocument(String fileName, String contentType, byte[] content) {}
}
