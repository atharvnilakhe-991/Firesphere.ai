import sys
from pathlib import Path
WORKSPACE_ROOT = Path("C:/Users/athar/OneDrive/Desktop/Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
sys.path.insert(0, str(AI_SERVICE_DIR))

import json
import torch
from torch.utils.data import DataLoader
from app.models.baseline_detector import BaselineInspectionDetector, CLASS_NAMES
from app.models.nms import apply_non_maximum_suppression, compute_iou
from app.training.phase8_4_trainer import Phase84YOLODataset, collate_fn
from app.training.checkpoints import CheckpointManager

CHECKPOINT_PATH = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"
VAL_IMAGES_DIR = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3" / "yolo" / "images" / "val"
VAL_LABELS_DIR = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3" / "yolo" / "labels" / "val"

model = BaselineInspectionDetector(num_classes=4, input_size=(256, 256))
CheckpointManager.load_checkpoint(CHECKPOINT_PATH, model)
model.eval()

ds = Phase84YOLODataset(images_dir=VAL_IMAGES_DIR, labels_dir=VAL_LABELS_DIR, target_size=(256, 256))
loader = DataLoader(ds, batch_size=32, shuffle=False, collate_fn=collate_fn)

# Suppose during extraction, decode_predictions was used with conf_threshold=0.02 and iou_threshold=0.45
# OR decode_predictions was called at tau=0.20 and iou_threshold=0.45!
# Let's check:
print("[*] Testing extraction with decode_predictions at conf=0.02, iou=0.45...")
dets_pre_nms = {}
gts_by_img = {}

with torch.no_grad():
    for images, targets, stems in loader:
        outputs = model(images)
        # What if decode_predictions was used?
        batch_dets = model.decode_predictions(outputs, conf_threshold=0.02, iou_threshold=0.45)
        for stem, dets, target in zip(stems, batch_dets, targets):
            dets_pre_nms[stem] = dets
            gt_list = []
            for k in range(len(target)):
                cid = int(target[k, 0].item())
                xc = target[k, 1].item()
                yc = target[k, 2].item()
                w = target[k, 3].item()
                h = target[k, 4].item()
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

print("[*] Done extracting. Evaluating NMS sweep at tau=0.20 on pre-NMS'd detections:")
for nms_iou in [0.35, 0.40, 0.45, 0.50, 0.55]:
    fe_tp = 0
    fe_fp = 0
    for stem, dets in dets_pre_nms.items():
        candidates = [d for d in dets if d["confidence"] >= 0.20]
        suppressed = apply_non_maximum_suppression(candidates, iou_threshold=nms_iou)
        preds = [p for p in suppressed if p["className"] == "FIRE_EXTINGUISHER"]
        gts = [g for g in gts_by_img[stem] if g["className"] == "FIRE_EXTINGUISHER"]
        
        sorted_preds = sorted(preds, key=lambda x: x["confidence"], reverse=True)
        matched = set()
        for p in sorted_preds:
            best_iou = 0.0
            best_idx = -1
            for g_idx, gt in enumerate(gts):
                if g_idx in matched:
                    continue
                iou = compute_iou(p["boundingBox"], gt["boundingBox"])
                if iou > best_iou:
                    best_iou = iou
                    best_idx = g_idx
            if best_iou >= 0.50 and best_idx != -1:
                fe_tp += 1
                matched.add(best_idx)
            else:
                fe_fp += 1
    print(f"Pre-filtered NMS at 0.45 -> NMS IoU={nms_iou:.2f}: FE FP={fe_fp}, FE TP={fe_tp}")

# Also check uniform sweep at tau=0.05 on pre-filtered detections:
print("\n[*] Checking Sweep A at tau=0.05 on pre-filtered detections:")
for tau in [0.05, 0.08, 0.10, 0.12, 0.15, 0.18, 0.20, 0.25, 0.30]:
    fe_tp = 0
    fe_fp = 0
    es_tp = 0
    es_fp = 0
    for stem, dets in dets_pre_nms.items():
        candidates = [d for d in dets if d["confidence"] >= tau]
        suppressed = apply_non_maximum_suppression(candidates, iou_threshold=0.45)
        preds_fe = [p for p in suppressed if p["className"] == "FIRE_EXTINGUISHER"]
        preds_es = [p for p in suppressed if p["className"] == "EXIT_SIGN"]
        gts_fe = [g for g in gts_by_img[stem] if g["className"] == "FIRE_EXTINGUISHER"]
        gts_es = [g for g in gts_by_img[stem] if g["className"] == "EXIT_SIGN"]

        # FE matching
        sorted_fe = sorted(preds_fe, key=lambda x: x["confidence"], reverse=True)
        matched = set()
        for p in sorted_fe:
            best_iou, best_idx = 0.0, -1
            for g_idx, gt in enumerate(gts_fe):
                if g_idx in matched: continue
                iou = compute_iou(p["boundingBox"], gt["boundingBox"])
                if iou > best_iou: best_iou, best_idx = iou, g_idx
            if best_iou >= 0.50 and best_idx != -1:
                fe_tp += 1
                matched.add(best_idx)
            else: fe_fp += 1

        # ES matching
        sorted_es = sorted(preds_es, key=lambda x: x["confidence"], reverse=True)
        matched_es = set()
        for p in sorted_es:
            best_iou, best_idx = 0.0, -1
            for g_idx, gt in enumerate(gts_es):
                if g_idx in matched_es: continue
                iou = compute_iou(p["boundingBox"], gt["boundingBox"])
                if iou > best_iou: best_iou, best_idx = iou, g_idx
            if best_iou >= 0.50 and best_idx != -1:
                es_tp += 1
                matched_es.add(best_idx)
            else: es_fp += 1

    fe_prec = fe_tp / (fe_tp + fe_fp) if (fe_tp + fe_fp) > 0 else 0
    fe_rec = fe_tp / 948
    fe_f1 = 2 * fe_prec * fe_rec / (fe_prec + fe_rec) if (fe_prec + fe_rec) > 0 else 0

    es_prec = es_tp / (es_tp + es_fp) if (es_tp + es_fp) > 0 else 0
    es_rec = es_tp / 498
    es_f1 = 2 * es_prec * es_rec / (es_prec + es_rec) if (es_prec + es_rec) > 0 else 0

    macro_f1_4 = (fe_f1 + es_f1) / 4
    macro_f1_2 = (fe_f1 + es_f1) / 2
    print(f"tau={tau:.2f} | 4-class F1={macro_f1_4:.4f} | 2-class F1={macro_f1_2:.4f} | FE FP={fe_fp}, FE TP={fe_tp} | ES TP={es_tp}, ES FP={es_fp}")
