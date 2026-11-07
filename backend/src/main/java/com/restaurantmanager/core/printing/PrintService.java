package com.restaurantmanager.core.printing;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.order.OrderService;
import com.restaurantmanager.core.order.dto.OrderResponse;
import com.restaurantmanager.core.payment.PaymentService;
import com.restaurantmanager.core.payment.dto.ReceiptResponse;
import com.restaurantmanager.core.profile.RestaurantProfileService;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PrintService {
    private static final Logger log = LoggerFactory.getLogger(PrintService.class);

    private final PrinterRepository repository;
    private final PrinterTransport transport;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final RestaurantProfileService profileService;
    private final ZoneId zone;

    public PrintService(PrinterRepository repository, PrinterTransport transport, OrderService orderService,
                        PaymentService paymentService, RestaurantProfileService profileService,
                        @Value("${app.timezone:Africa/Accra}") String timezone) {
        this.repository = repository;
        this.transport = transport;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.profileService = profileService;
        this.zone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public List<PrinterView> list(UserPrincipal principal) {
        UUID scoped = scopedBranch(principal);
        return repository.findAllByOrderByNameAsc().stream()
                .filter(p -> scoped == null || p.getBranchId() == null || scoped.equals(p.getBranchId()))
                .map(PrintService::toView).toList();
    }

    @Transactional
    public PrinterView save(UUID id, PrinterRequest request, UserPrincipal principal) {
        PrinterEntity printer = id == null ? new PrinterEntity()
                : repository.findById(id).orElseThrow(() -> new ApiException(404, "Printer not found"));
        UUID scoped = scopedBranch(principal);
        if (scoped != null && request.branchId() != null && !scoped.equals(request.branchId())) {
            throw new ApiException(403, "Forbidden");
        }
        printer.setName(request.name().trim());
        printer.setKind(request.kind());
        printer.setHost(request.host().trim());
        printer.setPort(request.port() == null ? 9100 : request.port());
        printer.setPaperWidthMm(request.paperWidthMm() == null ? 80 : request.paperWidthMm());
        printer.setBranchId(scoped != null ? scoped : request.branchId());
        printer.setActive(request.active() == null || request.active());
        return toView(repository.save(printer));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(repository.findById(id).orElseThrow(() -> new ApiException(404, "Printer not found")));
    }

    public PrintResult test(UUID id) {
        PrinterEntity printer = repository.findById(id).orElseThrow(() -> new ApiException(404, "Printer not found"));
        return send(printer, EscPosRenderer.testPage(printer.getName(), printer.getPaperWidthMm()));
    }

    /** No surrounding transaction: an unpaid order makes the receipt lookup throw, which must not poison a shared one. */
    public PrintResult printReceipt(UUID orderId, UUID printerId, UserPrincipal principal) {
        OrderResponse order = orderService.getOrder(orderId, principal);
        ReceiptResponse receipt = findReceipt(orderId, principal);
        PrinterEntity printer = choose(PrinterEntity.Kind.RECEIPT, printerId, order.branchId());
        return send(printer, EscPosRenderer.receipt(order, receipt, profileService.get(), printer.getPaperWidthMm(), zone));
    }

    /** No surrounding transaction: an unpaid order makes the receipt lookup throw, which must not poison a shared one. */
    public PrintResult printKitchenTicket(UUID orderId, UUID printerId, UserPrincipal principal) {
        OrderResponse order = orderService.getOrder(orderId, principal);
        PrinterEntity printer = choose(PrinterEntity.Kind.KITCHEN, printerId, order.branchId());
        return send(printer, EscPosRenderer.kitchenTicket(order, printer.getPaperWidthMm(), zone));
    }

    private ReceiptResponse findReceipt(UUID orderId, UserPrincipal principal) {
        try {
            return paymentService.receiptByOrderId(orderId, principal);
        } catch (ApiException notPaidYet) {
            return null; // prints as an unpaid bill
        }
    }

    /** Explicit printer, else the active printer of that kind at the order's branch, else a printer with no branch. */
    private PrinterEntity choose(PrinterEntity.Kind kind, UUID printerId, UUID branchId) {
        if (printerId != null) {
            PrinterEntity printer = repository.findById(printerId).orElseThrow(() -> new ApiException(404, "Printer not found"));
            if (!printer.isActive()) throw new ApiException(409, "Printer is turned off");
            return printer;
        }
        return repository.findByKindAndActiveTrue(kind).stream()
                .filter(p -> p.getBranchId() == null || p.getBranchId().equals(branchId))
                .min(Comparator.comparing((PrinterEntity p) -> p.getBranchId() == null ? 1 : 0).thenComparing(PrinterEntity::getName))
                .orElseThrow(() -> new ApiException(409, "No active " + kind.name().toLowerCase() + " printer is set up"));
    }

    private PrintResult send(PrinterEntity printer, byte[] payload) {
        try {
            transport.send(printer.getHost(), printer.getPort(), payload);
            return new PrintResult(printer.getId(), printer.getName(), true, payload.length, "Sent to " + printer.getName());
        } catch (IOException ex) {
            log.warn("Print to {} ({}:{}) failed: {}", printer.getName(), printer.getHost(), printer.getPort(), ex.getMessage());
            throw new ApiException(502, "Couldn't reach printer " + printer.getName() + " at "
                    + printer.getHost() + ":" + printer.getPort() + " (" + ex.getMessage() + ")");
        }
    }

    private static UUID scopedBranch(UserPrincipal principal) {
        boolean scoped = principal.role() == Role.MANAGER && principal.branchId() != null;
        return scoped ? principal.branchId() : null;
    }

    private static PrinterView toView(PrinterEntity p) {
        return new PrinterView(p.getId(), p.getBranchId(), p.getName(), p.getKind(), p.getHost(), p.getPort(),
                p.getPaperWidthMm(), p.isActive());
    }

    public record PrinterRequest(@NotBlank @Size(max = 80) String name,
                                 @NotNull PrinterEntity.Kind kind,
                                 @NotBlank @Size(max = 255) String host,
                                 @Min(1) @Max(65535) Integer port,
                                 Integer paperWidthMm,
                                 UUID branchId,
                                 Boolean active) {}

    public record PrinterView(UUID id, UUID branchId, String name, PrinterEntity.Kind kind, String host, int port,
                              int paperWidthMm, boolean active) {}

    public record PrintResult(UUID printerId, String printerName, boolean sent, int bytes, String message) {}
}
