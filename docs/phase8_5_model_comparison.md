# FireSphere.ai — Phase 8.5 Model Comparison & Evaluation Report

**Document Version:** `1.0.0-phase8.5`  
**Evaluation Scope:** Baseline (`firesphere-perception-baseline-v0.8.0`) vs Improved (`firesphere-perception-improved-v0.8.5`)  
**Evaluation Dataset:** Phase 8.3 Held-Out Test Set (`datasets/fire_safety/prepared/phase8_3/yolo/`)  
**Test Set Images:** 522 images (100% paired with ground truth)  
**Total Ground Truth Objects:** 796 objects (452 extinguishers, 320 signs, 18 obstacles, 6 doors)  
**Evaluation Protocol:** Fixed evaluation protocol at IoU threshold = 0.50, Confidence threshold = 0.15  
**Final Evaluation Gate:** `TEST_SET_USED_FOR_FINAL_EVALUATION = TRUE`  
**Execution Timestamp:** `2026-10-08T14:55:00Z`  
**Date:** 2026-10-08  

---

## 1. Overview of the Controlled Experiment

Phase 8.5 executed a strictly controlled model improvement experiment to test whether the poor baseline results of Phase 8.4 were driven by architectural resolution/scale limitations or genuine data scarcity.

### Control Conditions Preserved
- **Identical Dataset:** 100% identical Phase 8.3 prepared dataset (3,377 train, 988 val, 522 test). Zero sample additions, zero synthetic generation.
- **Identical Vocabulary:** 4 frozen perception classes only:
  - `0: FIRE_EXTINGUISHER`
  - `1: EXIT_DOOR`
  - `2: EXIT_SIGN`
  - `3: OBSTACLE`
- **Identical Evaluation Split:** Evaluated strictly once on the held-out test split after freezing the best validation checkpoint (`best.pt`, Epoch 10, Val Loss = 1.50467).

---

## 2. Direct Baseline vs Improved Metric Comparison

| Evaluation Metric | Phase 8.4 Baseline | Phase 8.5 Improved (FPN) | Engineering Delta ($\Delta$) | Empirical Verdict |
| :--- | :---: | :---: | :---: | :---: |
| **Mean Precision** | 0.2283 | 0.0748 | -0.1535 | **REGRESSED** |
| **Mean Recall** | 0.1125 | 0.0597 | -0.0528 | **REGRESSED** |
| **mAP@0.50** | 0.0579 | 0.0178 | -0.0401 | **REGRESSED** |
| **mAP@0.50:0.95** | 0.0194 | 0.0076 | -0.0118 | **REGRESSED** |

---

## 3. Class-by-Class Comparative Analysis

### 3.1 `FIRE_EXTINGUISHER` (Support: 452 test objects)
- **Phase 8.4 Baseline:** Precision = 0.5132 | Recall = 0.4314 | AP@0.50 = 0.2236
- **Phase 8.5 Improved:** Precision = 0.2992 | Recall = 0.2389 | AP@0.50 = 0.0711 (TP: 108, FP: 253, FN: 344)
- **Delta:** $\Delta \text{AP@0.50} = -0.1525$ (**REGRESSED**)
- **Root Cause:** In Phase 8.4's single-scale model, 768 anchors were evaluated per image. In the 3-scale FPN detector, anchor count surged to 4,032 ($5.25\times$). Without pre-trained backbone weights or dense anchor supervision, the foreground-to-background ratio dropped drastically, causing higher false positive rates and suppressed confidence scores.

### 3.2 `EXIT_SIGN` — Dedicated Small-Object Resolution Analysis (Support: 320 test objects)
- **Phase 8.4 Baseline:** Precision = 0.4000 | Recall = 0.0187 (1.87%) | AP@0.50 = 0.0079 (0.79%)
- **Phase 8.5 Improved:** Precision = 0.0000 | Recall = 0.0000 (0.00%) | AP@0.50 = 0.0000 (0.00%) (TP: 0, FP: 1, FN: 320)
- **Delta:** $\Delta \text{AP@0.50} = -0.0079$ (**REGRESSED**)
- **Empirical Determination:** **`EXIT_SIGN_REMAINS_UNSOLVED`**
- **Deep-Dive Finding:** While the $P_3$ high-resolution head ($32 \times 32$, stride 8) technically provides the spatial receptive bins required for small objects ($<16\text{px}$), training a 3-scale pyramid from scratch on only 1,741 training sign boxes caused anchor dilution. The network's classification logits on small signs did not breach the 0.15 confidence threshold on the held-out test set. Therefore, architecture changes alone without pre-training or specialized focal loss cannot overcome the small-object barrier.

### 3.3 `EXIT_DOOR` — Data Scarcity Diagnostic (Support: 6 test objects)
- **Phase 8.4 Baseline:** Precision = 0.0000 | Recall = 0.0000 | AP@0.50 = 0.0000
- **Phase 8.5 Improved:** Precision = 0.0000 | Recall = 0.0000 | AP@0.50 = 0.0000 (TP: 0, FP: 0, FN: 6)
- **Delta:** $\Delta \text{AP@0.50} = 0.0000$ (**UNCHANGED**)
- **Empirical Determination:** **`DATA_LIMITED_AND_NOT_RELIABLE`**
- **Root Cause:** With only 28 training boxes across 14 unique physical scenes, the model possesses near-zero sample diversity. Adding the large $P_5$ head ($8 \times 8$) was mathematically sound, but deep neural networks cannot generalize an entire structural category from 14 physical examples.

### 3.4 `OBSTACLE` — Intra-Class Heterogeneity Diagnostic (Support: 18 test objects)
- **Phase 8.4 Baseline:** Precision = 0.0000 | Recall = 0.0000 | AP@0.50 = 0.0000
- **Phase 8.5 Improved:** Precision = 0.0000 | Recall = 0.0000 | AP@0.50 = 0.0000 (TP: 0, FP: 0, FN: 18)
- **Delta:** $\Delta \text{AP@0.50} = 0.0000$ (**UNCHANGED**)
- **Empirical Determination:** **`DATA_LIMITED_AND_NOT_RELIABLE`**
- **Root Cause:** Obstacles encompass pallets, furniture, carts, bins, and boxes. 63 training boxes across 30 scenes cannot represent open-vocabulary obstruction geometry.

---

## 4. Key Experimental Conclusions & Governance Decision

1. **Phase 8.4 Remains the Superior Baseline Control:** Phase 8.4 (`firesphere-perception-baseline-v0.8.0`) achieved higher mAP@0.50 (0.0579 vs 0.0178) and higher extinguisher AP@0.50 (0.2236 vs 0.0711) due to tighter anchor coupling. The Phase 8.5 experiment is recorded honestly as a regression.
2. **Architecture Alone Cannot Overcome Data Starvation:** Multi-scale FPN increases parameter count ($1.2\text{M} \to 3.4\text{M}$) and anchors ($768 \to 4,032$), which exacerbates training difficulty when trained from scratch without large-scale pre-training or substantially larger datasets.
3. **Formal Governance Verdict:** **`DATA_EXPANSION_REQUIRED`**
   - Targeted dataset expansion is strictly mandatory for `EXIT_DOOR` and `OBSTACLE`.
   - Pretrained backbone weights or specialized dense focal loss strategies must be investigated in future phases.
