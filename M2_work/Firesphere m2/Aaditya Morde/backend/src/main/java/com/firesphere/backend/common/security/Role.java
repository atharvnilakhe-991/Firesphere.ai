package com.firesphere.backend.common.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum Role {
    CITIZEN(Set.of(
            Permission.USER_READ,
            Permission.USER_WRITE,
            Permission.NOC_READ,
            Permission.NOC_CREATE,
            Permission.NOC_AI_READ,
            Permission.NOC_AI_REQUEST
    )),
    OFFICER(Set.of(
            Permission.USER_READ,
            Permission.ORGANIZATION_READ,
            Permission.NOC_READ,
            Permission.NOC_UPDATE,
            Permission.INSPECTION_READ,
            Permission.INSPECTION_UPDATE,
            Permission.NOC_AI_READ,
            Permission.NOC_AI_REQUEST
    )),
    APPROVAL_AUTHORITY(Set.of(
            Permission.USER_READ,
            Permission.ORGANIZATION_READ,
            Permission.NOC_READ,
            Permission.NOC_APPROVE,
            Permission.NOC_REJECT,
            Permission.NOC_AI_READ,
            Permission.NOC_CERTIFICATE_GENERATE
    )),
    ADMIN(Set.of(
            Permission.USER_READ,
            Permission.USER_WRITE,
            Permission.ORGANIZATION_READ,
            Permission.ORGANIZATION_WRITE,
            Permission.NOC_READ,
            Permission.NOC_CREATE,
            Permission.NOC_UPDATE,
            Permission.NOC_APPROVE,
            Permission.NOC_REJECT,
            Permission.INSPECTION_READ,
            Permission.INSPECTION_UPDATE,
            Permission.NOC_AI_READ,
            Permission.NOC_AI_REQUEST,
            Permission.NOC_CERTIFICATE_GENERATE
    ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public List<SimpleGrantedAuthority> getAuthorities() {
        var authorities = getPermissions()
                .stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toList());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return authorities;
    }
}
