package com.restaurantmanager.core.reservation;

import com.restaurantmanager.core.reservation.dto.ReservationCreateRequest;
import com.restaurantmanager.core.reservation.dto.GuestReservationCancelRequest;
import com.restaurantmanager.core.reservation.dto.GuestReservationLookupRequest;
import com.restaurantmanager.core.reservation.dto.ReservationResponse;
import com.restaurantmanager.core.reservation.dto.ReservationStatusUpdateRequest;
import com.restaurantmanager.core.common.Pagination;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody ReservationCreateRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return reservationService.create(request, principal);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ResponseEntity<List<ReservationResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID tableId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<ReservationResponse> all = reservationService.list(date, tableId);
        Pagination.Params params = Pagination.from(page, size);
        List<ReservationResponse> data = Pagination.slice(all, params);
        return ResponseEntity.ok()
                .headers(Pagination.headers(all.size(), params))
                .body(data);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ReservationResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return reservationService.listMine(principal);
    }

    @PostMapping("/guest/lookup")
    public List<ReservationResponse> guestLookup(@Valid @RequestBody GuestReservationLookupRequest request) {
        return reservationService.listGuestReservations(request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ReservationResponse updateStatus(@PathVariable UUID id,
                                            @Valid @RequestBody ReservationStatusUpdateRequest request) {
        return reservationService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public void cancel(@PathVariable UUID id,
                       @AuthenticationPrincipal UserPrincipal principal) {
        reservationService.cancel(id, principal);
    }

    @PostMapping("/{id}/guest-cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guestCancel(@PathVariable UUID id,
                            @Valid @RequestBody GuestReservationCancelRequest request) {
        reservationService.cancelGuest(id, request);
    }
}
