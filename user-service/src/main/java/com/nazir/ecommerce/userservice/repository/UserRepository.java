package com.nazir.ecommerce.userservice.repository;

import com.nazir.ecommerce.userservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the User aggregate.
 *
 * LEARNING POINT — Spring Data JPA method naming convention:
 *   findBy{Field}         → SELECT * FROM users WHERE field = ?
 *   existsBy{Field}       → SELECT COUNT(*) > 0 ...
 *   findBy{A}And{B}       → WHERE a = ? AND b = ?
 *   Spring generates the SQL at startup — zero boilerplate.
 *
 * LEARNING POINT — When to use @Query:
 *   Use @Query for complex JPQL/SQL that can't be expressed by method names.
 *   Keep business logic in the Service layer, NOT in @Query.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // ─── Lookup methods ───────────────────────────────────────────────────────

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    /**
     * Fetch user WITH roles eagerly to avoid N+1 on login.
     * Without this, accessing user.getRoles() after the session closes → LazyInitializationException.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    // ─── Existence checks (used before create to fail fast) ───────────────────

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    // ─── Admin queries ────────────────────────────────────────────────────────

    Page<User> findByStatus(User.UserStatus status, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<User> search(@Param("q") String query, Pageable pageable);

    // ─── Update helpers ───────────────────────────────────────────────────────

    /**
     * Bulk update — more efficient than loading + saving when only one field changes.
     * @Modifying tells Spring this is a write operation (not SELECT).
     * clearAutomatically = true → evicts entities from 1st-level cache after update.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.lastLoginAt = :ts WHERE u.id = :id")
    void updateLastLoginAt(@Param("id") UUID id, @Param("ts") LocalDateTime ts);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") User.UserStatus status);
}
