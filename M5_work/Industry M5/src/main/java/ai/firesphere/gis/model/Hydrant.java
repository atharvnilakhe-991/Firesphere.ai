package ai.firesphere.gis.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "gis_hydrants", indexes = {
        @Index(name = "idx_hydrant_code", columnList = "tenant_id, hydrant_code", unique = true),
        @Index(name = "idx_hydrant_coords", columnList = "latitude, longitude")
})
public class Hydrant extends BaseAuditableEntity {

    @Column(name = "hydrant_code", nullable = false, length = 32)
    private String hydrantCode;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "geom")
    private Point geom;

    @Column(name = "pressure_psi")
    private Double pressurePsi;

    @Column(name = "flow_rate_lpm")
    private Double flowRateLpm;

    @Column(name = "is_operable", nullable = false)
    private Boolean operable;

    @Column(name = "address", length = 300)
    private String address;

    public Hydrant() {
        this.operable = true;
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
        if (this.latitude != null && this.longitude != null) {
            ai.firesphere.gis.util.GeometryUtils.validateCoordinates(this.latitude, this.longitude);
            this.geom = ai.firesphere.gis.util.GeometryUtils.createPoint(this.longitude, this.latitude);
        } else if (this.geom != null) {
            this.longitude = this.geom.getX();
            this.latitude = this.geom.getY();
        }
    }

    public Point getGeom() {
        return geom;
    }

    public void setGeom(Point geom) {
        this.geom = geom;
        if (geom != null) {
            this.longitude = geom.getX();
            this.latitude = geom.getY();
        }
    }

    public String getHydrantCode() {
        return hydrantCode;
    }

    public void setHydrantCode(String hydrantCode) {
        this.hydrantCode = hydrantCode;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
        if (this.latitude != null && this.longitude != null) {
            ai.firesphere.gis.util.GeometryUtils.validateCoordinates(this.latitude, this.longitude);
            this.geom = ai.firesphere.gis.util.GeometryUtils.createPoint(this.longitude, this.latitude);
        }
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
        if (this.latitude != null && this.longitude != null) {
            ai.firesphere.gis.util.GeometryUtils.validateCoordinates(this.latitude, this.longitude);
            this.geom = ai.firesphere.gis.util.GeometryUtils.createPoint(this.longitude, this.latitude);
        }
    }

    public Double getPressurePsi() {
        return pressurePsi;
    }

    public void setPressurePsi(Double pressurePsi) {
        this.pressurePsi = pressurePsi;
    }

    public Double getFlowRateLpm() {
        return flowRateLpm;
    }

    public void setFlowRateLpm(Double flowRateLpm) {
        this.flowRateLpm = flowRateLpm;
    }

    public Boolean getOperable() {
        return operable;
    }

    public void setOperable(Boolean operable) {
        this.operable = operable;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
