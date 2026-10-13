# FireSphere.ai — Phase 8.1: Dataset-to-Capability Mapping

**Document Version:** `1.0.0-phase8.1`  
**Classification:** `DATA_GOVERNANCE_SPECIFICATION`  
**Lifecycle Status:** `FROZEN`  
**Service Version:** `0.8.1-phase8.1`  
**Author:** Member 4 — AI/ML, Computer Vision & AI Productivity  
**Date:** 2026-10-08  

---

## 1. Governance & Immutability Principles

Under strict FireSphere Case C Governance, Truth-in-AI guidelines, and Phase 8.1 scope freeze rules:
1. **Canonical Dataset Immutability:** The canonical dataset in `datasets/fire_safety/` (6,471 images, 6,471 annotations, 9,077 bounding boxes across 4,522 train, 1,293 val, 656 test) remains **completely untouched, unmerged, and unmodified**.
2. **No Coercive Semantic Relabeling:** Datasets depicting generic fire equipment presence (e.g. fire extinguishers, exit doors) must **NEVER** be artificially relabeled as statutory violations (`FIRE_EXTINGUISHER_BLOCKED`, `EMERGENCY_EXIT_OBSTRUCTED`).
3. **No Training Commenced:** This document establishes architectural and data scope decisions only. Zero model training runs are initiated during Phase 8.1.
4. **License Compliance:** Only datasets with permissive open-access licenses allowing commercial SaaS integration (`CC BY 4.0`, `CC0`, `Public Domain`, `Apache 2.0`, `MIT`) are eligible for production training pipelines. Non-commercial (`CC BY-NC`) licenses are strictly quarantined.

---

## 2. Master Dataset Decision Registry

| Dataset ID | Dataset Name / Source | Size / Images | License | Supported Capability | Decision | Primary Rationale |
| :--- | :--- | :---: | :---: | :--- | :---: | :--- |
| **DS-06** | Blocked Emergency Exit (`blocked emergency exit.v1i.yolov8.zip`) | 156 images (120 usable) | `CC BY 4.0` | `EMERGENCY_EXIT_OBSTRUCTED` (Class 3) | **`TRAINING_CANDIDATE`** | 120 verified images with 126 genuine violation boxes. 36 door-only images quarantined. Requires group-aware cluster locking for 52 scenes. |
| **DS-07** | Missing / Noncompliant Extinguisher (`My First Project.v6i.yolov8.zip`) | 865 images (50 empty boxes) | `CC BY 4.0` | `FIRE_EXTINGUISHER_MISSING` (Class 0) | **`EVALUATION_ONLY`** | 50 verified empty cabinet boxes all originate from a single video clip (`Sna54U5CjZHZECx9dyDf_2`). Excluded from standalone training due to extreme single-scene overfitting. |
| **DS-08** | FireSafetyNet Condition Check (`FSE Condition Check` 4 archives) | 2,363 physical images (~3.67 GB) | Research (Verification in progress) | `FIRE_EXTINGUISHER_BLOCKED` (Class 1) | **`AUXILIARY`** | High quality modal/amodal extinguisher polygons. Contains 0 direct obstruction boxes. Usable as auxiliary occlusion feature extractor; never relabeled as violation ground truth. |
| **DS-01** | Emergency Exit Signs (`Emergency Exit Signs.v2i.yolov8.zip`) | 1,470 images (657 canonical) | `CC0 / Public Domain` | Core Perception (`EXIT_SIGN`) | **`TRAINING_CANDIDATE`** | Clean directional exit signs. Ingested into canonical base for infrastructure localization. |
| **DS-02** | Fire Extinguisher Components (`Fire Extinguisher.v1-roboflow...`) | 163 images (163 canonical) | `CC BY 4.0` | Core Perception (`FIRE_EXTINGUISHER`) | **`TRAINING_CANDIDATE`** | Extinguisher cylinder localization baseline. Non-violation presence data. |
| **DS-03** | Fire Safety Equipment (`Fire Safety Equipment.v1i.yolov8.zip`) | 553 images (509 canonical) | `CC BY 4.0` | Multi-Equipment (`EXIT_SIGN`, `HOSE_REEL`, etc.) | **`TRAINING_CANDIDATE`** | Multi-class life-safety equipment presence baseline. Non-violation presence data. |
| **DS-04** | Fire Safety Multi (`Fire Safety.v1i.yolov8.zip`) | 17,047 claimed (5,032 canonical) | `CC BY 4.0` | Multi-Equipment Localization | **`TRAINING_CANDIDATE`** | Curated presence dataset (12,015 non-fire images quarantined). Ingested into canonical baseline. |
| **DS-05** | Safety Equipment - Sign (`Safety Equipment - Sign.v1...`) | 110 images (110 canonical) | `CC BY 4.0` | Core Perception (`FIRE_EXTINGUISHER`) | **`TRAINING_CANDIDATE`** | Extinguisher presence baseline. Ingested into canonical base. |
| **DS-EXT**| Commercial Sprinkler Head Defect (`SHD-Bench` on Zenodo) | 960 claimed images | `CC BY-NC 4.0` | `SPRINKLER_HEAD_DEFECT` (Class 5) | **`EXCLUDED`** | Non-commercial license strictly prohibited in commercial SaaS platform. Optical resolution incompatible with wide-angle photos. |

---

## 3. In-Depth Dataset Audits & Decision Rationale

---

### Dataset 6: Blocked Emergency Exit Benchmark
* **Source Archive:** `blocked emergency exit.v1i.yolov8.zip` (SHA-256: `242e5ce5afff...`, 6.82 MB).
* **Provider:** Roboflow Universe (`sakthi-ypq7a`).
* **License:** `CC BY 4.0` (Verified compatible with commercial SaaS with attribution).
* **Usable Classes & Annotations:**
  - `blocked emergency exit` (18 boxes) $\to$ `DIRECT`
  - `boxes blocking exit` (39 boxes) $\to$ `DIRECT`
  - `exit block violation` (15 boxes) $\to$ `DIRECT`
  - `objects blocked emergency exit` (54 boxes) $\to$ `DIRECT`
  - Total genuine violation annotations: **126 boxes across 120 images**.
* **Quarantined Non-Violations:** 42 boxes of `emergency exit door` across 36 images contain only clean doors with zero obstruction. Quarantined to prevent semantic pollution.
* **Scene Diversity & Leakage Analysis:**
  - 156 total images represent **52 unique physical scenes** subject to a 3x Roboflow augmentation pipeline.
  - Zero bitwise or cross-dataset collisions with Phase 8B canonical data.
* **Formal Decision:** **`TRAINING_CANDIDATE`**.
* **Integration Constraint:** In future Phase 9 training, all 3 augmented variants of each physical scene **must be locked into the same split partition** using group-aware cluster locking to prevent severe train-to-test data leakage.

---

### Dataset 7: Missing / Noncompliant Extinguisher Candidate
* **Source Archive:** `My First Project.v6i.yolov8.zip` (SHA-256: `4bea67d28558...`, 14.63 MB).
* **Provider:** Roboflow Universe (`patyas-workspace`).
* **License:** `CC BY 4.0` (Verified compatible).
* **Audit Findings:**
  - Contains 50 polygon annotations for candidate class `missing fire extinguisher`.
  - Visual verification confirms these 50 annotations accurately delineate empty red plastic wall-mounted extinguisher cradles where the cylinder is physically missing.
  - **Critical Failure Mode — Pseudo-Replication:** All 50 instances are consecutive video frames extracted from a single physical video capture (`Sna54U5CjZHZECx9dyDf_2`).
  - Contains 158 annotations for `fire extinguisher tagged as noncompliant`, but visual inspection reveals standard extinguisher presence with visual noise, NOT verified non-compliance.
* **Formal Decision:** **`EVALUATION_ONLY` / `REFERENCE_ONLY`**.
* **Integration Constraint:** **STRICTLY EXCLUDED FROM STANDALONE PRODUCTION TRAINING.** Relying on a single physical scene causes catastrophic model memorization and produces extreme false positives when deployed in novel buildings. May only serve as an auxiliary visual evaluation probe.

---

### Dataset 8: FireSafetyNet Condition Check (FSE Condition Check)
* **Source Archives:** 4 ZIP archives (~3.67 GB total, 9,466 members).
* **Provider:** Ruhr University Bochum (*Journal of Computing in Civil Engineering*, ASCE, June 2025).
* **License:** Research academic publication. Commercial SaaS licensing clarification underway.
* **Audit Findings:**
  - 2,363 unique physical images across 1,907 scenes. High-resolution ($2560 \times 1440$).
  - Annotation format: YOLOv8 instance segmentation polygons for class `fire-extinguisher`.
  - Total modal instances: 4,021 polygons; Total amodal instances: 3,230 polygons.
  - **Semantic Ground Truth:** Every annotation encloses the extinguisher cylinder itself. The occluding objects (tables, chairs, pillars) are **NEVER** annotated.
  - **Direct Violation Count:** `DIRECT_FIRE_EXTINGUISHER_BLOCKED = 0`.
* **Formal Decision:** **`AUXILIARY`**.
* **Integration Constraint:**
  - **STRICTLY FORBIDDEN FROM DIRECT VIOLATION RELABELING:** Detections of occluded extinguishers must never be coerced into `FIRE_EXTINGUISHER_BLOCKED` ground truth.
  - May be utilized as an auxiliary condition head to evaluate extinguisher visibility ratios, subject to commercial licensing confirmation.

---

### Canonical Datasets 1–5: Baseline Equipment Localization Corpi
* **Source Archives:**
  - `Emergency Exit Signs.v2i.yolov8.zip` (CC0 / 657 canonical images)
  - `Fire Extinguisher.v1-roboflow-instant-1--eval-.yolov8.zip` (CC BY 4.0 / 163 images)
  - `Fire Safety Equipment.v1i.yolov8.zip` (CC BY 4.0 / 509 canonical images)
  - `Fire Safety.v1i.yolov8.zip` (CC BY 4.0 / 5,032 canonical images)
  - `Safety Equipment - Sign.v1-roboflow-instant-1--eval-.yolov8.zip` (CC BY 4.0 / 110 images)
* **Total Canonical Corpus:** 6,471 images, 9,077 bounding boxes.
* **Semantic Analysis:** High-quality localization of physical safety fixtures:
  - 2,610 `fire_extinguisher`
  - 2,272 `exit_sign` / `emergency exit sign`
  - 918 `fire_hose` / `fire hose reel`
  - 784 `fire alarm` / `fire alarm panel`
* **Formal Decision:** **`TRAINING_CANDIDATE` (Stage 1 Core Perception Only)**.
* **Integration Constraint:** Strictly utilized to train Stage 1 atomic object perception (`FIRE_EXTINGUISHER`, `EXIT_DOOR`, `EXIT_SIGN`). None of these annotations represent violation ground truth.

---

### External Dataset: Zenodo SHD-Bench Sprinkler Head Defect
* **Source:** Zenodo Record 8123490 (Fire Protection Engineering Lab).
* **License:** `CC BY-NC 4.0` (Creative Commons Non-Commercial).
* **Formal Decision:** **`EXCLUDED`**.
* **Integration Constraint:** Strictly excluded and quarantined. Utilizing non-commercial licensed datasets in a commercial fire-safety SaaS product creates direct legal infringement.

---

## 4. Split Leakage & Augmentation Governance

When candidate datasets are integrated into future training splits:
1. **Perceptual Hash Deduplication:** All images must pass perceptual dHash screening (Hamming distance $\ge 5$) against existing canonical partitions.
2. **Cluster-Aware Split Locking:** For datasets with augmented variants (Dataset 6 has 3 variants per scene) or multi-frame video walks (Dataset 8 has 188 frame clusters), all frames belonging to the same physical cluster must be deterministically locked into the same split partition (`train`, `val`, or `test`). Splitting augmented variants across train and test partitions is strictly prohibited.
