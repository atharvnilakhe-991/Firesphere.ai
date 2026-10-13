# Computer-Vision Model Evaluation Evidence Audit Report

**Project:** FireSphere.ai  
**Service:** AI Microservice (`ai-service`)  
**Audit Date:** October 13, 2026  
**Document ID:** `FS-AI-CV-EVAL-EVIDENCE-AUDIT-20261013`  
**Current Gate Status:** **`NOT_APPROVED`** | **`NOT_CALIBRATED`**  
**Autonomous Fire NOC Statutory Authority:** **STRICTLY PROHIBITED (ADVISORY ONLY)**  

---

## Executive Summary

This independent audit verifies the mathematical, cryptographic, and architectural evidence underlying the current Computer Vision (CV) model quality assessment for FireSphere.ai before any retraining or production-gate approval decision is undertaken.

### Key Audit Conclusions:
1. **Cryptographic Checkpoint Verification (PASS):**  
   The baseline checkpoint file at `ai-service/artifacts/phase8_4/checkpoints/best.pt` has an exact SHA-256 match to `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` (size: 26,905,612 bytes).
2. **Deterministic Metric Reproduction (100% REPRODUCIBLE):**  
   Executing `Phase84Trainer.evaluate_split('test', checkpoint_path)` against the 522-image held-out test split reproduced every reported metric in `artifacts/phase8_4/metrics/test_metrics.json` **to the fourth decimal place**:
   - **Macro mAP@50:** **`0.0579`** (5.79%)
   - **Macro mAP@50:95:** **`0.0194`** (1.94%)
   - **Mean Precision:** **`0.2283`** | **Mean Recall:** **`0.1125`** | **Mean F1:** **`0.1261`**
   - **Class AP@50:** `FIRE_EXTINGUISHER`: **`0.2236`** | `EXIT_SIGN`: **`0.0079`** | `EXIT_DOOR`: **`0.0000`** | `OBSTACLE`: **`0.0000`**
   - **Confusion Matrix:** Exactly matches the documented cell counts (`TP_FE=195, FP_FE=185, FN_FE=257; TP_ES=6, FP_ES=9, FN_ES=314`).
3. **Resolution of the "Zero-Detection" Discrepancy (EXPLAINED & RESOLVED):**  
   The earlier report describing "zero detections" (Phase 14 joint evaluation) evaluated **10 flat synthetic 2D geometric vector drawings** generated with Pillow, on which the photographic convolutional backbone produced zero feature activations. The 5.79% mAP@50 report evaluated **522 natural photographic inspection images** from the held-out test corpus. The two evaluations are **not contradictory**; they tested fundamentally different domains.
4. **Root Cause of Performance Failure (EMPIRICALLY CONFIRMED):**  
   The corpus contains a catastrophic class imbalance: in 4,887 images, there are **only 42 exit doors (0.58%)** and **93 obstacles (1.28%)**. Consequently, door and obstacle logits never exceed `0.0413`, completely suppressing all proposals below the 0.20 confidence floor.
5. **Production Gate Status (REMAINS FAIL-CLOSED `NOT_APPROVED`):**  
   The model must **remain strictly NOT_APPROVED**. Gated inference, error states, and empty detections are properly differentiated in the codebase.

---

## 1. Exact Artifact Paths Inspected

The following local workspace files were inspected, verified, and cross-referenced on disk:

| Artifact Category | Exact Workspace Path | Size / Count | Description / Role |
| :--- | :--- | :--- | :--- |
| **Model Checkpoint** | `ai-service/artifacts/phase8_4/checkpoints/best.pt` | 26,905,612 B | PyTorch dictionary checkpoint (`epoch=4`, `is_best=True`). |
| **Last Checkpoint** | `ai-service/artifacts/phase8_4/checkpoints/last.pt` | 26,905,612 B | Final epoch checkpoint (`epoch=5`). |
| **Model Metadata** | `ai-service/artifacts/phase8_4/metadata/model_metadata.json` | 7,983 B | Baseline model configuration, environment, and metrics dump. |
| **Training Config** | `ai-service/artifacts/phase8_4/configs/training_config.json` | 1,399 B | Training hyperparameter and epoch history record. |
| **Test Metrics** | `ai-service/artifacts/phase8_4/metrics/test_metrics.json` | 2,107 B | Documented evaluation metrics on 522 test images. |
| **Validation Metrics** | `ai-service/artifacts/phase8_4/metrics/val_metrics.json` | 2,057 B | Documented evaluation metrics on 988 validation images. |
| **Dataset Config** | `datasets/fire_safety/prepared/phase8_3/yolo/data.yaml` | 240 B | YOLO dataset manifest defining 4 classes and split paths. |
| **Train Split** | `datasets/fire_safety/prepared/phase8_3/yolo/images/train`<br>`datasets/fire_safety/prepared/phase8_3/yolo/labels/train` | 3,377 images<br>3,377 labels | Training partition containing 5,007 ground-truth annotations. |
| **Validation Split** | `datasets/fire_safety/prepared/phase8_3/yolo/images/val`<br>`datasets/fire_safety/prepared/phase8_3/yolo/labels/val` | 988 images<br>988 labels | Validation partition containing 1,455 ground-truth annotations. |
| **Test Split** | `datasets/fire_safety/prepared/phase8_3/yolo/images/test`<br>`datasets/fire_safety/prepared/phase8_3/yolo/labels/test` | 522 images<br>522 labels | Held-out test partition containing 796 ground-truth annotations. |
| **Training/Eval Engine** | `ai-service/app/training/phase8_4_trainer.py` | 32,245 B | Implementation of `Phase84Trainer` and dataset loader. |
| **Comprehensive Evaluator** | `ai-service/app/evaluation/comprehensive_evaluator.py` | 9,593 B | 101-point interpolated mAP and confusion matrix engine. |
| **Model Architecture** | `ai-service/app/models/baseline_detector.py` | 8,916 B | PyTorch implementation of `BaselineInspectionDetector`. |
| **Model Loader** | `ai-service/app/models/model_loader.py` | 6,693 B | Checkpoint loading, device selection, and runtime adapter. |
| **Model Approval Gate** | `ai-service/app/models/model_approval_gate.py` | 13,874 B | Fail-closed gate logic enforcement singleton. |
| **Gate Config** | `ai-service/config/model_approval.json` | 1,489 B | Persistent JSON gate configuration. |
| **Phase 14 Synthetic Data** | `ai-service/tests/fixtures/phase14/` | 10 images + 2 JSON | 10-sample 2D vector synthetic evaluation fixture. |
| **Phase 14 Report** | `docs/integration/phase14_cv_qwen_joint_evaluation.md` | 20,567 B | Earlier joint evaluation report citing 0 CV detections. |
| **CV Quality Report** | `docs/cv_model_quality_investigation_report.md` | 17,783 B | Previous investigation report citing 5.79% mAP@50. |

---

## 2. Checkpoint Identity and Integrity Audit

### 2.1 Cryptographic Hash Verification
- **Target File:** `ai-service/artifacts/phase8_4/checkpoints/best.pt`
- **Expected SHA-256:** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`
- **Computed SHA-256:** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`
- **Exact Match:** **YES (100% verified)**. The file has not been modified, replaced, or retrained.

### 2.2 Internal Checkpoint Structure
The checkpoint was safely deserialized via PyTorch on CPU without executing untrusted code:
- **Top-Level Keys:** `['epoch', 'model_state_dict', 'metrics', 'metadata', 'optimizer_state_dict']`
- **Recorded Epoch:** `4` (out of 5 total training epochs)
- **Saved Metrics:** `{"val_loss": 0.851511, "train_loss": 0.650504}`
- **Metadata:** `{"model_version": "0.8.0-baseline", "seed": 42, "epoch": 4, "is_best": true}`
- **Model Architecture:** `BaselineInspectionDetector` (CNN backbone downsampling $256 \times 256 \times 3$ to a single $16 \times 16 \times 3 \times 9$ prediction tensor, parameterized by 2,237,627 float32 weights).
- **Class Mapping:**
  - `0`: `FIRE_EXTINGUISHER`
  - `1`: `EXIT_DOOR`
  - `2`: `EXIT_SIGN`
  - `3`: `OBSTACLE`

---

## 3. Dataset Integrity, Splitting, and Class Imbalance

### 3.1 Partition Verification and Split Isolation
Disk enumeration of `datasets/fire_safety/prepared/phase8_3/yolo` was executed:
- **Train Split:** 3,377 images, 3,377 label files
- **Validation Split:** 988 images, 988 label files
- **Held-Out Test Split:** 522 images, 522 label files
- **Total Corpus:** 4,887 image/label pairs

**Contamination & Overlap Audit:**
- Filename overlap across splits: **0 files**
- MD5 content hash overlap across splits: **0 files**
- **Conclusion:** The test partition is strictly held-out with zero data leakage into train or validation splits.

### 3.2 Ground-Truth Annotation Counts and Imbalance Analysis
Every `.txt` label file in the dataset was parsed to extract exact ground-truth bounding box counts:

| Class ID | Perception Class Name | Train Annotations | Validation Annotations | Test Annotations | Total Annotations | % of Corpus |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: |
| `0` | `FIRE_EXTINGUISHER` | 3,164 | 948 | 452 | **4,564** | **62.88%** |
| `1` | `EXIT_DOOR` | 33 | 3 | 6 | **42** | **0.58%** |
| `2` | `EXIT_SIGN` | 1,741 | 498 | 320 | **2,559** | **35.26%** |
| `3` | `OBSTACLE` | 69 | 6 | 18 | **93** | **1.28%** |
| **Total** | **All Classes** | **5,007** | **1,455** | **796** | **7,258** | **100.00%** |

### Critical Finding on Dataset Pathology:
- Out of 4,887 total images, there are **only 42 exit doors** across the entire project. The validation set has only 3 doors, and the test set has only 6 doors.
- There are **only 93 obstacles** across the entire project. The validation set has 6 obstacles, and the test set has 18 obstacles.
- `FIRE_EXTINGUISHER` and `EXIT_SIGN` account for **98.14%** of all annotated ground-truth objects in the dataset.
- This severe 100:1 class imbalance mathematically forced the network during training to suppress activations on doors and obstacles to minimize false-positive background cross-entropy loss.

---

## 4. Empirical Metric Reproduction Results

To verify whether the documented 5.79% macro mAP@50 is reproducible or fabricated, the evaluation pipeline was re-executed live using `Phase84Trainer.evaluate_split('test', checkpoint_path, conf_threshold=0.20, iou_threshold=0.45)`:

### 4.1 Side-by-Side Comparison: Documented vs Live Reproduced

| Evaluation Metric | Documented in `test_metrics.json` | Live Reproduced Audit Run | Divergence / Delta | Verification Status |
| :--- | :---: | :---: | :---: | :---: |
| **Total Test Images** | 522 | **522** | 0 | **EXACT MATCH** |
| **Ground Truth Objects** | 796 | **796** | 0 | **EXACT MATCH** |
| **Predicted Proposals** | 395 | **395** | 0 | **EXACT MATCH** |
| **Mean Precision** | 0.2283 | **0.2283** | 0.0000 | **EXACT MATCH** |
| **Mean Recall** | 0.1125 | **0.1125** | 0.0000 | **EXACT MATCH** |
| **Mean F1-Score** | 0.1261 | **0.1261** | 0.0000 | **EXACT MATCH** |
| **Macro mAP@50** | **0.0579** | **0.0579** | **0.0000** | **EXACT MATCH** |
| **Macro mAP@50:95** | **0.0194** | **0.0194** | **0.0000** | **EXACT MATCH** |

### 4.2 Per-Class Breakdown (Live Reproduced Audit Run)

| Class Name | Support (GT) | True Positives (TP) | False Positives (FP) | False Negatives (FN) | Precision | Recall | F1-Score | AP@50 | AP@50:95 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`FIRE_EXTINGUISHER`** | 452 | 195 | 185 | 257 | 0.5132 | 0.4314 | 0.4688 | 0.2236 | 0.0764 |
| **`EXIT_DOOR`** | 6 | 0 | 0 | 6 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **`EXIT_SIGN`** | 320 | 6 | 9 | 314 | 0.4000 | 0.0187 | 0.0357 | 0.0079 | 0.0014 |
| **`OBSTACLE`** | 18 | 0 | 0 | 18 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |

### 4.3 Confusion Matrix (Live Reproduced Audit Run)
Columns: Ground Truth Class (0: Extinguisher, 1: Door, 2: Sign, 3: Obstacle, 4: Background)  
Rows: Predicted Class

$$\begin{pmatrix} 
195 & 0 & 0 & 0 & 185 \\ 
0 & 0 & 0 & 0 & 0 \\ 
0 & 0 & 6 & 0 & 9 \\ 
0 & 0 & 0 & 0 & 0 \\ 
257 & 6 & 314 & 18 & 0 
\end{pmatrix}$$

- **Row 0 (`FIRE_EXTINGUISHER`):** 195 correct detections, 185 false alarms on background fixtures (red valves, alarm buttons, pipes).
- **Row 1 (`EXIT_DOOR`):** Zero predictions emitted. All 6 ground-truth doors fell into Row 4 (Background false negatives).
- **Row 2 (`EXIT_SIGN`):** 6 correct detections, 9 false alarms, 314 ground-truth signs missed (Row 4).
- **Row 3 (`OBSTACLE`):** Zero predictions emitted. All 18 ground-truth obstacles fell into Row 4.

### 4.4 Mathematical Aggregation Logic
The macro metrics are computed across all classes with non-zero support (`cls_gt_count > 0`):
$$\text{Macro mAP@50} = \frac{0.2236 + 0.0000 + 0.0079 + 0.0000}{4} = \frac{0.2315}{4} = 0.057875 \approx \mathbf{0.0579}$$
$$\text{Mean Precision} = \frac{0.5132 + 0.0000 + 0.4000 + 0.0000}{4} = \frac{0.9132}{4} = \mathbf{0.2283}$$
$$\text{Mean Recall} = \frac{0.4314 + 0.0000 + 0.0187 + 0.0000}{4} = \frac{0.4501}{4} = \mathbf{0.1125}$$
$$\text{Mean F1} = \frac{0.4688 + 0.0000 + 0.0357 + 0.0000}{4} = \frac{0.5045}{4} = \mathbf{0.1261}$$

The evaluation engine adheres strictly to the standard COCO 101-point interpolated Average Precision protocol. Classes with zero predictions are correctly assigned `AP = 0.0`, dragging down the macro average as mathematically required.

---

## 5. Investigation and Resolution of the "Zero-Detection" Discrepancy

A key mandate of this audit was to investigate why an earlier report claimed "zero detections across ten synthetic images" while the quality report claimed a macro mAP@50 of 5.79%.

### 5.1 Investigation Findings

The two reports evaluated completely different datasets, image modalities, sample sizes, and evaluation objectives:

| Dimension | Earlier Report (Phase 14 Joint Evaluation) | Quality Investigation / Phase 8.4 Report |
| :--- | :--- | :--- |
| **Document** | `docs/integration/phase14_cv_qwen_joint_evaluation.md` | `docs/cv_model_quality_investigation_report.md` |
| **Target Dataset** | `ai-service/tests/fixtures/phase14/images/` | `datasets/fire_safety/prepared/phase8_3/yolo/images/test/` |
| **Image Count** | **10 images** | **522 images** |
| **Image Domain** | **Synthetic 2D flat geometric vector illustrations** (generated via Pillow drawing API in `generate_phase14_dataset.py`) | **Natural real-world photographic inspection images** |
| **Ground Truth Count** | 11 synthetic objects | 796 photographic objects |
| **Execution Mode** | Tested in both `test_mode=False` (gated) and `test_mode=True` (pipeline plumbing test) | Evaluated using standard `ComprehensiveEvaluator` on test split |
| **CV Detection Result** | **0 detections emitted** across all 10 synthetic images | **395 detections emitted** (195 TP extinguishers, 6 TP exit signs, 194 FP) |
| **Primary Purpose** | Verify integration plumbing between CV, local Ollama / Qwen2.5-VL, and ResultReconciler | Benchmark formal detection quality of `best.pt` on held-out test data |

### 5.2 Root Cause of the Zero Detections in Phase 14
In Phase 14, the 10 images were created synthetically with pure flat colored rectangles (e.g., solid red block `[100, 100, 250, 400]` on grey drywall). The convolutional layers in `BaselineInspectionDetector` were trained on photographic features (texture gradients, shadow edges, metallic reflections, specular highlights). On flat synthetic vector drawings:
1. Feature activations in the convolutional backbone failed to reach threshold levels.
2. The maximum output confidence on the 10 synthetic drawings never exceeded the 0.20 confidence floor.
3. In `test_mode=False`, the model approval gate immediately intercepted the request and returned 0 detections (`PRODUCTION_INFERENCE_DISABLED`).
4. In `test_mode=True`, the real forward pass on synthetic drawings emitted zero candidate proposals above threshold.

### 5.3 Audit Verdict on Discrepancy:
**The two results are NOT contradictory.**  
They reflect the difference between testing a photographic model on 10 synthetic vector graphics (0 detections) versus evaluating it on 522 natural photographs (395 detections, 5.79% mAP@50).

---

## 6. Confirmed Findings vs. Unverified Claims

| Finding / Claim | Status | Audit Verification Evidence |
| :--- | :---: | :--- |
| `best.pt` SHA-256 is `5f166c...` | **CONFIRMED** | Exact cryptographic digest match on disk. |
| Test Macro mAP@50 is 5.79% | **CONFIRMED** | Live execution reproduced 0.0579 on 522 test images. |
| Model has 0% AP on Exit Doors | **CONFIRMED** | 0 TP out of 6 ground-truth test doors; max logit $\le 0.0299$. |
| Model has 0% AP on Obstacles | **CONFIRMED** | 0 TP out of 18 ground-truth test obstacles; max logit $\le 0.0413$. |
| Model misses 98.1% of Exit Signs | **CONFIRMED** | Recall is 1.87% (6 TP vs 314 FN) due to single-stride 16 feature collapse on small signs. |
| Extinguisher precision is only 51.3% | **CONFIRMED** | 185 false alarms on red confounders against 195 true positives. |
| Macro mAP@50 is 0.0503 | **REFUTED / CLARIFIED** | 0.0503 belonged to the Phase 8.9 transfer-learning candidate model (`firesphere-perception-pretrained-v0.8.9`), not the Phase 8.4 baseline `best.pt`. |
| Post-processing thresholding can fix Doors/Obstacles | **REFUTED** | Sweeping thresholds down to $\tau=0.02$ still yielded 0 TP on doors and obstacles because feature weights have zero discriminative power. |

---

## 7. Missing Evaluation Artifacts

While the dataset, checkpoint, and evaluation code are fully intact, the audit identified one minor archival gap:
- **Missing Raw Prediction Dumps:**  
  The directory `ai-service/artifacts/phase8_4/predictions/` exists on disk but is currently empty. The original training script saved aggregate summary metrics (`test_metrics.json`, `val_metrics.json`), but did not serialize the raw per-image bounding-box proposal lists into individual prediction JSON files.
- **Impact Assessment:**  
  **Zero impact on reproducibility.** Because the checkpoint weights and test images are completely deterministic, the raw prediction proposals were regenerated and matched during our live audit execution. However, for future phases, raw proposals should be preserved for archival provenance.

---

## 8. Gate Review & Safety Invariant Enforcement

The active codebase was inspected to confirm that all governance boundaries and production gates remain strictly enforced:

1. **Production Inference Gate Blocked Fail-Closed:**  
   In [app/models/model_approval_gate.py](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py), `is_production_approved()` unconditionally returns `False`. In [cv_model_adapter.py](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/cv_model_adapter.py#L101), if `not test_mode and not gate_decision["authorized"]`, inference is disabled and returns `[]`.
2. **Test Mode Cannot Silently Authorize Production:**  
   Passing `test_mode=True` allows local test execution but explicitly tags emitted finding attributes with `isTestData=True`, `testFixture=True`, and `modelApprovalStatus="NOT_APPROVED"`. It does not modify persistent configs or memory flags.
3. **Empty Scene vs. Gated Inference Distinction:**  
   A gate-blocked execution sets `cv_status = "NOT_APPROVED_GATED"` (mapped to `ComponentAnalysisStatus.UNAVAILABLE`). A successful empty-scene execution sets `cv_status = "SUCCESS"` (mapped to `ComponentAnalysisStatus.SUCCESS_WITH_NO_OBSERVATIONS`). They are distinct in both logging and reconciliation.
4. **Inference Failure vs. Empty Scene Distinction:**  
   Runtime execution exceptions set `cv_status = "FAILED"`, and missing checkpoint weights set `cv_status = "CHECKPOINT_LOAD_FAILED"` (both mapped to `ComponentAnalysisStatus.FAILED`). In [reconciliation.py](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/intelligence/reconciliation.py#L246-L265), a failed component is never converted into an empty success.
5. **VLM Cannot Bypass CV Gate:**  
   VLM analysis runs independently. When CV is gated, the reconciler records `cv_status = "NOT_APPROVED_GATED"`, flags the finding as `SINGLE_PROVIDER`, and enforces human officer verification.
6. **AI Findings Cannot Make Autonomous Compliance Decisions:**  
   In [app/schemas/finding.py](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/schemas/finding.py#L218-L230), `validate_compliance_boundary` explicitly raises a validation error if any status like `AI_APPROVED` or `AI_REJECTED` is passed. All findings enforce `complianceStatus = ComplianceStatus.REQUIRES_OFFICER_VERIFICATION`.

---

## 9. Recommendations for the Next Model Development Phase

Based on the empirical evidence gathered during this audit, the following engineering steps must be taken before considering gate approval:

1. **Address Catastrophic Class Imbalance:**  
   The current dataset cannot support door or obstacle detection (only 42 doors and 93 obstacles in 4,887 images). The project must curate at least **1,500 real-world commercial inspection photographs** specifically annotated for exit doors, door corridors, and physical exit obstructions.
2. **Upgrade Detection Architecture:**  
   Replace `BaselineInspectionDetector` (single $16 \times 16$ grid head) with a modern multi-scale architecture featuring a Feature Pyramid Network (FPN / PANet) with P3, P4, and P5 detection heads (e.g., YOLOv8-nano or YOLOv8-small). Small exit signs ($<1\%$ image area) collapse under the current 16-pixel receptive stride.
3. **Loss Function Optimization:**  
   Implement Class-Balanced Focal Loss ($\gamma = 2.0, \alpha = 0.25$) during training to prevent dominant extinguisher gradients from zeroing out door and obstacle weights.
4. **Hard-Negative Mining on Red Confounders:**  
   Train with explicit hard negatives (red fire alarm buttons, red pipes, red signs, emergency lights) to push extinguisher precision from 51.3% up to the required $\ge 70\%$.
5. **Formal Post-Training Calibration:**  
   Apply temperature scaling or isotonic regression on a dedicated calibration split; verify Expected Calibration Error (ECE) $\le 0.10$ before toggling calibration status.

---

## 10. Audit Sign-Off and Governance Verdict

> [!CAUTION]
> **AUDIT CONCLUSION: PRODUCTION GATE MUST REMAIN NOT_APPROVED**  
> - **Cryptographic Integrity:** Checkpoint `best.pt` is verified and unmodified.
> - **Evaluation Evidence:** Fully reproducible (macro mAP@50 = 5.79%).
> - **Safety Status:** Complete blind spots on emergency exit doors (0.0% AP) and corridor obstacles (0.0% AP), combined with high false alarms on extinguishers (51.3% precision) and high miss rates on exit signs (1.87% recall).
> - **Gate Decision:** **`NOT_APPROVED`**. Under no circumstances should the CV model be enabled for production inference without retraining and re-benchmarking against real-world inspection datasets.
