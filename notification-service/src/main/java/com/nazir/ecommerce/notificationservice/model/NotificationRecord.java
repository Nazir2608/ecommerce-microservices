package com.nazir.ecommerce.notificationservice.model;

import lombok.*;

import java.time.LocalDateTime;

/**
 * In-memory record of a sent notification.
 * NOT persisted to a database — this service is stateless.
 * Used only for the /api/v1/notifications/health endpoint to show recent activity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRecord {
    private String eventId;
    private String notificationType;
    private String recipientEmail;
    private String subject;
    private NotificationStatus status;
    private String errorMessage;
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();

    public enum NotificationStatus {SENT, SKIPPED_DUPLICATE, FAILED}
}
