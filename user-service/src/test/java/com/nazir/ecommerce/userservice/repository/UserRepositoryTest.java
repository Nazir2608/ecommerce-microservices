//package com.nazir.ecommerce.userservice.repository;
//
//import com.nazir.ecommerce.userservice.model.User;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.util.Optional;
//import java.util.Set;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * Repository slice test using @DataJpaTest + Testcontainers.
// * <p>
// * — @DataJpaTest:
// * Loads ONLY the JPA slice of the Spring context:
// * • Entity classes, repositories, JPA configuration
// * • Flyway migrations (configured separately)
// * • In-memory H2 by default, overridden here with Testcontainers PostgreSQL
// * Does NOT load: @Service, @Controller, security, Kafka, Redis.
// * → Fast (seconds), focused on persistence behaviour.
// * <p>
// * — Testcontainers:
// * Starts a real PostgreSQL Docker container for the test run.
// *
// * @Container + @Testcontainers manages container lifecycle automatically.
// * @DynamicPropertySource injects the container's URL into Spring's Environment
// * BEFORE the ApplicationContext starts.
// * <p>
// * — @AutoConfigureTestDatabase(replace = NONE):
// * Tells @DataJpaTest NOT to replace the datasource with an embedded DB.
// * Required when using Testcontainers (we want the real PostgreSQL).
// * <p>
// * — TestEntityManager:
// * A test-only EntityManager wrapper. Use it to persist fixtures directly
// * (bypasses the repository layer so tests don't depend on save() being correct).
// * em.persistAndFlush() → writes to DB immediately (no deferred flushing).
// * em.clear() → evicts entities from 1st-level cache → next find() hits DB.
// */
//@DataJpaTest
//@Testcontainers
//@ActiveProfiles("test")
//@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@DisplayName("UserRepository")
//class UserRepositoryTest {
//
//    // ─── Testcontainers: shared PostgreSQL for all tests in this class ─────────
//    @Container
//    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
//            .withDatabaseName("userdb_test")
//            .withUsername("testuser")
//            .withPassword("testpassword")
//            .withReuse(true);   // reuse container between test classes (faster CI)
//
//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//    }
//
//    // ─── Dependencies ─────────────────────────────────────────────────────────
//    @Autowired
//    private UserRepository userRepository;
//    @Autowired
//    private TestEntityManager em;
//
//    // ─── Fixtures ─────────────────────────────────────────────────────────────
//
//    private User savedUser;
//
//    @BeforeEach
//    void setUp() {
//        userRepository.deleteAll();
//
//        savedUser = em.persistAndFlush(User.builder()
//                .username("nazir")
//                .email("nazir@example.com")
//                .password("$2a$12$hashedpwd")
//                .firstName("Nazir")
//                .lastName("Khan")
//                .status(User.UserStatus.ACTIVE)
//                .roles(Set.of(User.Role.ROLE_CUSTOMER))
//                .build());
//
//        em.clear(); // evict from cache so reads go to DB
//    }
//
//    // ─── findByEmail ──────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("findByEmail() should return user when email exists")
//    void findByEmail_found() {
//        Optional<User> result = userRepository.findByEmail("nazir@example.com");
//
//        assertThat(result).isPresent();
//        assertThat(result.get().getUsername()).isEqualTo("nazir");
//    }
//
//    @Test
//    @DisplayName("findByEmail() should return empty when email does not exist")
//    void findByEmail_notFound() {
//        Optional<User> result = userRepository.findByEmail("unknown@example.com");
//        assertThat(result).isEmpty();
//    }
//
//    // ─── findByEmailWithRoles ─────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("findByEmailWithRoles() should eagerly load roles")
//    void findByEmailWithRoles_loadsRoles() {
//        Optional<User> result = userRepository.findByEmailWithRoles("nazir@example.com");
//
//        assertThat(result).isPresent();
//        // roles are loaded even after session close (JOIN FETCH in query)
//        assertThat(result.get().getRoles()).contains(User.Role.ROLE_CUSTOMER);
//    }
//
//    // ─── existsBy ─────────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("existsByEmail() should return true for existing email")
//    void existsByEmail_true() {
//        assertThat(userRepository.existsByEmail("nazir@example.com")).isTrue();
//    }
//
//    @Test
//    @DisplayName("existsByEmail() should return false for unknown email")
//    void existsByEmail_false() {
//        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
//    }
//
//    @Test
//    @DisplayName("existsByUsername() should return true for existing username")
//    void existsByUsername_true() {
//        assertThat(userRepository.existsByUsername("nazir")).isTrue();
//    }
//
//    // ─── search ───────────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("search() should match partial email")
//    void search_byPartialEmail() {
//        Page<User> result = userRepository.search("nazir@", PageRequest.of(0, 10));
//        assertThat(result.getTotalElements()).isEqualTo(1);
//        assertThat(result.getContent().get(0).getEmail()).isEqualTo("nazir@example.com");
//    }
//
//    @Test
//    @DisplayName("search() should match partial first name (case-insensitive)")
//    void search_byFirstName_caseInsensitive() {
//        Page<User> result = userRepository.search("NAZ", PageRequest.of(0, 10));
//        assertThat(result.getTotalElements()).isEqualTo(1);
//    }
//
//    @Test
//    @DisplayName("search() should return empty page when no match")
//    void search_noMatch() {
//        Page<User> result = userRepository.search("xxxxxxx", PageRequest.of(0, 10));
//        assertThat(result.getTotalElements()).isEqualTo(0);
//    }
//
//    // ─── findByStatus ─────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("findByStatus() should return only ACTIVE users")
//    void findByStatus_active() {
//        // Add a suspended user
//        em.persistAndFlush(User.builder()
//                .username("suspended_user")
//                .email("suspended@example.com")
//                .password("$2a$12$hash")
//                .firstName("Sus")
//                .status(User.UserStatus.SUSPENDED)
//                .build());
//        em.clear();
//
//        Page<User> result = userRepository.findByStatus(
//                User.UserStatus.ACTIVE, PageRequest.of(0, 10));
//
//        assertThat(result.getTotalElements()).isEqualTo(1);
//        assertThat(result.getContent().get(0).getStatus()).isEqualTo(User.UserStatus.ACTIVE);
//    }
//}
