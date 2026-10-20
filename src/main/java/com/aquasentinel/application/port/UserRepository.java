package com.aquasentinel.application.port;

import com.aquasentinel.domain.model.UserAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<UserAccount> findById(UUID id);
    Optional<UserAccount> findByUsername(String username);
    List<UserAccount> findAll();
    long count();
    void save(UserAccount user);
    void update(UserAccount user);
}
