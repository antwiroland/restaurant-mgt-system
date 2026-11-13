package com.restaurantmanager.core.shift;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchService;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentRefundEntity;
import com.restaurantmanager.core.payment.PaymentRefundRepository;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.shift.dto.CashMovementRequest;
import com.restaurantmanager.core.shift.dto.CashMovementResponse;
import com.restaurantmanager.core.shift.dto.ShiftCloseRequest;
import com.restaurantmanager.core.shift.dto.ShiftDetailResponse;
import com.restaurantmanager.core.shift.dto.ShiftOpenRequest;
import com.restaurantmanager.core.shift.dto.ShiftResponse;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cash shifts: a staff member's drawer from opening float to closing count.
 *
 * <p>Expected cash = opening float + cash payments they took + pay-ins - cash refunds they handed back - pay-outs.
 * Cash payments and refunds are tied to the open shift of the person who handled the money when they happen
 * (see {@link CashDrawer}), so the count is per drawer, not per order creator.
 */
@Service
public class ShiftService {
    private static final List<PaymentStatus> RECEIVED = List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
    private static final int HISTORY_LIMIT = 200;

    private final CashierShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final CashMovementRepository movementRepository;
    private final BranchService branchService;

    public ShiftService(CashierShiftRepository shiftRepository,
                        UserRepository userRepository,
                        PaymentRepository paymentRepository,
                        PaymentRefundRepository refundRepository,
                        CashMovementRepository movementRepository,
                        BranchService branchService) {
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.movementRepository = movementRepository;
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

        BranchEntity branch;
        if ((principal.role() == Role.MANAGER || principal.role() == Role.CASHIER) && principal.branchId() != null) {
            if (request.branchId() != null && !principal.branchId().equals(request.branchId())) {
                throw new ApiException(403, "Forbidden");
            }
            branch = branchService.require(principal.branchId());
        } else {
            branch = request.branchId() == null ? cashier.getBranch() : branchService.require(request.branchId());
        }
        shift.setBranch(branch);

        return toResponse(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftResponse closeShift(UserPrincipal principal, UUID shiftId, ShiftCloseRequest request) {
        try {
            CashierShiftEntity shift = shiftRepository.findById(shiftId)
                    .orElseThrow(() -> new ApiException(404, "Shift not found"));
            if (shift.getStatus() != ShiftStatus.OPEN) {
                throw new ApiException(409, "Shift is already closed");
            }
            assertCanHandle(shift, principal, "Cannot close another cashier's shift");

            BigDecimal expectedCash = breakdown(shift).expected();
            BigDecimal closingCash = request.closingCash() == null ? BigDecimal.ZERO : request.closingCash();
            shift.setExpectedCash(expectedCash);
            shift.setClosingCash(closingCash);
            shift.setVariance(closingCash.subtract(expectedCash));
            shift.setStatus(ShiftStatus.CLOSED);
            shift.setClosedAt(Instant.now());
            String closingNote = blankToNull(request.notes());
            if (closingNote != null) {
                shift.setNotes(shift.getNotes() == null ? closingNote : shift.getNotes() + "\n" + closingNote);
            }
            return toResponse(shiftRepository.saveAndFlush(shift));
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ApiException(409, "Shift was already closed by another request");
        }
    }

    /** Records cash put into (PAY_IN) or taken out of (PAY_OUT) an open shift's drawer. */
    @Transactional
    public ShiftDetailResponse addMovement(UserPrincipal principal, UUID shiftId, CashMovementRequest request) {
        CashierShiftEntity shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ApiException(404, "Shift not found"));
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new ApiException(409, "Cash can only be moved in or out of an open shift");
        }
        assertCanHandle(shift, principal, "Cannot move cash in another cashier's drawer");
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (request.type() == CashMovementType.PAY_OUT && amount.compareTo(breakdown(shift).expected()) > 0) {
            throw new ApiException(409, "There is not that much cash in the drawer");
        }
        CashMovementEntity movement = new CashMovementEntity();
        movement.setShiftId(shift.getId());
        movement.setType(request.type());
        movement.setAmount(amount);
        movement.setReason(request.reason().trim());
        movement.setCreatedBy(principal.userId());
        movementRepository.save(movement);
        return detail(shift);
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> activeShifts(UserPrincipal principal) {
        List<CashierShiftEntity> shifts;
        if (principal.role() == Role.ADMIN) {
            shifts = shiftRepository.findByStatusOrderByOpenedAtDesc(ShiftStatus.OPEN);
        } else if ((principal.role() == Role.MANAGER || principal.role() == Role.CASHIER) && principal.branchId() != null) {
            shifts = shiftRepository.findByStatusAndBranch_IdOrderByOpenedAtDesc(ShiftStatus.OPEN, principal.branchId());
        } else if (principal.role() == Role.CASHIER) {
            shifts = shiftRepository.findByCashier_IdAndStatusOrderByOpenedAtDesc(principal.userId(), ShiftStatus.OPEN);
        } else {
            shifts = shiftRepository.findByStatusOrderByOpenedAtDesc(ShiftStatus.OPEN);
        }
        return shifts.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Shifts opened in [from, to) (default: the last 30 days), newest first, at most 200. Cashiers see their own;
     * managers their branch; admins everything (optionally one branch or one cashier).
     */
    @Transactional(readOnly = true)
    public List<ShiftResponse> history(UserPrincipal principal, Instant from, Instant to, UUID branchId, UUID cashierId) {
        Instant end = to == null ? Instant.now().plus(1, ChronoUnit.DAYS) : to;
        Instant start = from == null ? end.minus(31, ChronoUnit.DAYS) : from;
        if (!start.isBefore(end)) {
            throw new ApiException(400, "from must be before to");
        }
        return shiftRepository.findByOpenedAtGreaterThanEqualAndOpenedAtLessThanOrderByOpenedAtDesc(start, end).stream()
                .filter(shift -> canSee(shift, principal))
                .filter(shift -> branchId == null || (shift.getBranch() != null && shift.getBranch().getId().equals(branchId)))
                .filter(shift -> cashierId == null || shift.getCashier().getId().equals(cashierId))
                .limit(HISTORY_LIMIT)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftDetailResponse detail(UserPrincipal principal, UUID shiftId) {
        CashierShiftEntity shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ApiException(404, "Shift not found"));
        if (!canSee(shift, principal)) {
            throw new ApiException(403, "Forbidden");
        }
        return detail(shift);
    }

    private ShiftDetailResponse detail(CashierShiftEntity shift) {
        Breakdown b = breakdown(shift);
        List<CashMovementEntity> movements = movementRepository.findByShiftIdOrderByCreatedAtAsc(shift.getId());
        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(movements.stream().map(CashMovementEntity::getCreatedBy).distinct().toList())
                .forEach(user -> names.put(user.getId(), user.getName()));
        return new ShiftDetailResponse(
                toResponse(shift, b),
                b.salesCount(), b.sales(), b.refundsCount(), b.refunds(), b.payIns(), b.payOuts(),
                shift.getStatus() == ShiftStatus.CLOSED && shift.getExpectedCash() != null ? shift.getExpectedCash() : b.expected(),
                movements.stream()
                        .map(m -> new CashMovementResponse(m.getId(), m.getType(), m.getAmount(), m.getReason(),
                                m.getCreatedBy(), names.get(m.getCreatedBy()), m.getCreatedAt()))
                        .toList());
    }

    private record Breakdown(int salesCount, BigDecimal sales, int refundsCount, BigDecimal refunds,
                             BigDecimal payIns, BigDecimal payOuts, BigDecimal expected) {
    }

    private Breakdown breakdown(CashierShiftEntity shift) {
        List<PaymentEntity> sales = paymentRepository.findByShiftIdAndMethodAndStatusIn(shift.getId(), PaymentMethod.CASH, RECEIVED);
        List<PaymentRefundEntity> refunds = refundRepository.findByShiftId(shift.getId());
        List<CashMovementEntity> movements = movementRepository.findByShiftIdOrderByCreatedAtAsc(shift.getId());
        BigDecimal salesTotal = sum(sales.stream().map(PaymentEntity::getAmount).toList());
        BigDecimal refundsTotal = sum(refunds.stream().map(PaymentRefundEntity::getAmount).toList());
        BigDecimal payIns = sum(movements.stream().filter(m -> m.getType() == CashMovementType.PAY_IN).map(CashMovementEntity::getAmount).toList());
        BigDecimal payOuts = sum(movements.stream().filter(m -> m.getType() == CashMovementType.PAY_OUT).map(CashMovementEntity::getAmount).toList());
        BigDecimal expected = shift.getOpeningCash().add(salesTotal).subtract(refundsTotal).add(payIns).subtract(payOuts);
        return new Breakdown(sales.size(), salesTotal, refunds.size(), refundsTotal, payIns, payOuts, expected);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** Own shift, or a manager/admin (branch managers only at their branch). */
    private void assertCanHandle(CashierShiftEntity shift, UserPrincipal principal, String message) {
        boolean ownShift = shift.getCashier().getId().equals(principal.userId());
        if (!ownShift && principal.role() != Role.MANAGER && principal.role() != Role.ADMIN) {
            throw new ApiException(403, message);
        }
        if ((principal.role() == Role.MANAGER || principal.role() == Role.CASHIER)
                && principal.branchId() != null
                && (shift.getBranch() == null || !principal.branchId().equals(shift.getBranch().getId()))) {
            throw new ApiException(403, "Forbidden");
        }
    }

    private boolean canSee(CashierShiftEntity shift, UserPrincipal principal) {
        if (principal.role() == Role.ADMIN) {
            return true;
        }
        if (principal.role() == Role.CASHIER) {
            return shift.getCashier().getId().equals(principal.userId());
        }
        if (principal.role() == Role.MANAGER) {
            return principal.branchId() == null
                    || (shift.getBranch() != null && principal.branchId().equals(shift.getBranch().getId()));
        }
        return false;
    }

    private ShiftResponse toResponse(CashierShiftEntity shift) {
        return toResponse(shift, shift.getStatus() == ShiftStatus.OPEN ? breakdown(shift) : null);
    }

    /** For an open shift, expected cash is the live figure so far; for a closed one, as stored at closing. */
    private ShiftResponse toResponse(CashierShiftEntity shift, Breakdown live) {
        BigDecimal expected = shift.getStatus() == ShiftStatus.OPEN && live != null ? live.expected() : shift.getExpectedCash();
        return new ShiftResponse(
                shift.getId(),
                shift.getCashier().getId(),
                shift.getCashier().getName(),
                shift.getBranch() == null ? null : shift.getBranch().getId(),
                shift.getBranch() == null ? null : shift.getBranch().getName(),
                shift.getStatus(),
                shift.getOpeningCash(),
                shift.getClosingCash(),
                expected,
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
