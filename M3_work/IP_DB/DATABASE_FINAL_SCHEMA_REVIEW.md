# FireSphere.ai — Database Final Schema Review Template

This document provides the mandatory review and audit template for validating all physical database entities prior to drafting or executing Flyway migrations. Every table considered for production deployment must undergo this review.

---

## Schema Review Checklist

Before approving any table for migration:
- [ ] Table purpose and domain ownership are documented and agreed upon.
- [ ] All primary and foreign keys are explicitly typed (e.g., UUIDv4 or BIGINT identity) and named consistently.
- [ ] Multi-tenancy key (`tenant_id`) is evaluated and applied where tenant isolation is mandated.
- [ ] Normalization rules adhered to; no ambiguous generic fields.
- [ ] All constraints (NOT NULL, UNIQUE, CHECK) are explicitly declared.
- [ ] Status columns have valid check constraints or reference lookup tables.
- [ ] Timestamps (`created_at`, `updated_at` with time zone) are standardized.
- [ ] Indexes defined for foreign keys and common query predicates.
- [ ] Spatial columns follow EPSG:4326 (`GEOMETRY(Point, 4326)` or `GEOMETRY(Polygon, 4326)`) and possess `GIST` indexes.
- [ ] Audit requirements (soft deletion, audit logging, actor tracking) are established.
- [ ] No unresolved questions remain for the entity.

---

## Entity Audit Template

```markdown
### Entity: [table_name]

- **Table Name**: `[table_name]`
- **Purpose / Description**: 
  [Describe the authoritative business purpose and domain responsibility of this table]
- **Source / Approval**: 
  [Data Dictionary Ref / Jira / RFC / Approved By: Member 1 / Member 2 / Project Lead]
- **Primary Key**: 
  - Column: `id` ([UUID | BIGINT GENERATED ALWAYS AS IDENTITY])
  - Constraint Name: `pk_[table_name]`
- **Tenant Key**: 
  - Column: `tenant_id` ([UUID | BIGINT])
  - Required: [YES | NO]
  - Isolation Rule: [Row-Level Security (RLS) / Application Filter / Foreign Key to tenants]
- **Foreign Keys & Relationships**:
  - `fk_[table_name]_[ref_table]`: `[local_column]` → `[ref_table]([ref_column])` (ON DELETE [RESTRICT | CASCADE | SET NULL])
  - Cardinality: [1:1 | 1:N | N:M]
- **Status Fields**:
  - `status`: `VARCHAR(...)` or `ENUM` (Allowed values: `...`)
  - Check Constraint: `chk_[table_name]_status`
- **Timestamps & Lifecycle**:
  - `created_at`: `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at`: `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `deleted_at`: `TIMESTAMPTZ NULL` (if soft delete required)
- **Column Specifications & Constraints**:
  | Column Name | Data Type | Nullable | Default | Constraints | Description |
  |-------------|-----------|----------|---------|-------------|-------------|
  | `id`        | UUID      | NO       | gen_random_uuid() | PRIMARY KEY | Entity unique identifier |
  | `tenant_id` | UUID      | NO       | -       | FK to tenants | Tenant isolation key |
  | ...         | ...       | ...      | ...     | ...         | ...         |
- **Indexes**:
  - `idx_[table_name]_[column]`: B-Tree on `[column]` (Reason: foreign key lookup)
  - `idx_[table_name]_[tenant_id]_[column]`: Composite B-Tree (Reason: tenant-scoped filtering)
- **Spatial Columns**:
  - Column: `geom` / `location` / `boundary`
  - Type: `GEOMETRY(Point, 4326)` / `GEOMETRY(Polygon, 4326)`
  - SRID: `4326` (WGS 84)
- **Spatial Indexes**:
  - Index Name: `idx_[table_name]_[geom_col]_gist`
  - Type: `USING GIST ([geom_col])`
- **Audit & Governance Requirements**:
  - Actor tracking: `created_by` / `updated_by`
  - Change data capture (CDC) / trigger-based audit logging: [YES | NO]
- **Unresolved Questions / Blockers**:
  - [List any open questions, discrepancies, or missing requirements from Member 1/2]
- **Sign-off**:
  - Reviewed by M3: [ ] Date:
  - Approved by M1/M2/Lead: [ ] Date:
```

---

## Active Entity Reviews

*(Entity reviews will be populated here once the master Data Dictionary and reconciled specifications are received. No tables are reviewed or approved at this time.)*
