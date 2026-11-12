package com.restaurantmanager.core.hr.recruitment;

import org.junit.jupiter.api.Test;

import static com.restaurantmanager.core.hr.recruitment.ApplicationStage.APPLIED;
import static com.restaurantmanager.core.hr.recruitment.ApplicationStage.HIRED;
import static com.restaurantmanager.core.hr.recruitment.ApplicationStage.INTERVIEW;
import static com.restaurantmanager.core.hr.recruitment.ApplicationStage.OFFER;
import static com.restaurantmanager.core.hr.recruitment.ApplicationStage.REJECTED;
import static com.restaurantmanager.core.hr.recruitment.ApplicationStage.SCREENING;
import static com.restaurantmanager.core.hr.recruitment.ApplicationStage.TRIAL_SHIFT;
import static com.restaurantmanager.core.hr.recruitment.ApplicationStage.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStageTest {
    @Test
    void forwardMovesAndSkipsAllowed_backwardsAndDirectHireNot() {
        assertThat(APPLIED.refusal(SCREENING)).isNull();
        assertThat(APPLIED.refusal(OFFER)).isNull();
        assertThat(TRIAL_SHIFT.refusal(INTERVIEW)).isNotNull();
        assertThat(OFFER.refusal(HIRED)).isEqualTo("Use Hire to complete a hire");
        assertThat(SCREENING.refusal(SCREENING)).isNotNull();
    }

    @Test
    void closingFromAnyOpenStage_andReconsiderOnlyToScreening() {
        for (ApplicationStage open : new ApplicationStage[]{APPLIED, SCREENING, INTERVIEW, TRIAL_SHIFT, OFFER}) {
            assertThat(open.refusal(REJECTED)).isNull();
            assertThat(open.refusal(WITHDRAWN)).isNull();
            assertThat(open.isClosed()).isFalse();
        }
        assertThat(REJECTED.refusal(SCREENING)).isNull();
        assertThat(WITHDRAWN.refusal(INTERVIEW)).isNotNull();
        assertThat(HIRED.refusal(REJECTED)).isEqualTo("This candidate has been hired");
        assertThat(HIRED.isClosed()).isTrue();
    }
}
