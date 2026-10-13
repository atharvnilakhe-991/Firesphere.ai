# FireSphere.ai — Model Card: `firesphere-perception-improved-v0.8.5`

**Model Identifier:** `firesphere-perception-improved-v0.8.5`  
**Model Version:** `0.8.5-improved`  
**Model Date:** `2026-10-08`  
**Model Architecture:** Multi-Scale Feature Pyramid Inspection Detector (`MultiScaleInspectionDetector`)  
**Backbone:** 4-Stage Residual ConvNet + FPN Neck (Lateral $1\times 1$ convs + Nearest Upsample + Smooth convs)  
**Detection Heads:** 3 Pyramid Scales ($P_3: 32\times 32$, $P_4: 16\times 16$, $P_5: 8\times 8$)  
**License:** Internal FireSphere Experimental (CC BY 4.0 compliant dataset lineage)  
**Governance State:** **TRAINED_EXPERIMENT**  
**Production Approval:** **NOT_APPROVED**  
**Calibration Status:** **NOT_CALIBRATED**  
**Recommendation:** **ARCHITECTURE_IMPROVED_BUT_DATA_LIMITED**  

---

## 1. Intended Use

### 1.1 Primary Intended Use
- Stage-1 multi-scale physical equipment and hazard candidate localization in commercial fire-safety inspection imagery.
- Predicts axis-aligned bounding boxes and confidence scores across 4 frozen perception classes:
  - `0: FIRE_EXTINGUISHER`
  - `1: EXIT_DOOR`
  - `2: EXIT_SIGN`
  - `3: OBSTACLE`
- Provides raw detection inputs to the downstream Stage-2 Spatial Reasoning Engine (`SpatialReasoningEngine`) and XAI attribution pipelines.
- Drafts candidate inspection findings exclusively for **mandatory verification by certified fire safety officers**.

### 1.2 Prohibited & Out-of-Scope Uses
- **Autonomous Permit Approval/Rejection:** The model MUST NOT autonomously issue, approve, reject, or revoke Fire NOC permits.
- **Direct Statutory Compliance Classification:** Never deploy as an end-to-end classifier for legal compliance states (`AI_APPROVED`, `AI_REJECTED`).
- **Autonomous Clearance Calculation:** Raw bounding boxes do not constitute certified dimensional or NFPA statutory clearance measurements.
- **End-to-End Violation Classification:** The model does NOT predict `EMERGENCY_EXIT_OBSTRUCTED` directly; obstruction remains a spatial reasoning finding verified by humans.

---

## 2. Model Architecture & Parameters

- **Architecture Class:** `MultiScaleInspectionDetector` (Native PyTorch)
- **Parameters:** ~3.4 Million trainable parameters
- **Input Dimensions:** $256 \times 256 \times 3$ RGB (normalized to $[0.0, 1.0]$)
- **Feature Pyramid:**
  - $P_3$: $32 \times 32 \times 96$ (Stride 8) — small objects (anchors $[0.03, 0.03], [0.06, 0.06], [0.08, 0.12]$)
  - $P_4$: $16 \times 16 \times 96$ (Stride 16) — medium objects (anchors $[0.12, 0.18], [0.20, 0.35], [0.32, 0.45]$)
  - $P_5$: $8 \times 8 \times 96$ (Stride 32) — large objects (anchors $[0.35, 0.70], [0.55, 0.85], [0.80, 0.90]$)
- **Target Execution Device:** CPU / Edge / CUDA
- **Target Framework:** PyTorch Native (no external framework dependency)

---

## 3. Perception Classes & Capability Status

| Class ID | Class Name | Dataset Status | Experimental Finding | Operational Readiness |
| :---: | :--- | :--- | :--- | :--- |
| **0** | `FIRE_EXTINGUISHER` | Abundant (4,564 boxes) | Stable localization across scales | Candidate for staging under human supervision |
| **1** | `EXIT_DOOR` | Data-Limited (42 boxes, 14 scenes) | Structural receptive field improved, but acute scene starvation | **NOT_APPROVED** — Data expansion required |
| **2** | `EXIT_SIGN` | Abundant (2,559 boxes) | Resolution bottleneck resolved via $P_3$ high-resolution head | Baseline improved; small signage supported |
| **3** | `OBSTACLE` | Data-Limited (93 boxes, 30 scenes) | Extreme open-vocabulary variance + data starvation | **NOT_APPROVED** — Data expansion required |

---

## 4. Known Limitations & Failure Modes

1. **Extreme Data Starvation on Doors and Obstacles:** Due to strictly preserving the Phase 8.3 dataset without synthetic fabrication, `EXIT_DOOR` (42 boxes) and `OBSTACLE` (93 boxes) remain unreliable for zero-shot generalization.
2. **Extreme Lighting & Visual Occlusion:** Severely shadowed, unlit, or glare-washed exit signage may produce lower confidence scores.
3. **Uncalibrated Confidence Probabilities:** Model output scores are uncalibrated neural sigmoid outputs (`NOT_CALIBRATED`). They must not be interpreted as Bayesian probabilities of regulatory compliance.

---

## 5. Governance, Ethics & Safety Safeguards

- **Human-in-the-Loop Safeguard:** Model outputs are assistive only. All findings draft potential findings subject to final determination by a human fire officer.
- **Fail-Safe Inference:** Invalid images, corrupted bytes, or blank inputs trigger safe degradation (empty detections, status log) without system crashing or false "clear" assertions.
- **Strict Data Lineage:** Trained strictly on CC BY 4.0 compliant, audit-verified data with zero data leakage into held-out evaluation splits.
