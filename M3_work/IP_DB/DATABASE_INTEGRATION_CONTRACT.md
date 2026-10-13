# FireSphere.ai — Cross-Member Database & Infrastructure Integration Contract

**Role:** Member 3 (Database, Infrastructure & DevOps)  
**Target Audience:** Member 1 (Frontend/Mobile), Member 2 (Backend Services), Member 4 (AI/ML & Computer Vision), Member 5 (Operations, GIS & Integrations)  
**Date:** October 2026  
**Status:** **Authoritative Architecture Integration Contract**

---

## 1. Overview & Architectural Principles

FireSphere.ai is developed independently by five specialized engineering members who will integrate their components against this shared infrastructure foundation. This document defines the clear technical boundaries, access patterns, environment requirements, and responsibilities for each member.

```text
+-----------------------------------------------------------------------------------+
|                            FireSphere.ai Architecture                             |
+-----------------------------------------------------------------------------------+
|  [ M1: Mobile & Web Client ]                                                      |
|         |                                                                         |
|         v (REST / WebSocket / Pre-signed URLs — NEVER DIRECT DB ACCESS)           |
|  [ M2: Backend Services (Spring Boot / Node) ]                                    |
|         |                     |                          |                        |
|         v                     v                          v                        |
|  (Relational Core)       (Fast Cache)             (Binary Files)                  |
|  PostgreSQL 16           Redis 7                  MinIO / S3                      |
|  PostGIS 3.4             (M3 Infra)               (M3 Infra)                      |
|  (M3 Infra)                   ^                          ^                        |
|         ^                     |                          |                        |
|         |                     v                          |                        |
|         +----------------- [ M4: AI/ML Engine ] ---------+                        |
|         |                  (Inference & Vision)          |                        |
|         v                                                v                        |
|  [ M5: GIS & Operations Engine (Dispatch / Telemetry / NBC Rules) ]               |
+-----------------------------------------------------------------------------------+
```

---

## 2. Contract with Member 1 — Frontend & Mobile Engineering

### 2.1 Zero Direct Database Access Rule
- **CRITICAL SECURITY REQUIREMENT:** Mobile applications (Flutter/React Native) and Web SPA frontends must **NEVER** receive direct database credentials or connect directly to PostgreSQL or Redis on ports 5432 or 6379.
- All client interactions are mediated strictly through Member 2's authenticated Backend API endpoints.

### 2.2 File Upload & Download Protocol (Pre-Signed S3 URLs)
To avoid routing multi-megabyte inspection photos and incident video files through application servers:
1. **Upload Request:** Mobile app requests an upload ticket from Backend: `POST /api/v1/evidence/upload-ticket`.
2. **Pre-Signed URL Issuance:** Backend asks MinIO/S3 for a pre-signed PUT URL with a short TTL (15 minutes max) and returns it to the client:
   ```json
   {
     "upload_url": "http://localhost:9000/firesphere-photos/org_1/bldg_12/uuid.jpg?X-Amz-Signature=...",
     "object_key": "org_1/bldg_12/uuid.jpg",
     "file_uuid": "c39a2d8e-7b51-4e9f-8d9e-10bfa43e8c22"
   }
   ```
3. **Direct Upload:** Client streams binary payload directly to MinIO using HTTP PUT.
4. **Metadata Registration:** Client informs Backend with file metadata, file size, and client-computed SHA-256 hash: `POST /api/v1/evidence/confirm-upload`. Backend persists metadata into `firesphere.building_documents` or `firesphere.incident_evidence`.
5. **Download / Preview:** Previews are served via expiring pre-signed GET URLs. Buckets do not permit unrestricted public access.

---

## 3. Contract with Member 2 — Backend Engineering

### 3.1 Database Connection & Environment Variables
The Backend application connects to the local database stack using these environment configurations:

```properties
# Spring Boot / JVM application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/firesphere?currentSchema=firesphere
spring.datasource.username=firesphere_admin
spring.datasource.password=postgres_dev_password_change_me
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate Configuration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.default_schema=firesphere
spring.jpa.properties.hibernate.dialect=org.hibernate.spatial.dialect.postgis.PostgisPG10Dialect

# Redis Caching & Queue
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=redis_dev_password_change_me

# S3 / MinIO Storage Client
aws.s3.endpoint=http://localhost:9000
aws.s3.region=us-east-1
aws.s3.access-key=minio_dev_admin
aws.s3.secret-key=minio_dev_password_change_me
aws.s3.bucket-documents=firesphere-documents
aws.s3.bucket-photos=firesphere-photos
aws.s3.bucket-evidence=firesphere-evidence
```

### 3.2 Schema Ownership & Strict DDL Ban
- **Member 3 owns all schema evolution.** Member 2 must **NEVER** use `hibernate.ddl-auto=update` or execute manual DDL statements. Always set `hibernate.ddl-auto=validate`.
- If a new entity field, table, or index is required by business logic, submit a schema change request to Member 3 as documented in [DATABASE_MIGRATION_GUIDE.md](file:///d:/IP%20Folder/DATABASE_MIGRATION_GUIDE.md).

### 3.3 Multi-Tenant Row-Level Security (RLS) Protocol & Connection Pool Safety
When borrowing a database connection from the connection pool, the backend interceptor must execute:
```sql
SELECT firesphere.set_tenant_context(:org_id, :is_super_admin);
SELECT set_config('app.current_user_id', :user_id, FALSE);
```
This binds `app.current_org_id`, `app.is_super_admin`, and `app.current_user_id` for the transaction lifecycle, enabling transparent database-level tenant isolation and audit attribution.

> [!CAUTION]
> **CRITICAL CONNECTION POOL SAFETY WARNING (HikariCP / JPA):**  
> Database tenant context is session-scoped.  
> Because Spring Boot / JVM backends use pooled connections (such as HikariCP), application code must ensure tenant context is correctly initialized for every borrowed connection/request and cannot leak between tenants.  
> The backend integration must define a safe checkout/checkin/reset strategy.  
> Upon returning connections to the pool or finishing an HTTP request lifecycle, the application interceptor must clear the session variables:
> ```sql
> SELECT set_config('app.current_org_id', '', FALSE);
> SELECT set_config('app.is_super_admin', 'false', FALSE);
> SELECT set_config('app.current_user_id', '', FALSE);
> ```
> Failure to reset connection session context risks cross-tenant data leaks across asynchronous requests.

---

## 4. Contract with Member 4 — AI & Machine Learning Engineering

### 4.1 Storage Boundaries for AI Inputs & Outputs
- **Raw Input Media:** Inspection photos, architectural PDFs, and incident recordings are retrieved from MinIO buckets using S3 object keys stored in PostgreSQL (`building_documents.file_path`, `incident_evidence.file_path`).
- **Intermediate Artifacts:** Model caches, temporary embeddings, and tensor checkpoints must NOT be stored in relational tables. Store them in local scratch volumes or MinIO temporary buckets.
- **Inference Results:** AI outputs are recorded in database tables with model versions and confidence scores.

### 4.2 Critical Blocker on Incident AI Persistence (ADR-005 & Schema Contract)
> [!WARNING]
> **INTEGRATION BLOCKER — `firesphere.ai_insights` DOES NOT EXIST:**  
> The database currently does NOT contain a table named `ai_insights` or `incident_ai_analysis`.  
> - **Schema Status:** The AI results persistence schema has NOT been approved.  
> - **Joint Requirement:** Member 4 (AI/ML), Member 2 (Backend), and Member 3 (Database) must jointly define the exact schema contract for AI inference outputs (e.g. bounding box format, confidence score precision, model versioning, polymorphic vs dedicated incident linkage).  
> - **No Speculative DDL:** Member 3 will not generate speculative tables without formal contract agreement.  
> - **Authoritative Blocker Reference:** See [DATABASE_AI_SCHEMA_BLOCKER.md](file:///d:/IP%20Folder/DATABASE_AI_SCHEMA_BLOCKER.md) for current status and schema proposal template.  
> - Once the schema contract is finalized and ratified, Member 3 will author and commit migration `V1.0.7__ai_insights.sql` (or subsequent forward version).


---

## 5. Contract with Member 5 — Operations, GIS & Integrations

### 5.1 PostGIS Spatial Foundation
Member 3 provides native PostGIS 3.4+ spatial capabilities for all geographic and dispatch operations:
- **Coordinate Reference System:** **WGS 84 (SRID 4326)** across all spatial tables.
- **Geometry Types:**
  - `GEOMETRY(Point, 4326)` for stations, hydrants, water sources, buildings, incidents, and vehicle telemetry.
  - `GEOMETRY(Polygon, 4326)` for jurisdiction zones, beats, station coverage, and building footprints.
- **Spatial Indexing:** All geometry columns are indexed using native PostgreSQL **GiST** indexes.
- **Nearest-Neighbor Hydrant Locator:** Member 5 can call the high-performance stored function directly:
  ```sql
  SELECT * FROM firesphere.find_nearest_operational_hydrants(:org_id, :longitude, :latitude, 5);
  ```
  This performs sub-5ms KNN lookups using the spatial `<->` distance operator over thousands of hydrants.

### 5.2 Vehicle Telemetry Stream
Live GPS pings for apparatus vehicles stream into `firesphere.vehicle_location_history (vehicle_id, location, speed_kmh, heading_degrees, telemetry_timestamp)`. For fast turnout station mapping, spatial containment queries can use `ST_Contains(station.jurisdiction_geom, incident.location)`.

---

## 6. Summary of Integration Touchpoints

| Member | Primary Service Integration | Protocol / Tooling | Access Authority |
| :--- | :--- | :--- | :--- |
| **M1 Mobile/Web** | Pre-signed S3 file uploads & downloads | HTTP PUT / GET via MinIO pre-signed URLs | Mediated by Backend API |
| **M2 Backend** | JDBC Relational Core & Redis Cache | PostgreSQL Port 5432 / Redis Port 6379 | `firesphere_admin` / Read-Write |
| **M4 AI/ML** | S3 evidence media & inference metadata | Python boto3 (MinIO) & async workers | S3 read-write / DB metadata insert |
| **M5 Operations/GIS** | PostGIS spatial queries & dispatch routing | SQL spatial operators (`ST_Distance`, `ST_Contains`, KNN `<->`) | Read-Write operational entities |
