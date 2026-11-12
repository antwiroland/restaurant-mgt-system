package com.restaurantmanager.core.hr.tips;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TipPoolRepository extends JpaRepository<TipPoolEntity, UUID> {
    @Query("select p from TipPoolEntity p where p.branchId = :branchId and p.source = :source and p.status <> :cancelled "
            + "and p.periodStart <= :to and p.periodEnd >= :from")
    List<TipPoolEntity> findOverlapping(@Param("branchId") UUID branchId, @Param("source") TipSource source, @Param("from") LocalDate from,
                                        @Param("to") LocalDate to, @Param("cancelled") TipPoolStatus cancelled);

    List<TipPoolEntity> findByBranchIdInOrderByPeriodStartDescCreatedAtDesc(Collection<UUID> branchIds);

    List<TipPoolEntity> findAllByOrderByPeriodStartDescCreatedAtDesc();

    /** Approved pools whose period ends inside [from, to] (payroll picks their shares up). */
    @Query("select p from TipPoolEntity p where p.status in :statuses and p.periodEnd >= :from and p.periodEnd <= :to")
    List<TipPoolEntity> findEndingBetween(@Param("statuses") Collection<TipPoolStatus> statuses, @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);
}
