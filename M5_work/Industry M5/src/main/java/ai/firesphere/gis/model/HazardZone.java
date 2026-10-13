package ai.firesphere.gis.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import org.locationtech.jts.geom.Geometry;

@Entity
@Table(name = "gis_hazard_zones", indexes = {
        @Index(name = "idx_hazard_tenant", columnList = "tenant_id"),
        @Index(name = "idx_hazard_coords", columnList = "center_lat, center_lon")
})
public class HazardZone extends BaseAuditableEntity {

    @Column(name = "zone_code", length = 64)
    private String zoneCode;

    @Column(name = "hazard_name", nullable = false, length = 150)
    private String hazardName;

    @Column(name = "hazard_type", nullable = false, length = 60)
    private String hazardType; // CHEMICAL, LPG_STORAGE, HIGH_VOLTAGE, COLLAPSE_RISK

    @Column(name = "center_lat", nullable = false)
    private Double centerLatitude;

    @Column(name = "center_lon", nullable = false)
    private Double centerLongitude;

    @Column(name = "radius_meters", nullable = false)
    private Double radiusMeters;

    @Column(name = "geom")
    private Geometry geom;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "severity", nullable = false, length = 30)
    private String severity; // HIGH, CRITICAL

    @Column(name = "evacuation_protocol", length = 1000)
    private String evacuationProtocol;

    public HazardZone() {
        this.active = true;
    }

    @PrePersist
    @Override
    public void prePersist() {
        super.prePersist();
        synchronizeGeometry();
    }

    @PreUpdate
    public void preUpdate() {
        synchronizeGeometry();
    }

    public void synchronizeGeometry() {
        if (this.geom != null) {
            if (this.centerLatitude == null || this.centerLongitude == null) {
                this.centerLatitude = this.geom.getCentroid().getY();
                this.centerLongitude = this.geom.getCentroid().getX();
            }
        } else if (this.centerLatitude != null && this.centerLongitude != null && this.radiusMeters != null && this.radiusMeters > 0) {
            ai.firesphere.gis.util.GeometryUtils.validateCoordinates(this.centerLatitude, this.centerLongitude);
            this.geom = ai.firesphere.gis.util.GeometryUtils.createCircularPolygon(this.centerLongitude, this.centerLatitude, this.radiusMeters, 32);
        }
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public void setZoneCode(String zoneCode) {
        this.zoneCode = zoneCode;
    }

    public Geometry getGeom() {
        return geom;
    }

    public void setGeom(Geometry geom) {
        this.geom = geom;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getHazardName() {
        return hazardName;
    }

    public void setHazardName(String hazardName) {
        this.hazardName = hazardName;
    }

    public String getHazardType() {
        return hazardType;
    }

    public void setHazardType(String hazardType) {
        this.hazardType = hazardType;
    }

    public Double getCenterLatitude() {
        return centerLatitude;
    }

    public void setCenterLatitude(Double centerLatitude) {
        this.centerLatitude = centerLatitude;
    }

    public Double getCenterLongitude() {
        return centerLongitude;
    }

    public void setCenterLongitude(Double centerLongitude) {
        this.centerLongitude = centerLongitude;
    }

    public Double getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getEvacuationProtocol() {
        return evacuationProtocol;
    }

    public void setEvacuationProtocol(String evacuationProtocol) {
        this.evacuationProtocol = evacuationProtocol;
    }
}
