package ai.firesphere.fleet;

import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.contracts.fleet.VehicleAvailabilityContract;
import ai.firesphere.fleet.dto.*;
import ai.firesphere.fleet.model.Vehicle;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleStatusHistory;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.fleet.repository.VehicleRepository;
import ai.firesphere.fleet.repository.VehicleStatusHistoryRepository;
import ai.firesphere.fleet.service.FleetServiceImpl;
import ai.firesphere.fleet.service.FleetVehicleAvailabilityAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FleetServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleStatusHistoryRepository vehicleStatusHistoryRepository;

    @InjectMocks
    private FleetServiceImpl fleetService;

    private final UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID stationId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Vehicle createMockVehicle(UUID id, VehicleStatus status) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setTenantId(tenantA);
        v.setRegistrationNumber("MH-01-FS-501");
        v.setCallSign("ENGINE-5");
        v.setVehicleType(VehicleType.PUMPER_ENGINE);
        v.setStatus(status);
        v.setAssignedStationId(stationId);
        v.setMake("Rosenbauer");
        v.setModel("Commander 4000");
        v.setManufactureYear(2023);
        v.setWaterCapacityLiters(4500);
        v.setFoamCapacityLiters(500);
        v.setPumpCapacityLpm(3000);
        v.setFuelPercent(100.0);
        v.setWaterLevelPercent(100.0);
        return v;
    }

    // -------------------------------------------------------------
    // REGISTRATION TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("registerVehicle: success sets tenant, default AVAILABLE, uppercase reg, and history")
    void registerVehicle_Success() {
        when(vehicleRepository.existsByRegistrationNumberAndTenantId("MH-01-FS-501", tenantA)).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            v.setId(vehicleId);
            return v;
        });

        VehicleRegisterRequest request = new VehicleRegisterRequest(
                "mh-01-fs-501", // lowercase input
                "ENGINE-5",
                VehicleType.PUMPER_ENGINE,
                stationId,
                "Rosenbauer",
                "Commander 4000",
                2023,
                4500,
                500,
                3000,
                "Primary pumper for station 1"
        );

        VehicleResponseDTO response = fleetService.registerVehicle(request);

        assertNotNull(response);
        assertEquals(vehicleId, response.id());
        assertEquals("MH-01-FS-501", response.registrationNumber());
        assertEquals(VehicleStatus.AVAILABLE, response.status());
        assertTrue(response.available());

        verify(vehicleStatusHistoryRepository, times(1)).save(any(VehicleStatusHistory.class));
    }

    @Test
    @DisplayName("registerVehicle: duplicate registration number throws ConflictException (409)")
    void registerVehicle_Duplicate_ThrowsConflict() {
        when(vehicleRepository.existsByRegistrationNumberAndTenantId("MH-01-FS-501", tenantA)).thenReturn(true);

        VehicleRegisterRequest request = new VehicleRegisterRequest(
                "MH-01-FS-501",
                "ENGINE-5",
                VehicleType.PUMPER_ENGINE,
                stationId,
                null, null, null, null, null, null, null
        );

        assertThrows(ConflictException.class, () -> fleetService.registerVehicle(request));
        verify(vehicleRepository, never()).save(any());
    }

    // -------------------------------------------------------------
    // RETRIEVAL TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("getVehicle: returns vehicle for current tenant")
    void getVehicle_Success() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByIdAndTenantId(vehicleId, tenantA)).thenReturn(Optional.of(vehicle));

        VehicleResponseDTO response = fleetService.getVehicle(vehicleId);
        assertNotNull(response);
        assertEquals(vehicleId, response.id());
    }

    @Test
    @DisplayName("getVehicle: throws 404 when not found or belonging to another tenant")
    void getVehicle_NotFound_Throws404() {
        when(vehicleRepository.findByIdAndTenantId(vehicleId, tenantA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> fleetService.getVehicle(vehicleId));
    }

    @Test
    @DisplayName("getVehicles: paginated filtering with specification")
    void getVehicles_Paginated_Success() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.AVAILABLE);
        Page<Vehicle> page = new PageImpl<>(List.of(vehicle), PageRequest.of(0, 10), 1);
        when(vehicleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        var response = fleetService.getVehicles(VehicleStatus.AVAILABLE, VehicleType.PUMPER_ENGINE, stationId, "ENGINE", PageRequest.of(0, 10));
        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals(1, response.totalElements());
    }

    @Test
    @DisplayName("getVehiclesByStation: returns vehicles assigned to station")
    void getVehiclesByStation_Success() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findAllByTenantIdAndAssignedStationId(tenantA, stationId)).thenReturn(List.of(vehicle));

        List<VehicleResponseDTO> list = fleetService.getVehiclesByStation(stationId);
        assertEquals(1, list.size());
        assertEquals(vehicleId, list.get(0).id());
    }

    // -------------------------------------------------------------
    // UPDATE METADATA TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("updateVehicle: updates mutable attributes without altering status or reg number")
    void updateVehicle_Success() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByIdAndTenantId(vehicleId, tenantA)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        VehicleUpdateRequest request = new VehicleUpdateRequest(
                "RESCUE-PUMPER-5",
                null,
                "Volvo",
                "FMX 460",
                2024,
                5000,
                600,
                3500,
                "Upgraded water pump"
        );

        VehicleResponseDTO response = fleetService.updateVehicle(vehicleId, request);
        assertEquals("RESCUE-PUMPER-5", response.callSign());
        assertEquals("Volvo", response.make());
        assertEquals("MH-01-FS-501", response.registrationNumber()); // Registration number remains intact
        assertEquals(VehicleStatus.AVAILABLE, response.status()); // Status remains intact
    }

    // -------------------------------------------------------------
    // STATUS LIFECYCLE TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("updateStatus: valid emergency deployment progression")
    void updateStatus_EmergencyProgression_Success() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByIdAndTenantId(vehicleId, tenantA)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. AVAILABLE -> DISPATCHED
        VehicleResponseDTO d1 = fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.DISPATCHED, "Alarm 1"));
        assertEquals(VehicleStatus.DISPATCHED, d1.status());

        // 2. DISPATCHED -> EN_ROUTE
        VehicleResponseDTO d2 = fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.EN_ROUTE, "En route"));
        assertEquals(VehicleStatus.EN_ROUTE, d2.status());

        // 3. EN_ROUTE -> ON_SCENE
        VehicleResponseDTO d3 = fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.ON_SCENE, "On scene"));
        assertEquals(VehicleStatus.ON_SCENE, d3.status());

        // 4. ON_SCENE -> AVAILABLE
        VehicleResponseDTO d4 = fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.AVAILABLE, "Back in quarters"));
        assertEquals(VehicleStatus.AVAILABLE, d4.status());

        verify(vehicleStatusHistoryRepository, times(4)).save(any(VehicleStatusHistory.class));
    }

    @Test
    @DisplayName("updateStatus: maintenance and out-of-service lifecycle paths")
    void updateStatus_MaintenanceAndOutOfService_Success() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByIdAndTenantId(vehicleId, tenantA)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        // AVAILABLE -> MAINTENANCE -> AVAILABLE
        fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.MAINTENANCE, "Routine pump service"));
        assertEquals(VehicleStatus.MAINTENANCE, vehicle.getStatus());

        fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.AVAILABLE, "Service complete"));
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());

        // AVAILABLE -> OUT_OF_SERVICE -> MAINTENANCE -> AVAILABLE
        fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.OUT_OF_SERVICE, "Engine overheat breakdown"));
        assertEquals(VehicleStatus.OUT_OF_SERVICE, vehicle.getStatus());

        fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.MAINTENANCE, "Towed to workshop"));
        assertEquals(VehicleStatus.MAINTENANCE, vehicle.getStatus());

        fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.AVAILABLE, "Repaired and recertified"));
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());
    }

    @Test
    @DisplayName("updateStatus: invalid transitions rejected with 422")
    void updateStatus_InvalidTransitions_Throws422() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.OUT_OF_SERVICE);
        when(vehicleRepository.findByIdAndTenantId(vehicleId, tenantA)).thenReturn(Optional.of(vehicle));

        // OUT_OF_SERVICE -> EN_ROUTE is forbidden
        assertThrows(BusinessRuleViolationException.class,
                () -> fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.EN_ROUTE, "Invalid jump")));

        // OUT_OF_SERVICE -> DISPATCHED is forbidden
        assertThrows(BusinessRuleViolationException.class,
                () -> fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.DISPATCHED, "Invalid jump")));

        // OUT_OF_SERVICE -> ON_SCENE is forbidden
        assertThrows(BusinessRuleViolationException.class,
                () -> fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.ON_SCENE, "Invalid jump")));

        // AVAILABLE -> ON_SCENE is forbidden (cannot jump straight to scene without dispatch/en-route)
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        assertThrows(BusinessRuleViolationException.class,
                () -> fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.ON_SCENE, "Jump directly to scene")));
    }

    @Test
    @DisplayName("updateStatus: same-status transition rejected with 422 and no history saved")
    void updateStatus_SameStatus_Throws422() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByIdAndTenantId(vehicleId, tenantA)).thenReturn(Optional.of(vehicle));

        assertThrows(BusinessRuleViolationException.class,
                () -> fleetService.updateStatus(vehicleId, new VehicleStatusUpdateRequest(VehicleStatus.AVAILABLE, "No change")));

        verify(vehicleStatusHistoryRepository, never()).save(any());
    }

    // -------------------------------------------------------------
    // READINESS & CONTRACT TESTS
    // -------------------------------------------------------------

    @Test
    @DisplayName("getVehicleReadiness: deterministic readiness mapping based on status")
    void getVehicleReadiness_Deterministic() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByIdAndTenantId(vehicleId, tenantA)).thenReturn(Optional.of(vehicle));

        VehicleReadinessDTO r1 = fleetService.getVehicleReadiness(vehicleId);
        assertTrue(r1.available());
        assertEquals("OPERATIONAL_READY", r1.readinessState());

        vehicle.setStatus(VehicleStatus.DISPATCHED);
        VehicleReadinessDTO r2 = fleetService.getVehicleReadiness(vehicleId);
        assertFalse(r2.available());
        assertEquals("DEPLOYED", r2.readinessState());

        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        VehicleReadinessDTO r3 = fleetService.getVehicleReadiness(vehicleId);
        assertFalse(r3.available());
        assertEquals("UNDER_MAINTENANCE", r3.readinessState());

        vehicle.setStatus(VehicleStatus.OUT_OF_SERVICE);
        VehicleReadinessDTO r4 = fleetService.getVehicleReadiness(vehicleId);
        assertFalse(r4.available());
        assertEquals("OUT_OF_SERVICE", r4.readinessState());
    }

    @Test
    @DisplayName("VehicleAvailabilityContract adapter queries repository correctly")
    void testVehicleAvailabilityAdapter() {
        Vehicle vehicle = createMockVehicle(vehicleId, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findByIdAndTenantId(vehicleId, tenantA)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findAllByTenantIdAndAssignedStationId(tenantA, stationId)).thenReturn(List.of(vehicle));

        VehicleAvailabilityContract contract = new FleetVehicleAvailabilityAdapter(vehicleRepository);

        assertTrue(contract.isVehicleAvailable(vehicleId));
        assertEquals(Optional.of(VehicleStatus.AVAILABLE), contract.getVehicleStatus(vehicleId));
        assertEquals(List.of(vehicleId), contract.getAvailableVehiclesByStation(stationId, VehicleType.PUMPER_ENGINE));

        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        assertFalse(contract.isVehicleAvailable(vehicleId));
        assertTrue(contract.getAvailableVehiclesByStation(stationId, VehicleType.PUMPER_ENGINE).isEmpty());
    }
}
