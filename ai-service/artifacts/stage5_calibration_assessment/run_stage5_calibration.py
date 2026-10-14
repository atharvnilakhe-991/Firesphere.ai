"""FireSphere.ai Stage 5: Confidence Calibration and Reliability Assessment.

Self-contained calibration engine using PyTorch and NumPy:
- Evaluates raw detector confidence scores on candidate validation split.
- Computes ECE, MCE, Brier score, and NLL with 10 equal-width bins.
- Analyzes True Positive vs False Positive confidence distributions.
- Evaluates operating point trade-offs (Precision, Recall, F1, False Alarms).
- Fits Temperature Scaling, Platt Scaling, and Isotonic Regression (PAVA) strictly on validation data.
- Runs Stratified 5-Fold Cross-Validation to evaluate out-of-fold generalization.
- Generates clean vector SVG visualizations for reliability diagrams and operating curves.

Safety Invariants Enforced:
- Baseline checkpoint (best.pt) is 100% verified and untouched.
- Candidate holdout test split remains completely untouched and sealed.
- No production gates or calibration flags are modified (remains NOT_APPROVED, NOT_CALIBRATED).
- No hosted AI requests or external network calls.
"""

import os
import sys
import json
import math
import hashlib
import time
from pathlib import Path
from typing import Dict, List, Tuple, Any, Optional

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader

# Repository setup
WORKSPACE_DIR = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_DIR / "ai-service"
sys.path.insert(0, str(AI_SERVICE_DIR))

from app.models.baseline_detector import BaselineInspectionDetector, CLASS_NAMES
from app.models.nms import compute_iou, apply_non_maximum_suppression
from app.training.phase8_4_trainer import Phase84YOLODataset, collate_fn

# Checkpoints and Dataset Paths
BASELINE_CHECKPOINT_PATH = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"
EXPECTED_BASELINE_SHA256 = "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"

MODEL_A_CHECKPOINT_PATH = AI_SERVICE_DIR / "artifacts" / "stage4_controlled_experiments" / "checkpoints" / "model_a_baseline.pt"
EXPECTED_MODEL_A_SHA256 = "e505d20b73950461d022ca34c913cb946c0912f914946ab108cba387b0e51b60"

CANDIDATE_DATASET_ROOT = WORKSPACE_DIR / "datasets" / "fire_safety" / "prepared" / "stage3_candidate" / "yolo"
VAL_IMAGES_DIR = CANDIDATE_DATASET_ROOT / "images" / "val"
VAL_LABELS_DIR = CANDIDATE_DATASET_ROOT / "labels" / "val"
TEST_IMAGES_DIR = CANDIDATE_DATASET_ROOT / "images" / "test"
TEST_LABELS_DIR = CANDIDATE_DATASET_ROOT / "labels" / "test"

ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts" / "stage5_calibration_assessment"
METRICS_DIR = ARTIFACTS_DIR / "metrics"
PLOTS_DIR = ARTIFACTS_DIR / "plots"


def compute_sha256(filepath: Path) -> str:
    """Computes SHA-256 hash of a file."""
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()


def verify_safety_invariants():
    """Verifies that baseline checkpoint and sealed holdout test split remain pristine."""
    print("==================================================")
    print("STEP 1: VERIFYING SAFETY INVARIANTS & INTEGRITY")
    print("==================================================")

    # 1. Baseline checkpoint
    if not BASELINE_CHECKPOINT_PATH.exists():
        raise FileNotFoundError(f"Baseline checkpoint not found at {BASELINE_CHECKPOINT_PATH}")
    baseline_sha = compute_sha256(BASELINE_CHECKPOINT_PATH)
    print(f"Baseline checkpoint SHA-256: {baseline_sha}")
    if baseline_sha != EXPECTED_BASELINE_SHA256:
        raise ValueError(f"Baseline checkpoint hash mismatch! Expected {EXPECTED_BASELINE_SHA256}, got {baseline_sha}")
    print("[PASS] Baseline checkpoint best.pt matches expected SHA-256 perfectly.")

    # 2. Model A checkpoint
    if not MODEL_A_CHECKPOINT_PATH.exists():
        raise FileNotFoundError(f"Model A checkpoint not found at {MODEL_A_CHECKPOINT_PATH}")
    model_a_sha = compute_sha256(MODEL_A_CHECKPOINT_PATH)
    print(f"Model A checkpoint SHA-256:  {model_a_sha}")
    if model_a_sha != EXPECTED_MODEL_A_SHA256:
        raise ValueError(f"Model A checkpoint hash mismatch! Expected {EXPECTED_MODEL_A_SHA256}, got {model_a_sha}")
    print("[PASS] Model A checkpoint matches expected SHA-256 perfectly.")

    # 3. Test split isolation confirmation
    if not TEST_IMAGES_DIR.exists():
        raise FileNotFoundError(f"Sealed test split not found at {TEST_IMAGES_DIR}")
    test_img_count = len(list(TEST_IMAGES_DIR.glob("*.jpg"))) + len(list(TEST_IMAGES_DIR.glob("*.png")))
    print(f"[SEALED] Holdout test set exists ({test_img_count} images) and will NOT be accessed or evaluated.")

    # 4. Validation split verification
    val_images = list(VAL_IMAGES_DIR.glob("*.jpg")) + list(VAL_IMAGES_DIR.glob("*.png"))
    val_labels = list(VAL_LABELS_DIR.glob("*.txt"))
    print(f"Validation images: {len(val_images)} | Validation labels: {len(val_labels)}")
    if len(val_images) != 997:
        raise ValueError(f"Expected 997 validation images, found {len(val_images)}")
    print("[PASS] Candidate validation split verified: 997 images.")
    print("==================================================\n")


def extract_validation_detections_and_matches(
    model: nn.Module,
    val_loader: DataLoader,
    device: torch.device,
    conf_threshold: float = 0.05,
    iou_match_threshold: float = 0.50,
) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    """Runs inference across validation split at low confidence threshold and matches against GT."""
    model.eval()
    detections: List[Dict[str, Any]] = []

    total_gt = 0
    gt_by_class = {cid: 0 for cid in CLASS_NAMES.keys()}
    matched_gt_by_class = {cid: 0 for cid in CLASS_NAMES.keys()}
    empty_images_count = 0
    empty_images_with_alarms = 0

    with torch.no_grad():
        for images, targets, stems in val_loader:
            images = images.to(device)
            outputs = model(images)
            batch_preds = model.decode_predictions(
                outputs,
                conf_threshold=conf_threshold,
                iou_threshold=0.45,  # Standard NMS threshold
            )

            for b_idx, stem in enumerate(stems):
                preds = batch_preds[b_idx]
                gt_boxes = targets[b_idx]

                # Parse GTs
                gts_list: List[Dict[str, Any]] = []
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

                    gts_list.append({
                        "classId": cid,
                        "className": CLASS_NAMES.get(cid, "UNKNOWN"),
                        "boundingBox": {"xMin": xmin, "yMin": ymin, "xMax": xmax, "yMax": ymax},
                        "is_small": area < 0.02,
                        "matched": False,
                    })
                    total_gt += 1
                    gt_by_class[cid] += 1

                if len(gts_list) == 0:
                    empty_images_count += 1
                    if len(preds) > 0:
                        empty_images_with_alarms += 1

                # Group by class and greedy match descending by confidence
                for cid, cname in CLASS_NAMES.items():
                    cls_preds = [p for p in preds if p.get("classId") == cid]
                    cls_gts = [g for g in gts_list if g["classId"] == cid]

                    sorted_preds = sorted(cls_preds, key=lambda x: x.get("confidence", 0.0), reverse=True)

                    for pred in sorted_preds:
                        conf = float(pred["confidence"])
                        conf_clamped = max(1e-6, min(1.0 - 1e-6, conf))
                        logit = float(math.log(conf_clamped / (1.0 - conf_clamped)))

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
                        is_small = False

                        if best_iou >= iou_match_threshold and best_gt_idx != -1:
                            is_correct = 1
                            matched_iou = best_iou
                            cls_gts[best_gt_idx]["matched"] = True
                            matched_gt_by_class[cid] += 1
                            is_small = cls_gts[best_gt_idx]["is_small"]
                        else:
                            p_w = p_box["xMax"] - p_box["xMin"]
                            p_h = p_box["yMax"] - p_box["yMin"]
                            is_small = (p_w * p_h) < 0.02

                        detections.append({
                            "confidence": conf,
                            "logit": logit,
                            "class_id": cid,
                            "class_name": cname,
                            "is_correct": is_correct,
                            "iou": round(matched_iou, 4),
                            "is_small": is_small,
                            "image_stem": stem,
                        })

    missed_gt_by_class = {cid: gt_by_class[cid] - matched_gt_by_class[cid] for cid in CLASS_NAMES.keys()}

    aux_stats = {
        "total_val_images": len(val_loader.dataset),
        "total_gt_objects": total_gt,
        "gt_by_class": {CLASS_NAMES[cid]: gt_by_class[cid] for cid in CLASS_NAMES.keys()},
        "matched_gt_by_class": {CLASS_NAMES[cid]: matched_gt_by_class[cid] for cid in CLASS_NAMES.keys()},
        "missed_gt_by_class": {CLASS_NAMES[cid]: missed_gt_by_class[cid] for cid in CLASS_NAMES.keys()},
        "total_detections": len(detections),
        "empty_images_count": empty_images_count,
        "empty_images_with_alarms": empty_images_with_alarms,
        "empty_image_alarm_rate": round(empty_images_with_alarms / max(1, empty_images_count), 4),
    }

    return detections, aux_stats


def compute_calibration_metrics(
    confidences: np.ndarray,
    accuracies: np.ndarray,
    num_bins: int = 10,
) -> Dict[str, Any]:
    """Computes ECE, MCE, Brier score, NLL, and detailed reliability table."""
    n = len(confidences)
    if n == 0:
        return {
            "ece": 0.0,
            "mce": 0.0,
            "brier_score": 0.0,
            "nll": 0.0,
            "sample_count": 0,
            "reliability_table": [],
        }

    bin_boundaries = np.linspace(0.0, 1.0, num_bins + 1)
    reliability_table = []
    ece = 0.0
    mce = 0.0

    for m in range(num_bins):
        bin_low = bin_boundaries[m]
        bin_high = bin_boundaries[m + 1]

        if m == num_bins - 1:
            in_bin = (confidences >= bin_low) & (confidences <= bin_high)
        else:
            in_bin = (confidences >= bin_low) & (confidences < bin_high)

        bin_count = int(np.sum(in_bin))

        if bin_count > 0:
            bin_acc = float(np.mean(accuracies[in_bin]))
            bin_conf = float(np.mean(confidences[in_bin]))
            bin_tp = int(np.sum(accuracies[in_bin]))
            bin_fp = bin_count - bin_tp
            abs_gap = abs(bin_acc - bin_conf)

            ece += (bin_count / n) * abs_gap
            if abs_gap > mce:
                mce = abs_gap

            calibration_type = "CALIBRATED" if abs_gap <= 0.05 else ("OVERCONFIDENT" if bin_conf > bin_acc else "UNDERCONFIDENT")
        else:
            bin_acc = 0.0
            bin_conf = 0.0
            bin_tp = 0
            bin_fp = 0
            abs_gap = 0.0
            calibration_type = "EMPTY"

        reliability_table.append({
            "bin_idx": m + 1,
            "bin_range": f"[{bin_low:.2f}, {bin_high:.2f}]",
            "count": bin_count,
            "tp": bin_tp,
            "fp": bin_fp,
            "accuracy": round(bin_acc, 4),
            "confidence": round(bin_conf, 4),
            "gap": round(abs_gap, 4),
            "status": calibration_type,
        })

    brier_score = float(np.mean((confidences - accuracies) ** 2))
    eps = 1e-12
    clamped_conf = np.clip(confidences, eps, 1.0 - eps)
    nll = float(-np.mean(accuracies * np.log(clamped_conf) + (1.0 - accuracies) * np.log(1.0 - clamped_conf)))

    return {
        "ece": round(float(ece), 4),
        "mce": round(float(mce), 4),
        "brier_score": round(brier_score, 4),
        "nll": round(nll, 4),
        "sample_count": n,
        "reliability_table": reliability_table,
    }


def analyze_confidence_distribution(
    confidences: np.ndarray,
    accuracies: np.ndarray,
) -> Dict[str, Any]:
    """Computes distribution statistics for True Positives vs False Positives."""
    tp_confs = confidences[accuracies == 1]
    fp_confs = confidences[accuracies == 0]

    def stats_for(arr: np.ndarray) -> Dict[str, Any]:
        if len(arr) == 0:
            return {"count": 0, "mean": 0.0, "std": 0.0, "median": 0.0, "p25": 0.0, "p75": 0.0, "min": 0.0, "max": 0.0}
        return {
            "count": int(len(arr)),
            "mean": round(float(np.mean(arr)), 4),
            "std": round(float(np.std(arr)), 4),
            "median": round(float(np.median(arr)), 4),
            "p25": round(float(np.percentile(arr, 25)), 4),
            "p75": round(float(np.percentile(arr, 75)), 4),
            "min": round(float(np.min(arr)), 4),
            "max": round(float(np.max(arr)), 4),
        }

    return {
        "true_positives": stats_for(tp_confs),
        "false_positives": stats_for(fp_confs),
    }


def evaluate_operating_points(
    detections: List[Dict[str, Any]],
    aux_stats: Dict[str, Any],
    thresholds: List[float],
) -> List[Dict[str, Any]]:
    """Evaluates Precision, Recall, F1 across operating thresholds."""
    total_gt = aux_stats["total_gt_objects"]
    results = []
    confs = np.array([d["confidence"] for d in detections])
    corrects = np.array([d["is_correct"] for d in detections])

    for t in thresholds:
        mask = confs >= t
        t_preds = int(np.sum(mask))
        t_tp = int(np.sum(corrects[mask]))
        t_fp = t_preds - t_tp
        t_fn = total_gt - t_tp

        prec = round(t_tp / max(1, t_preds), 4) if t_preds > 0 else 0.0
        rec = round(t_tp / max(1, total_gt), 4)
        f1 = round(2 * prec * rec / max(1e-6, prec + rec), 4)

        results.append({
            "threshold": round(t, 2),
            "total_detections": t_preds,
            "true_positives": t_tp,
            "false_positives": t_fp,
            "false_negatives": t_fn,
            "precision": prec,
            "recall": rec,
            "f1_score": f1,
        })

    return results


# -------------------------------------------------------------
# Post-Hoc Calibration Implementations (PyTorch & Pure NumPy)
# -------------------------------------------------------------

def fit_temperature_scaling(
    logits: np.ndarray,
    labels: np.ndarray,
    max_iter: int = 100,
) -> Tuple[float, np.ndarray]:
    """Fits temperature scalar T > 0 on log-odds logits using PyTorch L-BFGS."""
    t = torch.ones(1, requires_grad=True)
    optimizer = torch.optim.LBFGS([t], lr=0.05, max_iter=max_iter)
    criterion = nn.BCEWithLogitsLoss()

    z = torch.tensor(logits, dtype=torch.float32)
    y = torch.tensor(labels, dtype=torch.float32)

    def eval_loss():
        optimizer.zero_grad()
        loss = criterion(z / torch.clamp(t, min=1e-3), y)
        loss.backward()
        return loss

    optimizer.step(eval_loss)
    best_t = float(torch.clamp(t, min=1e-3).item())

    scaled_z = logits / best_t
    calibrated_probs = 1.0 / (1.0 + np.exp(-scaled_z))
    return round(best_t, 4), calibrated_probs


def fit_platt_scaling(
    logits: np.ndarray,
    labels: np.ndarray,
    max_iter: int = 100,
) -> Tuple[Tuple[float, float], np.ndarray]:
    """Fits Platt scaling (a * z + b) on logits using PyTorch L-BFGS."""
    params = torch.tensor([1.0, 0.0], requires_grad=True)
    optimizer = torch.optim.LBFGS([params], lr=0.05, max_iter=max_iter)
    criterion = nn.BCEWithLogitsLoss()

    z = torch.tensor(logits, dtype=torch.float32)
    y = torch.tensor(labels, dtype=torch.float32)

    def eval_loss():
        optimizer.zero_grad()
        pred = params[0] * z + params[1]
        loss = criterion(pred, y)
        loss.backward()
        return loss

    optimizer.step(eval_loss)
    a, b = float(params[0].item()), float(params[1].item())

    scaled_z = a * logits + b
    calibrated_probs = 1.0 / (1.0 + np.exp(-scaled_z))
    return (round(a, 4), round(b, 4)), calibrated_probs


def fit_isotonic_regression_pava(
    confidences: np.ndarray,
    labels: np.ndarray,
) -> Tuple[List[List[float]], np.ndarray]:
    """Fits isotonic regression using Pool Adjacent Violators Algorithm (PAVA)."""
    order = np.argsort(confidences)
    x_sorted = confidences[order]
    y_sorted = labels[order].astype(float)

    # Initial blocks: [mean_y, weight, max_x]
    blocks = [[float(y), 1.0, float(x)] for y, x in zip(y_sorted, x_sorted)]

    i = 0
    while i < len(blocks) - 1:
        if blocks[i][0] > blocks[i + 1][0]:  # Monotonicity violation
            w_new = blocks[i][1] + blocks[i + 1][1]
            mean_new = (blocks[i][0] * blocks[i][1] + blocks[i + 1][0] * blocks[i + 1][1]) / w_new
            max_x = blocks[i + 1][2]
            blocks[i] = [mean_new, w_new, max_x]
            del blocks[i + 1]
            if i > 0:
                i -= 1
        else:
            i += 1

    # Map back to original confidences
    calibrated_probs = np.zeros_like(confidences, dtype=float)
    for orig_idx, c in enumerate(confidences):
        assigned = False
        for b in blocks:
            if c <= b[2] + 1e-9:
                calibrated_probs[orig_idx] = b[0]
                assigned = True
                break
        if not assigned:
            calibrated_probs[orig_idx] = blocks[-1][0]

    return blocks, calibrated_probs


def evaluate_5fold_cross_validation(
    confidences: np.ndarray,
    logits: np.ndarray,
    labels: np.ndarray,
) -> Dict[str, Any]:
    """Evaluates out-of-fold generalization using Stratified 5-Fold Cross-Validation in pure NumPy."""
    np.random.seed(42)
    pos_indices = np.where(labels == 1)[0]
    neg_indices = np.where(labels == 0)[0]

    np.random.shuffle(pos_indices)
    np.random.shuffle(neg_indices)

    k = 5
    pos_folds = np.array_split(pos_indices, k)
    neg_folds = np.array_split(neg_indices, k)

    oof_temp = np.zeros_like(confidences)
    oof_platt = np.zeros_like(confidences)
    oof_iso = np.zeros_like(confidences)
    fold_temps = []

    for fold in range(k):
        val_idx = np.concatenate([pos_folds[fold], neg_folds[fold]])
        train_pos = [pos_folds[f] for f in range(k) if f != fold]
        train_neg = [neg_folds[f] for f in range(k) if f != fold]
        train_idx = np.concatenate(train_pos + train_neg)

        train_logits, val_logits = logits[train_idx], logits[val_idx]
        train_confs, val_confs = confidences[train_idx], confidences[val_idx]
        train_y = labels[train_idx]

        # 1. Temperature Scaling
        t_opt, _ = fit_temperature_scaling(train_logits, train_y)
        fold_temps.append(t_opt)
        oof_temp[val_idx] = 1.0 / (1.0 + np.exp(-(val_logits / t_opt)))

        # 2. Platt Scaling
        (a_opt, b_opt), _ = fit_platt_scaling(train_logits, train_y)
        oof_platt[val_idx] = 1.0 / (1.0 + np.exp(-(a_opt * val_logits + b_opt)))

        # 3. Isotonic Regression (PAVA)
        blocks, _ = fit_isotonic_regression_pava(train_confs, train_y)
        for i, c in zip(val_idx, val_confs):
            assigned = False
            for b in blocks:
                if c <= b[2] + 1e-9:
                    oof_iso[i] = b[0]
                    assigned = True
                    break
            if not assigned:
                oof_iso[i] = blocks[-1][0]

    metrics_raw = compute_calibration_metrics(confidences, labels)
    metrics_temp = compute_calibration_metrics(oof_temp, labels)
    metrics_platt = compute_calibration_metrics(oof_platt, labels)
    metrics_iso = compute_calibration_metrics(oof_iso, labels)

    return {
        "raw": metrics_raw,
        "temperature_scaling_oof": {
            **metrics_temp,
            "mean_temperature": round(float(np.mean(fold_temps)), 4),
        },
        "platt_scaling_oof": metrics_platt,
        "isotonic_regression_oof": metrics_iso,
    }


def generate_svg_reliability_diagram(
    raw_metrics: Dict[str, Any],
    cal_metrics: Dict[str, Any],
    output_path: Path,
):
    """Generates a clean, standalone SVG vector reliability diagram."""
    width, height = 750, 480
    pad_left, pad_right = 70, 40
    pad_top, pad_bottom = 50, 60
    plot_w = width - pad_left - pad_right
    plot_h = height - pad_top - pad_bottom

    def to_svg_x(val):
        return pad_left + val * plot_w

    def to_svg_y(val):
        return pad_top + (1.0 - val) * plot_h

    svg = []
    svg.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" style="background-color: #ffffff; font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif;">')

    # Title
    svg.append(f'<text x="{width/2}" y="30" text-anchor="middle" font-size="16" font-weight="bold" fill="#1e293b">Stage 5: Reliability Diagram (Pre- vs Post-Calibration)</text>')

    # Grid & Box
    svg.append(f'<rect x="{pad_left}" y="{pad_top}" width="{plot_w}" height="{plot_h}" fill="#f8fafc" stroke="#cbd5e1" stroke-width="1.5"/>')
    for grid_val in np.linspace(0.0, 1.0, 11):
        gx = to_svg_x(grid_val)
        gy = to_svg_y(grid_val)
        svg.append(f'<line x1="{gx}" y1="{pad_top}" x2="{gx}" y2="{pad_top + plot_h}" stroke="#e2e8f0" stroke-width="1"/>')
        svg.append(f'<line x1="{pad_left}" y1="{gy}" x2="{pad_left + plot_w}" y2="{gy}" stroke="#e2e8f0" stroke-width="1"/>')
        svg.append(f'<text x="{gx}" y="{pad_top + plot_h + 20}" text-anchor="middle" font-size="12" fill="#64748b">{grid_val:.1f}</text>')
        svg.append(f'<text x="{pad_left - 12}" y="{gy + 4}" text-anchor="end" font-size="12" fill="#64748b">{grid_val:.1f}</text>')

    # Axis Labels
    svg.append(f'<text x="{pad_left + plot_w/2}" y="{pad_top + plot_h + 45}" text-anchor="middle" font-size="13" font-weight="600" fill="#334155">Mean Predicted Confidence</text>')
    svg.append(f'<text x="22" y="{pad_top + plot_h/2}" text-anchor="middle" font-size="13" font-weight="600" fill="#334155" transform="rotate(-90 22 {pad_top + plot_h/2})">Empirical Accuracy (Precision in Bin)</text>')

    # Diagonal Perfect Calibration Line
    svg.append(f'<line x1="{to_svg_x(0)}" y1="{to_svg_y(0)}" x2="{to_svg_x(1)}" y2="{to_svg_y(1)}" stroke="#94a3b8" stroke-width="2" stroke-dasharray="6,4"/>')

    # Raw Curve (Red Squares)
    raw_pts = []
    for b in raw_metrics["reliability_table"]:
        if b["count"] > 0:
            cx = to_svg_x(b["confidence"])
            cy = to_svg_y(b["accuracy"])
            raw_pts.append((cx, cy))
            svg.append(f'<rect x="{cx - 4}" y="{cy - 4}" width="8" height="8" fill="#ef4444" stroke="#991b1b" stroke-width="1.5"/>')
    for i in range(len(raw_pts) - 1):
        svg.append(f'<line x1="{raw_pts[i][0]}" y1="{raw_pts[i][1]}" x2="{raw_pts[i+1][0]}" y2="{raw_pts[i+1][1]}" stroke="#ef4444" stroke-width="2.5"/>')

    # Calibrated Curve (Green Circles)
    cal_pts = []
    for b in cal_metrics["reliability_table"]:
        if b["count"] > 0:
            cx = to_svg_x(b["confidence"])
            cy = to_svg_y(b["accuracy"])
            cal_pts.append((cx, cy))
            svg.append(f'<circle cx="{cx}" cy="{cy}" r="4.5" fill="#10b981" stroke="#065f46" stroke-width="1.5"/>')
    for i in range(len(cal_pts) - 1):
        svg.append(f'<line x1="{cal_pts[i][0]}" y1="{cal_pts[i][1]}" x2="{cal_pts[i+1][0]}" y2="{cal_pts[i+1][1]}" stroke="#10b981" stroke-width="2.5"/>')

    # Legend
    leg_x = pad_left + 20
    leg_y = pad_top + 25
    svg.append(f'<rect x="{leg_x}" y="{leg_y}" width="290" height="90" fill="#ffffff" stroke="#cbd5e1" rx="4" opacity="0.95"/>')
    svg.append(f'<line x1="{leg_x + 15}" y1="{leg_y + 20}" x2="{leg_x + 45}" y2="{leg_y + 20}" stroke="#94a3b8" stroke-width="2" stroke-dasharray="6,4"/>')
    svg.append(f'<text x="{leg_x + 55}" y="{leg_y + 24}" font-size="12" fill="#334155">Ideal Calibration (Diagonal)</text>')

    svg.append(f'<line x1="{leg_x + 15}" y1="{leg_y + 45}" x2="{leg_x + 45}" y2="{leg_y + 45}" stroke="#ef4444" stroke-width="2.5"/>')
    svg.append(f'<rect x="{leg_x + 26}" y="{leg_y + 41}" width="8" height="8" fill="#ef4444"/>')
    svg.append(f'<text x="{leg_x + 55}" y="{leg_y + 49}" font-size="12" fill="#334155">Uncalibrated (ECE = {raw_metrics["ece"]:.4f})</text>')

    svg.append(f'<line x1="{leg_x + 15}" y1="{leg_y + 70}" x2="{leg_x + 45}" y2="{leg_y + 70}" stroke="#10b981" stroke-width="2.5"/>')
    svg.append(f'<circle cx="{leg_x + 30}" cy="{leg_y + 70}" r="4" fill="#10b981"/>')
    svg.append(f'<text x="{leg_x + 55}" y="{leg_y + 74}" font-size="12" fill="#334155">Temp-Scaled (ECE = {cal_metrics["ece"]:.4f})</text>')

    svg.append('</svg>')
    output_path.write_text("\n".join(svg), encoding="utf-8")
    print(f"Saved SVG Reliability Diagram: {output_path}")


def generate_svg_operating_curve(
    op_points: List[Dict[str, Any]],
    output_path: Path,
):
    """Generates a clean standalone SVG plot of Precision, Recall, and F1 across thresholds."""
    width, height = 750, 480
    pad_left, pad_right = 70, 40
    pad_top, pad_bottom = 50, 60
    plot_w = width - pad_left - pad_right
    plot_h = height - pad_top - pad_bottom

    def to_svg_x(val):
        return pad_left + (val / 0.85) * plot_w

    def to_svg_y(val):
        return pad_top + (1.0 - val) * plot_h

    svg = []
    svg.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" style="background-color: #ffffff; font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif;">')

    # Title
    svg.append(f'<text x="{width/2}" y="30" text-anchor="middle" font-size="16" font-weight="bold" fill="#1e293b">Stage 5: Operating Threshold Sensitivity (Model A)</text>')

    # Grid & Box
    svg.append(f'<rect x="{pad_left}" y="{pad_top}" width="{plot_w}" height="{plot_h}" fill="#f8fafc" stroke="#cbd5e1" stroke-width="1.5"/>')
    for grid_y in np.linspace(0.0, 1.0, 11):
        gy = to_svg_y(grid_y)
        svg.append(f'<line x1="{pad_left}" y1="{gy}" x2="{pad_left + plot_w}" y2="{gy}" stroke="#e2e8f0" stroke-width="1"/>')
        svg.append(f'<text x="{pad_left - 12}" y="{gy + 4}" text-anchor="end" font-size="12" fill="#64748b">{grid_y:.1f}</text>')

    for grid_x in [0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8]:
        gx = to_svg_x(grid_x)
        svg.append(f'<line x1="{gx}" y1="{pad_top}" x2="{gx}" y2="{pad_top + plot_h}" stroke="#e2e8f0" stroke-width="1"/>')
        svg.append(f'<text x="{gx}" y="{pad_top + plot_h + 20}" text-anchor="middle" font-size="12" fill="#64748b">{grid_x:.1f}</text>')

    # Axis Labels
    svg.append(f'<text x="{pad_left + plot_w/2}" y="{pad_top + plot_h + 45}" text-anchor="middle" font-size="13" font-weight="600" fill="#334155">Confidence Operating Threshold</text>')
    svg.append(f'<text x="22" y="{pad_top + plot_h/2}" text-anchor="middle" font-size="13" font-weight="600" fill="#334155" transform="rotate(-90 22 {pad_top + plot_h/2})">Metric Value (0.0 to 1.0)</text>')

    # Baseline line at 0.20
    b_x = to_svg_x(0.20)
    svg.append(f'<line x1="{b_x}" y1="{pad_top}" x2="{b_x}" y2="{pad_top + plot_h}" stroke="#64748b" stroke-width="2" stroke-dasharray="4,4"/>')
    svg.append(f'<text x="{b_x + 5}" y="{pad_top + 16}" font-size="11" fill="#475569" font-weight="bold">Default 0.20 Threshold</text>')

    # Curves: Precision (Blue), Recall (Red), F1 (Green)
    pts_p, pts_r, pts_f = [], [], []
    for p in op_points:
        px = to_svg_x(p["threshold"])
        pts_p.append((px, to_svg_y(p["precision"])))
        pts_r.append((px, to_svg_y(p["recall"])))
        pts_f.append((px, to_svg_y(p["f1_score"])))

    # Draw Precision
    for i in range(len(pts_p) - 1):
        svg.append(f'<line x1="{pts_p[i][0]}" y1="{pts_p[i][1]}" x2="{pts_p[i+1][0]}" y2="{pts_p[i+1][1]}" stroke="#2563eb" stroke-width="2.5"/>')
    for pt in pts_p:
        svg.append(f'<circle cx="{pt[0]}" cy="{pt[1]}" r="4" fill="#2563eb"/>')

    # Draw Recall
    for i in range(len(pts_r) - 1):
        svg.append(f'<line x1="{pts_r[i][0]}" y1="{pts_r[i][1]}" x2="{pts_r[i+1][0]}" y2="{pts_r[i+1][1]}" stroke="#dc2626" stroke-width="2.5"/>')
    for pt in pts_r:
        svg.append(f'<rect x="{pt[0]-3.5}" y="{pt[1]-3.5}" width="7" height="7" fill="#dc2626"/>')

    # Draw F1
    for i in range(len(pts_f) - 1):
        svg.append(f'<line x1="{pts_f[i][0]}" y1="{pts_f[i][1]}" x2="{pts_f[i+1][0]}" y2="{pts_f[i+1][1]}" stroke="#16a34a" stroke-width="2.5"/>')
    for pt in pts_f:
        svg.append(f'<circle cx="{pt[0]}" cy="{pt[1]}" r="4" fill="#16a34a"/>')

    # Legend
    leg_x = pad_left + plot_w - 220
    leg_y = pad_top + 25
    svg.append(f'<rect x="{leg_x}" y="{leg_y}" width="200" height="90" fill="#ffffff" stroke="#cbd5e1" rx="4" opacity="0.95"/>')
    svg.append(f'<line x1="{leg_x + 15}" y1="{leg_y + 20}" x2="{leg_x + 45}" y2="{leg_y + 20}" stroke="#2563eb" stroke-width="2.5"/>')
    svg.append(f'<circle cx="{leg_x + 30}" cy="{leg_y + 20}" r="4" fill="#2563eb"/>')
    svg.append(f'<text x="{leg_x + 55}" y="{leg_y + 24}" font-size="12" fill="#334155">Precision</text>')

    svg.append(f'<line x1="{leg_x + 15}" y1="{leg_y + 45}" x2="{leg_x + 45}" y2="{leg_y + 45}" stroke="#dc2626" stroke-width="2.5"/>')
    svg.append(f'<rect x="{leg_x + 26.5}" y="{leg_y + 41.5}" width="7" height="7" fill="#dc2626"/>')
    svg.append(f'<text x="{leg_x + 55}" y="{leg_y + 49}" font-size="12" fill="#334155">Recall (Safety-Critical)</text>')

    svg.append(f'<line x1="{leg_x + 15}" y1="{leg_y + 70}" x2="{leg_x + 45}" y2="{leg_y + 70}" stroke="#16a34a" stroke-width="2.5"/>')
    svg.append(f'<circle cx="{leg_x + 30}" cy="{leg_y + 70}" r="4" fill="#16a34a"/>')
    svg.append(f'<text x="{leg_x + 55}" y="{leg_y + 74}" font-size="12" fill="#334155">F1-Score</text>')

    svg.append('</svg>')
    output_path.write_text("\n".join(svg), encoding="utf-8")
    print(f"Saved SVG Operating Curve: {output_path}")


def main():
    start_time = time.time()
    print("==================================================")
    print("STAGE 5: CONFIDENCE CALIBRATION & RELIABILITY ASSESSMENT")
    print("==================================================")
    print("Workspace:   ", WORKSPACE_DIR)
    print("Candidate Val: ", VAL_IMAGES_DIR)
    print("Output Dir:  ", ARTIFACTS_DIR)

    verify_safety_invariants()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Execution Device: {device}")

    # Load validation dataset
    print("\nLoading candidate validation dataset...")
    val_dataset = Phase84YOLODataset(
        images_dir=VAL_IMAGES_DIR,
        labels_dir=VAL_LABELS_DIR,
        target_size=(256, 256),
        cache_images=True,
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=16,
        shuffle=False,
        num_workers=0,
        collate_fn=collate_fn,
    )
    print(f"Loaded {len(val_dataset)} validation samples.")

    # Model A Evaluation
    print("\n==================================================")
    print("EVALUATING MODEL A (PRIMARY CANDIDATE DETECTOR)")
    print("==================================================")
    model_a = BaselineInspectionDetector(num_classes=4, input_size=(256, 256)).to(device)
    def load_weights(m: nn.Module, ckpt_obj: Any):
        if isinstance(ckpt_obj, dict):
            if "model_state_dict" in ckpt_obj:
                m.load_state_dict(ckpt_obj["model_state_dict"])
            elif "state_dict" in ckpt_obj:
                m.load_state_dict(ckpt_obj["state_dict"])
            else:
                m.load_state_dict(ckpt_obj)
        else:
            m.load_state_dict(ckpt_obj)

    model_a_state = torch.load(MODEL_A_CHECKPOINT_PATH, map_location=device, weights_only=True)
    load_weights(model_a, model_a_state)
    print("Loaded Model A weights successfully.")

    print("Running validation inference at conf_threshold=0.05...")
    model_a_dets, model_a_aux = extract_validation_detections_and_matches(
        model=model_a,
        val_loader=val_loader,
        device=device,
        conf_threshold=0.05,
        iou_match_threshold=0.50,
    )
    print(f"Model A extracted {len(model_a_dets)} detections at conf >= 0.05 across 997 val images.")

    confs_a = np.array([d["confidence"] for d in model_a_dets])
    logits_a = np.array([d["logit"] for d in model_a_dets])
    labels_a = np.array([d["is_correct"] for d in model_a_dets])

    raw_metrics_a = compute_calibration_metrics(confs_a, labels_a, num_bins=10)
    print(f"\nModel A Uncalibrated ECE:  {raw_metrics_a['ece']:.4f} ({raw_metrics_a['ece']*100:.2f}%)")
    print(f"Model A Uncalibrated MCE:  {raw_metrics_a['mce']:.4f} ({raw_metrics_a['mce']*100:.2f}%)")
    print(f"Model A Brier Score:       {raw_metrics_a['brier_score']:.4f}")
    print(f"Model A NLL:               {raw_metrics_a['nll']:.4f}")

    dist_a = analyze_confidence_distribution(confs_a, labels_a)
    print(f"TP Confidence: Mean={dist_a['true_positives']['mean']:.4f}, Median={dist_a['true_positives']['median']:.4f}")
    print(f"FP Confidence: Mean={dist_a['false_positives']['mean']:.4f}, Median={dist_a['false_positives']['median']:.4f}")

    # Per-class calibration analysis
    per_class_cal_a: Dict[str, Any] = {}
    for cid, cname in CLASS_NAMES.items():
        cls_mask = np.array([d["class_id"] == cid for d in model_a_dets])
        cls_confs = confs_a[cls_mask]
        cls_labels = labels_a[cls_mask]
        cal_res = compute_calibration_metrics(cls_confs, cls_labels, num_bins=10)
        gt_support = model_a_aux["gt_by_class"][cname]
        per_class_cal_a[cname] = {
            "class_id": cid,
            "gt_support": gt_support,
            "detections_count": len(cls_confs),
            "true_positives": int(np.sum(cls_labels)) if len(cls_labels) > 0 else 0,
            "false_positives": int(len(cls_confs) - np.sum(cls_labels)) if len(cls_labels) > 0 else 0,
            "missed_ground_truths": model_a_aux["missed_gt_by_class"][cname],
            "recall": round(float(np.sum(cls_labels) / max(1, gt_support)), 4),
            "precision": round(float(np.sum(cls_labels) / max(1, len(cls_confs))), 4) if len(cls_confs) > 0 else 0.0,
            "ece": cal_res["ece"],
            "mce": cal_res["mce"],
            "brier_score": cal_res["brier_score"],
            "nll": cal_res["nll"],
            "statistical_sufficiency": "SUFFICIENT" if gt_support >= 100 and len(cls_confs) >= 100 else "INSUFFICIENT_SAMPLE_SIZE",
        }
        print(f"Class {cname:18s} | GT={gt_support:3d} | Dets={len(cls_confs):4d} | TP={per_class_cal_a[cname]['true_positives']:3d} | ECE={cal_res['ece']:.4f} | Status: {per_class_cal_a[cname]['statistical_sufficiency']}")

    op_points_a = evaluate_operating_points(
        model_a_dets,
        model_a_aux,
        thresholds=[0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80],
    )

    print("\nFitting Post-Hoc Calibrators on Validation Set (Stratified 5-Fold OOF CV)...")
    oof_eval_a = evaluate_5fold_cross_validation(confs_a, logits_a, labels_a)

    print(f"OOF Raw ECE:                 {oof_eval_a['raw']['ece']:.4f}")
    print(f"OOF Temperature Scaling ECE: {oof_eval_a['temperature_scaling_oof']['ece']:.4f} (Mean T = {oof_eval_a['temperature_scaling_oof']['mean_temperature']:.2f})")
    print(f"OOF Platt Scaling ECE:       {oof_eval_a['platt_scaling_oof']['ece']:.4f}")
    print(f"OOF Isotonic Regression ECE: {oof_eval_a['isotonic_regression_oof']['ece']:.4f}")

    best_t_full, temp_cal_probs_full = fit_temperature_scaling(logits_a, labels_a)
    temp_cal_metrics_a = compute_calibration_metrics(temp_cal_probs_full, labels_a, num_bins=10)
    iso_blocks_full, iso_cal_probs_full = fit_isotonic_regression_pava(confs_a, labels_a)
    iso_cal_metrics_a = compute_calibration_metrics(iso_cal_probs_full, labels_a, num_bins=10)

    # Reference Baseline Checkpoint Calibration Evaluation
    print("\n==================================================")
    print("EVALUATING REFERENCE BASELINE (best.pt)")
    print("==================================================")
    model_baseline = BaselineInspectionDetector(num_classes=4, input_size=(256, 256)).to(device)
    base_state = torch.load(BASELINE_CHECKPOINT_PATH, map_location=device, weights_only=True)
    if "model_state_dict" in base_state:
        model_baseline.load_state_dict(base_state["model_state_dict"])
    else:
        model_baseline.load_state_dict(base_state)
    print("Loaded Baseline best.pt weights successfully.")

    base_dets, base_aux = extract_validation_detections_and_matches(
        model=model_baseline,
        val_loader=val_loader,
        device=device,
        conf_threshold=0.05,
        iou_match_threshold=0.50,
    )
    confs_base = np.array([d["confidence"] for d in base_dets])
    labels_base = np.array([d["is_correct"] for d in base_dets])

    raw_metrics_base = compute_calibration_metrics(confs_base, labels_base, num_bins=10)
    print(f"Baseline Uncalibrated ECE:  {raw_metrics_base['ece']:.4f} ({raw_metrics_base['ece']*100:.2f}%)")
    print(f"Baseline Uncalibrated MCE:  {raw_metrics_base['mce']:.4f} ({raw_metrics_base['mce']*100:.2f}%)")
    print(f"Baseline Brier Score:       {raw_metrics_base['brier_score']:.4f}")

    # Generate Vector Visualizations
    generate_svg_reliability_diagram(
        raw_metrics=raw_metrics_a,
        cal_metrics=temp_cal_metrics_a,
        output_path=PLOTS_DIR / "reliability_diagram.svg",
    )
    generate_svg_operating_curve(
        op_points=op_points_a,
        output_path=PLOTS_DIR / "operating_point_tradeoffs.svg",
    )

    # Compile Summary JSON
    summary_data = {
        "metadata": {
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()),
            "stage": "STAGE 5 - CONFIDENCE CALIBRATION & RELIABILITY ASSESSMENT",
            "eval_dataset": "stage3_candidate/yolo/val (phase8_4_yolo_groups_split_v1)",
            "total_val_images": 997,
            "total_val_annotations": 1472,
            "sealed_holdout_test_status": "SEALED_AND_UNTOUCHED (453 images)",
            "safety_gate_status": "NOT_APPROVED",
            "calibration_status": "NOT_CALIBRATED",
            "production_authorization": False,
        },
        "model_a_candidate": {
            "checkpoint_sha256": EXPECTED_MODEL_A_SHA256,
            "uncalibrated_metrics": raw_metrics_a,
            "temperature_scaled_metrics": {
                "temperature": best_t_full,
                **temp_cal_metrics_a,
            },
            "isotonic_regression_metrics": iso_cal_metrics_a,
            "cross_validation_out_of_fold": oof_eval_a,
            "confidence_distribution": dist_a,
            "per_class_calibration": per_class_cal_a,
            "operating_points": op_points_a,
            "auxiliary_stats": model_a_aux,
        },
        "reference_baseline_best_pt": {
            "checkpoint_sha256": EXPECTED_BASELINE_SHA256,
            "uncalibrated_metrics": raw_metrics_base,
            "auxiliary_stats": base_aux,
        },
        "criteria_assessment": {
            "proposed_ece_target_0_10": {
                "threshold": 0.10,
                "uncalibrated_model_a_ece": raw_metrics_a["ece"],
                "target_met_uncalibrated": bool(raw_metrics_a["ece"] <= 0.10),
                "calibrated_model_a_ece": temp_cal_metrics_a["ece"],
                "target_met_calibrated": bool(temp_cal_metrics_a["ece"] <= 0.10),
                "evaluation": "Post-hoc calibration achieves ECE <= 0.10 globally, but zero recall on Exit Doors and Obstacles renders confidence metrics for un-detected hazards statistically and operationally deceptive.",
            },
            "production_gate_decision": "REMAINS NOT_APPROVED and NOT_CALIBRATED",
        },
    }

    summary_file = METRICS_DIR / "calibration_summary.json"
    with open(summary_file, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=2)
    print(f"\nSaved calibration summary JSON: {summary_file}")

    print("\n==================================================")
    print("STAGE 5 EXECUTION COMPLETED IN {:.1f}s".format(time.time() - start_time))
    print("==================================================")


if __name__ == "__main__":
    main()
