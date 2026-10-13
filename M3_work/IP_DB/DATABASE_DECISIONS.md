# FireSphere.ai — Database Decision Log

This document tracks architectural and design decisions, schema clarifications, and resolutions of ambiguities regarding the database and infrastructure for FireSphere.ai.

---

## Decision Record Template

```markdown
### ADR-[NUMBER]: [Title]
- **Date**: YYYY-MM-DD
- **Status**: [PROPOSED | UNDER_REVIEW | APPROVED | REJECTED | SUPERSEDED | UNRESOLVED | BLOCKED]
- **Stakeholders**: [Member 1, Member 2, Member 3, Project Lead, Platform Architect]
- **Context & Problem Statement**:
  [Describe the context and problem that requires a decision]
- **Options Considered**:
  1. [Option 1]
  2. [Option 2]
- **Decision Outcome**:
  [Chosen option and justification, or pending questions]
- **Consequences**:
  - Positive: [...]
  - Negative / Risks: [...]
  - Impact on Migrations / Infrastructure: [...]
```

---

## Master Decision Cross-Reference Index

Comprehensive decision analysis, options, temporary states, and approval matrices are maintained in [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md).

| Decision ID | Title | Status | Blocking Phase 2? | Primary Owner |
| :--- | :--- | :--- | :---: | :--- |
| **ADR-001** | Resolution of Duplicate `incident_evidence` | `AUDITED — UNDER REVIEW` | **YES** | Member 2 & Member 1 |
| **ADR-002** | Resolution of Ambiguous `incident_ai_analysis` | `BLOCKED — MISSING IN CANDIDATE SPEC` | **YES** | Member 2 & Architect |
| **ADR-003** | Resolution of Incident Tables with Missing Descriptions | `PARTIALLY RESOLVED — BASE TABLE UNVERIFIED` | **YES** | Member 1 & Architect |
| **ADR-004** | 85 vs 91 Table Production Target Scope | `PROPOSED — PENDING APPROVAL` | **YES** | Platform Architect |
| **ADR-005** | Incident AI Analysis Persistence (`incident_ai_analysis`) | `BLOCKED — UNRESOLVED` | **YES** | Member 2 (AI Lead) |
| **ADR-006** | Tenant Identifier Canonicalization (`organization_id UUID` vs `org_id BIGINT`) | `PROPOSED — PENDING APPROVAL` | **YES** | Platform Architect & Member 1 |
| **ADR-007** | Department / User Circular Foreign Key Deadlock | `PROPOSED — PENDING APPROVAL` | **YES** | Member 3 (Database Engineer) |
| **ADR-008** | `incident_evidence` Scope & Semantics | `UNDER REVIEW — PENDING SIGN-OFF` | **YES** | Member 2 & Member 1 |
| **ADR-009** | SSO Entity Canonical Naming (`sso_user_identities` vs `sso_identities`) | `PROPOSED — PENDING APPROVAL` | **NO** | Member 1 (Auth Lead) |
| **ADR-010** | Audit Log Table Canonical Naming (`audit_logs` vs `cdc_audit_logs`) | `PROPOSED — PENDING APPROVAL` | **NO** | Member 3 & Member 1 |

---

## Logged Issues & Ambiguities

*Cross-References*: 
- [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md) (Authoritative Decision Package)
- [DATABASE_PHASE2_ENTRY_CRITERIA.md](file:///d:/IP%20Folder/DATABASE_PHASE2_ENTRY_CRITERIA.md) (Quality Gate Checklist)
- [DATABASE_SOURCE_CONFLICTS.md](file:///d:/IP%20Folder/DATABASE_SOURCE_CONFLICTS.md) (Source Document Conflict Register)

---

### ADR-001: Resolution of Duplicate `incident_evidence`
- **Date**: 2026-10-05
- **Status**: `AUDITED — UNDER REVIEW (PENDING STAKEHOLDER SIGN-OFF)`
- **Stakeholders**: Member 2 (Evidence Pipeline), Member 1 (Backend Services), Member 3 (Database)
- **Flagged In**: [03_MEMBER_3_DATABASE_INFRA_DEVOPS.md](file:///d:/IP%20Folder/03_MEMBER_3_DATABASE_INFRA_DEVOPS.md) (Line 26)
- **Forensic Audit Findings (`FireSphere_Database_Documentation_v2.xml`)**:
  1. Legacy table 25 (`database_26.sql`) contained `incident_evidence`. The legacy dump contained 0 rows and lacked CREATE TABLE DDL.
  2. The candidate architecture document (`FireSphere_Database_Documentation_v2.xml`) does **not** contain duplicate table declarations inside itself. It lists `incident_evidence` as table #34 in Appendix A.
  3. In Section 4.25, inferred columns are: `id`, `incident_id`, `evidence_type`, `file_path`, `file_hash_sha256`, `captured_at`, `uploaded_by`.
  4. In Section 5, it is marked `MODIFY`: adds `organization_id UUID`, SHA-256 integrity hash verification, capture coordinates, and MinIO S3 object key references.
- **Rule**:
  Per Member 3 instructions, do not guess or approve without cross-member validation.
- **Remaining Open Items Before Approval**:
  - Member 2 (AI/Evidence pipeline) must confirm whether `incident_evidence` is strictly for raw evidence storage or if processed/annotated evidence models require a separate table or sub-schema (see ADR-008).
  - S3 bucket path schema (`evidence/{org_id}/{incident_id}/{uuid}.{ext}`) must be formally aligned with backend services.
- **Decision Outcome**:
  *Candidate architecture definition logged. Awaiting Member 1 & Member 2 sign-off during Phase 1C freeze.*

---

### ADR-002: Resolution of Ambiguous `incident_ai_analysis`
- **Date**: 2026-10-05
- **Status**: `BLOCKED — COMPLETELY MISSING FROM CANDIDATE ARCHITECTURE`
- **Stakeholders**: Member 2 (AI Pipeline), Member 1 (Backend Services), Member 3 (Database), Platform Architect
- **Flagged In**: [03_MEMBER_3_DATABASE_INFRA_DEVOPS.md](file:///d:/IP%20Folder/03_MEMBER_3_DATABASE_INFRA_DEVOPS.md) (Line 27)
- **Forensic Audit Findings (`FireSphere_Database_Documentation_v2.xml`)**:
  1. An exhaustive full-text search across the candidate architecture document `FireSphere_Database_Documentation_v2.xml` yielded **zero occurrences** of `incident_ai_analysis`.
  2. The table is **not** present in the 60 legacy exports (`database_2.sql` to `database_61.sql`).
  3. The table is **not** defined among the 25 new required production tables in Section 6.
  4. The table is **not** listed in the 91 tables in Appendix A.
  5. The candidate architecture defines a generalized AI subsystem: `ai_models`, `ai_agents`, `ai_agent_workflows`, `ai_agent_workflow_steps`, `ai_sessions`, `ai_runs`, and `ai_insights`. In Section 4.4, `ai_insights` records polymorphic predictions (`target_entity_type`, `target_entity_id`, `insight_summary`, `confidence_score`).
- **Rule**:
  Under no circumstances may Member 3 invent the schema, columns, or lifecycle for `incident_ai_analysis`.
- **Critical Risk**:
  If Member 2 expects a dedicated `incident_ai_analysis` table (e.g. for computer vision hazard detection, fire spread simulation, or post-incident review scores) rather than rows in generic `ai_insights`, the database contract is currently broken.
- **Pending Questions**:
  1. Did the architect intentionally subsume `incident_ai_analysis` into `ai_insights` (with `target_entity_type = 'incident'`)?
  2. Or did the architect inadvertently omit `incident_ai_analysis` from the candidate specification?
  3. If a dedicated table is required, what are its columns, JSONB payloads, model references, and execution lifecycle? (See ADR-005).
- **Decision Outcome**:
  *FORMAL BLOCKER RAISED. Candidate architecture does not contain this entity. Requires authoritative clarification from Architect and Member 2.*

---

### ADR-003: Resolution of Incident Tables with Missing Descriptions
- **Date**: 2026-10-05
- **Status**: `PARTIALLY RESOLVED IN CANDIDATE SPEC — BASE TABLE UNVERIFIED`
- **Stakeholders**: Member 1 (Domain Services), Member 2, Member 3 (Database), Platform Architect
- **Flagged In**: [03_MEMBER_3_DATABASE_INFRA_DEVOPS.md](file:///d:/IP%20Folder/03_MEMBER_3_DATABASE_INFRA_DEVOPS.md) (Line 28)
- **Forensic Audit Findings (`FireSphere_Database_Documentation_v2.xml`)**:
  1. The legacy MariaDB export contained only two incident-domain tables: `incidents` (`database_30.sql`) and `incident_evidence` (`database_26.sql`), both completely empty (0 rows) without DDL.
  2. To resolve the functional gap, the candidate architecture document created **6 new production tables** with detailed column dictionaries in Section 6 and relationships in Section 7.5:
     - `incident_dispatches` (Section 6.13, Appendix A #33)
     - `incident_vehicles` (Section 6.17, Appendix A #37)
     - `incident_crews` (Section 6.12, Appendix A #32)
     - `incident_timeline` (Section 6.16, Appendix A #36)
     - `incident_casualties` (Section 6.11, Appendix A #31)
     - `incident_reports` (Section 6.15, Appendix A #35)
  3. However, the root entity `incidents` remains based on the empty legacy export. Its columns are inferred and marked: `"Not verifiable from supplied SQL dump — requires schema inspection."`
  4. Furthermore, `incident_ai_analysis` is absent (see ADR-002 & ADR-005).
- **Rule**:
  Do not finalize schema or generate Flyway migration scripts until the base `incidents` contract is verified and the 6 child table contracts are reviewed and signed off by Member 1 (Backend Services).
- **Decision Outcome**:
  *The 6 child incident tables are documented in the candidate specification. Formal sign-off on the root `incidents` schema and full incident workflow lifecycle is pending Phase 1C stakeholder review.*

---

### ADR-004: 85 vs 91 Table Production Target Scope
- **Date**: 2026-10-05
- **Status**: `PROPOSED — PENDING APPROVAL`
- **Stakeholders**: Platform Architect, Project Lead, Member 1, Member 3
- **Context & Conflict**:
  Sections 1, 2, 5, 6, 9 state **85 tables** (60 legacy + 25 new required). However, Appendix A catalogs **91 tables**, adding 6 tables marked `(NEW)`: `inventory_categories` (#46), `notification_templates` (#57), `payments` (#64), `purchase_order_items` (#66), `purchase_orders` (#67), and `vendors` (#83). None of these 6 tables have column data dictionaries in Section 6.
- **Options**:
  1. Freeze at 85 tables for initial V1 core schema; defer the 6 procurement/payment tables.
  2. Expand to 91 tables; Architect must author full column dictionaries for the 6 tables before Phase 2.
- **Decision Outcome**:
  *Detailed in [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-004-85-vs-91-table-production-target-scope). Awaiting Architect decision.*

---

### ADR-005: Incident AI Analysis Persistence (`incident_ai_analysis`)
- **Date**: 2026-10-05
- **Status**: `BLOCKED — UNRESOLVED`
- **Stakeholders**: Member 2 (AI Lead), Platform Architect, Member 1, Member 3
- **Context & Conflict**:
  Table required by project instructions is missing from XML candidate specification.
- **Options**:
  1. Use generic polymorphic `ai_insights` (`target_entity_type = 'incident'`).
  2. Author dedicated relational table `incident_ai_analysis`.
  3. Store JSONB payload in `incidents.ai_analysis_json`.
- **Decision Outcome**:
  *Detailed in [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-005-incident-ai-analysis-persistence-incident_ai_analysis). Awaiting Member 2 selection.*

---

### ADR-006: Tenant Identifier Canonicalization (`organization_id UUID` vs `org_id BIGINT`)
- **Date**: 2026-10-05
- **Status**: `PROPOSED — PENDING APPROVAL`
- **Stakeholders**: Platform Architect, Member 1 (Backend Lead), Member 3
- **Context & Conflict**:
  Section 2.2 states `organization_id UUID`, but Section 6 DDL and Section 8.1 implement `org_id BIGINT` foreign keys and RLS policies.
- **Options**:
  1. Pure UUID everywhere.
  2. Dual-Key Architecture (Recommended): `id BIGSERIAL` / `org_id BIGINT` internally; `uuid UUID` externally.
  3. Pure BIGINT everywhere.
- **Decision Outcome**:
  *Detailed in [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-006-tenant-identifier-canonicalization-organization_id-uuid-vs-org_id-bigint). Recommended Option 2 pending approval.*

---

### ADR-007: Department / User Circular Foreign Key Deadlock
- **Date**: 2026-10-05
- **Status**: `PROPOSED — PENDING APPROVAL`
- **Stakeholders**: Member 3 (Database Engineer), Member 1 (Backend Lead), Platform Architect
- **Context & Conflict**:
  Mutual foreign key dependency between `departments.head_user_id` and `users.department_id` creates insertion deadlock.
- **Options**:
  1. Make both columns nullable and declare `departments.head_user_id DEFERRABLE INITIALLY DEFERRED`.
  2. Decouple head of department to a separate `department_leadership` table.
- **Decision Outcome**:
  *Detailed in [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-007-department--user-circular-foreign-key-deadlock). Option 1 proposed pending approval.*

---

### ADR-008: `incident_evidence` Scope & Semantics
- **Date**: 2026-10-05
- **Status**: `UNDER REVIEW — PENDING SIGN-OFF`
- **Stakeholders**: Member 2 (AI/Evidence Lead), Member 1 (Backend Lead), Member 3
- **Context & Conflict**:
  Clarify whether `incident_evidence` strictly stores raw media file metadata or also holds AI bounding boxes/annotations.
- **Options**:
  1. Raw evidence file store only (MinIO keys, MIME type, SHA-256 hash, GPS coordinates).
  2. Combined evidence and annotation store.
- **Decision Outcome**:
  *Detailed in [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-008-incident_evidence-scope--semantics). Option 1 recommended pending sign-off.*

---

### ADR-009: SSO Entity Canonical Naming (`sso_user_identities` vs `sso_identities`)
- **Date**: 2026-10-05
- **Status**: `PROPOSED — PENDING APPROVAL`
- **Stakeholders**: Member 1 (Auth Lead), Member 3, Platform Architect
- **Context & Conflict**:
  Legacy dump is `database_52.sql` (`sso_user_identities`); target catalog lists `sso_identities`.
- **Options**:
  1. Canonicalize as `sso_identities` in target DDL; map legacy data during ETL.
  2. Retain legacy name `sso_user_identities`.
- **Decision Outcome**:
  *Detailed in [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-009-sso-entity-canonical-naming). Option 1 proposed pending sign-off.*

---

### ADR-010: Audit Log Canonical Naming (`audit_logs` vs `cdc_audit_logs`)
- **Date**: 2026-10-05
- **Status**: `PROPOSED — PENDING APPROVAL`
- **Stakeholders**: Member 3, Member 1, Platform Architect
- **Context & Conflict**:
  Section 4.11 and Appendix A (#11) list `audit_logs`, while Section 9 table asserts replacement by `cdc_audit_logs`.
- **Options**:
  1. Canonicalize physical table as `firesphere.audit_logs`.
  2. Canonicalize physical table as `firesphere.cdc_audit_logs`.
- **Decision Outcome**:
  *Detailed in [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-010-audit-log-canonical-naming). Option 1 proposed pending sign-off.*
