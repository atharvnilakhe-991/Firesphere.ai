# FireSphere.ai — Database Migrations & Architecture Foundation

**Role:** Member 3 (Database, Infrastructure & DevOps)  
**Target Engine:** PostgreSQL 16+ with PostGIS 3.4+  
**Target Schema:** `firesphere`  
**Migration Tool:** Flyway (Version-Controlled & Deterministic)

---

## 1. Directory Structure

```text
database/
├── migrations/          # Immutable, versioned DDL migration scripts (V1.x.x)
│   ├── V1.0.0__core_extensions_and_helpers.sql
│   ├── V1.0.1__core_tenancy_and_identities.sql
│   ├── V1.0.2__spatial_infrastructure_and_locations.sql
│   ├── V1.0.3__operations_noc_and_inspections.sql
│   └── V1.0.4__emergency_incidents_fleet_and_audit.sql
├── seeds/               # Repeatable seed scripts for reference taxonomy & demo tenants (R__xx)
│   └── R__01_seed_reference_data.sql
├── tests/               # Automated SQL verification tests
│   ├── 01_verify_extensions_and_schemas.sql
│   ├── 02_verify_spatial_indexes.sql
│   ├── 03_verify_relational_integrity.sql
│   └── 04_verify_audit_triggers.sql
└── README.md            # This documentation
```

---

## 2. Migration Principles & Rules

1. **Deterministic Execution:** Migrations execute in alphanumeric version order (`V1.0.0` -> `V1.0.1` -> ...).
2. **Immutable:** Once committed and applied to any environment, migration files must NEVER be edited in place. Any schema evolution requires a new version (`V1.0.5`, `V1.0.6`, etc.).
3. **Repeatable Seeds:** Reference and static lookup data are maintained in `database/seeds/R__*.sql` and re-executed whenever their checksum changes.
4. **Idempotent Constraints:** Tables use `CREATE TABLE IF NOT EXISTS` and explicit constraints to ensure clean execution from an empty database.
5. **No Speculative Schema Additions:** Only clearly specified, audited database structures are modeled. Unresolved architectural items are ring-fenced behind explicit integration boundaries.

---

## 3. Explicit Implementation Boundaries (Blocked / Unresolved Components)

In accordance with Member 3 rules, the following entities are **NOT** included in the initial migration set to avoid speculative modeling:

```text
TABLE/COMPONENT: inventory_categories, notification_templates, payments, purchase_order_items, purchase_orders, vendors
Status: BLOCKED / PENDING INTEGRATION
Reason: Omitted from Section 6 column dictionaries; present only in Appendix A
Source Conflict: CONFLICT-01 & CONFLICT-02 (ADR-004)
Required Decision: Formal sign-off on 85 vs 91 table scope; provide column DDL dictionaries if 91 selected
Dependent Members: Member 1 (Procurement/Finance) & Platform Architect

TABLE/COMPONENT: incident_ai_analysis
Status: BLOCKED / PENDING INTEGRATION
Reason: Completely absent from candidate XML architecture (0 occurrences)
Source Conflict: CONFLICT-03 (ADR-002, ADR-005)
Required Decision: Selection between generic polymorphic ai_insights vs dedicated DDL table
Dependent Members: Member 2 (AI Lead) & Platform Architect
```

---

## 4. How to Run Migrations

### Via Docker Compose (Recommended)
```bash
# Run Flyway container against the local PostgreSQL stack
docker compose run --rm flyway migrate
```

### Via Flyway CLI
```bash
flyway -url=jdbc:postgresql://localhost:5432/firesphere \
       -user=firesphere_admin \
       -password=postgres_dev_password_change_me \
       -schemas=firesphere \
       -locations=filesystem:./database/migrations,filesystem:./database/seeds \
       migrate
```

### Check Migration Status
```bash
docker compose run --rm flyway info
```

---

## 5. How to Run Database Validation Tests

```bash
# Execute test scripts inside the running postgres container
docker compose exec postgres psql -U firesphere_admin -d firesphere -f /workspace/database/tests/01_verify_extensions_and_schemas.sql
docker compose exec postgres psql -U firesphere_admin -d firesphere -f /workspace/database/tests/02_verify_spatial_indexes.sql
docker compose exec postgres psql -U firesphere_admin -d firesphere -f /workspace/database/tests/03_verify_relational_integrity.sql
docker compose exec postgres psql -U firesphere_admin -d firesphere -f /workspace/database/tests/04_verify_audit_triggers.sql
```
*(Or invoke `bash scripts/validate_foundation.sh`)*
