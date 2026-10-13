# FireSphere.ai — Member 5: Fire Store / Inventory Management Module
**Document Version:** 1.0.0  
**Phase:** 5 — Fire Store / Inventory Management Module  
**Status:** Implemented, Tested & Verified  
**Runtime:** Java 21 LTS | Spring Boot 3.3.4 | PostgreSQL + PostGIS / H2 (Test)  

---

## 1. Purpose & Scope

The Fire Store / Inventory Management module is the authoritative ledger and asset controller for emergency response inventory across all fire stations and response apparatus. It tracks firefighting consumables (foam agents, extinguishing powders), personal protective equipment (PPE), breathing apparatus (SCBA), hoses, nozzles, and rescue extrication tools.

Key architectural responsibilities:
* **Authoritative Stock Ledger**: Inventory owns item balances (`quantityOnHand`), reorder thresholds, batch tracking, and physical store placement without duplicate models.
* **Deterministic Stock Movements**: Every stock-altering action is recorded in an immutable chronological ledger (`inventory_stock_movements`), answering *what*, *when*, *who*, *why*, *how much*, and *resulting balance*.
* **Strict Quantity Invariants**: Negative stock is strictly disallowed; issue and adjustment operations reject requests that would deplete stock below zero.
* **Tenant Isolation**: Every item, query, and movement is partitioned by tenant UUID via [`TenantContext`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/common/tenant/TenantContext.java).
* **Decoupled Boundary**: Phase 2 Incident, Phase 3 Emergency Dispatch, and Phase 4 Fleet remain strictly locked. Inventory does not mutate external entities or activate automatic vehicle/dispatch allocations.

---

## 2. Architecture & Layering

The module adheres to standard enterprise layering:

$$\text{InventoryController} \longrightarrow \text{InventoryService} \longrightarrow \begin{cases} \text{InventoryItemRepository} \\ \text{StockMovementRepository} \end{cases}$$

1. **Controller Layer ([`InventoryController`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/inventory/controller/InventoryController.java))**: Exposes REST endpoints under `/api/v1/inventory/items`. Validates request bodies with Bean Validation (`@Valid`) and maps business exceptions to standardized JSON error responses.
2. **Service Layer ([`InventoryServiceImpl`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/inventory/service/InventoryServiceImpl.java))**: Transaction boundary (`@Transactional`), tenant scoping, inventory invariant enforcement (positive movement, sufficient stock, active status check), SKU normalization, and audit movement creation.
3. **Repository Layer ([`InventoryItemRepository`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/inventory/repository/InventoryItemRepository.java), [`StockMovementRepository`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/inventory/repository/StockMovementRepository.java))**: Spring Data JPA repositories with dynamic Criteria API filtering via [`InventorySpecification`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/inventory/repository/InventorySpecification.java).
4. **Domain Entity Layer ([`InventoryItem`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/inventory/model/InventoryItem.java), [`StockMovement`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/inventory/model/StockMovement.java))**: JPA entities extending `BaseAuditableEntity` with optimistic locking (`version`).
5. **DTO Layer ([`ai.firesphere.inventory.dto`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/inventory/dto))**: Typed request and response records preventing JPA entity exposure across HTTP interfaces.

---

## 3. Domain Model & Entities

### 3.1 `InventoryItem` Entity
* `id`: UUID (Primary Key)
* `tenantId`: UUID (Multi-tenant partition key)
* `sku`: String(64) (Unique SKU per tenant, normalized to uppercase)
* `name`: String(150) (Item nomenclature)
* `category`: String(60) (Apparatus/equipment classification: `PPE`, `FOAM_AGENT`, `HOSE_NOZZLE`, `MEDICAL`, `BREATHING_AIR`, `RESCUE_TOOL`)
* `stationId`: UUID (Home fire station reference)
* `quantityOnHand`: Integer (Authoritative on-hand quantity; non-negative)
* `minimumThreshold`: Integer (Low-stock warning threshold)
* `unitOfMeasure`: String(30) (Measurement unit: `UNITS`, `LITERS`, `CYLINDERS`, `METERS`, `SETS`)
* `description`: String(500) (Detailed specification)
* `locationInStore`: String(100) (Physical rack/shelf/bin coordinate within the station store)
* `batchNumber`: String(60) (Lot/batch identifier for consumables)
* `isActive`: Boolean (Operational readiness status; default `true`)

### 3.2 `StockMovement` Entity
* `id`: UUID (Primary Key)
* `tenantId`: UUID
* `itemId`: UUID (Reference to `inventory_items`)
* `movementType`: `StockMovementType` (`RESTOCK`, `ISSUE`, `RETURN`, `INSPECTION_ADJUSTMENT`, `SCRAP`)
* `quantity`: Integer (Delta quantity; strictly positive)
* `resultingQuantity`: Integer (Stock balance on hand immediately following the transaction)
* `incidentId`: UUID (Optional reference when issued or returned for an emergency incident)
* `destinationOrSource`: String(100) (Receiving crew/engine or dispatch source)
* `remarks`: String(500) (Operational reason or audit remarks)
* `createdBy`: String (Officer username or `STORE_OFFICER`)
* `createdAt`: Instant (Audit timestamp)

---

## 4. Stock Movement Types

The module implements 5 deterministic movement types defined by [`StockMovementType`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/inventory/model/StockMovementType.java):

| Movement Type | Meaning & Direction | Triggering Operation |
| :--- | :--- | :--- |
| `RESTOCK` | **Inflow (+)** | Initial item intake registration or vendor delivery receipts (`POST /receive`) |
| `ISSUE` | **Outflow (-)** | Dispensed to response crew, incident scene, or station training (`POST /issue`) |
| `RETURN` | **Inflow (+)** | Recovered unburned gear or surplus returned to store (`POST /return`) |
| `INSPECTION_ADJUSTMENT` | **Audit Delta (+/-)** | Periodic cycle count reconciliations or discovered surplus (`POST /adjust`) |
| `SCRAP` | **Outflow (-)** | Condemned, heat-damaged, expired, or decommissioned write-off (`POST /adjust`) |

---

## 5. Stock Invariants & Concurrency

1. **No Negative Stock Invariant**:
   - Issue operations fail with HTTP `422 Unprocessable Entity` (`BUSINESS_RULE_VIOLATION`) if `requestedQuantity > quantityOnHand`.
   - Negative adjustment operations fail with HTTP 422 if `quantityOnHand + delta < 0`.
2. **Strictly Positive Movement Quantities**:
   - `quantity` for receive, issue, and return must be $\ge 1$.
   - Adjustment delta cannot be 0.
3. **Atomic Persistence**:
   - The balance update on `InventoryItem` and insertion into `StockMovement` execute within the same transactional boundary (`@Transactional`). If movement logging fails, stock changes rollback completely.
4. **Inactive Item Protection**:
   - Items with `isActive = false` cannot be issued (`HTTP 422`).
5. **Concurrency & Optimistic Locking**:
   - `InventoryItem` extends `BaseAuditableEntity` with JPA `@Version` column (`version BIGINT`), detecting concurrent updates and preventing race conditions or double allocations.

---

## 6. REST API Endpoints

All endpoints are hosted under `/api/v1/inventory/items`:

| Method | Endpoint | Description | Success Status |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/inventory/items` | Register new inventory SKU with station assignment and initial quantity | `201 Created` |
| `GET` | `/api/v1/inventory/items` | Paginated search with filtering (`category`, `stationId`, `isActive`, `lowStock`, `search`) | `200 OK` |
| `GET` | `/api/v1/inventory/items/{id}` | Get item details by UUID for current tenant | `200 OK` |
| `PUT` | `/api/v1/inventory/items/{id}` | Update mutable metadata (name, description, threshold, location, batch, active). Does NOT alter quantity. | `200 OK` |
| `POST` | `/api/v1/inventory/items/{id}/receive` | Stock intake/restock operation | `200 OK` |
| `POST` | `/api/v1/inventory/items/{id}/issue` | Dispense stock for field deployment or incident response | `200 OK` |
| `POST` | `/api/v1/inventory/items/{id}/return` | Return unused gear or consumables back into store | `200 OK` |
| `POST` | `/api/v1/inventory/items/{id}/adjust` | Audited cycle count adjustment (positive or negative delta) | `200 OK` |
| `GET` | `/api/v1/inventory/items/{id}/movements` | Chronological audit ledger of stock movements | `200 OK` |
| `GET` | `/api/v1/inventory/items/low-stock` | Filter items where `quantityOnHand <= minimumThreshold` | `200 OK` |

---

## 7. Database Migrations

### Flyway Migration: `V5__inventory_management.sql`
1. Adds `is_active BOOLEAN NOT NULL DEFAULT TRUE`, `description VARCHAR(500)`, and `location_in_store VARCHAR(100)` to `inventory_items`.
2. Adds `resulting_quantity INTEGER` to `inventory_stock_movements`.
3. Creates performance indexes:
   * `idx_inventory_tenant_cat` on `inventory_items(tenant_id, category)`
   * `idx_inventory_tenant_active` on `inventory_items(tenant_id, is_active)`
   * `idx_movements_tenant_item` on `inventory_stock_movements(tenant_id, item_id)`
   * `idx_movements_tenant_time` on `inventory_stock_movements(tenant_id, created_at)`

---

## 8. Decoupled Module Boundaries

* **Phase 2 Incident Management (Locked)**: Unmodified. `incidentId` in `StockMovement` is an optional UUID reference.
* **Phase 3 Emergency Dispatch (Locked)**: Unmodified. Dispatch orders do not mutate inventory balances automatically.
* **Phase 4 Fleet / Vehicle Management (Locked)**: Unmodified. Inventory does not mutate `Vehicle` or `VehicleStatus`.
* **Phase 6 GIS Operations (Deferred)**: PostGIS hydrants, hazard zones, spatial routing, and geofencing remain deferred.
* **Notifications (Deferred)**: Low-stock detection provides deterministic read indicators; automated push, SMS, or email dispatch remains in Phase 7.
