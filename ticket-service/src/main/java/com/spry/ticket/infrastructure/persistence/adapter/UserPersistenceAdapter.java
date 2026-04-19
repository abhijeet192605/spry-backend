package com.spry.ticket.infrastructure.persistence.adapter;

import com.spry.ticket.domain.model.User;
import com.spry.ticket.domain.port.out.UserRepository;
import com.spry.ticket.infrastructure.persistence.entity.UserEntity;
import com.spry.ticket.infrastructure.persistence.jpa.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public User save(User user) {
        return toDomain(jpaRepository.save(toEntity(user)));
    }

    private UserEntity toEntity(User user) {
        UserEntity e = new UserEntity();
        if (user.getId() != null) e.setId(user.getId());
        e.setName(user.getName());
        e.setEmail(user.getEmail());
        return e;
    }

    private User toDomain(UserEntity e) {
        return User.builder()
                .id(e.getId())
                .name(e.getName())
                .email(e.getEmail())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
