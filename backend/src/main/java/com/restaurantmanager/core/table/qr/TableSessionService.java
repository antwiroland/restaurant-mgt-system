package com.restaurantmanager.core.table.qr;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tracks which phones/browsers are ordering at a table via its QR code. Sessions are persisted so staff
 * see them across restarts and instances, and are closed when the table is settled or reversed.
 */
@Service
public class TableSessionService {
    private final TableMobileSessionRepository sessionRepository;
    private final RestaurantTableRepository tableRepository;

    public TableSessionService(TableMobileSessionRepository sessionRepository, RestaurantTableRepository tableRepository) {
        this.sessionRepository = sessionRepository;
        this.tableRepository = tableRepository;
    }

    @Transactional
    public TableSessionView link(String tableToken, String tableNumber, String sessionId, UserPrincipal principal) {
        RestaurantTableEntity table = resolve(tableToken, tableNumber);
        TableMobileSessionEntity session = sessionRepository
                .findFirstByTable_IdAndSessionIdAndClosedAtIsNull(table.getId(), sessionId.trim())
                .orElseGet(() -> {
                    TableMobileSessionEntity created = new TableMobileSessionEntity();
                    created.setTable(table);
                    created.setSessionId(sessionId.trim());
                    return created;
                });
        session.setLastSeenAt(Instant.now());
        if (principal != null && principal.role() == Role.CUSTOMER) {
            session.setCustomerUserId(principal.userId());
        }
        sessionRepository.save(session);
        return view(table);
    }

    @Transactional(readOnly = true)
    public List<TableSessionView> allActive() {
        return sessionRepository.countOpenByTable().stream()
                .map(row -> new TableSessionView(row.getTableId(), row.getTableNumber(), true,
                        (int) row.getSessionCount(), row.getLastSeenAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TableSessionView activeForTableNumber(String tableNumber) {
        return view(resolve(null, tableNumber));
    }

    @Transactional
    public void closeForTableNumber(String tableNumber) {
        closeAll(resolve(null, tableNumber).getId());
    }

    @Transactional
    public int closeAll(UUID tableId) {
        return sessionRepository.closeAllForTable(tableId, Instant.now());
    }

    private TableSessionView view(RestaurantTableEntity table) {
        List<TableMobileSessionEntity> open = sessionRepository.findByTable_IdAndClosedAtIsNullOrderByLinkedAtAsc(table.getId());
        Instant lastSeen = open.stream().map(TableMobileSessionEntity::getLastSeenAt).max(Instant::compareTo).orElse(null);
        return new TableSessionView(table.getId(), table.getNumber(), !open.isEmpty(), open.size(), lastSeen);
    }

    private RestaurantTableEntity resolve(String tableToken, String tableNumber) {
        if (tableToken != null && !tableToken.isBlank()) {
            return tableRepository.findByQrToken(tableToken.trim())
                    .orElseThrow(() -> new ApiException(404, "Table token not found"));
        }
        if (tableNumber != null && !tableNumber.isBlank()) {
            return tableRepository.findByNumber(tableNumber.trim())
                    .orElseThrow(() -> new ApiException(404, "Table not found"));
        }
        throw new ApiException(400, "tableToken or tableNumber is required");
    }

    public record TableSessionView(UUID tableId, String tableNumber, boolean active, int sessionCount, Instant lastSeenAt) {}
}
