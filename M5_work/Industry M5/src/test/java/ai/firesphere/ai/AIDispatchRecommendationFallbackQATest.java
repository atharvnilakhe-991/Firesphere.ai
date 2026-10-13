package ai.firesphere.ai;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.dto.DispatchRecommendationRequest;
import ai.firesphere.dispatch.repository.DispatchOrderRepository;
import ai.firesphere.fleet.repository.VehicleRepository;
import ai.firesphere.incident.dto.IncidentCreateRequest;
import ai.firesphere.incident.dto.IncidentResponseDTO;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.incident.service.IncidentService;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI QA Suite: Deterministic Dispatch Recommendation Sizing Engine.
 * Validates fallback apparatus and crew recommendation logic when Member 4's
 * real-time AI dispatch optimization models are absent/unavailable.
 * Ensures recommendations are treated as purely advisory (RECOMMENDATION ONLY)
 * and do not automatically mutate dispatch orders or vehicle readiness.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AIDispatchRecommendationFallbackQATest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private DispatchOrderRepository dispatchOrderRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private UUID testIncidentId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
        IncidentCreateRequest incidentRequest = new IncidentCreateRequest(
                "AI Advisory Incident",
                "Incident created for AI recommendation evaluation",
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.CRITICAL,
                19.0760,
                72.8777,
                "Sector 4, Central District",
                "Commander Davis",
                "+919876543210",
                UUID.randomUUID(),
                List.of()
        );
        IncidentResponseDTO incident = incidentService.createIncident(incidentRequest);
        testIncidentId = incident.id();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("AI Fallback Sizing: Critical structural fire produces multi-apparatus turnout recommendations")
    void testDeterministicTurnoutSizing_CriticalIncidentProfile() throws Exception {
        DispatchRecommendationRequest request = new DispatchRecommendationRequest(
                testIncidentId,
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.CRITICAL,
                19.0760,
                72.8777
        );

        mockMvc.perform(post("/api/v1/dispatch/recommendations")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(testIncidentId.toString()))
                .andExpect(jsonPath("$.recommendedUnits").isArray())
                .andExpect(jsonPath("$.recommendedResponderCount").isNumber())
                .andExpect(jsonPath("$.rationale").value(containsString("RECOMMENDATION ONLY")))
                .andExpect(jsonPath("$.rationale").value(containsString("Deterministic turnout sizing based on severity")));
    }

    @Test
    @DisplayName("AI Fallback Sizing: Low severity incident produces minimal single-unit response")
    void testDeterministicTurnoutSizing_LowSeverityIncidentProfile() throws Exception {
        DispatchRecommendationRequest request = new DispatchRecommendationRequest(
                testIncidentId,
                IncidentType.FALSE_ALARM,
                IncidentSeverity.LOW,
                19.0760,
                72.8777
        );

        mockMvc.perform(post("/api/v1/dispatch/recommendations")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(testIncidentId.toString()))
                .andExpect(jsonPath("$.recommendedResponderCount").value(3))
                .andExpect(jsonPath("$.recommendedUnits[0].vehicleType").value("FIRST_RESPONSE_UNIT"));
    }

    @Test
    @DisplayName("AI Safety: Recommendation calculation is strictly read-only and does not mutate dispatch state")
    void testRecommendationIsPurelyAdvisory_DoesNotMutateState() throws Exception {
        long ordersBefore = dispatchOrderRepository.count();
        long vehiclesBefore = vehicleRepository.count();

        DispatchRecommendationRequest request = new DispatchRecommendationRequest(
                testIncidentId,
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.CRITICAL,
                19.0760,
                72.8777
        );

        mockMvc.perform(post("/api/v1/dispatch/recommendations")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        long ordersAfter = dispatchOrderRepository.count();
        long vehiclesAfter = vehicleRepository.count();

        // State remains strictly unmodified
        assertThat(ordersAfter).isEqualTo(ordersBefore);
        assertThat(vehiclesAfter).isEqualTo(vehiclesBefore);
    }

    @Test
    @DisplayName("AI Input Validation: Missing required incident coordinates or severity rejected with HTTP 400")
    void testRecommendationInputValidation() throws Exception {
        // Missing severity and coordinates
        String invalidPayload = "{\"incidentId\": \"" + testIncidentId + "\", \"incidentType\": \"STRUCTURAL_FIRE\"}";

        mockMvc.perform(post("/api/v1/dispatch/recommendations")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
