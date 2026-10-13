"""
FireSphere.ai — CV Experiment Reconciliation Runner
Re-evaluates the Phase 8.4 baseline detector on the 988-image validation dataset
and records both 4-class Macro F1 (all GT classes) and 2-class Macro F1 (supported classes).
"""

import sys
import hashlib
import json
import time
from pathlib import Path
from typing import Any, Dict, List, Tuple

WORKSPACE_ROOT = Path("C:/Users/athar/OneDrive/Desktop/Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
if str(AI_SERVICE_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SERVICE_DIR))

import numpy as np
import torch
from torch.utils.data import DataLoader

from app.models.baseline_detector import BaselineInspectionDetector, CLASS_NAMES
from app.models.nms import apply_non_maximum_suppression, compute_iou
from app.training.phase8_4_trainer import Phase84YOLODataset, collate_fn
from app.training.checkpoints import CheckpointManager

CHECKPOINT_PATH = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"
VAL_IMAGES_DIR = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3" / "yolo" / "images" / "val"
VAL_LABELS_DIR = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3" / "yolo" / "labels" / "val"
OUTPUT_DIR = AI_SERVICE_DIR / "artifacts" / "phase4_cv_reconciliation"


def compute_sha256(path: Path) -> str:
    sha = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            sha.update(chunk)
    return sha.hexdigest()


def extract_raw_detections(
    model: BaselineInspectionDetector,
    loader: DataLoader,
    conf_floor: float = 0.02,
) -> Tuple[Dict[str, List[Dict[str, Any]]], Dict[str, List[Dict[str, Any]]]]:
    raw_preds: Dict[str, List[Dict[str, Any]]] = {}
    gts: Dict[str, List[Dict[str, Any]]] = {}

    with torch.no_grad():
        for images, targets, stems in loader:
            outputs = model(images)
            b, num_anchors, _, s, _ = outputs.shape

            pred_tx = torch.sigmoid(outputs[:, :, 0, :, :])
            pred_ty = torch.sigmoid(outputs[:, :, 1, :, :])
            pred_tw = torch.clamp(outputs[:, :, 2, :, :], -3.0, 3.0)
            pred_th = torch.clamp(outputs[:, :, 3, :, :], -3.0, 3.0)
            pred_obj = torch.sigmoid(outputs[:, :, 4, :, :])
            pred_cls_probs = torch.softmax(outputs[:, :, 5:, :, :], dim=2)

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

                            if confidence >= conf_floor:
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
                raw_preds[stem] = img_dets

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
                gts[stem] = gt_list

    return raw_preds, gts


def evaluate_policy(
    raw_preds: Dict[str, List[Dict[str, Any]]],
    gts: Dict[str, List[Dict[str, Any]]],
    threshold_policy: Dict[str, float],
    iou_nms: float = 0.45,
    eval_iou: float = 0.50,
) -> Dict[str, Any]:
    filtered_preds: Dict[str, List[Dict[str, Any]]] = {}
    for stem, dets in raw_preds.items():
        candidates = [d for d in dets if d["confidence"] >= threshold_policy.get(d["className"], 0.20)]
        filtered_preds[stem] = apply_non_maximum_suppression(candidates, iou_threshold=iou_nms)

    per_class_results = {}
    for cid, cname in CLASS_NAMES.items():
        tp = 0
        fp = 0
        gt_count = 0

        for stem in raw_preds.keys():
            preds = [p for p in filtered_preds.get(stem, []) if p["className"] == cname]
            img_gts = [g for g in gts.get(stem, []) if g["className"] == cname]
            gt_count += len(img_gts)

            sorted_preds = sorted(preds, key=lambda x: x["confidence"], reverse=True)
            matched = set()
            for p in sorted_preds:
                best_iou = 0.0
                best_idx = -1
                for g_idx, gt in enumerate(img_gts):
                    if g_idx in matched:
                        continue
                    iou = compute_iou(p["boundingBox"], gt["boundingBox"])
                    if iou > best_iou:
                        best_iou = iou
                        best_idx = g_idx
                if best_iou >= eval_iou and best_idx != -1:
                    tp += 1
                    matched.add(best_idx)
                else:
                    fp += 1

        fn = gt_count - tp
        prec = round(tp / (tp + fp), 4) if (tp + fp) > 0 else 0.0
        rec = round(tp / (tp + fn), 4) if (tp + fn) > 0 else 0.0
        f1 = round(2 * (prec * rec) / (prec + rec), 4) if (prec + rec) > 0 else 0.0

        per_class_results[cname] = {
            "support": gt_count,
            "tp": tp,
            "fp": fp,
            "fn": fn,
            "precision": prec,
            "recall": rec,
            "f1": f1,
        }

    # 4-class Macro (all GT classes in dataset: FE, ED, ES, OB)
    macro_f1_4class = round(sum(c["f1"] for c in per_class_results.values()) / 4.0, 4)
    # 2-class Macro (supported classes only: FE, ES)
    fe_f1 = per_class_results["FIRE_EXTINGUISHER"]["f1"]
    es_f1 = per_class_results["EXIT_SIGN"]["f1"]
    macro_f1_2class = round((fe_f1 + es_f1) / 2.0, 4)

    return {
        "threshold_policy": threshold_policy,
        "iou_nms": iou_nms,
        "macro_f1_4class": macro_f1_4class,
        "macro_f1_2class": macro_f1_2class,
        "per_class": per_class_results,
    }


def main():
    print("[*] Starting FireSphere.ai CV Experiment Reconciliation Runner...")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    sha = compute_sha256(CHECKPOINT_PATH)
    print(f"[*] Checkpoint SHA-256: {sha}")
    assert sha == "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"

    model = BaselineInspectionDetector(num_classes=4, input_size=(256, 256))
    CheckpointManager.load_checkpoint(CHECKPOINT_PATH, model)
    model.eval()

    ds = Phase84YOLODataset(images_dir=VAL_IMAGES_DIR, labels_dir=VAL_LABELS_DIR, target_size=(256, 256))
    loader = DataLoader(ds, batch_size=32, shuffle=False, collate_fn=collate_fn)

    print(f"[*] Extracting raw predictions on {len(ds)} validation images...")
    t0 = time.time()
    raw_preds, gts = extract_raw_detections(model, loader, conf_floor=0.02)
    print(f"[*] Extraction complete in {time.time() - t0:.2f}s.")

    # Sweep A: Uniform thresholds
    uniform_thresholds = [0.05, 0.08, 0.10, 0.12, 0.15, 0.18, 0.20, 0.25, 0.30]
    sweep_a = []
    print("\n--- Sweep A: Uniform Thresholds ---")
    for tau in uniform_thresholds:
        policy = {cname: tau for cname in CLASS_NAMES.values()}
        res = evaluate_policy(raw_preds, gts, policy, iou_nms=0.45)
        sweep_a.append(res)
        fe = res["per_class"]["FIRE_EXTINGUISHER"]
        es = res["per_class"]["EXIT_SIGN"]
        print(f"tau={tau:.2f} | 4-cls Macro F1={res['macro_f1_4class']:.4f} | 2-cls Macro F1={res['macro_f1_2class']:.4f} | "
              f"FE (P={fe['precision']:.3f}, R={fe['recall']:.3f}, F1={fe['f1']:.3f}, FP={fe['fp']}) | "
              f"ES (P={es['precision']:.3f}, R={es['recall']:.3f}, F1={es['f1']:.3f}, TP={es['tp']})")

    # Sweep B: NMS IoU sweep at tau=0.20
    nms_thresholds = [0.35, 0.40, 0.45, 0.50, 0.55]
    sweep_b = []
    print("\n--- Sweep B: NMS IoU Sweep (tau = 0.20) ---")
    base_policy = {cname: 0.20 for cname in CLASS_NAMES.values()}
    for nms_iou in nms_thresholds:
        res = evaluate_policy(raw_preds, gts, base_policy, iou_nms=nms_iou)
        sweep_b.append(res)
        fe = res["per_class"]["FIRE_EXTINGUISHER"]
        print(f"NMS IoU={nms_iou:.2f} | 4-cls F1={res['macro_f1_4class']:.4f} | 2-cls F1={res['macro_f1_2class']:.4f} | "
              f"FE FP={fe['fp']}, FE TP={fe['tp']}, FE F1={fe['f1']:.3f}")

    # Sweep C: Decoupled Policies
    candidate_policies = [
        {"name": "Baseline Uniform", "policy": {"FIRE_EXTINGUISHER": 0.20, "EXIT_SIGN": 0.20, "EXIT_DOOR": 0.20, "OBSTACLE": 0.20}},
        {"name": "Operational Uniform tau=0.15", "policy": {"FIRE_EXTINGUISHER": 0.15, "EXIT_SIGN": 0.15, "EXIT_DOOR": 0.15, "OBSTACLE": 0.15}},
        {"name": "Decoupled Opt-1 (FE=0.20, ES=0.10)", "policy": {"FIRE_EXTINGUISHER": 0.20, "EXIT_SIGN": 0.10, "EXIT_DOOR": 0.05, "OBSTACLE": 0.05}},
        {"name": "Decoupled Opt-2 (FE=0.22, ES=0.08)", "policy": {"FIRE_EXTINGUISHER": 0.22, "EXIT_SIGN": 0.08, "EXIT_DOOR": 0.05, "OBSTACLE": 0.05}},
        {"name": "Decoupled Opt-3 (FE=0.18, ES=0.10)", "policy": {"FIRE_EXTINGUISHER": 0.18, "EXIT_SIGN": 0.10, "EXIT_DOOR": 0.05, "OBSTACLE": 0.05}},
    ]
    sweep_c = []
    print("\n--- Sweep C: Decoupled Policies ---")
    for cp in candidate_policies:
        res = evaluate_policy(raw_preds, gts, cp["policy"], iou_nms=0.45)
        res["policy_name"] = cp["name"]
        sweep_c.append(res)
        fe = res["per_class"]["FIRE_EXTINGUISHER"]
        es = res["per_class"]["EXIT_SIGN"]
        print(f"Policy: {cp['name']} | 4-cls F1={res['macro_f1_4class']:.4f} | 2-cls F1={res['macro_f1_2class']:.4f} | "
              f"FE F1={fe['f1']:.3f} | ES F1={es['f1']:.3f} (TP={es['tp']})")

    out_data = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "checkpoint_path": str(CHECKPOINT_PATH),
        "checkpoint_sha256": sha,
        "dataset_split": "val",
        "dataset_images": len(ds),
        "sweep_a_uniform": sweep_a,
        "sweep_b_nms_iou": sweep_b,
        "sweep_c_decoupled": sweep_c,
    }
    out_file = OUTPUT_DIR / "reconciliation_results.json"
    out_file.write_text(json.dumps(out_data, indent=2), encoding="utf-8")
    print(f"\n[+] Reconciliation results successfully saved to: {out_file}")


if __name__ == "__main__":
    main()
