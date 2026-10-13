# FireSphere.ai — Phase 8.3 Production Training Data Preparation & Readiness Gate Report

**Document Version:** `1.0.0-phase8.3`  
**Governing Phase 8.1 Scope:** `ai-service/docs/phase8_1_production_ai_scope.md`  
**Governing Phase 8.2 Manifest:** `datasets/fire_safety/manifests/phase8_2_capability_manifest.json`  
**Dataset Workspace:** `datasets/fire_safety/prepared/phase8_3/`  
**Status:** **PASS — READY_WITH_DATA_LIMITATIONS**

---

## 1. Executive Summary

Phase 8.3 converts the approved Phase 8.2 Dataset & Capability Manifest into an immutable, leakage-safe, group-partitioned production training dataset ready for consumption by Phase 8.4 model training.

In strict compliance with Phase 8.3 governance:
1. **Zero Model Training Executed:** No YOLO or PyTorch models were trained, no weights or checkpoints were created, and no benchmarks were run.
2. **Canonical Baseline Preserved:** The canonical dataset (`datasets/fire_safety/images/`, `annotations/`, `splits/`) remains 100% immutable and untouched (verified baseline: 6,471 images, 6,471 annotations, 9,077 bounding boxes).
3. **Approved Perception Vocabulary Enforced:** Prepared YOLO dataset (`yolo/data.yaml`) is strictly restricted to the 4 approved perception classes:
   - `0: FIRE_EXTINGUISHER`
   - `1: EXIT_DOOR`
   - `2: EXIT_SIGN`
   - `3: OBSTACLE`
4. **Compound Violation Decoupled:** Genuine obstruction violation data from Dataset 6 is isolated in `violations/` as `EMERGENCY_EXIT_OBSTRUCTED` (126 annotations), preserving the Phase 8.1 architectural rule: *Fire-safety findings are reasoning outputs, not direct object detection perception classes*.
5. **Strict Group-Aware Splitting & Anti-Leakage:** 4,887 prepared images were partitioned across 3,586 unique physical scene groups using deterministic group-aware splitting (`seed=42`). Zero cross-split duplicates (SHA-256), near-duplicates (dHash Hamming distance < 5), or physical scene collisions exist between Train, Validation, and Test splits.
6. **No Synthetic Balancing:** No oversampling, synthetic duplication, or label hallucination was introduced. Empirical class distributions are honestly reported.

---

## 2. Source Datasets Used

Only datasets approved for training in `datasets/fire_safety/manifests/phase8_2_training_candidates.json` were admitted into the preparation pipeline:

| Source Dataset | Source Archive | License | Role | Approved Classes | Samples Contributed |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `fire_safety_multi` | `Fire Safety.v1i.yolov8.zip` | CC BY 4.0 | MVP Perception | `fire_extinguisher`, `Exit sign` | 3,494 |
| `emergency_exit_signs` | `Emergency Exit Signs.v2i.yolov8.zip` | CC0 Public Domain | MVP Perception | `Down/Left/Right/Straight/Up Exit` | 657 |
| `fire_safety_equipment` | `Fire Safety Equipment.v1i.yolov8.zip` | CC BY 4.0 | MVP Perception | `Fire Extinguisher`, `Emergency Exit SIgn` | 307 |
| `fire_extinguisher_components` | `Fire Extinguisher.v1-roboflow-instant-1--eval-.yolov8.zip` | CC BY 4.0 | MVP Perception | `shell` (as extinguisher body) | 163 |
| `safety_equipment_sign` | `Safety Equipment - Sign.v1-roboflow-instant-1--eval-.yolov8.zip` | CC BY 4.0 | MVP Perception | `fire-extinguisher` | 110 |
| `roboflow-blocked-emergency-exit-v1` (Dataset 6) | `blocked emergency exit.v1i.yolov8.zip` | CC BY 4.0 | Violation & Perception | `emergency exit door`, `boxes blocking exit`, `objects blocked emergency exit` | 156 |
| **TOTAL** | — | — | — | — | **4,887** |

### Excluded & Auxiliary Datasets Enforced
- **Dataset 7 (Zenodo SHD-Bench):** `EXCLUDED` from training. Governed by CC BY-NC 4.0 (Non-Commercial restriction). Reserved exclusively for zero-shot synthetic evaluation auditing.
- **Dataset 8 (Zenodo Fire Extinguisher Segmentation):** Marked `AUXILIARY`. Excluded from direct blocked-extinguisher ground truth to prevent semantic coercion.

---

## 3. Source Class Normalization & Mapping

Source class normalization strictly followed the Phase 8.2 schema. No semantic coercion was performed (e.g., generic extinguishers were never relabeled as blocked or expired):

```
Source Class Mapping Audit:
-------------------------------------------------------------------------------------------------------------
Source Dataset                  Source Class                     Target Perception Class   Mapping Type
-------------------------------------------------------------------------------------------------------------
fire_safety_multi               fire_extinguisher (ID 7)         FIRE_EXTINGUISHER (ID 0)  DIRECT
fire_safety_multi               Exit sign (ID 0)                 EXIT_SIGN (ID 2)          DIRECT
fire_safety_equipment           Fire Extinguisher (ID 2)         FIRE_EXTINGUISHER (ID 0)  DIRECT
fire_safety_equipment           Emergency Exit SIgn (ID 0)       EXIT_SIGN (ID 2)          SEMANTIC_EQUIV
fire_extinguisher_components    shell (ID 5)                     FIRE_EXTINGUISHER (ID 0)  PARTIAL (SHELL)
safety_equipment_sign           fire-extinguisher (ID 0)         FIRE_EXTINGUISHER (ID 0)  DIRECT
emergency_exit_signs            Straight Exit (ID 0..4)          EXIT_SIGN (ID 2)          SEMANTIC_EQUIV
blocked_emergency_exit (DS 6)   emergency exit door (ID 2)       EXIT_DOOR (ID 1)          DIRECT
blocked_emergency_exit (DS 6)   boxes blocking exit (ID 1)       OBSTACLE (ID 3)           DIRECT
blocked_emergency_exit (DS 6)   objects blocked... (ID 4)        OBSTACLE (ID 3)           DIRECT
blocked_emergency_exit (DS 6)   blocked emergency exit (ID 0)   EMERGENCY_EXIT_OBSTRUCTED VIOLATION_COMPOUND
blocked_emergency_exit (DS 6)   exit block violation (ID 3)      EMERGENCY_EXIT_OBSTRUCTED VIOLATION_COMPOUND
-------------------------------------------------------------------------------------------------------------
```

---

## 4. Prepared Image & Annotation Validation

All candidate images and labels were validated using cryptographic and computer-vision integrity checks:
- **Format Verification:** All images readable, valid JPEG/PNG formats.
- **Dimensionality:** All images satisfy minimum resolution thresholds ($\ge 32 \times 32$ px), OpenCV decodability verified.
- **YOLO Bounding Box Syntax:** Checked normalized bounds ($0 \le x, y \le 1$ and $0 < w, h \le 1$). Zero out-of-bounds or negative coordinates.
- **Image-Label Pairing:** 100% of prepared images have corresponding label files.
- **Rejected Samples:** **0** rejected samples.

---

## 5. Duplicate & Near-Duplicate Detection Audit

To eliminate cross-split memorization and leakage, full cryptographic and perceptual hashing was conducted:
- **Exact Duplicates (SHA-256):** 4,670 unique SHA-256 hashes identified across 4,887 images. 118 exact duplicate clusters found within original source archives.
- **Near-Duplicates (dHash):** Evaluated at Hamming distance threshold $< 5$.
- **Deduplication / Group-Lock Policy:** All identical and near-duplicate images are strictly confined to identical group IDs.
- **Cross-Split Collisions:**
  - Exact duplicates crossing splits: **0**
  - Near-duplicates crossing splits: **0**
  - Group leakage across splits: **0**

---

## 6. Physical Scene & Video Grouping

Dataset 6 and canonical data contain multi-view captures and augmentation lineages. Splitting frames from the same physical scene across train and test would result in severe data leakage.

- **Dataset 6 Grouping:** 52 distinct physical scenes were identified. Each physical scene comprises 3 augmented variants (156 total images). All 3 variants per scene are strictly locked to the same group ID (`ds6_scene_001` through `ds6_scene_052`).
- **Canonical Grouping:** Roboflow augmentation lineage was resolved by stripping `.rf.*` hashes to cluster physical capture sessions (`canon_*`).
- **Total Unique Groups:** **3,586** physical scene groups.
- **Group Distribution:**
  - **Train:** 2,510 groups (70.0%)
  - **Val:** 717 groups (20.0%)
  - **Test:** 359 groups (10.0%)

---

## 7. Split Statistics & Distribution

Deterministic group-aware splitting (`seed=42`) produced the following final distribution:

| Split | Images | Image Percentage | Physical Groups | Bounding Boxes |
| :--- | :--- | :--- | :--- | :--- |
| **Train** | 3,377 | 69.1% | 2,510 | 5,097 |
| **Validation** | 988 | 20.2% | 717 | 1,470 |
| **Test** | 522 | 10.7% | 359 | 817 |
| **TOTAL** | **4,887** | **100.0%** | **3,586** | **7,384** |

---

## 8. Class Distribution & Bounding Box Analysis

Comprehensive analysis of target perception classes and compound violation ground truth:

| Class Name | Target Role | Total Annotations | Total Images | Unique Groups | Train | Val | Test | Area (Min / Med / Max) | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `FIRE_EXTINGUISHER` | Primary Perception | 4,564 | 2,496 | 2,391 | 3,164 | 948 | 452 | 0.0001 / 0.0234 / 0.7104 | **READY_FOR_TRAINING** |
| `EXIT_SIGN` | Primary Perception | 2,559 | 2,235 | 1,143 | 1,741 | 498 | 320 | 0.00004 / 0.0395 / 1.0000 | **READY_FOR_TRAINING** |
| `EXIT_DOOR` | Primary Perception | 42 | 42 | 14 | 33 | 3 | 6 | 0.0867 / 0.4242 / 0.8227 | **DATA_LIMITED** |
| `OBSTACLE` | Primary Perception | 93 | 90 | 30 | 69 | 6 | 18 | 0.0341 / 0.3249 / 0.8235 | **DATA_LIMITED** |
| `EMERGENCY_EXIT_OBSTRUCTED` | Compound Violation | 126 | 120 | 40 | 90 | 15 | 21 | 0.0341 / 0.3952 / 0.8235 | **DATA_LIMITED** |

---

## 9. Data Sufficiency & Limitation Assessment

1. **`FIRE_EXTINGUISHER`:** **READY_FOR_TRAINING**
   - High abundance (4,564 boxes across 2,496 images and 2,391 groups).
   - High scene diversity with robust variation in scale (median area 2.3% of image, maximum 71.0%).
2. **`EXIT_SIGN`:** **READY_FOR_TRAINING**
   - High abundance (2,559 boxes across 2,235 images and 1,143 groups).
   - Well-distributed across orientations (straight, left, right, up, down).
3. **`EXIT_DOOR`:** **DATA_LIMITED**
   - Only 42 annotations across 14 unique physical scenes from Dataset 6.
   - High risk of overfitting if trained without balanced loss weighting or freezing.
4. **`OBSTACLE`:** **DATA_LIMITED**
   - 93 annotations across 30 unique physical scenes from Dataset 6.
   - Diversity restricted primarily to boxes, pallets, and clutter in emergency exit corridors.
5. **`EMERGENCY_EXIT_OBSTRUCTED`:** **DATA_LIMITED**
   - 126 annotations across 40 unique physical scenes from Dataset 6.
   - Preserved as an isolated compound violation capability.

---

## 10. Prepared Artifacts & Manifest Manifests

All prepared files are located strictly in `datasets/fire_safety/prepared/phase8_3/`:

```
datasets/fire_safety/prepared/phase8_3/
├── yolo/
│   ├── data.yaml                 # 4 perception classes
│   ├── images/ {train,val,test}  # 4,887 image symlinks/copies
│   └── labels/ {train,val,test}  # 4,887 normalized YOLO labels
├── violations/
│   ├── data.yaml                 # 1 compound violation class
│   ├── images/ {train,val,test}  # Violation subset images
│   └── labels/ {train,val,test}  # Violation subset labels
├── manifests/
│   ├── phase8_3_training_manifest.json     # 3,377 train records with provenance
│   ├── phase8_3_validation_manifest.json   # 988 val records with provenance
│   ├── phase8_3_test_manifest.json         # 522 test records with provenance
│   ├── phase8_3_class_statistics.json      # Bbox & split distribution
│   ├── phase8_3_group_manifest.json        # 3,586 physical scene groups
│   ├── phase8_3_provenance.json            # Dataset licenses & origins
│   ├── phase8_3_rejected_samples.json      # Empty list (0 rejections)
│   └── phase8_3_training_gate.json         # Formal gate decision
├── statistics/
│   └── class_and_bbox_statistics.json      # Detailed area/aspect ratio analysis
├── duplicates/
│   └── duplicate_report.json               # Hashing & cross-split duplicate audit
├── groups/
│   └── scene_grouping_report.json          # Scene grouping integrity audit
├── validation/
│   └── validation_audit_report.json        # Verification audit certificate
└── README.md                               # Workspace documentation
```

---

## 11. Canonical Dataset Immutability Verification

Before and after execution of Phase 8.3 preparation, the canonical dataset was audited:
- **Canonical Image Count:** 6,471 (Expected: 6,471) — **UNTOUCHED**
- **Canonical Label Count:** 6,471 (Expected: 6,471) — **UNTOUCHED**
- **Canonical Bounding Boxes:** 9,077 (Expected: 9,077) — **UNTOUCHED**
- **Canonical Split Partitions:** Train 4,522 / Val 1,293 / Test 656 — **UNTOUCHED**

Zero bytes of source data were modified or deleted.

---

## 12. Training Gate Decision & Phase 8.4 Handoff

```json
{
  "status": "READY_FOR_PHASE_8_4",
  "gate_condition": "READY_WITH_DATA_LIMITATIONS",
  "timestamp": "2026-10-08T12:21:46.814810+00:00",
  "total_images_prepared": 4887,
  "class_readiness": {
    "FIRE_EXTINGUISHER": "READY_FOR_TRAINING",
    "EXIT_SIGN": "READY_FOR_TRAINING",
    "EXIT_DOOR": "DATA_LIMITED",
    "OBSTACLE": "DATA_LIMITED",
    "EMERGENCY_EXIT_OBSTRUCTED": "DATA_LIMITED"
  },
  "leakage_status": "ZERO_LEAKAGE_VERIFIED",
  "canonical_integrity": "UNTOUCHED"
}
```

### Readiness Assessment
- **Status:** **PASS — READY_WITH_DATA_LIMITATIONS**
- **Phase 8.4 Permitted:** **YES**, under the explicit condition that Phase 8.4 training configurations incorporate class-imbalance mitigations (e.g., loss weighting or selective focal loss) for `EXIT_DOOR` and `OBSTACLE`.
- **Absolute Stop Condition:** Preparation is complete. Phase 8.4 training must NOT begin in this phase.
