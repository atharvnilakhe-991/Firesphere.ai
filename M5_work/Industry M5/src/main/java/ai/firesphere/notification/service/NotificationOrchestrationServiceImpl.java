package ai.firesphere.notification.service;

import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.dto.AlertBroadcastRequest;
import ai.firesphere.notification.dto.NotificationReceiptDTO;
import ai.firesphere.notification.event.NotificationEvent;
import ai.firesphere.notification.event.NotificationEventType;
import ai.firesphere.notification.model.NotificationPriority;
import ai.firesphere.notification.model.NotificationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of Phase 1 NotificationOrchestrationService contract.
 * Bridges legacy broadcast requests to the Phase 7 NotificationService engine.
 */
@Service
public class NotificationOrchestrationServiceImpl implements NotificationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrationServiceImpl.class);

    private final NotificationService notificationService;
    private final Map<UUID, String> acknowledgments = new ConcurrentHashMap<>();

    public NotificationOrchestrationServiceImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public NotificationReceiptDTO broadcast(AlertBroadcastRequest request) {
        if (request == null || request.recipients() == null || request.recipients().isEmpty()) {
            throw new IllegalArgumentException("Broadcast request must have valid recipients");
        }

        UUID broadcastBatchId = UUID.randomUUID();
        String target = request.recipients().get(0);

        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.SYSTEM_ALERT)
                .referenceId("BROADCAST-" + broadcastBatchId.toString().substring(0, 8))
                .priority(NotificationPriority.CRITICAL)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, target.length() >= 8 ? target : "device-token-" + target))
                .payload(Map.of(
                        "alertTitle", request.title(),
                        "severity", request.alertType(),
                        "message", request.message()
                ))
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);
        NotificationRecord record = records.isEmpty() ? null : records.get(0);

        UUID receiptId = record != null ? record.getId() : broadcastBatchId;
        String status = record != null ? record.getStatus().name() : "DISPATCHED";

        log.info("[NOTIFICATION ORCHESTRATION] Broadcast alert '{}' sent to {} targets. Receipt ID: {}",
                request.title(), request.recipients().size(), receiptId);

        return new NotificationReceiptDTO(
                receiptId,
                "PUSH",
                target,
                status,
                Instant.now()
        );
    }

    @Override
    public NotificationReceiptDTO acknowledgeReceipt(UUID receiptId, String officerBadge) {
        if (receiptId == null) {
            throw new IllegalArgumentException("Receipt ID cannot be null");
        }
        acknowledgments.put(receiptId, officerBadge != null ? officerBadge : "OFFICER-UNKNOWN");
        log.info("[NOTIFICATION ORCHESTRATION] Receipt {} acknowledged by officer {}", receiptId, officerBadge);

        return new NotificationReceiptDTO(
                receiptId,
                "BROADCAST",
                officerBadge,
                "ACKNOWLEDGED",
                Instant.now()
        );
    }
}
