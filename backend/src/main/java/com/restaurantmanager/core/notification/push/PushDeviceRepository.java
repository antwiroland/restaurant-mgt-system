package com.restaurantmanager.core.notification.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDeviceEntity, UUID> {
    Optional<PushDeviceEntity> findByToken(String token);

    List<PushDeviceEntity> findByUserIdIn(Collection<UUID> userIds);

    @Modifying
    @Transactional
    void deleteByTokenIn(Collection<String> tokens);
}
