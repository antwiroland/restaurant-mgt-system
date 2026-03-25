package com.restaurantmanager.core.shift;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchService;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.shift.dto.ShiftCloseRequest;
import com.restaurantmanager.core.shift.dto.ShiftOpenRequest;
import com.restaurantmanager.core.shift.dto.ShiftResponse;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShiftService {
    private final CashierShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BranchService branchService;

    public ShiftService(CashierShiftRepository shiftRepository,
                        UserRepository userRepository,
                        PaymentRepository paymentRepository,
                        BranchService branchService) {
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.branchService = branchService;
    }

    @Transactional
    public ShiftResponse openShift(UserPrincipal principal, ShiftOpenRequest request) {
        UserEntity cashier = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(401, "User not found"));
        if (cashier.getRole() != Role.CASHIER && cashier.getRole() != Role.MANAGER && cashier.getRole() != Role.ADMIN) {
            throw new ApiException(403, "Only cashier/manager/admin can open shifts");
        }

        if (shiftRepository.findFirstByCashierIdAndStatusOrderByOpenedAtDesc(cashier.getId(), ShiftStatus.OPEN).isPresent()) {
            throw new ApiException(409, "Shift already open for this cashier");
        }

        CashierShiftEntity shift = new CashierShiftEntity();
        shift.setCashier(cashier);
        shift.setStatus(ShiftStatus.OPEN);
        shift.setOpeningCash(request.openingCash() == null ? BigDecimal.ZERO : request.openingCash());
        shift.setNotes(blankToNull(request.notes()));

        BranchEntity branch = request.branchId() == null ? cashier.getBranch() : branchService.require(request.branchId());
        shift.setBranch(branch);

        return toResponse(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftResponse closeShift(UserPrincipal principal, UUID shiftId, ShiftCloseRequest request) {
        CashierShiftEntity shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ApiException(404, "Shift not found"));
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new ApiException(409, "Shift is already closed");
        }

        boolean ownShift = shift.getCashier().getId().equals(principal.userId());
        if (!ownShift && principal.role() != Role.MANAGER && principal.role() != Role.ADMIN) {
            throw new ApiException(403, "Cannot close another cashier's shift");
        }

        BigDecimal expectedCash = computeExpectedCash(shift);
        BigDecimal closingCash = request.closingCash() == null ? BigDecimal.ZERO : request.closingCash();
        shift.setExpectedCash(expectedCash);
        shift.setClosingCash(closingCash);
        shift.setVariance(closingCash.subtract(expectedCash));
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setClosedAt(Instant.now());
        shift.setNotes(blankToNull(request.notes()));
        return toResponse(shiftRepository.save(shift));
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> activeShifts() {
        return shiftRepository.findByStatusOrderByOpenedAtDesc(ShiftStatus.OPEN).stream()
                .map(this::toResponse)
                .toList();
    }

    private BigDecimal computeExpectedCash(CashierShiftEntity shift) {
        Instant end = Instant.now();
        Instant start = shift.getOpenedAt();
        BigDecimal cashSales = paymentRepository.findByMethodAndStatusAndPaidAtBetween(
                        PaymentMethod.CASH, PaymentStatus.SUCCESS, start, end).stream()
                .filter(payment -> payment.getOrder().getCreatedByUserId() != null
                        && payment.getOrder().getCreatedByUserId().equals(shift.getCashier().getId()))
                .filter(payment -> shift.getBranch() == null
                        || (payment.getOrder().getBranch() != null
                        && payment.getOrder().getBranch().getId().equals(shift.getBranch().getId())))
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return shift.getOpeningCash().add(cashSales);
    }

    private ShiftResponse toResponse(CashierShiftEntity shift) {
        return new ShiftResponse(
                shift.getId(),
                shift.getCashier().getId(),
                shift.getCashier().getName(),
                shift.getBranch() == null ? null : shift.getBranch().getId(),
                shift.getBranch() == null ? null : shift.getBranch().getName(),
                shift.getStatus(),
                shift.getOpeningCash(),
                shift.getClosingCash(),
                shift.getExpectedCash(),
                shift.getVariance(),
                shift.getNotes(),
                shift.getOpenedAt(),
                shift.getClosedAt()
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
