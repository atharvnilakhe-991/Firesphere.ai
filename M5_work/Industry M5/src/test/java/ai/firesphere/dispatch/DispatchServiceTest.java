package ai.firesphere.dispatch;

import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.dto.*;
import ai.firesphere.dispatch.model.DispatchOrder;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.dispatch.model.DispatchStatusHistory;
import ai.firesphere.dispatch.repository.DispatchOrderRepository;
import ai.firesphere.dispatch.repository.DispatchStatusHistoryRepository;
import ai.firesphere.dispatch.service.DispatchServiceImpl;
import ai.firesphere.incident.dto.IncidentResponseDTO;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.incident.service.IncidentService;
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
class DispatchServiceTest {

    @Mock
    private DispatchOrderRepository dispatchOrderRepository;

    @Mock
    private DispatchStatusHistoryRepository dispatchStatusHistoryRepository;

    @Mock
    private IncidentService incidentService;

    @InjectMocks
    private DispatchServiceImpl dispatchService;

    private final UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID incidentId = UUID.randomUUID();
    private final UUID stationId = UUID.randomUUID();
    private final UUID vehicleId1 = UUID.randomUUID();
    private final UUID vehicleId2 = UUID.randomUUID();
    private final UUID responderId1 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private IncidentResponseDTO mockIncident(IncidentStatus status) {
        return new IncidentResponseDTO(
                incidentId,
                "INC-20261005-001",
                "Warehouse Fire",
                "Commercial structure smoke",
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.HIGH,
                status,
                19.0760,
                72.8777,
                "Industrial Area 4",
                "Watchman",
                "+919876543210",
                stationId,
                List.of(),
                Instant.now(),
                Instant.now(),
                null, null, null, null, null, null, null,
                Instant.now(), Instant.now()
        );
    }

    private DispatchOrder createMockOrder(UUID id, DispatchStatus status) {
        DispatchOrder order = new DispatchOrder();
        order.setId(id);
        order.setTenantId(tenantA);
        order.setIncidentId(incidentId);
        order.setStationId(stationId);
        order.setDispatchCode("DSP-20261005-00001");
        order.setStatus(status);
        order.setPriority(DispatchPriority.HIGH);
        order.setAssignedVehicleIds(new ArrayList<>(List.of(vehicleId1)));
        order.setAssignedResponderIds(new ArrayList<>(List.of(responderId1)));
        order.setDispatchedAt(Instant.now());
        order.setOperationalNotes("First alarm turnout");
        return order;
    }

    // -------------------------------------------------------------
    // CREATION TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("createDispatch: success sets tenant, CREATED status, priority, and history")
    void createDispatch_Success() {
        when(incidentService.getIncident(incidentId)).thenReturn(mockIncident(IncidentStatus.VERIFIED));
        when(dispatchOrderRepository.findAllByIncidentIdAndTenantIdAndStatusIn(eq(incidentId), eq(tenantA), any()))
                .thenReturn(List.of());
        when(dispatchOrderRepository.existsByDispatchCode(anyString())).thenReturn(false);
        when(dispatchOrderRepository.save(any(DispatchOrder.class))).thenAnswer(inv -> {
            DispatchOrder o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        DispatchCreateRequest request = new DispatchCreateRequest(
                incidentId,
                stationId,
                DispatchPriority.HIGH,
                List.of(vehicleId1),
                List.of(responderId1),
                "Initial turnout dispatch"
        );

        DispatchResponseDTO response = dispatchService.createDispatch(request);

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals(incidentId, response.incidentId());
        assertEquals(stationId, response.stationId());
        assertEquals(DispatchStatus.CREATED, response.status());
        assertEquals(DispatchPriority.HIGH, response.priority());
        assertTrue(response.dispatchCode().startsWith("DSP-"));

        verify(dispatchStatusHistoryRepository, times(1)).save(any(DispatchStatusHistory.class));
    }

    @Test
    @DisplayName("createDispatch: fails when incident does not exist in current tenant")
    void createDispatch_IncidentNotFound_Throws404() {
        when(incidentService.getIncident(incidentId))
                .thenThrow(new ResourceNotFoundException("Incident not found: " + incidentId));

        DispatchCreateRequest request = new DispatchCreateRequest(
                incidentId,
                stationId,
                DispatchPriority.HIGH,
                List.of(vehicleId1),
                List.of(responderId1),
                "Turnout"
        );

        assertThrows(ResourceNotFoundException.class, () -> dispatchService.createDispatch(request));
        verify(dispatchOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDispatch: fails when incident is CLOSED")
    void createDispatch_ClosedIncident_Throws422() {
        when(incidentService.getIncident(incidentId)).thenReturn(mockIncident(IncidentStatus.CLOSED));

        DispatchCreateRequest request = new DispatchCreateRequest(
                incidentId,
                stationId,
                DispatchPriority.HIGH,
                List.of(vehicleId1),
                List.of(responderId1),
                "Turnout"
        );

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> dispatchService.createDispatch(request)
        );
        assertTrue(ex.getMessage().contains("closed incident"));
        verify(dispatchOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDispatch: duplicate active dispatch for same vehicle rejected with 422")
    void createDispatch_DuplicateActiveVehicle_Throws422() {
        when(incidentService.getIncident(incidentId)).thenReturn(mockIncident(IncidentStatus.VERIFIED));

        DispatchOrder existingActiveOrder = createMockOrder(UUID.randomUUID(), DispatchStatus.DISPATCHED);
        existingActiveOrder.setAssignedVehicleIds(List.of(vehicleId1));

        when(dispatchOrderRepository.findAllByIncidentIdAndTenantIdAndStatusIn(eq(incidentId), eq(tenantA), any()))
                .thenReturn(List.of(existingActiveOrder));

        DispatchCreateRequest request = new DispatchCreateRequest(
                incidentId,
                stationId,
                DispatchPriority.HIGH,
                List.of(vehicleId1), // Duplicate vehicle on active dispatch
                List.of(responderId1),
                "Turnout duplicate"
        );

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> dispatchService.createDispatch(request)
        );
        assertTrue(ex.getMessage().contains("Duplicate active dispatch"));
        verify(dispatchOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDispatch: allows multiple active dispatches for same incident with different vehicles")
    void createDispatch_DifferentVehicles_Success() {
        when(incidentService.getIncident(incidentId)).thenReturn(mockIncident(IncidentStatus.VERIFIED));

        DispatchOrder existingActiveOrder = createMockOrder(UUID.randomUUID(), DispatchStatus.DISPATCHED);
        existingActiveOrder.setAssignedVehicleIds(List.of(vehicleId1));

        when(dispatchOrderRepository.findAllByIncidentIdAndTenantIdAndStatusIn(eq(incidentId), eq(tenantA), any()))
                .thenReturn(List.of(existingActiveOrder));
        when(dispatchOrderRepository.existsByDispatchCode(anyString())).thenReturn(false);
        when(dispatchOrderRepository.save(any(DispatchOrder.class))).thenAnswer(inv -> {
            DispatchOrder o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        // Request with different vehicleId2
        DispatchCreateRequest request = new DispatchCreateRequest(
                incidentId,
                stationId,
                DispatchPriority.HIGH,
                List.of(vehicleId2),
                List.of(responderId1),
                "Reinforcement turnout"
        );

        DispatchResponseDTO response = dispatchService.createDispatch(request);
        assertNotNull(response);
        verify(dispatchOrderRepository, times(1)).save(any());
    }

    // -------------------------------------------------------------
    // RETRIEVAL & FILTERING TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("getDispatch: returns order for current tenant")
    void getDispatch_Success() {
        UUID orderId = UUID.randomUUID();
        DispatchOrder order = createMockOrder(orderId, DispatchStatus.CREATED);
        when(dispatchOrderRepository.findByIdAndTenantId(orderId, tenantA)).thenReturn(Optional.of(order));

        DispatchResponseDTO response = dispatchService.getDispatch(orderId);
        assertNotNull(response);
        assertEquals(orderId, response.id());
    }

    @Test
    @DisplayName("getDispatch: throws 404 when order not found or belongs to another tenant")
    void getDispatch_NotFoundOrCrossTenant_Throws404() {
        UUID orderId = UUID.randomUUID();
        when(dispatchOrderRepository.findByIdAndTenantId(orderId, tenantA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> dispatchService.getDispatch(orderId));
    }

    @Test
    @DisplayName("getDispatches: paginated retrieval with specification")
    void getDispatches_Success() {
        DispatchOrder order = createMockOrder(UUID.randomUUID(), DispatchStatus.DISPATCHED);
        Page<DispatchOrder> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
        when(dispatchOrderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        var response = dispatchService.getDispatches(DispatchStatus.DISPATCHED, DispatchPriority.HIGH, incidentId, stationId, PageRequest.of(0, 10));
        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals(1, response.totalElements());
    }

    // -------------------------------------------------------------
    // UPDATE METADATA TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("updateDispatch: updates mutable notes and priority")
    void updateDispatch_Success() {
        UUID orderId = UUID.randomUUID();
        DispatchOrder order = createMockOrder(orderId, DispatchStatus.CREATED);
        when(dispatchOrderRepository.findByIdAndTenantId(orderId, tenantA)).thenReturn(Optional.of(order));
        when(dispatchOrderRepository.save(any(DispatchOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchUpdateRequest request = new DispatchUpdateRequest(
                DispatchPriority.CRITICAL,
                null,
                null,
                null,
                "Escalated priority"
        );

        DispatchResponseDTO response = dispatchService.updateDispatch(orderId, request);
        assertEquals(DispatchPriority.CRITICAL, response.priority());
        assertEquals("Escalated priority", response.operationalNotes());
    }

    @Test
    @DisplayName("updateDispatch: rejected with 422 when order is in terminal state COMPLETED")
    void updateDispatch_TerminalCompleted_Throws422() {
        UUID orderId = UUID.randomUUID();
        DispatchOrder order = createMockOrder(orderId, DispatchStatus.COMPLETED);
        when(dispatchOrderRepository.findByIdAndTenantId(orderId, tenantA)).thenReturn(Optional.of(order));

        DispatchUpdateRequest request = new DispatchUpdateRequest(
                DispatchPriority.LOW, null, null, null, "Try update"
        );

        assertThrows(BusinessRuleViolationException.class, () -> dispatchService.updateDispatch(orderId, request));
    }

    @Test
    @DisplayName("updateDispatch: rejected with 422 when order is in terminal state CANCELLED")
    void updateDispatch_TerminalCancelled_Throws422() {
        UUID orderId = UUID.randomUUID();
        DispatchOrder order = createMockOrder(orderId, DispatchStatus.CANCELLED);
        when(dispatchOrderRepository.findByIdAndTenantId(orderId, tenantA)).thenReturn(Optional.of(order));

        DispatchUpdateRequest request = new DispatchUpdateRequest(
                DispatchPriority.LOW, null, null, null, "Try update"
        );

        assertThrows(BusinessRuleViolationException.class, () -> dispatchService.updateDispatch(orderId, request));
    }

    // -------------------------------------------------------------
    // LIFECYCLE STATE MACHINE TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("updateDispatchStatus: valid full linear progression")
    void updateDispatchStatus_FullLifecycleProgression() {
        UUID orderId = UUID.randomUUID();
        DispatchOrder order = createMockOrder(orderId, DispatchStatus.CREATED);
        when(dispatchOrderRepository.findByIdAndTenantId(orderId, tenantA)).thenReturn(Optional.of(order));
        when(dispatchOrderRepository.save(any(DispatchOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. CREATED -> DISPATCHED
        DispatchResponseDTO d1 = dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.DISPATCHED, "Transmitted to station"));
        assertEquals(DispatchStatus.DISPATCHED, d1.status());
        assertNotNull(order.getDispatchedAt());

        // 2. DISPATCHED -> ACKNOWLEDGED
        DispatchResponseDTO d2 = dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.ACKNOWLEDGED, "Station crew acknowledged"));
        assertEquals(DispatchStatus.ACKNOWLEDGED, d2.status());
        assertNotNull(order.getAcknowledgedAt());

        // 3. ACKNOWLEDGED -> EN_ROUTE
        DispatchResponseDTO d3 = dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.EN_ROUTE, "Apparatus rolling"));
        assertEquals(DispatchStatus.EN_ROUTE, d3.status());
        assertNotNull(order.getEnRouteAt());

        // 4. EN_ROUTE -> ARRIVED
        DispatchResponseDTO d4 = dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.ARRIVED, "On scene arrival"));
        assertEquals(DispatchStatus.ARRIVED, d4.status());
        assertNotNull(order.getArrivedAt());

        // 5. ARRIVED -> COMPLETED
        DispatchResponseDTO d5 = dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.COMPLETED, "Units returned to station"));
        assertEquals(DispatchStatus.COMPLETED, d5.status());
        assertNotNull(order.getCompletedAt());

        verify(dispatchStatusHistoryRepository, times(5)).save(any());
    }

    @Test
    @DisplayName("updateDispatchStatus: CREATED -> CANCELLED is permitted")
    void updateDispatchStatus_CreatedToCancelled_Permitted() {
        UUID orderId = UUID.randomUUID();
        DispatchOrder order = createMockOrder(orderId, DispatchStatus.CREATED);
        when(dispatchOrderRepository.findByIdAndTenantId(orderId, tenantA)).thenReturn(Optional.of(order));
        when(dispatchOrderRepository.save(any(DispatchOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchResponseDTO response = dispatchService.updateDispatchStatus(
                orderId, new DispatchStatusUpdateRequest(DispatchStatus.CANCELLED, "Call cancelled by caller"));

        assertEquals(DispatchStatus.CANCELLED, response.status());
        assertNotNull(order.getCancelledAt());
        assertEquals("Call cancelled by caller", order.getCancellationReason());
    }

    @Test
    @DisplayName("updateDispatchStatus: invalid transitions rejected with 422")
    void updateDispatchStatus_InvalidTransitions_Throws422() {
        UUID orderId = UUID.randomUUID();
        DispatchOrder order = createMockOrder(orderId, DispatchStatus.CREATED);
        when(dispatchOrderRepository.findByIdAndTenantId(orderId, tenantA)).thenReturn(Optional.of(order));

        // CREATED -> EN_ROUTE must fail
        assertThrows(BusinessRuleViolationException.class,
                () -> dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.EN_ROUTE, "Invalid")));

        // CREATED -> ARRIVED must fail
        assertThrows(BusinessRuleViolationException.class,
                () -> dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.ARRIVED, "Invalid")));

        // CREATED -> COMPLETED must fail
        assertThrows(BusinessRuleViolationException.class,
                () -> dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.COMPLETED, "Invalid")));

        // From ARRIVED -> EN_ROUTE must fail
        order.setStatus(DispatchStatus.ARRIVED);
        assertThrows(BusinessRuleViolationException.class,
                () -> dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.EN_ROUTE, "Invalid backwards")));

        // From COMPLETED -> any state must fail
        order.setStatus(DispatchStatus.COMPLETED);
        assertThrows(BusinessRuleViolationException.class,
                () -> dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.CREATED, "Invalid reopen")));

        // From CANCELLED -> any state must fail
        order.setStatus(DispatchStatus.CANCELLED);
        assertThrows(BusinessRuleViolationException.class,
                () -> dispatchService.updateDispatchStatus(orderId, new DispatchStatusUpdateRequest(DispatchStatus.DISPATCHED, "Invalid revive")));
    }

    // -------------------------------------------------------------
    // HISTORY & RECOMMENDATION TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("getDispatchHistory: returns history chronological entries")
    void getDispatchHistory_Success() {
        UUID orderId = UUID.randomUUID();
        when(dispatchOrderRepository.existsByIdAndTenantId(orderId, tenantA)).thenReturn(true);

        DispatchStatusHistory h1 = new DispatchStatusHistory(orderId, null, DispatchStatus.CREATED, "DISPATCHER", "Created");
        h1.setId(UUID.randomUUID());
        DispatchStatusHistory h2 = new DispatchStatusHistory(orderId, DispatchStatus.CREATED, DispatchStatus.DISPATCHED, "DISPATCHER", "Dispatched");
        h2.setId(UUID.randomUUID());

        when(dispatchStatusHistoryRepository.findAllByDispatchOrderIdAndTenantIdOrderByTransitionedAtAsc(orderId, tenantA))
                .thenReturn(List.of(h1, h2));

        List<DispatchStatusHistoryDTO> history = dispatchService.getDispatchHistory(orderId);
        assertEquals(2, history.size());
        assertEquals(DispatchStatus.CREATED, history.get(0).newStatus());
        assertEquals(DispatchStatus.DISPATCHED, history.get(1).newStatus());
    }

    @Test
    @DisplayName("getRecommendations: deterministic recommendation for CRITICAL severity")
    void getRecommendations_CriticalSeverity_Success() {
        when(incidentService.getIncident(incidentId)).thenReturn(mockIncident(IncidentStatus.VERIFIED));

        DispatchRecommendationRequest request = new DispatchRecommendationRequest(
                incidentId,
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.CRITICAL,
                19.0760,
                72.8777
        );

        DispatchRecommendationResponse response = dispatchService.getRecommendations(request);

        assertNotNull(response);
        assertEquals(incidentId, response.incidentId());
        assertEquals(14, response.recommendedResponderCount());
        assertTrue(response.rationale().contains("RECOMMENDATION ONLY"));
        assertFalse(response.recommendedUnits().isEmpty());
    }
}
