# FireSphere.ai — Database Migration & Schema Evolution Guide

**Role:** Member 3 (Database, Infrastructure & DevOps)  
**Target Engine:** PostgreSQL 16+ with PostGIS 3.4+  
**Authority:** Sole Custodian of Schema DDL & Flyway Migrations

---

## 1. Migration Custodianship & Team Rules

1. **Sole Custodian Rule:** Member 3 is the sole authorized custodian of all database migrations and DDL statements. Backend services (M2), mobile/frontend (M1), AI pipelines (M4), and operations (M5) must NEVER execute manual DDL, modify production schemas, or configure JPA/Hibernate `ddl-auto=update`.
2. **Immutable Migrations:** Once a versioned migration (`V*.sql`) is committed to the repository, it must NEVER be modified, renamed, or deleted. Any bug fix or structural modification requires authoring a new versioned migration script.
3. **Repeatable Seeds:** Reference taxonomy, status lookups, and system roles are maintained in repeatable migration files (`R__*.sql`). These execute automatically whenever their checksum changes.
4. **Deterministic Execution:** Flyway applies migrations strictly in version order. All migrations must be idempotent and repeatable from a blank database.

---

## 2. Migration File Naming Convention

Flyway migration scripts are located in [database/migrations/](file:///d:/IP%20Folder/database/migrations) and follow this strict standard:

```text
V<Major>.<Minor>.<Patch>__<concise_snake_case_description>.sql
```

### Version Hierarchy
- **Major (`V1.x.x`):** Foundational architectural baseline and major module additions.
- **Minor (`V1.1.x`):** New functional sub-modules, table additions, or non-breaking feature schemas.
- **Patch (`V1.0.1`):** Index additions, new columns, constraint modifications, or trigger updates.

### Baseline Migration Chain (M3 Handoff Baseline)
- `V1.0.0__core_extensions_and_helpers.sql`
- `V1.0.1__core_tenancy_and_identities.sql`
- `V1.0.2__spatial_infrastructure_and_locations.sql`
- `V1.0.3__operations_noc_and_inspections.sql`
- `V1.0.4__emergency_incidents_fleet_and_audit.sql`
- `V1.0.5__enable_row_level_security.sql`
- `V1.0.6__add_secondary_foreign_key_indexes.sql`
- `R__01_seed_reference_data.sql`

*Note: Migrations `V1.0.0` through `V1.0.6` are complete and frozen for cross-member contract integration (see [DATABASE_M3_FINAL_HANDOFF.md](file:///d:/IP%20Folder/DATABASE_M3_FINAL_HANDOFF.md)). Any new schema evolution requested during integration will be authored forward-only starting at `V1.0.7`.*


---

## 3. How Other Team Members Request Schema Changes

When Member 1, Member 2, Member 4, or Member 5 requires a new table, an additional column, or an index optimization:

```text
+-------------------+      +-------------------+      +-------------------+      +-------------------+
|  Team Member      | ---> |  Member 3 Review  | ---> |  Flyway Migration | ---> |  CI Validation &  |
|  Schema Request   |      |  & Impact Audit   |      |  Script Authored  |      |  Integration Test |
+-------------------+      +-------------------+      +-------------------+      +-------------------+
```

### Request Submission Format
When requesting a change, submit:
1. Target table and intended business purpose.
2. Proposed column name, PostgreSQL data type, and nullability.
3. Foreign key relationship and target table.
4. Primary read/write query patterns (to determine indexing strategy).
5. Estimated data volume and lifecycle retention requirements.

Member 3 will audit the request for 3NF normalization, tenant isolation compliance, and performance, author the next Flyway migration, and push it to the repository.

---

## 4. Flyway Command Reference

### Running Migrations
```bash
docker compose run --rm flyway migrate
```

### Inspecting Migration Status & Checksums
```bash
docker compose run --rm flyway info
```

### Validating Applied Migrations Against Filesystem
```bash
docker compose run --rm flyway validate
```

### Repairing Flyway Metadata Table (After Failed Run)
```bash
docker compose run --rm flyway repair
```

---

## 5. Troubleshooting Common Issues

### Issue: Checksum Mismatch Error
- **Cause:** A committed migration file was edited locally after having been applied to the database.
- **Remedy:** Never edit applied migrations. If developing locally, you can reset with `docker compose down -v` and `docker compose up -d`. In shared environments, run `flyway repair` only under M3 supervision.

### Issue: Circular Foreign Key Deadlock on Insert
- **Cause:** Mutually dependent foreign keys (e.g. `departments` and `users`).
- **Remedy:** Use `DEFERRABLE INITIALLY DEFERRED` constraint behavior. Ensure transactions are committed together.
