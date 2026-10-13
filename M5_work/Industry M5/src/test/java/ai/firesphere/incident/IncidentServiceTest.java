package ai.firesphere.incident;

import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.incident.dto.*;
import ai.firesphere.incident.model.*;
import ai.firesphere.incident.repository.IncidentRepository;
import ai.firesphere.incident.repository.IncidentStatusHistoryRepository;
import ai.firesphere.incident.service.IncidentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentStatusHistoryRepository historyRepository;

    @InjectMocks
    private IncidentServiceImpl incidentService;

    private final UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID incidentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------
    // CREATION TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("Create incident with valid data succeeds and sets REPORTED status")
    void createIncident_ValidRequest_Success() {
        IncidentCreateRequest request = new IncidentCreateRequest(
                "Factory Chemical Fire",
                "Solvent tank explosion on loading dock",
                IncidentType.INDUSTRIAL_HAZARD,
                IncidentSeverity.CRITICAL,
                19.0760,
                72.8777,
                "Plot 42, MIDC Industrial Area, Mumbai",
                "Security Chief Sharma",
                "+919876543210",
                UUID.randomUUID(),
                List.of("HAZMAT-1", "ENGINE-4")
        );

        when(incidentRepository.existsByIncidentNumber(anyString())).thenReturn(false);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            inc.setId(incidentId);
            return inc;
        });

        IncidentResponseDTO response = incidentService.createIncident(request);

        assertNotNull(response);
        assertEquals(incidentId, response.id());
        assertEquals("Factory Chemical Fire", response.title());
        assertEquals(IncidentType.INDUSTRIAL_HAZARD, response.incidentType());
        assertEquals(IncidentSeverity.CRITICAL, response.severity());
        assertEquals(IncidentStatus.REPORTED, response.status());
        assertEquals(19.0760, response.latitude());
        assertEquals(72.8777, response.longitude());
        assertEquals(2, response.assignedResources().size());
        assertNotNull(response.reportedAt());

        // Verifies status history is recorded
        verify(historyRepository, times(1)).save(any(IncidentStatusHistory.class));
    }

    @Test
    @DisplayName("Create incident fails if coordinates are out of valid range")
    void createIncident_InvalidCoordinates_ThrowsException() {
        // Invalid latitude > 90
        IncidentCreateRequest badLat = new IncidentCreateRequest(
                "Fire", "Desc", IncidentType.STRUCTURAL_FIRE, IncidentSeverity.HIGH,
                95.0, 72.8777, "Address", null, null, null, null
        );
        assertThrows(BusinessRuleViolationException.class, () -> incidentService.createIncident(badLat));

        // Invalid longitude < -180
        IncidentCreateRequest badLon = new IncidentCreateRequest(
                "Fire", "Desc", IncidentType.STRUCTURAL_FIRE, IncidentSeverity.HIGH,
                19.0760, -185.0, "Address", null, null, null, null
        );
        assertThrows(BusinessRuleViolationException.class, () -> incidentService.createIncident(badLon));
    }

    // -------------------------------------------------------------
    // RETRIEVAL TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("Get incident by ID succeeds for existing incident in tenant")
    void getIncident_ExistingId_Success() {
        Incident incident = createSampleIncident(IncidentStatus.REPORTED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        IncidentResponseDTO response = incidentService.getIncident(incidentId);

        assertNotNull(response);
        assertEquals(incidentId, response.id());
        assertEquals("INC-20261005-001", response.incidentNumber());
    }

    @Test
    @DisplayName("Get incident by ID throws ResourceNotFoundException if missing")
    void getIncident_NonExistent_ThrowsNotFound() {
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> incidentService.getIncident(incidentId));
    }

    @Test
    @DisplayName("Get incident by number succeeds for existing incident")
    void getIncidentByNumber_Success() {
        Incident incident = createSampleIncident(IncidentStatus.REPORTED);
        when(incidentRepository.findByIncidentNumberAndTenantId("INC-20261005-001", tenantA))
                .thenReturn(Optional.of(incident));

        IncidentResponseDTO response = incidentService.getIncidentByNumber("INC-20261005-001");
        assertNotNull(response);
        assertEquals("INC-20261005-001", response.incidentNumber());
    }

    @Test
    @DisplayName("List incidents with filtering and pagination returns PageResponse")
    void listIncidents_WithFilters_Success() {
        Incident incident = createSampleIncident(IncidentStatus.VERIFIED);
        Page<Incident> page = new PageImpl<>(List.of(incident), PageRequest.of(0, 10), 1);

        when(incidentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        var response = incidentService.getIncidents(
                IncidentStatus.VERIFIED, IncidentSeverity.HIGH, null, null, null, null, PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertEquals(1, response.totalElements());
        assertEquals(1, response.content().size());
        assertEquals(IncidentStatus.VERIFIED, response.content().getFirst().status());
    }

    // -------------------------------------------------------------
    // UPDATE TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("Update incident attributes succeeds without modifying lifecycle status")
    void updateIncident_Valid_Success() {
        Incident incident = createSampleIncident(IncidentStatus.VERIFIED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentUpdateRequest updateReq = new IncidentUpdateRequest(
                "Updated Commercial Complex Fire",
                "Fire escalated to 3rd floor warehouse area",
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.CRITICAL,
                19.0770,
                72.8780,
                "New Address Line",
                "Commander Roy",
                "+919999888877",
                UUID.randomUUID(),
                List.of("LADDER-1", "ENGINE-2")
        );

        IncidentResponseDTO updated = incidentService.updateIncident(incidentId, updateReq);

        assertNotNull(updated);
        assertEquals("Updated Commercial Complex Fire", updated.title());
        assertEquals("Fire escalated to 3rd floor warehouse area", updated.description());
        assertEquals(IncidentSeverity.CRITICAL, updated.severity());
        assertEquals(19.0770, updated.latitude());
        assertEquals(IncidentStatus.VERIFIED, updated.status()); // Status remains untouched
    }

    @Test
    @DisplayName("Update incident on closed incident fails with BusinessRuleViolationException")
    void updateIncident_ClosedIncident_ThrowsException() {
        Incident closedIncident = createSampleIncident(IncidentStatus.CLOSED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(closedIncident));

        IncidentUpdateRequest updateReq = new IncidentUpdateRequest(
                "Update", "Desc", IncidentType.STRUCTURAL_FIRE, IncidentSeverity.LOW,
                19.0760, 72.8777, "Addr", null, null, null, null
        );

        assertThrows(BusinessRuleViolationException.class, () -> incidentService.updateIncident(incidentId, updateReq));
    }

    // -------------------------------------------------------------
    // LIFECYCLE STATE TRANSITIONS TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("Valid lifecycle progression: REPORTED -> VERIFIED -> DISPATCHED -> EN_ROUTE -> ON_SCENE -> UNDER_CONTROL -> RESOLVED")
    void lifecycle_ValidSequentialProgression_Succeeds() {
        Incident incident = createSampleIncident(IncidentStatus.REPORTED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. REPORTED -> VERIFIED
        IncidentResponseDTO s1 = incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.VERIFIED, "Verified by officer call"));
        assertEquals(IncidentStatus.VERIFIED, s1.status());
        assertNotNull(incident.getVerifiedAt());

        // 2. VERIFIED -> DISPATCHED
        IncidentResponseDTO s2 = incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.DISPATCHED, "Station dispatched"));
        assertEquals(IncidentStatus.DISPATCHED, s2.status());
        assertNotNull(incident.getDispatchedAt());

        // 3. DISPATCHED -> EN_ROUTE
        IncidentResponseDTO s3 = incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.EN_ROUTE, "Units rolling out"));
        assertEquals(IncidentStatus.EN_ROUTE, s3.status());
        assertNotNull(incident.getEnRouteAt());

        // 4. EN_ROUTE -> ON_SCENE
        IncidentResponseDTO s4 = incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.ON_SCENE, "Units arrived"));
        assertEquals(IncidentStatus.ON_SCENE, s4.status());
        assertNotNull(incident.getOnSceneAt());

        // 5. ON_SCENE -> UNDER_CONTROL
        IncidentResponseDTO s5 = incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.UNDER_CONTROL, "Perimeter foam laid"));
        assertEquals(IncidentStatus.UNDER_CONTROL, s5.status());
        assertNotNull(incident.getUnderControlAt());

        // 6. UNDER_CONTROL -> RESOLVED
        IncidentResponseDTO s6 = incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED, "Blaze extinguished"));
        assertEquals(IncidentStatus.RESOLVED, s6.status());
        assertNotNull(incident.getResolvedAt());
    }

    @Test
    @DisplayName("Invalid transition: REPORTED -> ON_SCENE must fail")
    void lifecycle_ReportedToOnScene_Fails() {
        Incident incident = createSampleIncident(IncidentStatus.REPORTED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        assertThrows(BusinessRuleViolationException.class, () ->
                incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.ON_SCENE, "Skip steps")));
    }

    @Test
    @DisplayName("Invalid transition: REPORTED -> CLOSED must fail")
    void lifecycle_ReportedToClosed_Fails() {
        Incident incident = createSampleIncident(IncidentStatus.REPORTED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        assertThrows(BusinessRuleViolationException.class, () ->
                incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.CLOSED, "Direct close")));
    }

    @Test
    @DisplayName("Invalid transition: VERIFIED -> CLOSED must fail")
    void lifecycle_VerifiedToClosed_Fails() {
        Incident incident = createSampleIncident(IncidentStatus.VERIFIED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        assertThrows(BusinessRuleViolationException.class, () ->
                incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.CLOSED, "Close verified")));
    }

    @Test
    @DisplayName("Invalid transition: CLOSED -> REPORTED must fail")
    void lifecycle_ClosedToReported_Fails() {
        Incident incident = createSampleIncident(IncidentStatus.CLOSED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        assertThrows(BusinessRuleViolationException.class, () ->
                incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.REPORTED, "Reopen")));
    }

    @Test
    @DisplayName("Invalid transition: CLOSED -> VERIFIED must fail")
    void lifecycle_ClosedToVerified_Fails() {
        Incident incident = createSampleIncident(IncidentStatus.CLOSED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        assertThrows(BusinessRuleViolationException.class, () ->
                incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.VERIFIED, "Reopen")));
    }

    // -------------------------------------------------------------
    // CLOSURE TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("Close incident from RESOLVED with remarks succeeds")
    void closeIncident_FromResolved_Success() {
        Incident incident = createSampleIncident(IncidentStatus.RESOLVED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentCloseRequest closeReq = new IncidentCloseRequest("Fire fully damped down, area cleared, no casualties");
        IncidentResponseDTO closed = incidentService.closeIncident(incidentId, closeReq);

        assertEquals(IncidentStatus.CLOSED, closed.status());
        assertNotNull(incident.getClosedAt());
        assertEquals("Fire fully damped down, area cleared, no casualties", incident.getClosureRemarks());
    }

    @Test
    @DisplayName("Close incident fails if current status is not RESOLVED")
    void closeIncident_FromReportedOrOnScene_Fails() {
        Incident incident = createSampleIncident(IncidentStatus.ON_SCENE);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        IncidentCloseRequest closeReq = new IncidentCloseRequest("Premature close");
        assertThrows(BusinessRuleViolationException.class, () -> incidentService.closeIncident(incidentId, closeReq));
    }

    @Test
    @DisplayName("Close incident fails if already closed (duplicate close)")
    void closeIncident_AlreadyClosed_Fails() {
        Incident incident = createSampleIncident(IncidentStatus.CLOSED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        IncidentCloseRequest closeReq = new IncidentCloseRequest("Duplicate close");
        assertThrows(BusinessRuleViolationException.class, () -> incidentService.closeIncident(incidentId, closeReq));
    }

    @Test
    @DisplayName("Close incident fails if closure remarks are empty")
    void closeIncident_MissingRemarks_Fails() {
        Incident incident = createSampleIncident(IncidentStatus.RESOLVED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        IncidentCloseRequest closeReq = new IncidentCloseRequest("   ");
        assertThrows(BusinessRuleViolationException.class, () -> incidentService.closeIncident(incidentId, closeReq));
    }

    // -------------------------------------------------------------
    // TENANT ISOLATION TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("Tenant isolation: Tenant A cannot access Tenant B's incident")
    void tenantIsolation_GetIncidentFromDifferentTenant_Fails() {
        // Active context is Tenant A, but repository lookup is tenant-isolated
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> incidentService.getIncident(incidentId));
    }

    @Test
    @DisplayName("Tenant isolation: Tenant A cannot update Tenant B's incident")
    void tenantIsolation_UpdateIncidentFromDifferentTenant_Fails() {
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.empty());

        IncidentUpdateRequest updateReq = new IncidentUpdateRequest(
                "Title", "Desc", IncidentType.STRUCTURAL_FIRE, IncidentSeverity.LOW,
                19.0760, 72.8777, "Addr", null, null, null, null
        );

        assertThrows(ResourceNotFoundException.class, () -> incidentService.updateIncident(incidentId, updateReq));
    }

    @Test
    @DisplayName("Tenant isolation: Tenant A cannot transition status of Tenant B's incident")
    void tenantIsolation_TransitionStatusFromDifferentTenant_Fails() {
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                incidentService.updateStatus(incidentId, new IncidentStatusUpdateRequest(IncidentStatus.VERIFIED, "Tampering")));
    }

    @Test
    @DisplayName("Tenant isolation: Tenant A cannot close Tenant B's incident")
    void tenantIsolation_CloseIncidentFromDifferentTenant_Fails() {
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                incidentService.closeIncident(incidentId, new IncidentCloseRequest("Tampering close")));
    }

    // -------------------------------------------------------------
    // AUDIT HISTORY TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("Get incident history retrieves chronological transition records")
    void getIncidentHistory_Success() {
        Incident incident = createSampleIncident(IncidentStatus.VERIFIED);
        when(incidentRepository.findByIdAndTenantId(incidentId, tenantA)).thenReturn(Optional.of(incident));

        IncidentStatusHistory h1 = new IncidentStatusHistory(incidentId, null, IncidentStatus.REPORTED, "SYSTEM", "Initial report");
        IncidentStatusHistory h2 = new IncidentStatusHistory(incidentId, IncidentStatus.REPORTED, IncidentStatus.VERIFIED, "OFFICER-1", "Verified");

        when(historyRepository.findAllByIncidentIdAndTenantIdOrderByTransitionedAtAsc(incidentId, tenantA))
                .thenReturn(List.of(h1, h2));

        List<IncidentStatusHistoryDTO> history = incidentService.getIncidentHistory(incidentId);

        assertEquals(2, history.size());
        assertEquals(IncidentStatus.REPORTED, history.get(0).newStatus());
        assertEquals(IncidentStatus.VERIFIED, history.get(1).newStatus());
    }

    private Incident createSampleIncident(IncidentStatus status) {
        Incident incident = new Incident();
        incident.setId(incidentId);
        incident.setTenantId(tenantA);
        incident.setIncidentNumber("INC-20261005-001");
        incident.setTitle("Sample Emergency");
        incident.setDescription("Test description");
        incident.setIncidentType(IncidentType.STRUCTURAL_FIRE);
        incident.setSeverity(IncidentSeverity.HIGH);
        incident.setStatus(status);
        incident.setLatitude(19.0760);
        incident.setLongitude(72.8777);
        incident.setAddress("Sample St, Mumbai");
        incident.setReportedAt(Instant.now());
        if (status == IncidentStatus.CLOSED) {
            incident.setClosedAt(Instant.now());
            incident.setClosureRemarks("Resolved");
        }
        return incident;
    }
}
