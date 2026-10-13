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
        // Here username is actually the userId (UUID) because that's what we put in the JWT subject
        try {
            UUID id = UUID.fromString(username);
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

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

            return new AuthenticatedUser(user.getId(), user.getEmail(), user.getOrganizationId(), authorities);
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Invalid user id format");
        }
    }
}
