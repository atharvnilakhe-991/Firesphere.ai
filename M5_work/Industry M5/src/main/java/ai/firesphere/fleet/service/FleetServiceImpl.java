package ai.firesphere.fleet.service;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.fleet.dto.*;
import ai.firesphere.fleet.model.Vehicle;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleStatusHistory;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.fleet.repository.VehicleRepository;
import ai.firesphere.fleet.repository.VehicleSpecification;
import ai.firesphere.fleet.repository.VehicleStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FleetServiceImpl implements FleetService {

    private static final Logger log = LoggerFactory.getLogger(FleetServiceImpl.class);

    private static final Map<VehicleStatus, Set<VehicleStatus>> ALLOWED_TRANSITIONS = Map.of(
            VehicleStatus.AVAILABLE, Set.of(VehicleStatus.ASSIGNED, VehicleStatus.DISPATCHED, VehicleStatus.MAINTENANCE, VehicleStatus.OUT_OF_SERVICE),
            VehicleStatus.ASSIGNED, Set.of(VehicleStatus.DISPATCHED, VehicleStatus.EN_ROUTE, VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE, VehicleStatus.OUT_OF_SERVICE),
            VehicleStatus.DISPATCHED, Set.of(VehicleStatus.EN_ROUTE, VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE, VehicleStatus.OUT_OF_SERVICE),
            VehicleStatus.EN_ROUTE, Set.of(VehicleStatus.ON_SCENE, VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE, VehicleStatus.OUT_OF_SERVICE),
            VehicleStatus.ON_SCENE, Set.of(VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE, VehicleStatus.OUT_OF_SERVICE),
            VehicleStatus.MAINTENANCE, Set.of(VehicleStatus.AVAILABLE, VehicleStatus.OUT_OF_SERVICE),
            VehicleStatus.OUT_OF_SERVICE, Set.of(VehicleStatus.AVAILABLE, VehicleStatus.MAINTENANCE)
    );

    private final VehicleRepository vehicleRepository;
    private final VehicleStatusHistoryRepository vehicleStatusHistoryRepository;

    public FleetServiceImpl(
            VehicleRepository vehicleRepository,
            VehicleStatusHistoryRepository vehicleStatusHistoryRepository) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleStatusHistoryRepository = vehicleStatusHistoryRepository;
    }

    @Override
    @Transactional
    public VehicleResponseDTO registerVehicle(VehicleRegisterRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        String normalizedReg = request.registrationNumber().trim().toUpperCase();

        // Check duplicate registration number within tenant
        if (vehicleRepository.existsByRegistrationNumberAndTenantId(normalizedReg, tenantId)) {
            throw new ConflictException("Vehicle with registration number already exists: " + normalizedReg);
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setTenantId(tenantId);
        vehicle.setRegistrationNumber(normalizedReg);
        vehicle.setCallSign(request.callSign().trim());
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicle.setAssignedStationId(request.assignedStationId());
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setManufactureYear(request.manufactureYear());
        vehicle.setWaterCapacityLiters(request.waterCapacityLiters());
        vehicle.setFoamCapacityLiters(request.foamCapacityLiters());
        vehicle.setPumpCapacityLpm(request.pumpCapacityLpm());
        vehicle.setOperationalNotes(request.operationalNotes());

        Vehicle saved = vehicleRepository.save(vehicle);

        // Record initial status history
        VehicleStatusHistory history = new VehicleStatusHistory(
                saved.getId(),
                null,
                VehicleStatus.AVAILABLE,
                resolveCurrentActor(),
                "Initial vehicle registration"
        );
        history.setTenantId(tenantId);
        vehicleStatusHistoryRepository.save(history);

        log.info("Registered vehicle {} [{}] for station {} [Tenant: {}]",
                saved.getRegistrationNumber(), saved.getId(), saved.getAssignedStationId(), tenantId);

        return VehicleResponseDTO.fromEntity(saved);
    }

    @Override
    public VehicleResponseDTO getVehicle(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return vehicleRepository.findByIdAndTenantId(id, tenantId)
                .map(VehicleResponseDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
    }

    @Override
    public PageResponse<VehicleResponseDTO> getVehicles(
            VehicleStatus status,
            VehicleType vehicleType,
            UUID stationId,
            String search,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Specification<Vehicle> spec = VehicleSpecification.withFilters(
                tenantId, status, vehicleType, stationId, search);
        Page<Vehicle> page = vehicleRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(VehicleResponseDTO::fromEntity));
    }

    @Override
    public PageResponse<VehicleResponseDTO> getVehicles(VehicleStatus status, Pageable pageable) {
        return getVehicles(status, null, null, null, pageable);
    }

    @Override
    public List<VehicleResponseDTO> getVehiclesByStation(UUID stationId) {
        UUID tenantId = TenantContext.getTenantId();
        return vehicleRepository.findAllByTenantIdAndAssignedStationId(tenantId, stationId).stream()
                .map(VehicleResponseDTO::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public VehicleResponseDTO updateVehicle(UUID id, VehicleUpdateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));

        if (request.callSign() != null && !request.callSign().isBlank()) {
            vehicle.setCallSign(request.callSign().trim());
        }
        if (request.assignedStationId() != null) {
            vehicle.setAssignedStationId(request.assignedStationId());
        }
        if (request.make() != null) {
            vehicle.setMake(request.make());
        }
        if (request.model() != null) {
            vehicle.setModel(request.model());
        }
        if (request.manufactureYear() != null) {
            vehicle.setManufactureYear(request.manufactureYear());
        }
        if (request.waterCapacityLiters() != null) {
            vehicle.setWaterCapacityLiters(request.waterCapacityLiters());
        }
        if (request.foamCapacityLiters() != null) {
            vehicle.setFoamCapacityLiters(request.foamCapacityLiters());
        }
        if (request.pumpCapacityLpm() != null) {
            vehicle.setPumpCapacityLpm(request.pumpCapacityLpm());
        }
        if (request.operationalNotes() != null) {
            vehicle.setOperationalNotes(request.operationalNotes());
        }

        Vehicle updated = vehicleRepository.save(vehicle);
        log.info("Updated vehicle {} metadata [Tenant: {}]", vehicle.getRegistrationNumber(), tenantId);
        return VehicleResponseDTO.fromEntity(updated);
    }

    @Override
    @Transactional
    public VehicleResponseDTO updateStatus(UUID id, VehicleStatusUpdateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));

        VehicleStatus currentStatus = vehicle.getStatus();
        VehicleStatus targetStatus = request.status();

        Set<VehicleStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new BusinessRuleViolationException(
                    "Invalid vehicle status transition from " + currentStatus + " to " + targetStatus);
        }

        vehicle.setStatus(targetStatus);
        if (request.reason() != null && !request.reason().isBlank()) {
            String existing = vehicle.getOperationalNotes() != null ? vehicle.getOperationalNotes() + "\n" : "";
            vehicle.setOperationalNotes(existing + "[" + targetStatus + "] " + request.reason());
        }

        Vehicle saved = vehicleRepository.save(vehicle);

        // Record auditable status history
        VehicleStatusHistory history = new VehicleStatusHistory(
                saved.getId(),
                currentStatus,
                targetStatus,
                resolveCurrentActor(),
                request.reason()
        );
        history.setTenantId(tenantId);
        vehicleStatusHistoryRepository.save(history);

        log.info("Transitioned vehicle {} from {} to {} [Tenant: {}]",
                vehicle.getRegistrationNumber(), currentStatus, targetStatus, tenantId);

        return VehicleResponseDTO.fromEntity(saved);
    }

    @Override
    public List<VehicleStatusHistoryDTO> getVehicleHistory(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        if (!vehicleRepository.existsByIdAndTenantId(id, tenantId)) {
            throw new ResourceNotFoundException("Vehicle not found: " + id);
        }
        return vehicleStatusHistoryRepository.findAllByVehicleIdAndTenantIdOrderByTransitionedAtAsc(id, tenantId).stream()
                .map(VehicleStatusHistoryDTO::fromEntity)
                .toList();
    }

    @Override
    public VehicleReadinessDTO getVehicleReadiness(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        return VehicleReadinessDTO.fromEntity(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponseDTO updateTelematics(UUID id, VehicleTelematicsUpdateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));

        vehicle.setCurrentLatitude(request.latitude());
        vehicle.setCurrentLongitude(request.longitude());
        if (request.fuelPercent() != null) {
            vehicle.setFuelPercent(request.fuelPercent());
        }
        if (request.waterLevelPercent() != null) {
            vehicle.setWaterLevelPercent(request.waterLevelPercent());
        }

        Vehicle saved = vehicleRepository.save(vehicle);
        return VehicleResponseDTO.fromEntity(saved);
    }

    private String resolveCurrentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "FLEET_OFFICER";
    }
}
