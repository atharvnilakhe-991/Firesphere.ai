package ai.firesphere.security;

import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ErrorResponse;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.gis.foundation.model.LocationValidationResult;
import ai.firesphere.gis.foundation.model.LocationValidationStatus;
import ai.firesphere.gis.foundation.validation.GpsCoordinateValidator;
import ai.firesphere.inventory.dto.InventoryItemCreateRequest;
import ai.firesphere.inventory.dto.StockIssueRequest;
import ai.firesphere.inventory.service.InventoryService;
import ai.firesphere.operations.officer.dto.OfficerTaskCreateRequest;
import ai.firesphere.operations.officer.model.OfficerTaskType;
import ai.firesphere.operations.officer.repository.OfficerTaskRepository;
import ai.firesphere.operations.officer.service.OfficerOperationsService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security QA Suite: Input Validation, Malformed Payloads, and Information Disclosure Protection.
 * Validates rejection of corrupted/malformed inputs, boundary violations, enum integrity,
 * and asserts strict absence of stack traces, server file paths, and credentials in error envelopes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputValidationAndMalformedRequestSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OfficerOperationsService operationsService;

    @Autowired
    private OfficerTaskRepository taskRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private GpsCoordinateValidator gpsCoordinateValidator;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID stationId = UUID.randomUUID();
    private final Instant baseTime = Instant.parse("2026-10-09T10:00:00Z");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "security_qa", List.of("ROLE_ADMIN", "ROLE_DISPATCHER"));
        taskRepository.clear();
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
        TenantContext.clear();
        taskRepository.clear();
    }

    @Test
    @DisplayName("Negative QA: Malformed JSON request body returns HTTP 400 MALFORMED_REQUEST")
    void testMalformedJsonPayloadReturns400() throws Exception {
        String malformedJson = "{ \"taskReference\": \"TSK-BAD\", \"status\": "; // truncated syntax

        MvcResult result = mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                .andReturn();

        assertZeroInformationDisclosure(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Negative QA: Invalid enum constant in payload returns HTTP 400 MALFORMED_REQUEST")
    void testInvalidEnumValueInPayloadReturns400() throws Exception {
        String invalidEnumJson = """
                {
                    "taskReference": "TSK-ENUM-01",
                    "taskType": "NON_EXISTENT_SUPER_TYPE",
                    "title": "Invalid enum test",
                    "assignedOfficerBadge": "OF-101",
                    "priority": "HIGH",
                    "scheduledAt": "2026-10-09T10:00:00Z",
                    "deadlineAt": "2026-10-09T12:00:00Z"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEnumJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andReturn();

        assertZeroInformationDisclosure(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Negative QA: Unsupported media type (e.g. text/plain) returns HTTP 415 UNSUPPORTED_MEDIA_TYPE")
    void testUnsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("This is plain text, not JSON"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("Negative QA: Malformed UUID in path parameter returns HTTP 400 INVALID_PARAMETER")
    void testMalformedUuidInPathVariableReturns400() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/officer-operations/tasks/not-a-valid-uuid-string")
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.status").value(400))
                .andReturn();

        assertZeroInformationDisclosure(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Negative QA: Missing required fields in DTO returns HTTP 400 VALIDATION_FAILED with field errors")
    void testMissingRequiredFieldsValidationErrors() throws Exception {
        String incompletePayload = """
                {
                    "title": ""
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompletePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andReturn();

        assertZeroInformationDisclosure(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Input Validation: Negative and zero quantities in stock issue are rejected")
    void testNegativeAndZeroQuantitiesRejected() {
        var item = inventoryService.registerItem(new InventoryItemCreateRequest(
                "SKU-QTY-" + UUID.randomUUID().toString().substring(0, 4),
                "Hose Nozzle", "EQUIPMENT", stationId, 20, 5, "PIECES", "Water delivery"
        ));

        // Negative quantity
        assertThatThrownBy(() -> inventoryService.issueStock(
                item.id(), new StockIssueRequest(item.id(), -5, UUID.randomUUID(), "Site", "Invalid negative issue")
        )).isInstanceOf(Exception.class);

        // Zero quantity
        assertThatThrownBy(() -> inventoryService.issueStock(
                item.id(), new StockIssueRequest(item.id(), 0, UUID.randomUUID(), "Site", "Invalid zero issue")
        )).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Input Validation: NaN, Infinity, and out-of-bounds GPS coordinates are cleanly rejected without fabrication")
    void testInvalidCoordinatesRejected() {
        // Latitude out of bounds (> 90.0)
        LocationValidationResult latHigh = gpsCoordinateValidator.validate(90.0001, 72.877, 10.0);
        assertThat(latHigh.valid()).isFalse();
        assertThat(latHigh.status()).isEqualTo(LocationValidationStatus.INVALID);

        // Latitude out of bounds (< -90.0)
        LocationValidationResult latLow = gpsCoordinateValidator.validate(-90.5, 72.877, 10.0);
        assertThat(latLow.valid()).isFalse();
        assertThat(latLow.status()).isEqualTo(LocationValidationStatus.INVALID);

        // Longitude out of bounds (> 180.0)
        LocationValidationResult lonHigh = gpsCoordinateValidator.validate(19.076, 180.1, 10.0);
        assertThat(lonHigh.valid()).isFalse();
        assertThat(lonHigh.status()).isEqualTo(LocationValidationStatus.INVALID);

        // NaN coordinates
        LocationValidationResult nanCoord = gpsCoordinateValidator.validate(Double.NaN, 72.877, 10.0);
        assertThat(nanCoord.valid()).isFalse();
        assertThat(nanCoord.status()).isEqualTo(LocationValidationStatus.INVALID);

        // Infinite coordinates
        LocationValidationResult infCoord = gpsCoordinateValidator.validate(19.076, Double.POSITIVE_INFINITY, 10.0);
        assertThat(infCoord.valid()).isFalse();
        assertThat(infCoord.status()).isEqualTo(LocationValidationStatus.INVALID);
    }

    @Test
    @DisplayName("Input Validation: Scheduled date after deadline in task creation is rejected")
    void testScheduledDateAfterDeadlineRejected() {
        OfficerTaskCreateRequest invalidDatesReq = new OfficerTaskCreateRequest(
                "TSK-TIME-INVERTED", OfficerTaskType.INSPECTION, "Inverted Schedule", "Desc", "OF-101", null,
                DispatchPriority.HIGH,
                baseTime.plus(Duration.ofHours(5)), // scheduled at +5h
                baseTime.plus(Duration.ofHours(1))  // deadline at +1h (earlier than scheduled!)
        );

        assertThatThrownBy(() -> operationsService.createTask(invalidDatesReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be after deadlineAt");
    }

    @Test
    @DisplayName("Input Validation: Duplicate task reference within tenant is rejected with conflict")
    void testDuplicateTaskReferenceRejected() {
        OfficerTaskCreateRequest req = new OfficerTaskCreateRequest(
                "TSK-DUP-REF", OfficerTaskType.INSPECTION, "First Task", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        );
        operationsService.createTask(req);

        // Attempting to create another task with identical taskReference throws ConflictException
        assertThatThrownBy(() -> operationsService.createTask(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    private void assertZeroInformationDisclosure(String responseBody) {
        assertThat(responseBody).isNotBlank();
        assertThat(responseBody).doesNotContain("at ai.firesphere");
        assertThat(responseBody).doesNotContain(".java:");
        assertThat(responseBody).doesNotContain("org.springframework");
        assertThat(responseBody).doesNotContain("C:\\");
        assertThat(responseBody).doesNotContain("/home/");
        assertThat(responseBody).doesNotContain("password");
        assertThat(responseBody).doesNotContain("secret");
    }
}
