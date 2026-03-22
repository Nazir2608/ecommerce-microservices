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

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private EventType eventType;

    private UUID userId;

    private String email;
    private String username;
    private String firstName;
    private String lastName;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Builder.Default
    private String source = "user-service";

    public enum EventType {
        USER_REGISTERED,
        USER_UPDATED,
        USER_DELETED,
        USER_SUSPENDED,
        USER_ACTIVATED,
        PASSWORD_CHANGED
    }
}
