package com.restaurantmanager.core.promotion;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin management of Buy-X-Get-Y offers; active offers are applied automatically at order creation. */
@RestController
@RequestMapping("/promotions/offers")
public class MenuOfferController {
    private final MenuOfferRepository offerRepository;
    private final MenuItemRepository menuItemRepository;

    public MenuOfferController(MenuOfferRepository offerRepository, MenuItemRepository menuItemRepository) {
        this.offerRepository = offerRepository;
        this.menuItemRepository = menuItemRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    @Transactional(readOnly = true)
    public List<OfferResponse> list() {
        return offerRepository.findAllWithMenuItem().stream().map(OfferResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public OfferResponse create(@Valid @RequestBody OfferUpsertRequest request) {
        MenuOfferEntity offer = new MenuOfferEntity();
        apply(offer, request);
        return OfferResponse.from(offerRepository.save(offer));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public OfferResponse update(@PathVariable UUID id, @Valid @RequestBody OfferUpsertRequest request) {
        MenuOfferEntity offer = require(id);
        apply(offer, request);
        return OfferResponse.from(offerRepository.save(offer));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public OfferResponse setStatus(@PathVariable UUID id, @RequestBody OfferStatusRequest request) {
        MenuOfferEntity offer = require(id);
        offer.setActive(request.active());
        return OfferResponse.from(offerRepository.save(offer));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public void delete(@PathVariable UUID id) {
        offerRepository.delete(require(id));
    }

    private MenuOfferEntity require(UUID id) {
        return offerRepository.findById(id).orElseThrow(() -> new ApiException(404, "Offer not found"));
    }

    private void apply(MenuOfferEntity offer, OfferUpsertRequest request) {
        if (request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(400, "endsAt must be after startsAt");
        }
        MenuItemEntity item = menuItemRepository.findById(request.menuItemId())
                .orElseThrow(() -> new ApiException(404, "Menu item not found"));
        offer.setName(request.name().trim());
        offer.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        offer.setMenuItem(item);
        offer.setBuyQuantity(request.buyQuantity());
        offer.setFreeQuantity(request.freeQuantity());
        offer.setStartsAt(request.startsAt());
        offer.setEndsAt(request.endsAt());
        offer.setActive(request.active() == null || request.active());
    }

    public record OfferUpsertRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull UUID menuItemId,
            @Min(1) int buyQuantity,
            @Min(1) int freeQuantity,
            Instant startsAt,
            Instant endsAt,
            Boolean active
    ) {}

    public record OfferStatusRequest(boolean active) {}

    public record OfferResponse(UUID id, String name, String description, UUID menuItemId, String menuItemName,
                                int buyQuantity, int freeQuantity, Instant startsAt, Instant endsAt, boolean active,
                                boolean running, Instant createdAt) {
        static OfferResponse from(MenuOfferEntity e) {
            return new OfferResponse(e.getId(), e.getName(), e.getDescription(), e.getMenuItem().getId(),
                    e.getMenuItem().getName(), e.getBuyQuantity(), e.getFreeQuantity(), e.getStartsAt(), e.getEndsAt(),
                    e.isActive(), e.isRunningAt(Instant.now()), e.getCreatedAt());
        }
    }
}
