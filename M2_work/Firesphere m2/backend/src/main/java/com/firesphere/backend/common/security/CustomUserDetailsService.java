package com.firesphere.backend.common.security;

import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = null;
        try {
            UUID id = UUID.fromString(username);
            user = userRepository.findByUuid(id).orElse(null);
        } catch (IllegalArgumentException ignored) {
        }

        if (user == null) {
            try {
                Long id = Long.parseLong(username);
                user = userRepository.findById(id).orElse(null);
            } catch (NumberFormatException ignored) {
            }
        }

        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(username).orElse(null);
        }

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UsernameNotFoundException("User account is not active");
        }

        java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities = java.util.List.of();
        if (user.getRole() != null) {
            try {
                Role role = Role.valueOf(user.getRole());
                authorities = role.getAuthorities();
            } catch (IllegalArgumentException ignored) {
                // Invalid role mapping, default to no authorities
            }
        }

        return new AuthenticatedUser(
                user.getUuid(),
                user.getEmail(),
                user.getOrganizationUuid(),
                user.getId(),
                user.getOrgId(),
                authorities
        );
    }
}
