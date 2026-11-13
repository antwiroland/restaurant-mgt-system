package com.restaurantmanager.core.shift;

import com.restaurantmanager.core.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Finds the drawer (open cash shift) that cash goes into or comes out of. Cash is always the responsibility of the
 * staff member who physically takes or hands back the money, so it belongs to their open shift.
 */
@Component
public class CashDrawer {
    private final CashierShiftRepository shiftRepository;
    private final boolean requireOpenShift;

    public CashDrawer(CashierShiftRepository shiftRepository,
                      @Value("${app.shifts.require-open-shift-for-cash:true}") boolean requireOpenShift) {
        this.shiftRepository = shiftRepository;
        this.requireOpenShift = requireOpenShift;
    }

    /**
     * The open shift of this staff member, for cash they are about to take or hand back. Without one it is refused
     * (unless app.shifts.require-open-shift-for-cash is false, when the cash is not tied to any shift).
     */
    public UUID shiftForCash(UUID staffUserId) {
        UUID shiftId = shiftRepository.findFirstByCashierIdAndStatusOrderByOpenedAtDesc(staffUserId, ShiftStatus.OPEN)
                .map(CashierShiftEntity::getId)
                .orElse(null);
        if (shiftId == null && requireOpenShift) {
            throw new ApiException(409, "Open a cash shift before handling cash (Shifts page)");
        }
        return shiftId;
    }
}
