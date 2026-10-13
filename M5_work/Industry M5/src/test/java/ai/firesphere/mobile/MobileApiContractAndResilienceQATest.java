package ai.firesphere.mobile;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.incident.dto.IncidentCreateRequest;
import ai.firesphere.incident.dto.IncidentResponseDTO;
import ai.firesphere.incident.dto.IncidentStatusUpdateRequest;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.incident.service.IncidentService;
import ai.firesphere.testing.MockSecurityHelper;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mobile QA Suite: Mobile API Contracts, Response Envelopes & Network Resilience.
 * Validates mobile client envelope contracts: lightweight pagination (PageResponse),
 * predictable error responses (ErrorResponse), strict sequential state transitions,
 * malformed payload rejection, unsupported media types, and information disclosure protection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileApiContractAndResilienceQATest {

    private static final UUID MOBILE_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        MockSecurityHelper.setupSecurityContext(MOBILE_TENANT_ID, "OF-101", List.of("ROLE_OFFICER", "ROLE_DISPATCHER"));
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
    }

    @Test
    @DisplayName("Mobile Contract: Paginated endpoints return standard lightweight PageResponse envelope")
    void testMobilePageResponseEnvelopeStructure() throws Exception {
        IncidentCreateRequest req = new IncidentCreateRequest(
                "Mobile Structure Fire",
                "Apparatus dispatch in progress",
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.HIGH,
                19.0760,
                72.8777,
                "Colaba Causeway, Mumbai",
                "Duty Officer",
                "+919876543210",
                UUID.randomUUID(),
                List.of("ENGINE-1")
        );
        incidentService.createIncident(req);

        mockMvc.perform(get("/api/v1/incidents")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.last").isBoolean());
    }

    @Test
    @DisplayName("Mobile Contract: Requesting out-of-range page index returns empty content with last=true")
    void testMobilePaginationEmptyPageHandling() throws Exception {
        mockMvc.perform(get("/api/v1/incidents")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("page", "999")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("Mobile Contract: Error responses conform to standardized mobile ErrorResponse envelope")
    void testMobileErrorResponseEnvelopeFormat() throws Exception {
        IncidentCreateRequest req = new IncidentCreateRequest(
                "Transformer Explosion",
                "Sparks reported near sub-station",
                IncidentType.ELECTRICAL_FIRE,
                IncidentSeverity.MEDIUM,
                19.0760,
                72.8777,
                "Worli Sea Face, Mumbai",
                "Inspector Kadam",
                "+919876543210",
                UUID.randomUUID(),
                List.of("RESCUE-1")
        );
        IncidentResponseDTO created = incidentService.createIncident(req);

        // Mobile client attempts illegal state transition (REPORTED -> RESOLVED, skipping verification & dispatch)
        IncidentStatusUpdateRequest invalidTransition = new IncidentStatusUpdateRequest(
                IncidentStatus.RESOLVED,
                "Attempting direct resolution from reported"
        );

        mockMvc.perform(post("/api/v1/incidents/{id}/status", created.id())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTransition)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/incidents/" + created.id() + "/status"));
    }

    @Test
    @DisplayName("Mobile Contract: Unsupported Content-Type returns HTTP 415 UNSUPPORTED_MEDIA_TYPE")
    void testMobileUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("taskReference=TSK-01&title=Test"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("Mobile Contract: Malformed JSON from network drop returns HTTP 400 MALFORMED_REQUEST")
    void testMobileMalformedJsonSyntax() throws Exception {
        String truncatedJson = "{\"taskReference\": \"TSK-TRUNC\", \"title\":";

        mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(truncatedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Mobile Contract: Invalid UUID path parameter returns HTTP 400 INVALID_PARAMETER")
    void testMobileInvalidUuidPathParameter() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/{id}", "not-a-valid-uuid-token")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Mobile Tenant Isolation: Tenant 2 cannot access Tenant 1 incident on mobile endpoints")
    void testMobileCrossTenantIsolationOnMobileEndpoints() throws Exception {
        IncidentCreateRequest req = new IncidentCreateRequest(
                "Tenant 1 Exclusive Incident",
                "High-rise commercial structure",
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.CRITICAL,
                19.0760,
                72.8777,
                "BKC Complex, Mumbai",
                "Capt. Sawant",
                "+919876543210",
                UUID.randomUUID(),
                List.of("ENGINE-1")
        );
        IncidentResponseDTO created = incidentService.createIncident(req);

        MockSecurityHelper.setupSecurityContext(OTHER_TENANT_ID, "OF-OTHER", List.of("ROLE_OFFICER"));

        mockMvc.perform(get("/api/v1/incidents/{id}", created.id())
                        .header("X-Tenant-Id", OTHER_TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Mobile Security: Error envelopes strictly disclose zero stack traces or system paths")
    void testMobileInformationDisclosureDefense() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/incidents/{id}", UUID.randomUUID())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andReturn();

        String body = result.getResponse().getContentAsString();

        assertThat(body).doesNotContain("Exception:");
        assertThat(body).doesNotContain("at ai.firesphere");
        assertThat(body).doesNotContain(".java:");
        assertThat(body).doesNotContain("C:\\");
        assertThat(body).doesNotContain("/home/");
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("secret");
    }

    @Test
    @DisplayName("Mobile Incident Workflow: Strict sequential status transition (REPORTED -> VERIFIED -> DISPATCHED -> EN_ROUTE)")
    void testMobileIncidentLifecycleSequentialTransition() throws Exception {
        IncidentCreateRequest req = new IncidentCreateRequest(
                "Residential Fire Alarm",
                "Kitchen grease fire spreading to balcony",
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.HIGH,
                19.0760,
                72.8777,
                "Dadar West, Mumbai",
                "Resident Aniket",
                "+919876543210",
                UUID.randomUUID(),
                List.of("ENGINE-2")
        );
        IncidentResponseDTO created = incidentService.createIncident(req);
        assertThat(created.status()).isEqualTo(IncidentStatus.REPORTED);

        // Step 1: VERIFIED
        mockMvc.perform(post("/api/v1/incidents/{id}/status", created.id())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IncidentStatusUpdateRequest(
                                IncidentStatus.VERIFIED,
                                "Verified by local unit callback"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        // Step 2: DISPATCHED
        mockMvc.perform(post("/api/v1/incidents/{id}/status", created.id())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IncidentStatusUpdateRequest(
                                IncidentStatus.DISPATCHED,
                                "Turnout orders issued to Station 3"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        // Step 3: EN_ROUTE
        mockMvc.perform(post("/api/v1/incidents/{id}/status", created.id())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IncidentStatusUpdateRequest(
                                IncidentStatus.EN_ROUTE,
                                "Apparatus departed station bay"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_ROUTE"));
    }
}
