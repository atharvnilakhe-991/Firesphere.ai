# FireSphere.ai — Member 5: Notification System Module
**Document Version:** 1.0.0  
**Phase:** 7 — Notification System  
**Status:** Implemented, Tested & Verified  
**Runtime:** Java 21 LTS | Spring Boot 3.3.4 | Multi-Channel Mock Providers  

---

## 1. Notification Architecture

The Notification module orchestrates multi-channel emergency and operational communications across fire service stakeholders.

The design implements strict dependency inversion and pipeline separation:

$$\text{Event} \longrightarrow \text{Notification Service} \longrightarrow \text{Template Resolver} \longrightarrow \text{Recipient Validator} \longrightarrow \text{Channel Dispatcher} \longrightarrow \text{Provider}$$

### Key Components

* **Event Layer (`NotificationEvent`)**: Generic, decoupled notification event representation containing event type, reference ID, tenant context, multi-channel recipient mappings, variable payload, timestamp, priority, and idempotency key.
* **Service Layer (`NotificationService` / `NotificationServiceImpl`)**: Central orchestrator enforcing tenant isolation, idempotency checks, recipient validation, template resolution, bounded retries, and delivery history.
* **Template Registry (`NotificationTemplateRegistry`)**: Deterministic registry resolving channel-specific templates (`NotificationTemplate`) for all 8 standard events with safe `{{variable}}` substitution and mandatory variable enforcement.
* **Validation Layer (`RecipientValidator`)**: Channel-aware recipient format verification (E.164 phone numbers, RFC-compliant emails, device push tokens) executed strictly before provider dispatch.
* **Channel & Provider Abstraction**:
  * Channels: `PUSH`, `SMS`, `EMAIL`, `WHATSAPP`
  * Provider contracts: `PushNotificationProvider`, `SmsNotificationProvider`, `EmailNotificationProvider`, `WhatsAppNotificationProvider`
* **Mock Providers (`ai.firesphere.notification.provider.mock.*`)**: Deterministic, test-friendly mock implementations (`MockPushProvider`, `MockSmsProvider`, `MockEmailProvider`, `MockWhatsAppProvider`) capturing delivery receipts and supporting simulated network/gateway failures.
* **Repository Abstraction (`NotificationRecordRepository`)**: Thread-safe in-memory storage (`InMemoryNotificationRecordRepository`) maintaining delivery history, attempt logs, and idempotency indexes without creating unapproved database schema changes.

---

## 2. Supported Channels & Mock Providers

| Channel | Provider Interface | Mock Implementation | Validation Rules |
|---|---|---|---|
| `PUSH` | `PushNotificationProvider` | `MockPushProvider` | Non-empty, 8-256 alphanumeric characters, no spaces (`^[A-Za-z0-9_\-:.]{8,256}$`) |
| `SMS` | `SmsNotificationProvider` | `MockSmsProvider` | E.164 phone number, 8-15 digits (`^\+?[1-9]\d{7,14}$`) |
| `EMAIL` | `EmailNotificationProvider` | `MockEmailProvider` | RFC-standard email format (`^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`) |
| `WHATSAPP` | `WhatsAppNotificationProvider` | `MockWhatsAppProvider` | E.164 phone number, 8-15 digits (`^\+?[1-9]\d{7,14}$`) |

---

## 3. Supported Events & Templates

All 8 events are registered across all 4 channels in `NotificationTemplateRegistry`:

1. `incident_assignment` (Ref: Incident ID; Vars: `incidentNumber`, `address`, `severity`)
2. `dispatch` (Ref: Dispatch Order Code; Vars: `dispatchCode`, `unitCode`, `location`)
3. `vehicle_alert` (Ref: Apparatus Code; Vars: `vehicleCode`, `alertType`, `details`)
4. `low_inventory` (Ref: SKU; Vars: `sku`, `itemName`, `quantity`, `stationId`)
5. `maintenance_reminder` (Ref: Asset Code; Vars: `assetCode`, `dueDate`)
6. `system_alert` (Ref: Alert Reference; Vars: `alertTitle`, `severity`, `message`)
7. `inspection_notification` (Ref: Inspection Code; Vars: `inspectionCode`, `facilityName`, `date`)
8. `certificate_notification` (Ref: Certificate Number; Vars: `certNumber`, `facilityName`, `status`)

---

## 4. Priority & Status Lifecycle

### Priority Levels
* `LOW`
* `NORMAL` (Default)
* `HIGH`
* `CRITICAL`

### Status Lifecycle
* `CREATED`: Initialized record.
* `PENDING`: Initial delivery attempt failed; record remains retryable within attempt limit.
* `SENT`: Successfully acknowledged by provider.
* `FAILED`: Delivery permanently failed (validation error, template rendering failure, or retry attempts exhausted).

---

## 5. Retry & Idempotency Behavior

* **Bounded Retries**: Default max attempts = 3. Each attempt increments `attemptCount` and appends an immutable `DeliveryAttempt` record with timestamp, status, error message, and provider message ID.
* **Deterministic Idempotency**: Submitting an event with a duplicate logical `idempotencyKey` returns the existing notification record without re-dispatching to the underlying provider.

---

## 6. API Catalog

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/notifications/events` | Submit notification event across one or more channels |
| `GET` | `/api/v1/notifications/{id}` | Get notification record and attempt history by ID |
| `POST` | `/api/v1/notifications/{id}/retry` | Retry a failed or pending notification delivery |
| `GET` | `/api/v1/notifications` | List all notifications for current tenant |
| `POST` | `/api/v1/notifications/broadcast` | Broadcast operational alarm (Phase 1 bridge) |
| `POST` | `/api/v1/notifications/receipts/{id}/ack` | Acknowledge broadcast receipt with officer badge |

---

## 7. Test Strategy & Verification

* **Unit Tests (`RecipientValidatorTest`)**: 30 parameterized tests verifying phone, email, and push token validation.
* **Core Service Tests (`NotificationServiceTest`)**: 8 tests covering:
  1. Successful notification flow (`SENT` status, history recorded)
  2. Provider failure and retry boundary (`FAILED` after max attempts)
  3. Retry recovery (`SENT` on second attempt with complete history)
  4. Idempotency deduplication (no duplicate provider calls)
  5. Multi-channel independent delivery (channel failure does not impact other channels)
  6. Recipient validation rejection (pre-provider failure)
  7. Template selection & variable substitution across all 8 events
  8. Multi-tenant isolation
* **Controller MockMvc Tests (`NotificationControllerTest`)**: 5 tests verifying REST endpoints.
* **Total Phase 7 Tests**: 43 tests passed (0 failures, 0 errors).
* **Full Regression Suite**: 229 tests run (226 passed, 0 failures, 0 errors, 3 skipped PostGIS container tests).
