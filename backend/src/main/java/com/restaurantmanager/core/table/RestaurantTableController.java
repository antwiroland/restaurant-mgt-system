package com.restaurantmanager.core.table;

import com.restaurantmanager.core.table.dto.TableQrResponse;
import com.restaurantmanager.core.table.dto.TableRequest;
import com.restaurantmanager.core.table.dto.TableResponse;
import com.restaurantmanager.core.table.dto.TableScanRequest;
import com.restaurantmanager.core.table.dto.TableScanResponse;
import com.restaurantmanager.core.table.dto.TableStatusUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tables")
public class RestaurantTableController {
    private final RestaurantTableService tableService;

    public RestaurantTableController(RestaurantTableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<TableResponse> list() {
        return tableService.listTables();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse create(@Valid @RequestBody TableRequest request) {
        return tableService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TableResponse update(@PathVariable UUID id,
                                @Valid @RequestBody TableRequest request) {
        return tableService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public TableResponse updateStatus(@PathVariable UUID id,
                                      @Valid @RequestBody TableStatusUpdateRequest request) {
        return tableService.updateStatus(id, request);
    }

    @GetMapping("/{id}/qr")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public TableQrResponse qr(@PathVariable UUID id) {
        return tableService.qr(id);
    }

    @PostMapping("/scan")
    public TableScanResponse scan(@Valid @RequestBody TableScanRequest request) {
        return tableService.scan(request);
    }
}
