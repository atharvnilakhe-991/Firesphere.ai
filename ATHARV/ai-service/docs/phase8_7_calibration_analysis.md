# FireSphere.ai — Phase 8.7: Confidence Calibration & Small-Object Diagnostic Report

**Document ID:** `FS-AI-DOC-8.7-CALIBRATION`  
**Phase:** Phase 8.7 — Targeted Minority-Class Dataset Expansion & Confidence Calibration  
**Classification:** `TECHNICAL_ANALYSIS`  
**Dataset Split Evaluated:** `VALIDATION SPLIT STRICTLY` (988 images, 498 ground-truth exit signs)  
**Model Evaluated:** `firesphere-perception-baseline-v0.8.0` (Checkpoint SHA256: `5f166c4703...`)  
**Date:** 2026-10-08  
**Author:** Antigravity AI/ML Engineering Agent (Pair Programming)  

---

## 1. Executive Summary & Diagnostic Motivation

Phase 8.4 and Phase 8.5 reported paradoxically poor test recall for `EXIT_SIGN` (1.87% in Phase 8.4 and 0.0% in Phase 8.5) despite having an abundant training volume of 2,559 annotations in Phase 8.3.

Phase 8.6 diagnosed that the detector **did learn** visual features for illuminated exit signs, but raw sigmoid confidence scores clustered naturally in the low range $[0.06, 0.12]$. Because the baseline evaluation system applied a monolithic post-processing confidence cutoff of $\tau = 0.15$, **over 91% of valid exit sign detections were truncated** prior to non-maximum suppression (NMS).

Phase 8.7 executed a fine-grained confidence calibration sweep across 14 thresholds strictly on the **988 validation images** to establish an evidence-based candidate operating point without touching the held-out test split.

---

## 2. Size Stratification of Validation Exit Signs

Bounding box dimensions for all 498 ground-truth `EXIT_SIGN` instances in the validation split were analyzed:

| Size Strata | Area Threshold ($\text{Normalized } w \times h$) | Pixel Equivalent ($256 \times 256$ Canvas) | Count | Percentage |
| :--- | :---: | :---: | :---: | :---: |
| **Small** | $\text{Area} < 0.01$ | $< 25.6 \times 25.6 \text{ px}$ | **160** | **32.13%** |
| **Medium** | $0.01 \le \text{Area} < 0.05$ | $25.6 \times 25.6 \text{ to } 57.2 \times 57.2 \text{ px}$ | **85** | **17.07%** |
| **Large** | $\text{Area} \ge 0.05$ | $\ge 57.2 \times 57.2 \text{ px}$ | **253** | **50.80%** |
| **Total** | — | — | **498** | **100.0%** |

### Key Architectural Finding: Grid Quantization Limit
In the baseline detector, predictions are generated on a single-scale feature map of size $16 \times 16$ (downsampled from $256 \times 256$ with stride $S = 16$).
* Each grid cell spans $16 \times 16$ pixels (normalized width/height $0.0625$, normalized area $\approx 0.0039$).
* For **Small Signs** ($\text{area} < 0.01$), the object occupies 1 to 2 grid cells.
* Under coarse stride-16 quantization, bounding box regression predictions suffer from sub-pixel alignment errors, resulting in predicted IoU with ground truth clustering between $0.25$ and $0.45$.
* Because standard evaluation requires $\text{IoU} \ge 0.50$, small signs yielded **0.0% True Positives** at all tested confidence thresholds. Detections were concentrated entirely on **Medium and Large Signs**.

---

## 3. Fine-Grained Validation Threshold Sweep Results

The 988 validation images were evaluated across 14 confidence thresholds $\tau \in [0.05, 0.30]$:

| Conf Threshold $\tau$ | Total Detections | Detections / Image | Overall Prec | Overall Rec | All F1 | Exit Sign Recall | Exit Sign Prec | Exit Sign F1 | Small Sign Recall | Large Sign Recall |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **0.05** | 5,293 | 5.36 | 0.146 | **0.530** | 0.229 | **0.227** | 0.147 | 0.178 | 0.000 | **0.403** |
| **0.06** | 4,184 | 4.23 | 0.177 | 0.509 | 0.263 | **0.195** | 0.180 | **0.187** | 0.000 | 0.356 |
| **0.07** | 3,482 | 3.52 | 0.205 | 0.491 | 0.289 | **0.177** | 0.218 | **0.195** | 0.000 | 0.320 |
| **0.08** | 2,938 | 2.97 | 0.234 | 0.473 | 0.313 | **0.143** | **0.238** | **0.178** | 0.000 | 0.257 |
| **0.09** | 2,518 | 2.55 | 0.262 | 0.454 | 0.332 | 0.117 | 0.250 | 0.159 | 0.000 | 0.209 |
| **0.10** | 2,158 | 2.18 | 0.292 | 0.434 | 0.349 | 0.084 | 0.255 | 0.127 | 0.000 | 0.150 |
| **0.12** | 1,660 | 1.68 | 0.344 | 0.392 | 0.366 | 0.042 | 0.292 | 0.074 | 0.000 | 0.075 |
| **0.14** | 1,316 | 1.33 | 0.391 | 0.354 | 0.372 | 0.016 | 0.242 | 0.030 | 0.000 | 0.032 |
| **0.15** *(Baseline)* | 1,192 | 1.21 | 0.415 | 0.340 | 0.374 | **0.010** | 0.217 | 0.019 | 0.000 | 0.020 |
| **0.16** | 1,098 | 1.11 | 0.433 | 0.327 | 0.373 | 0.006 | 0.158 | 0.012 | 0.000 | 0.012 |
| **0.18** | 926 | 0.94 | 0.479 | 0.305 | 0.373 | 0.006 | 0.300 | 0.012 | 0.000 | 0.012 |
| **0.20** | 808 | 0.82 | 0.521 | 0.289 | 0.372 | 0.002 | 0.143 | 0.004 | 0.000 | 0.004 |
| **0.25** | 605 | 0.61 | 0.585 | 0.243 | 0.343 | 0.002 | 0.500 | 0.004 | 0.000 | 0.004 |
| **0.30** | 429 | 0.43 | 0.634 | 0.187 | 0.289 | 0.002 | 1.000 | 0.004 | 0.000 | 0.004 |

---

## 4. Operating Point Trade-Off Analysis

### 4.1 Failure of the Monolithic Threshold ($\tau = 0.15$)
At the default $\tau = 0.15$, the model produces 1,192 total detections (1.21 per image). However:
- Exit sign recall is an abysmal **1.0%** (only 5 true positives out of 498 ground-truth signs).
- The monolithic threshold was calibrated primarily for the dominant class (`FIRE_EXTINGUISHER`), which achieves 62% recall at $\tau = 0.15$. Applying the same cutoff to exit signs annihilates sign detection.

### 4.2 The Ultra-Low Regime ($\tau = 0.05$)
At $\tau = 0.05$, exit sign recall surges to **22.7%** (113 true positives).
- However, total false positives across all classes explode to **4,522 FP** (5.36 detections per image).
- Applying $\tau = 0.05$ across the entire model destroys precision ($0.146$) and generates unusable clutter for inspecting officers.

### 4.3 The Optimal Candidate Operating Point ($\tau_{\text{sign}} \approx 0.07 - 0.08$)
At $\tau = 0.08$:
- Exit sign recall reaches **14.3%** (71 true positives) with **23.8% precision** and **F1 = 0.178** (a **+14.3x recall improvement** over the baseline $\tau = 0.15$).
- On Large Signs ($\text{area} \ge 0.05$), recall reaches **25.7%**.
- Detections per image remain bounded at 2.97.

---

## 5. Candidate Calibration Recommendation

1. **Decouple Post-Processing Confidence Thresholds by Class:**
   Rather than applying a single monolithic threshold across all 4 perception classes, the inference pipeline should support **per-class operating thresholds**:
   $$\tau = \{\tau_{\text{extinguisher}}: 0.18, \quad \tau_{\text{sign}}: 0.08, \quad \tau_{\text{door}}: 0.15, \quad \tau_{\text{obstacle}}: 0.15\}$$
2. **Status Declaration:**
   This configuration is designated as a **CANDIDATE CALIBRATION** for diagnostic and post-processing evaluation in Phase 8.8. It is **NOT** a hardcoded production override.
3. **Architectural Implication for Future Phases:**
   Because 32.1% of exit signs are small ($\text{area} < 0.01$) and cannot achieve $\text{IoU} \ge 0.5$ on a stride-16 grid, future architectural work must incorporate **multi-scale feature pyramid heads (P3 stride-8)** with dense anchor coverage or pre-trained multi-scale backbones to resolve small sign geometry.
