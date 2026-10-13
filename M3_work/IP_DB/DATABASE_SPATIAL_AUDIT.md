# FireSphere.ai — PostGIS Spatial & GIS Subsystem Audit

**SOURCE DOCUMENT**: `FireSphere_Database_Documentation_v2.xml` (Sections 2.1, 4, 6, 8.2, 10.1)  
**STATUS**: `FORENSIC AUDIT COMPLETED`  
**SPATIAL ENGINE**: PostGIS 3.4+ on PostgreSQL 16+  
**COORDINATE REFERENCE SYSTEM (CRS)**: WGS 84 (SRID 4326)

---

## 1. Spatial Entity & Column Verification Matrix

| Table | Column | Spatial Type | SRID | Stated Spatial Purpose | Expected Query Workload | Spatial Index | Source Section | Status |
|---|---|---|---|---|---|---|---|---|
| `fire_stations` | `location` | `GEOMETRY(Point, 4326)` | 4326 | Geographic coordinates of fire station HQ | Station proximity lookups, KNN nearest station | `USING GIST (location)` | 4.21, 8.2, 10.1 | CONFIRMED / ENHANCED |
| `fire_stations` | `jurisdiction_geom` | `GEOMETRY(Polygon, 4326)` | 4326 | Primary operational response boundary | `ST_Contains(jurisdiction_geom, incident_location)` | `USING GIST (jurisdiction_geom)` | 4.21, 8.2, 10.1 | CONFIRMED / ENHANCED |
| `hydrants` | `location` | `GEOMETRY(Point, 4326)` | 4326 | Exact GPS position of fire hydrant | KNN Nearest Hydrant search (`<->` operator) in sub-5ms | `USING GIST (location)` | 4.24, 8.2, 10.1 | CONFIRMED / ENHANCED |
| `water_sources` | `location` | `GEOMETRY(Point, 4326)` | 4326 | Lakes, municipal reservoirs, suction tanks | Emergency water supply radius queries (`ST_DWithin`) | `USING GIST (location)` | 4.56, 8.2, 10.1 | CONFIRMED / ENHANCED |
| `buildings` | `location` | `GEOMETRY(Point, 4326)` | 4326 | Building centroid point | Map plotting, distance to nearest station/hydrant | `USING GIST (location)` | 4.14, 8.2 | INFERRED — NOT AUTHORITATIVE |
| `buildings` | `footprint_geom` | `GEOMETRY(Polygon, 4326)` | 4326 | Structural property boundary footprint | Building area calculation, spatial containment | `USING GIST (footprint_geom)` | 4.14, 8.2 | INFERRED — NOT AUTHORITATIVE |
| `incidents` | `location` | `GEOMETRY(Point, 4326)` | 4326 | Emergency 911/112 scene coordinates | Station dispatch routing, hazard buffer calculation | `USING GIST (location)` | 4.26, 8.2 | INFERRED — NOT AUTHORITATIVE |
| `incident_timeline` | `telemetry_coordinates` | `GEOMETRY(Point, 4326)` | 4326 | Geo-stamped milestone event location | Scene command playback, breadcrumb reconstruction | `USING GIST (telemetry_coordinates)` | 6.17, 8.2 | DOCUMENTED |
| `vehicle_location_history` | `location` | `GEOMETRY(Point, 4326)` | 4326 | AVL high-frequency apparatus GPS stream | Real-time vehicle tracking, trajectory playback | `CREATE INDEX idx_veh_loc_hist_geom USING GIST (location)` | 6.20, 8.2 | DOCUMENTED |
| `inspection_photos` | `capture_location` | `GEOMETRY(Point, 4326)` | 4326 | Geotagged site photograph coordinates | Geo-fencing verification of on-site inspection | `USING GIST (capture_location)` | 4.28 | INFERRED — NOT AUTHORITATIVE |
| `complaints` | `complaint_location` | `GEOMETRY(Point, 4326)` | 4326 | Reported violation scene coordinates | Heatmap density analysis, ward officer assignment | `USING GIST (complaint_location)` | 4.16 | INFERRED — NOT AUTHORITATIVE |
| `zones` | `boundary_geom` | `GEOMETRY(Polygon, 4326)` | 4326 | Geographic fire operational zone boundaries | Point-in-polygon assignment (`ST_Within`) | `USING GIST (boundary_geom)` | 4.60, 8.2 | INFERRED — NOT AUTHORITATIVE |
| `beats` | `patrol_area_geom` | `GEOMETRY(MultiPolygon, 4326)` | 4326 | Neighborhood-level patrol & inspection territory | Territory coverage analysis, beat assignment | `USING GIST (patrol_area_geom)` | 4.13, 8.2 | INFERRED — NOT AUTHORITATIVE |
| `divisions` | `divisional_boundary` | `GEOMETRY(MultiPolygon, 4326)` | 4326 | Regional municipal division boundaries | High-level regional containment | `USING GIST (divisional_boundary)` | 4.19, 8.2 | INFERRED — NOT AUTHORITATIVE |

---

## 2. PostGIS Functions & Queries Explicitly Mandated

### 1. Nearest Hydrant Locator (KNN Distance Operator)
- **Documented Query**:
  ```sql
  SELECT id, hydrant_code, 
         ST_Distance(location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_meters 
  FROM firesphere.hydrants 
  WHERE status = 'OPERATIONAL' 
  ORDER BY location <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) 
  LIMIT 5;
  ```
- **Performance Requirement**: Sub-5ms response over 100,000+ hydrants using `GIST` bounding-box distance indexing.

### 2. Turnout Station Routing (Spatial Containment)
- **Documented Query Pattern**:
  ```sql
  SELECT s.id, s.name, s.station_code 
  FROM firesphere.fire_stations s 
  WHERE ST_Contains(s.jurisdiction_geom, :incident_geom);
  ```

### 3. Hazard Isolation Buffer (Explosion / Hazmat Perimeter)
- **Documented Query Pattern**:
  ```sql
  SELECT ST_Buffer(:incident_geom::geography, :radius_meters);
  ```

### 4. Flat Coordinate Migration Trigger
- **Documented Trigger**:
  `fn_sync_lat_long_to_geom` automatically synchronizes legacy numeric `latitude` and `longitude` fields to PostGIS `location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)`.
