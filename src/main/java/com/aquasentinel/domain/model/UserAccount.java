package com.aquasentinel.domain.model;

import com.aquasentinel.domain.enums.Role;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class UserAccount {
    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final String salt;
    private final String kdfAlgorithm;
    private final int kdfIterations;
    private final Role role;
    private final boolean active;
    private final Instant createdAt;
    private final int version;

    public UserAccount(UUID id, String username, String passwordHash, String salt,
                       String kdfAlgorithm, int kdfIterations, Role role,
                       boolean active, Instant createdAt, int version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.salt = Objects.requireNonNull(salt, "salt must not be null");
        this.kdfAlgorithm = Objects.requireNonNull(kdfAlgorithm, "kdfAlgorithm must not be null");
        this.kdfIterations = kdfIterations;
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = version;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public String getKdfAlgorithm() { return kdfAlgorithm; }
    public int getKdfIterations() { return kdfIterations; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public int getVersion() { return version; }
}
