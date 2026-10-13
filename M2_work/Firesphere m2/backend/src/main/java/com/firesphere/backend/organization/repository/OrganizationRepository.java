package com.firesphere.backend.organization.repository;

import com.firesphere.backend.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByUuid(UUID uuid);
    Optional<Organization> findByCode(String code);
    
    default Optional<Organization> findById(UUID uuid) {
        return findByUuid(uuid);
    }
}
