"""FireSphere.ai — Stage 6: Independent CV Holdout Evaluation Runner.

Performs a strictly controlled, one-pass evaluation of the frozen Model A
checkpoint on the sealed Stage 3 candidate test split (453 images, 657 annotations).

Safety Invariants Enforced:
- Checkpoint SHA-256 is strictly verified before loading.
- Evaluates holdout test split exactly ONCE.
- No retraining, fine-tuning, or hyperparameter selection based on test data.
- CV approval gate remains strictly NOT_APPROVED.
- Calibration status remains strictly NOT_CALIBRATED.
- Production authorization remains strictly false.
"""

import os
import sys
import json
import math
import hashlib
import time
from pathlib import Path
from typing import Dict, List, Tuple, Any

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader

WORKSPACE_DIR = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_DIR / "ai-service"
sys.path.insert(0, str(AI_SERVICE_DIR))

from app.models.baseline_detector import BaselineInspectionDetector, CLASS_NAMES
from app.models.nms import compute_iou
from app.evaluation.comprehensive_evaluator import ComprehensiveEvaluator
from app.training.phase8_4_trainer import Phase84YOLODataset, collate_fn

# Checkpoints and Dataset Paths
BASELINE_CHECKPOINT_PATH = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"
EXPECTED_BASELINE_SHA256 = "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"

MODEL_A_CHECKPOINT_PATH = AI_SERVICE_DIR / "artifacts" / "stage4_controlled_experiments" / "checkpoints" / "model_a_baseline.pt"
EXPECTED_MODEL_A_SHA256 = "e505d20b73950461d022ca34c913cb946c0912f914946ab108cba387b0e51b60"

CANDIDATE_DATASET_ROOT = WORKSPACE_DIR / "datasets" / "fire_safety" / "prepared" / "stage3_candidate" / "yolo"
TEST_IMAGES_DIR = CANDIDATE_DATASET_ROOT / "images" / "test"
TEST_LABELS_DIR = CANDIDATE_DATASET_ROOT / "labels" / "test"
TEST_MANIFEST_PATH = WORKSPACE_DIR / "datasets" / "fire_safety" / "prepared" / "stage3_candidate" / "manifests" / "candidate_test_manifest.json"
EXPECTED_TEST_MANIFEST_SHA256 = "d795f13ba164bad7d5a35d45aa54c69b4aa91d0f6d4c4d96b36c5dfee911802c"

ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts" / "stage6_holdout_evaluation"
METRICS_DIR = ARTIFACTS_DIR / "metrics"


def compute_sha256(filepath: Path) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def verify_identity_and_integrity():
    """Verifies frozen checkpoint, manifest checksum, and dataset pairing."""
    print("==================================================")
    print("STEP 1: VERIFYING EVALUATION IDENTITY & INTEGRITY")
    print("==================================================")

    # 1. Model A Checkpoint
    assert MODEL_A_CHECKPOINT_PATH.exists(), f"Model A checkpoint missing: {MODEL_A_CHECKPOINT_PATH}"
    actual_model_a_sha = compute_sha256(MODEL_A_CHECKPOINT_PATH)
    print(f"Frozen Model A Checkpoint SHA-256: {actual_model_a_sha}")
    assert actual_model_a_sha == EXPECTED_MODEL_A_SHA256, (
        f"Model A hash mismatch! Expected {EXPECTED_MODEL_A_SHA256}, got {actual_model_a_sha}"
    )
    print("[PASS] Model A checkpoint matches expected SHA-256 perfectly.")

    # 2. Baseline Checkpoint
    assert BASELINE_CHECKPOINT_PATH.exists(), f"Baseline checkpoint missing: {BASELINE_CHECKPOINT_PATH}"
    actual_base_sha = compute_sha256(BASELINE_CHECKPOINT_PATH)
    print(f"Reference Baseline Checkpoint SHA-256: {actual_base_sha}")
    assert actual_base_sha == EXPECTED_BASELINE_SHA256, (
        f"Baseline hash mismatch! Expected {EXPECTED_BASELINE_SHA256}, got {actual_base_sha}"
    )
    print("[PASS] Baseline checkpoint matches expected SHA-256 perfectly.")

    # 3. Test Manifest Checksum
    assert TEST_MANIFEST_PATH.exists(), f"Test manifest missing: {TEST_MANIFEST_PATH}"
    actual_manifest_sha = compute_sha256(TEST_MANIFEST_PATH)
    print(f"Test Manifest SHA-256: {actual_manifest_sha}")
    assert actual_manifest_sha == EXPECTED_TEST_MANIFEST_SHA256, (
        f"Test manifest hash mismatch! Expected {EXPECTED_TEST_MANIFEST_SHA256}, got {actual_manifest_sha}"
    )
    print("[PASS] Test manifest matches expected checksum.")

    # 4. Dataset Files Count Verification
    test_manifest = json.loads(TEST_MANIFEST_PATH.read_text(encoding="utf-8"))
    exp_img_count = test_manifest["image_count"]
    exp_ann_count = test_manifest["annotation_count"]
    print(f"Manifest declared size: {exp_img_count} images, {exp_ann_count} annotations.")

    test_imgs = list(TEST_IMAGES_DIR.glob("*.jpg")) + list(TEST_IMAGES_DIR.glob("*.png"))
    test_lbls = list(TEST_LABELS_DIR.glob("*.txt"))
    print(f"Disk files found: {len(test_imgs)} images, {len(test_lbls)} label files.")
    assert len(test_imgs) == exp_img_count, f"Image count mismatch! Expected {exp_img_count}, got {len(test_imgs)}"
    assert len(test_lbls) == exp_img_count, f"Label count mismatch! Expected {exp_img_count}, got {len(test_lbls)}"
    print("[PASS] Holdout test dataset verified: exactly 453 images and labels.")
    print("==================================================\n")

    return test_manifest


def evaluate_model_on_holdout(
    model: nn.Module,
    test_loader: DataLoader,
    device: torch.device,
    model_id: str,
    model_version: str,
) -> Tuple[Dict[str, Any], List[Dict[str, Any]], Dict[str, Any]]:
    """Executes single-pass inference over holdout dataset."""
    model.eval()
    preds_by_img_020: Dict[str, List[Dict[str, Any]]] = {}
    gts_by_img: Dict[str, List[Dict[str, Any]]] = {}
    raw_detections_all: List[Dict[str, Any]] = []

    small_gt_count = 0
    small_gt_hits = 0
    empty_images = 0
    empty_false_alarms = 0
    total_gt = 0
    gt_by_class = {cid: 0 for cid in CLASS_NAMES.keys()}

    with torch.no_grad():
        for images, targets, stems in test_loader:
            images = images.to(device)
            outputs = model(images)

            # Direct primary operating point (0.20)
            batch_preds_020 = model.decode_predictions(outputs, conf_threshold=0.20, iou_threshold=0.45)
            # Diagnostic low-threshold predictions (0.05)
            batch_preds_005 = model.decode_predictions(outputs, conf_threshold=0.05, iou_threshold=0.45)

            for b_idx, stem in enumerate(stems):
                preds_020 = batch_preds_020[b_idx]
                preds_005 = batch_preds_005[b_idx]
                preds_by_img_020[stem] = preds_020

                gt_boxes = targets[b_idx]
                gt_list: List[Dict[str, Any]] = []

                if len(gt_boxes) == 0:
                    empty_images += 1
                    if len(preds_020) > 0:
                        empty_false_alarms += 1

                for k in range(len(gt_boxes)):
                    cid = int(gt_boxes[k, 0].item())
                    xc = float(gt_boxes[k, 1].item())
                    yc = float(gt_boxes[k, 2].item())
                    w = float(gt_boxes[k, 3].item())
                    h = float(gt_boxes[k, 4].item())
                    area = w * h

                    xmin = max(0.0, xc - w / 2.0)
                    ymin = max(0.0, yc - h / 2.0)
                    xmax = min(1.0, xc + w / 2.0)
                    ymax = min(1.0, yc + h / 2.0)

                    if area < 0.02:
                        small_gt_count += 1
                        for p in preds_020:
                            b = p["boundingBox"]
                            pxc = (b["xMin"] + b["xMax"]) / 2.0
                            pyc = (b["yMin"] + b["yMax"]) / 2.0
                            if abs(pxc - xc) < 0.1 and abs(pyc - yc) < 0.1:
                                small_gt_hits += 1
                                break

                    gt_list.append({
                        "classId": cid,
                        "className": CLASS_NAMES.get(cid, "UNKNOWN"),
                        "boundingBox": {"xMin": round(xmin, 4), "yMin": round(ymin, 4), "xMax": round(xmax, 4), "yMax": round(ymax, 4)},
                        "matched": False,
                    })
                    total_gt += 1
                    gt_by_class[cid] += 1

                gts_by_img[stem] = gt_list

                # Match 0.05 predictions for threshold and calibration analysis
                for cid, cname in CLASS_NAMES.items():
                    cls_preds = [p for p in preds_005 if p.get("classId") == cid]
                    cls_gts = [g for g in gt_list if g["classId"] == cid]
                    sorted_preds = sorted(cls_preds, key=lambda x: x.get("confidence", 0.0), reverse=True)

                    for pred in sorted_preds:
                        conf = float(pred["confidence"])
                        p_box = pred["boundingBox"]
                        best_iou = 0.0
                        best_gt_idx = -1

                        for g_idx, gt in enumerate(cls_gts):
                            if gt["matched"]:
                                continue
                            iou = compute_iou(p_box, gt["boundingBox"])
                            if iou > best_iou:
                                best_iou = iou
                                best_gt_idx = g_idx

                        is_correct = 0
                        matched_iou = 0.0
                        if best_iou >= 0.50 and best_gt_idx != -1:
                            is_correct = 1
                            matched_iou = best_iou
                            cls_gts[best_gt_idx]["matched"] = True

                        raw_detections_all.append({
                            "confidence": conf,
                            "class_id": cid,
                            "class_name": cname,
                            "is_correct": is_correct,
                            "iou": round(matched_iou, 4),
                            "image_stem": stem,
                        })

    # Standard Comprehensive Evaluation Report
    eval_report = ComprehensiveEvaluator.evaluate(
        predictions_by_image=preds_by_img_020,
        ground_truth_by_image=gts_by_img,
        model_id=model_id,
        model_version=model_version,
        dataset_name="candidate_holdout_test_split",
        target_classes=CLASS_NAMES,
    )

    aux_metrics = {
        "total_test_images": len(test_loader.dataset),
        "total_gt_objects": total_gt,
        "gt_by_class": {CLASS_NAMES[cid]: gt_by_class[cid] for cid in CLASS_NAMES},
        "small_objects_count": small_gt_count,
        "small_objects_detected": small_gt_hits,
        "small_objects_hit_rate": round(small_gt_hits / max(1, small_gt_count), 4),
        "empty_background_images": empty_images,
        "empty_images_with_false_alarms": empty_false_alarms,
        "empty_image_false_alarm_rate": round(empty_false_alarms / max(1, empty_images), 4),
    }

    return eval_report.model_dump(), raw_detections_all, aux_metrics


def compute_operating_points_sweep(
    detections: List[Dict[str, Any]],
    total_gt: int,
    thresholds: List[float],
) -> List[Dict[str, Any]]:
    """Computes operating points sweep on holdout detections."""
    confs = np.array([d["confidence"] for d in detections])
    corrects = np.array([d["is_correct"] for d in detections])
    results = []

    for t in thresholds:
        mask = confs >= t
        t_preds = int(np.sum(mask))
        t_tp = int(np.sum(corrects[mask]))
        t_fp = t_preds - t_tp
        t_fn = total_gt - t_tp

        prec = t_tp / max(1, t_preds) if t_preds > 0 else 0.0
        rec = t_tp / max(1, total_gt)
        f1 = (2 * prec * rec) / (prec + rec) if (prec + rec) > 0 else 0.0

        results.append({
            "threshold": round(t, 2),
            "total_detections": t_preds,
            "true_positives": t_tp,
            "false_positives": t_fp,
            "false_negatives": t_fn,
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1_score": round(f1, 4),
        })

    return results


def compute_holdout_calibration(
    confidences: np.ndarray,
    accuracies: np.ndarray,
    num_bins: int = 10,
) -> Dict[str, Any]:
    """Computes ECE, MCE, Brier score on holdout detections."""
    n = len(confidences)
    if n == 0:
        return {"ece": 0.0, "mce": 0.0, "brier_score": 0.0, "reliability_table": []}

    bin_boundaries = np.linspace(0.0, 1.0, num_bins + 1)
    ece, mce = 0.0, 0.0
    rel_table = []

    for m in range(num_bins):
        bin_low = bin_boundaries[m]
        bin_high = bin_boundaries[m + 1]

        if m == num_bins - 1:
            in_bin = (confidences >= bin_low) & (confidences <= bin_high)
        else:
            in_bin = (confidences >= bin_low) & (confidences < bin_high)

        count = int(np.sum(in_bin))
        if count > 0:
            bin_acc = float(np.mean(accuracies[in_bin]))
            bin_conf = float(np.mean(confidences[in_bin]))
            bin_tp = int(np.sum(accuracies[in_bin]))
            bin_fp = count - bin_tp
            gap = abs(bin_acc - bin_conf)
            ece += (count / n) * gap
            if gap > mce:
                mce = gap
            status = "CALIBRATED" if gap <= 0.05 else ("OVERCONFIDENT" if bin_conf > bin_acc else "UNDERCONFIDENT")
        else:
            bin_acc, bin_conf, bin_tp, bin_fp, gap = 0.0, 0.0, 0, 0, 0.0
            status = "EMPTY"

        rel_table.append({
            "bin_idx": m + 1,
            "bin_range": f"[{bin_low:.2f}, {bin_high:.2f}]",
            "count": count,
            "tp": bin_tp,
            "fp": bin_fp,
            "accuracy": round(bin_acc, 4),
            "confidence": round(bin_conf, 4),
            "gap": round(gap, 4),
            "status": status,
        })

    brier = float(np.mean((confidences - accuracies) ** 2))
    return {
        "ece": round(float(ece), 4),
        "mce": round(float(mce), 4),
        "brier_score": round(brier, 4),
        "reliability_table": rel_table,
    }


def main():
    start_time = time.time()
    print("==================================================")
    print("STAGE 6: INDEPENDENT CV HOLDOUT EVALUATION")
    print("==================================================")
    print("Workspace:   ", WORKSPACE_DIR)
    print("Candidate Test Split: ", TEST_IMAGES_DIR)
    print("Output Dir:  ", ARTIFACTS_DIR)

    # Step 1: Verification
    test_manifest = verify_identity_and_integrity()

    device = torch.device("cpu")
    print(f"Execution Device: {device} (16 Logical CPU Cores, 16GB RAM)")

    # Step 2: Ingest Test Dataset
    print("\nIngesting candidate holdout test split (453 images, RAM cached)...")
    test_dataset = Phase84YOLODataset(
        images_dir=TEST_IMAGES_DIR,
        labels_dir=TEST_LABELS_DIR,
        target_size=(256, 256),
        cache_images=True,
    )
    test_loader = DataLoader(
        test_dataset,
        batch_size=16,
        shuffle=False,
        num_workers=0,
        collate_fn=collate_fn,
    )
    print(f"Loaded {len(test_dataset)} holdout test images.")

    # Step 3: Evaluate Frozen Model A
    print("\n==================================================")
    print("EVALUATING FROZEN CANDIDATE MODEL A (ONE-PASS)")
    print("==================================================")
    model_a = BaselineInspectionDetector(num_classes=4, input_size=(256, 256)).to(device)
    ckpt_a = torch.load(MODEL_A_CHECKPOINT_PATH, map_location=device, weights_only=True)
    model_a.load_state_dict(ckpt_a["state_dict"])
    print("Loaded Model A weights successfully.")

    model_a_report, model_a_raw_dets, model_a_aux = evaluate_model_on_holdout(
        model=model_a,
        test_loader=test_loader,
        device=device,
        model_id="Model-A-Baseline-Candidate",
        model_version="1.0.0-stage6-holdout",
    )

    print(f"\nModel A Holdout Evaluation Results (T=0.20, IoU=0.45):")
    print(f"  Total Images:              {model_a_report['totalTestImages']}")
    print(f"  Total Ground Truth:        {model_a_report['totalGroundTruthObjects']}")
    print(f"  Total Predictions:         {model_a_report['totalPredictedObjects']}")
    print(f"  mAP@50:                    {model_a_report['mAP50']:.4f} ({model_a_report['mAP50']*100:.2f}%)")
    print(f"  mAP@50:95:                 {model_a_report['mAP50_95']:.4f}")
    print(f"  Macro Precision:           {model_a_report['meanPrecision']:.4f}")
    print(f"  Macro Recall:              {model_a_report['meanRecall']:.4f}")
    print(f"  Macro F1:                  {model_a_report['meanF1']:.4f}")

    # Micro Metrics
    total_tp = sum(m["truePositives"] for m in model_a_report["perClassMetrics"].values())
    total_fp = sum(m["falsePositives"] for m in model_a_report["perClassMetrics"].values())
    total_fn = sum(m["falseNegatives"] for m in model_a_report["perClassMetrics"].values())
    micro_prec = round(total_tp / max(1, total_tp + total_fp), 4)
    micro_rec = round(total_tp / max(1, total_tp + total_fn), 4)
    micro_f1 = round(2 * micro_prec * micro_rec / max(1e-6, micro_prec + micro_rec), 4)

    print(f"  Micro Precision:           {micro_prec:.4f}")
    print(f"  Micro Recall:              {micro_rec:.4f}")
    print(f"  Micro F1:                  {micro_f1:.4f}")

    print("\nPer-Class Holdout Metrics (Model A):")
    for cname, cm in model_a_report["perClassMetrics"].items():
        print(f"  {cname:18s} | GT={cm['support']:3d} | TP={cm['truePositives']:3d} | FP={cm['falsePositives']:3d} | FN={cm['falseNegatives']:3d} | P={cm['precision']:.4f} | R={cm['recall']:.4f} | F1={cm['f1Score']:.4f} | AP50={cm['ap50']:.4f}")

    # Step 4: Evaluate Reference Baseline (best.pt) on Holdout
    print("\n==================================================")
    print("EVALUATING REFERENCE BASELINE (best.pt) ON HOLDOUT")
    print("==================================================")
    model_baseline = BaselineInspectionDetector(num_classes=4, input_size=(256, 256)).to(device)
    ckpt_base = torch.load(BASELINE_CHECKPOINT_PATH, map_location=device, weights_only=True)
    model_baseline.load_state_dict(ckpt_base["model_state_dict"])
    print("Loaded Baseline best.pt weights successfully.")

    baseline_report, baseline_raw_dets, baseline_aux = evaluate_model_on_holdout(
        model=model_baseline,
        test_loader=test_loader,
        device=device,
        model_id="Baseline-Inspection-Detector-best.pt",
        model_version="0.8.4-reference",
    )

    print(f"\nBaseline best.pt Holdout Evaluation Results (T=0.20, IoU=0.45):")
    print(f"  Total Predictions:         {baseline_report['totalPredictedObjects']}")
    print(f"  mAP@50:                    {baseline_report['mAP50']:.4f} ({baseline_report['mAP50']*100:.2f}%)")
    print(f"  mAP@50:95:                 {baseline_report['mAP50_95']:.4f}")
    print(f"  Macro Precision:           {baseline_report['meanPrecision']:.4f}")
    print(f"  Macro Recall:              {baseline_report['meanRecall']:.4f}")
    print(f"  Macro F1:                  {baseline_report['meanF1']:.4f}")

    # Micro Metrics Baseline
    b_total_tp = sum(m["truePositives"] for m in baseline_report["perClassMetrics"].values())
    b_total_fp = sum(m["falsePositives"] for m in baseline_report["perClassMetrics"].values())
    b_total_fn = sum(m["falseNegatives"] for m in baseline_report["perClassMetrics"].values())
    b_micro_prec = round(b_total_tp / max(1, b_total_tp + b_total_fp), 4)
    b_micro_rec = round(b_total_tp / max(1, b_total_tp + b_total_fn), 4)
    b_micro_f1 = round(2 * b_micro_prec * b_micro_rec / max(1e-6, b_micro_prec + b_micro_rec), 4)

    # Step 5: Exploratory Operating Points Sweep (Diagnostic Only)
    print("\nComputing exploratory operating threshold sweep on holdout (Diagnostic Only)...")
    op_sweep = compute_operating_points_sweep(
        model_a_raw_dets,
        total_gt=model_a_report["totalGroundTruthObjects"],
        thresholds=[0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80],
    )

    # Step 6: Holdout Calibration Assessment
    print("Computing uncalibrated calibration metrics on holdout...")
    confs_a = np.array([d["confidence"] for d in model_a_raw_dets])
    labels_a = np.array([d["is_correct"] for d in model_a_raw_dets])
    cal_metrics_holdout = compute_holdout_calibration(confs_a, labels_a, num_bins=10)
    print(f"Holdout ECE: {cal_metrics_holdout['ece']:.4f} | MCE: {cal_metrics_holdout['mce']:.4f} | Brier: {cal_metrics_holdout['brier_score']:.4f}")

    # Step 7: Save Detailed Artifacts
    # Raw detections
    raw_dets_path = METRICS_DIR / "holdout_predictions_raw.json"
    with open(raw_dets_path, "w", encoding="utf-8") as f:
        json.dump(model_a_raw_dets, f, indent=2)
    print(f"Saved raw holdout predictions: {raw_dets_path}")

    # Summary
    summary_data = {
        "metadata": {
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()),
            "stage": "STAGE 6 — INDEPENDENT CV HOLDOUT EVALUATION",
            "eval_status": "COMPLETED_ONE_PASS",
            "test_manifest_checksum": EXPECTED_TEST_MANIFEST_SHA256,
            "test_images_count": len(test_dataset),
            "test_annotations_count": model_a_report["totalGroundTruthObjects"],
            "model_a_checkpoint_sha256": EXPECTED_MODEL_A_SHA256,
            "baseline_checkpoint_sha256": EXPECTED_BASELINE_SHA256,
            "cv_approval_gate": "NOT_APPROVED",
            "calibration_status": "NOT_CALIBRATED",
            "production_authorization": False,
        },
        "model_a_holdout_results": {
            "evaluation_threshold": 0.20,
            "nms_iou_threshold": 0.45,
            "mAP50": model_a_report["mAP50"],
            "mAP50_95": model_a_report["mAP50_95"],
            "macro_precision": model_a_report["meanPrecision"],
            "macro_recall": model_a_report["meanRecall"],
            "macro_f1": model_a_report["meanF1"],
            "micro_precision": micro_prec,
            "micro_recall": micro_rec,
            "micro_f1": micro_f1,
            "total_predictions": model_a_report["totalPredictedObjects"],
            "total_true_positives": total_tp,
            "total_false_positives": total_fp,
            "total_false_negatives": total_fn,
            "per_class_metrics": model_a_report["perClassMetrics"],
            "confusion_matrix": model_a_report["confusionMatrix"],
            "auxiliary_metrics": model_a_aux,
            "calibration_metrics": cal_metrics_holdout,
            "exploratory_operating_points": op_sweep,
        },
        "baseline_best_pt_holdout_results": {
            "evaluation_threshold": 0.20,
            "nms_iou_threshold": 0.45,
            "mAP50": baseline_report["mAP50"],
            "mAP50_95": baseline_report["mAP50_95"],
            "macro_precision": baseline_report["meanPrecision"],
            "macro_recall": baseline_report["meanRecall"],
            "macro_f1": baseline_report["meanF1"],
            "micro_precision": b_micro_prec,
            "micro_recall": b_micro_rec,
            "micro_f1": b_micro_f1,
            "total_predictions": baseline_report["totalPredictedObjects"],
            "total_true_positives": b_total_tp,
            "total_false_positives": b_total_fp,
            "total_false_negatives": b_total_fn,
            "per_class_metrics": baseline_report["perClassMetrics"],
            "auxiliary_metrics": baseline_aux,
        },
        "generalization_comparison": {
            "model_a_val_map50": 0.0693,
            "model_a_holdout_map50": model_a_report["mAP50"],
            "map50_generalization_delta": round(model_a_report["mAP50"] - 0.0693, 4),
            "baseline_val_map50": 0.0622,
            "baseline_holdout_map50": baseline_report["mAP50"],
            "candidate_vs_baseline_holdout_delta": round(model_a_report["mAP50"] - baseline_report["mAP50"], 4),
        },
        "safety_assessment": {
            "exit_door_recall": model_a_report["perClassMetrics"]["EXIT_DOOR"]["recall"],
            "obstacle_recall": model_a_report["perClassMetrics"]["OBSTACLE"]["recall"],
            "production_gate_decision": "REMAINS NOT_APPROVED",
        },
    }

    summary_file = METRICS_DIR / "holdout_summary.json"
    with open(summary_file, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=2)
    print(f"\nSaved machine-readable holdout summary: {summary_file}")

    print("\n==================================================")
    print("STAGE 6 EXECUTION COMPLETED IN {:.1f}s".format(time.time() - start_time))
    print("==================================================")


if __name__ == "__main__":
    main()
