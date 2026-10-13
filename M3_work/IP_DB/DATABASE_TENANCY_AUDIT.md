# FireSphere.ai — Multi-Tenancy & Row-Level Security (RLS) Audit

**SOURCE DOCUMENT**: `FireSphere_Database_Documentation_v2.xml` (Sections 2.2, 8.1, 10.1)  
**STATUS**: `FORENSIC AUDIT COMPLETED`  
**CORE ARCHITECTURE**: Kernel-level PostgreSQL Row-Level Security (RLS) anchored on `organizations(id)`

---

## 1. Explicitly Specified Tenancy Specifications

The following items are **explicitly defined and mandated** in the source document:

### A. Tenant Root & Identification
- **Root Authority Entity**: `organizations` table (`firesphere.organizations`).
- **Tenant Scope**: Municipal corporations, state emergency services, industrial safety departments (e.g., Pune Municipal Corporation [PMC], Thane Municipal Corporation [TMC]).
- **Tenant Key Reference**:
  - In Section 2.2: Stated as `organization_id UUID` referencing `organizations(id)`.
  - In Section 6 DDL tables: Defined as `org_id BIGINT` referencing `organizations(id)` with a parallel external `uuid UUID UNIQUE` on each entity.
  - In Section 8.1: Session variable cast as `current_setting('app.current_org_id')::BIGINT`.

### B. Zero-Trust Security Pipeline
- **Mandatory Flow**:  
  `JWT Token` → `Authenticated User` → `Organization Context` → `Backend Authorization` → `Database Query`
- **Zero-Trust Rule**:  
  *Client mobile devices or web browsers are strictly NEVER permitted to supply `organization_id` in request bodies, path parameters, or query parameters as a trusted authorization source.* The tenant context is extracted strictly from cryptographically verified server-side JWT claims.

### C. Session & Connection-Pool Context Setting
- **Session Injection Command**:  
  When a backend connection pool (e.g., HikariCP) borrows a database session, it must execute:
  ```sql
  SET LOCAL app.current_org_id = :org_id;
  ```
  Or via stored function:
  ```sql
  SELECT set_tenant_context(p_org_id, p_is_super_admin);
  ```
- **Transaction Scope**: `SET LOCAL` ensures that when the transaction completes (`COMMIT` or `ROLLBACK`), the tenant context does not leak into the next transaction borrowing the pooled physical connection.

### D. Row-Level Security (RLS) Policies
- **RLS Activation**: Every tenant-aware table must have RLS enabled:
  ```sql
  ALTER TABLE firesphere.[table_name] ENABLE ROW LEVEL SECURITY;
  ```
- **Standard Tenant Policy Filter**:
  ```sql
  CREATE POLICY tenant_isolation_policy ON firesphere.[table_name]
    FOR ALL
    USING (org_id = current_setting('app.current_org_id', true)::BIGINT)
    WITH CHECK (org_id = current_setting('app.current_org_id', true)::BIGINT);
  ```

### E. Global vs. Tenant-Scoped Catalogs
- **Platform Foundation Models / Default Roles**:
  Entities where `org_id IS NULL` (e.g., foundation `ai_models`, global system `roles`, master `permissions`) are globally readable across all tenants.
- **Tenant Custom Roles**:
  Roles where `org_id = X` are visible and usable strictly within tenant X.

### F. Super-Admin & Cross-Tenant Behavior
- **Legacy Finding**: In the legacy dump (`users`, `database_53.sql`), all 6 test users had `is_super_admin = 1`, completely breaking tenant isolation.
- **Sanitization Rule**: `is_super_admin` must be stripped from all tenant personnel accounts.
- **Super-Admin Bypass**: Super-admin cross-tenant inspection is controlled exclusively through `p_is_super_admin = true` passed to `set_tenant_context`, which sets `app.is_super_admin = 'true'`, triggering a dedicated bypass clause in RLS policies.

---

## 2. Implementation Details That Are Still Missing

The following architectural and operational requirements are **referenced or required in practice, but are MISSING explicit technical specifications** in the source document:

1. **Exact SQL Definition of `set_tenant_context`**:
   - `MISSING — REQUIRED INPUT`: The exact signature, security definer settings (`SECURITY DEFINER`), error handling (what happens if `p_org_id` is null or invalid), and validation logic for `set_tenant_context(p_org_id, p_is_super_admin)` are not provided.
2. **Reconciliation of Tenant Column Name & Data Type**:
   - `MISSING — REQUIRED INPUT`: Must resolve whether the physical column is `organization_id` or `org_id`, and whether its type is `UUID` or `BIGINT`. (Section 2.2 says `organization_id UUID`; Section 6 says `org_id BIGINT`).
3. **Session Reset / Pool Cleanup Hook**:
   - `MISSING — REQUIRED INPUT`: Missing specification for connection pool `DISCARD ALL` or `RESET ALL` configuration to guarantee no session leakage across thread reuse.
4. **Tenant Onboarding & Provisioning Workflow**:
   - `MISSING — REQUIRED INPUT`: When a new municipal organization is created, what default records must be provisioned (default roles, permissions, inspection checklists, status codes)?
5. **Direct vs. Indirect Tenant Filtering on Child Tables**:
   - `MISSING — REQUIRED INPUT`: Several child tables (e.g., `incident_crews`, `incident_vehicles`, `inspection_findings`) omit `org_id` and rely on foreign keys to parent tables (`incident_dispatches.org_id`, `inspections.org_id`). It is not specified whether these child tables require direct `org_id` denormalization for RLS performance, or subquery-based RLS policies (`USING (EXISTS (SELECT 1 FROM parent WHERE parent.id = child.parent_id AND parent.org_id = ...))`). Subquery RLS policies have severe query performance overhead.
