# FireSphere.ai — Master Table Inventory & Reconciliation Audit

**SOURCE DOCUMENT**: `FireSphere_Database_Documentation_v2.xml`  
**STATUS**: `FORENSIC AUDIT COMPLETED`  
**CLAIMED TOTAL**: 85 Tables (60 Legacy + 25 New)  
**ACTUAL EXTRACTED TOTAL**: 91 Tables Referenced in Document  
**DISCREPANCY DETECTED**: Exactly 6 tables are listed as `(NEW)` in Appendix A and referenced in relational topology (Section 7), but lack column-level dictionary specifications in Section 6.

---

## Master Table Inventory Matrix

| # | Table | Legacy / New | Module | Stated Purpose | PK | Tenant Key | Spatial | Transformation | Status |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `ai_agent_workflow_steps` | Legacy | AI / ML | Execution steps for multi-agent chains | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 2 | `ai_agent_workflows` | Legacy | AI / ML | Autonomous agent task orchestration pipelines | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 3 | `ai_agents` | Legacy | AI / ML | Autonomous AI agents catalog | BIGINT / BIGSERIAL | org_id | No | MODIFY | `CONFIRMED` |
| 4 | `ai_insights` | Legacy | AI / ML | Predictive risk & compliance findings | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 5 | `ai_models` | Legacy | AI / ML | Active LLMs, vision nets, and risk models | BIGINT / BIGSERIAL | org_id (nullable) | No | MODIFY | `CONFIRMED` |
| 6 | `ai_prompts` | Legacy | AI / ML | Parameterized prompt templates | BIGINT / BIGSERIAL | org_id | No | KEEP | `INFERRED` |
| 7 | `ai_runs` | Legacy | AI / ML | Audit trace of AI latency & token cost | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 8 | `ai_sessions` | Legacy | AI / ML | Interactive conversational sessions | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 9 | `api_clients` | Legacy | Security | OAuth2/M2M external client credentials | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 10 | `api_keys` | Legacy | Security | Programmatic API access tokens | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 11 | `audit_logs` | Legacy | Audit | Trigger-driven CDC audit logging | BIGSERIAL | org_id | No | REPLACE | `CONFLICT` |
| 12 | `audits` | Legacy | Operations | Station operational readiness audits | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 13 | `beats` | Legacy | GIS | Neighborhood patrol & inspection territory | BIGSERIAL | org_id | MultiPolygon | MODIFY | `INFERRED` |
| 14 | `buildings` | Legacy | GIS | Master registry of structures subject to NBC | BIGSERIAL | org_id | Point + Polygon | SPLIT | `INFERRED` |
| 15 | `citizens` | Legacy | Citizen | Public users registering complaints & NOC | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 16 | `complaints` | Legacy | Citizen | Citizen fire safety grievance reports | BIGINT / BIGSERIAL | org_id | Point | MODIFY | `INFERRED` |
| 17 | `dashboard_metrics` | Legacy | Analytics | Pre-aggregated daily KPI aggregates | BIGINT / BIGSERIAL | org_id | No | KEEP | `INFERRED` |
| 18 | `departments` | Legacy | Org | Internal municipal administrative branches | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 19 | `divisions` | Legacy | Org | Primary municipal regional divisions | BIGINT / BIGSERIAL | org_id | MultiPolygon | MODIFY | `INFERRED` |
| 20 | `documents` | Legacy | Storage | Metadata catalog for S3/MinIO files | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 21 | `fire_stations` | Legacy | GIS / Ops | Fire station headquarters & dispatch bases | BIGSERIAL | org_id | Point + Polygon | MODIFY | `CONFIRMED` |
| 22 | `fire_vehicles` | Legacy | Fleet | Firefighting apparatus inventory | BIGSERIAL | org_id | Point (telemetry) | SPLIT | `INFERRED` |
| 23 | `generated_reports` | Legacy | Analytics | Generated official PDF summaries | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 24 | `hydrants` | Legacy | Water / GIS | Municipal hydrants & pressure ratings | BIGSERIAL | org_id | Point | MODIFY | `INFERRED` |
| 25 | `incident_evidence` | Legacy | Incidents | Forensic scene photos, audio, files | BIGSERIAL | org_id | Point (capture) | MODIFY | `INFERRED` |
| 26 | `incidents` | Legacy | Incidents | Emergency dispatch calls & structural fires | BIGSERIAL | org_id | Point | SPLIT | `INFERRED` |
| 27 | `inspection_checklists` | Legacy | Inspection | Master NBC inspection checklist templates | BIGINT / BIGSERIAL | org_id | No | KEEP | `INFERRED` |
| 28 | `inspection_photos` | Legacy | Inspection | Photographic evidence captured on-site | BIGSERIAL | org_id | Point | MODIFY | `INFERRED` |
| 29 | `inspections` | Legacy | Inspection | Core fire safety field inspections | BIGSERIAL | org_id | No | SPLIT | `INFERRED` |
| 30 | `integrations` | Legacy | Connectors | Third-party webhooks & SCADA connectors | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 31 | `inventory_items` | Legacy | Inventory | Master catalog of firefighting equipment | BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 32 | `inventory_transactions`| Legacy | Inventory | Stock movement receipts & issues ledger | BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 33 | `mock_drills` | Legacy | Training | Fire evacuation drills conducted at sites | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 34 | `noc_application_categories` | Legacy | Fire NOC | Building categories & statutory fee rates | BIGINT / BIGSERIAL | org_id (nullable) | No | MODIFY | `CONFIRMED` |
| 35 | `noc_application_documents` | Legacy | Fire NOC | Uploaded architectural drawings & NOC docs | BIGINT / BIGSERIAL | Indirect | No | MODIFY | `INFERRED` |
| 36 | `noc_applications` | Legacy | Fire NOC | End-to-end statutory NOC applications | BIGSERIAL | org_id | No | SPLIT | `INFERRED` |
| 37 | `notifications` | Legacy | Comms | Omnichannel dispatch alerts & notices | BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 38 | `org_api_credentials` | Legacy | Security | Encrypted tenant gateway secrets | BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 39 | `org_settings` | Legacy | Org | Tenant key-value configuration flags | BIGSERIAL | org_id | No | MODIFY | `CONFIRMED` |
| 40 | `org_sso_providers` | Legacy | IAM | SAML 2.0 / OIDC identity configurations | BIGSERIAL | org_id | No | MODIFY | `CONFIRMED` |
| 41 | `organizations` | Legacy | Org | Multi-tenant municipal root authority | BIGSERIAL / UUID | None (Root) | No | MODIFY | `CONFIRMED` |
| 42 | `payment_transactions` | Legacy | Finance | Gateway transaction callbacks & raw payloads | BIGSERIAL | Indirect | No | MODIFY | `INFERRED` |
| 43 | `permissions` | Legacy | IAM | Security permissions catalog (globalized) | BIGSERIAL | Global (None) | No | MODIFY | `CONFIRMED` |
| 44 | `role_permissions` | Legacy | IAM | Many-to-many role-to-permission mapping | Composite PK | Global (None) | No | KEEP | `INFERRED` |
| 45 | `roles` | Legacy | IAM | System and custom tenant role definitions | BIGSERIAL | org_id (nullable) | No | MODIFY | `CONFIRMED` |
| 46 | `security_policies` | Legacy | Security | Tenant lockout and session rules | BIGSERIAL | org_id | No | MODIFY | `CONFIRMED` |
| 47 | `sso_user_identities` | Legacy | IAM | Federated SSO subject claims (legacy name) | BIGSERIAL | org_id | No | REPLACE | `CONFLICT` |
| 48 | `training_courses` | Legacy | Training | Firefighter curriculum & courses | BIGINT / BIGSERIAL | org_id | No | KEEP | `INFERRED` |
| 49 | `training_enrollments` | Legacy | Training | Trainee course participation & scores | BIGINT / BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 50 | `user_roles` | Legacy | IAM | User-to-role assignment junction | Composite PK | Indirect | No | MODIFY | `CONFIRMED` |
| 51 | `user_sessions` | Legacy | IAM | Active JWT refresh tokens & sessions | BIGSERIAL | Indirect | No | MODIFY | `INFERRED` |
| 52 | `users` | Legacy | IAM | Core user accounts for personnel & admins | BIGSERIAL | org_id | No | MODIFY | `CONFIRMED` |
| 53 | `vehicle_maintenance` | Legacy | Fleet | Fleet workshop repair & service logs | BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 54 | `vehicle_trip_logs` | Legacy | Fleet | Apparatus emergency trip records | BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 55 | `violations` | Legacy | Violations | Statutory non-compliance notices | BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 56 | `water_sources` | Legacy | Water / GIS | Emergency water supply points & reservoirs | BIGSERIAL | org_id | Point | MODIFY | `INFERRED` |
| 57 | `workflow_definitions` | Legacy | Workflow | Configurable approval workflows | BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 58 | `workflow_runs` | Legacy | Workflow | Active runtime workflow instances | BIGSERIAL | org_id | No | MODIFY | `INFERRED` |
| 59 | `workflow_steps` | Legacy | Workflow | Step definitions, roles, SLA hours | BIGSERIAL | Indirect | No | MODIFY | `INFERRED` |
| 60 | `zones` | Legacy | GIS | Geographic fire operational zones | BIGSERIAL | org_id | Polygon | MODIFY | `INFERRED` |
| 61 | `building_owners` | New (6.1) | GIS | Ownership shares & corporate entities | BIGSERIAL | org_id | No | NEW | `REQUIRES APPROVAL` |
| 62 | `building_contacts` | New (6.2) | GIS | 24/7 emergency contacts per building | BIGSERIAL | org_id | No | NEW | `REQUIRES APPROVAL` |
| 63 | `building_documents` | New (6.3) | GIS / Docs | Blueprints, structural elevation CAD plans | BIGSERIAL | org_id | No | NEW | `REQUIRES APPROVAL` |
| 64 | `building_risk_profiles` | New (6.4) | AI / Risk | AI risk scores & NBC hazard class | BIGSERIAL | org_id | No | NEW | `REQUIRES APPROVAL` |
| 65 | `building_fire_equipment` | New (6.5) | Compliance | Fixed fire protection equipment tracking | BIGSERIAL | org_id | No | NEW | `REQUIRES APPROVAL` |
| 66 | `noc_application_status_history` | New (6.6) | Fire NOC | Immutable append-only NOC state history | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 67 | `noc_certificates` | New (6.7) | Fire NOC | Cryptographic Fire Safety Certificates | BIGSERIAL | org_id | No | NEW | `REQUIRES APPROVAL` |
| 68 | `noc_renewals` | New (6.8) | Fire NOC | Annual/triennial NOC renewal cycles | BIGSERIAL | org_id | No | NEW | `REQUIRES APPROVAL` |
| 69 | `inspection_assignments` | New (6.9) | Inspection | Multi-officer inspection teams | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 70 | `inspection_checklist_items` | New (6.10) | Inspection | Discrete NBC compliance questions | BIGSERIAL | Global / Indirect | No | NEW | `REQUIRES APPROVAL` |
| 71 | `inspection_findings` | New (6.11) | Inspection | Field officer observations & measured values | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 72 | `violation_actions` | New (6.12) | Violations | Statutory notices, compounding fines, sealing | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 73 | `corrective_actions` | New (6.13) | Violations | Remedial repair evidence from owners | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 74 | `incident_dispatches` | New (6.14) | Incidents | CAD alarm turnouts & response timestamps | BIGSERIAL | org_id | No | NEW | `REQUIRES APPROVAL` |
| 75 | `incident_vehicles` | New (6.15) | Incidents | Apparatus deployment milestones ledger | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 76 | `incident_crews` | New (6.16) | Incidents | Firefighter SCBA accountability on-scene | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 77 | `incident_timeline` | New (6.17) | Incidents | Tamper-proof real-time incident chronology | BIGSERIAL | Indirect | Point | NEW | `REQUIRES APPROVAL` |
| 78 | `incident_casualties` | New (6.18) | Incidents | EMS triage classification & injuries | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 79 | `incident_reports` | New (6.19) | Incidents | Comprehensive post-fire investigation report | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 80 | `vehicle_location_history` | New (6.20) | Fleet | High-frequency AVL GPS telemetry stream | BIGSERIAL | Indirect | Point | NEW | `REQUIRES APPROVAL` |
| 81 | `vehicle_crew` | New (6.21) | Fleet | Daily duty rosters & apparatus shifts | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 82 | `vehicle_fuel_logs` | New (6.22) | Fleet | Fuel dispense quantities & odometer readings | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 83 | `vehicle_fitness` | New (6.23) | Fleet | RTO vehicle roadworthiness certificates | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 84 | `warehouses` | New (6.24) | Inventory | Central depots, station equipment stores | BIGSERIAL | org_id | No | NEW | `REQUIRES APPROVAL` |
| 85 | `inventory_stock` | New (6.25) | Inventory | Current atomic on-hand stock per warehouse | BIGSERIAL | Indirect | No | NEW | `REQUIRES APPROVAL` |
| 86 | `inventory_categories` | Discrepancy (Appx A #46) | Inventory | Equipment categorization lookup | `MISSING` | `MISSING` | No | NEW | `MISSING` |
| 87 | `notification_templates` | Discrepancy (Appx A #57) | Comms | Pre-compiled multi-channel alert templates | `MISSING` | `MISSING` | No | NEW | `MISSING` |
| 88 | `payments` | Discrepancy (Appx A #64) | Finance | Statutory billing invoice master | `MISSING` | `MISSING` | No | NEW | `MISSING` |
| 89 | `purchase_order_items` | Discrepancy (Appx A #66) | Supply Chain | Procurement purchase order line items | `MISSING` | `MISSING` | No | NEW | `MISSING` |
| 90 | `purchase_orders` | Discrepancy (Appx A #67) | Supply Chain | Equipment procurement purchase orders | `MISSING` | `MISSING` | No | NEW | `MISSING` |
| 91 | `vendors` | Discrepancy (Appx A #83) | Supply Chain | Approved suppliers & maintenance contractors | `MISSING` | `MISSING` | No | NEW | `MISSING` |

---

## Final Inventory Totals & Breakdown

- **Stated Total Tables Claimed**: 85
- **Legacy Tables Extracted**: 60
  - Confirmed from Data: 12
  - Inferred (Empty Dumps): 46
  - Inferred & Conflict (`audit_logs`, `sso_user_identities`): 2
- **New Tables Extracted with Full DDL (Section 6)**: 25
  - Confirmed / Documented: 25 (marked `REQUIRES APPROVAL` pending Project Lead signoff)
- **Sum of Documented Tables**: 60 + 25 = **85**
- **Discrepancy Tables in Appendix A Without DDL**: 6 (`inventory_categories`, `notification_templates`, `payments`, `purchase_order_items`, `purchase_orders`, `vendors`)
- **Total Named Tables Referenced**: **91**

### Discrepancy Report
The XML document asserts an **85-table architecture** in its title, executive summary, Section 6, and Section 9. However, Appendix A contains **91 numbered entries**. The 6 extra tables are marked as `MISSING — REQUIRED CONFIRMATION` because the document fails to provide their column data dictionaries in Section 6.
