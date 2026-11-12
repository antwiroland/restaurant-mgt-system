package com.restaurantmanager.core.hr.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CandidateRepository extends JpaRepository<CandidateEntity, UUID> {
    List<CandidateEntity> findByPhone(String phone);

    @Query("select c from CandidateEntity c where lower(c.email) = lower(:email)")
    List<CandidateEntity> findByEmail(@Param("email") String email);

    @Query("select c from CandidateEntity c where c.anonymisedAt is null and (lower(c.firstName) like :q or lower(c.lastName) like :q "
            + "or c.phone like :q or lower(c.email) like :q) order by c.createdAt desc")
    List<CandidateEntity> search(@Param("q") String q);

    List<CandidateEntity> findTop200ByOrderByCreatedAtDesc();
}
