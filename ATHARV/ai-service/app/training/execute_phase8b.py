import os
from pathlib import Path
import shutil
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent))

from app.core.logging import logger
from app.training.phase8b_pipeline import Phase8BPipeline
from app.training.readiness_gate import DatasetReadinessGate


def run_phase8b():
    print("=" * 80)
    print("FIRESPHERE.AI — EXECUTING PHASE 8B: REAL DATASET INGESTION & VALIDATION")
    print("=" * 80)

    # 1. Paths
    workspace_root = Path(r"c:\Users\athar\OneDrive\Desktop\Firesphere.ai")
    zip_dir = workspace_root / "datasets"
    canonical_root = workspace_root / "datasets" / "fire_safety"
    ai_service_root = workspace_root / "ai-service" / "datasets" / "fire_safety"

    t0 = time.time()

    # 2. Initialize pipeline
    pipeline = Phase8BPipeline(
        zip_sources_dir=zip_dir,
        processed_dataset_root=canonical_root,
        seed=42,
    )

    # 3. Execute
    results = pipeline.run_full_pipeline()

    # 4. Mirror key artifacts and metadata to ai-service/datasets/fire_safety
    ai_service_root.mkdir(parents=True, exist_ok=True)
    for sub in ["validation", "manifests", "splits", "quarantine"]:
        (ai_service_root / sub).mkdir(parents=True, exist_ok=True)

    # Copy json configs and reports
    for item in [
        "dataset_sources.json",
        "dataset_class_mapping.json",
        "dataset_meta.json",
        "README.md",
    ]:
        src = canonical_root / item
        if src.exists():
            shutil.copy2(src, ai_service_root / item)

    for item in [
        "annotation_validation_report.json",
        "class_distribution.json",
        "rejection_report.json",
    ]:
        src = canonical_root / "validation" / item
        if src.exists():
            shutil.copy2(src, ai_service_root / "validation" / item)

    for item in [
        "train.json", "train.jsonl",
        "val.json", "val.jsonl",
        "test.json", "test.jsonl",
        "full_manifest.json", "full_manifest.jsonl",
    ]:
        src = canonical_root / "manifests" / item
        if src.exists():
            shutil.copy2(src, ai_service_root / "manifests" / item)

    for item in ["train.txt", "val.txt", "test.txt"]:
        src = canonical_root / "splits" / item
        if src.exists():
            shutil.copy2(src, ai_service_root / "splits" / item)

    # 5. Evaluate readiness gate on both canonical and ai-service root
    gate_canonical = DatasetReadinessGate(dataset_root=canonical_root)
    eval_canonical = gate_canonical.evaluate_readiness()

    gate_ai = DatasetReadinessGate(dataset_root=ai_service_root)
    eval_ai = gate_ai.evaluate_readiness()

    t_elapsed = time.time() - t0

    print("\n" + "=" * 80)
    print("PHASE 8B PIPELINE EXECUTION SUMMARY")
    print("=" * 80)
    print(f"Elapsed Time: {t_elapsed:.2f} seconds")
    print(f"Archives Discovered: {results['archives_discovered']}")
    print(f"Total Raw Images Evaluated: {results['raw_images']}")
    print(f"Valid Decodable Images: {results['valid_images']}")
    print(f"Rejected Images: {results['invalid_images']}")
    print(f"Valid Bounding Box Annotations: {results['valid_annotations']}")
    print(f"Rejected Bounding Box Annotations: {results['invalid_annotations']}")
    print(f"Exact Duplicate Images: {results['exact_duplicates']}")
    print(f"Near-Duplicate Pairs Detected: {results['near_duplicates']}")
    print(f"Train Split: {results['train_count']} ({results['train_count']/results['valid_images']*100:.1f}%)")
    print(f"Validation Split: {results['val_count']} ({results['val_count']/results['valid_images']*100:.1f}%)")
    print(f"Test Split: {results['test_count']} ({results['test_count']/results['valid_images']*100:.1f}%)")
    print("-" * 80)
    print("Readiness Gate Evaluation (Canonical):")
    print(f"  Status: {eval_canonical.status}")
    print(f"  Ready for Phase 9: {eval_canonical.isReadyForPhase9}")
    print(f"  Checks: {sum(1 for c in eval_canonical.checks if c.passed)}/{len(eval_canonical.checks)} passed")
    print("  Blockers:")
    for b in eval_canonical.blockers:
        print(f"    - {b}")
    print("=" * 80)

    return results, eval_canonical


if __name__ == "__main__":
    run_phase8b()
