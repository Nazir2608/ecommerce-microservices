package com.nazir.ecommerce.notificationservice.controller;

import com.nazir.ecommerce.notificationservice.model.NotificationRecord;
import com.nazir.ecommerce.notificationservice.service.impl.NotificationServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification monitoring")
public class NotificationController {

    private final NotificationServiceImpl notificationService;

    @GetMapping("/recent")
    @Operation(summary = "View recent notification activity (last N records)")
    public ResponseEntity<List<NotificationRecord>> getRecent(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(notificationService.getRecentNotifications(limit));
    }

    @GetMapping("/health")
    @Operation(summary = "Service health check with stats")
    public ResponseEntity<Map<String, Object>> health() {
        List<NotificationRecord> recent = notificationService.getRecentNotifications(100);
        long sent = recent.stream().filter(r -> r.getStatus() == NotificationRecord.NotificationStatus.SENT).count();
        long skipped = recent.stream().filter(r -> r.getStatus() == NotificationRecord.NotificationStatus.SKIPPED_DUPLICATE).count();
        long failed = recent.stream().filter(r -> r.getStatus() == NotificationRecord.NotificationStatus.FAILED).count();

        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "notification-service",
                "recentEmailsSent", sent,
                "recentDuplicatesSkipped", skipped,
                "recentFailed", failed,
                "totalTracked", recent.size()
        ));
    }
}
