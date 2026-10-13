# FireSphere.ai — Member 5: GIS / PostGIS Operational Mapping Module
**Document Version:** 1.0.0  
**Phase:** 6 — GIS / PostGIS Operational Mapping Module  
**Status:** Implemented, Tested & Verified  
**Runtime:** Java 21 LTS | Spring Boot 3.3.4 | PostgreSQL + PostGIS / H2 (Test)  

---

> [!IMPORTANT]
> **Real routing/navigation is NOT implemented in Phase 6.**  
> Route calculations use a deterministic development baseline mock contract (`MapRoutingProviderContract` / `MockMapProvider`). External navigation services (Google Maps, OSRM, Mapbox, GraphHopper) are strictly excluded from Phase 6.

---

## 1. GIS Architecture

The GIS / PostGIS Operational Mapping module provides spatial data management, indexing, and geometric computation for emergency operational personnel, dispatchers, and field units.

The module follows strict enterprise layering:

$$\begin{cases} \text{HydrantController} \\ \text{HazardZoneController} \\ \text{GisRoutingController} \end{cases} \longrightarrow \text{GisOperationalService} \longrightarrow \begin{cases} \text{HydrantRepository} \\ \text{HazardZoneRepository} \\ \text{MapRoutingProviderContract} \end{cases} \longrightarrow \text{PostgreSQL / PostGIS}$$

* **Controller Layer**: REST APIs exposed under `/api/v1/gis/hydrants`, `/api/v1/gis/hazard-zones`, and `/api/v1/gis/routes`.
* **Service Layer (`GisOperationalServiceImpl`)**: Transaction boundaries, multi-tenant scoping via `TenantContext`, strict coordinate/radius bounds validation, bidirectional geometry-coordinate synchronization, and geodesic distance calculations.
* **Repository Layer (`HydrantRepository`, `HazardZoneRepository`)**: Spring Data JPA repositories with spatial HQL functions (`distance`, `contains`, `intersects`) backed by PostGIS spatial indexes.
* **Domain Entity Layer (`Hydrant`, `HazardZone`)**: JPA entities storing both numeric coordinates and JTS geometric types (`Point`, `Geometry`/`Polygon`) with WGS84 SRID 4326.
* **Database Layer**: PostgreSQL with PostGIS spatial extension enabled (`CREATE EXTENSION IF NOT EXISTS postgis;`), GIST indexes, and Flyway migration V6.

---

## 2. PostGIS Setup

PostGIS spatial extensions and indexes are provisioned via Flyway migration [`V6__gis_postgis_operational_mapping.sql`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/resources/db/migration/V6__gis_postgis_operational_mapping.sql):

1. **Extension Enablement**:
   ```sql
   CREATE EXTENSION IF NOT EXISTS postgis;
   ```
2. **Column Additions**:
   * `gis_hydrants.geom`: `GEOMETRY(Point, 4326)`
   * `gis_hazard_zones.geom`: `GEOMETRY(Geometry, 4326)`
   * `gis_hazard_zones.zone_code`: `VARCHAR(64)`
   * `gis_hazard_zones.is_active`: `BOOLEAN NOT NULL DEFAULT TRUE`
3. **Geometry Backfill**: Existing rows are populated with spatial points from latitude and longitude via `ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)`.

---

## 3. Spatial Reference System (SRS)

All spatial geometries, coordinates, and queries use **WGS84 (World Geodetic System 1984)** with **SRID 4326**:
* **Coordinate Order**: JTS `Coordinate(x, y)` corresponds to `(longitude, latitude)`.
* **Latitude Range**: $[-90.0, +90.0]$
* **Longitude Range**: $[-180.0, +180.0]$
* Coordinates are strictly validated on input. Any NaN, Infinite, or out-of-range values are rejected immediately with HTTP 400 (`VALIDATION_FAILED`).

---

## 4. Hydrant Model

The [`Hydrant`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/gis/model/Hydrant.java) entity represents physical fire hydrants and water sources:

* `id`: UUID (Primary Key)
* `tenantId`: UUID (Multi-tenant partition key)
* `hydrantCode`: String(32) (Unique per tenant, normalized to uppercase)
* `latitude`: Double (WGS84 latitude)
* `longitude`: Double (WGS84 longitude)
* `geom`: `org.locationtech.jts.geom.Point` (Spatial point representation with SRID 4326)
* `pressurePsi`: Double (Static/residual water pressure in PSI)
* `flowRateLpm`: Double (Discharge flow capacity in Liters Per Minute)
* `operable`: Boolean (Operational readiness status; default `true`)
* `address`: String(300) (Street description or physical station landmark)
* **Lifecycle Synchronization**: `@PrePersist` and `@PreUpdate` automatically synchronize `geom` with `(latitude, longitude)`, ensuring geometry and coordinate numbers never silently disagree.

---

## 5. Hazard Zone Model

The [`HazardZone`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/gis/model/HazardZone.java) entity defines geographic perimeters containing hazardous materials, chemical storage, high voltage, or structural collapse risks:

* `id`: UUID (Primary Key)
* `tenantId`: UUID (Multi-tenant partition key)
* `zoneCode`: String(64) (Unique identifier per tenant, e.g., `HZ-CHEM-01`)
* `hazardName`: String(150) (Nomenclature of the site or facility)
* `hazardType`: String(60) (`CHEMICAL`, `LPG_STORAGE`, `HIGH_VOLTAGE`, `COLLAPSE_RISK`)
* `centerLatitude`: Double (Centroid/reference latitude)
* `centerLongitude`: Double (Centroid/reference longitude)
* `radiusMeters`: Double (Standoff or evacuation radius)
* `geom`: `org.locationtech.jts.geom.Geometry` (Polygonal perimeter geometry, SRID 4326)
* `active`: Boolean (Operational active status; default `true`)
* `severity`: String(30) (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`)
* `evacuationProtocol`: String(1000) (Emergency actions, PPE requirements, or standoff protocols)
* **Geometry Generation**: If an explicit WKT polygon is provided, it is parsed and validated. If only center coordinates and radius are supplied, a 32-point circular buffer polygon is automatically constructed and synchronized.

---

## 6. Spatial Indexes

PostGIS Generalized Search Tree (GiST) spatial indexes are established to accelerate spatial operations:

```sql
CREATE INDEX IF NOT EXISTS idx_gis_hydrants_geom ON gis_hydrants USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_hazard_zones_geom ON gis_hazard_zones USING GIST (geom);
```

* **Purpose**: Allows bounding-box pruning (`ST_DWithin`, `ST_Distance`, `ST_Contains`, `ST_Intersects`) in $O(\log N)$ time rather than full-table scans.
* Supporting B-Tree indexes on `(tenant_id, is_operable)` and `(tenant_id, is_active)` provide rapid multi-tenant filtering.

---

## 7. Nearby Query Behavior (`/api/v1/gis/hydrants/nearby`)

* **Input Parameters**: `latitude`, `longitude`, `radiusMeters`, optional `operableOnly`.
* **Execution Flow**:
  1. Validates coordinates and radius ($0 < \text{radiusMeters} \le 100,000$).
  2. Projects radius in meters to an approximate geodetic degree envelope with a safety margin.
  3. Uses spatial repository query with database-side index filtering (`distance(h.geom, :point) <= :maxDistance`).
  4. Computes exact Great Circle distance in meters for each match using Haversine geodesic mathematics.
  5. Filters out any entity exceeding `radiusMeters`.
  6. Orders results ascending by distance (`distanceMeters ASC`).
* **Operable Filter**: When `operableOnly=true`, inoperable/damaged hydrants are excluded.

---

## 8. Point-in-Zone Behavior (`/api/v1/gis/hazard-zones/contains`)

* **Input Parameters**: `latitude`, `longitude`.
* **Execution Flow**:
  1. Validates coordinate bounds.
  2. Constructs a WGS84 JTS Point.
  3. Executes database spatial query: `contains(z.geom, :point) = true OR intersects(z.geom, :point) = true`.
  4. Returns all active hazard zones containing or intersecting the coordinate, including zone ID, zone code, name, hazard type, severity, and protocol.

---

## 9. Routing Contract (`MapRoutingProviderContract`)

The routing capability is defined as a clean contract interface:
* Method: `calculateEmergencyRoute(startLat, startLon, destLat, destLon)`
* Injected into `GisOperationalServiceImpl` as a dependency.
* Returns: distance in meters, estimated duration in seconds, waypoints, and polyline.

---

## 10. Mock-Routing Limitations

* **Implementation**: [`MockMapProvider`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/mock/MockMapProvider.java).
* **Classification**: **Development Baseline / Mock Routing**.
* **Behavior**: Deterministic synthetic route generation based on Haversine distance, average emergency vehicle speed (50 km/h with priority sirens), and 3 standard waypoints (depot exit, corridor transit, scene arrival).
* **Limitations**: Does NOT compute turn-by-turn road networks, live traffic, road closures, or actual topography. It makes zero external network calls.

---

## 11. Coordinate Validation

Coordinate validation is strictly enforced across all layers (DTO validation and service validation):
* Latitude: $[-90.0, +90.0]$
* Longitude: $[-180.0, +180.0]$
* Search Radius: $(0.0, 100,000.0]$ meters
* Rejection: `IllegalArgumentException` mapped to HTTP 400 `VALIDATION_FAILED`.

---

## 12. Tenant Isolation

* Enforced via [`TenantContext.getRequiredTenantId()`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/common/tenant/TenantContext.java).
* Every query restricts results by `tenant_id`.
* Hydrants and hazard zones belonging to Tenant A are never returned to Tenant B in list queries, proximity searches, containment queries, or direct ID lookups.

---

## 13. API Catalog

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/gis/hydrants` | Register new hydrant with coordinates |
| `GET` | `/api/v1/gis/hydrants/{id}` | Retrieve hydrant by ID |
| `GET` | `/api/v1/gis/hydrants` | List hydrants (paginated, optional `operable` filter) |
| `PUT` | `/api/v1/gis/hydrants/{id}` | Update hydrant coordinates or metadata |
| `GET` | `/api/v1/gis/hydrants/nearby` | Spatial proximity query within radius |
| `POST` | `/api/v1/gis/hazard-zones` | Create hazard zone (polygonal or circular buffer) |
| `GET` | `/api/v1/gis/hazard-zones/{id}` | Retrieve hazard zone by ID |
| `GET` | `/api/v1/gis/hazard-zones` | List hazard zones (paginated, optional `active` filter) |
| `PUT` | `/api/v1/gis/hazard-zones/{id}` | Update hazard zone parameters |
| `GET` | `/api/v1/gis/hazard-zones/contains` | Point-in-hazard-zone spatial containment query |
| `GET` | `/api/v1/gis/hazard-zones/nearby` | Spatial proximity query for nearby hazard zones |
| `POST` | `/api/v1/gis/routes/calculate` | Calculate mock emergency route (Contract baseline) |

---

## 14. Test Strategy

1. **Unit Testing (`GisOperationalServiceTest`)**: 13 tests verifying coordinate bounds, duplicate code conflict handling, pagination, WKT parsing, and deterministic routing.
2. **Spatial Proximity & Containment (`GisSpatialProximityIntegrationTest`)**: 8 tests verifying distance ordering, radius exclusion, operable filters, point-in-zone containment, perimeter boundary inclusion (`ST_Intersects`), invalid geometry rejection (self-intersecting/bowtie polygons and non-polygon types), coordinate-geometry consistency, and cross-tenant isolation.
3. **Controller MockMvc Tests (`GisControllerTest`)**: 10 tests verifying HTTP contracts, request validation, 201/200/400/404/409 response codes.
4. **H2 Spatial Query Verification (`SpatialQueryH2Test`)**: 2 tests validating JPA spatial function execution (`distance`, `intersects`) with JTS-backed H2 spatial aliases.
5. **Real PostGIS Container Suite (`PostGisIntegrationTest`)**: Testcontainers test targeting `postgis/postgis:16-3.4-alpine` validating live PostGIS extension, GiST indexes, `ST_DWithin` execution plans, and boundary semantics. Automatically skipped when Docker daemon is not detected in host environment.
6. **Full Regression Suite**: 186 total tests (183 passed, 0 failures, 0 errors, 3 skipped in PostGisIntegrationTest when Docker is absent). Build SUCCESS.

### Real PostGIS Environment Prerequisites & Execution

* **Docker required**: `YES`
* **Testcontainers required**: `YES`
* **Local PostgreSQL installation required**: `NO`
* **Local PostGIS installation required**: `NO`

Once Docker is running on the host workstation, execute the real PostGIS container suite:
```powershell
mvn test -Dtest=PostGisIntegrationTest
```
Or run the full test suite including both in-memory and live container tests:
```powershell
mvn clean test
```

---

## 15. Development Assumptions

1. Coordinates use standard decimal degrees in WGS84 (SRID 4326).
2. For circular hazard zones where explicit polygon vertices are not specified, a regular 32-sided polygon approximation is generated to allow spatial indexing.
3. Safe upper search radius limit is capped at 100 km (100,000 meters) to protect database resources.

---

## 16. Future GIS Extensions (Post-Phase 6)

* Integration with real OSM / OSRM routing engine or external navigation provider.
* Dynamic traffic and emergency vehicle route preemption.
* Live apparatus telematics GPS overlay.
* Real-time water network pressure telemetry (SCADA integration).
