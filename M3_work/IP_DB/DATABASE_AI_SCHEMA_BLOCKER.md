# FireSphere.ai — AI Schema Integration Blocker (ADR-005)

**Role:** Member 3 (Database, Infrastructure & DevOps)  
**Stakeholders:** Member 4 (AI/ML Engineering), Member 2 (Backend Engineering), Member 3 (Database)  
**Date:** October 2026  
**Status:** **ACTIVE INTEGRATION BLOCKER — SCHEMA NOT APPROVED**

---

## 1. Problem Statement

During the M3 forensic implementation audit and cross-member contract review, a critical disconnect was verified:

1. **Contract Dependency:** `DATABASE_INTEGRATION_CONTRACT.md` (Section 4.2) stated that Member 4 (AI/ML Lead) would write object detection, fire intensity, and casualty inference results to `firesphere.ai_insights`.
2. **Physical Database Reality:** **`firesphere.ai_insights` does NOT exist in the database.**
   - It was not present in the audited candidate XML specification (`FireSphere_Database_Documentation_v2.xml`).
   - It was not created in any Flyway migration (`V1.0.0` through `V1.0.5`).
   - Attempting to query or insert into `firesphere.ai_insights` causes PostgreSQL error `42P01: relation "firesphere.ai_insights" does not exist`.

---

## 2. Architectural Boundary & No-Speculation Rule

Per Member 3's strict governance rules:
- **Member 3 will NOT invent speculative tables, columns, or relationships.**
- Member 3 will NOT make arbitrary assumptions regarding:
  - Bounding box coordinate representation (JSONB vs PostGIS polygon vs normalized x/y/w/h).
  - Confidence score precision (NUMERIC(5,4) vs FLOAT).
  - Model metadata and checkpoint version tracking.
  - Linkage strategy (polymorphic entity linkage vs dedicated foreign key to `incidents(id)` and `incident_evidence(id)`).
- The column list suggested in the previous audit was an analytical observation, **not an approved architectural contract**.

---

## 3. Required Multi-Member Contract Definition

Before any Flyway DDL migration is generated, Member 4 (AI/ML), Member 2 (Backend), and Member 3 (Database) must convene and agree on the contract requirements:

### Key Design Questions for Member 4 & Member 2:
1. **Target Entity Scope:**
   - Are AI insights restricted strictly to emergency incidents and incident evidence media, or must the table also support building inspection photo analysis and NOC document OCR?
2. **Inference Payload Structure:**
   - What is the structure of computer vision detections?
   - Example JSONB schema:
     ```json
     {
       "model_name": "yolov8-fire-detect",
       "model_version": "v2.1.0",
       "detections": [
         {
           "class": "flame",
           "confidence": 0.942,
           "box_2d": [ymin, xmin, ymax, xmax]
         },
         {
           "class": "smoke_heavy",
           "confidence": 0.887,
           "box_2d": [ymin, xmin, ymax, xmax]
         }
       ],
       "summary": "Heavy structural fire detected on 3rd floor balcony"
     }
     ```
3. **Relational Constraints:**
   - Must `incident_id` and `evidence_id` be strict foreign keys with `ON DELETE CASCADE` or `SET NULL`?
   - Should `org_id` be enforced with Row-Level Security? (Yes, mandatory for multi-tenant isolation).
4. **Lifecycle & Retention:**
   - Are AI insights permanent legal records, or can stale/intermediate runs be purged?

---

## 4. Next Steps & Resolution Path

1. **Review & Sign-Off:** Member 4 and Member 2 review this blocker and provide the signed schema contract.
2. **Migration Creation:** Once approved, Member 3 will author and commit **`V1.0.6__ai_insights.sql`** with:
   - 3NF table structure
   - Canonical `org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id)`
   - Row-Level Security (RLS) policies matching `V1.0.5`
   - B-tree and JSONB GIN indexes for query performance
3. **Integration Gate:** Until this contract is ratified, Member 4 must mock local database persistence or rely on ephemeral JSON responses.
