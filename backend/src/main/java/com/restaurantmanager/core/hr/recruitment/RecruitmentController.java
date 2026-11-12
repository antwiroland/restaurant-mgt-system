package com.restaurantmanager.core.hr.recruitment;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.hr.document.HrDocumentService;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER')")
public class RecruitmentController {
    private final RecruitmentService recruitment;
    private final CandidateService candidates;

    public RecruitmentController(RecruitmentService recruitment, CandidateService candidates) {
        this.recruitment = recruitment;
        this.candidates = candidates;
    }

    // ---------------------------------------------------------------- vacancies

    @GetMapping("/vacancies")
    public List<RecruitmentService.VacancySummary> vacancies(@RequestParam(required = false) UUID branchId,
                                                             @RequestParam(required = false) VacancyStatus status,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.vacancies(principal, branchId, status);
    }

    @PostMapping("/vacancies")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentService.VacancyDetail createVacancy(@Valid @RequestBody RecruitmentService.VacancyRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.createVacancy(principal, request);
    }

    @GetMapping("/vacancies/{id}")
    public RecruitmentService.VacancyDetail vacancy(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.getVacancy(principal, id);
    }

    @PutMapping("/vacancies/{id}")
    public RecruitmentService.VacancyDetail updateVacancy(@PathVariable UUID id, @Valid @RequestBody RecruitmentService.VacancyRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.updateVacancy(principal, id, request);
    }

    @PostMapping("/vacancies/{id}/status")
    public RecruitmentService.VacancyDetail vacancyStatus(@PathVariable UUID id, @Valid @RequestBody RecruitmentService.VacancyStatusRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.changeVacancyStatus(principal, id, request);
    }

    @PostMapping("/vacancies/{id}/publish")
    public RecruitmentService.VacancyDetail publish(@PathVariable UUID id, @Valid @RequestBody RecruitmentService.PublishRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.setPublished(principal, id, request);
    }

    @PostMapping("/vacancies/{id}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentService.ApplicationDetail apply(@PathVariable UUID id, @Valid @RequestBody RecruitmentService.ApplyRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.apply(principal, id, request);
    }

    // ---------------------------------------------------------------- applications

    @GetMapping("/applications/{id}")
    public RecruitmentService.ApplicationDetail application(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.getApplication(principal, id);
    }

    @PostMapping("/applications/{id}/stage")
    public RecruitmentService.ApplicationDetail move(@PathVariable UUID id, @Valid @RequestBody RecruitmentService.MoveRequest request,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.move(principal, id, request);
    }

    @PostMapping("/applications/{id}/interviews")
    public RecruitmentService.ApplicationDetail scheduleInterview(@PathVariable UUID id, @Valid @RequestBody RecruitmentService.InterviewRequest request,
                                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.scheduleInterview(principal, id, request);
    }

    @PutMapping("/interviews/{id}")
    public RecruitmentService.InterviewView recordInterview(@PathVariable UUID id, @Valid @RequestBody RecruitmentService.InterviewResult request,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.recordInterview(principal, id, request);
    }

    @PutMapping("/applications/{id}/offer")
    public RecruitmentService.ApplicationDetail offer(@PathVariable UUID id, @Valid @RequestBody RecruitmentService.OfferRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.offer(principal, id, request);
    }

    @PostMapping("/applications/{id}/offer-response")
    public RecruitmentService.ApplicationDetail offerResponse(@PathVariable UUID id, @Valid @RequestBody RecruitmentService.OfferResponse request,
                                                              @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.offerResponse(principal, id, request);
    }

    @PostMapping("/applications/{id}/hire")
    public RecruitmentService.HireResult hire(@PathVariable UUID id, @Valid @RequestBody(required = false) RecruitmentService.HireRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.hire(principal, id, request);
    }

    @GetMapping("/me/interviews")
    public List<RecruitmentService.MyInterview> myInterviews(@AuthenticationPrincipal UserPrincipal principal) {
        return recruitment.myInterviews(principal);
    }

    // ---------------------------------------------------------------- candidates

    @GetMapping("/candidates")
    public List<CandidateService.CandidateSummary> candidates(@RequestParam(required = false) String q, @AuthenticationPrincipal UserPrincipal principal) {
        return candidates.search(principal, q);
    }

    @PostMapping("/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateService.CandidateDetail createCandidate(@Valid @RequestBody CandidateService.CandidateRequest request,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return candidates.create(principal, request);
    }

    @GetMapping("/candidates/{id}")
    public CandidateService.CandidateDetail candidate(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return candidates.get(principal, id);
    }

    @PutMapping("/candidates/{id}")
    public CandidateService.CandidateDetail updateCandidate(@PathVariable UUID id, @Valid @RequestBody CandidateService.CandidateRequest request,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return candidates.update(principal, id, request);
    }

    @PostMapping(value = "/candidates/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateService.CandidateDetail uploadDocument(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                                           @RequestParam String title, @AuthenticationPrincipal UserPrincipal principal) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(400, "Could not read the uploaded file");
        }
        return candidates.uploadDocument(principal, id, content, file.getOriginalFilename(), title);
    }

    @GetMapping("/candidate-documents/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        HrDocumentService.DownloadedDocument document = candidates.download(principal, id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(document.fileName()).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(document.content());
    }

    @PostMapping("/candidates/{id}/anonymise")
    public CandidateService.CandidateDetail anonymise(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return candidates.anonymise(principal, id);
    }
}
