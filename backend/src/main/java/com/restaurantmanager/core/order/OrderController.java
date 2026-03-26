package com.restaurantmanager.core.order;

import com.restaurantmanager.core.order.dto.GroupAddItemsRequest;
import com.restaurantmanager.core.order.dto.GroupCreateSessionRequest;
import com.restaurantmanager.core.order.dto.GroupFinalizeRequest;
import com.restaurantmanager.core.order.dto.GroupJoinRequest;
import com.restaurantmanager.core.order.dto.GroupJoinResponse;
import com.restaurantmanager.core.order.dto.GroupSessionResponse;
import com.restaurantmanager.core.order.dto.GroupViewResponse;
import com.restaurantmanager.core.order.dto.OrderCancelRequest;
import com.restaurantmanager.core.order.dto.OrderCreateRequest;
import com.restaurantmanager.core.order.dto.OrderResponse;
import com.restaurantmanager.core.order.dto.OrderStatusUpdateRequest;
import com.restaurantmanager.core.order.dto.PublicOrderTrackingResponse;
import com.restaurantmanager.core.order.dto.PublicTableOrderCreateRequest;
import com.restaurantmanager.core.order.dto.TableBillResponse;
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
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public OrderResponse createOrder(@Valid @RequestBody OrderCreateRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.createOrder(request, principal);
    }

    @PostMapping("/public/dine-in")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createPublicDineInOrder(@Valid @RequestBody PublicTableOrderCreateRequest request) {
        return orderService.createPublicDineInOrder(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public ResponseEntity<List<OrderResponse>> listOrders(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                          @RequestParam(required = false) OrderStatus status,
                                                          @RequestParam(required = false) OrderType type,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        List<OrderResponse> all = orderService.listOrders(principal, from, to, status, type);
        Pagination.Params params = Pagination.from(page, size);
        List<OrderResponse> data = Pagination.slice(all, params);
        return ResponseEntity.ok()
                .headers(Pagination.headers(all.size(), params))
                .body(data);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public OrderResponse getOrder(@PathVariable UUID id,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.getOrder(id, principal);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public OrderResponse updateStatus(@PathVariable UUID id,
                                      @Valid @RequestBody OrderStatusUpdateRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.updateStatus(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UserPrincipal principal,
                                                     @RequestBody(required = false) OrderCancelRequest request) {
        OrderService.CancelResult result = orderService.cancelOrder(id, principal, request);
        if (result.returnBody()) {
            return ResponseEntity.ok(result.order());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pickup/{pickupCode}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public OrderResponse findByPickupCode(@PathVariable String pickupCode) {
        return orderService.findByPickupCode(pickupCode);
    }

    @PostMapping("/dine-in/tables/{tableId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public void closeTable(@PathVariable UUID tableId,
                           @AuthenticationPrincipal UserPrincipal principal) {
        orderService.closeTable(tableId, principal);
    }

    @PostMapping("/dine-in/tables/{tableId}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void reverseTable(@PathVariable UUID tableId,
                             @AuthenticationPrincipal UserPrincipal principal) {
        orderService.reverseTable(tableId, principal);
    }

    @GetMapping("/dine-in/tables/{tableId}/bill")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public TableBillResponse tableBill(@PathVariable UUID tableId,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.tableBillByTableId(tableId, principal);
    }

    @GetMapping("/public/dine-in/tables/{tableToken}/bill")
    public TableBillResponse publicTableBill(@PathVariable String tableToken) {
        return orderService.tableBillByTableToken(tableToken);
    }

    @GetMapping("/public/dine-in/tables/{tableToken}/tracking")
    public List<PublicOrderTrackingResponse> publicTableTracking(@PathVariable String tableToken) {
        return orderService.publicTableTracking(tableToken);
    }

    @PostMapping("/group/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public GroupSessionResponse createGroupSession(@RequestBody(required = false) GroupCreateSessionRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.createGroupSession(request, principal);
    }

    @PostMapping("/group/sessions/{code}/join")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public GroupJoinResponse joinGroupSession(@PathVariable String code,
                                              @RequestBody(required = false) GroupJoinRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.joinGroupSession(code, request, principal);
    }

    @PostMapping("/group/sessions/{code}/items")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public GroupViewResponse addGroupItems(@PathVariable String code,
                                           @Valid @RequestBody GroupAddItemsRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.addGroupItems(code, request, principal);
    }

    @GetMapping("/group/sessions/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public GroupViewResponse viewGroupSession(@PathVariable String code) {
        return orderService.viewGroupSession(code);
    }

    @PostMapping("/group/sessions/{code}/finalize")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public OrderResponse finalizeGroupSession(@PathVariable String code,
                                              @Valid @RequestBody GroupFinalizeRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return orderService.finalizeGroupOrder(code, request, principal);
    }
}
