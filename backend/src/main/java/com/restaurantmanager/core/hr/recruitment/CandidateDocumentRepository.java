package com.restaurantmanager.core.hr.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CandidateDocumentRepository extends JpaRepository<CandidateDocumentEntity, UUID> {
    List<CandidateDocumentEntity> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
}
