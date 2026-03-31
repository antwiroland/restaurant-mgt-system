package com.restaurantmanager.core.table;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.branch.BranchService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.table.dto.TablePublicStatusView;
import com.restaurantmanager.core.table.dto.TableQrResponse;
import com.restaurantmanager.core.table.dto.TableRequest;
import com.restaurantmanager.core.table.dto.TableResponse;
import com.restaurantmanager.core.table.dto.TableScanRequest;
import com.restaurantmanager.core.table.dto.TableScanResponse;
import com.restaurantmanager.core.table.dto.TableStatusUpdateRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RestaurantTableService {
    private final RestaurantTableRepository tableRepository;
    private final TableQrCodeService tableQrCodeService;
    private final BranchService branchService;
    private final TableRealtimePublisher realtimePublisher;

    public RestaurantTableService(RestaurantTableRepository tableRepository,
                                  TableQrCodeService tableQrCodeService,
                                  BranchService branchService,
                                  TableRealtimePublisher realtimePublisher) {
        this.tableRepository = tableRepository;
        this.tableQrCodeService = tableQrCodeService;
        this.branchService = branchService;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional(readOnly = true)
    public List<TablePublicStatusView> listPublicTables() {
        return tableRepository.findAll().stream()
                .sorted((a, b) -> a.getNumber().compareToIgnoreCase(b.getNumber()))
                .map(t -> new TablePublicStatusView(t.getNumber(), t.getCapacity(), t.getZone(), t.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.TABLES, key = "#principal.userId()")
    public List<TableResponse> listTables(UserPrincipal principal) {
        List<RestaurantTableEntity> tables;
        if (isBranchScopedStaff(principal)) {
            UUID branchId = principal.branchId();
            tables = tableRepository.findByBranch_IdOrderByNumberAsc(branchId);
        } else {
            tables = tableRepository.findAllOrdered();
        }
        return tables.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.TABLES, CacheConfig.TABLE_QR, CacheConfig.TABLE_SCAN}, allEntries = true)
    public TableResponse create(TableRequest request) {
        if (tableRepository.existsByNumberIgnoreCase(request.number())) {
            throw new ApiException(409, "Table number already exists");
        }
        RestaurantTableEntity table = new RestaurantTableEntity();
        applyRequest(table, request);
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken(generateUniqueQrToken());
        return toResponse(tableRepository.save(table));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.TABLES, CacheConfig.TABLE_QR, CacheConfig.TABLE_SCAN}, allEntries = true)
    public TableResponse update(UUID id, TableRequest request) {
        RestaurantTableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        String incoming = request.number().trim();
        if (!table.getNumber().equalsIgnoreCase(incoming) && tableRepository.existsByNumberIgnoreCase(incoming)) {
            throw new ApiException(409, "Table number already exists");
        }
        applyRequest(table, request);
        return toResponse(tableRepository.save(table));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.TABLES, CacheConfig.TABLE_QR, CacheConfig.TABLE_SCAN}, allEntries = true)
    public void delete(UUID id) {
        RestaurantTableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        tableRepository.delete(table);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.TABLES, CacheConfig.TABLE_QR, CacheConfig.TABLE_SCAN}, allEntries = true)
    public TableResponse updateStatus(UUID id, TableStatusUpdateRequest request, UserPrincipal principal) {
        RestaurantTableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        assertTableBranchAccess(table, principal);
        table.setStatus(request.status());
        TableResponse saved = toResponse(tableRepository.save(table));
        realtimePublisher.publishTableStatusChanged(table.getNumber(), request.status());
        return saved;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.TABLE_QR, key = "#id + ':' + #principal.userId()")
    public TableQrResponse qr(UUID id, UserPrincipal principal) {
        RestaurantTableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        assertTableBranchAccess(table, principal);
        return new TableQrResponse(table.getId(), table.getNumber(), table.getQrToken(),
                "/tables/scan?token=" + table.getQrToken());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.TABLE_SCAN, key = "#request.qrToken()")
    public TableScanResponse scan(TableScanRequest request) {
        RestaurantTableEntity table = tableRepository.findByQrToken(request.qrToken())
                .orElseThrow(() -> new ApiException(404, "Table token not found"));
        return new TableScanResponse(table.getId(), table.getNumber(), table.getStatus());
    }

    @Transactional(readOnly = true)
    public byte[] qrImage(UUID id, String payload, int sizePx, UserPrincipal principal) {
        RestaurantTableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        assertTableBranchAccess(table, principal);
        String effectivePayload = payload == null || payload.isBlank() ? table.getQrToken() : payload.trim();
        return tableQrCodeService.generatePng(effectivePayload, sizePx);
    }

    private void applyRequest(RestaurantTableEntity table, TableRequest request) {
        table.setNumber(request.number().trim());
        table.setCapacity(request.capacity());
        table.setZone(blankToNull(request.zone()));
        if (request.branchId() != null) {
            table.setBranch(branchService.require(request.branchId()));
        } else {
            table.setBranch(null);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String generateUniqueQrToken() {
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "");
        } while (tableRepository.findByQrToken(token).isPresent());
        return token;
    }

    private TableResponse toResponse(RestaurantTableEntity table) {
        return new TableResponse(
                table.getId(),
                table.getNumber(),
                table.getCapacity(),
                table.getZone(),
                table.getStatus(),
                table.getQrToken(),
                table.getBranch() == null ? null : table.getBranch().getId(),
                table.getBranch() == null ? null : table.getBranch().getName());
    }

    private boolean isBranchScopedStaff(UserPrincipal principal) {
        return principal != null
                && (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER)
                && principal.branchId() != null;
    }

    private void assertTableBranchAccess(RestaurantTableEntity table, UserPrincipal principal) {
        if (!isBranchScopedStaff(principal)) {
            return;
        }
        UUID branchId = principal.branchId();
        if (table.getBranch() == null || !branchId.equals(table.getBranch().getId())) {
            throw new ApiException(403, "Forbidden");
        }
    }
}
