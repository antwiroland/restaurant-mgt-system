package com.restaurantmanager.core.hr.leave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Leave housekeeping: statuses follow approved leave each night; approvers are reminded of waiting requests each morning. */
@Component
public class LeaveDailyJobs {
    private static final Logger log = LoggerFactory.getLogger(LeaveDailyJobs.class);
    private final LeaveService leaveService;

    public LeaveDailyJobs(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @Scheduled(cron = "${hr.leave.status-cron:0 5 0 * * *}", zone = "${app.timezone:Africa/Accra}")
    public void syncStatuses() {
        int changed = leaveService.syncLeaveStatuses();
        if (changed > 0) log.info("Leave: {} employee status(es) updated for today's leave", changed);
    }

    @Scheduled(cron = "${hr.leave.reminder-cron:0 0 8 * * *}", zone = "${app.timezone:Africa/Accra}")
    public void remindApprovers() {
        int sent = leaveService.remindPending();
        if (sent > 0) log.info("Leave: reminded approvers about {} pending request(s)", sent);
    }
}
