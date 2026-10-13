package ai.firesphere.testing;

import ai.firesphere.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TestingPackageTests {

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
    }

    @Test
    void testMockSecurityHelper() {
        UUID tenantId = UUID.randomUUID();
        MockSecurityHelper.setupSecurityContext(tenantId, "captain_miller", List.of("DISPATCHER", "STATION_OFFICER"));

        assertEquals(tenantId, TenantContext.getTenantId());
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("captain_miller", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DISPATCHER")));

        MockSecurityHelper.clearSecurityContext();
        assertNull(TenantContext.getTenantId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testTestFixtures() {
        var incidentReq = TestFixtures.sampleIncidentCreateRequest();
        assertNotNull(incidentReq);
        assertEquals("Commercial High-Rise Fire Alarm", incidentReq.title());

        var officer = TestFixtures.sampleOfficer();
        assertNotNull(officer);
        assertEquals("BC-401", officer.badgeNumber());

        var telematics = TestFixtures.sampleTelematics(UUID.randomUUID());
        assertNotNull(telematics);
        assertEquals(48.5, telematics.speedKmh());

        var finding = TestFixtures.sampleAIFinding(UUID.randomUUID());
        assertNotNull(finding);
        assertEquals("BLOCKED_EMERGENCY_EXIT", finding.findingType());

        var cad = TestFixtures.sampleCadIncident(UUID.randomUUID());
        assertNotNull(cad);
        assertEquals("INC-20261005-001", cad.incidentNumber());
    }
}
