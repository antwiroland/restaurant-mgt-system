package com.restaurantmanager.core.hr.common;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HrSequenceRepository extends JpaRepository<HrSequenceEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from HrSequenceEntity s where s.name = :name")
    Optional<HrSequenceEntity> lockByName(@Param("name") String name);
}
