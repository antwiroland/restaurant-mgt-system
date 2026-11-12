package com.restaurantmanager.core.hr.recruitment;

/**
 * Hiring pipeline. Applications move forward (stages may be skipped), can be rejected or withdrawn from any open stage,
 * and a rejected or withdrawn application can be reconsidered (back to screening). HIRED is reached only through hiring.
 */
public enum ApplicationStage {
    APPLIED,
    SCREENING,
    INTERVIEW,
    TRIAL_SHIFT,
    OFFER,
    HIRED,
    REJECTED,
    WITHDRAWN;

    public boolean isClosed() {
        return this == HIRED || this == REJECTED || this == WITHDRAWN;
    }

    /** Why a move from this stage to {@code to} is not allowed, or null when it is. */
    public String refusal(ApplicationStage to) {
        if (to == this) {
            return "The application is already at this stage";
        }
        if (to == HIRED) {
            return "Use Hire to complete a hire";
        }
        if (this == HIRED) {
            return "This candidate has been hired";
        }
        if (this == REJECTED || this == WITHDRAWN) {
            return to == SCREENING ? null : "Reconsider the application (back to screening) first";
        }
        if (to == REJECTED || to == WITHDRAWN) {
            return null;
        }
        return to.ordinal() > ordinal() ? null : "Applications only move forward; reject or withdraw instead";
    }
}
