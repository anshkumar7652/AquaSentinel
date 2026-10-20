package com.aquasentinel.application.dto;

import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.model.UserAccount;

import java.time.Instant;
import java.util.UUID;

public record UserDto(UUID id, String username, Role role, boolean active, Instant createdAt) {
    public static UserDto from(UserAccount user) {
        return new UserDto(user.getId(), user.getUsername(), user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
