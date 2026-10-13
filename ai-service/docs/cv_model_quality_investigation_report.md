# FireSphere.ai — Computer-Vision Model Quality Investigation and Controlled Improvement Report

**Document ID:** `FS-AI-CV-QUALITY-INVESTIGATION-20261010`  
**Phase:** Phase 4 Follow-Up — Computer-Vision Quality Investigation & Controlled Improvement  
**Author / Role:** Member 4 (Senior Computer-Vision Engineer, ML Evaluation Specialist, AI Safety Reviewer)  
**Target Microservice:** `firesphere-ai-service` (`ai-service`)  
**Workspace:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Date:** 2026-10-10  
**Governance Gate Status:** **`NOT_APPROVED`** | **`NOT_CALIBRATED`**  
**Autonomous Fire NOC Statutory Authority:** **STRICTLY PROHIBITED (ADVISORY FINDINGS ONLY)**  

---

## 1. Executive Summary

This report documents the rigorous read-only audit, full baseline evaluation reproduction, empirical failure-mode investigation, and controlled offline experiment on the computer-vision perception model for **FireSphere.ai**.

All safety requirements have been strictly preserved:
- The reference model checkpoint (`artifacts/phase8_4/checkpoints/best.pt`) was left **100% immutable and intact** (SHA-256 verified).
- No training on, tuning against, or leaking of the held-out test split occurred.
- All experimental investigations were conducted strictly offline on the 988-image validation dataset and saved to a dedicated directory (`artifacts/phase4_cv_experiment/`).
- The governance gate remains **`NOT_APPROVED`** and **`NOT_CALIBRATED`**.
- The full offline test suite of **447 tests passed** with zero regressions.

---

## 2. Checkpoint and Dataset Provenance Verification

### 2.1 Model Checkpoint Integrity
| Attribute | Documented Specification | Verified Local Artifact | Verification Status |
| :--- | :--- | :--- | :---: |
| **Model ID** | `firesphere-perception-baseline-v0.8.0` | `artifacts/phase8_4/metadata/model_metadata.json` | **VERIFIED** |
| **Model Type** | `PYTORCH_BASELINE_DETECTOR` | `app.models.baseline_detector.BaselineInspectionDetector` | **VERIFIED** |
| **Checkpoint Path** | `artifacts/phase8_4/checkpoints/best.pt` | `ai-service/artifacts/phase8_4/checkpoints/best.pt` | **VERIFIED** |
| **Checkpoint Size** | 26,905,612 bytes | 26,905,612 bytes | **VERIFIED** |
| **Parameters** | 2,237,627 float32 weights | Deserialized in PyTorch (`epoch=4`, `is_best=True`) | **VERIFIED** |
| **Cryptographic SHA-256** | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | **EXACT MATCH** |

### 2.2 Dataset Metadata and Split Provenance
- **Dataset Location:** `datasets/fire_safety/prepared/phase8_3/yolo`
- **Specification:** `data.yaml` defining 4 perception classes (`nc: 4`):
  - `0`: `FIRE_EXTINGUISHER`
  - `1`: `EXIT_DOOR`
  - `2`: `EXIT_SIGN`
  - `3`: `OBSTACLE`
- **Corpus Structure and Support Counts (Verified via Disk Enumeration):**

| Dataset Split | Image Count | Label Count | GT Annotations | Class 0 (`EXTINGUISHER`) | Class 1 (`DOOR`) | Class 2 (`SIGN`) | Class 3 (`OBSTACLE`) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Train** | 3,377 | 3,377 | 5,007 | 3,164 (63.2%) | 33 (0.66%) | 1,741 (34.8%) | 69 (1.38%) |
| **Val** | 988 | 988 | 1,455 | 948 (65.2%) | 3 (0.21%) | 498 (34.2%) | 6 (0.41%) |
| **Held-Out Test** | 522 | 522 | 796 | 452 (56.8%) | 6 (0.75%) | 320 (40.2%) | 18 (2.26%) |
| **Total Corpus** | **4,887** | **4,887** | **7,258** | **4,564 (62.9%)** | **42 (0.58%)** | **2,559 (35.3%)** | **93 (1.28%)** |

---

## 3. Baseline Evaluation Reproduction

### 3.1 Exact Protocol Reproduction
The baseline evaluation procedure was executed locally using:
- **Command:** Python runner loading `best.pt`, batch size 32 on CPU, input resolution $256 \times 256$, confidence filter $\tau = 0.20$, and greedy class-wise NMS overlap $\text{IoU} = 0.45$.
- **Evaluation Engine:** `app.evaluation.comprehensive_evaluator.ComprehensiveEvaluator` implementing standard 101-point interpolated Average Precision at $\text{IoU} = 0.50$ (AP@50) and $\text{IoU} \in [0.50:0.05:0.95]$ (AP@50:95).

### 3.2 Verification of Metrics and Resolution of Reported Divergence
The evaluation was reproduced against the 522 test images (796 GT objects):

| Evaluation Metric | Documented `test_metrics.json` | Reproduced Local Execution | Phase 4 Follow-up Report Citation | Status / Root Cause |
| :--- | :---: | :---: | :---: | :--- |
| **Total Test Images** | 522 | **522** | 522 | **EXACT MATCH** |
| **Total Ground Truth** | 796 | **796** | 796 | **EXACT MATCH** |
| **Total Predictions** | 395 | **395** | 469 (P8.9) | **EXACT MATCH** with baseline |
| **Mean Precision** | 0.2283 | **0.2283** | 0.2064 | **EXACT MATCH** with baseline |
| **Mean Recall** | 0.1125 | **0.1125** | 0.1197 | **EXACT MATCH** with baseline |
| **Mean F1-Score** | 0.1261 | **0.1261** | 0.1482 | **EXACT MATCH** with baseline |
| **Macro mAP@50** | **0.0579** | **0.0579** | 0.0503 | **EXACT MATCH** with baseline |
| **Macro mAP@50:95** | **0.0194** | **0.0194** | 0.0195 | **EXACT MATCH** with baseline |
| **FIRE_EXTINGUISHER AP@50** | **0.2236** | **0.2236** | 0.1329 | **EXACT MATCH** with baseline |
| **EXIT_SIGN AP@50** | **0.0079** | **0.0079** | 0.0684 | **EXACT MATCH** with baseline |
| **EXIT_DOOR AP@50** | **0.0000** | **0.0000** | 0.0000 | **EXACT MATCH** (0 TP) |
| **OBSTACLE AP@50** | **0.0000** | **0.0000** | 0.0000 | **EXACT MATCH** (0 TP) |

> [!IMPORTANT]
> **Resolution of Reported Divergence:**  
> The Phase 4 follow-up report cited the metrics of the **Phase 8.9 candidate model** (`firesphere-perception-pretrained-v0.8.9` in `docs/phase8_9_external_test_report.md` with mAP@50 = 0.0503, Fire Extinguisher AP@50 = 0.1329, Exit Sign AP@50 = 0.0684) while referencing the **Phase 8.4 baseline checkpoint** (`best.pt`, SHA-256 `5f166c...`). The local reproduction proves that the baseline checkpoint `best.pt` actually achieves **mAP@50 = 0.0579** (Fire Extinguisher AP@50 = 0.2236, Exit Sign AP@50 = 0.0079). Both models exhibit zero true positives on exit doors and obstacles.

---

## 4. Failure Mode Investigation & Error Analysis

A comprehensive diagnostic investigation across predictions, feature layers, and ground truth bounding boxes was conducted to separate verified root causes from ungrounded hypotheses.

### 4.1 Verified Root Cause 1: Severe Class Scarcity & Logit Suppression (Doors and Obstacles)
- **Verified Finding:** Ground truth support across the entire 4,887-image dataset contains only **42 exit doors** (0.58%) and **93 obstacles** (1.28%).
- **Empirical Mechanism:** Multi-class cross-entropy loss with negative objectness BCE heavily penalized false background activations on scarce classes. Across all 988 validation images (1,455 GT annotations):
  - The maximum confidence ($\text{obj} \times \text{prob}$) output for `EXIT_DOOR` never exceeds **0.0299**.
  - The maximum confidence output for `OBSTACLE` never exceeds **0.0413**.
- **Consequence:** At standard operational thresholds ($\tau = 0.15$ or $0.20$), 100% of candidate proposals for doors and obstacles are eliminated before NMS or evaluation matching can occur, yielding **zero true positives**.
- **Governance Justification:** The suppression of `EXIT_DOOR` and `OBSTACLE` as `UNSUPPORTED` in [`model_approval_gate.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py) is mathematically and empirically validated.

### 4.2 Verified Root Cause 2: Scale Disparity and Feature Stride Collapse (Exit Signs)
- **Verified Finding:** On the test set, 25.9% of exit signs occupy $<1\%$ of image area ($<25.6 \times 25.6$ pixels at $256 \times 256$ resolution), and 32.1% on validation. The median sign area is only 0.0365.
- **Architecture Bottleneck:** `BaselineInspectionDetector` downsamples images through 4 convolutional stages to a single $16 \times 16$ grid (stride 16) without a Feature Pyramid Network (FPN) or multi-scale heads.
- **Consequence:** Small signs spanning $<16$ pixels are blended into background receptive fields. The baseline model produced only 15 candidate sign boxes across 522 test images (6 TP, 9 FP, 314 FN), yielding a recall of only **1.87%**.

### 4.3 Verified Root Cause 3: Aspect Ratio and Anchor Mismatch
- **Verified Finding:**
  - Fire extinguishers have a tall, narrow aspect ratio ($w/h$: median = 0.23, mean = 0.28). Baseline Anchor 1 is $[0.22, 0.35]$ (aspect ratio 0.63), which is over twice as wide as typical extinguishers.
  - Exit signs have wide horizontal placard aspect ratios ($w/h$: mean = 1.32, median = 1.25, max = 6.44). Baseline Anchor 0 is square $[0.08, 0.08]$ (aspect ratio 1.0), failing to tightly frame horizontal signs.
- **Consequence:** Box regression loss struggles on extreme ratios, leading to IoU matching degradation at higher IoU thresholds.

### 4.4 Verified Root Cause 4: High False Positive Rate on Red Visual Confounders
- **Verified Finding:** On `FIRE_EXTINGUISHER`, the baseline generated 185 false positives against 195 true positives on the test split, resulting in a precision of only **51.32%** (falling to **41.94%** under transfer-learning models).
- **Consequence:** Red fire hose reels, red standpipe valves, red emergency push-buttons, and red signage are frequently misidentified as extinguishers, proving that **the model cannot autonomously certify fire extinguisher presence without human officer verification**.

---

## 5. Controlled Experiment: Validation-Set Threshold & Policy Optimization

### 5.1 Experiment Hypothesis & Setup
- **Hypothesis:** Because `FIRE_EXTINGUISHER` has a confidence ceiling of ~0.76 while `EXIT_SIGN` has a ceiling of ~0.33 and `EXIT_DOOR`/`OBSTACLE` are suppressed ($<0.042$), a rigid uniform threshold ($\tau = 0.20$) causes severe recall collapse on exit signs without effectively controlling false alarms on extinguishers. Decoupling class-specific operating thresholds strictly on validation data will improve multi-class utility without leaking test split data.
- **Affected Artifacts:** `artifacts/phase4_cv_experiment/run_experiment.py`, `artifacts/phase4_cv_experiment/experiment_results.json`.
- **Validation Dataset:** 988 images, 1,455 GT annotations (`datasets/fire_safety/prepared/phase8_3/yolo/images/val`).
- **Baseline Preserved:** `artifacts/phase8_4/checkpoints/best.pt` remained completely unmodified.

### 5.2 Experimental Results (Executed Strictly on Validation Split)

#### Sweep A: Uniform Confidence Threshold Sweep (IoU NMS = 0.45)
| Threshold $\tau$ | Macro F1 | `EXTINGUISHER` P | `EXTINGUISHER` R | `EXTINGUISHER` F1 | `EXTINGUISHER` FP | `EXIT_SIGN` P | `EXIT_SIGN` R | `EXIT_SIGN` F1 | `EXIT_SIGN` TP |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **0.05** | 0.1046 | 0.1454 | **0.6941** | 0.2404 | 3,867 | 0.1466 | **0.2269** | 0.1781 | 113 |
| **0.08** | 0.1307 | 0.2335 | 0.6519 | 0.3439 | 2,022 | 0.2383 | 0.1426 | **0.1784** | 71 |
| **0.10** | **0.1318** | 0.2947 | 0.6213 | 0.4005 | 1,404 | 0.2545 | 0.0843 | 0.1267 | 42 |
| **0.12** | 0.1268 | 0.3461 | 0.5802 | 0.4338 | 1,038 | 0.2917 | 0.0422 | 0.0737 | 21 |
| **0.15** | 0.1205 | 0.4192 | 0.5169 | 0.4631 | 679 | 0.2174 | 0.0100 | 0.0192 | 5 |
| **0.18** | 0.1212 | 0.4815 | 0.4652 | 0.4732 | 475 | 0.3000 | 0.0060 | 0.0118 | 3 |
| **0.20 (Base)** | 0.1210 | **0.5243** | 0.4430 | **0.4802** | 381 | 0.1429 | 0.0020 | 0.0039 | 1 |
| **0.25** | 0.1148 | 0.5852 | 0.3724 | 0.4554 | 250 | 0.5000 | 0.0020 | 0.0040 | 1 |
| **0.30** | 0.0995 | **0.6332** | 0.2859 | 0.3939 | **157** | 1.0000 | 0.0020 | 0.0040 | 1 |

*Note: `EXIT_DOOR` and `OBSTACLE` had 0 TP across all thresholds because their maximum validation confidence is $<0.042$.*

#### Sweep B: NMS IoU Threshold Sweep ($\tau = 0.20$)
| NMS IoU | Macro F1 | `EXTINGUISHER` Precision | `EXTINGUISHER` Recall | `EXTINGUISHER` F1 | `EXTINGUISHER` FP |
| :---: | :---: | :---: | :---: | :---: | :---: |
| **0.35** | **0.1246** | **0.5630** | 0.4409 | **0.4947** | **324** |
| **0.40** | 0.1229 | 0.5442 | 0.4420 | 0.4878 | 351 |
| **0.45 (Base)** | 0.1210 | 0.5243 | 0.4430 | 0.4802 | 381 |
| **0.50** | 0.1184 | 0.4965 | 0.4462 | 0.4700 | 430 |
| **0.55** | 0.1159 | 0.4729 | 0.4473 | 0.4598 | 472 |

*Finding: Tightening NMS IoU from 0.45 to 0.35 eliminates 57 duplicate bounding-box false alarms, improving extinguisher precision by +3.87% with negligible recall impact.*

#### Sweep C: Decoupled Class-Specific Operating Policies
| Policy Name | Configuration | Macro F1 | `EXTINGUISHER` F1 | `EXIT_SIGN` F1 | `EXIT_SIGN` TP | `DOOR` TP | `OBSTACLE` TP |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Baseline Uniform** | $\tau = 0.20$ for all classes | 0.1210 | 0.4802 | 0.0039 | 1 | 0 | 0 |
| **Operational Uniform** | $\tau = 0.15$ for all classes | 0.1205 | 0.4631 | 0.0192 | 5 | 0 | 0 |
| **Decoupled Opt-1** | $\tau_{\text{FE}}=0.20, \tau_{\text{ES}}=0.10, \tau_{\text{other}}=0.05$ | 0.1517 | 0.4802 | 0.1267 | 42 | 0 | 0 |
| **Decoupled Opt-2** | $\tau_{\text{FE}}=0.22, \tau_{\text{ES}}=0.08, \tau_{\text{other}}=0.05$ | **0.1609** | 0.4654 | **0.1784** | **71** | 0 | 0 |
| **Decoupled Opt-3** | $\tau_{\text{FE}}=0.18, \tau_{\text{ES}}=0.10, \tau_{\text{other}}=0.05$ | 0.1499 | 0.4732 | 0.1267 | 42 | 0 | 0 |

### 5.3 Key Experimental Takeaways
1. **Decoupled Operating Point:** Applying $\tau_{\text{FE}} = 0.22$ and $\tau_{\text{ES}} = 0.08$ on validation data increases Macro F1 from **0.1210 to 0.1609** (+33.0% relative improvement) and recovers **71 True Positives** on exit signs (versus 1 in baseline), while suppressing extinguisher false alarms.
2. **Door and Obstacle Invariance:** No thresholding policy down to $\tau = 0.05$ can produce a single True Positive for exit doors or obstacles, proving that post-processing cannot overcome structural training data deficits.
3. **No Test Set Contamination:** This policy was discovered and evaluated strictly on the validation set, preserving the test split as an untouched final benchmark.

---

## 6. Regression and Safety Testing Verification

Following execution of the controlled experiment, the full test suite was executed offline:
- **Command:** `.\.venv\Scripts\pytest.exe -q`
- **Result:** **447 passed, 0 failed, 0 skipped, 11 warnings in 12.79s** (100% pass rate).

### Safety Invariants Audit:
1. **Model Approval Gate:** [`model_approval_gate.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py) enforces `ModelApprovalStatus.NOT_APPROVED`. Verified passing in `test_phase10_model_approval_status_is_not_approved`.
2. **Calibration Gate:** Status strictly preserved as `NOT_CALIBRATED`. Verified passing in `test_phase9_calibration_status_and_threshold_audit`.
3. **Capability Gating:** `EXIT_DOOR` and `OBSTACLE` remain `UNSUPPORTED` and cannot generate actionable statutory findings. Verified passing in `test_phase10_unsupported_classes_gated_from_actionable_findings`.
4. **Advisory Authority:** Model predictions are strictly non-binding suggestions (`AI_SUGGESTION`). Mandatory on-site confirmation by a human Fire Safety Officer is enforced. Verified passing in `test_phase9_ai_safety_and_human_review_invariants`.
5. **No Autonomous Certification:** Prohibited compliance states (`AI_APPROVED`, `AI_REJECTED`) are rejected with 422 Unprocessable Content. Verified passing in `test_phase10_prohibited_compliance_states_raise_validation_error` and `test_prohibited_statutory_states_rejected_by_schemas`.
6. **Gemini Safety:** External Gemini calls remain completely disabled by default (`GEMINI_ENABLED=false`). Verified passing offline with synthetic fallback models.

---

## 7. Remaining Limitations and Blockers

| Limitation ID | Category | Description | Root Cause |
| :--- | :--- | :--- | :--- |
| **LIM-001** | Dataset Scarcity | Zero detection capability on exit doors and obstacles | Entire corpus contains only 42 door instances and 93 obstacle instances. |
| **LIM-002** | False Alarm Rate | ~44%–59% of extinguisher proposals are false alarms | Visual confusion with background fixtures (hose reels, pipes, red signage). |
| **LIM-003** | Small Object Recall | Exit signs $<1\%$ image area suffer low recall ($<15\%$) | Single-stride $16 \times 16$ grid without multi-scale feature pyramids (FPN). |
| **LIM-004** | Calibration Deficit | Detection confidence scores are empirical, not probabilistic | Small sample sizes on scarce classes prevent reliable Platt scaling or temperature scaling. |

---

## 8. Recommended Next Experiment

### Recommended Architecture: Multi-Scale Feature Pyramid Transfer Learning (Phase 11 Proposal)
1. **Backbone & Neck:** Replace single-stage $16 \times 16$ head with a multi-scale Feature Pyramid Network (FPN / PANet) with P3 ($32 \times 32$), P4 ($16 \times 16$), and P5 ($8 \times 8$) detection heads.
2. **Loss Function:** Implement Class-Balanced Focal Loss ($\gamma = 2.0, \alpha = 0.25$) to prevent dominant classes (extinguishers) from overwhelming gradient updates on scarce classes (doors, obstacles).
3. **Data Prerequisite:** Curate $\ge 1,500$ verified Indian commercial building images with verified annotations for exit doors and obstacles across multiple NBC occupancies before unblocking the gate.

---

## 9. Final Governance Status

> [!CAUTION]
> **GOVERNANCE DECISION: MODEL REMAINS NOT APPROVED FOR PRODUCTION USE**  
> - **Production Gate Status:** `NOT_APPROVED`  
> - **Calibration Status:** `NOT_CALIBRATED`  
> - **Operational Actionability:** `FIRE_EXTINGUISHER` and `EXIT_SIGN` are `ADVISORY_ONLY`. `EXIT_DOOR` and `OBSTACLE` are `UNSUPPORTED`.  
> - **Statutory Authority:** The model **MUST NOT autonomously approve or reject Fire NOC applications**. Human Fire Safety Officer physical inspection remains mandatory.
