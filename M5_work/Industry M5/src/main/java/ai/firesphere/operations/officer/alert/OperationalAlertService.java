package ai.firesphere.operations.officer.alert;

import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEvent;
import ai.firesphere.notification.event.NotificationEventType;
import ai.firesphere.notification.model.NotificationPriority;
import ai.firesphere.notification.service.NotificationService;
import ai.firesphere.operations.officer.config.OfficerOperationsProperties;
import ai.firesphere.operations.officer.model.OfficerTask;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import ai.firesphere.operations.officer.model.OperationalAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates and deduplicates operational alerts for:
 * - Overdue inspections
 * - High-priority unassigned tasks
 * - Approaching scheduled deadlines
 * Reuses internal NotificationService if available.
 */
@Service
public class OperationalAlertService {

    private static final Logger log = LoggerFactory.getLogger(OperationalAlertService.class);

    private final OfficerOperationsProperties properties;
    private final Clock clock;
    private final NotificationService notificationService;

    // Deduplication store: alertFingerprint -> generatedAt
    private final Set<String> emittedAlertFingerprints = ConcurrentHashMap.newKeySet();

    public OperationalAlertService(
            OfficerOperationsProperties properties,
            Clock clock,
            @Autowired(required = false) NotificationService notificationService
    ) {
        this.properties = properties;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    public List<OperationalAlert> evaluateAlerts(List<OfficerTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }

        Instant now = clock.instant();
        List<OperationalAlert> alerts = new ArrayList<>();

        for (OfficerTask task : tasks) {
            if (task.status() != null && task.status().isTerminal()) {
                continue;
            }

            // 1. Check Overdue
            if (task.isOverdue(now)) {
                String fingerprint = task.taskId() + ":OVERDUE_INSPECTION";
                if (emittedAlertFingerprints.add(fingerprint)) {
                    OperationalAlert alert = new OperationalAlert(
                            "ALERT-OD-" + UUID.randomUUID().toString().substring(0, 8),
                            "OVERDUE_INSPECTION",
                            task.taskId(),
                            task.taskReference(),
                            task.assignedOfficerBadge(),
                            task.priority(),
                            "Overdue Operational Task: " + task.taskReference(),
                            String.format("Task '%s' was due at %s and is currently overdue.", task.taskReference(), task.deadlineAt()),
                            now,
                            false
                    );
                    alerts.add(alert);
                    dispatchToNotificationSystem(alert, task);
                }
            }

            // 2. Check High-Priority Unassigned
            if (task.status() == OfficerTaskStatus.UNASSIGNED &&
                    (task.priority() == DispatchPriority.HIGH || task.priority() == DispatchPriority.CRITICAL)) {
                String fingerprint = task.taskId() + ":HIGH_PRIORITY_UNASSIGNED";
                if (emittedAlertFingerprints.add(fingerprint)) {
                    OperationalAlert alert = new OperationalAlert(
                            "ALERT-UN-" + UUID.randomUUID().toString().substring(0, 8),
                            "HIGH_PRIORITY_UNASSIGNED",
                            task.taskId(),
                            task.taskReference(),
                            null,
                            task.priority(),
                            "High-Priority Unassigned Task: " + task.taskReference(),
                            String.format("Priority %s task '%s' has no officer assigned.", task.priority(), task.taskReference()),
                            now,
                            false
                    );
                    alerts.add(alert);
                    dispatchToNotificationSystem(alert, task);
                }
            }

            // 3. Check Approaching Deadline
            long thresholdMins = properties.getDeadlineApproachingThresholdMinutes();
            if (task.isApproachingDeadline(now, thresholdMins)) {
                String fingerprint = task.taskId() + ":DEADLINE_APPROACHING";
                if (emittedAlertFingerprints.add(fingerprint)) {
                    OperationalAlert alert = new OperationalAlert(
                            "ALERT-AP-" + UUID.randomUUID().toString().substring(0, 8),
                            "DEADLINE_APPROACHING",
                            task.taskId(),
                            task.taskReference(),
                            task.assignedOfficerBadge(),
                            task.priority(),
                            "Deadline Approaching: " + task.taskReference(),
                            String.format("Task '%s' deadline is approaching within %d minutes (%s).",
                                    task.taskReference(), thresholdMins, task.deadlineAt()),
                            now,
                            false
                    );
                    alerts.add(alert);
                    dispatchToNotificationSystem(alert, task);
                }
            }
        }

        return alerts;
    }

    private void dispatchToNotificationSystem(OperationalAlert alert, OfficerTask task) {
        if (notificationService == null) {
            log.debug("[OPERATIONAL ALERT] NotificationService unattached, skipped broadcast for alert {}", alert.alertId());
            return;
        }

        try {
            NotificationPriority notifPriority = switch (alert.priority()) {
                case CRITICAL -> NotificationPriority.CRITICAL;
                case HIGH -> NotificationPriority.HIGH;
                default -> NotificationPriority.NORMAL;
            };

            Map<String, Object> payload = new HashMap<>();
            payload.put("alertId", alert.alertId());
            payload.put("alertType", alert.alertType());
            payload.put("taskReference", alert.taskReference());
            payload.put("alertTitle", alert.title());
            payload.put("severity", alert.priority().name());
            payload.put("message", alert.message());

            Map<NotificationChannel, String> recipients = new HashMap<>();
            if (alert.officerBadge() != null) {
                recipients.put(NotificationChannel.PUSH, "officer-" + alert.officerBadge());
            } else {
                recipients.put(NotificationChannel.PUSH, "officer-broadcast");
            }

            NotificationEvent event = NotificationEvent.builder()
                    .eventType(NotificationEventType.SYSTEM_ALERT)
                    .referenceId(alert.alertId())
                    .tenantId(task.tenantId())
                    .channels(Set.of(NotificationChannel.PUSH))
                    .recipients(recipients)
                    .payload(payload)
                    .priority(notifPriority)
                    .build();

            notificationService.processEvent(event);
            log.info("[OPERATIONAL ALERT] Dispatched notification event for alert {}", alert.alertId());
        } catch (Exception e) {
            log.warn("[OPERATIONAL ALERT] Failed to dispatch alert to notification service: {}", e.getMessage());
        }
    }

    public void clearDeduplicationCache() {
        emittedAlertFingerprints.clear();
    }
}
