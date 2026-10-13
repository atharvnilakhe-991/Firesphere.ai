package ai.firesphere.notification;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.dto.AlertBroadcastRequest;
import ai.firesphere.notification.dto.NotificationSendRequest;
import ai.firesphere.notification.event.NotificationEventType;
import ai.firesphere.notification.model.NotificationPriority;
import ai.firesphere.notification.model.NotificationRecord;
import ai.firesphere.notification.model.NotificationStatus;
import ai.firesphere.notification.service.NotificationOrchestrationService;
import ai.firesphere.notification.service.NotificationService;
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

import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/notifications/events dispatches notification and returns 201 CREATED")
    void testPublishEventEndpoint() throws Exception {
        NotificationSendRequest request = new NotificationSendRequest(
                "incident_assignment",
                "INC-REST-01",
                "HIGH",
                List.of("PUSH", "SMS"),
                Map.of(
                        "PUSH", "device-token-rest-officer",
                        "SMS", "+14155552671"
                ),
                Map.of(
                        "incidentNumber", "INC-REST-01",
                        "address", "Market Street & 4th",
                        "severity", "HIGH"
                ),
                "IDEMP-REST-01"
        );

        mockMvc.perform(post("/api/v1/notifications/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status", everyItem(is("SENT"))))
                .andExpect(jsonPath("$[*].eventReferenceId", everyItem(is("INC-REST-01"))));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{id} returns 200 OK for existing notification")
    void testGetNotificationByIdEndpoint() throws Exception {
        NotificationSendRequest request = new NotificationSendRequest(
                "vehicle_alert",
                "VEH-999",
                "NORMAL",
                List.of("SMS"),
                Map.of("SMS", "+14155552671"),
                Map.of("vehicleCode", "TK-09", "alertType", "TIRE_PRESSURE", "details", "Low psi"),
                UUID.randomUUID().toString()
        );

        String responseStr = mockMvc.perform(post("/api/v1/notifications/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        List<?> list = objectMapper.readValue(responseStr, List.class);
        Map<?, ?> first = (Map<?, ?>) list.get(0);
        String idStr = (String) first.get("id");

        mockMvc.perform(get("/api/v1/notifications/" + idStr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(idStr)))
                .andExpect(jsonPath("$.eventType", is("vehicle_alert")))
                .andExpect(jsonPath("$.status", is("SENT")));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{id} returns 404 NOT FOUND for nonexistent ID")
    void testGetNonexistentNotification() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/notifications/broadcast returns 201 CREATED for valid legacy broadcast")
    void testBroadcastAlertEndpoint() throws Exception {
        AlertBroadcastRequest request = new AlertBroadcastRequest(
                List.of("officer-device-token-1", "officer-device-token-2"),
                "EVACUATION_ORDER",
                "District Evacuation Alert",
                "Immediate evacuation ordered for Sector 4"
        );

        mockMvc.perform(post("/api/v1/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptId").isNotEmpty())
                .andExpect(jsonPath("$.deliveryStatus", is("SENT")));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/receipts/{id}/ack returns 200 OK with officer acknowledgment")
    void testAcknowledgeReceiptEndpoint() throws Exception {
        UUID receiptId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/notifications/receipts/" + receiptId + "/ack")
                        .param("badge", "BADGE-501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId", is(receiptId.toString())))
                .andExpect(jsonPath("$.deliveryStatus", is("ACKNOWLEDGED")))
                .andExpect(jsonPath("$.recipient", is("BADGE-501")));
    }
}
