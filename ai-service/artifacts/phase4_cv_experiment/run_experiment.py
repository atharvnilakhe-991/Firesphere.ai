"""
FireSphere.ai — Phase 4 CV Quality Investigation Controlled Experiment
Evaluates inference thresholding and NMS sensitivity strictly on the validation split.

Hypothesis:
1. Baseline uniform threshold tau=0.20 causes severe recall collapse on EXIT_SIGN (whose confidence ceiling is ~0.33)
   while admitting high false alarms on FIRE_EXTINGUISHER.
2. EXIT_DOOR and OBSTACLE have confidence ceilings below 0.042 due to severe class scarcity (>95:1 imbalance),
   preventing positive detections at any operational threshold.
3. Class-specific thresholding on validation data achieves a superior Pareto operating point than a rigid global threshold.
"""

import sys
from pathlib import Path

WORKSPACE_ROOT = Path("C:/Users/athar/OneDrive/Desktop/Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
if str(AI_SERVICE_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SERVICE_DIR))

import json
import time
from typing import Any, Dict, List, Tuple
import numpy as np
import torch
from torch.utils.data import DataLoader

from app.models.baseline_detector import BaselineInspectionDetector, CLASS_NAMES
from app.models.nms import apply_non_maximum_suppression, compute_iou
from app.training.phase8_4_trainer import Phase84YOLODataset, collate_fn
from app.training.checkpoints import CheckpointManager

WORKSPACE_ROOT = Path("C:/Users/athar/OneDrive/Desktop/Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
CHECKPOINT_PATH = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"
VAL_IMAGES_DIR = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3" / "yolo" / "images" / "val"
VAL_LABELS_DIR = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3" / "yolo" / "labels" / "val"
EXPERIMENT_OUT_DIR = AI_SERVICE_DIR / "artifacts" / "phase4_cv_experiment"


def extract_raw_predictions_and_gt(
    model: BaselineInspectionDetector,
    loader: DataLoader,
    base_conf_thresh: float = 0.02,
) -> Tuple[Dict[str, List[Dict[str, Any]]], Dict[str, List[Dict[str, Any]]]]:
    """Extracts raw bounding box detections at low confidence threshold for multi-threshold simulation."""
    raw_preds_by_img: Dict[str, List[Dict[str, Any]]] = {}
    gts_by_img: Dict[str, List[Dict[str, Any]]] = {}

    with torch.no_grad():
        for images, targets, stems in loader:
            outputs = model(images)
            b, num_anchors, _, s, _ = outputs.shape

            pred_tx = torch.sigmoid(outputs[:, :, 0, :, :])
            pred_ty = torch.sigmoid(outputs[:, :, 1, :, :])
            pred_tw = torch.clamp(outputs[:, :, 2, :, :], -3.0, 3.0)
            pred_th = torch.clamp(outputs[:, :, 3, :, :], -3.0, 3.0)
            pred_obj = torch.sigmoid(outputs[:, :, 4, :, :])
            pred_cls_logits = outputs[:, :, 5:, :, :]
            pred_cls_probs = torch.softmax(pred_cls_logits, dim=2)

            col_grid = torch.arange(s).view(1, 1, 1, s).expand(b, num_anchors, s, s)
            row_grid = torch.arange(s).view(1, 1, s, 1).expand(b, num_anchors, s, s)

            pred_xc = (col_grid + pred_tx) / float(s)
            pred_yc = (row_grid + pred_ty) / float(s)

            anchor_tensor = torch.tensor(model.anchors, dtype=torch.float32)
            anc_w = anchor_tensor[:, 0].view(1, num_anchors, 1, 1)
            anc_h = anchor_tensor[:, 1].view(1, num_anchors, 1, 1)

            pred_w = torch.clamp(anc_w * torch.exp(pred_tw), 0.005, 1.0)
            pred_h = torch.clamp(anc_h * torch.exp(pred_th), 0.005, 1.0)

            for b_idx in range(b):
                stem = stems[b_idx]
                img_dets: List[Dict[str, Any]] = []

                for a_idx in range(num_anchors):
                    for r in range(s):
                        for c in range(s):
                            obj_conf = float(pred_obj[b_idx, a_idx, r, c].item())
                            cls_p = pred_cls_probs[b_idx, a_idx, :, r, c].numpy()
                            best_cid = int(np.argmax(cls_p))
                            best_p = float(cls_p[best_cid])
                            confidence = obj_conf * best_p

                            if confidence >= base_conf_thresh:
                                xc = float(pred_xc[b_idx, a_idx, r, c].item())
                                yc = float(pred_yc[b_idx, a_idx, r, c].item())
                                w = float(pred_w[b_idx, a_idx, r, c].item())
                                h = float(pred_h[b_idx, a_idx, r, c].item())
                                xmin = max(0.0, xc - w / 2.0)
                                ymin = max(0.0, yc - h / 2.0)
                                xmax = min(1.0, xc + w / 2.0)
                                ymax = min(1.0, yc + h / 2.0)

                                if xmax > xmin and ymax > ymin:
                                    img_dets.append({
                                        "classId": best_cid,
                                        "className": CLASS_NAMES.get(best_cid, "UNKNOWN"),
                                        "confidence": round(confidence, 4),
                                        "boundingBox": {
                                            "xMin": round(xmin, 4),
                                            "yMin": round(ymin, 4),
                                            "xMax": round(xmax, 4),
                                            "yMax": round(ymax, 4),
                                        },
                                    })

                raw_preds_by_img[stem] = img_dets

                # Parse GT
                gt_boxes_tensor = targets[b_idx]
                gt_list: List[Dict[str, Any]] = []
                for k in range(len(gt_boxes_tensor)):
                    cid = int(gt_boxes_tensor[k, 0].item())
                    xc = gt_boxes_tensor[k, 1].item()
                    yc = gt_boxes_tensor[k, 2].item()
                    w = gt_boxes_tensor[k, 3].item()
                    h = gt_boxes_tensor[k, 4].item()
                    gt_list.append({
                        "className": CLASS_NAMES.get(cid, "UNKNOWN"),
                        "boundingBox": {
                            "xMin": round(max(0.0, xc - w / 2.0), 4),
                            "yMin": round(max(0.0, yc - h / 2.0), 4),
                            "xMax": round(min(1.0, xc + w / 2.0), 4),
                            "yMax": round(min(1.0, yc + h / 2.0), 4),
                        },
                    })
                gts_by_img[stem] = gt_list

    return raw_preds_by_img, gts_by_img


def evaluate_threshold_policy(
    raw_preds_by_img: Dict[str, List[Dict[str, Any]]],
    gts_by_img: Dict[str, List[Dict[str, Any]]],
    threshold_policy: Dict[str, float],
    iou_nms: float = 0.45,
    eval_iou: float = 0.50,
) -> Dict[str, Any]:
    """Applies threshold policy + NMS, then computes matching against ground truth."""
    filtered_preds_by_img: Dict[str, List[Dict[str, Any]]] = {}

    for stem, dets in raw_preds_by_img.items():
        candidates = []
        for d in dets:
            cname = d["className"]
            thresh = threshold_policy.get(cname, 0.20)
            if d["confidence"] >= thresh:
                candidates.append(d)
        filtered_preds_by_img[stem] = apply_non_maximum_suppression(candidates, iou_threshold=iou_nms)

    # Compute matches per class
    results: Dict[str, Dict[str, Any]] = {}
    total_tp = 0
    total_fp = 0
    total_fn = 0
    total_gt = 0

    for cid, cname in CLASS_NAMES.items():
        tp = 0
        fp = 0
        gt_count = 0

        for stem in raw_preds_by_img.keys():
            preds = [p for p in filtered_preds_by_img.get(stem, []) if p["className"] == cname]
            gts = [g for g in gts_by_img.get(stem, []) if g["className"] == cname]
            gt_count += len(gts)

            sorted_preds = sorted(preds, key=lambda x: x["confidence"], reverse=True)
            matched_gts = set()

            for p in sorted_preds:
                best_iou = 0.0
                best_idx = -1
                for g_idx, gt in enumerate(gts):
                    if g_idx in matched_gts:
                        continue
                    iou = compute_iou(p["boundingBox"], gt["boundingBox"])
                    if iou > best_iou:
                        best_iou = iou
                        best_idx = g_idx

                if best_iou >= eval_iou and best_idx != -1:
                    tp += 1
                    matched_gts.add(best_idx)
                else:
                    fp += 1

        fn = gt_count - tp
        prec = round(tp / (tp + fp), 4) if (tp + fp) > 0 else 0.0
        rec = round(tp / (tp + fn), 4) if (tp + fn) > 0 else 0.0
        f1 = round(2 * (prec * rec) / (prec + rec), 4) if (prec + rec) > 0 else 0.0

        results[cname] = {
            "support": gt_count,
            "tp": tp,
            "fp": fp,
            "fn": fn,
            "precision": prec,
            "recall": rec,
            "f1": f1,
        }
        total_tp += tp
        total_fp += fp
        total_fn += fn
        total_gt += gt_count

    active_classes = [c for c in results.values() if c["support"] > 0]
    macro_prec = round(sum(c["precision"] for c in active_classes) / len(active_classes), 4)
    macro_rec = round(sum(c["recall"] for c in active_classes) / len(active_classes), 4)
    macro_f1 = round(sum(c["f1"] for c in active_classes) / len(active_classes), 4)

    return {
        "threshold_policy": threshold_policy,
        "iou_nms": iou_nms,
        "macro_precision": macro_prec,
        "macro_recall": macro_rec,
        "macro_f1": macro_f1,
        "per_class": results,
    }


def run_experiment():
    print("[*] Starting Phase 4 Controlled CV Quality Experiment...")
    t0 = time.time()

    model = BaselineInspectionDetector(num_classes=4, input_size=(256, 256))
    CheckpointManager.load_checkpoint(CHECKPOINT_PATH, model)
    model.eval()

    ds = Phase84YOLODataset(
        images_dir=VAL_IMAGES_DIR,
        labels_dir=VAL_LABELS_DIR,
        target_size=(256, 256),
        cache_images=False,
    )
    loader = DataLoader(ds, batch_size=32, shuffle=False, collate_fn=collate_fn, num_workers=0)

    print(f"[*] Extracting raw predictions on {len(ds)} validation images at conf>=0.02...")
    raw_preds, gts = extract_raw_predictions_and_gt(model, loader, base_conf_thresh=0.02)
    print(f"[*] Extraction complete in {time.time() - t0:.2f}s.")

    # 1. Sweep A: Uniform Confidence Thresholds
    uniform_thresholds = [0.05, 0.08, 0.10, 0.12, 0.15, 0.18, 0.20, 0.25, 0.30]
    sweep_a_results = []
    print("\n--- SWEEP A: Uniform Confidence Threshold Sweep (IoU NMS = 0.45) ---")
    for thresh in uniform_thresholds:
        policy = {cname: thresh for cname in CLASS_NAMES.values()}
        res = evaluate_threshold_policy(raw_preds, gts, policy, iou_nms=0.45)
        sweep_a_results.append(res)
        fe = res["per_class"]["FIRE_EXTINGUISHER"]
        es = res["per_class"]["EXIT_SIGN"]
        print(f"tau={thresh:0.2f} | Macro F1={res['macro_f1']:.4f} | "
              f"FE (P={fe['precision']:.3f}, R={fe['recall']:.3f}, F1={fe['f1']:.3f}, FP={fe['fp']}) | "
              f"ES (P={es['precision']:.3f}, R={es['recall']:.3f}, F1={es['f1']:.3f}, TP={es['tp']})")

    # 2. Sweep B: NMS IoU Threshold Sweep at baseline tau=0.20
    nms_thresholds = [0.35, 0.40, 0.45, 0.50, 0.55]
    sweep_b_results = []
    print("\n--- SWEEP B: NMS IoU Sweep (tau = 0.20) ---")
    base_policy = {cname: 0.20 for cname in CLASS_NAMES.values()}
    for nms_iou in nms_thresholds:
        res = evaluate_threshold_policy(raw_preds, gts, base_policy, iou_nms=nms_iou)
        sweep_b_results.append(res)
        fe = res["per_class"]["FIRE_EXTINGUISHER"]
        print(f"NMS IoU={nms_iou:.2f} | Macro F1={res['macro_f1']:.4f} | "
              f"FE (P={fe['precision']:.3f}, R={fe['recall']:.3f}, F1={fe['f1']:.3f}, FP={fe['fp']})")

    # 3. Sweep C: Class-Specific Decoupled Thresholds
    # FE ceiling is 0.76 (safe at 0.18-0.22); ES ceiling is 0.33 (needs lower 0.08-0.12)
    candidate_policies = [
        {"name": "Baseline Uniform", "policy": {"FIRE_EXTINGUISHER": 0.20, "EXIT_SIGN": 0.20, "EXIT_DOOR": 0.20, "OBSTACLE": 0.20}},
        {"name": "Operational Uniform tau=0.15", "policy": {"FIRE_EXTINGUISHER": 0.15, "EXIT_SIGN": 0.15, "EXIT_DOOR": 0.15, "OBSTACLE": 0.15}},
        {"name": "Decoupled Opt-1 (FE=0.20, ES=0.10)", "policy": {"FIRE_EXTINGUISHER": 0.20, "EXIT_SIGN": 0.10, "EXIT_DOOR": 0.05, "OBSTACLE": 0.05}},
        {"name": "Decoupled Opt-2 (FE=0.22, ES=0.08)", "policy": {"FIRE_EXTINGUISHER": 0.22, "EXIT_SIGN": 0.08, "EXIT_DOOR": 0.05, "OBSTACLE": 0.05}},
        {"name": "Decoupled Opt-3 (FE=0.18, ES=0.10)", "policy": {"FIRE_EXTINGUISHER": 0.18, "EXIT_SIGN": 0.10, "EXIT_DOOR": 0.05, "OBSTACLE": 0.05}},
    ]
    sweep_c_results = []
    print("\n--- SWEEP C: Decoupled Class-Specific Operating Policies ---")
    for cp in candidate_policies:
        res = evaluate_threshold_policy(raw_preds, gts, cp["policy"], iou_nms=0.45)
        res["policy_name"] = cp["name"]
        sweep_c_results.append(res)
        fe = res["per_class"]["FIRE_EXTINGUISHER"]
        es = res["per_class"]["EXIT_SIGN"]
        ed = res["per_class"]["EXIT_DOOR"]
        obs = res["per_class"]["OBSTACLE"]
        print(f"Policy: {cp['name']} | Macro F1={res['macro_f1']:.4f} | "
              f"FE F1={fe['f1']:.3f} | ES F1={es['f1']:.3f} (TP={es['tp']}) | "
              f"Door TP={ed['tp']} | Obstacle TP={obs['tp']}")

    # Save artifacts
    experiment_data = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "experiment_title": "Validation-Set Confidence & NMS Sensitivity Analysis",
        "checkpoint_evaluated": str(CHECKPOINT_PATH),
        "validation_dataset": str(VAL_IMAGES_DIR),
        "validation_samples": len(ds),
        "sweep_a_uniform_thresholds": sweep_a_results,
        "sweep_b_nms_iou_thresholds": sweep_b_results,
        "sweep_c_decoupled_policies": sweep_c_results,
    }

    out_file = EXPERIMENT_OUT_DIR / "experiment_results.json"
    out_file.write_text(json.dumps(experiment_data, indent=2), encoding="utf-8")
    print(f"\n[+] Saved detailed experimental metrics to: {out_file}")


if __name__ == "__main__":
    run_experiment()
