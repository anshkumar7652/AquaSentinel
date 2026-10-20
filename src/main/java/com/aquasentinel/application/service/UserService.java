package com.aquasentinel.application.service;

import com.aquasentinel.application.dto.UserDto;
import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.application.port.UserRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.exception.AuthorizationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.UserAccount;
import com.aquasentinel.infrastructure.security.PBKDF2PasswordHasher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class UserService {
    private final UserRepository userRepository;
    private final PBKDF2PasswordHasher passwordHasher;
    private final AuditRepository auditRepository;

    public UserService(UserRepository userRepository,
                       PBKDF2PasswordHasher passwordHasher,
                       AuditRepository auditRepository) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.auditRepository = auditRepository;
    }

    public UserDto createUser(Session session, String username, char[] password, Role role) {
        if (!session.isAdministrator()) {
            throw new AuthorizationException("Only administrators can create user accounts.");
        }
        if (username == null || username.trim().length() < 3) {
            throw new ValidationException("Username must be at least 3 characters.");
        }
        if (password == null || password.length < 8) {
            throw new ValidationException("Password must be at least 8 characters.");
        }
        if (userRepository.findByUsername(username.trim()).isPresent()) {
            throw new ValidationException("Username already exists: " + username.trim());
        }

        PBKDF2PasswordHasher.HashResult hash = passwordHasher.hashPassword(password);
        UUID newUserId = UUID.randomUUID();
        Instant now = Instant.now();

        UserAccount user = new UserAccount(
                newUserId,
                username.trim(),
                hash.hash(),
                hash.salt(),
                hash.algorithm(),
                hash.iterations(),
                role,
                true,
                now,
                1
        );

        userRepository.save(user);
        auditRepository.record(session.userId(), "USER_CREATED", "USER", newUserId.toString(), now, null,
                "{\"username\":\"" + user.getUsername() + "\",\"role\":\"" + role.name() + "\"}");

        return UserDto.from(user);
    }

    public void setUserActiveStatus(Session session, UUID targetUserId, boolean active) {
        if (!session.isAdministrator()) {
            throw new AuthorizationException("Only administrators can update account status.");
        }
        if (session.userId().equals(targetUserId) && !active) {
            throw new ValidationException("Administrators cannot deactivate their own active account.");
        }

        UserAccount user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ValidationException("User not found: " + targetUserId));

        UserAccount updated = new UserAccount(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getSalt(),
                user.getKdfAlgorithm(),
                user.getKdfIterations(),
                user.getRole(),
                active,
                user.getCreatedAt(),
                user.getVersion()
        );

        userRepository.update(updated);
        auditRepository.record(session.userId(), active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "USER", targetUserId.toString(), Instant.now(),
                "{\"active\":" + user.isActive() + "}",
                "{\"active\":" + active + "}");
    }

    public List<UserDto> listUsers(Session session) {
        if (!session.isAdministrator()) {
            throw new AuthorizationException("Only administrators can list system users.");
        }
        return userRepository.findAll().stream().map(UserDto::from).toList();
    }
}
