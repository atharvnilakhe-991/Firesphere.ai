# FireSphere.ai — Phase 8.5 Controlled Model Improvement Experiment Report

**Document Version:** `1.0.0-phase8.5`  
**Model Identifier:** `firesphere-perception-improved-v0.8.5`  
**Model Architecture:** `MultiScaleInspectionDetector` (PyTorch Native Multi-Scale FPN)  
**Governing Dataset:** `datasets/fire_safety/prepared/phase8_3/yolo/data.yaml`  
**Dataset Version:** `1.0.0-phase8.3`  
**Execution Timestamp:** `2026-10-08T14:55:00Z`  
**Training Status:** **TRAINED_EXPERIMENT**  
**Production Approval:** **NOT_APPROVED**  
**Calibration Status:** **NOT_CALIBRATED**  
**Governance Recommendation:** **DATA_EXPANSION_REQUIRED**  

---

## 1. Executive Summary

Phase 8.5 executes the first controlled model improvement experiment on the FireSphere.ai visual perception pipeline. In Phase 8.4, the baseline detector (`firesphere-perception-baseline-v0.8.0`) revealed severe recall degradation on `EXIT_SIGN` (1.87% recall, 0.79% AP@0.50) and zero detections on `EXIT_DOOR` and `OBSTACLE`.

Phase 8.5 conducted a strictly controlled architectural intervention:
**Can multi-scale feature pyramids ($P_3, P_4, P_5$) resolve the resolution and scale bottlenecks without altering the frozen dataset?**

### Key Governance Principles Enforced
1. **Zero Dataset Alteration:** Neither the canonical dataset (6,471 images/labels) nor the Phase 8.3 prepared YOLO dataset (4,887 images, 7,258 boxes) was modified, relabeled, oversampled, or augmented with synthetic data.
2. **Phase 8.4 Control Model Preserved:** The baseline model artifact (`best.pt`, SHA256: `5f166c4703e08f2379524fa9cb077d7f267a54a01c45c117b36f1c48ea92a83f`) was preserved completely untouched in `ai-service/artifacts/phase8_4/`.
3. **Four Frozen Perception Classes Only:**
   - `0: FIRE_EXTINGUISHER`
   - `1: EXIT_DOOR`
   - `2: EXIT_SIGN`
   - `3: OBSTACLE`
4. **Finding Decoupling:** `EMERGENCY_EXIT_OBSTRUCTED` was strictly excluded from perception model training; it remains a downstream Stage-2 spatial reasoning finding requiring inspecting officer verification.
5. **No Autonomous Legal Decisions:** The model produces visual bounding boxes and confidence scores only; it does not classify statutory compliance (`AI_APPROVED` / `AI_REJECTED`) or compute certified clearance dimensions.

---

## 2. Experimental Model Architecture & Optimization Regime

To address the diagnosed single-scale grid limitations of Phase 8.4 ($16 \times 16$ grid, 768 anchors), Phase 8.5 developed `MultiScaleInspectionDetector`:

- **Backbone Extractor:** 4-stage convolutional network with residual bottlenecks extracting multi-resolution feature maps ($C_2, C_3, C_4, C_5$).
- **FPN Neck:** Lateral $1\times 1$ convolutions, nearest-neighbor $2\times$ upsampling, and $3\times 3$ smoothing convolutions generating three feature pyramid representations:
  - **$P_3$ ($32 \times 32 \times 96$, Stride 8):** Dedicated to small objects (area $< 0.02$). Anchors: $[0.03, 0.03], [0.06, 0.06], [0.08, 0.12]$.
  - **$P_4$ ($16 \times 16 \times 96$, Stride 16):** Dedicated to medium objects ($0.02 \le \text{area} \le 0.15$). Anchors: $[0.12, 0.18], [0.20, 0.35], [0.32, 0.45]$.
  - **$P_5$ ($8 \times 8 \times 96$, Stride 32):** Dedicated to large objects (area $> 0.15$). Anchors: $[0.35, 0.70], [0.55, 0.85], [0.80, 0.90]$.
- **Total Anchors:** 4,032 anchors ($5.25\times$ expansion over Phase 8.4's 768 anchors).
- **Parameters:** ~3.42 Million parameters.
- **Optimization:** 10 full epochs, AdamW optimizer, cosine learning-rate decay ($1 \times 10^{-3} \to 1 \times 10^{-4}$), batch size 32, deterministic seed 42.

---

## 3. Training Progression & Loss Convergence

Training ran across 10 full epochs on CPU (Intel64 AMD64):

| Epoch | Learning Rate | Train Loss | Box Loss | Objectness Loss | Class Loss | Validation Loss | Duration | Checkpoint Saved |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | 0.001000 | 5.94142 | 1.73159 | 1.10985 | 0.91226 | 4.05556 | 221.99s | `last.pt`, `best.pt` |
| **2** | 0.000978 | 3.68731 | 1.39572 | 0.27028 | 0.41707 | 2.53695 | 170.76s | `last.pt`, `best.pt` |
| **3** | 0.000914 | 2.91112 | 1.17601 | 0.11819 | 0.29394 | 2.19188 | 165.79s | `last.pt`, `best.pt` |
| **4** | 0.000815 | 2.32148 | 0.99624 | 0.07842 | 0.16706 | 1.95411 | 161.07s | `last.pt`, `best.pt` |
| **5** | 0.000689 | 1.82974 | 0.80992 | 0.06259 | 0.09820 | 1.82326 | 157.51s | `last.pt`, `best.pt` |
| **6** | 0.000550 | 1.64798 | 0.74470 | 0.05401 | 0.06971 | 1.70257 | 161.42s | `last.pt`, `best.pt` |
| **7** | 0.000411 | 1.35468 | 0.62368 | 0.04889 | 0.03895 | 1.54335 | 160.79s | `last.pt`, `best.pt` |
| **8** | 0.000285 | 1.11856 | 0.51819 | 0.04719 | 0.02332 | 1.55074 | 157.60s | `last.pt` |
| **9** | 0.000186 | 0.94933 | 0.43828 | 0.04635 | 0.01761 | 1.53129 | 131.86s | `last.pt` |
| **10** | **0.000122** | **0.79370** | **0.36559** | **0.04562** | **0.01127** | **1.50467** | 132.15s | `last.pt`, **`best.pt` (SELECTED)** |

- **Total Training Duration:** 1,629.53 seconds (~27.2 minutes).
- **Best Validation Checkpoint:** Selected at **Epoch 10** with validation loss of **1.50467**.

---

## 4. Empirical Evaluation on Held-Out Test Set

The frozen best checkpoint (`best.pt`) was evaluated strictly once on the untouched Phase 8.3 test set (522 images, 796 ground truth objects):

### Overall Metrics
- **Mean Precision:** 0.0748 (Phase 8.4: 0.2283, $\Delta = -0.1535$, **REGRESSED**)
- **Mean Recall:** 0.0597 (Phase 8.4: 0.1125, $\Delta = -0.0528$, **REGRESSED**)
- **Mean F1:** 0.0664
- **mAP@0.50:** 0.0178 (Phase 8.4: 0.0579, $\Delta = -0.0401$, **REGRESSED**)
- **mAP@0.50:0.95:** 0.0076 (Phase 8.4: 0.0194, $\Delta = -0.0118$, **REGRESSED**)

### Per-Class Test Performance
- **`FIRE_EXTINGUISHER` (Support: 452):** TP = 108, FP = 253, FN = 344 | Precision = 0.2992, Recall = 0.2389, AP@0.50 = 0.0711 ($\Delta = -0.1525$, **REGRESSED**)
- **`EXIT_DOOR` (Support: 6):** TP = 0, FP = 0, FN = 6 | Precision = 0.0000, Recall = 0.0000, AP@0.50 = 0.0000 ($\Delta = 0.0000$, **UNCHANGED / DATA_LIMITED_AND_NOT_RELIABLE**)
- **`EXIT_SIGN` (Support: 320):** TP = 0, FP = 1, FN = 320 | Precision = 0.0000, Recall = 0.0000, AP@0.50 = 0.0000 ($\Delta = -0.0079$, **REGRESSED / EXIT_SIGN_REMAINS_UNSOLVED**)
- **`OBSTACLE` (Support: 18):** TP = 0, FP = 0, FN = 18 | Precision = 0.0000, Recall = 0.0000, AP@0.50 = 0.0000 ($\Delta = 0.0000$, **UNCHANGED / DATA_LIMITED_AND_NOT_RELIABLE**)

---

## 5. Engineering Root Cause Analysis

1. **Anchor Dilution & Class Imbalance:** Adding 3 pyramid heads increased spatial anchors from 768 to 4,032 ($5.25\times$). Without pre-trained weights, the model had to learn feature representations from scratch across $5.25\times$ more background anchors. This severe background-to-foreground imbalance suppressed the classification logits, causing test predictions to fall below the 0.15 confidence threshold.
2. **Data Starvation Overcomes Architecture:** Expanding the receptive field with $P_5$ ($8 \times 8$) was architecturally appropriate for full-height doors and floor obstacles. However, because `EXIT_DOOR` has only 28 training boxes (14 scenes) and `OBSTACLE` has 63 training boxes (30 scenes), no architecture can generalize these concepts without diverse real-world training data.
3. **Control Baseline Superiority:** Phase 8.4 (`firesphere-perception-baseline-v0.8.0`) remains the superior control model. Phase 8.5 strictly reports this regression in full compliance with scientific integrity rules.

---

## 6. Strategic Governance Decision

**Final Decision:** **`DATA_EXPANSION_REQUIRED`**

1. **Baseline Preservation:** Phase 8.4 remains the authoritative reference baseline. Phase 8.5 is archived as a controlled experimental trial (`TRAINED_EXPERIMENT`, `PRODUCTION_STATUS = NOT_APPROVED`).
2. **Next Steps:** Proceeding to Phase 8.6 requires targeted physical dataset expansion for `EXIT_DOOR` and `OBSTACLE`, combined with pre-trained feature backbones or dense focal loss for small `EXIT_SIGN` localization.
