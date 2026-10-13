# FireSphere.ai — Phase 8.2: Final Dataset & Capability Manifest Specification

**Document Version:** `1.0.0-phase8.2`  
**Classification:** `DATA_GOVERNANCE_AND_TRAINING_SPECIFICATION`  
**Lifecycle Status:** `APPROVED & FROZEN`  
**Phase Transition:** Phase 8.1 (Scope Freeze) $\to$ Phase 8.2 (Manifest) $\to$ Phase 8.3 (Data Prep & Training Pipeline)  
**Author:** Member 4 — AI/ML, Computer Vision & AI Productivity  
**Date:** 2026-10-08  

---

## 1. Executive Objective & Governance Alignment

Phase 8.2 translates the frozen **Phase 8.1 Production AI Scope** into an authoritative, reproducible, and mathematically verified dataset and capability manifest. It operationalizes the core architectural philosophy of FireSphere.ai:
* **Decoupled 2-Stage Pipeline:** Rejection of monolithic 1-stage violation detectors in favor of atomic object perception followed by deterministic spatial, temporal, and condition reasoning.
* **Truth-in-AI Governance:** Strict prohibition against coercively relabeling generic equipment datasets (`fire_extinguisher`, `emergency exit door`) into statutory violation classes (`FIRE_EXTINGUISHER_BLOCKED`, `EMERGENCY_EXIT_OBSTRUCTED`).
* **Canonical Dataset Immutability:** The canonical training corpus located at `datasets/fire_safety/` (6,471 images, 9,077 annotations across 4,522 train, 1,293 val, 656 test) remains completely untouched, unaltered, and unmerged.
* **Anti-Leakage Group Locking:** Enforcing cluster-aware partition locking to prevent augmented variants (e.g. Dataset 6) and multi-frame video walk captures (e.g. Dataset 7, Dataset 8) from leaking across train, validation, and test splits.

---

## 2. Frozen Perception Scope & Training Eligibility

Phase 8.1 established the **Minimum Sufficient Perception Vocabulary**. In Phase 8.2, every perception class is assigned an explicit training status:

| Class ID | Perception Class Name | Category | Training Status | Approved Sources | Training Role |
| :---: | :--- | :--- | :---: | :--- | :--- |
| **0** | `FIRE_EXTINGUISHER` | Life-Safety Equipment | **`TRAINING_ALLOWED`** | Canonical Datasets 2, 3, 4, 5 (2,610 canonical boxes) | Stage-1 atomic cylinder localization |
| **1** | `EXIT_DOOR` | Egress Infrastructure | **`TRAINING_ALLOWED`** | Dataset 6 (42 door boxes), Canonical Egress | Stage-1 egress portal & leaf localization |
| **2** | `EXIT_SIGN` | Egress Infrastructure | **`TRAINING_ALLOWED`** | Canonical Datasets 1, 3, 4 (2,272 canonical boxes) | Stage-1 signage & lintel spatial anchor |
| **3** | `OBSTACLE` | Encroachment Entity | **`TRAINING_ALLOWED`** | Dataset 6 (93 obstacle boxes: boxes, clutter) | Stage-1 secondary obstruction entity |
| — | `EMERGENCY_EXIT_OBSTRUCTED` | Direct Compound Violation | **`TRAINING_ALLOWED`** | Dataset 6 (126 genuine violation boxes, 52 scenes) | Direct compound violation detector |
| **4** | `FIRE_HOSE_REEL` | Life-Safety Equipment | **`AUXILIARY`** | Canonical Datasets 3, 4 (918 canonical boxes) | Baseline presence localization |
| **5** | `MANUAL_CALL_POINT` | Alarm Infrastructure | **`AUXILIARY`** | Canonical Datasets 3, 4 (784 canonical boxes) | Baseline presence localization |
| **6** | `ELECTRICAL_PANEL` | Electrical Infrastructure | **`FUTURE_TRAINING`** | Canonical Dataset 3 (Alarm Panel subset) | Deferred pending electrical hazard data |
| **7** | `EMPTY_BRACKET_CABINET` | Absence Anchor | **`EVALUATION_ONLY`** | Dataset 7 (50 polygons from single video clip) | Held-out evaluation probe only |
| **8** | `SPRINKLER_HEAD` | Suppression Infrastructure | **`EXCLUDED`** | Zenodo SHD-Bench (CC BY-NC 4.0) | Strictly excluded from CV pipeline |

---

## 3. Finding Capability Manifest & Reasoning Dependencies

The 10 statutory defect findings defined in the FireSphere schema are mapped to their perception dependencies, algorithmic strategies, and regulatory clearance configurations:

| ID | FireSphere Finding | Production Status | Primary Strategy | Perception Dependencies | Reasoning / OCR | Regulatory Clearance Configuration |
| :---: | :--- | :---: | :--- | :--- | :---: | :--- |
| **0** | `FIRE_EXTINGUISHER_MISSING` | `PHASE_2_FUTURE` | Expected-Location & Absence Reasoning | `FIRE_EXTINGUISHER`, `EMPTY_BRACKET_CABINET` | Anchor co-location logic | `NOT_HARDCODED` (`CONFIGURABLE / NOT_YET_DEFINED`) |
| **1** | `FIRE_EXTINGUISHER_BLOCKED` | **`PRODUCTION_MVP`** | Object Perception + Spatial Clearance | `FIRE_EXTINGUISHER`, `OBSTACLE` | 2D IoU, proximity, ground line | `NOT_HARDCODED` (`POTENTIAL_ACCESS_OBSTRUCTION`) |
| **2** | `FIRE_EXTINGUISHER_EXPIRED` | `PHASE_2_FUTURE` | Dedicated Macro Crop + OCR Intelligence | `FIRE_EXTINGUISHER` (Close-up) | OCR text parsing ($>365\text{d}$) | `NOT_HARDCODED` (Maintenance schedule logic) |
| **3** | `EMERGENCY_EXIT_OBSTRUCTED` | **`PRODUCTION_MVP`** | Direct Detection + Spatial Reasoning | Direct Obstruction Box, `EXIT_DOOR`, `OBSTACLE` | Egress route encroachment | `NOT_HARDCODED` (`POTENTIAL_ACCESS_OBSTRUCTION`) |
| **4** | `EXIT_SIGNAGE_MISSING` | `PHASE_2_FUTURE` | Lintel Proximity Absence Reasoning | `EXIT_DOOR`, `EXIT_SIGN` | Lintel boundary search | `NOT_HARDCODED` (Requires floor-plan schedule) |
| **5** | `SPRINKLER_HEAD_DEFECT` | **`NOT_SUPPORTED`** | Manual Officer Physical Checklist | None (Excluded from CV) | None (Manual checklist) | `NOT_APPLICABLE` (Manual checklist only) |
| **6** | `ELECTRICAL_PANEL_HAZARD` | `PHASE_2_FUTURE` | Hybrid Door State + Clearance | `ELECTRICAL_PANEL`, `OBSTACLE` | Door state + working space | `NOT_HARDCODED` (`CONFIGURABLE / NOT_YET_DEFINED`) |
| **7** | `COMBUSTIBLE_MATERIAL_HAZARD`| `PHASE_2_FUTURE` | Clutter Perception + Occupancy Rule | `OBSTACLE` | Contextual occupancy rules | `NOT_HARDCODED` (Ignition proximity rule) |
| **8** | `MANUAL_CALL_POINT_BLOCKED` | `PHASE_2_FUTURE` | Object Perception + Spatial Clearance | `MANUAL_CALL_POINT`, `OBSTACLE` | Frontal access cone check | `NOT_HARDCODED` (`POTENTIAL_ACCESS_OBSTRUCTION`) |
| **9** | `HOSE_REEL_OBSTRUCTED` | `PHASE_2_FUTURE` | Object Perception + Spatial Clearance | `FIRE_HOSE_REEL`, `OBSTACLE` | Cabinet swing perimeter | `NOT_HARDCODED` (`POTENTIAL_ACCESS_OBSTRUCTION`) |

### Special Governance Mandate: No Hard-Coded Clearance Dimensions
> **MANDATORY POLICY:** Under Section 14 requirements, FireSphere **prohibits hard-coding statutory clearance dimensions** (such as 36 inches, 0.9 meters, or NFPA 70 clearance tables) into the machine-learning manifest or computer vision model. The vision engine identifies **"potential access obstructions"** via configurable mathematical parameters (`clearance_rule_source: CONFIGURABLE / NOT_YET_DEFINED`, `clearance_rule_status: NOT_HARDCODED`). Final statutory non-compliance determinations remain the sole legal responsibility of authorized fire officers.

---

## 4. Master Source Class Mapping Registry

Every source annotation class discovered across audited datasets is mapped explicitly to the target taxonomy:

| Source Dataset | Source Class | FireSphere Class | Mapping Type | Decision | Semantic Rationale |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `blocked emergency exit` (DS-06) | `blocked emergency exit` | `EMERGENCY_EXIT_OBSTRUCTED` | `DIRECT` | `TRAINING_CANDIDATE` | Direct evidence of blocked exit corridor. |
| `blocked emergency exit` (DS-06) | `boxes blocking exit` | `EMERGENCY_EXIT_OBSTRUCTED` | `DIRECT` | `TRAINING_CANDIDATE` | Combustible obstacle blocking egress; also `OBSTACLE`. |
| `blocked emergency exit` (DS-06) | `exit block violation` | `EMERGENCY_EXIT_OBSTRUCTED` | `DIRECT` | `TRAINING_CANDIDATE` | Statutory exit block violation box. |
| `blocked emergency exit` (DS-06) | `objects blocked emergency exit`| `EMERGENCY_EXIT_OBSTRUCTED` | `DIRECT` | `TRAINING_CANDIDATE` | Clutter blocking escape door; also `OBSTACLE`. |
| `blocked emergency exit` (DS-06) | `emergency exit door` | `EXIT_DOOR` | `SEMANTIC_EQUIVALENT` | `TRAINING_CANDIDATE` | Door fixture. Quarantined from direct violation. |
| `emergency_exit_signs` (DS-01) | `Back Exit`, `Left Exit`, etc. | `EXIT_SIGN` | `SEMANTIC_EQUIVALENT` | `TRAINING_CANDIDATE` | Directional emergency exit sign fixture. |
| `fire_extinguisher_components` (DS-02)| `shell` | `FIRE_EXTINGUISHER` | `SEMANTIC_EQUIVALENT` | `TRAINING_CANDIDATE` | Pressurized cylinder shell localization. |
| `fire_safety_equipment` (DS-03) | `Fire Extinguisher` | `FIRE_EXTINGUISHER` | `DIRECT` | `TRAINING_CANDIDATE` | Complete portable fire extinguisher. |
| `fire_safety_equipment` (DS-03) | `Emergency Exit SIgn` | `EXIT_SIGN` | `DIRECT` | `TRAINING_CANDIDATE` | Illuminated / photoluminescent sign fixture. |
| `fire_safety_equipment` (DS-03) | `Fire Hose Reel` | `FIRE_HOSE_REEL` | `DIRECT` | `AUXILIARY` | Hose reel presence baseline. |
| `fire_safety_equipment` (DS-03) | `Fire Alarm Panel` | `ELECTRICAL_PANEL` | `PARTIAL` | `AUXILIARY` | Alarm panel fixture presence. |
| `fire_safety_multi` (DS-04) | `fire_extinguisher` | `FIRE_EXTINGUISHER` | `DIRECT` | `TRAINING_CANDIDATE` | Complete portable fire extinguisher. |
| `fire_safety_multi` (DS-04) | `Exit sign` | `EXIT_SIGN` | `DIRECT` | `TRAINING_CANDIDATE` | Exit signage fixture. |
| `fire_safety_multi` (DS-04) | `fire_hose` | `FIRE_HOSE_REEL` | `DIRECT` | `AUXILIARY` | Hose reel presence baseline. |
| `fire_safety_multi` (DS-04) | `Fire alarm` | `MANUAL_CALL_POINT` | `PARTIAL` | `AUXILIARY` | Pull station / alarm box fixture presence. |
| `safety_equipment_sign` (DS-05) | `fire-extinguisher` | `FIRE_EXTINGUISHER` | `DIRECT` | `TRAINING_CANDIDATE` | Extinguisher presence baseline. |
| `fire_safetynet_condition_check` (DS-08)| `fire-extinguisher` | `FIRE_EXTINGUISHER` | `AUXILIARY` | `AUXILIARY` | Modal/amodal masks; NOT violation ground truth. |
| `my-first-project` (DS-07) | `missing fire extinguisher` | `EMPTY_BRACKET_CABINET` | `PARTIAL` | `EVALUATION_ONLY` | 50 empty boxes from 1 clip; evaluation probe only. |
| `my-first-project` (DS-07) | `fire extinguisher tagged...` | `FIRE_EXTINGUISHER` | `INVALID` | `EXCLUDED` | Visual noise; not verified non-compliance. |
| `zenodo-sprinkler-head-defect` | `sprinkler_corroded` | `SPRINKLER_HEAD_DEFECT` | `INVALID` | `EXCLUDED` | CC BY-NC 4.0 license; sub-pixel scale limit. |

---

## 5. Dataset Decisions Summary

1. **`TRAINING_CANDIDATE`:**
   - Canonical Datasets 1, 2, 3, 4, 5 (for Stage-1 equipment presence localization).
   - Dataset 6 (120 usable images, 126 genuine violation boxes, subject to group-aware cluster locking).
2. **`AUXILIARY`:**
   - Dataset 8 (FireSafetyNet): 2,363 images with modal/amodal extinguisher masks. Usable for condition/occlusion estimation; strictly prohibited from violation relabeling.
3. **`EVALUATION_ONLY`:**
   - Dataset 7 (My First Project): 50 verified missing extinguisher frames. Strictly excluded from standalone training due to single-scene pseudo-replication (`Sna54U5CjZHZECx9dyDf_2`).
4. **`EXCLUDED`:**
   - Zenodo SHD-Bench: `CC BY-NC 4.0` license prohibits commercial SaaS deployment; sub-pixel optical resolution disparity.
   - Dataset 6 Quarantined Doors: 36 door-only images excluded from violation training.

---

## 6. Anti-Leakage & Split Governance

1. **Exact Duplicate Rule:** Zero cross-split bitwise SHA-256 collisions.
2. **Near-Duplicate Rule:** Perceptual dHash Hamming distance $< 5$ clusters locked to a single partition.
3. **Augmentation Lineage Rule:** All 3 augmented variants of Dataset 6's 52 physical scenes locked to the same partition.
4. **Video Family Rule:** Sequential video frames locked to a single partition.
5. **Split Policy:** Group-aware deterministic split with `seed = 42`, targeting approximately 70% train, 20% validation, 10% test. Physical scene independence takes precedence over exact percentage targets.

---

## 7. Commercial Licensing Matrix

* **Permissive (Approved for SaaS Training):**
  - Public Domain (CC0): Dataset 1 (`emergency_exit_signs`)
  - CC BY 4.0: Datasets 2, 3, 4, 5, 6, 7
* **Restricted (Quarantined / Excluded):**
  - CC BY-NC 4.0: Zenodo SHD-Bench (Excluded)
  - Research Publication Under Review: Dataset 8 (Auxiliary use only)
