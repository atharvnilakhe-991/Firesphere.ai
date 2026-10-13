package ai.firesphere.contracts;

import ai.firesphere.contracts.gis.MapRoutingProviderContract;
import ai.firesphere.contracts.incident.IncidentAssignmentContract;
import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.contracts.integration.ExternalCadIntegrationContract;
import ai.firesphere.contracts.mock.*;
import ai.firesphere.contracts.notification.NotificationProviderContract;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractsMockTests {

    @Test
    void testMockIncidentAssignment() {
        IncidentAssignmentContract contract = new MockIncidentAssignmentService();
        var result = contract.assignIncident(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), List.of(UUID.randomUUID()));
        assertTrue(result.success());
        assertFalse(contract.getAvailableOfficers(UUID.randomUUID()).isEmpty());
    }

    @Test
    void testMockNotificationProvider() {
        NotificationProviderContract contract = new MockNotificationProvider();
        var pushReceipt = contract.sendPushNotification("fcm_token_123", "Emergency", "Fire reported", java.util.Map.of());
        assertEquals("DELIVERED", pushReceipt.deliveryStatus());
    }

    @Test
    void testMockMapProvider() {
        MapRoutingProviderContract contract = new MockMapProvider();
        var route = contract.calculateEmergencyRoute(19.0760, 72.8777, 19.0800, 72.8800);
        assertTrue(route.distanceMeters() > 0);
        assertFalse(route.waypoints().isEmpty());
    }

    @Test
    void testMockAIInspectionProvider_HumanInTheLoop() {
        AIInspectionResultProviderContract contract = new MockAIInspectionResultProvider();
        UUID findingId = UUID.randomUUID();
        var ingest = contract.ingestAIFinding(new AIInspectionResultProviderContract.AIFindingDTO(
                findingId, UUID.randomUUID(), "BLOCKED_EXIT", 0.94, "HIGH", "https://s3.firesphere.ai/evidence1.jpg", "{}", "PENDING", null, null, null, null
        ));
        assertTrue(ingest.accepted());

        var reviewed = contract.reviewFinding(findingId, "APPROVED", "Confirmed blocked emergency staircase", "OFFICER-44");
        assertEquals("APPROVED", reviewed.status());
        assertEquals("OFFICER-44", reviewed.reviewedBy());
    }

    @Test
    void testMockCadIntegration() {
        ExternalCadIntegrationContract contract = new MockExternalCadIntegrationService();
        var receipt = contract.transmitCadIncident(new ExternalCadIntegrationContract.CadIncidentDTO(
                UUID.randomUUID(), "INC-100", "FIRE", "HIGH", 19.0760, 72.8777, "Main St", "Alice", "+919999999999", "Flames seen"
        ));
        assertNotNull(receipt.externalCadIncidentId());
        assertEquals("TRANSMITTED", receipt.status());
    }
}
