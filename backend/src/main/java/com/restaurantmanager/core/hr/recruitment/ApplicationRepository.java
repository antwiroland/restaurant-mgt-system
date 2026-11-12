package com.restaurantmanager.core.hr.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {
    List<ApplicationEntity> findByVacancyIdOrderByCreatedAtAsc(UUID vacancyId);

    List<ApplicationEntity> findByVacancyIdIn(Collection<UUID> vacancyIds);

    List<ApplicationEntity> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);

    List<ApplicationEntity> findByCandidateIdIn(Collection<UUID> candidateIds);

    boolean existsByVacancyIdAndCandidateId(UUID vacancyId, UUID candidateId);
}
