package com.multitenant.app.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class for all tenant-scoped entities that need automatic audit tracking.
 *
 * HOW IT WORKS:
 * Any entity that extends this class automatically gets these 4 columns populated:
 *   - created_at  : Set once when the entity is first saved (INSERT)
 *   - updated_at  : Updated every time the entity is modified (UPDATE)
 *   - created_by  : Keycloak user ID (JWT subject) of who created it
 *   - updated_by  : Keycloak user ID of who last modified it
 *
 * @MappedSuperclass - Tells JPA this is not a table itself, but its fields should be
 *                     inherited by child entities (CompanyUser, Client, etc.)
 *
 * @EntityListeners(AuditingEntityListener.class) - Spring Data JPA listener that
 *                     auto-fills @CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy.
 *                     The "By" fields are resolved by AuditorAwareImpl which reads the JWT.
 *
 * USAGE:
 *   public class CompanyUser extends AuditEntity { ... }
 *   // Now CompanyUser automatically has created_at, updated_at, created_by, updated_by
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Stores the Keycloak user ID (UUID string) of who created this record
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    // Stores the Keycloak user ID of who last modified this record
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
}
