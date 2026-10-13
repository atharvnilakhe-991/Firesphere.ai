# FireSphere.ai — Phase 9 Data Contract & Ingestion Interface

## 1. Purpose & Governance Binding

This document establishes the binding data contract for Phase 9 (Computer Vision Model Training & Evaluation). Under FireSphere Case C Governance:
* Phase 9 training scripts **MUST NOT** consume raw downloaded ZIP files or temporary directories.
* Phase 9 training scripts **MUST ONLY** consume the canonical dataset directory defined in this contract.
* Phase 9 **MUST NOT** perform automatic semantic relabeling or fabricate bounding boxes.

---

## 2. Canonical Dataset Location

```
C:\Users\athar\OneDrive\Desktop\Firesphere.ai\datasets\fire_safety\
```

Any training, validation, or benchmarking script in Phase 9 must locate the dataset via:
```python
CANONICAL_DATASET_ROOT = Path("datasets/fire_safety")
```

---

## 3. Directory Layout & Artifact Specification

| Directory / File | Description | Purpose in Phase 9 |
|---|---|---|
| `images/` | 6,471 canonical JPEG images | Source images for PyTorch / Ultralytics YOLO dataloaders |
| `annotations/` | 6,471 YOLO normalized bounding box files | Training targets (`<class_id> <xc> <yc> <w> <h>`) |
| `splits/` | Split partition index files (`train.txt`, `val.txt`, `test.txt`) | Deterministic split indexing (70% Train, 20% Val, 10% Test) |
| `manifests/` | Detailed JSON and JSONL records with SHA-256 and cluster IDs | Full provenance and leakage-prevention auditability |
| `dataset_meta.json` | Dataset metadata with verified image counts | Automated readiness gate check before training launch |
| `dataset_sources.json` | Provenance registry of original source datasets | Compliance and license attribution validation |
| `validation/` | Rejection and annotation validation reports | Quality baseline confirmation |

---

## 4. Manifest Record Schema

Every record in `manifests/train.jsonl`, `val.jsonl`, `test.jsonl`, and `full_manifest.jsonl` adheres to the following contract:

```json
{
  "sample_id": "fire_safety_multi_IMG_0042",
  "image_path": "datasets/fire_safety/images/fire_safety_multi_IMG_0042.jpg",
  "annotation_path": "datasets/fire_safety/annotations/fire_safety_multi_IMG_0042.txt",
  "split": "train",
  "source_dataset": "fire_safety_multi",
  "source_archive": "Fire Safety.v1i.yolov8.zip",
  "source_class": "fire_extinguisher",
  "mapped_class": "",
  "target_class": "",
  "mapping_type": "PARTIAL",
  "image_sha256": "4b7a19...",
  "sha256": "4b7a19...",
  "perceptual_hash": "11001010...",
  "group_id": "fire_safety_multi_IMG_0042",
  "validation_status": "VALID"
}
```

---

## 5. Current Dataset Readiness Constraint

* **Current Readiness Status:** `DATASET_PARTIALLY_READY` (`isReadyForPhase9 = False`).
* **Training Restriction:** Training violation detectors (Classes 0–9) is strictly blocked until field annotations for genuine fire code violations (obstructions, missing equipment, blocked exits, open electrical panels) are provided.
* **Allowed Phase 9 Preliminary Task:** Equipment presence spatial localization baselines (`fire_extinguisher`, `exit_sign`, `fire_alarm_panel`, `fire_hose_reel`) may be trained for localization pre-training only, but must NOT be promoted as violation classifiers.
