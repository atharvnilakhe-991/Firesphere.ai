# FireSphere.ai — Phase 3A: AI Persistence Logical Data Model Design
**Document Reference:** `docs/integration/phase3a_ai_persistence_design.md`  
**Status:** PROPOSED ARCHITECTURAL SPECIFICATION (Subject to Team Sign-Off)  
**Authors:** Senior Database Architect, Spring Boot Persistence Engineer, AI Service Integration Engineer  
**Date:** October 2026  

---

## 1. Executive Summary

This document specifies the logical data model and relational schema required to achieve durable, auditable, and multi-tenant persistence for artificial intelligence inspection outputs in FireSphere.ai.

### Fundamental Persistence Invariants:
1. **Durable Persistence Ownership:** The Spring Boot Backend (Member 2) is the **exclusive authoritative writer** to the PostgreSQL database. Member 4's AI Service does not maintain direct database connections, preventing tight coupling and bypassing of backend business rules.
2. **Advisory Governance Boundary:** The database enforces via `CHECK` constraints that `compliance_status` cannot be set to `AI_APPROVED` or `AI_REJECTED`. AI findings are strictly advisory observations requiring authorized Fire Safety Officer validation.
3. **Dual-Key Alignment:** All proposed tables implement the Strategy C Dual-Key architecture (`id BIGSERIAL PRIMARY KEY` for relational performance, and `uuid UUID UNIQUE` for API routing and cross-service correlation).

---

## 2. In-Memory Storage Limitations in M4 (Current State)

Inspection of Member 4's codebase reveals two active repository abstractions:
1. `InMemoryJobRepository` (`ai-service/app/jobs/repository.py`):
   - Stores async jobs in a Python dictionary `_jobs: Dict[str, AIJobRecord]`.
   - **Limitation:** Ephemeral. All queued, running, and completed job states are permanently lost upon process restart, crash, or horizontal replica scaling.
2. `InMemoryFindingRepository` (`ai-service/app/services/finding_repository.py`):
   - Stores findings in an in-memory dictionary `_findings_by_id: Dict[str, Finding]`.
   - **Limitation:** Explicitly documented in code as an ephemeral test cache. Cannot serve as the regulatory system of record for NOC compliance audits.

To transition from ephemeral execution to statutory persistence, the data must be durably stored in PostgreSQL by Member 2 upon completion of AI analysis.

---

## 3. Proposed Relational Schema Architecture (Proposed `V1.0.7`)

The proposed schema defines four cohesive, normalized entities inside the `firesphere` schema:

```
+------------------------------------+
|       firesphere.inspections       | (Existing M3 Table)
+------------------------------------+
                  |
                  | 1:N
                  v
+------------------------------------+
|    firesphere.ai_analysis_runs     | (Proposed Entity 1)
+------------------------------------+
                  |
                  | 1:N
                  v
+------------------------------------+
|       firesphere.ai_findings       | (Proposed Entity 2)
+------------------------------------+
        |                    |
        | 1:1                | 1:N
        v                    v
+------------------+  +--------------------------+
|  ai_officer_     |  |   ai_finding_audits      |
|  reviews         |  |   (Proposed Entity 4)    |
|  (Proposed Ent 3)|  +--------------------------+
+------------------+
```

---

## 4. Entity Specifications

### Entity 1: `firesphere.ai_analysis_runs`
**Purpose:** Records each invocation of the AI inspection analysis pipeline, tracking execution duration, model version, and governance approval states.

| Column Name | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` | No | `PRIMARY KEY` | Internal clustered surrogate identifier. |
| `uuid` | `UUID` | No | `UNIQUE DEFAULT gen_random_uuid()` | Canonical public `runId` matching M4 and M2 contract. |
| `org_id` | `BIGINT` | No | `REFERENCES firesphere.organizations(id)` | Tenant identifier for Row-Level Security. |
| `inspection_id` | `BIGINT` | No | `REFERENCES firesphere.inspections(id)` | Target physical inspection session. |
| `inspection_uuid` | `UUID` | No | `NOT NULL` | Indexed correlation UUID matching API request. |
| `status` | `VARCHAR(50)` | No | `DEFAULT 'PROCESSING'` | `PROCESSING`, `COMPLETED`, `FAILED`. |
| `model_name` | `VARCHAR(100)` | Yes | | Name of CV model executed (e.g. `firesphere-inspection-cv`). |
| `model_version` | `VARCHAR(50)` | Yes | | Version of model executed (e.g. `0.3.0-phase3`). |
| `model_approval_status`| `VARCHAR(50)` | No | `DEFAULT 'NOT_APPROVED'` | Production approval gate (`NOT_APPROVED`). |
| `calibration_status` | `VARCHAR(50)` | No | `DEFAULT 'NOT_CALIBRATED'`| Statistical calibration state (`NOT_CALIBRATED`). |
| `execution_duration_ms`| `NUMERIC(10,2)`| Yes | | End-to-end pipeline runtime in milliseconds. |
| `failure_reason` | `TEXT` | Yes | | Error message if run failed (zero secrets/tokens). |
| `summary_json` | `JSONB` | Yes | `DEFAULT '{}'::jsonb` | Full `InspectionAISummary` aggregation. |
| `requested_at` | `TIMESTAMPTZ` | No | `DEFAULT CURRENT_TIMESTAMP` | Submission timestamp. |
| `completed_at` | `TIMESTAMPTZ` | Yes | | Finalization timestamp. |

**Indexes:**
- `CREATE INDEX idx_ai_runs_org ON firesphere.ai_analysis_runs(org_id);`
- `CREATE INDEX idx_ai_runs_inspection ON firesphere.ai_analysis_runs(inspection_id);`
- `CREATE INDEX idx_ai_runs_uuid ON firesphere.ai_analysis_runs(uuid);`

---

### Entity 2: `firesphere.ai_findings`
**Purpose:** Persists individual safety hazard observations linked to source evidence photographs, complete with spatial localization coordinates, risk scoring, and explainability narratives.

| Column Name | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` | No | `PRIMARY KEY` | Internal surrogate identifier. |
| `uuid` | `UUID` | No | `UNIQUE DEFAULT gen_random_uuid()` | Canonical public `findingId` matching M4. |
| `run_id` | `BIGINT` | No | `REFERENCES firesphere.ai_analysis_runs(id) ON DELETE CASCADE` | Parent analysis run. |
| `org_id` | `BIGINT` | No | `REFERENCES firesphere.organizations(id)` | Tenant identifier for RLS. |
| `evidence_uuid` | `UUID` | No | `NOT NULL` | Foreign link to source photograph evidence UUID. |
| `finding_type` | `VARCHAR(100)` | No | | Categorized hazard code (e.g., `FIRE_EXTINGUISHER_MISSING`). |
| `confidence` | `NUMERIC(5,4)` | No | `CHECK (confidence >= 0.0 AND confidence <= 1.0)` | Model confidence score `[0.0000 - 1.0000]`. |
| `confidence_level` | `VARCHAR(50)` | No | `DEFAULT 'MEDIUM'` | `VERY_LOW`, `LOW`, `MEDIUM`, `HIGH`, `VERY_HIGH`. |
| `severity` | `VARCHAR(50)` | No | | `INFORMATIONAL`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. |
| `severity_reason` | `TEXT` | Yes | | Technical justification for severity rating. |
| `operational_risk_score`| `INT` | Yes | `CHECK (operational_risk_score BETWEEN 0 AND 100)` | Quantitative prioritization metric `[0 - 100]`. |
| `operational_risk_level`| `VARCHAR(50)` | Yes | | Categorical risk bracket. |
| `explanation` | `TEXT` | No | | Human-readable explanation of observation. |
| `verification_guidance`| `JSONB` | Yes | `DEFAULT '[]'::jsonb` | Specific checklist checks for inspecting officer. |
| `box_xmin` | `NUMERIC(6,4)` | Yes | `CHECK (box_xmin BETWEEN 0.0 AND 1.0)` | Normalized bounding box minimum X. |
| `box_ymin` | `NUMERIC(6,4)` | Yes | `CHECK (box_ymin BETWEEN 0.0 AND 1.0)` | Normalized bounding box minimum Y. |
| `box_xmax` | `NUMERIC(6,4)` | Yes | `CHECK (box_xmax BETWEEN 0.0 AND 1.0)` | Normalized bounding box maximum X. |
| `box_ymax` | `NUMERIC(6,4)` | Yes | `CHECK (box_ymax BETWEEN 0.0 AND 1.0)` | Normalized bounding box maximum Y. |
| `evidence_quality` | `VARCHAR(50)` | No | `DEFAULT 'ACCEPTABLE'` | `GOOD`, `ACCEPTABLE`, `POOR`, `UNUSABLE`. |
| `compliance_status` | `VARCHAR(50)` | No | `DEFAULT 'REQUIRES_OFFICER_VERIFICATION'` | Regulatory state. **CHECK constraint strictly forbids `AI_APPROVED` / `AI_REJECTED`.** |
| `officer_review_status`| `VARCHAR(50)`| No | `DEFAULT 'UNREVIEWED'` | `UNREVIEWED`, `OFFICER_CONFIRMED`, `OFFICER_DISMISSED`. |
| `version` | `INT` | No | `DEFAULT 1` | Optimistic locking counter. |
| `created_at` | `TIMESTAMPTZ` | No | `DEFAULT CURRENT_TIMESTAMP` | Record creation timestamp. |
| `updated_at` | `TIMESTAMPTZ` | No | `DEFAULT CURRENT_TIMESTAMP` | Last modification timestamp. |

**Governance Check Constraint:**
```sql
ALTER TABLE firesphere.ai_findings
    ADD CONSTRAINT chk_advisory_compliance_boundary
    CHECK (compliance_status NOT IN ('AI_APPROVED', 'AI_REJECTED', 'APPROVED', 'REJECTED'));
```

**Indexes:**
- `CREATE INDEX idx_ai_findings_run ON firesphere.ai_findings(run_id);`
- `CREATE INDEX idx_ai_findings_evidence ON firesphere.ai_findings(evidence_uuid);`
- `CREATE INDEX idx_ai_findings_type ON firesphere.ai_findings(finding_type);`
- `CREATE INDEX idx_ai_findings_uuid ON firesphere.ai_findings(uuid);`

---

### Entity 3: `firesphere.ai_officer_reviews`
**Purpose:** Stores authoritative human determinations made by certified Fire Safety Officers, ensuring clear legal liability and auditability.

| Column Name | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` | No | `PRIMARY KEY` | Surrogate identifier. |
| `finding_id` | `BIGINT` | No | `UNIQUE REFERENCES firesphere.ai_findings(id) ON DELETE CASCADE` | Target finding (1:1 relationship). |
| `org_id` | `BIGINT` | No | `REFERENCES firesphere.organizations(id)` | Tenant identifier for RLS. |
| `officer_user_id` | `BIGINT` | No | `REFERENCES firesphere.users(id)` | Authorized reviewing Fire Officer. |
| `assessment` | `VARCHAR(50)` | No | | `CONFIRMED_HAZARD`, `NOT_A_HAZARD`, `UNABLE_TO_DETERMINE`, `ADDITIONAL_INSPECTION_REQUIRED`. |
| `evidence_reviewed` | `BOOLEAN` | No | `DEFAULT FALSE` | Officer visual evidence review flag. |
| `officer_remarks` | `TEXT` | Yes | | Officer commentary and site notes. |
| `corrections_json` | `JSONB` | Yes | `DEFAULT '{}'::jsonb` | Manual officer adjustments to category or severity. |
| `reviewed_at` | `TIMESTAMPTZ` | No | `DEFAULT CURRENT_TIMESTAMP` | Verification timestamp. |

---

### Entity 4: `firesphere.ai_finding_audits`
**Purpose:** Immutable append-only audit trail capturing every state change, model update, or officer correction for forensic accountability.

| Column Name | Data Type | Nullable | Constraints & Defaults | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` | No | `PRIMARY KEY` | Surrogate audit record identifier. |
| `finding_id` | `BIGINT` | No | `REFERENCES firesphere.ai_findings(id) ON DELETE CASCADE` | Target finding. |
| `action` | `VARCHAR(50)` | No | | `CREATED`, `OFFICER_REVIEWED`, `OVERRIDDEN`, `RECONCILED`. |
| `performed_by_type` | `VARCHAR(50)` | No | | `AI_SERVICE`, `OFFICER_USER`, `SYSTEM_ADMIN`. |
| `performed_by_id` | `VARCHAR(100)`| Yes | | User ID or Service name. |
| `previous_state` | `JSONB` | Yes | | Snapshot of finding prior to update. |
| `new_state` | `JSONB` | No | | Snapshot of finding after update. |
| `recorded_at` | `TIMESTAMPTZ` | No | `DEFAULT CURRENT_TIMESTAMP` | Immutable log timestamp. |

---

## 5. Write Ownership & API Boundaries

1. **Member 4 (AI Service):** Performs in-memory inference and returns `InspectionAnalysisResponse` JSON over HTTP. Performs **zero** database writes.
2. **Member 2 (Spring Boot Backend):**
   - Receives `ExternalAiResponse` in `AiOrchestrationService`.
   - Opens `@Transactional` boundary.
   - Persists `AiAnalysisRun` in `firesphere.ai_analysis_runs`.
   - Iterates rich findings and persists `AiAnalysisFinding` records in `firesphere.ai_findings`.
   - Logs completion in `AuditLogger`.
   - Commits database transaction.
3. **Officer Review Workflow:**
   - Officer reviews finding in frontend (M1) -> Submits determination to M2 backend -> M2 verifies officer RBAC -> M2 inserts record in `firesphere.ai_officer_reviews` and appends to `firesphere.ai_finding_audits`.
