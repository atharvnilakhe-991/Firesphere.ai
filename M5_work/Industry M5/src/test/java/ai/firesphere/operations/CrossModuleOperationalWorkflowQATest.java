package ai.firesphere.operations;

import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.dto.DispatchCreateRequest;
import ai.firesphere.dispatch.dto.DispatchRecommendationRequest;
import ai.firesphere.dispatch.dto.DispatchRecommendationResponse;
import ai.firesphere.dispatch.dto.DispatchResponseDTO;
import ai.firesphere.dispatch.dto.DispatchStatusUpdateRequest;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.dispatch.service.DispatchService;
import ai.firesphere.fleet.dto.VehicleRegisterRequest;
import ai.firesphere.fleet.dto.VehicleResponseDTO;
import ai.firesphere.fleet.dto.VehicleStatusUpdateRequest;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.fleet.service.FleetService;
import ai.firesphere.gis.foundation.model.LocationValidationResult;
import ai.firesphere.gis.foundation.model.LocationValidationStatus;
import ai.firesphere.gis.foundation.validation.GpsCoordinateValidator;
import ai.firesphere.incident.dto.IncidentCreateRequest;
import ai.firesphere.incident.dto.IncidentResponseDTO;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.incident.service.IncidentService;
import ai.firesphere.inventory.dto.InventoryItemCreateRequest;
import ai.firesphere.inventory.dto.InventoryItemResponseDTO;
import ai.firesphere.inventory.dto.StockIssueRequest;
import ai.firesphere.inventory.dto.StockMovementResponseDTO;
import ai.firesphere.inventory.model.StockMovementType;
import ai.firesphere.inventory.service.InventoryService;
import ai.firesphere.operations.officer.dto.OfficerTaskCreateRequest;
import ai.firesphere.operations.officer.dto.OfficerTaskResponseDTO;
import ai.firesphere.operations.officer.dto.OfficerTaskStatusUpdateRequest;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import ai.firesphere.operations.officer.model.OfficerTaskType;
import ai.firesphere.operations.officer.service.OfficerOperationsService;
import ai.firesphere.testing.MockSecurityHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Functional QA Suite for Cross-Module Operational Workflows.
 * Validates the full operational thread from incident intake, GPS validation,
 * apparatus recommendations and sequential dispatch lifecycle, fleet status coordination,
 * emergency inventory issue with movement audit, field officer task management,
 * and multi-tenant isolation across all participating subsystems.
 */
@SpringBootTest
@ActiveProfiles("test")
class CrossModuleOperationalWorkflowQATest {

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
    private GpsCoordinateValidator gpsCoordinateValidator;

    private final UUID tenant1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenant2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID stationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenant1);
        MockSecurityHelper.setupSecurityContext(tenant1, "ops_commander", List.of("ROLE_ADMIN", "ROLE_DISPATCHER"));
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("End-to-End Operational Lifecycle: Incident -> GIS -> Dispatch -> Fleet -> Inventory -> Officer Task")
    void testCompleteCrossModuleOperationalLifecycle() {
        // Step 1: Validate Incident Scene GPS Coordinates
        double sceneLat = 19.0760;
        double sceneLon = 72.8777;
        LocationValidationResult gpsResult = gpsCoordinateValidator.validate(sceneLat, sceneLon, 10.0);
        assertThat(gpsResult.valid()).isTrue();
        assertThat(gpsResult.status()).isEqualTo(LocationValidationStatus.VALID);

        // Step 2: Intake and Register Incident
        IncidentCreateRequest incidentReq = new IncidentCreateRequest(
                "Dock Warehouse Chemical Blaze",
                "High-temperature blaze near flammable chemicals",
                IncidentType.INDUSTRIAL_HAZARD,
                IncidentSeverity.CRITICAL,
                sceneLat,
                sceneLon,
                "Pier 44, Dock Basin, Port Area",
                "Security Chief Patel",
                "+919876543210",
                stationId,
                List.of("ENGINE-1", "HAZMAT-1")
        );
        IncidentResponseDTO incident = incidentService.createIncident(incidentReq);
        assertThat(incident).isNotNull();
        assertThat(incident.status()).isEqualTo(IncidentStatus.REPORTED);
        assertThat(incident.severity()).isEqualTo(IncidentSeverity.CRITICAL);
        UUID incidentId = incident.id();

        // Step 3: Get Turnout Recommendations and Issue Dispatch Order
        DispatchRecommendationRequest recReq = new DispatchRecommendationRequest(
                incidentId,
                incident.incidentType(),
                incident.severity(),
                sceneLat,
                sceneLon
        );
        DispatchRecommendationResponse recommendations = dispatchService.getRecommendations(recReq);
        assertThat(recommendations).isNotNull();
        assertThat(recommendations.recommendedUnits()).isNotEmpty();

        DispatchCreateRequest dispatchReq = new DispatchCreateRequest(
                incidentId,
                stationId,
                DispatchPriority.CRITICAL,
                List.of(UUID.randomUUID()),
                List.of(UUID.randomUUID()),
                "Emergency chemical turnout order"
        );
        DispatchResponseDTO dispatch = dispatchService.createDispatch(dispatchReq);
        assertThat(dispatch).isNotNull();
        assertThat(dispatch.status()).isEqualTo(DispatchStatus.CREATED);
        UUID dispatchId = dispatch.id();

        // Step 4: Advance Dispatch Order Through Full Sequential Lifecycle
        DispatchResponseDTO s1 = dispatchService.updateDispatchStatus(dispatchId, new DispatchStatusUpdateRequest(DispatchStatus.DISPATCHED, "Turnout alert sounded"));
        assertThat(s1.status()).isEqualTo(DispatchStatus.DISPATCHED);

        DispatchResponseDTO s2 = dispatchService.updateDispatchStatus(dispatchId, new DispatchStatusUpdateRequest(DispatchStatus.ACKNOWLEDGED, "Crew acknowledged"));
        assertThat(s2.status()).isEqualTo(DispatchStatus.ACKNOWLEDGED);

        DispatchResponseDTO s3 = dispatchService.updateDispatchStatus(dispatchId, new DispatchStatusUpdateRequest(DispatchStatus.EN_ROUTE, "Apparatus en route"));
        assertThat(s3.status()).isEqualTo(DispatchStatus.EN_ROUTE);

        DispatchResponseDTO s4 = dispatchService.updateDispatchStatus(dispatchId, new DispatchStatusUpdateRequest(DispatchStatus.ARRIVED, "On scene command established"));
        assertThat(s4.status()).isEqualTo(DispatchStatus.ARRIVED);

        DispatchResponseDTO s5 = dispatchService.updateDispatchStatus(dispatchId, new DispatchStatusUpdateRequest(DispatchStatus.COMPLETED, "Fire contained and overhaul complete"));
        assertThat(s5.status()).isEqualTo(DispatchStatus.COMPLETED);

        // Step 5: Register and Transition Fleet Vehicle
        String regNumber = "MH01-FS-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        VehicleResponseDTO vehicle = fleetService.registerVehicle(new VehicleRegisterRequest(
                regNumber,
                "ENGINE-QA-01",
                VehicleType.WATER_TANKER,
                stationId,
                "Rosenbauer",
                "Panther 6x6",
                2024,
                12000,
                1500,
                6000,
                "Ready for deployment"
        ));
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.AVAILABLE);

        VehicleResponseDTO dispatchedVeh = fleetService.updateStatus(vehicle.id(), new VehicleStatusUpdateRequest(VehicleStatus.DISPATCHED, "Assigned to chemical fire"));
        assertThat(dispatchedVeh.status()).isEqualTo(VehicleStatus.DISPATCHED);

        VehicleResponseDTO returnedVeh = fleetService.updateStatus(vehicle.id(), new VehicleStatusUpdateRequest(VehicleStatus.AVAILABLE, "Overhaul complete"));
        assertThat(returnedVeh.status()).isEqualTo(VehicleStatus.AVAILABLE);

        // Step 6: Register Inventory Item and Issue Emergency Stock
        String sku = "SKU-FOAM-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        InventoryItemResponseDTO item = inventoryService.registerItem(new InventoryItemCreateRequest(
                sku,
                "Class B Foam Concentrate 50L",
                "FOAM_CONCENTRATE",
                stationId,
                30,
                5,
                "LITERS",
                "Initial batch"
        ));
        assertThat(item.quantityOnHand()).isEqualTo(30);

        StockMovementResponseDTO issueMovement = inventoryService.issueStock(item.id(), new StockIssueRequest(
                item.id(),
                10,
                incidentId,
                "Pier 44 Incident Site",
                "Foam suppression line deployment"
        ));
        assertThat(issueMovement.movementType()).isEqualTo(StockMovementType.ISSUE);
        assertThat(issueMovement.quantity()).isEqualTo(10);
        assertThat(issueMovement.resultingQuantity()).isEqualTo(20);

        InventoryItemResponseDTO updatedItem = inventoryService.getItem(item.id());
        assertThat(updatedItem.quantityOnHand()).isEqualTo(20);

        // Step 7: Create Field Officer Inspection Task Linked to Incident Site
        Instant now = Instant.now();
        OfficerTaskResponseDTO task = officerOperationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-INCIDENT-" + incidentId.toString().substring(0, 8),
                OfficerTaskType.HAZARD_VERIFICATION,
                "Post-fire chemical barrier inspection",
                "Inspect chemical storage integrity at Pier 44",
                "OF-PATEL-101",
                UUID.randomUUID(),
                DispatchPriority.HIGH,
                now,
                now.plusSeconds(7200)
        ));
        assertThat(task.status()).isEqualTo(OfficerTaskStatus.ASSIGNED);

        OfficerTaskResponseDTO completedTask = officerOperationsService.updateTaskStatus(
                task.taskId(), new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.COMPLETED, "Inspection certified clean")
        );
        assertThat(completedTask.status()).isEqualTo(OfficerTaskStatus.COMPLETED);
        assertThat(completedTask.completedAt()).isNotNull();

        // Step 8: Multi-Tenant Boundary Check: Switch to Tenant 2 and verify isolation
        TenantContext.setTenantId(tenant2);
        MockSecurityHelper.setupSecurityContext(tenant2, "tenant2_admin", List.of("ROLE_ADMIN"));

        // Tenant 2 cannot access Tenant 1's incident
        assertThatThrownBy(() -> incidentService.getIncident(incidentId))
                .isInstanceOf(ResourceNotFoundException.class);

        // Tenant 2 cannot access Tenant 1's dispatch order
        assertThatThrownBy(() -> dispatchService.getDispatch(dispatchId))
                .isInstanceOf(ResourceNotFoundException.class);

        // Tenant 2 cannot access Tenant 1's fleet vehicle
        assertThatThrownBy(() -> fleetService.getVehicle(vehicle.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        // Tenant 2 cannot access Tenant 1's inventory item
        assertThatThrownBy(() -> inventoryService.getItem(item.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        // Tenant 2 cannot access Tenant 1's officer task
        assertThatThrownBy(() -> officerOperationsService.getTask(task.taskId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
