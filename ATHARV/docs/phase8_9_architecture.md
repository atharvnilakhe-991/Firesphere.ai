# FireSphere.ai — Phase 8.9 Pretrained Transfer-Learning Architecture Specification

**Document Version:** `1.0.0-phase8.9`  
**Governing Phase:** Phase 8.9 Pretrained Transfer-Learning Experiment & Controlled Evaluation  
**Author:** AI/ML Engineering Agent  
**Candidate Model Architecture:** `PretrainedInspectionDetector` (MobileNetV2 / ResNet18 Backbone + Decoupled Bi-Scale Neck)  
**Date:** 2026-10-09  
**Status:** `ARCHITECTURAL_SPECIFICATION_LOCKED`  

---

## 1. Executive Summary & Design Motivation

Phase 8.8 demonstrated that training a lightweight CNN from scratch on minority-class data expansion causes severe **representation dilution**:
- `EXIT_DOOR` training scene diversity surged from 14 to 529 physical scenes (+37.8x expansion).
- However, the scratch-trained detector reallocated shared convolution filters toward door boundaries, causing `FIRE_EXTINGUISHER` AP@50 to collapse by **-47.9%** (from 0.1973 to 0.1028) on the held-out test set.
- `EXIT_SIGN` recall remained bounded by stride-16 grid quantization.
- `OBSTACLE` remained data-limited.

Phase 8.9 formulates the **Pretrained Transfer-Learning Hypothesis**:
> *"A pretrained visual backbone trained on rich visual corpuses (e.g. ImageNet-1k) provides generic edge, texture, and object-part representations that prevent established classes from collapsing when new minority classes are introduced."*

---

## 2. Lessons from Past Phases Informing Phase 8.9 Architecture

| Historical Phase | Architecture Tested | Key Empirical Finding | Lesson Applied to Phase 8.9 |
| :--- | :--- | :--- | :--- |
| **Phase 8.4 Baseline** | `BaselineInspectionDetector` (Single Stride-16 head) | Extinguisher AP@50 reached 0.2236, but small exit signs and doors failed. | Baseline provides strong anchor-coupling for medium objects; single scale fails on extreme aspect ratios. |
| **Phase 8.5 Regressed** | `MultiScaleInspectionDetector` (3-scale FPN: P3, P4, P5; 4,032 anchors) | Severe anchor dilution and loss collapse when trained from scratch. | **Do NOT use 3 indiscriminate scales from scratch.** Decouple scale heads and restrict anchors to prevent foreground dilution. |
| **Phase 8.6 Diagnosis** | Diagnostic pipeline audit | Loss, gradients, target assignment, and decoding are mathematically sound. | Core loss and decoding machinery should be preserved intact. |
| **Phase 8.8 Regressed** | `BaselineInspectionDetector` on audited expanded data | Adding 524 doors degraded extinguishers by -47.9% due to filter competition. | **Backbone features must be frozen initially** to preserve generic visual features. |

---

## 3. Detailed Architectural Specification

```
                                [ INPUT IMAGE ]
                             (256 x 256 x 3 RGB)
                                      │
                                      ▼
                      ┌───────────────────────────────┐
                      │     PRETRAINED BACKBONE       │
                      │  (MobileNetV2 / ResNet18)     │
                      └───────────────┬───────────────┘
                                      │
                 ┌────────────────────┴────────────────────┐
                 │ (Stage 3: Stride 8)                     │ (Stage 4: Stride 16)
                 ▼                                         ▼
         ┌───────────────┐                         ┌───────────────┐
         │ Feature C3    │                         │ Feature C4    │
         │ (32x32 x 32)  │                         │ (16x16 x 96)  │
         └───────┬───────┘                         └───────┬───────┘
                 │                                         │
                 │          Lateral Connection             │
                 │ ◄───────────────────────────────────────┤
                 ▼                                         ▼
         ┌───────────────┐                         ┌───────────────┐
         │ High-Res Neck │                         │ Low-Res Neck  │
         │ P3 (32x32x64) │                         │ P4 (16x16x64) │
         └───────┬───────┘                         └───────┬───────┘
                 │                                         │
                 ▼                                         ▼
         ┌───────────────┐                         ┌───────────────┐
         │ Small-Object  │                         │ Large-Object  │
         │ Detection     │                         │ Detection     │
         │ Head (P3)     │                         │ Head (P4)     │
         │ (EXIT_SIGN)   │                         │ (EXTINGUISHER,│
         │               │                         │  DOOR, OBST)  │
         └───────┬───────┘                         └───────┬───────┘
                 │                                         │
                 └────────────────────┬────────────────────┘
                                      │
                                      ▼
                        [ DECODING & GREEDY NMS ]
                          (IoU = 0.45, tau = 0.15)
                                      │
                                      ▼
                       [ 4 PERCEPTION DETECTIONS ]
```

### 3.1 Backbone Selection: MobileNetV2 / ResNet18
- **Lightweight & CPU-Optimized:** MobileNetV2 contains ~2.2M parameters in inverted residual bottlenecks; ResNet18 contains ~11M parameters. Both achieve <40ms CPU latency at $256 \times 256$.
- **Pre-trained Semantic Priors:** Early layers extract robust Gabor-like edge, texture, and corner primitives invariant to scene shifts; deep layers extract spatial object boundaries.

### 3.2 Decoupled Bi-Scale Detection Neck (P3 & P4)
To resolve the Phase 8.5 anchor dilution catastrophe while addressing the small-object and tall-object limitations:
- **P3 Head ($32 \times 32$, Stride 8):** Dedicated exclusively to small objects (`EXIT_SIGN`). Anchor scales: $[0.04, 0.04]$ and $[0.08, 0.08]$. Evaluates 2 anchors per cell ($2 \times 32 \times 32 = 2,048$ anchors).
- **P4 Head ($16 \times 16$, Stride 16):** Dedicated to medium and large objects (`FIRE_EXTINGUISHER`, `EXIT_DOOR`, `OBSTACLE`). Anchor scales: $[0.22, 0.35]$ (extinguisher/obstacle) and $[0.20, 0.75]$ (tall exit door). Evaluates 2 anchors per cell ($2 \times 16 \times 16 = 512$ anchors).
- **Total Anchors:** 2,560 anchors (a manageable 36% reduction compared to Phase 8.5's 4,032 anchors).

### 3.3 Two-Stage Transfer Learning Protocol
1. **Stage A (Warmup / Frozen Backbone):**
   - Backbone layers 100% frozen (`param.requires_grad = False`).
   - Only Neck (P3/P4) and Detection Heads are trained for 3 epochs with AdamW ($lr = 1\times 10^{-3}$).
   - Prevents uninitialized random head gradients from corrupting pre-trained backbone weights.
2. **Stage B (Fine-Tuning / Partial Unfreeze):**
   - Unfreeze Stage 3 and Stage 4 backbone layers (`param.requires_grad = True`).
   - Retain Stem and early Stage 1/2 layers frozen.
   - Train with 10x lower learning rate ($lr = 1\times 10^{-4}$) with cosine annealing schedule for 5 epochs.

---

## 4. Class Balance & Loss Formulation

To counter the extreme data imbalance ($4,112$ extinguishers vs $714$ doors vs $75$ obstacles):
$$\mathcal{L}_{total} = 2.0 \cdot \mathcal{L}_{box} + 1.0 \cdot \mathcal{L}_{obj} + 1.5 \cdot \mathcal{L}_{cls}$$
With class-frequency inverse square-root weighting applied to classification cross-entropy:
$$w_c = \sqrt{\frac{N_{max}}{N_c + 1e-4}}$$
- $w_{extinguisher} = 1.00$
- $w_{door} = 2.40$
- $w_{sign} = 1.35$
- $w_{obstacle} = 7.40$ (clamped to maximum $5.0$ to prevent gradient explosion).
