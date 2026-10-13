package ai.firesphere.notification.service;

import ai.firesphere.contracts.notification.NotificationProviderContract;
import ai.firesphere.notification.dto.AlertBroadcastRequest;
import ai.firesphere.notification.dto.NotificationReceiptDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

public interface NotificationOrchestrationService {

    NotificationReceiptDTO broadcast(AlertBroadcastRequest request);

    NotificationReceiptDTO acknowledgeReceipt(UUID receiptId, String officerBadge);
}
