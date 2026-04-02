package com.nazir.ecommerce.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumed from topic: user.events (published by user-service)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEvent {
    private String eventId;
    private EventType eventType;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private LocalDateTime occurredAt;

    public enum EventType {
        USER_REGISTERED,
        USER_UPDATED,
        USER_DELETED,
        USER_SUSPENDED,
        USER_ACTIVATED
    }
}
