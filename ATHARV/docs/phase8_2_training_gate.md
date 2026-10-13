# FireSphere.ai — Phase 8.2: Phase 8.3 Training Gate Specification

**Document Version:** `1.0.0-phase8.2`  
**Classification:** `PIPELINE_READINESS_GATE`  
**Lifecycle Status:** `ACTIVE_GATE`  
**Target Next Phase:** Phase 8.3 — Production Training Pipeline & Data Preparation  
**Author:** Member 4 — AI/ML, Computer Vision & AI Productivity  
**Date:** 2026-10-08  

---

## 1. Executive Gate Declaration

```text
================================================================================
FIRESPHERE.AI PIPELINE READINESS GATE: PHASE 8.3 PRE-TRAINING GATE
================================================================================
STATUS: CONDITIONAL_APPROVAL_PENDING_PHASE_8_3_ACTIVATION
TRAINING EXECUTION IN PHASE 8.2: STRICTLY PROHIBITED
================================================================================
```

Under strict FireSphere Case C Governance, Truth in AI mandates, and Section 27 requirements:

### PHASE 8.3 MAY NOT START UNTIL:

* [x] **Manifest Exists:** The authoritative `phase8_2_capability_manifest.json` and associated registries are generated, syntactically valid, and cryptographically consistent.
* [x] **Dataset Decisions Are Complete:** Every source dataset has been assigned exactly one formal decision (`TRAINING_CANDIDATE`, `AUXILIARY`, `EVALUATION_ONLY`, `EXCLUDED`) with documented rationale.
* [x] **Source Mappings Are Validated:** Every source class discovered across archives has been explicitly mapped to the FireSphere taxonomy without coercive violation relabeling.
* [x] **Leakage Policy Exists:** Exact duplicate, perceptual near-duplicate, augmentation lineage, and sequential video frame locking policies are codified in `phase8_2_leakage_policy.json`.
* [x] **Licensing Decisions Are Recorded:** Commercial SaaS feasibility has been verified in `phase8_2_license_matrix.json` (CC0 and CC BY 4.0 approved; CC BY-NC 4.0 strictly excluded).
* [x] **Training Candidates Are Explicitly Identified:** `phase8_2_training_candidates.json` defines approved Stage-1 perception classes (`FIRE_EXTINGUISHER`, `EXIT_DOOR`, `EXIT_SIGN`, `OBSTACLE`) and compound direct violations (`EMERGENCY_EXIT_OBSTRUCTED`).
* [x] **Excluded Datasets Are Explicitly Recorded:** `phase8_2_excluded_data.json` documents exclusions for Zenodo SHD-Bench, door-only quarantine subsets, and single-scene datasets.
* [x] **Canonical Dataset Integrity Is Verified:** The canonical corpus (`datasets/fire_safety/`) remains 100% byte-for-byte immutable (6,471 images, 6,471 annotations, 9,077 bounding boxes untouched).

---

## 2. Strict Pre-Training Boundaries

Phase 8.2 is a **DATA GOVERNANCE AND MANIFEST PHASE ONLY**. 

The following operations remain **STRICTLY BARRED** until Phase 8.3 is formally authorized:
1. **Zero Model Training:** No invocation of YOLOv8/v11, PyTorch, Ultralytics, torchvision, or CUDA training loops.
2. **Zero Weight Generation:** No compilation or saving of `.pt`, `.onnx`, or TensorRT engine artifacts.
3. **Zero Canonical Mutation:** No merging of Dataset 6, Dataset 7, or Dataset 8 into canonical directories (`images/`, `annotations/`, `splits/`).
4. **Zero Coercive Relabeling:** No conversion of generic safety equipment into violation labels.
5. **No Hard-Coded Clearance Enforcement:** Clearance rules in the manifest must remain marked `CONFIGURABLE / NOT_YET_DEFINED` and `NOT_HARDCODED`.

---

## 3. Phase 8.3 Execution Roadmap

When Phase 8.3 commences, it must adhere strictly to the rules codified in this manifest:
1. **Manifest Ingestion:** Phase 8.3 data preparation scripts must consume `phase8_2_capability_manifest.json` as their single source of truth.
2. **Deterministic Group-Aware Partitioning:** Execute cluster-aware splitting (seed = 42) locking Dataset 6's 52 physical scenes with their 3x augmentations.
3. **Quarantine Isolation:** Ensure all 36 door-only images from Dataset 6 and all non-commercial Zenodo records remain quarantined from training directories.
4. **Held-Out Evaluation Setup:** Mount Dataset 7's 50 empty cabinet images strictly into the evaluation probe harness.
