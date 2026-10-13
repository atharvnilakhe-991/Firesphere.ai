package com.firesphere.backend.auth.repository;

import com.firesphere.backend.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByUuid(UUID uuid);
    Optional<User> findByUuidAndOrgId(UUID uuid, Long orgId);
    Page<User> findAllByOrgId(Long orgId, Pageable pageable);
    
    default Optional<User> findById(UUID uuid) {
        return findByUuid(uuid);
    }
}
