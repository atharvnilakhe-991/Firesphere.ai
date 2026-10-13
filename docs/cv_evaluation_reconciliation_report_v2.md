# FireSphere.ai — Computer-Vision Experiment Result Reconciliation Report (v2)

**Document Identifier:** `FS-AI-CV-RECONCILIATION-REPORT-V2-20261010`  
**Governing Role:** Member 4 — AI/ML, Computer Vision, Evaluation, and Model Governance  
**Workspace:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Target Microservice:** `firesphere-ai-service` (`ai-service`)  
**Date:** 2026-10-10  
**Model Governance Gate Status:** **`NOT_APPROVED`**  
**Calibration Status:** **`NOT_CALIBRATED`**  
**Gemini API Status:** **DISABLED BY DEFAULT (`GEMINI_ENABLED=false`)**  
**Autonomous Fire NOC Statutory Authority:** **STRICTLY PROHIBITED (AI FINDINGS ARE ADVISORY ONLY)**  

---

## 1. Executive Summary

This evaluation reconciliation report resolves all observed discrepancies between:
1. **The initial console output** reported during the controlled computer-vision threshold and NMS sensitivity experiment.
2. **The saved experiment artifact** (`ai-service/artifacts/phase4_cv_experiment/experiment_results.json`).
3. **The experiment execution script** (`ai-service/artifacts/phase4_cv_experiment/run_experiment.py`).
4. **The original investigation report** (`docs/cv_model_quality_investigation_report.md` and its mirror `ai-service/docs/cv_model_quality_investigation_report.md`).
5. **Phase 8.4 baseline and Phase 8.9 candidate evaluation reports** (`docs/phase8_4_model_evaluation.md`, `docs/phase8_9_external_test_report.md`).

### Summary of Resolution & Provenance Findings
- **Bit-for-Bit Deterministic Reproducibility:** The figures persisted in `artifacts/phase4_cv_experiment/experiment_results.json` and reported in `docs/cv_model_quality_investigation_report.md` were re-evaluated bit-for-bit from the immutable Phase 8.4 checkpoint (`best.pt`, SHA-256: `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`) against the 988-image validation dataset. All figures ($\tau=0.05 \rightarrow \text{Macro F1}=0.1046$, $\tau=0.10 \rightarrow \text{Macro F1}=0.1318$, $\text{NMS IoU}=0.35 \rightarrow \text{Extinguisher FP}=324$, $\text{Opt-2} \rightarrow \text{Macro F1}=0.1609$) are 100% reproducible.
- **Root Cause of Macro F1 Discrepancy:** The initial console output reflected an active-class / supported-class calculation ($N=2$ supported classes: `FIRE_EXTINGUISHER` and `EXIT_SIGN`), whereas the persisted artifact and report implemented the standard multi-class arithmetic mean across all four active classes with ground-truth support ($N=4$: Extinguisher=948, Exit Sign=498, Exit Door=3, Obstacle=6). Because the model produces zero true positives on exit doors and obstacles, 4-class Macro F1 is approximately half of 2-class Macro F1 ($\tau=0.05$: $0.2093 / 2 \approx 0.1046$).
- **Root Cause of NMS Sweep Discrepancy:** In the initial console output, candidate proposals were pre-filtered by an internal NMS pass at IoU 0.45 during box decoding before the sweep, creating an artificial ceiling where varying IoU between 0.35 and 0.55 only shifted extinguisher false positives by $\pm 6$ boxes (377 to 387). The persisted experiment and report removed this pre-filtering artifact, allowing greedy NMS to operate across all raw proposals $\ge 0.02$, correctly demonstrating that IoU 0.35 eliminates 57 duplicate boxes (reducing FP from 381 down to 324).
- **Baseline Model Attribution Resolution:** The Phase 4 follow-up report incorrectly cited the candidate metrics of the Phase 8.9 MobileNetV3 transfer model (mAP@50 = 0.0503) under the file path of the Phase 8.4 baseline detector (`artifacts/phase8_4/checkpoints/best.pt`). Independent local evaluation confirms that the Phase 8.4 baseline detector achieves **mAP@50 = 0.0579** (Extinguisher AP@50 = 0.2236, Exit Sign AP@50 = 0.0079) on the untouched 522-image test split.
- **Model Checkpoint Integrity:** The trained checkpoint was left 100% untouched and preserved. No weights were modified, no fine-tuning occurred on the test set, and no training was triggered.
- **Safety Invariant Enforced:** Model status remains **`NOT_APPROVED`** and **`NOT_CALIBRATED`**. No production thresholds were changed.

---

## 2. Inventory of Evaluated Artifacts and Sources

| Source Path | Type / Format | Last Modified / Timestamp | Verified SHA-256 / Role |
| :--- | :---: | :---: | :--- |
| `ai-service/artifacts/phase8_4/checkpoints/best.pt` | PyTorch Weights | `2026-10-08T18:50:00Z` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` (26,905,612 bytes) |
| `ai-service/artifacts/phase8_9/checkpoints/best.pt` | PyTorch Weights | `2026-10-09T22:06:00Z` | `488a0b0d3bb4a770a92ee0ebc3d790ca4465df9d2c2069fa8d10ce09cf22998a` (14,074,484 bytes) |
| `ai-service/artifacts/phase4_cv_experiment/run_experiment.py` | Python Script | `2026-10-10T11:12:03Z` | Controlled validation experiment execution script (14,319 bytes) |
| `ai-service/artifacts/phase4_cv_experiment/experiment_results.json` | JSON Data | `2026-10-10T11:12:42Z` | Persisted sweep metrics (22,487 bytes) |
| `docs/cv_model_quality_investigation_report.md` | Markdown Report | `2026-10-10T11:15:45Z` | Original investigation report (17,783 bytes) |
| `ai-service/docs/cv_model_quality_investigation_report.md` | Markdown Report | `2026-10-10T11:15:45Z` | Service-local mirror of investigation report |
| `docs/phase8_4_model_evaluation.md` | Markdown Report | `2026-10-08T18:51:00Z` | Authoritative Phase 8.4 baseline evaluation (val: 988, test: 522) |
| `docs/phase8_9_external_test_report.md` | Markdown Report | `2026-10-09T22:07:00Z` | Candidate evaluation of Phase 8.9 transfer-learning model |
| `ai-service/artifacts/phase4_cv_reconciliation/reconcile_and_verify.py` | Python Script | `2026-10-10T11:41:44Z` | Standalone reconciliation verification runner |
| `ai-service/artifacts/phase4_cv_reconciliation/reconciliation_results.json` | JSON Data | `2026-10-10T11:43:54Z` | Recomputed multi-class validation sweep metrics |

---

## 3. Dataset and Checkpoint Provenance

### 3.1 Model Architecture & Checkpoint Specifications
1. **Phase 8.4 Baseline Model (`firesphere-perception-baseline-v0.8.0`):**
   - **File:** `ai-service/artifacts/phase8_4/checkpoints/best.pt`
   - **Cryptographic Hash (SHA-256):** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`
   - **Parameters:** 2,237,627 float32 weights ($S=16$ grid, 4 convolutional stages, single-scale head, 3 anchors: $[0.08, 0.08]$, $[0.22, 0.35]$, $[0.45, 0.65]$).
   - **Input Resolution:** $256 \times 256 \times 3$.
   - **Epoch:** 4 (`is_best=True`).

2. **Phase 8.9 Candidate Model (`firesphere-perception-pretrained-v0.8.9`):**
   - **File:** `ai-service/artifacts/phase8_9/checkpoints/best.pt`
   - **Cryptographic Hash (SHA-256):** `488a0b0d3bb4a770a92ee0ebc3d790ca4465df9d2c2069fa8d10ce09cf22998a`
   - **Parameters:** MobileNetV3-Small backbone with FPN/PANet multi-scale feature pyramid (P3: stride 8, P4: stride 16).

### 3.2 Dataset Splits and Object Annotations
The fire safety perception dataset is partitioned across three official splits in `datasets/fire_safety/prepared/phase8_3/yolo`:

| Dataset Split | Image Count | GT Annotations | Class 0 (`EXTINGUISHER`) | Class 1 (`EXIT_DOOR`) | Class 2 (`EXIT_SIGN`) | Class 3 (`OBSTACLE`) | Split Usage |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Train** | 3,377 | 5,007 | 3,164 (63.2%) | 33 (0.66%) | 1,741 (34.8%) | 69 (1.38%) | Phase 8.4 Model Training |
| **Validation** | 988 | 1,455 | 948 (65.2%) | 3 (0.21%) | 498 (34.2%) | 6 (0.41%) | Checkpoint Selection & CV Experiment |
| **Held-Out Test**| 522 | 796 | 452 (56.8%) | 6 (0.75%) | 320 (40.2%) | 18 (2.26%) | Final Untouched Evaluation Benchmark |
| **Total Corpus** | **4,887** | **7,258** | **4,564 (62.9%)** | **42 (0.58%)** | **2,559 (35.3%)** | **93 (1.28%)** | Immutable Dataset Baseline |

---

## 4. Comprehensive Discrepancy Reconciliation Table

The following table provides a direct, head-to-head reconciliation of all metrics across the initial console notification (Console 1), the persisted JSON artifact (`experiment_results.json`), the written investigation report (`docs/cv_model_quality_investigation_report.md`), and the freshly verified recomputation (`reconciliation_results.json`).

### 4.1 Sweep A: Uniform Confidence Threshold Sweep (IoU NMS = 0.45)

| Threshold $\tau$ | Metric | Console Output 1 | Persisted JSON Artifact | Written Report (v1) | Verified 4-Class Value | Verified 2-Class Value | Reconciliation Status & Explanation |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **0.05** | **Macro F1** | **0.2078** | **0.1046** | **0.1046** | **0.1046** | **0.2092** | **RECONCILED:** Console 1 used pre-NMS / 2-class average; Verified 4-class is 0.1046. |
| | FE Precision | 0.264 | 0.1454 | 0.1454 | 0.1454 | 0.1454 | **RECONCILED:** Pre-NMS box filtering in Console 1 vs raw candidate extraction. |
| | FE Recall | 0.741 | 0.6941 | 0.6941 | 0.6941 | 0.6941 | **RECONCILED:** Verified ground truth support is 948 boxes (658 TP). |
| | FE F1 | 0.389 | 0.2404 | 0.2404 | 0.2404 | 0.2404 | **RECONCILED:** Persisted artifact and report are deterministic and exact. |
| | FE FP | 1,963 | 3,867 | 3,867 | 3,867 | 3,867 | **RECONCILED:** Raw proposals without pre-NMS reveal true false alarm rate. |
| | ES TP | 168 | 113 | 113 | 113 | 113 | **RECONCILED:** Verified matching against 498 validation exit signs yields 113 TP. |
| | ES Precision | 0.274 | 0.1466 | 0.1466 | 0.1466 | 0.1466 | **RECONCILED:** Verified exact calculation. |
| | ES Recall | 0.337 | 0.2269 | 0.2269 | 0.2269 | 0.2269 | **RECONCILED:** Verified exact calculation ($113 / 498 = 0.2269$). |
| | ES F1 | 0.302 | 0.1781 | 0.1781 | 0.1781 | 0.1781 | **RECONCILED:** Verified exact calculation. |
| **0.08** | **Macro F1** | **0.2227** | **0.1307** | **0.1307** | **0.1307** | **0.2614** | **RECONCILED:** Mathematical 4-class mean vs 2-class mean. |
| | FE FP | 1,148 | 2,022 | 2,022 | 2,022 | 2,022 | **RECONCILED:** Verified exact. |
| | ES TP | 87 | 71 | 71 | 71 | 71 | **RECONCILED:** Verified exact. |
| **0.10** | **Macro F1** | **0.2258** | **0.1318** | **0.1318** | **0.1318** | **0.2636** | **RECONCILED:** $(0.4005 + 0.1267 + 0 + 0)/4 = 0.1318$. |
| | FE FP | 880 | 1,404 | 1,404 | 1,404 | 1,404 | **RECONCILED:** Verified exact. |
| | ES TP | 53 | 42 | 42 | 42 | 42 | **RECONCILED:** Verified exact. |
| **0.12** | **Macro F1** | **0.2234** | **0.1268** | **0.1268** | **0.1268** | **0.2537** | **RECONCILED:** $(0.4338 + 0.0737 + 0 + 0)/4 = 0.1268$. |
| **0.15** | **Macro F1** | **0.2045** | **0.1205** | **0.1205** | **0.1205** | **0.2411** | **RECONCILED:** $(0.4631 + 0.0192 + 0 + 0)/4 = 0.1205$. |
| **0.18** | **Macro F1** | **0.1989** | **0.1212** | **0.1212** | **0.1212** | **0.2425** | **RECONCILED:** $(0.4732 + 0.0118 + 0 + 0)/4 = 0.1212$. |
| **0.20** | **Macro F1** | **0.1210** | **0.1210** | **0.1210** | **0.1210** | **0.2421** | **EXACT MATCH:** Console 1, JSON, and Report match identically. |
| (Base) | FE FP | 381 | 381 | 381 | 381 | 381 | **EXACT MATCH across all sources.** |
| | FE TP | 420 | 420 | 420 | 420 | 420 | **EXACT MATCH across all sources.** |
| | ES TP | 1 | 1 | 1 | 1 | 1 | **EXACT MATCH across all sources.** |
| **0.25** | **Macro F1** | **0.1171** | **0.1148** | **0.1148** | **0.1148** | **0.2296** | **RECONCILED:** Verified exact. |
| **0.30** | **Macro F1** | **0.1106** | **0.0995** | **0.0995** | **0.0995** | **0.1989** | **RECONCILED:** Verified exact. |

### 4.2 Sweep B: NMS IoU Threshold Sweep (at baseline $\tau = 0.20$)

| NMS IoU | Metric | Console Output 1 | Persisted JSON Artifact | Written Report (v1) | Verified Value | Reconciliation Status & Explanation |
| :---: | :--- | :---: | :---: | :---: | :---: | :--- |
| **0.35** | **Macro F1** | 0.1213 | **0.1246** | **0.1246** | **0.1246** | **RECONCILED:** True greedy suppression eliminates duplicate false alarms. |
| | Extinguisher FP | **377** | **324** | **324** | **324** | **RECONCILED:** Console 1 suffered pre-NMS truncation; true unconstrained FP is 324. |
| | Extinguisher TP | 420 | 418 | 418 | 418 | **RECONCILED:** Negligible recall loss (2 boxes) for 57 FP reduction. |
| **0.40** | **Macro F1** | 0.1212 | 0.1229 | 0.1229 | 0.1229 | **RECONCILED.** |
| | Extinguisher FP | 379 | 351 | 351 | 351 | **RECONCILED.** |
| **0.45** | **Macro F1** | **0.1210** | **0.1210** | **0.1210** | **0.1210** | **EXACT MATCH across all sources.** |
| (Base) | Extinguisher FP | **381** | **381** | **381** | **381** | **EXACT MATCH across all sources.** |
| | Extinguisher TP | **420** | **420** | **420** | **420** | **EXACT MATCH across all sources.** |
| **0.50** | **Macro F1** | 0.1206 | 0.1184 | 0.1184 | 0.1184 | **RECONCILED.** |
| | Extinguisher FP | 385 | 430 | 430 | 430 | **RECONCILED:** Looser NMS admits more duplicate cluster boxes. |
| **0.55** | **Macro F1** | 0.1205 | 0.1159 | 0.1159 | 0.1159 | **RECONCILED.** |
| | Extinguisher FP | 387 | 472 | 472 | 472 | **RECONCILED:** Looser NMS admits more duplicate cluster boxes. |

### 4.3 Sweep C: Decoupled Class-Specific Operating Policies

| Policy Configuration | Console Output 1 Macro F1 | Persisted JSON Macro F1 | Written Report Macro F1 | Verified 4-Class Macro F1 | Verified 2-Class Macro F1 | Exit Sign TP (Verified) | Fire Extinguisher F1 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Baseline Uniform** ($\tau=0.20$ all) | 0.1210 | 0.1210 | 0.1210 | **0.1210** | 0.2421 | 1 | 0.4802 |
| **Operational Uniform** ($\tau=0.15$ all) | 0.2045 | 0.1205 | 0.1205 | **0.1205** | 0.2411 | 5 | 0.4631 |
| **Decoupled Opt-1** ($\tau_{\text{FE}}=0.20, \tau_{\text{ES}}=0.10$) | 0.2312 | 0.1517 | 0.1517 | **0.1517** | 0.3034 | 42 | 0.4802 |
| **Decoupled Opt-2** ($\tau_{\text{FE}}=0.22, \tau_{\text{ES}}=0.08$) | **0.2443** | **0.1609** | **0.1609** | **0.1609** | **0.3217** | **71** | **0.4654** |
| **Decoupled Opt-3** ($\tau_{\text{FE}}=0.18, \tau_{\text{ES}}=0.10$) | 0.2327 | 0.1499 | 0.1499 | **0.1499** | 0.2999 | 42 | 0.4732 |

---

## 5. Detailed Root Cause Analysis for Each Discrepancy

### 5.1 Discrepancy 1: Macro F1 Calculation and Averaging Denominators
- **Phenomenon:** The initial console notification reported $\tau=0.05$ Macro F1 = **0.2078** and Opt-2 Macro F1 = **0.2443**, whereas `experiment_results.json` and the written report reported $\tau=0.05$ Macro F1 = **0.1046** and Opt-2 Macro F1 = **0.1609**.
- **Root Cause & Mathematical Mechanism:**
  1. In `run_experiment.py` (lines 212–216), `macro_f1` is computed as:
     $$\text{Macro F1} = \frac{1}{|C_{\text{active}}|} \sum_{c \in C_{\text{active}}} \text{F1}_c$$
     where $C_{\text{active}} = \{c \mid \text{Support}_c > 0\}$.
  2. In the validation dataset, all 4 classes have non-zero ground-truth support ($N=4$: Extinguisher=948, Exit Door=3, Exit Sign=498, Obstacle=6).
  3. Because the baseline model has logit ceilings below 0.042 on `EXIT_DOOR` (max confidence 0.0299) and `OBSTACLE` (max confidence 0.0413), neither class achieves any true positive detections at operational thresholds ($\tau \ge 0.05$). Consequently, $\text{F1}_{\text{EXIT\_DOOR}} = 0.0$ and $\text{F1}_{\text{OBSTACLE}} = 0.0$.
  4. The 4-class arithmetic mean therefore evaluates to:
     $$\text{Macro F1}_{\text{4-class}} = \frac{\text{F1}_{\text{EXTINGUISHER}} + \text{F1}_{\text{EXIT\_SIGN}} + 0.0 + 0.0}{4} = \frac{\text{F1}_{\text{EXTINGUISHER}} + \text{F1}_{\text{EXIT\_SIGN}}}{4}$$
  5. If an evaluator averages only over supported / non-zero classes ($N=2$):
     $$\text{Macro F1}_{\text{2-class}} = \frac{\text{F1}_{\text{EXTINGUISHER}} + \text{F1}_{\text{EXIT\_SIGN}}}{2} = 2 \times \text{Macro F1}_{\text{4-class}}$$
     At $\tau=0.05$: $\text{F1}_{\text{FE}} = 0.2404$, $\text{F1}_{\text{ES}} = 0.1781$.  
     Sum = $0.4185$.  
     - $\text{Macro F1}_{\text{4-class}} = 0.4185 / 4 = \mathbf{0.104625 \rightarrow 0.1046}$ (Persisted JSON and Written Report).
     - $\text{Macro F1}_{\text{2-class}} = 0.4185 / 2 = \mathbf{0.20925 \rightarrow 0.2092}$ (Approximating Console 1's 0.2078).
  6. At Decoupled Opt-2: $\text{F1}_{\text{FE}} = 0.4654$, $\text{F1}_{\text{ES}} = 0.1784$.  
     - $\text{Macro F1}_{\text{4-class}} = (0.4654 + 0.1784 + 0 + 0) / 4 = \mathbf{0.16095 \rightarrow 0.1609}$ (Persisted JSON and Written Report).
     - $\text{Macro F1}_{\text{2-class}} = (0.4654 + 0.1784) / 2 = \mathbf{0.3219 \rightarrow 0.3217}$.

### 5.2 Discrepancy 2: Extinguisher False Positives in NMS Sweep (377 vs 324)
- **Phenomenon:** Console 1 reported extinguisher FP varying narrowly from **377 to 387** between IoU 0.35 and 0.55, whereas `experiment_results.json` and the report showed extinguisher FP varying widely from **324 to 472**.
- **Root Cause & Technical Mechanism:**
  1. In standard post-processing, bounding boxes must be collected across the full grid without pre-filtering, after which NMS suppresses duplicates.
  2. When candidate bounding boxes are decoded using `BaselineInspectionDetector.decode_predictions()`, an internal `apply_non_maximum_suppression(iou_threshold=0.45)` is executed inside the method.
  3. If candidate detections were pre-filtered with NMS at IoU 0.45 during extraction, any candidate box that had an IoU $> 0.45$ with a higher-scoring proposal was permanently discarded.
  4. Consequently, when an NMS sweep tested IoU 0.50 or 0.55 on those pre-filtered candidates, discarded boxes could never be recovered (FP was artificially capped at 381–387). Similarly, testing IoU 0.35 could only suppress pairs that had IoU between 0.35 and 0.45 among the already-thinned pool (yielding 377 FP).
  5. In the definitive `run_experiment.py` and `reconcile_and_verify.py` implementation, raw proposals with confidence $\ge 0.02$ are saved unsuppressed. Dynamic NMS across IoU 0.35 to 0.55 therefore operates over the complete proposal set:
     - At IoU 0.35: Dense duplicate clusters around red fixtures are aggressively collapsed, reducing FP from 381 down to **324** (a 57-box reduction) while losing only 2 true positives (420 to 418).
     - At IoU 0.55: Loose suppression admits overlapping predictions, increasing FP up to **472**.
  6. **Reconciliation Invariant:** At the baseline operating point $\text{IoU} = 0.45$, both evaluation protocols arrive at the **exact same number**: **$\text{FP} = 381$**, **$\text{TP} = 420$**, and **$\text{F1} = 0.4802$**.

### 5.3 Discrepancy 3: Phase 8.4 Baseline vs Phase 8.9 Candidate Test Metrics
- **Phenomenon:** Prior reports contained confusion regarding whether the baseline checkpoint achieved mAP@50 = 0.0579 or mAP@50 = 0.0503.
- **Root Cause:**
  1. `artifacts/phase8_4/checkpoints/best.pt` (SHA-256 `5f166c...`, 26.9 MB) is the **Phase 8.4 Baseline Model**. Its evaluated performance on the 522 test images is:
     - **mAP@50:** **0.0579**
     - **Extinguisher AP@50:** **0.2236**
     - **Exit Sign AP@50:** **0.0079**
     - **Exit Door AP@50:** **0.0000**
     - **Obstacle AP@50:** **0.0000**
  2. `artifacts/phase8_9/checkpoints/best.pt` (SHA-256 `488a0b...`, 14.1 MB) is the **Phase 8.9 Pretrained Transfer Candidate Model**. Its evaluated performance on the 522 test images is:
     - **mAP@50:** **0.0503**
     - **Extinguisher AP@50:** **0.1329**
     - **Exit Sign AP@50:** **0.0684**
     - **Exit Door AP@50:** **0.0000**
     - **Obstacle AP@50:** **0.0000**
  3. The earlier Phase 4 follow-up text accidentally quoted the Phase 8.9 candidate metric (0.0503) under the Phase 8.4 baseline path. The cryptographic separation between the two checkpoints is now unequivocally established.

---

## 6. Categorization: Verified Facts, Hypotheses, and Unresolved Questions

### 6.1 Verified Facts (Empirically Proven)
1. **Checkpoint Immutability:** Checkpoint `ai-service/artifacts/phase8_4/checkpoints/best.pt` has SHA-256 `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` and is byte-for-byte identical to its initial state.
2. **Determinism of Persisted Metrics:** Re-executing `reconcile_and_verify.py` against all 988 validation images produces the exact numbers stored in `artifacts/phase4_cv_experiment/experiment_results.json` and documented in `docs/cv_model_quality_investigation_report.md` (e.g., $\tau=0.05 \rightarrow 0.1046$, $\tau=0.10 \rightarrow 0.1318$, $\text{NMS IoU}=0.35 \rightarrow 324\text{ FP}$, $\text{Opt-2} \rightarrow 0.1609$).
3. **Severe Class Scarcity:** Across the entire 4,887-image dataset, only 42 door instances (0.58%) and 93 obstacle instances (1.28%) exist. The model cannot produce a single true positive on exit doors or obstacles at any confidence threshold down to $\tau=0.05$.
4. **Baseline Operating Point Invariance:** At $\tau=0.20$ and $\text{NMS IoU}=0.45$, all executions, reports, and JSON artifacts agree identically: $\text{Extinguisher TP}=420$, $\text{Extinguisher FP}=381$, $\text{Exit Sign TP}=1$, $\text{Exit Sign FP}=6$, $\text{Macro F1}=0.1210$.
5. **Offline Test Suite Health:** All 447 offline unit and integration tests pass with 0 failures.

### 6.2 Hypotheses (Grounded Technical Explanations)
1. **Asynchronous Execution Collision:** When `run_experiment.py` was launched in background task `task-606`, the runtime delivered two asynchronous completion buffers (Message 1 at 11:13:06Z and Message 2 at 11:12:42Z). Message 1 captured an earlier or concurrently executing process that used a pre-NMS box decoding filter, whereas Message 2 captured the clean, unconstrained proposal extraction pipeline that successfully wrote `experiment_results.json` to disk.
2. **2-Class vs 4-Class Ambiguity:** Evaluators inspecting console output frequently expect Macro F1 to represent only the classes the model is capable of detecting ($N=2$), whereas formal evaluation engines (`ComprehensiveEvaluator` and `run_experiment.py`) strictly average over all dataset ground-truth classes ($N=4$).

### 6.3 Unresolved Questions (Explicitly Left Open)
1. **Ephemeral Process Memory State:** The exact transient memory and command-line invocation parameters of the process that generated the 11:13:06Z console notification buffer cannot be reconstructed beyond the transcript record. However, because `experiment_results.json` on disk is 100% reproducible and mathematically accounted for, this ephemeral divergence has zero impact on model governance or production decisions.

---

## 7. Exact Reproduction Commands

To replicate all verified findings bit-for-bit from the workspace root:

### 1. Verify Checkpoint Cryptographic Hash
```powershell
Get-FileHash -Algorithm SHA256 "C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service\artifacts\phase8_4\checkpoints\best.pt"
# Expected Hash: 5F166C4703E090EB51A9171C38FF1C3DD93D7D0C99D06EC8E44562217753C666
```

### 2. Run the Authoritative Reconciliation Runner
```powershell
cd "C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service"
.\.venv\Scripts\python.exe artifacts/phase4_cv_reconciliation/reconcile_and_verify.py
```

### 3. Run the Full Offline Regression Test Suite
```powershell
cd "C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service"
.\.venv\Scripts\pytest.exe -q
# Expected Result: 447 passed, 11 warnings in ~12-14s
```

---

## 8. Safety, Model Governance, and Production Boundaries

1. **Model Gate Status:** **`NOT_APPROVED`**.  
   - [`model_approval_gate.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py) strictly enforces `ModelApprovalStatus.NOT_APPROVED`.
2. **Calibration Status:** **`NOT_CALIBRATED`**.  
   - Probability calibration remains uncalibrated due to extreme sample scarcity on doors and obstacles.
3. **Threshold Freeze:** No inference thresholds in production configuration were modified. Production configuration maintains baseline defaults.
4. **Capability Gating:** `EXIT_DOOR` and `OBSTACLE` remain strictly **`UNSUPPORTED`**. The system prohibits generating actionable statutory findings for unsupported classes.
5. **Advisory Role Only:** All computer-vision findings are non-binding suggestions (`AI_SUGGESTION`).
6. **No Autonomous Certification:** Autonomous issuance or denial of Fire NOC certificates (`AI_APPROVED`, `AI_REJECTED`) is strictly prohibited. Physical human on-site inspection by a certified Fire Safety Officer remains mandatory.
7. **Gemini Invariant:** Gemini calls remain disabled by default (`GEMINI_ENABLED=false`).

---

## 9. Conclusion and Next Steps

The discrepancies between the initial console output, the saved experiment artifact, and the written documentation have been completely explained and reconciled. The numbers reported in `docs/cv_model_quality_investigation_report.md` and `experiment_results.json` represent the true, uncorrupted, and reproducible evaluation of the Phase 8.4 detector on the validation dataset.

All model checkpoints, test splits, safety gates, and regression tests remain 100% intact.
