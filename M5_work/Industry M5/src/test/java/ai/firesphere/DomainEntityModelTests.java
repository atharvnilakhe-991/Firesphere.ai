package ai.firesphere;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.model.DispatchOrder;
import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.fleet.model.Vehicle;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.gis.model.Hydrant;
import ai.firesphere.incident.model.Incident;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.inventory.model.InventoryItem;
import ai.firesphere.inventory.model.StockMovement;
import ai.firesphere.inventory.model.StockMovementType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainEntityModelTests {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testIncidentEntityDefaultsAndPrePersist() {
        UUID customTenant = UUID.randomUUID();
        TenantContext.setTenantId(customTenant);

        Incident incident = new Incident();
        incident.setTitle("Substation Transformer Fire");
        incident.setIncidentType(IncidentType.ELECTRICAL_FIRE);
        incident.setSeverity(IncidentSeverity.HIGH);
        incident.setLatitude(19.0760);
        incident.setLongitude(72.8777);
        incident.setAddress("Substation 12");
        incident.prePersist();

        assertEquals(customTenant, incident.getTenantId());
        assertEquals("SYSTEM", incident.getCreatedBy());
        assertEquals(IncidentStatus.REPORTED, incident.getStatus());
        assertNotNull(incident.getReportedAt());
    }

    @Test
    void testDispatchOrderDefaultsAndPrePersist() {
        DispatchOrder order = new DispatchOrder();
        order.setIncidentId(UUID.randomUUID());
        order.setStationId(UUID.randomUUID());
        order.setDispatchCode("DSP-2026-001");
        order.prePersist();

        assertNotNull(order.getTenantId());
        assertEquals(DispatchStatus.CREATED, order.getStatus());
        assertNotNull(order.getDispatchedAt());
    }

    @Test
    void testFleetVehicleDefaults() {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("MH-01-FS-101");
        vehicle.setCallSign("ENGINE-1");
        vehicle.setVehicleType(VehicleType.PUMPER_ENGINE);
        vehicle.setAssignedStationId(UUID.randomUUID());

        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());
        assertEquals(100.0, vehicle.getFuelPercent());
        assertEquals(100.0, vehicle.getWaterLevelPercent());
    }

    @Test
    void testInventoryAndStockMovementDefaults() {
        InventoryItem item = new InventoryItem();
        item.setSku("HOSE-50M-65MM");
        item.setName("Delivery Fire Hose 65mm x 50m");
        item.setCategory("HOSE_NOZZLE");
        item.setStationId(UUID.randomUUID());

        assertEquals(0, item.getQuantityOnHand());
        assertEquals(5, item.getMinimumThreshold());
        assertEquals("UNITS", item.getUnitOfMeasure());

        StockMovement movement = new StockMovement();
        movement.setItemId(UUID.randomUUID());
        movement.setMovementType(StockMovementType.ISSUE);
        movement.setQuantity(2);
        assertEquals(2, movement.getQuantity());
    }

    @Test
    void testHydrantDefaults() {
        Hydrant hydrant = new Hydrant();
        hydrant.setHydrantCode("HYD-401");
        hydrant.setLatitude(19.0760);
        hydrant.setLongitude(72.8777);

        assertTrue(hydrant.getOperable());
    }
}
