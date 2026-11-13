package com.restaurantmanager.core.hr.schedule;

import com.restaurantmanager.core.notification.push.PushNotificationService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Schedule notifications over the existing push channel. Recipients are de-duplicated, employees without a login are
 * skipped, and delivery happens after the transaction commits (handled by {@link PushNotificationService}).
 */
@Component
public class ScheduleNotifier {
    private final PushNotificationService push;

    public ScheduleNotifier(PushNotificationService push) {
        this.push = push;
    }

    public void schedulePublished(Collection<UUID> userIds, String period) {
        send(userIds, "Your schedule is out", "Shifts for " + period + " have been published. Open the app to see them.", "SCHEDULE_PUBLISHED");
    }

    public void shiftChanged(Collection<UUID> userIds, String when) {
        send(userIds, "Shift changed", "Your shift on " + when + " was updated.", "SHIFT_CHANGED");
    }

    public void shiftCancelled(Collection<UUID> userIds, String when) {
        send(userIds, "Shift cancelled", "Your shift on " + when + " was cancelled.", "SHIFT_CANCELLED");
    }

    public void swapRequested(Collection<UUID> approverUserIds, String who) {
        send(approverUserIds, "Shift swap request", who + " asked to swap a shift.", "SHIFT_SWAP_REQUESTED");
    }

    public void swapDecided(Collection<UUID> userIds, boolean approved, String when) {
        send(userIds, approved ? "Shift swap approved" : "Shift swap declined",
                "The swap for " + when + (approved ? " was approved. Check your schedule." : " was declined."), "SHIFT_SWAP_DECIDED");
    }

    /** {@code why}: "has left", "is on leave"... */
    public void shiftsReleased(java.util.Collection<UUID> managerUserIds, String who, int count, String why) {
        send(managerUserIds, "Shifts need cover", who + " " + why + "; " + count + " upcoming shift" + (count == 1 ? " is" : "s are")
                + " now open.", "SHIFTS_RELEASED");
    }

    public void openShiftClaimed(Collection<UUID> managerUserIds, String who, String when) {
        send(managerUserIds, "Open shift taken", who + " took the open shift on " + when + ".", "OPEN_SHIFT_CLAIMED");
    }

    private void send(Collection<UUID> userIds, String title, String body, String type) {
        List<UUID> recipients = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!recipients.isEmpty()) {
            push.notifyUsers(recipients, title, body, Map.of("type", type));
        }
    }
}
