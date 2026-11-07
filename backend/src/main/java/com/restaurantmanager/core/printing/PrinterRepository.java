package com.restaurantmanager.core.printing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrinterRepository extends JpaRepository<PrinterEntity, UUID> {
    List<PrinterEntity> findAllByOrderByNameAsc();

    List<PrinterEntity> findByKindAndActiveTrue(PrinterEntity.Kind kind);
}
