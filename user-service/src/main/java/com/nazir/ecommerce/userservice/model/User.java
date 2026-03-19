package com.nazir.ecommerce.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * User — aggregate root for the user-service bounded context.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Database-per-Service pattern                      │
 * │                                                                     │
 * │  This entity maps to the "userdb" PostgreSQL database.              │
 * │  No other service has direct access to this schema.                 │
 * │  Other services get user data via:                                  │
 * │    a) Feign HTTP call  → GET /api/v1/users/{id}                     │
 * │    b) Kafka event      → user.events topic (denormalized snapshot)  │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — UUID as primary key                               │
 * │                                                                     │
 * │  UUID vs auto-increment integer:                                    │
 * │  + UUID is globally unique across services — no collisions when     │
 * │    merging or migrating data                                        │
 * │  + Safe to expose in APIs (no sequential enumeration attacks)       │
 * │  - Larger storage (16 bytes vs 8 bytes)                             │
 * │  - Slightly slower index scans (random inserts → page splits)       │
 * └─────────────────────────────────────────────────────────────────────┘
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email",    columnList = "email",    unique = true),
        @Index(name = "idx_users_username", columnList = "username", unique = true),
        @Index(name = "idx_users_status",   columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password")   // NEVER include password in logs / toString
@EqualsAndHashCode(of = "id")     // identity based on primary key only
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt-hashed password. NEVER store plain text.
     * The column is excluded from toString and never returned in DTOs.
     */
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /**
     * User status drives what the user can do.
     * ACTIVE          → normal access
     * INACTIVE        → soft-disabled by admin
     * SUSPENDED       → banned (e.g., payment fraud)
     * PENDING_VERIFY  → registered but email not confirmed yet
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Roles determine what endpoints a user can access (RBAC).
     *
     * LEARNING POINT — @ElementCollection:
     *   Stores the collection in a separate join table (user_roles).
     *   Simpler than a full @ManyToMany entity; works well when the
     *   collection is always loaded with the parent and never queried
     *   standalone. For complex permission systems, model Role as an entity.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    @Builder.Default
    private Set<Role> roles = new HashSet<>(Set.of(Role.ROLE_CUSTOMER));

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    /**
     * LEARNING POINT — @CreationTimestamp / @UpdateTimestamp:
     *   Hibernate automatically sets these. No need to set them manually.
     *   updatable = false on createdAt ensures it's never overwritten.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // ─── Domain helpers ───────────────────────────────────────────────────────

    /** True if the account is locked due to too many failed login attempts. */
    public boolean isAccountLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    /** Increment failed attempts and lock after 5 failures (15-minute lock). */
    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(15);
        }
    }

    /** Clear failed attempts on successful login. */
    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = LocalDateTime.now();
    }

    // ─── Enums ────────────────────────────────────────────────────────────────

    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        PENDING_VERIFY
    }

    public enum Role {
        ROLE_CUSTOMER,
        ROLE_SELLER,
        ROLE_ADMIN
    }
}
