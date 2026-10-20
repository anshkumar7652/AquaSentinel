package com.aquasentinel.application.service;

import com.aquasentinel.application.port.AuditRepository;
import com.aquasentinel.application.port.UserRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.exception.AuthenticationException;
import com.aquasentinel.domain.exception.ValidationException;
import com.aquasentinel.domain.model.UserAccount;
import com.aquasentinel.infrastructure.security.PBKDF2PasswordHasher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class AuthenticationService {
    private final UserRepository userRepository;
    private final PBKDF2PasswordHasher passwordHasher;
    private final AuditRepository auditRepository;

    public AuthenticationService(UserRepository userRepository,
                                 PBKDF2PasswordHasher passwordHasher,
                                 AuditRepository auditRepository) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.auditRepository = auditRepository;
    }

    public boolean isSetupRequired() {
        return userRepository.count() == 0;
    }

    public Session setupInitialAdmin(String username, char[] password) {
        if (!isSetupRequired()) {
            throw new ValidationException("System is already initialized. Cannot run first-time setup.");
        }
        if (username == null || username.trim().length() < 3) {
            throw new ValidationException("Username must be at least 3 characters long.");
        }
        if (password == null || password.length < 8) {
            throw new ValidationException("Password must be at least 8 characters long.");
        }

        PBKDF2PasswordHasher.HashResult hashResult = passwordHasher.hashPassword(password);
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        UserAccount admin = new UserAccount(
                userId,
                username.trim(),
                hashResult.hash(),
                hashResult.salt(),
                hashResult.algorithm(),
                hashResult.iterations(),
                Role.ADMINISTRATOR,
                true,
                now,
                1
        );

        userRepository.save(admin);
        auditRepository.record(userId, "FIRST_RUN_ADMIN_SETUP", "USER", userId.toString(), now, null,
                "{\"username\":\"" + admin.getUsername() + "\",\"role\":\"ADMINISTRATOR\"}");

        return new Session(admin.getId(), admin.getUsername(), admin.getRole());
    }

    public Session authenticate(String username, char[] password) {
        if (username == null || password == null) {
            throw new AuthenticationException("Invalid username or password.");
        }

        Optional<UserAccount> userOpt = userRepository.findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            // Constant-time dummy verification to protect against timing attacks
            passwordHasher.verifyPassword(password, "dummyHash", "dummySalt", PBKDF2PasswordHasher.ALGORITHM, PBKDF2PasswordHasher.DEFAULT_ITERATIONS);
            throw new AuthenticationException("Invalid username or password.");
        }

        UserAccount user = userOpt.get();
        if (!user.isActive()) {
            throw new AuthenticationException("Account is disabled. Please contact an administrator.");
        }

        boolean valid = passwordHasher.verifyPassword(
                password,
                user.getPasswordHash(),
                user.getSalt(),
                user.getKdfAlgorithm(),
                user.getKdfIterations()
        );

        if (!valid) {
            throw new AuthenticationException("Invalid username or password.");
        }

        auditRepository.record(user.getId(), "USER_LOGIN", "USER", user.getId().toString(), Instant.now(), null, null);
        return new Session(user.getId(), user.getUsername(), user.getRole());
    }
}
