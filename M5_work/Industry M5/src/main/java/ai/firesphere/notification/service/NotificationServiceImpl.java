package ai.firesphere.notification.service;

import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEvent;
import ai.firesphere.notification.model.NotificationPriority;
import ai.firesphere.notification.model.NotificationRecord;
import ai.firesphere.notification.model.NotificationStatus;
import ai.firesphere.notification.provider.EmailNotificationProvider;
import ai.firesphere.notification.provider.ProviderDeliveryResult;
import ai.firesphere.notification.provider.PushNotificationProvider;
import ai.firesphere.notification.provider.SmsNotificationProvider;
import ai.firesphere.notification.provider.WhatsAppNotificationProvider;
import ai.firesphere.notification.repository.NotificationRecordRepository;
import ai.firesphere.notification.template.NotificationTemplate;
import ai.firesphere.notification.template.NotificationTemplateRegistry;
import ai.firesphere.notification.template.TemplateNotFoundException;
import ai.firesphere.notification.template.TemplateRenderingException;
import ai.firesphere.notification.validation.InvalidRecipientException;
import ai.firesphere.notification.validation.RecipientValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Production-oriented implementation of the Notification Service.
 * Orchestrates: Event -> Template Selection -> Recipient Validation -> Channel Dispatch -> Provider Execution -> Retry -> History.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final PushNotificationProvider pushProvider;
    private final SmsNotificationProvider smsProvider;
    private final EmailNotificationProvider emailProvider;
    private final WhatsAppNotificationProvider whatsAppProvider;
    private final NotificationTemplateRegistry templateRegistry;
    private final RecipientValidator recipientValidator;
    private final NotificationRecordRepository recordRepository;

    public NotificationServiceImpl(
            PushNotificationProvider pushProvider,
            SmsNotificationProvider smsProvider,
            EmailNotificationProvider emailProvider,
            WhatsAppNotificationProvider whatsAppProvider,
            NotificationTemplateRegistry templateRegistry,
            RecipientValidator recipientValidator,
            NotificationRecordRepository recordRepository
    ) {
        this.pushProvider = pushProvider;
        this.smsProvider = smsProvider;
        this.emailProvider = emailProvider;
        this.whatsAppProvider = whatsAppProvider;
        this.templateRegistry = templateRegistry;
        this.recipientValidator = recipientValidator;
        this.recordRepository = recordRepository;
    }

    @Override
    public List<NotificationRecord> processEvent(NotificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Notification event cannot be null");
        }

        UUID tenantId = event.tenantId() != null ? event.tenantId() : TenantContext.getTenantId();
        List<NotificationRecord> createdRecords = new ArrayList<>();

        for (NotificationChannel channel : event.channels()) {
            NotificationRecord record = dispatchChannel(event, channel, tenantId);
            createdRecords.add(record);
        }

        return createdRecords;
    }

    private NotificationRecord dispatchChannel(NotificationEvent event, NotificationChannel channel, UUID tenantId) {
        String idempotencyKey = event.idempotencyKey();

        // 1. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<NotificationRecord> existing = recordRepository.findByIdempotencyKeyAndChannel(idempotencyKey, channel);
            if (existing.isPresent()) {
                NotificationRecord existingRecord = existing.get();
                log.info("[NOTIFICATION] Duplicate detected for key '{}' on channel {}. Returning existing record ID: {}",
                        idempotencyKey, channel, existingRecord.getId());
                return existingRecord;
            }
        }

        String recipient = event.recipients().get(channel);

        // 2. Validate recipient existence
        if (recipient == null || recipient.isBlank()) {
            NotificationRecord failedRecord = new NotificationRecord(
                    UUID.randomUUID(), tenantId, event.eventType(), event.referenceId(),
                    "", channel, event.priority(), null, null, null,
                    idempotencyKey, 3, event.timestamp()
            );
            failedRecord.markFailedImmediately("Missing recipient destination for channel " + channel);
            return recordRepository.save(failedRecord);
        }

        // 3. Channel-specific recipient format validation
        try {
            recipientValidator.validate(channel, recipient);
        } catch (InvalidRecipientException ex) {
            log.warn("[NOTIFICATION] Recipient validation failed for channel {}: {}", channel, ex.getMessage());
            NotificationRecord failedRecord = new NotificationRecord(
                    UUID.randomUUID(), tenantId, event.eventType(), event.referenceId(),
                    recipient, channel, event.priority(), null, null, null,
                    idempotencyKey, 3, event.timestamp()
            );
            failedRecord.markFailedImmediately(ex.getMessage());
            return recordRepository.save(failedRecord);
        }

        // 4. Template resolution and variable rendering
        NotificationTemplate template;
        NotificationTemplate.RenderedContent rendered;
        try {
            template = templateRegistry.getTemplateOrThrow(event.eventType(), channel);
            rendered = template.render(event.payload());
        } catch (TemplateNotFoundException | TemplateRenderingException ex) {
            log.error("[NOTIFICATION] Template resolution/render failed for event '{}' on channel {}: {}",
                    event.eventType(), channel, ex.getMessage());
            NotificationRecord failedRecord = new NotificationRecord(
                    UUID.randomUUID(), tenantId, event.eventType(), event.referenceId(),
                    recipient, channel, event.priority(), null, null, null,
                    idempotencyKey, 3, event.timestamp()
            );
            failedRecord.markFailedImmediately(ex.getMessage());
            return recordRepository.save(failedRecord);
        }

        // 5. Initialize NotificationRecord
        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID(), tenantId, event.eventType(), event.referenceId(),
                recipient, channel, event.priority(), template.getTemplateCode(),
                rendered.subject(), rendered.body(), idempotencyKey, 3, event.timestamp()
        );
        recordRepository.save(record);

        // 6. Provider Dispatch
        executeProviderDelivery(record, event.payload());
        return recordRepository.save(record);
    }

    private void executeProviderDelivery(NotificationRecord record, Map<String, Object> payload) {
        ProviderDeliveryResult result;
        try {
            result = switch (record.getChannel()) {
                case PUSH -> {
                    Map<String, String> data = new HashMap<>();
                    if (payload != null) {
                        payload.forEach((k, v) -> data.put(k, String.valueOf(v)));
                    }
                    data.put("notificationId", record.getId().toString());
                    data.put("eventType", record.getEventType().getCode());
                    yield pushProvider.sendPush(record.getRecipient(), record.getRenderedSubject(), record.getRenderedContent(), data);
                }
                case SMS -> smsProvider.sendSms(record.getRecipient(), record.getRenderedContent());
                case EMAIL -> emailProvider.sendEmail(record.getRecipient(), record.getRenderedSubject(), record.getRenderedContent());
                case WHATSAPP -> whatsAppProvider.sendWhatsApp(record.getRecipient(), record.getRenderedContent());
            };
        } catch (Exception ex) {
            log.error("[NOTIFICATION] Unexpected error invoking provider for channel {}: {}", record.getChannel(), ex.getMessage(), ex);
            result = ProviderDeliveryResult.failure("Unexpected provider exception: " + ex.getMessage());
        }

        if (result.success()) {
            record.recordAttempt(NotificationStatus.SENT, null, result.providerMessageId());
            log.info("[NOTIFICATION] Delivered {} message to {} (Status: SENT, msgId: {})",
                    record.getChannel(), record.getRecipient(), result.providerMessageId());
        } else {
            record.recordAttempt(NotificationStatus.FAILED, result.errorMessage(), null);
            log.warn("[NOTIFICATION] Delivery attempt {} failed for {} to {}: {}",
                    record.getAttemptCount(), record.getChannel(), record.getRecipient(), result.errorMessage());
        }
    }

    @Override
    public NotificationRecord retryNotification(UUID notificationId) {
        if (notificationId == null) {
            throw new IllegalArgumentException("Notification ID cannot be null");
        }

        NotificationRecord record = recordRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification record not found: " + notificationId));

        if (record.getStatus() == NotificationStatus.SENT) {
            log.info("[NOTIFICATION] Notification {} is already SENT. Skipping retry.", notificationId);
            return record;
        }

        if (record.getAttemptCount() >= record.getMaxAttempts()) {
            throw new BusinessRuleViolationException(String.format(
                    "Cannot retry notification %s: maximum retry attempts (%d) exceeded",
                    notificationId, record.getMaxAttempts()));
        }

        executeProviderDelivery(record, Collections.emptyMap());
        return recordRepository.save(record);
    }

    @Override
    public Optional<NotificationRecord> getNotificationById(UUID notificationId) {
        return recordRepository.findById(notificationId);
    }

    @Override
    public List<NotificationRecord> getNotificationsByTenant(UUID tenantId) {
        return recordRepository.findAllByTenantId(tenantId);
    }

    @Override
    public List<NotificationRecord> getNotificationsByReferenceId(String referenceId) {
        return recordRepository.findAllByReferenceId(referenceId);
    }
}
