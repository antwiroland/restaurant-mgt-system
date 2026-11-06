package com.restaurantmanager.core.financial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationSignOffRepository extends JpaRepository<ReconciliationSignOffEntity, UUID> {
    @Query("""
            select s from ReconciliationSignOffEntity s
            where s.businessDate = :date
              and ((:branchId is null and s.branchId is null) or s.branchId = :branchId)
            """)
    Optional<ReconciliationSignOffEntity> findForDateAndBranch(@Param("date") LocalDate date, @Param("branchId") UUID branchId);

    List<ReconciliationSignOffEntity> findByBusinessDateBetweenOrderByBusinessDateDesc(LocalDate from, LocalDate to);
}
