# Member 4 — AI/ML + Computer Vision Implementation Plan

## Ownership
Python/FastAPI AI service, CV, OCR, AI jobs, model inference, findings, severity/risk, XAI, report drafting and AI integration.

## Existing completed phases to verify
### Phase 1
- [x] FastAPI/config/logging/security
- [x] schemas
- [x] health/readiness
- [x] AI run/finding contracts
- [x] placeholder pipeline/tests

### Phase 2
- [x] JPEG/PNG/WebP validation
- [x] magic bytes
- [x] 20MB limit
- [x] minimum dimensions
- [x] OpenCV decoding
- [x] RGB conversion
- [x] letterbox
- [x] normalization
- [x] bbox transforms
- [x] multi-evidence fault isolation

### Phase 3–5
- [x] CV adapter/model metadata
- [x] confidence/severity/risk
- [x] XAI
- [x] officer verification
- [x] report drafting
- [x] corrective actions/limitations
- [x] traceability

### Phase 6–7
- [x] Redis queue
- [x] MinIO/S3
- [x] retries
- [x] worker
- [x] jobs API
- [x] idempotency/checksums
- [x] Docker/compose
- [x] health/metrics
- [x] E2E infrastructure path
- [x] model/dataset readiness infrastructure

## Critical AI product rule
Do not block the application waiting for a perfect violation dataset.

Use:
- direct violation data where genuinely available
- object datasets for perception
- condition datasets for condition analysis
- OCR/date processing for expiry
- spatial/rule reasoning for obstruction/absence cases
- officer verification for uncertain findings

Never relabel generic equipment images as violations.

## Phase 8 — Production perception/model implementation
- [ ] finalize production object classes
- [ ] finalize available trustworthy training data
- [ ] scene-level leakage prevention
- [ ] training pipeline
- [ ] validation/test split
- [ ] checkpoint management
- [ ] model metadata/versioning
- [ ] inference benchmarking
- [ ] calibration if justified
- [ ] CPU/GPU execution
- [ ] failure-safe inference

## Phase 9 — OCR/inspection intelligence
- [ ] extinguisher crop
- [ ] OCR
- [ ] date extraction
- [ ] date normalization
- [ ] expiry calculation
- [ ] OCR confidence
- [ ] officer verification
- [ ] evidence traceability

## Phase 10 — Reasoning/findings
Examples:
- extinguisher + obstacle + spatial relationship → possible blocked extinguisher
- exit + obstruction → possible obstructed exit
- electrical panel + exposed wire/open condition → possible electrical hazard
- expected extinguisher location + no detected extinguisher → possible missing extinguisher

## Phase 11 — Production safety
- [ ] no autonomous legal approval/rejection
- [ ] no fabricated regulations/dimensions/pressures/fines/deadlines
- [ ] zero findings ≠ certification
- [ ] finding linked to evidence
- [ ] model version recorded
- [ ] inference status recorded
- [ ] failures recorded
- [ ] test mode clearly marked
- [ ] officer verification where applicable

## Phase 12 — Integration contract
- [ ] Spring Boot job contract
- [ ] inspectionId/jobId/analysisRunId
- [ ] evidence references
- [ ] finding/report references
- [ ] tenant context
- [ ] authentication
- [ ] idempotency
- [ ] timeout/retry behavior
- [ ] error mapping

## Completion
- [ ] service builds
- [ ] tests pass
- [ ] AI job verified
- [ ] inference verified
- [ ] XAI verified
- [ ] report drafting verified
- [ ] deployment docs
- [ ] limitations documented
- [ ] final AI handoff report
