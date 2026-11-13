package com.restaurantmanager.core.hr.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface RecurringInstallmentRepository extends JpaRepository<RecurringInstallmentEntity, UUID> {
    @Modifying
    @Query("delete from RecurringInstallmentEntity i where i.runId = :runId")
    int deleteByRunId(@Param("runId") UUID runId);

    /** Repaid so far in runs other than {@code excludeRun}. */
    @Query("select coalesce(sum(i.amount), 0) from RecurringInstallmentEntity i where i.deductionId = :deductionId and i.runId <> :excludeRun")
    BigDecimal repaidExcept(@Param("deductionId") UUID deductionId, @Param("excludeRun") UUID excludeRun);

    @Query("select coalesce(sum(i.amount), 0) from RecurringInstallmentEntity i where i.deductionId = :deductionId")
    BigDecimal repaid(@Param("deductionId") UUID deductionId);
}
