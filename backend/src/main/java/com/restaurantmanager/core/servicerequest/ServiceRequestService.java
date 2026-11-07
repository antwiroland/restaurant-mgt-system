package com.restaurantmanager.core.servicerequest;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Call a waiter" / "Bring the bill" from the table QR page. Staff see open requests live on
 * {@code /topic/service-requests}; the guest's page follows its own table on
 * {@code /topic/public.tables.{token}.requests}.
 */
@Service
public class ServiceRequestService {
    /** A repeated tap within this window returns the existing open request instead of creating another. */
    static final Duration DEDUPE_WINDOW = Duration.ofMinutes(3);
    /** Guests see their table's requests from the last few hours. */
    private static final Duration GUEST_HISTORY = Duration.ofHours(4);

    private final ServiceRequestRepository repository;
    private final RestaurantTableRepository tableRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ServiceRequestService(ServiceRequestRepository repository, RestaurantTableRepository tableRepository,
                                 SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.tableRepository = tableRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public RequestView createFromTable(String tableToken, CreateRequest request) {
        RestaurantTableEntity table = requireTable(tableToken);
        Instant since = Instant.now().minus(DEDUPE_WINDOW);
        var recentOpen = repository.findByTable_IdAndCreatedAtAfterOrderByCreatedAtDesc(table.getId(), since).stream()
                .filter(r -> r.getType() == request.type() && r.getStatus() != ServiceRequestEntity.Status.RESOLVED)
                .findFirst();
        if (recentOpen.isPresent()) {
            return toView(recentOpen.get());
        }
        ServiceRequestEntity entity = new ServiceRequestEntity();
        entity.setTable(table);
        entity.setBranchId(table.getBranch() == null ? null : table.getBranch().getId());
        entity.setType(request.type());
        entity.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        RequestView view = toView(repository.save(entity));
        publish(view, tableToken);
        return view;
    }

    @Transactional(readOnly = true)
    public List<RequestView> listForTable(String tableToken) {
        RestaurantTableEntity table = requireTable(tableToken);
        return repository.findByTable_IdAndCreatedAtAfterOrderByCreatedAtDesc(table.getId(), Instant.now().minus(GUEST_HISTORY))
                .stream().map(ServiceRequestService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestView> listForStaff(boolean includeResolved, UserPrincipal principal) {
        EnumSet<ServiceRequestEntity.Status> statuses = includeResolved
                ? EnumSet.allOf(ServiceRequestEntity.Status.class)
                : EnumSet.of(ServiceRequestEntity.Status.OPEN, ServiceRequestEntity.Status.ACKNOWLEDGED);
        return repository.findForStaff(statuses, scopedBranch(principal)).stream()
                .filter(r -> includeResolved || r.getStatus() != ServiceRequestEntity.Status.RESOLVED)
                .map(ServiceRequestService::toView).toList();
    }

    @Transactional
    public RequestView updateStatus(UUID id, ServiceRequestEntity.Status status, UserPrincipal principal) {
        ServiceRequestEntity entity = repository.findById(id).orElseThrow(() -> new ApiException(404, "Request not found"));
        UUID branch = scopedBranch(principal);
        if (branch != null && entity.getBranchId() != null && !branch.equals(entity.getBranchId())) {
            throw new ApiException(403, "Forbidden");
        }
        if (status == ServiceRequestEntity.Status.OPEN) {
            throw new ApiException(400, "A request can only be acknowledged or resolved");
        }
        if (entity.getStatus() == ServiceRequestEntity.Status.RESOLVED) {
            return toView(entity);
        }
        Instant now = Instant.now();
        if (entity.getAcknowledgedAt() == null) {
            entity.setAcknowledgedAt(now);
            entity.setAcknowledgedBy(principal.userId());
        }
        if (status == ServiceRequestEntity.Status.RESOLVED) {
            entity.setResolvedAt(now);
            entity.setResolvedBy(principal.userId());
        }
        entity.setStatus(status);
        RequestView view = toView(repository.save(entity));
        publish(view, entity.getTable().getQrToken());
        return view;
    }

    private RestaurantTableEntity requireTable(String tableToken) {
        return tableRepository.findByQrToken(tableToken).orElseThrow(() -> new ApiException(404, "Table not found"));
    }

    private static UUID scopedBranch(UserPrincipal principal) {
        boolean scoped = (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER) && principal.branchId() != null;
        return scoped ? principal.branchId() : null;
    }

    private void publish(RequestView view, String tableToken) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "SERVICE_REQUEST_" + view.status());
        payload.put("id", view.id().toString());
        payload.put("tableNumber", view.tableNumber());
        payload.put("type", view.type().name());
        payload.put("status", view.status().name());
        payload.put("createdAt", view.createdAt().toString());
        messagingTemplate.convertAndSend("/topic/service-requests", payload);
        if (tableToken != null) {
            messagingTemplate.convertAndSend("/topic/public.tables." + tableToken + ".requests", payload);
        }
    }

    private static RequestView toView(ServiceRequestEntity e) {
        return new RequestView(e.getId(), e.getTable().getId(), e.getTable().getNumber(), e.getBranchId(), e.getType(),
                e.getNote(), e.getStatus(), e.getCreatedAt(), e.getAcknowledgedAt(), e.getResolvedAt());
    }

    public record CreateRequest(@NotNull ServiceRequestEntity.Type type, @Size(max = 280) String note) {}

    public record StatusRequest(@NotNull ServiceRequestEntity.Status status) {}

    public record RequestView(UUID id, UUID tableId, String tableNumber, UUID branchId, ServiceRequestEntity.Type type,
                              String note, ServiceRequestEntity.Status status, Instant createdAt,
                              Instant acknowledgedAt, Instant resolvedAt) {}
}
