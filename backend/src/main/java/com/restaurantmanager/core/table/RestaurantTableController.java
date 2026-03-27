package com.restaurantmanager.core.table;

import com.restaurantmanager.core.table.dto.TablePublicStatusView;
import com.restaurantmanager.core.table.dto.TableQrResponse;
import com.restaurantmanager.core.table.dto.TableRequest;
import com.restaurantmanager.core.table.dto.TableResponse;
import com.restaurantmanager.core.table.dto.TableScanRequest;
import com.restaurantmanager.core.table.dto.TableScanResponse;
import com.restaurantmanager.core.table.dto.TableStatusUpdateRequest;
import com.restaurantmanager.core.common.Pagination;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.restaurantmanager.core.security.UserPrincipal;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tables")
public class RestaurantTableController {
    private final RestaurantTableService tableService;

    public RestaurantTableController(RestaurantTableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/public")
    public List<TablePublicStatusView> listPublic() {
        return tableService.listPublicTables();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ResponseEntity<List<TableResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        List<TableResponse> all = tableService.listTables(principal);
        Pagination.Params params = Pagination.from(page, size);
        List<TableResponse> data = Pagination.slice(all, params);
        return ResponseEntity.ok()
                .headers(Pagination.headers(all.size(), params))
                .body(data);
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
                                      @Valid @RequestBody TableStatusUpdateRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return tableService.updateStatus(id, request, principal);
    }

    @GetMapping("/{id}/qr")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public TableQrResponse qr(@PathVariable UUID id,
                              @AuthenticationPrincipal UserPrincipal principal) {
        return tableService.qr(id, principal);
    }

    @GetMapping(value = "/{id}/qr-image", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public byte[] qrImage(@PathVariable UUID id,
                          @RequestParam(required = false) String payload,
                          @RequestParam(defaultValue = "240") @Min(120) @Max(1024) int sizePx,
                          @AuthenticationPrincipal UserPrincipal principal) {
        return tableService.qrImage(id, payload, sizePx, principal);
    }

    @PostMapping("/scan")
    public TableScanResponse scan(@Valid @RequestBody TableScanRequest request) {
        return tableService.scan(request);
    }
}
