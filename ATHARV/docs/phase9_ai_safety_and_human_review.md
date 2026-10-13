# FireSphere.ai — Phase 9 AI Safety, Human-in-the-Loop Governance & Officer Review

**Document Version:** `1.0.0-phase9`  
**Governing Phase:** Phase 9 — Final AI Model Selection, Calibration, Evaluation & Validation  
**Author:** AI/ML Engineering Agent  
**Core Product Principle:** Human-in-the-Loop Decision Authority  
**Date:** 2026-10-09  

---

## 1. Core Product & Safety Philosophy

FireSphere.ai is a public safety platform built for citizens, property owners, and certified Fire Officers. Under Indian Fire Service regulations and international safety jurisprudence:
- **AI MUST REMAIN STRICTLY ADVISORY:** The AI service processes computer vision evidence to assist, accelerate, and organize inspection reviews.
- **AI NEVER MAKES STATUTORY DECISIONS:** The AI model lacks statutory authority, legal standing, and sensory completeness. Only a sworn, authorized human Fire Officer can approve, reject, or issue citations for a Fire No-Objection Certificate (NOC).

---

## 2. Inviolable Governance Invariants

```
                                  [ INSPECTION EVIDENCE ]
                                    (Citizen / Officer)
                                             │
                                             ▼
                             ┌───────────────────────────────┐
                             │       AI SERVICE PIPELINE     │
                             │   - Computer Vision Detection │
                             │   - Advisory Finding Mapping  │
                             │   - Explainable Diagnostics   │
                             └───────────────┬───────────────┘
                                             │
                                             ▼
                             ┌───────────────────────────────┐
                             │       ADVISORY FINDINGS       │
                             │  Status: REQUIRES_OFFICER_    │
                             │          VERIFICATION         │
                             └───────────────┬───────────────┘
                                             │
                                             ▼
                             ┌───────────────────────────────┐
                             │    AUTHORIZED FIRE OFFICER    │
                             │  - Reviews visual evidence    │
                             │  - Confirms or dismisses cues │
                             │  - Performs on-site checks    │
                             └───────────────┬───────────────┘
                                             │
                      ┌──────────────────────┴──────────────────────┐
                      ▼                                             ▼
             [ LEGAL NOC APPROVAL ]                        [ OFFICIAL CITATION ]
```

### Invariant 1: Prohibited Autonomous Decision States
The platform explicitly forbids autonomous certification states in all code, schemas, and API responses:
- `AI_APPROVED` — **STRICTLY PROHIBITED**
- `AI_REJECTED` — **STRICTLY PROHIBITED**
- `APPROVED` / `REJECTED` by automated algorithm — **STRICTLY PROHIBITED**

Any attempt to serialize these states raises an immediate schema validation exception via [`validate_compliance_boundary`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/schemas/finding.py#L130-L141).

### Invariant 2: Absence of Evidence $\neq$ Evidence of Compliance
A core failure mode of naive AI inspection systems is equating "zero detections" with "zero defects."
- **Inviolable Rule:** If the AI model detects zero objects in an evidence photograph, the inspection status remains `NOT_ASSESSED` or `REQUIRES_OFFICER_VERIFICATION`.
- Zero detections must **NEVER** automatically approve an inspection or grant a Fire NOC. A blank wall, an off-angle shot, an empty corridor, or an uninspected electrical room does not constitute fire compliance.

### Invariant 3: Generic Visual Detections $\neq$ Legal Code Violations
- A bounding box identifying an object (e.g. `OBSTACLE` or `FIRE_EXTINGUISHER`) indicates a visual pattern match, **NOT** a confirmed statutory infraction under the National Building Code (NBC) of India, state fire services acts, or municipal fire prevention bylaws.
- The AI service must **NOT** cite universal statutory clearance distances (e.g. "violates NBC Part 4 Section 3.2"), impose financial fines, or declare legal deadlines without explicit, verified jurisdiction-specific legal rules configured by municipal fire authorities.

---

## 3. Human Review Workflow & Evidence Traceability

To support transparent officer oversight, every finding produced by the AI service includes:
1. **Evidence Traceability:** UUID reference linking directly to the original raw photograph in MinIO/S3 object storage.
2. **Spatial Localization:** Normalized bounding box coordinates allowing the officer UI to highlight the exact visual region of interest.
3. **Plain-English Explanations:** Explanations detailing what visual features triggered the detection (e.g. *"Red cylindrical canister detected mounted on wall"*).
4. **Specific Verification Guidance:** Recommended on-site physical inspection actions for the officer (e.g. *"Verify pressure gauge indicates operating pressure in green zone; check hydrostatic test date tag"*).
5. **Categorical Confidence Indicator:** Plain-language rating (`LOW`, `MEDIUM`, `HIGH`) paired with an explicit disclaimer that model confidence is not regulatory certainty.
