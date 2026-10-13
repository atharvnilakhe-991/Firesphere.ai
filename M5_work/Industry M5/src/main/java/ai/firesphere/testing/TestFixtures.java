package ai.firesphere.testing;

import ai.firesphere.contracts.fleet.VehicleLocationProviderContract;
import ai.firesphere.contracts.incident.IncidentAssignmentContract;
import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.contracts.integration.ExternalCadIntegrationContract;
import ai.firesphere.incident.dto.IncidentCreateRequest;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Standard test fixtures and mock factory utilities for Member 5 operational modules.
 */
public final class TestFixtures {

    public static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID DEFAULT_STATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    public static final UUID DEFAULT_OFFICER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    private TestFixtures() {}

    public static IncidentCreateRequest sampleIncidentCreateRequest() {
        return new IncidentCreateRequest(
                "Commercial High-Rise Fire Alarm",
                "Smoke detector triggered on 12th floor with waterflow gong activated",
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.CRITICAL,
                19.0760,
                72.8777,
                "Tower 3, Central Financial Center, Mumbai",
                "Bldg Engineer Suresh",
                "+919876543210",
                DEFAULT_STATION_ID,
                List.of("ENGINE-1", "LADDER-2")
        );
    }

    public static IncidentAssignmentContract.StationOfficerDTO sampleOfficer() {
        return new IncidentAssignmentContract.StationOfficerDTO(
                DEFAULT_OFFICER_ID,
                "BC-401",
                "Battalion Chief Sarah Vance",
                "BATTALION_CHIEF",
                DEFAULT_STATION_ID,
                true
        );
    }

    public static VehicleLocationProviderContract.VehicleTelematicsDTO sampleTelematics(UUID vehicleId) {
        return new VehicleLocationProviderContract.VehicleTelematicsDTO(
                vehicleId,
                19.0760,
                72.8777,
                48.5,
                90.0,
                85.0,
                100.0,
                Instant.now()
        );
    }

    public static AIInspectionResultProviderContract.AIFindingDTO sampleAIFinding(UUID findingId) {
        return new AIInspectionResultProviderContract.AIFindingDTO(
                findingId,
                UUID.randomUUID(),
                "BLOCKED_EMERGENCY_EXIT",
                0.94,
                "HIGH",
                "https://s3.firesphere.ai/evidence/exit_block_01.jpg",
                "{\"x\": 120, \"y\": 80, \"width\": 300, \"height\": 450}",
                "PENDING_REVIEW",
                null,
                null,
                Instant.now(),
                null
        );
    }

    public static ExternalCadIntegrationContract.CadIncidentDTO sampleCadIncident(UUID incidentId) {
        return new ExternalCadIntegrationContract.CadIncidentDTO(
                incidentId,
                "INC-20261005-001",
                "STRUCTURAL_FIRE",
                "CRITICAL",
                19.0760,
                72.8777,
                "Tower 3, Central Financial Center, Mumbai",
                "Suresh",
                "+919876543210",
                "Commercial structure fire reported"
        );
    }
}
