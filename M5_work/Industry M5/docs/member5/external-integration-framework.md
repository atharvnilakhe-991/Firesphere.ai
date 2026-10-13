# Phase 8 — External Integration Framework

## Overview
Phase 8 implements the enterprise-grade **External Integration Framework** for FireSphere.ai. It decouples business modules from external services and third-party SDKs using clean internal contracts, provider adapters, deterministic mock implementations, idempotency caching, correlation ID tracing, bounded retry policies, and structured security-conscious logging.

---

## Architecture

```
+-------------------------------------------------------------+
|                Business / Operations Layer                 |
| (Incident, Dispatch, Fleet, Inventory, GIS, Admin, AI Ops)  |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|            Internal Integration Interfaces (API)            |
| - PaymentIntegration             - SmsIntegration           |
| - EmailIntegration               - WhatsAppIntegration      |
| - MapsIntegration                - DigitalSignatureInteg.   |
| - GovernmentServiceIntegration                              |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                      Provider Adapters                      |
| - Idempotency Check (InMemoryIdempotencyStore)              |
| - Correlation ID Tracking (CorrelationContext)              |
| - Bounded Exponential Retries (IntegrationRetryExecutor)    |
| - Credential-Sanitized Logging (IntegrationLogger)          |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|            Provider SPI & Deterministic Mocks               |
| - MockPaymentProvider            - MockSmsProvider          |
| - MockEmailProvider              - MockWhatsAppProvider     |
| - MockMapsProvider               - MockDigitalSignatureProv.|
| - MockGovernmentServiceProvider                             |
+-------------------------------------------------------------+
```

---

## Integration Domains

### 1. Payment Integration
- **Internal Interface**: `ai.firesphere.integration.api.payment.PaymentIntegration`
- **Adapter**: `PaymentIntegrationAdapter`
- **Mock Provider**: `MockPaymentProvider`
- **Operations**:
  - `initiatePayment`: Issues gateway order and checkout URL.
  - `verifyPayment`: Verifies cryptographic payment signatures.
  - `checkStatus`: Queries current settlement state.

### 2. SMS Integration
- **Internal Interface**: `ai.firesphere.integration.api.sms.SmsIntegration`
- **Adapter**: `SmsIntegrationAdapter`
- **Mock Provider**: `MockSmsProvider`
- **Operations**:
  - `sendSms`: Dispatches SMS alerts with correlation IDs and idempotency protection.

### 3. Email Integration
- **Internal Interface**: `ai.firesphere.integration.api.email.EmailIntegration`
- **Adapter**: `EmailIntegrationAdapter`
- **Mock Provider**: `MockEmailProvider`
- **Operations**:
  - `sendEmail`: Sends transactional HTML/text emails (e.g. NOC revocation, inspection notifications).

### 4. WhatsApp Integration
- **Internal Interface**: `ai.firesphere.integration.api.whatsapp.WhatsAppIntegration`
- **Adapter**: `WhatsAppIntegrationAdapter`
- **Mock Provider**: `MockWhatsAppProvider`
- **Operations**:
  - `sendWhatsApp`: Dispatches templated WhatsApp messages with dynamic placeholders.

### 5. Maps & Routing Integration
- **Internal Interface**: `ai.firesphere.integration.api.maps.MapsIntegration`
- **Adapter**: `MapsIntegrationAdapter`
- **Mock Provider**: `MockMapsProvider`
- **Operations**:
  - `geocode`: Resolves address strings to geographic coordinates.
  - `reverseGeocode`: Resolves geographic coordinates to street addresses.
  - `calculateRoute`: Computes emergency distance and estimated duration.

### 6. Digital Signature Integration
- **Internal Interface**: `ai.firesphere.integration.api.signature.DigitalSignatureIntegration`
- **Adapter**: `DigitalSignatureIntegrationAdapter`
- **Mock Provider**: `MockDigitalSignatureProvider`
- **Operations**:
  - `signDocument`: Applies PKI digital signature and produces cryptographic signature token.
  - `verifySignature`: Validates document signature and certificate authority chain.

### 7. Government & Municipal Emergency CAD (112/911)
- **Internal Interface**: `ai.firesphere.integration.api.government.GovernmentServiceIntegration`
- **Adapter**: `GovernmentServiceIntegrationAdapter`
- **Mock Provider**: `MockGovernmentServiceProvider`
- **Operations**:
  - `transmitCadIncident`: Transmits emergency incident to central municipal emergency CAD exchange.
  - `queryCadStatus`: Queries external dispatch status.
  - `verifyCompliance`: Queries municipal database for fire safety NOC status.

### 8. Operational Integration Service (AI Inspections)
- **Service**: `OperationalIntegrationServiceImpl`
- **Operations**:
  - `ingestAIFinding`: Ingests computer vision findings (blocked exits, missing equipment).
  - `getAIFinding`: Fetches finding by UUID.
  - `reviewAIFinding`: Records Human-in-the-Loop review by fire officers.
  - `transmitToCad`: Transmits incidents via emergency CAD contract.

---

## Core Framework Capabilities

1. **Correlation Tracing**:
   - `CorrelationContext` manages ThreadLocal correlation IDs across layers.
   - Handled via `X-Correlation-ID` header or auto-generated as `CORR-XXXXXXXX`.
2. **Idempotency Protection**:
   - `IdempotencyStore` / `InMemoryIdempotencyStore` caches successful integration responses by `IdempotencyKey`.
   - Repeated calls with the same key immediately return `IntegrationStatus.DUPLICATE` with cached payloads, preventing duplicate external side effects.
3. **Resilience & Retry**:
   - `IntegrationRetryExecutor` performs bounded exponential backoff retries.
   - Only transient / retryable errors (`category == TIMEOUT` or `retryable == true`) are retried. Non-retryable errors abort immediately.
4. **Structured Logging & Credential Sanitization**:
   - `IntegrationLogger` records structured logs with correlation IDs and latencies.
   - Credentials, authorization tokens, and secrets are strictly redacted before output.

---

## REST Endpoints (`/api/v1/integrations`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/payment/initiate` | Initiate payment gateway checkout |
| `POST` | `/payment/verify` | Verify payment settlement |
| `GET` | `/payment/status/{transactionId}` | Query payment status |
| `POST` | `/sms/send` | Dispatch SMS message |
| `POST` | `/email/send` | Dispatch transactional email |
| `POST` | `/whatsapp/send` | Dispatch WhatsApp message |
| `POST` | `/maps/geocode` | Geocode address to lat/lng |
| `POST` | `/maps/reverse-geocode` | Reverse geocode lat/lng to address |
| `POST` | `/maps/route` | Calculate emergency driving route |
| `POST` | `/signature/sign` | PKI digital signing |
| `POST` | `/signature/verify` | Verify digital signature |
| `POST` | `/cad/transmit` | Transmit incident to 112 CAD |
| `GET` | `/cad/status/{externalCadId}` | Query 112 CAD status |
| `POST` | `/cad/compliance` | Lookup building safety compliance |
| `POST` | `/ai-findings` | Ingest computer vision finding |
| `GET` | `/ai-findings/{id}` | Retrieve AI finding |
| `POST` | `/ai-findings/{id}/review` | Human officer review of AI finding |

---

## Automated Verification

- `IntegrationFrameworkTest`: 15 comprehensive unit and service tests verifying all 7 integrations, retry recovery, idempotency deduplication, timeout handling, non-retryable aborts, and correlation propagation.
- `IntegrationOperationsControllerTest`: 10 Spring Boot MockMvc tests verifying all HTTP REST endpoints, status codes, and header propagation.
- Full test suite: **254 tests run (251 passed, 0 failures, 0 errors, 3 skipped PostGIS container tests)**.
