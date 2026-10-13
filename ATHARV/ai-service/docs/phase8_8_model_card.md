# FireSphere.ai — Phase 8.8 Candidate Model Card

**Model Identifier:** `firesphere-perception-candidate-v0.8.8`  
**Model Version:** `0.8.8-candidate`  
**Model Type:** Native PyTorch Lightweight Visual Inspection Detector  
**Checkpoint Path:** [`ai-service/artifacts/phase8_8/checkpoints/best.pt`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_8/checkpoints/best.pt)  
**Checkpoint SHA-256:** `b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1`  
**Parameter Count:** 2,237,627 parameters (100% trainable)  
**Model File Size:** 8.56 MB  
**Production Status:** `NOT_APPROVED`  
**Date:** 2026-10-09  

---

## 1. Intended Purpose & Operational Context

`firesphere-perception-candidate-v0.8.8` is an experimental candidate detector trained as part of Phase 8.8 to investigate whether the audited Phase 8.8 minority-class dataset expansion improves detection of `EXIT_DOOR` and `OBSTACLE` entities.

### Intended Use
- Stage-1 visual object localization for indoor fire safety inspections.
- Proposing bounding boxes and class logits for downstream rule reasoning.
- Human-in-the-Loop officer assistance: drafting inspection remarks.

### Prohibited Use
- Autonomous approval or rejection of Fire NOC applications.
- Legal certification of building statutory compliance.
- Unsupervised emergency life-safety decision-making.

---

## 2. Perception Vocabulary

The model adheres strictly to the frozen 4-class perception vocabulary:

| Class ID | Class Name | Perception Semantic Scope |
| :---: | :--- | :--- |
| `0` | `FIRE_EXTINGUISHER` | Portable fire extinguisher appliances (cylinder, gauge, nozzle). |
| `1` | `EXIT_DOOR` | Egress doors, commercial corridor doors, stairwell fire exits. |
| `2` | `EXIT_SIGN` | Illuminated or directional emergency egress signage. |
| `3` | `OBSTACLE` | Physical clutter encroaching on emergency egress corridors. |

---

## 3. Training & Validation Overview

- **Training Corpus:** Phase 8.8 Audited Training Partition (3,427 images, 4,960 bounding boxes).
- **Validation Corpus:** Phase 8.8 Audited Validation Partition (928 images, 1,443 bounding boxes).
- **Held-Out Benchmark:** Untouched Phase 8.3 Test Partition (522 images, 796 bounding boxes).
- **Optimization:** AdamW ($lr=0.001$, weight decay = $1\times 10^{-4}$), 5 epochs, batch size 32.
- **Hardware:** Intel Core CPU (torch 2.14.1+cpu).

---

## 4. Empirical Performance Summary

### Held-Out Test Evaluation (522 images, IoU = 0.50, tau = 0.15)
- **Mean Precision:** 0.1277
- **Mean Recall:** 0.0882
- **Mean F1:** 0.0949
- **mAP@50:** 0.0276
- **mAP@50:95:** 0.0097

### Per-Class Test Performance
- `FIRE_EXTINGUISHER`: Precision = 0.3243 | Recall = 0.3186 | AP@50 = 0.1028 (Support: 452)
- `EXIT_DOOR`: Precision = 0.0000 | Recall = 0.0000 | AP@50 = 0.0000 (Support: 6)
- `EXIT_SIGN`: Precision = 0.1864 | Recall = 0.0344 | AP@50 = 0.0074 (Support: 320)
- `OBSTACLE`: Precision = 0.0000 | Recall = 0.0000 | AP@50 = 0.0000 (Support: 18)

---

## 5. Latency & Resource Footprint

Benchmarked across 100 test samples on CPU ($256 \times 256$, batch size 1):
- **Preprocess Latency (mean):** 17.52 ms
- **Inference Latency (mean):** 36.50 ms
- **Median Latency (p50):** 40.17 ms
- **95th Percentile Latency (p95):** 118.91 ms
- **Throughput:** 17.97 FPS
- **Model Checkpoint Size:** 8.56 MB

---

## 6. Safety, Bias & Operational Constraints

1. **Class Competition & Extinguisher Regression:**  
   Introducing 524 training doors into a single-scale $16 \times 16$ anchor grid diluted the feature capacity for fire extinguishers, causing a -47.9% regression in extinguisher AP@50.
2. **Minority-Class Generalization:**  
   The candidate model failed to generalize to the 6 held-out test doors and 18 held-out test obstacles at IoU 0.50 without pre-trained representations.
3. **Status:** The model is evaluated as `CANDIDATE_REGRESSED` and is strictly **NOT APPROVED** for production deployment. Phase 8.4 remains the authoritative reference baseline.
