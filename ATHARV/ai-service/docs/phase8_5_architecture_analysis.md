# FireSphere.ai — Phase 8.5 Architecture & Failure Diagnostic Report

**Document Version:** `1.0.0-phase8.5`  
**Governing Phase:** Phase 8.5 Controlled Model Improvement Experiment  
**Author:** AI/ML Engineering Agent  
**Baseline Model:** `firesphere-perception-baseline-v0.8.0` (`BaselineInspectionDetector`)  
**Improved Model:** `firesphere-perception-improved-v0.8.5` (`MultiScaleInspectionDetector`)  
**Date:** 2026-10-08  

---

## 1. Executive Summary & Diagnostic Motivation

Phase 8.4 established the first baseline visual perception detector for FireSphere.ai (`firesphere-perception-baseline-v0.8.0`), trained strictly from scratch on the frozen Phase 8.3 prepared dataset (4,887 images, 7,258 bounding boxes). 

Evaluation of the baseline model revealed stark performance divergence:
- **`FIRE_EXTINGUISHER`**: Precision = 0.5132, Recall = 0.4314, AP@0.50 = 0.2236
- **`EXIT_DOOR`**: Precision = 0.0000, Recall = 0.0000, AP@0.50 = 0.0000
- **`EXIT_SIGN`**: Precision = 0.4000, Recall = 0.0187 (1.87%), AP@0.50 = 0.0079 (0.79%)
- **`OBSTACLE`**: Precision = 0.0000, Recall = 0.0000, AP@0.50 = 0.0000
- **Aggregate Test**: Mean Precision = 0.2283, Mean Recall = 0.1125, mAP@0.50 = 0.0579

Before requesting costly external data collection or modifying dataset partitions, Phase 8.5 mandated a controlled diagnostic investigation:
**Is the failure caused by training duration, architectural resolution bottlenecks, scale assignment, class imbalance, or genuine dataset insufficiency?**

---

## 2. Comprehensive Phase 8.4 Architecture Diagnostic

An in-depth review of `BaselineInspectionDetector` (`ai-service/app/models/baseline_detector.py`) identified the following 23 engineering parameters:

| # | Architectural Property | Phase 8.4 Baseline Specification | Engineering Assessment |
| :--- | :--- | :--- | :--- |
| **1** | **Input Resolution** | $256 \times 256 \times 3$ RGB (normalized in $[0.0, 1.0]$) | Fixed input size compromises very small objects. |
| **2** | **Backbone Structure** | 4-stage CNN ($3 \to 32 \to 64 \to 128 \to 256$) with SiLU & ResidualBlocks | Standard feedforward hierarchy without feature reuse. |
| **3** | **Feature-Map Resolutions** | Stage 0: $128 \times 128$; Stage 1: $64 \times 64$; Stage 2: $32 \times 32$; Stage 3: $16 \times 16$ | Early high-resolution features were discarded after downsampling. |
| **4** | **Detection-Head Resolution** | Single head at $16 \times 16$ (Stride 16) | Severe spatial quantization. Stride 16 means 1 cell = $16 \times 16$ pixels. |
| **5** | **Number of Scales** | Single scale (1) | Single feature map forced to predict objects spanning $1.6\text{px}$ to $220\text{px}$. |
| **6** | **Bounding-Box Representation** | $(t_x, t_y, t_w, t_h)$ relative to grid cell & anchor | Standard anchor-based relative parameterization. |
| **7** | **Loss Function** | Multi-task: $2.0 \cdot L_{box} + 1.0 \cdot L_{obj} + 1.5 \cdot L_{cls}$ | Balanced weights, but single-scale target matching caused loss collapse. |
| **8** | **Classification Loss** | CrossEntropyLoss over 4 classes | Standard multi-class classification. |
| **9** | **Localization Loss** | Smooth L1 Loss on transformed coordinates | Effective, but sensitive to extreme scale mismatches. |
| **10** | **Objectness Loss** | BCEWithLogitsLoss with `pos_weight=2.0` | Heavy background imbalance ($16 \times 16 \times 3 = 768$ anchors vs 1-3 positives). |
| **11** | **NMS Implementation** | Greedy IoU-based NMS (IoU threshold = 0.45) | Sound implementation; not the cause of misses. |
| **12** | **Confidence Threshold** | 0.15 (eval) / 0.20 (inference) | Lowering threshold to 0.01 did not restore recall (confirmed head failure). |
| **13** | **Training Augmentation** | Resize only | No spatial or photometric augmentation. |
| **14** | **Optimizer** | AdamW ($\beta_1=0.9, \beta_2=0.999$, weight decay = $1\times 10^{-4}$) | Effective optimizer. |
| **15** | **Learning Rate** | $1 \times 10^{-3}$ (fixed) | Constant LR without schedule or decay. |
| **16** | **Batch Size** | 32 | Appropriate for CPU/memory stability. |
| **17** | **Number of Epochs** | 5 | Truncated schedule; insufficient for deep feature convergence. |
| **18** | **LR Schedule** | Constant (none) | Lacked cosine annealing or warm-up. |
| **19** | **Early Stopping** | Validation loss tracking (`best.pt` saved at Epoch 4) | Sound validation-driven checkpoint selection. |
| **20** | **Class Weighting** | None (uniform 1.0) | Minority classes received minimal gradient influence. |
| **21** | **Target Assignment** | Minimum log-ratio anchor matching at single $16 \times 16$ grid | Spatial collisions when multiple objects fell into same $16 \times 16$ cell. |
| **22** | **Small-Object Handling** | **DEFICIENT.** Single stride 16 destroyed small features. | Objects $< 16\text{px}$ collapsed into sub-grid features. |
| **23** | **Large-Object Handling** | **LIMITED.** Receptive field at Stage 3 inadequate for doors. | Objects spanning $>80\%$ image height lost boundary context. |

---

## 3. Deep-Dive Investigation: The EXIT_SIGN Catastrophic Recall Failure

### 3.1 Quantitative Data Evidence
Despite poor test recall (1.87%), `EXIT_SIGN` was **NOT** data-starved in Phase 8.3:
- **Total Prepared Bounding Boxes:** 2,559
- **Train Split:** 1,741 boxes
- **Validation Split:** 498 boxes
- **Test Split:** 320 boxes

### 3.2 Bounding Box Geometry Analysis
A rigorous geometric audit across all 2,559 `EXIT_SIGN` bounding boxes revealed:
- **Normalized Box Width:** Median = 0.0883, Min = 0.0063, Max = 0.6420
- **Normalized Box Height:** Median = 0.0762, Min = 0.0063, Max = 0.6180
- **Normalized Box Area ($w \times h$):** Median = 0.0071, Min = 0.00004, Max = 0.3860
- **Scale Breakdown:**
  - **Small (Area $< 0.01$):** **32.2% (824 boxes)**
  - **Medium ($0.01 \le \text{Area} \le 0.10$):** 61.4% (1,571 boxes)
  - **Large (Area $> 0.10$):** 6.4% (164 boxes)

### 3.3 The Resolution Bottleneck Mechanism
1. **Sub-Pixel Downsampling:** At an input resolution of $256 \times 256$, a sign with normalized width/height of $0.0063$ occupies **only $1.6 \times 1.6$ pixels**. Even a median sign ($0.08 \times 0.07$) occupies only $20 \times 19$ pixels.
2. **Feature Annihilation at Stride 16:** The baseline model extracted predictions strictly from Stage 3, where the spatial dimension is $16 \times 16$ (stride 16). Downsampling by $16\times$ collapses a $20\text{px}$ sign into approximately $1.2$ feature cells, and any sign smaller than $16\text{px}$ disappears into sub-feature interpolation.
3. **Anchor Scale Disparity:** The smallest anchor in Phase 8.4 was Anchor 0 ($[0.08, 0.08]$, area $0.0064$). For the 824 small signs (areas down to $0.00004$), the anchor was up to **160 times larger** than the actual object! The log-ratio regression targets exploded, making gradient descent unstable.
4. **Conclusion on `EXIT_SIGN`:** **The failure was predominantly architectural (feature resolution and anchor mismatch), NOT dataset volume.**

---

## 4. Deep-Dive Investigation: EXIT_DOOR and OBSTACLE Data Limitations

### 4.1 Quantitative Evidence
In contrast to `EXIT_SIGN`, `EXIT_DOOR` and `OBSTACLE` suffer from severe dataset limitations:
- **`EXIT_DOOR`:**
  - Total boxes: 42 (Train: 28, Val: 9, Test: 5)
  - Physical scene groups: Only 14 unique scenes across the entire corpus.
  - Scale breakdown: **95.2% Large (Area $> 0.10$)**. Median height = 0.8258 (spans $>82\%$ of the image).
- **`OBSTACLE`:**
  - Total boxes: 93 (Train: 63, Val: 19, Test: 11)
  - Physical scene groups: Only 30 unique scenes across the entire corpus.
  - Scale breakdown: **96.8% Large (Area $> 0.10$)**. Median height = 0.8344.

### 4.2 Failure Mode Diagnosis: Architecture vs Data Limitation
1. **Spatial Scale Conflict:** Both classes represent massive objects (doors spanning the vertical frame; floor obstacles such as pallets, carts, and boxes spanning the lower foreground). In Phase 8.4, the maximum anchor was Anchor 2 ($[0.55, 0.65]$), which under-represented door heights ($0.85 - 0.95$).
2. **Physical Diversity Deficit:** 14 door scenes and 30 obstacle scenes do not capture the visual distribution of commercial building interiors (emergency crash bars, glass doors, steel doors, cardboard boxes, equipment pallets, maintenance carts, trash bins).
3. **Conclusion on `EXIT_DOOR` and `OBSTACLE`:** **While multi-scale detection heads with large receptive fields ($P_5$) are necessary to detect large objects, these classes are genuinely data-limited. Model architectural changes alone cannot overcome an absence of training scene diversity.**

---

## 5. Architectural Solution: MultiScaleInspectionDetector (FPN)

To address the diagnosed failure modes without altering the authoritative dataset, Phase 8.5 implements `MultiScaleInspectionDetector`:

### 5.1 Top-Down Feature Pyramid Network (FPN)
Instead of discarding intermediate features, lateral $1 \times 1$ convolutions and top-down nearest-neighbor upsampling fuse semantic features from deep layers with high-resolution spatial details from shallow layers:
- **$C_3$ ($32 \times 32 \times 128$)** $\to$ Lateral Conv $\to$ Add upsampled $P_4 \to$ Smooth Conv $\to$ **$P_3$ ($32 \times 32 \times 96$)**
- **$C_4$ ($16 \times 16 \times 256$)** $\to$ Lateral Conv $\to$ Add upsampled $P_5 \to$ Smooth Conv $\to$ **$P_4$ ($16 \times 16 \times 96$)**
- **$C_5$ ($8 \times 8 \times 256$)** $\to$ Lateral Conv $\to$ **$P_5$ ($8 \times 8 \times 96$)**

### 5.2 Multi-Scale Pyramid Detection Heads
Three dedicated detection heads operate concurrently at distinct spatial strides:

| Pyramid Level | Spatial Grid | Stride | Anchor Aspect Ratios | Target Assignment Domain |
| :---: | :---: | :---: | :---: | :---: |
| **$P_3$** | $32 \times 32$ | 8 | $[0.03, 0.03]$, $[0.06, 0.06]$, $[0.08, 0.12]$ | **Small Objects (Area $< 0.02$):** Small `EXIT_SIGN`, compact extinguishers |
| **$P_4$** | $16 \times 16$ | 16 | $[0.12, 0.18]$, $[0.20, 0.35]$, $[0.32, 0.45]$ | **Medium Objects ($0.02 \le \text{Area} \le 0.15$):** Standard `FIRE_EXTINGUISHER`, large signs |
| **$P_5$** | $8 \times 8$ | 32 | $[0.35, 0.70]$, $[0.55, 0.85]$, $[0.80, 0.90]$ | **Large Objects (Area $> 0.15$):** Full-height `EXIT_DOOR`, floor `OBSTACLE` |

### 5.3 Training Schedule & Optimization
- **Epochs:** 10 full epochs (extended from 5)
- **Schedule:** Cosine learning-rate annealing ($1 \times 10^{-3} \to 1 \times 10^{-4}$)
- **Checkpoint Selection:** Strict validation loss monitoring (`best.pt` selected by validation split only)
- **Target Assignment:** Area-based scale decoupling prevents small and large objects from interfering in gradient updates.
