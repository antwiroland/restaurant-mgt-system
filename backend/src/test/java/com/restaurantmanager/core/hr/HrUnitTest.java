package com.restaurantmanager.core.hr;

import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.document.DocumentFileType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HrUnitTest {
    @Test
    void roleDefaults_adminHasEverything_managerHasNoCompensationOrPayroll_othersNothing() {
        assertThat(HrAccessService.defaultsFor(Role.ADMIN)).containsExactlyInAnyOrder(HrPermission.values());
        assertThat(HrAccessService.defaultsFor(Role.MANAGER))
                .contains(HrPermission.EMPLOYEE_VIEW, HrPermission.SCHEDULE_MANAGE, HrPermission.LEAVE_APPROVE)
                .doesNotContain(HrPermission.COMPENSATION_VIEW, HrPermission.PAYROLL_PROCESS, HrPermission.PAYROLL_APPROVE,
                        HrPermission.BRANCH_ALL, HrPermission.EMPLOYEE_ARCHIVE, HrPermission.EMPLOYEE_TRANSFER, HrPermission.SETTINGS_MANAGE);
        assertThat(HrAccessService.defaultsFor(Role.CASHIER)).isEmpty();
        assertThat(HrAccessService.defaultsFor(Role.RIDER)).isEmpty();
        assertThat(HrAccessService.defaultsFor(Role.CUSTOMER)).isEmpty();
    }

    @Test
    void branchScope_centralSeesAll_scopedSeesOwn_unassignedSeesNothing() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var central = new HrAccessService.HrAccess(UUID.randomUUID(), Role.ADMIN, Set.of(), true, Set.of());
        var scoped = new HrAccessService.HrAccess(UUID.randomUUID(), Role.MANAGER, Set.of(), false, Set.of(a));
        var unassigned = new HrAccessService.HrAccess(UUID.randomUUID(), Role.MANAGER, Set.of(), false, Set.of());

        assertThat(central.canSeeBranch(b)).isTrue();
        assertThat(central.canSeeBranch(null)).isTrue();
        assertThat(central.branchFilter()).isNull();
        assertThat(scoped.canSeeBranch(a)).isTrue();
        assertThat(scoped.canSeeBranch(b)).isFalse();
        assertThat(scoped.canSeeBranch(null)).isFalse();
        assertThat(unassigned.canSeeBranch(a)).isFalse();
        assertThat(unassigned.branchFilter()).isEmpty();
        assertThatThrownBy(() -> scoped.assertBranch(b)).hasMessageContaining("branch");
    }

    @Test
    void permissionCodes_areUniqueAndResolvable() {
        assertThat(Set.of(HrPermission.values()).stream().map(HrPermission::code).distinct().count()).isEqualTo(HrPermission.values().length);
        assertThat(HrPermission.fromCode("hr.payroll.approve")).contains(HrPermission.PAYROLL_APPROVE);
        assertThat(HrPermission.fromCode("PAYROLL_APPROVE")).contains(HrPermission.PAYROLL_APPROVE);
        assertThat(HrPermission.fromCode("hr.unknown")).isEmpty();
    }

    @Test
    void documentTypeDetection_usesFileSignatures() {
        assertThat(DocumentFileType.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), "a.exe")).contains(DocumentFileType.PDF);
        assertThat(DocumentFileType.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10}, "x")).contains(DocumentFileType.PNG);
        assertThat(DocumentFileType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, "x")).contains(DocumentFileType.JPEG);
        byte[] zip = {'P', 'K', 3, 4, 0};
        assertThat(DocumentFileType.detect(zip, "offer.DOCX")).contains(DocumentFileType.DOCX);
        assertThat(DocumentFileType.detect(zip, "payload.zip")).isEmpty();
        assertThat(DocumentFileType.detect(new byte[]{'M', 'Z', 0, 0}, "cv.pdf")).isEmpty();
        assertThat(DocumentFileType.detect(new byte[]{1, 2}, "x.pdf")).isEmpty();
    }
}
