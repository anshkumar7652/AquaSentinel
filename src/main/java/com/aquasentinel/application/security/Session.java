package com.aquasentinel.application.security;

import com.aquasentinel.domain.enums.Role;

import java.util.Objects;
import java.util.UUID;

public record Session(UUID userId, String username, Role role) {
    public Session {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    public boolean isAdministrator() {
        return role == Role.ADMINISTRATOR;
    }

    public boolean isAnalystOrAdmin() {
        return role == Role.ADMINISTRATOR || role == Role.ANALYST;
    }

    public boolean canManageSources() {
        return role == Role.ADMINISTRATOR;
    }
}
