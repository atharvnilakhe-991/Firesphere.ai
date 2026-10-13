package ai.firesphere.fleet.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "fleet_vehicles", indexes = {
        @Index(name = "idx_vehicles_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_vehicles_reg_number", columnList = "registration_number", unique = true),
        @Index(name = "idx_vehicles_tenant_station", columnList = "tenant_id, assigned_station_id"),
        @Index(name = "idx_vehicles_tenant_type", columnList = "tenant_id, vehicle_type")
})
public class Vehicle extends BaseAuditableEntity {

    @Column(name = "registration_number", nullable = false, unique = true, length = 32)
    private String registrationNumber;

    @Column(name = "call_sign", nullable = false, length = 50)
    private String callSign;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 50)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VehicleStatus status;

    @Column(name = "assigned_station_id", nullable = false)
    private UUID assignedStationId;

    @Column(name = "make", length = 50)
    private String make;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    @Column(name = "water_capacity_liters")
    private Integer waterCapacityLiters;

    @Column(name = "foam_capacity_liters")
    private Integer foamCapacityLiters;

    @Column(name = "pump_capacity_lpm")
    private Integer pumpCapacityLpm;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "fuel_percent")
    private Double fuelPercent;

    @Column(name = "water_level_percent")
    private Double waterLevelPercent;

    @Column(name = "operational_notes", length = 1000)
    private String operationalNotes;

    public Vehicle() {
        this.status = VehicleStatus.AVAILABLE;
        this.fuelPercent = 100.0;
        this.waterLevelPercent = 100.0;
    }

    public boolean isAvailable() {
        return this.status == VehicleStatus.AVAILABLE;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getCallSign() {
        return callSign;
    }

    public void setCallSign(String callSign) {
        this.callSign = callSign;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public UUID getAssignedStationId() {
        return assignedStationId;
    }

    public void setAssignedStationId(UUID assignedStationId) {
        this.assignedStationId = assignedStationId;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getManufactureYear() {
        return manufactureYear;
    }

    public void setManufactureYear(Integer manufactureYear) {
        this.manufactureYear = manufactureYear;
    }

    public Integer getWaterCapacityLiters() {
        return waterCapacityLiters;
    }

    public void setWaterCapacityLiters(Integer waterCapacityLiters) {
        this.waterCapacityLiters = waterCapacityLiters;
    }

    public Integer getFoamCapacityLiters() {
        return foamCapacityLiters;
    }

    public void setFoamCapacityLiters(Integer foamCapacityLiters) {
        this.foamCapacityLiters = foamCapacityLiters;
    }

    public Integer getPumpCapacityLpm() {
        return pumpCapacityLpm;
    }

    public void setPumpCapacityLpm(Integer pumpCapacityLpm) {
        this.pumpCapacityLpm = pumpCapacityLpm;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(Double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(Double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public Double getFuelPercent() {
        return fuelPercent;
    }

    public void setFuelPercent(Double fuelPercent) {
        this.fuelPercent = fuelPercent;
    }

    public Double getWaterLevelPercent() {
        return waterLevelPercent;
    }

    public void setWaterLevelPercent(Double waterLevelPercent) {
        this.waterLevelPercent = waterLevelPercent;
    }

    public String getOperationalNotes() {
        return operationalNotes;
    }

    public void setOperationalNotes(String operationalNotes) {
        this.operationalNotes = operationalNotes;
    }
}
