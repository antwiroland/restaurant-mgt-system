package com.restaurantmanager.core.order;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.delivery.DeliveryZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Picks the branch for an order that did not get one from its table (dine-in)
 * or from the staff member who created it.
 *
 * <p>Without this, orders placed by customers in the mobile app (pickup and
 * delivery) had no branch, so staff assigned to a branch could neither see nor
 * process them. Resolution order:
 * <ol>
 *   <li>the branch the client asked for ({@code branchId} on the request), if it exists and is active;</li>
 *   <li>the branch of the chosen delivery zone;</li>
 *   <li>the configured default branch ({@code app.orders.default-branch-code});</li>
 *   <li>the only active branch, when exactly one exists.</li>
 * </ol>
 * If none of these applies (several branches and no default) the order stays
 * unassigned; branch-scoped staff can still see and handle unassigned orders.
 */
@Component
public class OrderBranchResolver {
    private final BranchRepository branchRepository;
    private final DeliveryZoneRepository zoneRepository;
    private final String defaultBranchCode;

    public OrderBranchResolver(BranchRepository branchRepository,
                               DeliveryZoneRepository zoneRepository,
                               @Value("${app.orders.default-branch-code:}") String defaultBranchCode) {
        this.branchRepository = branchRepository;
        this.zoneRepository = zoneRepository;
        this.defaultBranchCode = defaultBranchCode == null ? "" : defaultBranchCode.trim();
    }

    public Optional<BranchEntity> resolve(UUID requestedBranchId, UUID deliveryZoneId) {
        if (requestedBranchId != null) {
            BranchEntity requested = branchRepository.findById(requestedBranchId)
                    .filter(BranchEntity::isActive)
                    .orElseThrow(() -> new ApiException(400, "Branch not available"));
            return Optional.of(requested);
        }
        if (deliveryZoneId != null) {
            Optional<BranchEntity> zoneBranch = zoneRepository.findById(deliveryZoneId)
                    .map(zone -> zone.getBranchId())
                    .flatMap(branchRepository::findById)
                    .filter(BranchEntity::isActive);
            if (zoneBranch.isPresent()) {
                return zoneBranch;
            }
        }
        if (!defaultBranchCode.isEmpty()) {
            Optional<BranchEntity> configured = branchRepository.findByCodeIgnoreCase(defaultBranchCode)
                    .filter(BranchEntity::isActive);
            if (configured.isPresent()) {
                return configured;
            }
        }
        List<BranchEntity> active = branchRepository.findAll().stream().filter(BranchEntity::isActive).toList();
        return active.size() == 1 ? Optional.of(active.get(0)) : Optional.empty();
    }
}
