# FireSphere.ai — Stage 5.1 Independent Calibration Methodology and Metrics Audit

**Audit Document Identifier:** `AUDIT-INTEG-CV-CALIBRATION-STAGE5.1-v1.0`  
**Audit Date:** October 13, 2026  
**Workspace:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Subsystem:** Computer Vision Perception Adapter (`ai-service`)  
**Audit Target:** Stage 5 Confidence Calibration and Reliability Assessment  
**Primary Auditor Role:** Senior CV & ML Evaluation Auditor  
**Holdout Test Set Protection Status:** `VERIFIED_PRISTINE_AND_SEALED`  
**Safety Invariants Status:** `NOT_APPROVED`, `NOT_CALIBRATED`, production authorization `false`  

---

## 1. Executive Summary

This audit independently reviews the implementation, mathematical validity, data separation, and reporting integrity of the Stage 5 confidence calibration assessment ([`docs/integration/cv_confidence_calibration_assessment.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_confidence_calibration_assessment.md)) prior to authorizing Stage 6 holdout evaluation.

### Key Audit Findings:
1. **Holdout Test Set Isolation: VERIFIED PRISTINE.**
   The candidate test split (`stage3_candidate/yolo/images/test` and `labels/test`, 453 images, 657 annotations) was strictly preserved. It was **not read, loaded, enumerated, visualized, or evaluated** in any capacity. Checkpoint SHA-256 hashes for baseline [`best.pt`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_4/checkpoints/best.pt) and candidate [`model_a_baseline.pt`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage4_controlled_experiments/checkpoints/model_a_baseline.pt) were verified byte-for-byte.
2. **Defect DEF-CAL-01 Confirmed (MCE In-Sample Reporting Error):**
   In [`docs/integration/cv_confidence_calibration_assessment.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_confidence_calibration_assessment.md) (Section 7 Table), Isotonic Regression was reported with an Out-of-Fold ECE of **0.0094** alongside an MCE of **0.0009**. The audit revealed that **0.0009 is the IN-SAMPLE MCE** (evaluated on the identical 3,666 observations used to fit the isotonic mapping). The actual detection-level Out-of-Fold MCE was **0.0626 (6.26%)**, and the image-level grouped Out-of-Fold MCE was **0.0696 (6.96%)**.
3. **Defect DEF-CAL-02 Confirmed (Intra-Image Leakage in Cross-Validation):**
   The 5-fold cross-validation in [`run_stage5_calibration.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/run_stage5_calibration.py) partitioned individual detections rather than grouping by image. Detections from the same image crossed calibration training and validation folds. Re-evaluation under strict **Image-Level Grouped 5-Fold Cross-Validation** yielded:
   - Grouped Out-of-Fold ECE: **0.0094**
   - Grouped Out-of-Fold MCE: **0.0696**
   - Grouped Out-of-Fold Brier Score: **0.1052**
4. **Defect DEF-CAL-03 Confirmed (Rounding Boundary Drift at Operating Threshold 0.20):**
   Evaluating `decode_predictions(conf_threshold=0.20)` directly on model logits yielded **565** detections. Post-hoc thresholding $s \ge 0.20$ on rounded 4-decimal float records from `conf_threshold=0.05` yielded **568** detections (+3 detections). The 3 boundary detections had raw floating-point scores in $[0.19996, 0.19998]$ that were rounded up to $0.2000$.
5. **Threshold Table Arithmetic: 100% VERIFIED.**
   All arithmetic invariants across all 11 operating thresholds ($T \in [0.05, 0.80]$) were independently verified:
   - $\text{TP} + \text{FP} = \text{Predictions}$ holds exactly for all $T$.
   - $\text{TP} + \text{FN} = 1,472$ (Total Ground Truth) holds exactly for all $T$.
   - Precision, Recall, and $F_1$ formulas match the underlying counts.
6. **Rare-Class Vacuous Calibration: CONFIRMED DECEPTIVE.**
   `EXIT_DOOR` (3 GTs) and `OBSTACLE` (9 GTs) had **0 detections** and **0 true positives**. The reported ECE of $0.0000$ for these classes is a vacuous mathematical artifact of empty bins ($|B_m| = 0$), not proof of reliable calibration.
7. **Final Recommendation for Stage 6:** **CONDITIONAL GO**.
   Holdout evaluation is authorized under strict conditions: holdout evaluation must evaluate raw uncalibrated predictions as primary evidence, the production gate must remain closed (`NOT_APPROVED`), and calibration status must remain `NOT_CALIBRATED`.

---

## 2. Code, Files, and Functions Inspected

The audit directly inspected and executed the following active files in the workspace:

| Target File | Inspected Functions / Blocks | Inspection Focus | Audit Finding |
| :--- | :--- | :--- | :--- |
| [`run_stage5_calibration.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/run_stage5_calibration.py) | `extract_validation_detections_and_matches()` | Low-threshold inference, greedy IoU matching, FP/TP labeling | Correct logic; rounding to 4 decimals causes minor $+3$ drift at 0.20 |
| [`run_stage5_calibration.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/run_stage5_calibration.py) | `compute_calibration_metrics()` | 10-bin partitioning, ECE, MCE, Brier, NLL | Mathematically correct implementation of standard ECE/MCE/Brier |
| [`run_stage5_calibration.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/run_stage5_calibration.py) | `fit_isotonic_regression_pava()` | Pool Adjacent Violators Algorithm (PAVA) | Correct, exact monotonic step-function implementation |
| [`run_stage5_calibration.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/run_stage5_calibration.py) | `evaluate_5fold_cross_validation()` | Stratified 5-Fold Cross-Validation | Split was at detection level, allowing intra-image correlation across folds |
| [`run_stage5_calibration.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/run_stage5_calibration.py) | `evaluate_operating_points()` | Threshold sweeps, TP/FP/FN accounting | Arithmetic verified; 100% consistent with detection counts |
| [`calibration_summary.json`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/metrics/calibration_summary.json) | Full document | Serialized numerical values, tables, and dictionary structures | Verified source of the in-sample vs out-of-fold MCE confusion |
| [`cv_confidence_calibration_assessment.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_confidence_calibration_assessment.md) | Sections 1, 4, 6, 7, 8 | Textual claims, reliability tables, per-class summaries | Identified reported MCE=0.0009 defect and rare-class limitations |
| [`BaselineInspectionDetector`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/baseline_detector.py) | `decode_predictions()` | Confidence composition: $\sigma(\text{obj}) \times \text{Softmax}(\text{cls})$ | Confirmed composite score structure causing downward compression |
| [`nms.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/nms.py) | `apply_non_maximum_suppression()` | Class-isolated IoU suppression | Confirmed NMS preserves higher-confidence predictions strictly |

---

## 3. Calibration Data Separation Audit

### 3.1. What Examples Were Used to Fit Each Calibrator?
1. **Full-Validation Fitting (`main()` lines 837–840):**
   - Temperature Scaling ($T = 1.1132$) was fitted on all **3,666 validation detections**.
   - Isotonic Regression (PAVA blocks) was fitted on all **3,666 validation detections**.
   - These parameters were stored under `temperature_scaled_metrics` and `isotonic_regression_metrics` in [`calibration_summary.json`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/metrics/calibration_summary.json).
2. **Cross-Validation Fitting (`evaluate_5fold_cross_validation()` lines 480–546):**
   - Detections were split into 5 folds ($k=5$). In each fold, calibrators were fitted on $80\%$ of detections ($2,932$ detections) and scored on the remaining $20\%$ ($734$ detections).
   - This generated genuinely held-out predictions for each fold, aggregated under `cross_validation_out_of_fold`.

### 3.2. Evaluation Separation & In-Sample vs. Out-of-Fold Classification

| Calibrator & Evaluation Mode | Fitting Set | Scoring Set | Genuinely Out-of-Fold? | ECE | MCE | Brier Score | Audit Classification |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **Isotonic PAVA (In-Sample)** | All 3,666 val detections | All 3,666 val detections | **NO** | 0.0006 | **0.0009** | 0.1026 | **IN-SAMPLE (OVERFIT)** |
| **Isotonic PAVA (Det-Level OOF)** | 80% val detections | 20% val detections (x5) | **PARTIALLY\*** | **0.0094** | **0.0626** | 0.1053 | **DETECTION-LEVEL OOF** |
| **Isotonic PAVA (Image-Level OOF)** | 80% val images | 20% val images (x5) | **YES** | **0.0094** | **0.0696** | 0.1052 | **IMAGE-LEVEL GROUPED OOF** |
| **Platt Scaling (Det-Level OOF)** | 80% val detections | 20% val detections (x5) | **PARTIALLY\*** | 0.0106 | 0.0820 | 0.1043 | **DETECTION-LEVEL OOF** |
| **Temperature Scaling (OOF)** | 80% val detections | 20% val detections (x5) | **PARTIALLY\*** | 0.0672 | 0.3663 | 0.1171 | **DETECTION-LEVEL OOF** |

*\*Note on "Partially Out-of-Fold":* In detection-level splitting, detections from the same image appear in both training and validation folds. Detections from the same image share lighting, perspective, and background context, allowing intra-image correlation to bleed into validation folds.

### 3.3. Confirmed Reporting Discrepancy (DEF-CAL-01)
In [`docs/integration/cv_confidence_calibration_assessment.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_confidence_calibration_assessment.md) (Section 7), the summary table reported:
- Isotonic Regression Out-of-Fold ECE: `0.0094` (Correct, from detection-level OOF)
- Isotonic Regression Out-of-Fold MCE: `0.0009` (**INCORRECT: In-Sample MCE mistakenly copied**)
- Actual Out-of-Fold MCE: **`0.0626`** (Detection-Level) / **`0.0696`** (Image-Level Grouped).

**Conclusion:** The claim that Isotonic Regression achieved an out-of-fold MCE of $0.0009$ is **REFUTED**. The true out-of-fold MCE is between $0.0626$ and $0.0696$.

---

## 4. Verification of Metric Implementations

The audit verified the mathematical implementations against standard definitions:

### 4.1. Expected Calibration Error (ECE) & Maximum Calibration Error (MCE)
- **Implementation:**
  $$\text{ECE} = \sum_{m=1}^{10} \frac{N_m}{N} |\text{acc}(B_m) - \text{conf}(B_m)|, \quad \text{MCE} = \max_{m: N_m > 0} |\text{acc}(B_m) - \text{conf}(B_m)|$$
- **Bin Boundaries:** Equal-width partition $[0.0, 0.1), [0.1, 0.2), \dots, [0.8, 0.9), [0.9, 1.0]$. The boundary at $1.0$ is closed.
- **Treatment of Empty Bins:** If $N_m = 0$, the gap is set to $0.0$ and excluded from MCE. This prevents division-by-zero errors.
- **Audit Verification:** **VERIFIED CORRECT**.

### 4.2. Brier Score
- **Implementation:**
  $$\text{Brier} = \frac{1}{N} \sum_{i=1}^N (s_i - y_i)^2$$
- **Audit Verification:** **VERIFIED CORRECT**. Strictly proper scoring rule computed on all detections.

### 4.3. Negative Log-Likelihood (NLL)
- **Implementation:**
  $$\text{NLL} = -\frac{1}{N} \sum_{i=1}^N \left[ y_i \ln(s_i + 10^{-12}) + (1 - y_i) \ln(1 - s_i + 10^{-12}) \right]$$
- **Clamping:** Probabilities are clamped to $[10^{-12}, 1 - 10^{-12}]$ to prevent $-\infty$.
- **Audit Verification:** **VERIFIED CORRECT**.

### 4.4. Binary Ground Truth Assignment & IoU Matching
- Bounding boxes are matched greedily per class, sorted descending by confidence.
- IoU threshold for True Positive assignment is $0.50$.
- Each ground truth object is matched at most once.
- Sub-threshold detections or duplicate detections are labeled False Positives ($y_i = 0$).
- **Audit Verification:** **VERIFIED CORRECT**. Matches standard Pascal VOC / COCO evaluation protocols.

---

## 5. Threshold Analysis Arithmetic Verification

The audit recalculated the full operating threshold sweep using raw validation detections stored in [`validation_detections_raw.json`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/metrics/validation_detections_raw.json):

| Operating Threshold ($T$) | Total Predictions ($N$) | True Positives (TP) | False Positives (FP) | False Negatives (FN) | Arithmetic Invariant 1 ($\text{TP} + \text{FP} = N$) | Arithmetic Invariant 2 ($\text{TP} + \text{FN} = 1,472$) | Precision ($\frac{\text{TP}}{N}$) | Recall ($\frac{\text{TP}}{1,472}$) | $F_1$ Score ($\frac{2PR}{P+R}$) |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **0.05** | 3,666 | 689 | 2,977 | 783 | **VERIFIED (True)** | **VERIFIED (True)** | 0.1879 | 0.4681 | 0.2682 |
| **0.10** | 1,416 | 541 | 875 | 931 | **VERIFIED (True)** | **VERIFIED (True)** | 0.3821 | 0.3675 | 0.3747 |
| **0.15** | 849 | 431 | 418 | 1,041 | **VERIFIED (True)** | **VERIFIED (True)** | 0.5077 | 0.2928 | 0.3714 |
| **0.20** | **568\*** | **362\*** | **206\*** | **1,110** | **VERIFIED (True)** | **VERIFIED (True)** | 0.6373 | 0.2459 | 0.3549 |
| **0.25** | 420 | 309 | 111 | 1,163 | **VERIFIED (True)** | **VERIFIED (True)** | 0.7357 | 0.2099 | 0.3266 |
| **0.30** | 332 | 260 | 72 | 1,212 | **VERIFIED (True)** | **VERIFIED (True)** | 0.7831 | 0.1766 | 0.2882 |
| **0.40** | 190 | 164 | 26 | 1,308 | **VERIFIED (True)** | **VERIFIED (True)** | 0.8632 | 0.1114 | 0.1974 |
| **0.50** | 104 | 97 | 7 | 1,375 | **VERIFIED (True)** | **VERIFIED (True)** | 0.9327 | 0.0659 | 0.1231 |
| **0.60** | 57 | 56 | 1 | 1,416 | **VERIFIED (True)** | **VERIFIED (True)** | 0.9825 | 0.0380 | 0.0733 |
| **0.70** | 9 | 8 | 1 | 1,464 | **VERIFIED (True)** | **VERIFIED (True)** | 0.8889 | 0.0054 | 0.0108 |
| **0.80** | 0 | 0 | 0 | 1,472 | **VERIFIED (True)** | **VERIFIED (True)** | 0.0000 | 0.0000 | 0.0000 |

### Arithmetic Audit Verdict:
- $\text{TP} + \text{FP} = N$ holds across 100% of rows.
- $\text{TP} + \text{FN} = 1,472$ holds across 100% of rows.
- Formulations of Precision, Recall, and $F_1$ are verified without arithmetic defects.
- **Float Rounding Discrepancy (DEF-CAL-03):** Direct evaluation in `decode_predictions(conf_threshold=0.20)` produced **565** detections (361 TP, 204 FP). Filtering rounded records ($s \ge 0.20$) produced **568** detections (362 TP, 206 FP). The 3 additional detections had unrounded raw scores of $0.19996$, $0.19998$, and $0.19998$, which were rounded to $0.2000$.

---

## 6. Rare-Class Limitations & Scope Audit

### 6.1. Ground Truth Imbalance in Validation Split
The candidate validation split contains 1,472 ground truth objects:
- `FIRE_EXTINGUISHER`: 842 objects ($57.2\%$)
- `EXIT_SIGN`: 618 objects ($42.0\%$)
- `EXIT_DOOR`: **3 objects** ($0.2\%$)
- `OBSTACLE`: **9 objects** ($0.6\%$)

### 6.2. Rare-Class Calibration Audit:
- For `EXIT_DOOR`: The model generated **0 detections** at $T \ge 0.05$. Recall was $0.0\%$. ECE evaluated to $0.0000$.
- For `OBSTACLE`: The model generated **0 detections** at $T \ge 0.05$. Recall was $0.0\%$. ECE evaluated to $0.0000$.
- **Audit Verdict:** The reported ECE of $0.0000$ is **purely vacuous**. ECE measures $| \text{acc}(B_m) - \text{conf}(B_m) |$ summed over populated bins. When all bins are empty ($N_m = 0$), ECE evaluates to $0.0$.
- In safety-critical ML engineering, reporting a model as "calibrated" when it is 100% blind to safety assets is dangerous. The validation dataset is **statistically insufficient** to establish calibration for doors and obstacles.

---

## 7. Confirmed Defects and Unverified Claims

| Defect ID | Severity | Description | Audit Impact | Correction |
| :--- | :---: | :--- | :--- | :--- |
| **DEF-CAL-01** | **Medium** | Reported Isotonic MCE of 0.0009 was in-sample, not out-of-fold. | Overstates the calibration tightness of isotonic regression on unseen data. | Out-of-fold MCE corrected to **0.0626** (Det-Level) and **0.0696** (Image-Level). |
| **DEF-CAL-02** | **Low** | 5-Fold CV in Stage 5 split individual detections rather than grouping by image. | Permitted intra-image correlation across folds. | Re-evaluated under Image-Level Grouped 5-Fold CV. True grouped OOF ECE is **0.0094**, MCE is **0.0696**. |
| **DEF-CAL-03** | **Low** | Rounding raw confidence to 4 decimal places before thresholding created +3 detections at 0.20. | Minor numerical discrepancy between Stage 4 (565 dets) and Stage 5 (568 dets). | Traced to float boundary $[0.19996, 0.19998]$. Negligible impact on precision ($63.89\%$ vs $63.73\%$). |
| **CLAIM-01** | **High** | Claiming ECE $\le 0.10$ proves production calibration readiness. | Misleading in safety-critical systems where minority classes have 0% recall. | Unverified for production. Global ECE is swamped by low-confidence negative proposals. |

---

## 8. Stage 6 Recommendation & Safety Stop Point

### Recommendation: **CONDITIONAL GO**

Stage 6 (Independent Holdout Evaluation on the sealed candidate test split) is recommended under the following **strict conditions**:

1. **Condition 1 (Primary Evaluation Truth):**
   Holdout evaluation must evaluate raw, uncalibrated detector outputs as primary evidence. Calibrated scores may be analyzed secondarily for comparison, but must not be treated as certified production probabilities.
2. **Condition 2 (Per-Class Safety Accounting):**
   Holdout evaluation must explicitly evaluate and report per-class recall for `EXIT_DOOR` and `OBSTACLE` without obscuring them behind macro or global averages.
3. **Condition 3 (Single-Pass Holdout Integrity):**
   The sealed test split (`stage3_candidate/yolo/images/test`, 453 images) must be evaluated exactly once. No thresholds, post-hoc mappings, or model parameters may be tuned based on observed holdout performance.
4. **Condition 4 (Gate Invariant Preservation):**
   CV approval gate remains **`NOT_APPROVED`**. Calibration state remains **`NOT_CALIBRATED`**. Production inference authorization remains **`false`**.

```
================================================================================
EXPLICIT STOP POINT REACHED: STAGE 5.1 AUDIT COMPLETE
================================================================================
The Stage 5 calibration assessment has been independently audited and verified.
Defects DEF-CAL-01, DEF-CAL-02, and DEF-CAL-03 are formally documented.
The sealed holdout test split remains completely untouched and pristine.

Awaiting user review and authorization before proceeding to STAGE 6.
================================================================================
```
