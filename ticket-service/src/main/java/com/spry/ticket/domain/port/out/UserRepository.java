package com.spry.ticket.domain.port.out;

import com.spry.ticket.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);
    User save(User user);
}
