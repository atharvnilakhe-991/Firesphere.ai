package ai.firesphere.notification.controller;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.dto.AlertBroadcastRequest;
import ai.firesphere.notification.dto.NotificationReceiptDTO;
import ai.firesphere.notification.dto.NotificationRecordResponseDTO;
import ai.firesphere.notification.dto.NotificationSendRequest;
import ai.firesphere.notification.event.NotificationEvent;
import ai.firesphere.notification.event.NotificationEventType;
import ai.firesphere.notification.model.NotificationPriority;
import ai.firesphere.notification.model.NotificationRecord;
import ai.firesphere.notification.service.NotificationOrchestrationService;
import ai.firesphere.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller exposing multi-channel notification dispatch, history, and retry.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Multi-channel operational notification and alerting API")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationOrchestrationService orchestrationService;

    public NotificationController(
            NotificationService notificationService,
            NotificationOrchestrationService orchestrationService
    ) {
        this.notificationService = notificationService;
        this.orchestrationService = orchestrationService;
    }

    @PostMapping("/events")
    @Operation(summary = "Publish notification event across specified channels")
    public ResponseEntity<List<NotificationRecordResponseDTO>> publishEvent(@Valid @RequestBody NotificationSendRequest request) {
        NotificationEventType eventType = NotificationEventType.fromString(request.eventType());

        Set<NotificationChannel> channels = request.channels().stream()
                .map(NotificationChannel::fromString)
                .collect(Collectors.toSet());

        Map<NotificationChannel, String> recipients = new HashMap<>();
        if (request.recipients() != null) {
            request.recipients().forEach((chStr, dest) -> {
                try {
                    NotificationChannel ch = NotificationChannel.fromString(chStr);
                    recipients.put(ch, dest);
                } catch (Exception ignored) {
                }
            });
        }

        NotificationPriority priority = NotificationPriority.fromString(request.priority());

        NotificationEvent event = NotificationEvent.builder()
                .eventType(eventType)
                .referenceId(request.referenceId())
                .tenantId(TenantContext.getTenantId())
                .channels(channels)
                .recipients(recipients)
                .payload(request.payload())
                .idempotencyKey(request.idempotencyKey())
                .priority(priority)
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);
        List<NotificationRecordResponseDTO> response = records.stream()
                .map(NotificationRecordResponseDTO::fromRecord)
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve notification record and attempt history by ID")
    public ResponseEntity<NotificationRecordResponseDTO> getNotificationById(@PathVariable UUID id) {
        return notificationService.getNotificationById(id)
                .map(record -> ResponseEntity.ok(NotificationRecordResponseDTO.fromRecord(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a previously failed or pending notification delivery")
    public ResponseEntity<NotificationRecordResponseDTO> retryNotification(@PathVariable UUID id) {
        NotificationRecord retried = notificationService.retryNotification(id);
        return ResponseEntity.ok(NotificationRecordResponseDTO.fromRecord(retried));
    }

    @GetMapping
    @Operation(summary = "List all notification history for current tenant")
    public ResponseEntity<List<NotificationRecordResponseDTO>> listTenantNotifications() {
        UUID tenantId = TenantContext.getTenantId();
        List<NotificationRecord> records = notificationService.getNotificationsByTenant(tenantId);
        List<NotificationRecordResponseDTO> response = records.stream()
                .map(NotificationRecordResponseDTO::fromRecord)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Legacy broadcast alarm to multiple recipients")
    public ResponseEntity<NotificationReceiptDTO> broadcastAlert(@Valid @RequestBody AlertBroadcastRequest request) {
        NotificationReceiptDTO receipt = orchestrationService.broadcast(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    @PostMapping("/receipts/{id}/ack")
    @Operation(summary = "Acknowledge receipt of a broadcast notification")
    public ResponseEntity<NotificationReceiptDTO> acknowledgeReceipt(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "OFFICER") String badge
    ) {
        NotificationReceiptDTO receipt = orchestrationService.acknowledgeReceipt(id, badge);
        return ResponseEntity.ok(receipt);
    }
}
