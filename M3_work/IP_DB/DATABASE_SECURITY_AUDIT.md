# FireSphere.ai — Database Security, Cryptography & Audit Forensic Audit

**Document Status:** Complete Forensic Security Audit  
**Authority:** Architectural Audit of `FireSphere_Database_Documentation_v2.xml` & Legacy SQL Dumps (`database_2.sql` to `database_61.sql`)  
**Date:** October 2026  
**Auditor:** Member 3 (Database, Infrastructure & DevOps Engineer)

---

## 1. Executive Security & Threat Overview

During the forensic audit of the legacy SQL dumps and the candidate architecture specification (`FireSphere_Database_Documentation_v2.xml`), critical vulnerabilities, cryptographic deficiencies, and credential leakages were identified in the legacy database. This document establishes the security contract, cryptographic controls, credential quarantine protocol, Change Data Capture (CDC) audit logging architecture, and regulatory compliance mapping required for FireSphere.ai on PostgreSQL 16+.

---

## 2. Authentication & Credential Storage Audit

### 2.1 Legacy Credential Storage Analysis (`database_53.sql` - `users`)
Inspection of the legacy `users` export revealed severe security failures:

| Metric / Item | Legacy Implementation | Security Risk Assessment | Production Requirement |
| :--- | :--- | :--- | :--- |
| **Password Hashing** | Unsalted SHA-256 for 5 of 6 users; 1 user with legacy bcrypt | **CRITICAL**: Unsalted SHA-256 is vulnerable to rainbow table attacks and GPU brute-force cracking within minutes. | Mandatory **bcrypt (cost factor 12)** or Argon2id for all stored credentials. |
| **Token Storage** | Plaintext verification and reset tokens stored in `metadata_json` | **CRITICAL**: Exposure of session/verification tokens allows account takeover via database dumps or read-only SQL injection. | Never store raw tokens. Store cryptographic SHA-256 hashes of tokens (`token_hash`) with mandatory TTL. |
| **Privilege Isolation** | All 6 users had `is_super_admin = 1` | **CRITICAL**: Complete breakdown of multi-tenant isolation; any compromised user possesses root platform privileges. | Strip `is_super_admin` from all accounts during ingestion. Super-admin must be restricted to platform infrastructure engineers under break-glass MFA. |
| **MFA Support** | No MFA fields present in legacy schema | **HIGH**: Lack of multi-factor authentication for emergency dispatchers and inspectors. | Add `mfa_enabled BOOLEAN DEFAULT FALSE`, `mfa_secret_encrypted TEXT`, and `totp_verified_at TIMESTAMP`. |

### 2.2 Forensic Audit of Leaked Credentials in Legacy Dumps
- **File Audited:** `database_53.sql` (`users`)
- **Findings:**
  - Column `metadata_json` contained plaintext email verification tokens, activation URLs, and device telemetry.
  - Raw secret strings have been quarantined. **(Secrets Redacted in compliance with security guidelines: `[REDACTED_VERIFICATION_TOKEN_***]`).**
  - **Remediation Action:** All legacy password hashes must be invalidated upon import. The migration pipeline must flag `force_password_reset = TRUE` and require an out-of-band email/SMS OTP reset before granting access.

### 2.3 Proposed Production Credential Architecture
Under PostgreSQL 16+ schema `firesphere`:
1. **Passwords:** Stored in `users.password_hash` as `VARCHAR(255)` utilizing `bcrypt` with cost factor 12.
2. **API Tokens (`api_keys`):**
   - Raw keys are generated client-side or during one-time display using format `fs_live_[32_secure_random_chars]`.
   - The database stores **only** the SHA-256 hash in `api_keys.key_hash` (`VARCHAR(64)`).
   - Lookup is performed via `api_keys.key_prefix` (`VARCHAR(16)`) followed by hash verification.
3. **M2M Client Secrets (`api_clients`):**
   - External OAuth2 / CAD client credentials store hashed secrets using `bcrypt`.
   - Strict IP CIDR whitelisting (`allowed_cidrs INET[]`) enforced at application and network gateway layers.

---

## 3. Cryptographic Storage & Encryption Requirements

### 3.1 Transparent Column Encryption (`pgcrypto`)
Section 8.3 of `FireSphere_Database_Documentation_v2.xml` specifies `pgcrypto` transparent encryption for third-party credentials:
- **Active Extension:** `CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA firesphere;`
- **Encrypted Entities:**
  - `org_api_credentials.api_secret`: Stored as `BYTEA` using `pgp_sym_encrypt(secret_text, current_setting('app.encryption_master_key'))`.
  - `integrations.config_json`: Integration secrets (Twilio Auth Tokens, SendGrid API Keys, WhatsApp Business API secrets) encrypted at rest.
  - `org_sso_providers.client_secret`: Encrypted SSO client secrets for OAuth2/SAML.

### 3.2 Key Management Architecture
- **Zero Hardcoding Rule:** No master encryption keys or symmetric passphrases may be committed to SQL files, Flyway scripts, or repository configuration.
- **Key Injection Protocol:** The master key is passed to PostgreSQL sessions via runtime environment variables and injected dynamically per-session:
  ```sql
  SET LOCAL app.encryption_master_key = :env_master_key;
  ```
- **At-Rest & In-Transit Cryptography:**
  - **In-Transit:** Mandatory TLS 1.3 for all client-to-database connections (`sslmode=verify-full`). Unencrypted port 5432 access blocked at container network level.
  - **At-Rest:** Tablespace storage volume encrypted via LUKS / AES-256 at host OS / cloud EBS volume tier.

---

## 4. Change Data Capture (CDC) & Tamper-Proof Audit Logging

### 4.1 Audit Engine Architecture
The audit subsystem provides an immutable legal audit trail for municipal and life-safety compliance.

```
+------------------------------------------------------------------------+
|                          PostgreSQL Engine                             |
|                                                                        |
|  [ Transaction: INSERT / UPDATE / DELETE on Critical Entity ]          |
|                               |                                        |
|                               v                                        |
|              [ firesphere_audit_trigger() ]                            |
|                               |                                        |
|      +------------------------+-----------------------+                |
|      | Captures:                                      |                |
|      | - table_name & entity_id                       |                |
|      | - action ('INSERT', 'UPDATE', 'DELETE')        |                |
|      | - old_values JSONB / new_values JSONB          |                |
|      | - changed_fields TEXT[]                        |                |
|      | - user_id & organization_id                    |                |
|      | - client_ip INET & user_agent TEXT             |                |
|      | - created_at CURRENT_TIMESTAMP                 |                |
|      +------------------------+-----------------------+                |
|                               |                                        |
|                               v                                        |
|                   [ INSERT INTO audit_logs ]                           |
|               (or cdc_audit_logs - see Conflict)                       |
+------------------------------------------------------------------------+
```

### 4.2 Audited Critical Entities
Triggers must be attached to all entities impacting statutory compliance, public safety, and financial records:
1. `buildings` & `building_risk_profiles` (Structural hazard classification changes)
2. `noc_applications`, `noc_certificates`, `noc_renewals` (Statutory fire safety approvals)
3. `inspections`, `inspection_findings`, `corrective_actions` (Enforcement audits)
4. `violations` & `violation_actions` (Legal enforcement actions, stop-work orders)
5. `incidents`, `incident_reports`, `incident_dispatches` (Emergency response actions)
6. `payments` & `payment_transactions` (Municipal revenue and statutory fees)
7. `users`, `user_roles`, `permissions` (Privilege and access control modifications)

### 4.3 Immutability & Tamper-Proof Protections
To ensure evidentiary admissibility in court inquiries following fire disasters:
- **Append-Only Table Privileges:**
  ```sql
  REVOKE UPDATE, DELETE, TRUNCATE ON firesphere.audit_logs FROM firesphere_app;
  GRANT SELECT, INSERT ON firesphere.audit_logs FROM firesphere_app;
  ```
- **Integrity Constraints:** Database rules prevent any non-superuser from disabling audit triggers or modifying audit log records.

---

## 5. Regulatory Compliance & Statutory Mapping

The candidate specification claims alignment with international enterprise security and safety standards:

| Compliance Framework | Statutory Requirement | Database Implementation Mechanism |
| :--- | :--- | :--- |
| **National Building Code (NBC) of India (Part 4)** | Life-safety inspection verification and fire NOC certification auditability. | Non-deletable inspection findings, mandatory geotagged photos with SHA-256 hash validation, immutable NOC status history. |
| **NFPA 101 / NFPA 1710** | Standard for the Organization and Deployment of Fire Suppression Operations. | Precise sub-second incident timestamps in `incident_timeline`, apparatus dispatch telemetry in `vehicle_location_history`. |
| **ISO/IEC 27001 (A.12.4)** | Logging and monitoring of system administrator activities and data access. | Trigger-based CDC audit logging recording user context, IP address, and complete before/after state diffs. |
| **SOC 2 Type II (Trust Services Criteria)** | Security, confidentiality, and tenant segregation controls. | Database-enforced Row-Level Security (RLS) with zero-trust server-side session variable binding. |
| **GDPR / Digital Personal Data Protection (DPDP) Act** | Right to erasure, access restriction, and protection of citizen PII. | Segregated `citizens` module, encrypted sensitive contact details, soft-delete mechanisms with audit trail. |

---

## 6. Security Gaps & Pre-Implementation Blockers

1. **Naming Contradiction (`audit_logs` vs `cdc_audit_logs`):** Section 4.11 and Appendix A call the table `audit_logs`, whereas Section 9 table and Section 10.1 refer to `cdc_audit_logs`. The authoritative DDL migration contract must establish one canonical name.
2. **Missing Master Key Rotation Specification:** The document specifies `pgp_sym_encrypt` but lacks an architectural procedure for periodic key rotation and re-encryption.
3. **Redacted Legacy Data Sanitization Script:** Flyway migration `V1.0.5__seed_reference_data.sql` and data cleaning rules must explicitly strip legacy plaintext tokens and quarantine legacy users prior to production go-live.
