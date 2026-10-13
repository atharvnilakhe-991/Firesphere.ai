package com.firesphere.backend.auth.repository;

import com.firesphere.backend.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Page<User> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
