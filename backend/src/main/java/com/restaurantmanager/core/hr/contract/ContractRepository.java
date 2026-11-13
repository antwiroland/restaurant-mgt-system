package com.restaurantmanager.core.hr.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<ContractEntity, UUID> {
    List<ContractEntity> findByEmployeeIdOrderByStartDateDescCreatedAtDesc(UUID employeeId);

    List<ContractEntity> findByEmployeeIdInAndStatus(java.util.Collection<UUID> employeeIds, ContractStatus status);

    Optional<ContractEntity> findFirstByEmployeeIdAndStatus(UUID employeeId, ContractStatus status);

    boolean existsByPreviousContractIdAndStatusIn(UUID previousContractId, List<ContractStatus> statuses);

    @Query("select c from ContractEntity c where c.status = 'ACTIVE' and ((c.endDate is not null and c.endDate <= :until) "
            + "or (c.probationEndDate is not null and c.probationEndDate >= :from and c.probationEndDate <= :until)) "
            + "order by coalesce(c.endDate, c.probationEndDate)")
    List<ContractEntity> findActiveWithDatesDue(@Param("from") LocalDate from, @Param("until") LocalDate until);

    @Query("select c from ContractEntity c where c.status = 'ACTIVE' and c.endDate is not null and c.endDate < :today")
    List<ContractEntity> findActiveEndedBefore(@Param("today") LocalDate today);
}
