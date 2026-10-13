# FireSphere.ai — Phase 8.7: Data Readiness & Controlled Training Gate Report

**Document ID:** `FS-AI-DOC-8.7-READINESS`  
**Phase:** Phase 8.7 — Targeted Minority-Class Dataset Expansion & Confidence Calibration  
**Classification:** `ENGINEERING_GATE_REPORT`  
**Phase 8.7 Gate Decision:** `DATA_READY_FOR_CONTROLLED_TRAINING`  
**Production Status:** `NOT_APPROVED` (Data preparation and calibration analysis only; No model registered)  
**Date:** 2026-10-08  
**Author:** Antigravity AI/ML Engineering Agent (Pair Programming)  

---

## 1. Executive Summary & Readiness Gate Status

Phase 8.7 evaluated whether the FireSphere.ai visual perception dataset has attained sufficient quality, licensing compliance, minority-class representation, and calibration clarity to justify a controlled retraining experiment in Phase 8.8.

### Formal Gate Decision: `DATA_READY_FOR_CONTROLLED_TRAINING`

All non-negotiable readiness requirements established in Phase 8.1–8.6 are satisfied:
1. **Verified Semantics:** Every candidate class mapping was audited. E-commerce stock catalog photos and fire appliances were strictly rejected.
2. **Valid Annotations:** All 7,165 bounding boxes across 4,858 images have valid normalized coordinates satisfying $0.0 \le x_c, y_c \le 1.0$ and $0.0 < w, h \le 1.0$.
3. **Complete Provenance & Licensing:** All accepted data originates from verified `CC BY 4.0` or `Public Domain (CC0)` open-access archives with complete attribution metadata.
4. **Zero Data Leakage:** Group-aware splitting (deterministic seed 42) ensures physical-scene independence across Train, Validation, and Test partitions.
5. **Canonical Benchmark Protection:** Zero samples collide with the untouched Phase 8.3 test set (522 images).
6. **Minority-Class Breakthrough:** `EXIT_DOOR` expanded from 14 physical scenes to **545 unique physical scenes** (**+38.9x expansion**).
7. **Empirical Calibration:** Candidate operating threshold for `EXIT_SIGN` calibrated to $\tau_{\text{sign}} \approx 0.08$ on validation data.

---

## 2. Quantitative Class Readiness Matrix

| Perception Class | Class ID | Phase 8.3 Baseline | Phase 8.7 Prepared | Delta (Boxes) | Physical Scenes | Readiness Status | Empirical Assessment |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| `FIRE_EXTINGUISHER` | 0 | 4,564 | 4,112 | -452 (Curated) | >2,000 | **HIGH** | Dominant control class. Proven capable of 99.8% overfit in Phase 8.6. |
| `EXIT_DOOR` | 1 | 42 | 739 | **+697 (+1659%)** | **545 (+38.9x)** | **HIGH** | Bottleneck resolved. Sourced from 529 commercial/corridor scenes. |
| `EXIT_SIGN` | 2 | 2,559 | 2,239 | -320 (Curated) | >1,500 | **HIGH** | Abundant data. Calibration operating point ($\tau = 0.08$) establishes +14.3x recall improvement. |
| `OBSTACLE` | 3 | 93 | 75 | -18 (Curated) | **30** | **LIMITED / PRESERVED** | Preserved from Dataset 6. Candidate e-commerce catalog trash cans rejected under Truth-in-AI. |
| **TOTALS** | — | **7,258** | **7,165** | — | **3,735 Groups** | **READY** | Ready for controlled Phase 8.8 experiment. |

---

## 3. Provenance & Commercial Licensing Registry

All datasets staged into `datasets/fire_safety/prepared/phase8_7/` carry verified commercial open-access licensing:

| Dataset Identifier | Source Archive Filename | Provider / Platform | License | Commercial SaaS Status |
| :--- | :--- | :--- | :--- | :---: |
| `fire_safety_multi` | `Fire Safety.v1i.yolov8.zip` | Jakub Konopa (Roboflow Universe) | `CC BY 4.0` | **VERIFIED_ALLOWED** |
| `roboflow-blocked-emergency-exit-v1` | `blocked emergency exit.v1i.yolov8.zip` | sakthi-ypq7a (Roboflow Universe) | `CC BY 4.0` | **VERIFIED_ALLOWED** |
| `patyas-workspace-my-first-project-v6i` | `My First Project.v6i.yolov8.zip` | patyas-workspace (Roboflow Universe) | `CC BY 4.0` | **VERIFIED_ALLOWED** |
| `emergency_exit_signs` | `Emergency Exit Signs.v2i.yolov8.zip` | emergency-exit-signs (Roboflow) | `Public Domain (CC0)` | **VERIFIED_ALLOWED** |
| `fire_extinguisher_components` | `Fire Extinguisher.v1-roboflow-instant-1--eval-.yolov8.zip` | Roboflow Universe | `CC BY 4.0` | **VERIFIED_ALLOWED** |
| `fire_safety_equipment` | `Fire Safety Equipment.v1i.yolov8.zip` | anass-workspace-f1hzt (Roboflow) | `CC BY 4.0` | **VERIFIED_ALLOWED** |

---

## 4. Anti-Leakage Audit Summary

The anti-leakage verification suite executed during staging confirmed:
* **Internal Cross-Split Separation:**
  $$\text{Train (3,440 images)} \quad \cap \quad \text{Val (930 images)} = \emptyset \quad (\text{0 duplicate SHA-256 hashes})$$
  $$\text{Train (3,440 images)} \quad \cap \quad \text{Test (488 images)} = \emptyset \quad (\text{0 duplicate SHA-256 hashes})$$
  $$\text{Val (930 images)} \quad \cap \quad \text{Test (488 images)} = \emptyset \quad (\text{0 duplicate SHA-256 hashes})$$
* **Canonical Test Split Isolation:**
  Zero samples from the Phase 8.3 held-out test set (522 images) exist in the Phase 8.7 prepared training or validation splits.
* **Lineage & Scene Locking:**
  All synthetic augmentations, crops, and sequential frames from the same physical scene are locked into a single partition via `group_id = scene_{base_stem}`.

---

## 5. Documented Limitations & Truth-in-AI Declarations

1. **`OBSTACLE` Data Limitation:**
   While `EXIT_DOOR` achieved a 38.9x expansion in physical scenes, `OBSTACLE` remains limited to 30 physical scenes (75 boxes). Candidate sources in local archives (such as `trash_can`) consisted of e-commerce studio catalog photos of domestic kitchen bins. In accordance with Truth-in-AI principles, these were rejected to prevent severe distribution shift. Future phases should source dedicated physical obstruction benchmarks from building maintenance or public safety inspection archives.
2. **Small-Object Quantization Limitation for `EXIT_SIGN`:**
   32.1% of ground-truth exit signs have an area $< 0.01$ (smaller than a single stride-16 grid cell). While class-calibrated thresholding ($\tau = 0.08$) recovers medium and large signs, sub-stride small signs require multi-scale feature pyramids (stride-8 heads) to achieve $\text{IoU} \ge 0.5$.

---

## 6. Production Safety & Human-in-the-Loop Principles

* **NO Production Registration:** Under no circumstances is any model registered as production-ready in Phase 8.7.
* **Authoritative Controls Preserved:** Phase 8.4 (`firesphere-perception-baseline-v0.8.0`) remains the authoritative baseline control. Phase 8.5 remains an archived experimental model.
* **Human-in-the-Loop:** AI perception provides advisory visual evidence and draft findings. The inspecting Fire Officer retains sole statutory legal authority. No `AI_APPROVED` or `AI_REJECTED` states exist.
