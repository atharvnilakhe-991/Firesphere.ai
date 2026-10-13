# Phase: GIS Foundation

## 1. Overview & Objective
The **GIS Foundation** module provides standalone, independently testable geospatial capabilities for FireSphere.ai. It focuses on:
- Location models for properties, inspections, incidents, and facilities
- Strict GPS coordinate validation, accuracy categorisation, and malformed telemetry rejection
- Tactical map display packaging in deterministic mock mode without external tile service dependencies
- Proximity-based querying of nearby fire infrastructure (hydrants) reusing existing repositories
- Provider-neutral officer navigation handoff (RFC-5870 standard `geo:` URIs, Google Maps, Apple Maps, and Waze)
- Graceful offline/degraded behavior when external providers or repositories are unattached

---

## 2. Architecture & Design

The module is isolated under `ai.firesphere.gis.foundation`:

```
ai.firesphere.gis.foundation/
  ├── model/
  │    ├── LocationRecord.java           # Canonical location abstraction
  │    ├── LocationReferenceType.java     # PROPERTY, INSPECTION, INCIDENT, FACILITY, OTHER
  │    ├── LocationValidationStatus.java  # VALID, INVALID, UNVERIFIED, APPROXIMATE, POOR_ACCURACY
  │    ├── LocationAccuracyLevel.java     # HIGH (<=15m), MODERATE (15-50m), POOR (>50m), UNKNOWN
  │    └── LocationValidationResult.java  # Validation status, accuracy level, and error list
  ├── validation/
  │    └── GpsCoordinateValidator.java    # Strict coordinate, range [-90,90][-180,180], and sanity check
  ├── display/
  │    ├── MapDisplayDTO.java             # Bounding box, center coordinates, markers, provider status
  │    ├── MapMarkerDTO.java              # Marker styling, coordinate, category color, accuracy radius
  │    ├── MapProviderStatus.java         # MOCK_MODE, ACTIVE, UNAVAILABLE
  │    └── MapDisplayService.java         # Packaging map display metadata with mock/fallback controls
  ├── infrastructure/
  │    ├── NearbyInfrastructureDTO.java   # Nearby infrastructure response with nearest distance
  │    ├── InfrastructureItemDTO.java     # Hydrant details, distance in meters, operational status
  │    └── NearbyInfrastructureService.java # Queries HydrantRepository, calculates Haversine distance
  ├── navigation/
  │    ├── OfficerNavigationHandoffDTO.java # Destination, geo URI, Google/Apple/Waze URLs, status
  │    ├── NavigationHandoffStatus.java   # READY, INVALID_DESTINATION, MISSING_COORDINATES
  │    └── NavigationHandoffService.java  # Prepares provider-neutral intents without auto-launching
  ├── service/
  │    ├── GisFoundationService.java      # Unified facade contract
  │    └── GisFoundationServiceImpl.java  # Orchestration service implementation
  ├── controller/
  │    └── GisFoundationController.java   # REST API under /api/v1/gis/foundation
  └── dto/
       ├── LocationValidationRequest.java
       ├── MapDisplayRequest.java
       ├── NearbyInfrastructureRequest.java
       └── NavigationHandoffRequest.java
```

---

## 3. Location Validation & GPS Rules

Validation is strictly enforced by `GpsCoordinateValidator`:
1. **Latitude**: Must be within $[-90.0, +90.0]$. Rejects `null`, `NaN`, `Infinite`, and out-of-range values.
2. **Longitude**: Must be within $[-180.0, +180.0]$. Rejects `null`, `NaN`, `Infinite`, and out-of-range values.
3. **Accuracy Telemetry**:
   - $\le 15.0\text{ m}$: `HIGH` accuracy
   - $15.0 - 50.0\text{ m}$: `MODERATE` accuracy
   - $> 50.0\text{ m}$: `POOR` accuracy (flagged as `APPROXIMATE`)
   - $> 200.0\text{ m}$: Exceeds tactical dispatch limits (flagged as `POOR_ACCURACY`)
   - Missing/Null accuracy: Flagged as `UNVERIFIED` accuracy
4. **Data Integrity**: Invalid coordinates are NEVER silently replaced with fabricated defaults. Clear errors are returned.

---

## 4. Map Display & Mock Mode

- Defaults to `MapProviderStatus.MOCK_MODE`.
- No paid or external tile providers are contacted.
- Evaluates bounding box envelope ($\sim 500\text{ m}$ surrounding buffer) and builds styled markers based on entity type:
  - `PROPERTY`: `#38A169` (Green)
  - `INSPECTION`: `#3182CE` (Blue)
  - `INCIDENT`: `#E53E3E` (Red)
  - `FACILITY`: `#805AD5` (Purple)
- Offline Capability Flag: Explicitly set to `offlineCapable = false` to avoid inaccurate claims of offline raster tile cache.

---

## 5. Nearby Fire Infrastructure

- Reuses existing `HydrantRepository` and `GeometryUtils.calculateHaversineDistanceMeters`.
- Filters by `distance <= radiusMeters`, filters by `operableOnly` when requested, and sorts results by ascending distance.
- Safely handles empty results by returning `NearbyInfrastructureDTO.empty(...)` with HTTP 200 OK without crashing.
- Includes fixture fallback support for decoupled testing when the database is empty or disconnected.

---

## 6. Officer Navigation Handoff

Prepares provider-neutral intent URIs for dispatch and mobile terminals without launching external processes:
- **Standard RFC-5870 Geo URI**: `geo:lat,lng?q=lat,lng(label)`
- **Google Maps**: `https://www.google.com/maps/dir/?api=1&destination=lat,lng`
- **Apple Maps**: `https://maps.apple.com/?daddr=lat,lng&dirflg=d`
- **Waze**: `https://waze.com/ul?ll=lat,lng&navigate=yes`
- Validates destination coordinates before generating handoff; returns `INVALID_DESTINATION` or `MISSING_COORDINATES` on validation failure.

---

## 7. Failure and Degraded Behavior

| Scenario | System Behavior |
|---|---|
| Missing / null coordinates | Validation returns `valid: false` with detailed error messages. Controller returns 200 with `INVALID` status or 400 when Bean Validation fails. |
| Out of bounds coordinates | Rejected with boundary error. No default coordinates fabricated. |
| Map provider unavailable | Graceful fallback returning `MapProviderStatus.UNAVAILABLE` with informative status message and empty markers. |
| No nearby hydrants found | Returns empty list with `totalFound: 0` and `nearestDistanceMeters: null`. Does not throw exception. |
| Negative or zero search radius | Rejection with `IllegalArgumentException` explaining radius requirements. |

---

## 8. REST Endpoints (`/api/v1/gis/foundation`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/validate` | Validates latitude, longitude, and accuracy |
| `POST` | `/map-display` | Generates tactical map bounding box and marker pins |
| `POST` | `/nearby-infrastructure` | Queries nearby hydrants around coordinate point |
| `GET` | `/nearby-infrastructure` | Query-param variant (`latitude`, `longitude`, `radiusMeters`, `operableOnly`) |
| `POST` | `/navigation-handoff` | Generates universal navigation URIs and deep links |

---

## 9. Verification & Testing

Targeted GIS Foundation tests:
- `GisFoundationTest`: 16 tests covering valid, boundary, invalid latitude, invalid longitude, missing coordinates, NaN/Inf, accuracy levels, mock display, nearby infrastructure, empty results, navigation handoff, unavailable provider, and failure modes.
- `GisFoundationControllerTest`: 6 MockMvc integration tests covering all HTTP REST endpoints.
- Full test suite: **276 tests run, 273 passed, 0 failures, 0 errors, 3 skipped (PostGIS container tests paused)**.

```bash
# Run GIS Foundation tests
mvn test -Dtest=GisFoundationTest,GisFoundationControllerTest

# Run all GIS module tests
mvn test -Dtest=Gis*Test,SpatialQueryH2Test

# Run complete regression suite
mvn test
```

---

## 10. Limitations & Future Dependencies

- **Offline Tile Cache**: Raster/vector map tiles require local mbtiles/PBF storage which is outside the GIS Foundation scope.
- **Turn-by-turn Routing Engine**: Detailed waypoint polyline routing relies on `MapRoutingProviderContract` (OSRM/GraphHopper mock baseline).
- **PostgreSQL / PostGIS Verification**: Phase 6 Testcontainers verification remains paused awaiting Docker environment availability.
