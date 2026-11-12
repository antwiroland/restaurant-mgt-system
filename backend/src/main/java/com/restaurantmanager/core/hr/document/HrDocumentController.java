package com.restaurantmanager.core.hr.document;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class HrDocumentController {
    private final HrDocumentService documentService;

    public HrDocumentController(HrDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/employees/{employeeId}/documents")
    public List<HrDocumentService.DocumentView> list(@PathVariable UUID employeeId,
                                                     @RequestParam(defaultValue = "false") boolean includeArchived,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return documentService.list(principal, employeeId, includeArchived);
    }

    @PostMapping(value = "/employees/{employeeId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public HrDocumentService.DocumentView upload(@PathVariable UUID employeeId,
                                                 @RequestPart("file") MultipartFile file,
                                                 @RequestParam(required = false) DocumentType documentType,
                                                 @RequestParam String title,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
                                                 @RequestParam(required = false) String notes,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(400, "Could not read the uploaded file");
        }
        return documentService.upload(principal, employeeId,
                new HrDocumentService.UploadRequest(content, file.getOriginalFilename(), documentType, title, expiresOn, notes));
    }

    @GetMapping("/me/documents")
    public List<HrDocumentService.DocumentView> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return documentService.mine(principal);
    }

    @GetMapping("/me/documents/{id}/download")
    public ResponseEntity<byte[]> downloadMine(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return file(documentService.downloadMine(principal, id));
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return file(documentService.download(principal, id));
    }

    private static ResponseEntity<byte[]> file(HrDocumentService.DownloadedDocument document) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(document.fileName()).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("private, no-store");
        return new ResponseEntity<>(document.content(), headers, HttpStatus.OK);
    }

    @PostMapping("/documents/{id}/archive")
    public HrDocumentService.DocumentView archive(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return documentService.archive(principal, id);
    }
}
