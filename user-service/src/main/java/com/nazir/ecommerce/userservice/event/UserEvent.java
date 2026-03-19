package com.nazir.ecommerce.userservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Domain event published to Kafka topic: {@code user.events}
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Domain Events                                          │
 * │                                                                          │
 * │  A domain event records that something important happened in the domain. │
 * │  Other services subscribe and react — without direct coupling.           │
 * │                                                                          │
 * │  USER_REGISTERED  → notification-service sends welcome email            │
 * │  USER_UPDATED     → order-service refreshes cached user snapshot         │
 * │  USER_DELETED     → order-service anonymizes order data (GDPR)           │
 * │  USER_SUSPENDED   → notification-service sends account suspension email  │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Event payload design                                   │
 * │                                                                          │
 * │  Include enough data so consumers don't need to call back:               │
 * │    ✓ userId, email, firstName — enough for notification-service          │
 * │  But don't include sensitive data:                                       │
 * │    ✗ password, phone number — don't belong in events                    │
 * │                                                                          │
 * │  Event schema evolves carefully — consumers may be on older versions.   │
 * │  Never remove fields. Add new optional fields with defaults.             │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEvent {

    /** Unique event ID — used by consumers for idempotency. */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    /** What happened. */
    private EventType eventType;

    /** Who it happened to. */
    private UUID userId;

    // Denormalized snapshot — consumers don't need to call user-service
    private String email;
    private String username;
    private String firstName;
    private String lastName;

    /** When it happened — ISO-8601, UTC. */
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    /** Service that produced this event. */
    @Builder.Default
    private String source = "user-service";

    // ─── Event types ──────────────────────────────────────────────────────────

    public enum EventType {
        USER_REGISTERED,
        USER_UPDATED,
        USER_DELETED,
        USER_SUSPENDED,
        USER_ACTIVATED,
        PASSWORD_CHANGED
    }
}
