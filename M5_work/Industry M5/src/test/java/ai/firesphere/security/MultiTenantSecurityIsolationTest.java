package ai.firesphere.security;

import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.dto.DispatchCreateRequest;
import ai.firesphere.dispatch.dto.DispatchResponseDTO;
import ai.firesphere.dispatch.dto.DispatchStatusUpdateRequest;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.dispatch.service.DispatchService;
import ai.firesphere.fleet.dto.VehicleRegisterRequest;
import ai.firesphere.fleet.dto.VehicleResponseDTO;
import ai.firesphere.fleet.dto.VehicleStatusUpdateRequest;
import ai.firesphere.fleet.dto.VehicleTelematicsUpdateRequest;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.fleet.service.FleetService;
import ai.firesphere.gis.dto.HydrantCreateRequest;
import ai.firesphere.gis.dto.HydrantResponseDTO;
import ai.firesphere.gis.service.GisOperationalService;
import ai.firesphere.incident.dto.IncidentCreateRequest;
import ai.firesphere.incident.dto.IncidentResponseDTO;
import ai.firesphere.incident.dto.IncidentStatusUpdateRequest;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.incident.service.IncidentService;
import ai.firesphere.inventory.dto.*;
import ai.firesphere.inventory.model.StockMovementType;
import ai.firesphere.inventory.service.InventoryService;
import ai.firesphere.operations.officer.dto.OfficerTaskCreateRequest;
import ai.firesphere.operations.officer.dto.OfficerTaskResponseDTO;
import ai.firesphere.operations.officer.dto.OfficerTaskStatusUpdateRequest;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import ai.firesphere.operations.officer.model.OfficerTaskType;
import ai.firesphere.operations.officer.repository.OfficerTaskRepository;
import ai.firesphere.operations.officer.service.OfficerOperationsService;
import ai.firesphere.testing.MockSecurityHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security QA Suite: Comprehensive Multi-Tenant Isolation Verification.
 * Verifies that modifying identifiers, queries, or headers across tenants
 * cannot read, mutate, or disclose data across tenant boundaries.
 */
@SpringBootTest
@ActiveProfiles("test")
class MultiTenantSecurityIsolationTest {

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private FleetService fleetService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OfficerOperationsService officerOperationsService;

    @Autowired
    private OfficerTaskRepository officerTaskRepository;

    @Autowired
    private GisOperationalService gisOperationalService;

    private final UUID tenant1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenant2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID stationId = UUID.randomUUID();
    private final Instant baseTime = Instant.parse("2026-10-09T10:00:00Z");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenant1);
        MockSecurityHelper.setupSecurityContext(tenant1, "admin_t1", List.of("ROLE_ADMIN", "ROLE_DISPATCHER", "ROLE_COMMANDER"));
        officerTaskRepository.clear();
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
        TenantContext.clear();
        officerTaskRepository.clear();
    }

    @Test
    @DisplayName("Tenant Isolation: Incident records created by Tenant 1 are invisible and immutable to Tenant 2")
    void testIncidentCrossTenantIsolation() {
        // Create incident in Tenant 1
        IncidentResponseDTO inc = incidentService.createIncident(new IncidentCreateRequest(
                "T1 Refinery Fire", "Tank explosion", IncidentType.INDUSTRIAL_HAZARD,
                IncidentSeverity.CRITICAL, 19.076, 72.877, "Refinery Sector 4",
                "Commander Khan", "+919988776655", stationId, List.of("ENG-1")
        ));
        UUID incidentId = inc.id();

        // Switch to Tenant 2
        TenantContext.setTenantId(tenant2);
        MockSecurityHelper.setupSecurityContext(tenant2, "admin_t2", List.of("ROLE_ADMIN"));

        // Read attempt blocked
        assertThatThrownBy(() -> incidentService.getIncident(incidentId))
                .isInstanceOf(ResourceNotFoundException.class);

        // Update attempt blocked
        assertThatThrownBy(() -> incidentService.updateStatus(
                incidentId, new IncidentStatusUpdateRequest(IncidentStatus.UNDER_CONTROL, "Unauthorized note")
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant Isolation: Dispatch orders created by Tenant 1 are isolated from Tenant 2")
    void testDispatchCrossTenantIsolation() {
        // Create incident & dispatch in Tenant 1
        IncidentResponseDTO inc = incidentService.createIncident(new IncidentCreateRequest(
                "T1 Substation Fire", "Electrical arc", IncidentType.ELECTRICAL_FIRE,
                IncidentSeverity.HIGH, 19.076, 72.877, "Sector 9",
                "Chief Joshi", "+919988776655", stationId, List.of("ENG-1")
        ));

        DispatchResponseDTO dispatch = dispatchService.createDispatch(new DispatchCreateRequest(
                inc.id(), stationId, DispatchPriority.HIGH, List.of(UUID.randomUUID()), List.of(UUID.randomUUID()), "Turnout"
        ));
        UUID dispatchId = dispatch.id();

        // Switch to Tenant 2
        TenantContext.setTenantId(tenant2);
        MockSecurityHelper.setupSecurityContext(tenant2, "admin_t2", List.of("ROLE_ADMIN"));

        // Read attempt blocked
        assertThatThrownBy(() -> dispatchService.getDispatch(dispatchId))
                .isInstanceOf(ResourceNotFoundException.class);

        // Update status attempt blocked
        assertThatThrownBy(() -> dispatchService.updateDispatchStatus(
                dispatchId, new DispatchStatusUpdateRequest(DispatchStatus.DISPATCHED, "T2 unauthorized hijack")
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant Isolation: Fleet vehicles and telematics in Tenant 1 are inaccessible to Tenant 2")
    void testFleetCrossTenantIsolation() {
        String reg = "MH-FS-T1-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        VehicleResponseDTO veh = fleetService.registerVehicle(new VehicleRegisterRequest(
                reg, "PUMPER-T1", VehicleType.PUMPER_ENGINE, stationId,
                "MAN", "TGM", 2023, 10000, 1000, 4000, "Ready"
        ));
        UUID vehId = veh.id();

        // Switch to Tenant 2
        TenantContext.setTenantId(tenant2);
        MockSecurityHelper.setupSecurityContext(tenant2, "admin_t2", List.of("ROLE_ADMIN"));

        // Read vehicle blocked
        assertThatThrownBy(() -> fleetService.getVehicle(vehId))
                .isInstanceOf(ResourceNotFoundException.class);

        // Update status blocked
        assertThatThrownBy(() -> fleetService.updateStatus(
                vehId, new VehicleStatusUpdateRequest(VehicleStatus.OUT_OF_SERVICE, "T2 tamper")
        )).isInstanceOf(ResourceNotFoundException.class);

        // Ingest telematics blocked
        assertThatThrownBy(() -> fleetService.updateTelematics(
                vehId, new VehicleTelematicsUpdateRequest(19.076, 72.877, 45.0, 180.0, 80.0, 3500.0)
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant Isolation: Inventory items, stock movements, and stock issue are isolated per tenant")
    void testInventoryCrossTenantIsolation() {
        String sku = "SKU-T1-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        InventoryItemResponseDTO item = inventoryService.registerItem(new InventoryItemCreateRequest(
                sku, "Thermal Camera", "EQUIPMENT", stationId, 15, 2, "PIECES", "Thermal tools"
        ));
        UUID itemId = item.id();

        // Switch to Tenant 2
        TenantContext.setTenantId(tenant2);
        MockSecurityHelper.setupSecurityContext(tenant2, "admin_t2", List.of("ROLE_ADMIN"));

        // Read item blocked
        assertThatThrownBy(() -> inventoryService.getItem(itemId))
                .isInstanceOf(ResourceNotFoundException.class);

        // Stock issue blocked
        assertThatThrownBy(() -> inventoryService.issueStock(
                itemId, new StockIssueRequest(itemId, 2, UUID.randomUUID(), "Site", "T2 breach attempt")
        )).isInstanceOf(ResourceNotFoundException.class);

        // Stock return blocked
        assertThatThrownBy(() -> inventoryService.returnStock(
                itemId, new StockReturnRequest(itemId, 1, null, "Site", "T2 breach return")
        )).isInstanceOf(ResourceNotFoundException.class);

        // Stock adjustment blocked
        assertThatThrownBy(() -> inventoryService.adjustStock(
                itemId, new StockAdjustmentRequest(20, StockMovementType.INSPECTION_ADJUSTMENT, "T2 tamper adjustment", "Unauthorized adjustment")
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant Isolation: Officer tasks, workloads, and escalations in Tenant 1 are isolated from Tenant 2")
    void testOfficerOperationsCrossTenantIsolation() {
        Instant now = Instant.now();
        OfficerTaskResponseDTO task = officerOperationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-T1-SEC", OfficerTaskType.INSPECTION, "Inspect Pier", "Desc", "OF-101", null,
                DispatchPriority.HIGH, now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1))
        ));

        // Alerts evaluated in Tenant 1
        var t1Alerts = officerOperationsService.evaluateOperationalAlerts();
        assertThat(t1Alerts).isNotEmpty();

        // Switch to Tenant 2
        TenantContext.setTenantId(tenant2);
        MockSecurityHelper.setupSecurityContext(tenant2, "admin_t2", List.of("ROLE_ADMIN", "ROLE_COMMANDER"));

        // Read task by ID blocked
        assertThatThrownBy(() -> officerOperationsService.getTask(task.taskId()))
                .isInstanceOf(ResourceNotFoundException.class);

        // Read task by reference blocked
        assertThatThrownBy(() -> officerOperationsService.getTaskByReference("TSK-T1-SEC"))
                .isInstanceOf(ResourceNotFoundException.class);

        // Update task status blocked
        assertThatThrownBy(() -> officerOperationsService.updateTaskStatus(
                task.taskId(), new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.COMPLETED, "T2 tamper")
        )).isInstanceOf(ResourceNotFoundException.class);

        // Manual escalation blocked
        assertThatThrownBy(() -> officerOperationsService.manualEscalateTask(
                task.taskId(), "T2 unauthorized escalate"
        )).isInstanceOf(ResourceNotFoundException.class);

        // List tasks returns zero results
        assertThat(officerOperationsService.getTasks(null, null, null, null, null)).isEmpty();

        // Evaluate alerts in Tenant 2 returns zero alerts
        assertThat(officerOperationsService.evaluateOperationalAlerts()).isEmpty();

        // Evaluate escalations in Tenant 2 returns zero escalations
        assertThat(officerOperationsService.evaluateEscalations()).isEmpty();

        // Escalation history for Tenant 2 is completely empty
        assertThat(officerOperationsService.getEscalationHistory()).isEmpty();

        // Overdue tasks list in Tenant 2 is completely empty
        assertThat(officerOperationsService.getOverdueTasks()).isEmpty();

        // Officer OF-101 workload under Tenant 2 reports 0 tasks
        var t2Workload = officerOperationsService.getOfficerWorkload("OF-101");
        assertThat(t2Workload.totalAssignedTasks()).isEqualTo(0);
    }

    @Test
    @DisplayName("Tenant Isolation: GIS hydrants registered in Tenant 1 are invisible in Tenant 2 spatial queries")
    void testGisHydrantCrossTenantIsolation() {
        String hydrantCode = "HYD-T1-" + UUID.randomUUID().toString().substring(0, 4);
        HydrantResponseDTO hydrant = gisOperationalService.registerHydrant(new HydrantCreateRequest(
                hydrantCode, 19.0760, 72.8777, 120.0, 2000.0, true, "Dockgate 5"
        ));
        UUID hydrantId = hydrant.id();

        // Switch to Tenant 2
        TenantContext.setTenantId(tenant2);
        MockSecurityHelper.setupSecurityContext(tenant2, "admin_t2", List.of("ROLE_ADMIN"));

        // Read hydrant by ID blocked
        assertThatThrownBy(() -> gisOperationalService.getHydrantById(hydrantId))
                .isInstanceOf(ResourceNotFoundException.class);

        // Spatial query around same coordinates returns empty list for Tenant 2
        List<HydrantResponseDTO> t2Nearby = gisOperationalService.findNearbyHydrants(19.0760, 72.8777, 1000.0);
        assertThat(t2Nearby.stream().noneMatch(h -> h.id().equals(hydrantId))).isTrue();
    }
}
