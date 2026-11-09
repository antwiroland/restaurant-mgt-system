package com.restaurantmanager.core.hr.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<EmployeeEntity, UUID>, JpaSpecificationExecutor<EmployeeEntity> {
    /** Directory listing: to-one associations are fetched in the same query so paging stays a single round trip. */
    @Override
    @EntityGraph(attributePaths = {"department", "position", "primaryBranch"})
    Page<EmployeeEntity> findAll(Specification<EmployeeEntity> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "position", "primaryBranch", "reportingManager"})
    @Query("select e from EmployeeEntity e where e.id = :id")
    Optional<EmployeeEntity> findDetailedById(@Param("id") UUID id);

    Optional<EmployeeEntity> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    boolean existsByEmployeeNumberIgnoreCase(String employeeNumber);

    boolean existsByDepartmentId(UUID departmentId);

    boolean existsByPositionId(UUID positionId);

    @Query("select e.userId from EmployeeEntity e where e.userId in :userIds")
    Set<UUID> findLinkedUserIds(@Param("userIds") Collection<UUID> userIds);

    @Query("select e.userId from EmployeeEntity e where e.userId is not null")
    Set<UUID> findAllLinkedUserIds();

    List<EmployeeEntity> findByIdIn(Collection<UUID> ids);

    List<EmployeeEntity> findByPrimaryBranchId(UUID branchId);

    /** Rows of [primaryBranchId (nullable), employmentStatus, count] for the branch workforce summary. */
    @Query("select e.primaryBranch.id, e.employmentStatus, count(e) from EmployeeEntity e left join e.primaryBranch "
            + "group by e.primaryBranch.id, e.employmentStatus")
    List<Object[]> countByPrimaryBranchAndStatus();
}
