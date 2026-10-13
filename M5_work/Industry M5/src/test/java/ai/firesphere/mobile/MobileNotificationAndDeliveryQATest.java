package ai.firesphere.mobile;

import ai.firesphere.notification.dto.AlertBroadcastRequest;
import ai.firesphere.notification.dto.NotificationReceiptDTO;
import ai.firesphere.notification.dto.NotificationRecordResponseDTO;
import ai.firesphere.notification.dto.NotificationSendRequest;
import ai.firesphere.testing.MockSecurityHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mobile QA Suite: Mobile Push Notifications & Turnout Acknowledgment.
 * Validates mobile turnout alerting workflows: FCM/APNs push notification dispatch,
 * officer mobile turnout acknowledgment, idempotent delivery retry handling,
 * and tenant-isolated notification audit histories.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileNotificationAndDeliveryQATest {

    private static final UUID MOBILE_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockSecurityHelper.setupSecurityContext(MOBILE_TENANT_ID, "OF-101", List.of("ROLE_OFFICER"));
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
    }

    @Test
    @DisplayName("Mobile Push: Dispatches turnout notification targeting mobile push channel")
    void testMobilePushNotificationDispatch() throws Exception {
        NotificationSendRequest request = new NotificationSendRequest(
                "DISPATCH",
                "INC-MOB-DISPATCH-01",
                "CRITICAL",
                List.of("PUSH"),
                Map.of("PUSH", "fcm-device-token-officer-101"),
                Map.of(
                        "dispatchCode", "DSP-101",
                        "unitCode", "ENGINE-1",
                        "location", "Apollo Bandar, Colaba"
                ),
                "mob-turnout-idemp-" + UUID.randomUUID()
        );

        MvcResult result = mockMvc.perform(post("/api/v1/notifications/events")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        List<NotificationRecordResponseDTO> records = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        assertThat(records).hasSize(1);
        NotificationRecordResponseDTO record = records.get(0);
        assertThat(record.channel()).isEqualTo("PUSH");
        assertThat(record.recipient()).isEqualTo("fcm-device-token-officer-101");
        assertThat(record.status()).isIn("SENT", "DELIVERED", "DISPATCHED");
    }

    @Test
    @DisplayName("Mobile Acknowledgment: Field officer acknowledges turnout broadcast on mobile device")
    void testMobileTurnoutBroadcastAndAcknowledgment() throws Exception {
        // Step 1: Dispatch broadcast turnout alert to mobile devices
        AlertBroadcastRequest broadcastReq = new AlertBroadcastRequest(
                List.of("mobile-token-crew-01"),
                "TURNOUT_ALARM",
                "Structure Fire - Full Turnout",
                "Apparatus Engine-1, Ladder-2 requested at Sector 4"
        );

        MvcResult broadcastResult = mockMvc.perform(post("/api/v1/notifications/broadcast")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(broadcastReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptId").isNotEmpty())
                .andExpect(jsonPath("$.channel").value("PUSH"))
                .andReturn();

        NotificationReceiptDTO receipt = objectMapper.readValue(
                broadcastResult.getResponse().getContentAsString(),
                NotificationReceiptDTO.class
        );

        // Step 2: Field officer taps "Acknowledge" on mobile device
        mockMvc.perform(post("/api/v1/notifications/receipts/{id}/ack", receipt.receiptId())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("badge", "OF-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value(receipt.receiptId().toString()))
                .andExpect(jsonPath("$.deliveryStatus").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.recipient").value("OF-101"));
    }

    @Test
    @DisplayName("Mobile Acknowledgment: Retrying turnout ACK over flaky cellular connection is idempotent")
    void testMobileTurnoutAcknowledgment_IdempotentRetries() throws Exception {
        AlertBroadcastRequest broadcastReq = new AlertBroadcastRequest(
                List.of("mobile-token-crew-02"),
                "TURNOUT_ALARM",
                "Warehouse Fire Alert",
                "Turnout Engine-3"
        );

        MvcResult broadcastResult = mockMvc.perform(post("/api/v1/notifications/broadcast")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(broadcastReq)))
                .andExpect(status().isCreated())
                .andReturn();

        NotificationReceiptDTO receipt = objectMapper.readValue(
                broadcastResult.getResponse().getContentAsString(),
                NotificationReceiptDTO.class
        );

        // First ACK attempt
        mockMvc.perform(post("/api/v1/notifications/receipts/{id}/ack", receipt.receiptId())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("badge", "OF-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("ACKNOWLEDGED"));

        // Second ACK attempt (cellular retransmission)
        mockMvc.perform(post("/api/v1/notifications/receipts/{id}/ack", receipt.receiptId())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("badge", "OF-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("Mobile Network Retry: Notification with idempotencyKey suppresses duplicate dispatches")
    void testMobileBroadcast_IdempotencyKeyDeduplication() throws Exception {
        String idempKey = "mob-net-retry-" + UUID.randomUUID();

        NotificationSendRequest request = new NotificationSendRequest(
                "SYSTEM_ALERT",
                "REF-EVAC-101",
                "HIGH",
                List.of("PUSH"),
                Map.of("PUSH", "mobile-token-incident-lead"),
                Map.of(
                        "alertTitle", "Evacuation Perimeter",
                        "severity", "HIGH",
                        "message", "Evacuate within 500 meters of chemical leak"
                ),
                idempKey
        );

        // Initial submission
        MvcResult firstResult = mockMvc.perform(post("/api/v1/notifications/events")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        List<NotificationRecordResponseDTO> firstRecords = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        // Retried submission with same idempotency key
        MvcResult retryResult = mockMvc.perform(post("/api/v1/notifications/events")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        List<NotificationRecordResponseDTO> retryRecords = objectMapper.readValue(
                retryResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        // Verifies same record returned without duplicate record generation
        assertThat(retryRecords).hasSize(firstRecords.size());
        assertThat(retryRecords.get(0).id()).isEqualTo(firstRecords.get(0).id());
    }

    @Test
    @DisplayName("Mobile Tenant Isolation: Tenant 2 cannot view Tenant 1's notification history")
    void testMobileTenantNotificationHistoryIsolation() throws Exception {
        // Send a notification under Tenant 1
        NotificationSendRequest request = new NotificationSendRequest(
                "SYSTEM_ALERT",
                "REF-STATUS-T1",
                "NORMAL",
                List.of("PUSH"),
                Map.of("PUSH", "mobile-t1-token"),
                Map.of(
                        "alertTitle", "Incident Containment",
                        "severity", "NORMAL",
                        "message", "Incident fire perimeter contained"
                ),
                "mob-t1-key-" + UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/notifications/events")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Switch to Tenant 2
        MockSecurityHelper.setupSecurityContext(OTHER_TENANT_ID, "OF-OTHER", List.of("ROLE_OFFICER"));

        MvcResult result = mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Tenant-Id", OTHER_TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andReturn();

        List<NotificationRecordResponseDTO> tenant2Records = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        // Tenant 2 must see 0 notifications from Tenant 1
        assertThat(tenant2Records).noneMatch(r -> "REF-STATUS-T1".equals(r.eventReferenceId()));
    }
}
