package com.restaurantmanager.core.hr.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeaveTypeRepository extends JpaRepository<LeaveTypeEntity, UUID> {
    List<LeaveTypeEntity> findAllByOrderByDisplayOrderAscNameAsc();

    boolean existsByCodeIgnoreCase(String code);
}
