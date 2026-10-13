# FireSphere.ai — Phase 8.6 Model Governance & Strategy Decision

**Document Version:** `1.0.0-phase8.6`  
**Governing Phase:** Phase 8.6 Detection Pipeline Validation & Training Objective Diagnosis  
**Author:** AI/ML Engineering Agent  
**Execution Timestamp:** `2026-10-08T17:15:00Z`  
**Formal Decision Gate:** **`PIPELINE_HEALTHY_DATA_EXPANSION_REQUIRED`**  
**Production Approval Status:** **`NOT_APPROVED`**  
**Calibration Status:** **`NOT_CALIBRATED`**  
**Authoritative Baseline Model:** `firesphere-perception-baseline-v0.8.0`  
**Archived Experimental Model:** `firesphere-perception-improved-v0.8.5`  
**Date:** 2026-10-08  

---

## 1. Executive Summary & Formal Decision Gate

In Phase 8.6, FireSphere.ai subjected its visual perception detector pipeline to rigorous diagnostic validation to answer whether prior performance limitations were caused by implementation defects or fundamental dataset characteristics.

### The Formal Gate Verdict:
```
================================================================================
PHASE 8.6 DECISION GATE: PIPELINE_HEALTHY_DATA_EXPANSION_REQUIRED
================================================================================
```

### Core Verification Criteria Satisfied:
1. **Tiny-Set Overfit Memorization (PASS):** The detector achieved **89.5% recall, 94.4% precision, and 95.8% mean IoU** on a deterministic 16-image subset, driving total loss down by **99.85%** ($4.3488 \to 0.00633$).
2. **Target Coordinate Invertibility (PASS):** GT box encoding and decoding round-trip confirmed maximum coordinate error of **$6 \times 10^{-6}$**, demonstrating flawless mathematical precision.
3. **Gradient Dynamics (PASS):** Finite, non-zero, non-NaN gradients across all layers with healthy parameter update norm ($\Delta \theta = 1.495$).
4. **Loss Formulation (PASS):** Loss function is strictly monotone and safely handles zero-target background frames without numerical instability.
5. **Validation Threshold Sweep (INSIGHT):** `EXIT_SIGN` predictions surged to **22.7% recall** at $\tau = 0.05$ (113 true positives), proving that the detector learned signage features, but raw confidences cluster in $[0.05, 0.12]$.
6. **Data Scarcity Confirmed (INSIGHT):** `EXIT_DOOR` and `OBSTACLE` produced zero detections across all thresholds down to $0.05$, confirming that no post-processing or hyperparameter adjustment can overcome an acute lack of real-world training scenes (14 and 30 scenes).

---

## 2. Model Status & Governance Hierarchy

1. **Phase 8.4 Baseline (`firesphere-perception-baseline-v0.8.0`):**
   - **Status:** **`TRAINED_BASELINE`**
   - **Role:** Authoritative control model.
   - **Checkpoint:** Preserved untouched at `ai-service/artifacts/phase8_4/checkpoints/best.pt` (SHA256: `5f166c4703e0...`).
2. **Phase 8.5 Improved (`firesphere-perception-improved-v0.8.5`):**
   - **Status:** **`TRAINED_EXPERIMENT`**
   - **Role:** Documented multi-scale FPN trial that regressed against the baseline due to anchor dilution.
   - **Checkpoint:** Preserved untouched at `ai-service/artifacts/phase8_5/checkpoints/best.pt`.
3. **Phase 8.6 Diagnostic Output:**
   - **Status:** Diagnostic trial only. No new production weights trained.
   - **Production Approval:** **`NOT_APPROVED`**. Under no circumstances is this model deployed to operational environments.

---

## 3. Resolving the Phase 8.4 vs Phase 8.5 Performance Paradox

Phase 8.6 diagnostics have successfully unraveled the performance divergence between Phase 8.4 and Phase 8.5:

1. **Why Phase 8.4 Maintained Higher Baseline Precision:**
   The single $16 \times 16$ grid evaluated only 768 anchors per image. With ~1-3 objects per image, the positive-to-negative ratio was $1:767$. This allowed the classification and objectness heads to maintain tight anchor coupling and produce reasonable extinguisher precision (51.3%).
2. **Why Phase 8.5 FPN Regressed:**
   The 3-scale feature pyramid ($P_3, P_4, P_5$) expanded anchors from $768 \to 4,032$ ($5.25\times$). When trained from scratch without pre-trained weights, the model faced a $1:4,031$ negative anchor imbalance. The massive flood of background gradients suppressed prediction logits, pushing almost all test predictions below the evaluation threshold.
3. **The Small `EXIT_SIGN` Mystery Solved:**
   Sign features were not lost; their raw neural confidence was simply compressed into $[0.05, 0.12]$ due to small pixel footprint. At $\tau = 0.05$, recall jumped by $22.7\times$.

---

## 4. Strategic Directives for Future Phases (Phase 8.7+)

1. **Targeted Physical Dataset Expansion:**
   - Prior to any architectural refactoring, targeted collection of diverse physical scenes containing `EXIT_DOOR` and `OBSTACLE` objects is **strictly required**.
   - Expanding door physical scenes from 14 to $\ge 150$ and obstacle scenes from 30 to $\ge 200$ will establish a viable distribution for generalization.
2. **Class-Specific Calibrated Inference:**
   - Future deployment pipelines must adopt calibrated, per-class confidence thresholds ($\tau_{\text{sign}} \approx 0.08, \tau_{\text{exting}} \approx 0.18$) rather than an arbitrary monolithic cutoff.
3. **Human-in-the-Loop & Safety Non-Negotiables:**
   - Visual object detection remains advisory only.
   - Inspecting fire safety officers retain ultimate authority over permit compliance and violation findings.
   - Zero autonomous `AI_APPROVED` or `AI_REJECTED` states.
