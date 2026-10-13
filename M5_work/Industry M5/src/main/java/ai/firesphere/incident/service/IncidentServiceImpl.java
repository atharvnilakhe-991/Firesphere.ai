package ai.firesphere.incident.service;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.incident.dto.*;
import ai.firesphere.incident.model.*;
import ai.firesphere.incident.repository.IncidentRepository;
import ai.firesphere.incident.repository.IncidentStatusHistoryRepository;
import jakarta.persistence.criteria.Predicate;
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
@Transactional
public class IncidentServiceImpl implements IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentServiceImpl.class);

    /**
     * Strict lifecycle state transition map:
     * REPORTED -> VERIFIED -> DISPATCHED -> EN_ROUTE -> ON_SCENE -> UNDER_CONTROL -> RESOLVED -> CLOSED
     */
    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = Map.of(
            IncidentStatus.REPORTED, Set.of(IncidentStatus.VERIFIED),
            IncidentStatus.VERIFIED, Set.of(IncidentStatus.DISPATCHED),
            IncidentStatus.DISPATCHED, Set.of(IncidentStatus.EN_ROUTE),
            IncidentStatus.EN_ROUTE, Set.of(IncidentStatus.ON_SCENE),
            IncidentStatus.ON_SCENE, Set.of(IncidentStatus.UNDER_CONTROL),
            IncidentStatus.UNDER_CONTROL, Set.of(IncidentStatus.RESOLVED),
            IncidentStatus.RESOLVED, Set.of(IncidentStatus.CLOSED),
            IncidentStatus.CLOSED, Collections.emptySet()
    );

    private final IncidentRepository incidentRepository;
    private final IncidentStatusHistoryRepository historyRepository;

    public IncidentServiceImpl(IncidentRepository incidentRepository,
                               IncidentStatusHistoryRepository historyRepository) {
        this.incidentRepository = incidentRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    public IncidentResponseDTO createIncident(IncidentCreateRequest request) {
        validateCoordinates(request.latitude(), request.longitude());

        UUID tenantId = resolveTenantId();
        log.info("Creating incident for tenant {}: {}", tenantId, request.title());

        Incident incident = new Incident();
        incident.setTenantId(tenantId);
        incident.setIncidentNumber(generateUniqueIncidentNumber(tenantId));
        incident.setTitle(request.title().trim());
        incident.setDescription(request.description() != null ? request.description().trim() : null);
        incident.setIncidentType(request.incidentType());
        incident.setSeverity(request.severity());
        incident.setStatus(IncidentStatus.REPORTED);
        incident.setLatitude(request.latitude());
        incident.setLongitude(request.longitude());
        incident.setAddress(request.address().trim());
        incident.setCallerName(request.callerName() != null ? request.callerName().trim() : null);
        incident.setCallerPhone(request.callerPhone() != null ? request.callerPhone().trim() : null);
        incident.setAssignedStationId(request.assignedStationId());
        if (request.assignedResources() != null) {
            incident.setAssignedResources(new ArrayList<>(request.assignedResources()));
        }
        incident.setReportedAt(Instant.now());

        Incident saved = incidentRepository.save(incident);

        // Record initial status in history
        recordHistory(saved.getId(), null, IncidentStatus.REPORTED, "Initial emergency intake report");

        return IncidentResponseDTO.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponseDTO getIncident(UUID id) {
        UUID tenantId = resolveTenantId();
        Incident incident = incidentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));
        return IncidentResponseDTO.fromEntity(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponseDTO getIncidentByNumber(String incidentNumber) {
        UUID tenantId = resolveTenantId();
        Incident incident = incidentRepository.findByIncidentNumberAndTenantId(incidentNumber, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident with number", incidentNumber));
        return IncidentResponseDTO.fromEntity(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IncidentResponseDTO> getIncidents(
            IncidentStatus status,
            IncidentSeverity severity,
            IncidentType incidentType,
            UUID stationId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable) {

        UUID tenantId = resolveTenantId();

        Specification<Incident> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (incidentType != null) {
                predicates.add(cb.equal(root.get("incidentType"), incidentType));
            }
            if (stationId != null) {
                predicates.add(cb.equal(root.get("assignedStationId"), stationId));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("reportedAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("reportedAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Incident> page = incidentRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(IncidentResponseDTO::fromEntity));
    }

    @Override
    public IncidentResponseDTO updateIncident(UUID id, IncidentUpdateRequest request) {
        validateCoordinates(request.latitude(), request.longitude());

        UUID tenantId = resolveTenantId();
        Incident incident = incidentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new BusinessRuleViolationException("Cannot update an already closed incident.");
        }

        incident.setTitle(request.title().trim());
        incident.setDescription(request.description() != null ? request.description().trim() : null);
        incident.setIncidentType(request.incidentType());
        incident.setSeverity(request.severity());
        incident.setLatitude(request.latitude());
        incident.setLongitude(request.longitude());
        incident.setAddress(request.address().trim());
        incident.setCallerName(request.callerName() != null ? request.callerName().trim() : null);
        incident.setCallerPhone(request.callerPhone() != null ? request.callerPhone().trim() : null);
        incident.setAssignedStationId(request.assignedStationId());
        if (request.assignedResources() != null) {
            incident.setAssignedResources(new ArrayList<>(request.assignedResources()));
        }

        Incident updated = incidentRepository.save(incident);
        log.info("Incident {} attributes updated successfully for tenant {}", incident.getIncidentNumber(), tenantId);
        return IncidentResponseDTO.fromEntity(updated);
    }

    @Override
    public IncidentResponseDTO updateStatus(UUID id, IncidentStatusUpdateRequest request) {
        UUID tenantId = resolveTenantId();
        Incident incident = incidentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

        IncidentStatus currentStatus = incident.getStatus();
        IncidentStatus requestedStatus = request.status();

        if (currentStatus == requestedStatus) {
            log.warn("Incident {} is already in status {}", incident.getIncidentNumber(), currentStatus);
            return IncidentResponseDTO.fromEntity(incident);
        }

        Set<IncidentStatus> allowedNextStates = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowedNextStates.contains(requestedStatus)) {
            String errorMsg = String.format("Invalid status transition from %s to %s for incident %s",
                    currentStatus, requestedStatus, incident.getIncidentNumber());
            log.warn(errorMsg);
            throw new BusinessRuleViolationException(errorMsg);
        }

        incident.setStatus(requestedStatus);
        Instant now = Instant.now();

        switch (requestedStatus) {
            case VERIFIED -> incident.setVerifiedAt(now);
            case DISPATCHED -> incident.setDispatchedAt(now);
            case EN_ROUTE -> incident.setEnRouteAt(now);
            case ON_SCENE -> incident.setOnSceneAt(now);
            case UNDER_CONTROL -> incident.setUnderControlAt(now);
            case RESOLVED -> incident.setResolvedAt(now);
            case CLOSED -> {
                incident.setClosedAt(now);
                if (request.reason() != null && !request.reason().isBlank()) {
                    incident.setClosureRemarks(request.reason());
                }
            }
            default -> {}
        }

        Incident saved = incidentRepository.save(incident);
        recordHistory(saved.getId(), currentStatus, requestedStatus, request.reason());

        log.info("Incident {} status transitioned: {} -> {} (reason: {})",
                incident.getIncidentNumber(), currentStatus, requestedStatus, request.reason());

        return IncidentResponseDTO.fromEntity(saved);
    }

    @Override
    public IncidentResponseDTO closeIncident(UUID id, IncidentCloseRequest request) {
        UUID tenantId = resolveTenantId();
        Incident incident = incidentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new BusinessRuleViolationException("Incident is already closed.");
        }

        // Closing must only be allowed from RESOLVED state
        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new BusinessRuleViolationException(String.format(
                    "Incident cannot be closed directly from status: %s. It must be in RESOLVED state prior to closure.",
                    incident.getStatus()
            ));
        }

        if (request.remarks() == null || request.remarks().trim().isEmpty()) {
            throw new BusinessRuleViolationException("Closure remarks are required to close an incident.");
        }

        IncidentStatus prevStatus = incident.getStatus();
        incident.setStatus(IncidentStatus.CLOSED);
        incident.setClosedAt(Instant.now());
        incident.setClosureRemarks(request.remarks().trim());

        Incident closed = incidentRepository.save(incident);
        recordHistory(closed.getId(), prevStatus, IncidentStatus.CLOSED, request.remarks().trim());

        log.info("Incident {} formally CLOSED with remarks: '{}'", closed.getIncidentNumber(), request.remarks());
        return IncidentResponseDTO.fromEntity(closed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentStatusHistoryDTO> getIncidentHistory(UUID id) {
        UUID tenantId = resolveTenantId();
        // Verify incident exists under tenant first
        incidentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));

        return historyRepository.findAllByIncidentIdAndTenantIdOrderByTransitionedAtAsc(id, tenantId).stream()
                .map(IncidentStatusHistoryDTO::fromEntity)
                .toList();
    }

    private void recordHistory(UUID incidentId, IncidentStatus prev, IncidentStatus next, String reason) {
        String actor = getCurrentActor();
        IncidentStatusHistory history = new IncidentStatusHistory(incidentId, prev, next, actor, reason);
        historyRepository.save(history);
    }

    private String getCurrentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "SYSTEM";
    }

    private UUID resolveTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return tenantId;
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || latitude < -90.0 || latitude > 90.0) {
            throw new BusinessRuleViolationException("Latitude must be between -90.0 and +90.0 degrees.");
        }
        if (longitude == null || longitude < -180.0 || longitude > 180.0) {
            throw new BusinessRuleViolationException("Longitude must be between -180.0 and +180.0 degrees.");
        }
    }

    private String generateUniqueIncidentNumber(UUID tenantId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 10; i++) {
            int randomPart = ThreadLocalRandom.current().nextInt(10000, 99999);
            String candidate = "INC-" + datePart + "-" + randomPart;
            if (!incidentRepository.existsByIncidentNumber(candidate)) {
                return candidate;
            }
        }
        return "INC-" + datePart + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
