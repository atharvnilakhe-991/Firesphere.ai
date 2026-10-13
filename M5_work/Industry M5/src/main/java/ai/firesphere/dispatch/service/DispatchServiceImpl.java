package ai.firesphere.dispatch.service;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.dto.*;
import ai.firesphere.dispatch.model.DispatchOrder;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.dispatch.model.DispatchStatusHistory;
import ai.firesphere.dispatch.repository.DispatchOrderRepository;
import ai.firesphere.dispatch.repository.DispatchSpecification;
import ai.firesphere.dispatch.repository.DispatchStatusHistoryRepository;
import ai.firesphere.incident.dto.IncidentResponseDTO;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.incident.service.IncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional(readOnly = true)
public class DispatchServiceImpl implements DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchServiceImpl.class);

    private static final Set<DispatchStatus> ACTIVE_STATUSES = Set.of(
            DispatchStatus.CREATED,
            DispatchStatus.DISPATCHED,
            DispatchStatus.ACKNOWLEDGED,
            DispatchStatus.EN_ROUTE,
            DispatchStatus.ARRIVED
    );

    private static final Map<DispatchStatus, Set<DispatchStatus>> ALLOWED_TRANSITIONS = Map.of(
            DispatchStatus.CREATED, Set.of(DispatchStatus.DISPATCHED, DispatchStatus.CANCELLED),
            DispatchStatus.DISPATCHED, Set.of(DispatchStatus.ACKNOWLEDGED),
            DispatchStatus.ACKNOWLEDGED, Set.of(DispatchStatus.EN_ROUTE),
            DispatchStatus.EN_ROUTE, Set.of(DispatchStatus.ARRIVED),
            DispatchStatus.ARRIVED, Set.of(DispatchStatus.COMPLETED),
            DispatchStatus.COMPLETED, Set.of(),
            DispatchStatus.CANCELLED, Set.of()
    );

    private final DispatchOrderRepository dispatchOrderRepository;
    private final DispatchStatusHistoryRepository dispatchStatusHistoryRepository;
    private final IncidentService incidentService;

    public DispatchServiceImpl(
            DispatchOrderRepository dispatchOrderRepository,
            DispatchStatusHistoryRepository dispatchStatusHistoryRepository,
            IncidentService incidentService) {
        this.dispatchOrderRepository = dispatchOrderRepository;
        this.dispatchStatusHistoryRepository = dispatchStatusHistoryRepository;
        this.incidentService = incidentService;
    }

    @Override
    @Transactional
    public DispatchResponseDTO createDispatch(DispatchCreateRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // 1. Incident validation: must exist within current tenant via IncidentService boundary
        IncidentResponseDTO incident;
        try {
            incident = incidentService.getIncident(request.incidentId());
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Cannot create dispatch order: incident not found " + request.incidentId());
        }

        // 2. Prevent dispatch against a CLOSED incident
        if (incident.status() == IncidentStatus.CLOSED) {
            throw new BusinessRuleViolationException("Cannot dispatch resources for a closed incident: " + incident.incidentNumber());
        }

        // 3. Duplicate active dispatch protection
        List<DispatchOrder> activeOrders = dispatchOrderRepository.findAllByIncidentIdAndTenantIdAndStatusIn(
                incident.id(), tenantId, ACTIVE_STATUSES);

        if (!activeOrders.isEmpty()) {
            if (request.assignedVehicleIds() != null && !request.assignedVehicleIds().isEmpty()) {
                for (DispatchOrder activeOrder : activeOrders) {
                    if (activeOrder.getAssignedVehicleIds() != null) {
                        for (UUID vehicleId : request.assignedVehicleIds()) {
                            if (activeOrder.getAssignedVehicleIds().contains(vehicleId)) {
                                throw new BusinessRuleViolationException(
                                        "Duplicate active dispatch: vehicle " + vehicleId +
                                                " is already assigned to active dispatch order " + activeOrder.getDispatchCode());
                            }
                        }
                    }
                }
            } else {
                // If no vehicles specified, check if active order already exists for same station
                boolean duplicateStation = activeOrders.stream()
                        .anyMatch(o -> o.getStationId().equals(request.stationId()) &&
                                (o.getAssignedVehicleIds() == null || o.getAssignedVehicleIds().isEmpty()));
                if (duplicateStation) {
                    throw new BusinessRuleViolationException(
                            "Duplicate active dispatch: an active dispatch order already exists for station " +
                                    request.stationId() + " on incident " + incident.incidentNumber());
                }
            }
        }

        // 4. Construct dispatch order
        DispatchOrder order = new DispatchOrder();
        order.setTenantId(tenantId);
        order.setIncidentId(incident.id());
        order.setStationId(request.stationId());
        order.setPriority(request.priority());
        order.setStatus(DispatchStatus.CREATED);
        order.setDispatchCode(generateDispatchCode());
        order.setDispatchedAt(Instant.now());
        order.setOperationalNotes(request.operationalNotes());

        if (request.assignedVehicleIds() != null) {
            order.setAssignedVehicleIds(new ArrayList<>(request.assignedVehicleIds()));
        }
        if (request.assignedResponderIds() != null) {
            order.setAssignedResponderIds(new ArrayList<>(request.assignedResponderIds()));
        }

        DispatchOrder savedOrder = dispatchOrderRepository.save(order);

        // 5. Record initial auditable history
        DispatchStatusHistory history = new DispatchStatusHistory(
                savedOrder.getId(),
                null,
                DispatchStatus.CREATED,
                resolveCurrentActor(),
                "Dispatch order created"
        );
        history.setTenantId(tenantId);
        dispatchStatusHistoryRepository.save(history);

        log.info("Created dispatch order {} [{}] for incident {} [Tenant: {}]",
                savedOrder.getDispatchCode(), savedOrder.getId(), incident.incidentNumber(), tenantId);

        return DispatchResponseDTO.fromEntity(savedOrder);
    }

    @Override
    public DispatchResponseDTO getDispatch(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return dispatchOrderRepository.findByIdAndTenantId(id, tenantId)
                .map(DispatchResponseDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch order not found: " + id));
    }

    @Override
    public PageResponse<DispatchResponseDTO> getDispatches(
            DispatchStatus status,
            DispatchPriority priority,
            UUID incidentId,
            UUID stationId,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Specification<DispatchOrder> spec = DispatchSpecification.withFilters(
                tenantId, status, priority, incidentId, stationId);
        Page<DispatchOrder> page = dispatchOrderRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(DispatchResponseDTO::fromEntity));
    }

    @Override
    public PageResponse<DispatchResponseDTO> getDispatches(DispatchStatus status, Pageable pageable) {
        return getDispatches(status, null, null, null, pageable);
    }

    @Override
    public List<DispatchResponseDTO> getDispatchesForIncident(UUID incidentId) {
        UUID tenantId = TenantContext.getTenantId();
        return dispatchOrderRepository.findAllByIncidentIdAndTenantId(incidentId, tenantId).stream()
                .map(DispatchResponseDTO::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public DispatchResponseDTO updateDispatch(UUID id, DispatchUpdateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        DispatchOrder order = dispatchOrderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch order not found: " + id));

        // Immutable in terminal states
        if (order.getStatus() == DispatchStatus.COMPLETED || order.getStatus() == DispatchStatus.CANCELLED) {
            throw new BusinessRuleViolationException(
                    "Cannot modify dispatch order in terminal status: " + order.getStatus());
        }

        if (request.priority() != null) {
            order.setPriority(request.priority());
        }
        if (request.stationId() != null) {
            order.setStationId(request.stationId());
        }
        if (request.assignedVehicleIds() != null) {
            order.setAssignedVehicleIds(new ArrayList<>(request.assignedVehicleIds()));
        }
        if (request.assignedResponderIds() != null) {
            order.setAssignedResponderIds(new ArrayList<>(request.assignedResponderIds()));
        }
        if (request.operationalNotes() != null) {
            order.setOperationalNotes(request.operationalNotes());
        }

        DispatchOrder updated = dispatchOrderRepository.save(order);
        log.info("Updated dispatch order {} metadata [Tenant: {}]", order.getDispatchCode(), tenantId);
        return DispatchResponseDTO.fromEntity(updated);
    }

    @Override
    @Transactional
    public DispatchResponseDTO updateDispatchStatus(UUID id, DispatchStatusUpdateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        DispatchOrder order = dispatchOrderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch order not found: " + id));

        DispatchStatus currentStatus = order.getStatus();
        DispatchStatus targetStatus = request.status();

        Set<DispatchStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new BusinessRuleViolationException(
                    "Invalid dispatch status transition from " + currentStatus + " to " + targetStatus);
        }

        Instant now = Instant.now();
        order.setStatus(targetStatus);

        switch (targetStatus) {
            case DISPATCHED -> order.setDispatchedAt(now);
            case ACKNOWLEDGED -> order.setAcknowledgedAt(now);
            case EN_ROUTE -> order.setEnRouteAt(now);
            case ARRIVED -> order.setArrivedAt(now);
            case COMPLETED -> order.setCompletedAt(now);
            case CANCELLED -> {
                order.setCancelledAt(now);
                order.setCancellationReason(request.getEffectiveNotes());
            }
        }

        if (request.statusNotes() != null && !request.statusNotes().isBlank()) {
            String existingNotes = order.getOperationalNotes() != null ? order.getOperationalNotes() + "\n" : "";
            order.setOperationalNotes(existingNotes + "[" + targetStatus + "] " + request.statusNotes());
        }

        DispatchOrder saved = dispatchOrderRepository.save(order);

        // Record auditable history
        DispatchStatusHistory history = new DispatchStatusHistory(
                saved.getId(),
                currentStatus,
                targetStatus,
                resolveCurrentActor(),
                request.statusNotes()
        );
        history.setTenantId(tenantId);
        dispatchStatusHistoryRepository.save(history);

        log.info("Transitioned dispatch order {} from {} to {} [Tenant: {}]",
                order.getDispatchCode(), currentStatus, targetStatus, tenantId);

        return DispatchResponseDTO.fromEntity(saved);
    }

    @Override
    @Transactional
    public DispatchResponseDTO acknowledgeDispatch(UUID id, UUID responderId) {
        String note = (responderId != null) ? "Acknowledged by responder: " + responderId : "Acknowledged by responder";
        DispatchResponseDTO response = updateDispatchStatus(id, new DispatchStatusUpdateRequest(DispatchStatus.ACKNOWLEDGED, note));

        if (responderId != null) {
            UUID tenantId = TenantContext.getTenantId();
            dispatchOrderRepository.findByIdAndTenantId(id, tenantId).ifPresent(order -> {
                if (!order.getAssignedResponderIds().contains(responderId)) {
                    order.getAssignedResponderIds().add(responderId);
                    dispatchOrderRepository.save(order);
                }
            });
        }

        return response;
    }

    @Override
    public List<DispatchStatusHistoryDTO> getDispatchHistory(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        if (!dispatchOrderRepository.existsByIdAndTenantId(id, tenantId)) {
            throw new ResourceNotFoundException("Dispatch order not found: " + id);
        }
        return dispatchStatusHistoryRepository.findAllByDispatchOrderIdAndTenantIdOrderByTransitionedAtAsc(id, tenantId).stream()
                .map(DispatchStatusHistoryDTO::fromEntity)
                .toList();
    }

    @Override
    public DispatchRecommendationResponse getRecommendations(DispatchRecommendationRequest request) {
        // Validate incident exists for tenant
        incidentService.getIncident(request.incidentId());

        List<DispatchRecommendationResponse.RecommendedUnit> units = new ArrayList<>();
        int responderCount;
        double estimatedDistanceMeters;
        long estimatedResponseSeconds;
        UUID recommendedStationId;
        String stationName;

        IncidentSeverity severity = request.severity();
        IncidentType incidentType = request.incidentType();

        if (severity == IncidentSeverity.CRITICAL) {
            recommendedStationId = UUID.nameUUIDFromBytes("STATION-HQ-CENTRAL".getBytes());
            stationName = "Central Fire Headquarters (Station 1)";
            estimatedDistanceMeters = 2500.0;
            estimatedResponseSeconds = 300;
            units.add(new DispatchRecommendationResponse.RecommendedUnit("PUMPER_ENGINE", 2, "Primary Fire Suppression"));
            units.add(new DispatchRecommendationResponse.RecommendedUnit("AERIAL_LADDER", 1, "Elevated Rescue & Ventilation"));
            if (incidentType == IncidentType.HAZMAT_LEAK || incidentType == IncidentType.INDUSTRIAL_HAZARD) {
                units.add(new DispatchRecommendationResponse.RecommendedUnit("HAZMAT_TENDER", 1, "Hazardous Material Containment"));
            } else {
                units.add(new DispatchRecommendationResponse.RecommendedUnit("WATER_TENDER", 1, "Bulk Water Supply"));
            }
            responderCount = 14;
        } else if (severity == IncidentSeverity.HIGH) {
            recommendedStationId = UUID.nameUUIDFromBytes("STATION-NORTH-02".getBytes());
            stationName = "North Division Fire Station 2";
            estimatedDistanceMeters = 3200.0;
            estimatedResponseSeconds = 420;
            units.add(new DispatchRecommendationResponse.RecommendedUnit("PUMPER_ENGINE", 1, "Primary Fire Suppression"));
            units.add(new DispatchRecommendationResponse.RecommendedUnit("WATER_TENDER", 1, "Water Supply"));
            if (incidentType == IncidentType.RESCUE_OPERATION) {
                units.add(new DispatchRecommendationResponse.RecommendedUnit("RESCUE_TENDER", 1, "Technical Extraction"));
            }
            responderCount = 8;
        } else if (severity == IncidentSeverity.MEDIUM) {
            recommendedStationId = UUID.nameUUIDFromBytes("STATION-EAST-03".getBytes());
            stationName = "East Division Fire Station 3";
            estimatedDistanceMeters = 4100.0;
            estimatedResponseSeconds = 480;
            units.add(new DispatchRecommendationResponse.RecommendedUnit("PUMPER_ENGINE", 1, "Initial Attack"));
            responderCount = 5;
        } else {
            recommendedStationId = UUID.nameUUIDFromBytes("STATION-WEST-04".getBytes());
            stationName = "West Division Substation 4";
            estimatedDistanceMeters = 5200.0;
            estimatedResponseSeconds = 600;
            units.add(new DispatchRecommendationResponse.RecommendedUnit("FIRST_RESPONSE_UNIT", 1, "Site Assessment & Minor Suppression"));
            responderCount = 3;
        }

        String rationale = "RECOMMENDATION ONLY (Development Baseline Assumption): Deterministic turnout sizing based on severity " +
                severity + " and incident type " + incidentType +
                ". Subject to municipal authority SOPs; does not auto-dispatch or reserve fleet apparatus.";

        return new DispatchRecommendationResponse(
                request.incidentId(),
                recommendedStationId,
                stationName,
                estimatedDistanceMeters,
                estimatedResponseSeconds,
                units,
                responderCount,
                rationale
        );
    }

    private String generateDispatchCode() {
        String datePart = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now());
        for (int i = 0; i < 10; i++) {
            int randomPart = ThreadLocalRandom.current().nextInt(10000, 99999);
            String candidate = "DSP-" + datePart + "-" + randomPart;
            if (!dispatchOrderRepository.existsByDispatchCode(candidate)) {
                return candidate;
            }
        }
        return "DSP-" + datePart + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String resolveCurrentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "DISPATCHER";
    }
}
