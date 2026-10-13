package com.firesphere.backend.common.security;

public enum Permission {
    USER_READ("user:read"),
    USER_WRITE("user:write"),
    ORGANIZATION_READ("organization:read"),
    ORGANIZATION_WRITE("organization:write"),
    NOC_READ("noc:read"),
    NOC_CREATE("noc:create"),
    NOC_UPDATE("noc:update"),
    NOC_APPROVE("noc:approve"),
    NOC_REJECT("noc:reject"),
    INSPECTION_READ("inspection:read"),
    INSPECTION_UPDATE("inspection:update"),
    NOC_AI_READ("noc:ai:read"),
    NOC_AI_REQUEST("noc:ai:request"),
    NOC_CERTIFICATE_GENERATE("noc:certificate:generate");

    private final String permission;

    Permission(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}
