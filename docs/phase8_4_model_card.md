# FireSphere.ai — Model Card: `firesphere-perception-baseline-v0.8.0`

**Model Version:** `0.8.0-baseline`  
**Model Date:** `2026-10-08`  
**Model Type:** Multi-Class 2D Object Detection ConvNet (`BaselineInspectionDetector`)  
**License:** Internal FireSphere Production Baseline (CC BY 4.0 compliant dataset lineage)  
**Governance State:** **TRAINED_BASELINE**  
**Production Approval:** **NOT_APPROVED**  
**Calibration Status:** **NOT_CALIBRATED**  
**Recommendation:** **BASELINE_TRAINED_DATA_EXPANSION_REQUIRED**

---

## 1. Intended Use

### Primary Intended Use
- Stage-1 physical equipment localization in commercial fire-safety inspection photographs.
- Detects bounding boxes for portable fire extinguishers, exit signs, exit doors, and obstructions.
- Supplies raw bounding-box detections to the Stage-2 Spatial Reasoning Engine.
- AI findings draft candidate inspection items for **mandatory inspecting officer verification**.

### Prohibited & Out-of-Scope Uses
- **Autonomous Fire NOC Decisions:** The model MUST NOT autonomously approve or reject fire permits or NOC applications.
- **Legal Compliance Certification:** Raw model predictions are visual detections, not proof of statutory or NFPA code compliance.
- **Direct Violation Perception:** The model MUST NOT be deployed as an end-to-end classifier for `FIRE_EXTINGUISHER_BLOCKED` or `FIRE_EXTINGUISHER_EXPIRED`.

---

## 2. Model Architecture & Parameters

- **Architecture:** `BaselineInspectionDetector` (4-stage CNN with Residual blocks + 3-anchor spatial grid head).
- **Parameters:** ~1.2 Million parameters.
- **Input:** $256 \times 256 \times 3$ RGB, normalized to $[0.0, 1.0]$.
- **Execution Target:** CPU / CUDA.
- **Artifact SHA-256 (`best.pt`):** `5f166c4703e08f2379524fa9cb077d7f267a54a01c45c117b36f1c48ea92a83f`

---

## 3. Perception Classes

| Class ID | Class Name | Training Dataset Status | Baseline Capability |
| :---: | :--- | :--- | :--- |
| **0** | `FIRE_EXTINGUISHER` | Abundant (4,564 boxes) | Candidate for Controlled Staging (AP@50 = 22.4%) |
| **1** | `EXIT_DOOR` | Data-Limited (42 boxes) | **Not Ready for Production** (AP@50 = 0.0%) |
| **2** | `EXIT_SIGN` | Abundant (2,559 boxes) | Baseline Established (AP@50 = 0.8%, needs anchor tuning) |
| **3** | `OBSTACLE` | Data-Limited (93 boxes) | **Not Ready for Production** (AP@50 = 0.0%) |

---

## 4. Known Limitations & Failure Modes

1. **Severe Imbalance on Exit Doors and Obstacles:** The model cannot reliably detect `EXIT_DOOR` or `OBSTACLE` in general operational settings due to extreme dataset undersampling.
2. **Small-Scale Sensitivity:** High false negative rate on small or distant illuminated exit signage.
3. **Uncalibrated Confidences:** Confidence scores represent raw heuristic neural probabilities, not true Bayesian compliance probabilities (`NOT_CALIBRATED`).

---

## 5. Ethical & Regulatory Considerations

- **Human-in-the-Loop Integrity:** All visual detections are reviewed by certified municipal fire marshals. AI serves purely as an assistive inspection co-pilot.
- **Truth in AI:** Zero synthetic balance, zero fabricated labels, and zero hallucinated compliance rules exist within this model.
