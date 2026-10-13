package ai.firesphere.integration.api.email;

import java.time.Instant;

public record EmailMessageResult(
        String externalMessageId,
        String recipientEmail,
        String status, // SENT, QUEUED, REJECTED, FAILED
        Instant sentAt
) {}
