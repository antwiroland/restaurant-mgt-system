package com.restaurantmanager.core.feedback;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/orders/{orderId}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public FeedbackService.FeedbackView submit(@PathVariable UUID orderId,
                                               @Valid @RequestBody FeedbackService.FeedbackRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return feedbackService.submitAsCustomer(orderId, request, principal);
    }

    @PostMapping("/orders/public/dine-in/tables/{tableToken}/orders/{orderId}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackService.FeedbackView submitGuest(@PathVariable String tableToken, @PathVariable UUID orderId,
                                                    @Valid @RequestBody FeedbackService.FeedbackRequest request) {
        return feedbackService.submitAsGuest(tableToken, orderId, request);
    }

    @GetMapping("/orders/public/dine-in/tables/{tableToken}/orders/{orderId}/rating")
    public FeedbackService.GuestRatingPrompt guestRatingPrompt(@PathVariable String tableToken, @PathVariable UUID orderId) {
        return feedbackService.guestRatingPrompt(tableToken, orderId);
    }

    @GetMapping("/orders/{orderId}/feedback")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public FeedbackService.FeedbackView forOrder(@PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        return feedbackService.forOrder(orderId, principal);
    }

    @GetMapping("/feedback")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<FeedbackService.FeedbackView> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer maxRating) {
        return feedbackService.list(from, to, maxRating);
    }

    @GetMapping("/feedback/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public FeedbackService.Summary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return feedbackService.summary(from, to);
    }

    @PostMapping("/feedback/{id}/respond")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public FeedbackService.FeedbackView respond(@PathVariable UUID id,
                                                @Valid @RequestBody FeedbackService.RespondRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return feedbackService.respond(id, request.response(), principal.userId());
    }
}
