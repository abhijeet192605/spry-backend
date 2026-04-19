package com.spry.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletableEntity extends AuditEntity {

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public void softDelete(UUID actorId) {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = actorId;
    }
}
