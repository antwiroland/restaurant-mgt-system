package com.restaurantmanager.core.reservation;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.reservation.dto.ReservationCreateRequest;
import com.restaurantmanager.core.reservation.dto.ReservationResponse;
import com.restaurantmanager.core.reservation.dto.ReservationStatusUpdateRequest;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {
    private static final int DEFAULT_DURATION_MINS = 90;

    private final ReservationRepository reservationRepository;
    private final RestaurantTableRepository tableRepository;
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              RestaurantTableRepository tableRepository,
                              UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReservationResponse create(ReservationCreateRequest request, UserPrincipal principal) {
        RestaurantTableEntity table = tableRepository.findById(request.tableId())
                .orElseThrow(() -> new ApiException(404, "Table not found"));

        int durationMins = request.durationMins() == null ? DEFAULT_DURATION_MINS : request.durationMins();
        Instant requestedStart = request.reservedAt();
        Instant requestedEnd = request.reservedAt().plusSeconds(durationMins * 60L);

        boolean conflict = reservationRepository.findAll().stream()
                .filter(existing -> existing.getTable().getId().equals(table.getId()))
                .filter(existing -> existing.getStatus() != ReservationStatus.CANCELLED)
                .anyMatch(existing -> {
                    Instant existingStart = existing.getReservedAt();
                    Instant existingEnd = existing.getReservedAt().plusSeconds(existing.getDurationMins() * 60L);
                    return existingStart.isBefore(requestedEnd) && existingEnd.isAfter(requestedStart);
                });
        if (conflict) {
            throw new ApiException(409, "Table already reserved for the selected time");
        }

        ReservationEntity reservation = new ReservationEntity();
        reservation.setTable(table);
        reservation.setPartySize(request.partySize());
        reservation.setReservedAt(request.reservedAt());
        reservation.setDurationMins(durationMins);
        reservation.setNotes(blankToNull(request.notes()));
        reservation.setStatus(ReservationStatus.PENDING);

        if (principal != null && principal.role() == Role.CUSTOMER) {
            UserEntity customer = userRepository.findById(principal.userId())
                    .orElseThrow(() -> new ApiException(401, "User not found"));
            reservation.setCustomerUserId(customer.getId());
            reservation.setCustomerName(valueOrFallback(request.customerName(), customer.getName()));
            reservation.setCustomerPhone(valueOrFallback(request.customerPhone(), customer.getPhone()));
        } else {
            if (request.customerName() == null || request.customerName().isBlank()) {
                throw new ApiException(400, "customerName is required for guest reservation");
            }
            if (request.customerPhone() == null || request.customerPhone().isBlank()) {
                throw new ApiException(400, "customerPhone is required for guest reservation");
            }
            reservation.setCustomerName(request.customerName().trim());
            reservation.setCustomerPhone(request.customerPhone().trim());
        }

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(LocalDate date, UUID tableId) {
        return reservationRepository.findAll().stream()
                .filter(r -> tableId == null || r.getTable().getId().equals(tableId))
                .filter(r -> {
                    if (date == null) {
                        return true;
                    }
                    LocalDate reservationDate = r.getReservedAt().atOffset(ZoneOffset.UTC).toLocalDate();
                    return reservationDate.equals(date);
                })
                .sorted((a, b) -> a.getReservedAt().compareTo(b.getReservedAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listMine(UserPrincipal principal) {
        if (principal == null || principal.role() != Role.CUSTOMER) {
            throw new ApiException(403, "Customer access required");
        }

        return reservationRepository.findAllByCustomerUserIdOrderByReservedAtAsc(principal.userId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReservationResponse updateStatus(UUID id, ReservationStatusUpdateRequest request) {
        ReservationEntity reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Reservation not found"));
        reservation.setStatus(request.status());
        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public void cancel(UUID id, UserPrincipal principal) {
        ReservationEntity reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Reservation not found"));

        if (principal.role() == Role.CUSTOMER) {
            if (reservation.getCustomerUserId() == null || !reservation.getCustomerUserId().equals(principal.userId())) {
                throw new ApiException(403, "You can only cancel your own reservation");
            }
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
    }

    private ReservationResponse toResponse(ReservationEntity reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getTable().getId(),
                reservation.getTable().getNumber(),
                reservation.getCustomerName(),
                reservation.getCustomerPhone(),
                reservation.getPartySize(),
                reservation.getReservedAt(),
                reservation.getDurationMins(),
                reservation.getStatus(),
                reservation.getNotes());
    }

    private String valueOrFallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
