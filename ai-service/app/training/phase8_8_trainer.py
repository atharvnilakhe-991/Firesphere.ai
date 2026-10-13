"""FireSphere.ai — Phase 8.8 Controlled Model Trainer & Comparative Evaluator.

Executes:
1. Controlled training of firesphere-perception-candidate-v0.8.8 using BaselineInspectionDetector
   on the audited Phase 8.8 prepared training dataset.
2. Epoch-level loss logging and best validation checkpoint selection.
3. Validation-set evaluation (ComprehensiveEvaluator at tau=0.15).
4. Validation-only confidence threshold sweep (tau in [0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50]).
5. Final External Evaluation strictly ONCE on the untouched Phase 8.3 522-image test set.
6. Baseline (Phase 8.4) vs Candidate (Phase 8.8) comparative analysis and CPU latency benchmarking.
7. Error analysis visualization generation.
8. Persistence of all required machine-readable artifacts.
"""

from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import random
import sys
import time
from typing import Any, Dict, List, Optional, Tuple

import cv2
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset

# Ensure immediate line buffering for stdout
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(line_buffering=True)

WORKSPACE_ROOT = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
if str(AI_SERVICE_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SERVICE_DIR))

from app.evaluation.comprehensive_evaluator import ComprehensiveEvaluator
from app.models.baseline_detector import BaselineInspectionDetector, CLASS_NAMES
from app.models.nms import compute_iou
from app.training.checkpoints import CheckpointManager

PREPARED_8_8 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_8"
PREPARED_8_3 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3"
ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts" / "phase8_8"
CHECKPOINT_8_4 = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"


def compute_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


class YOLOPerceptionDataset(Dataset):
    """Memory-efficient PyTorch Dataset for prepared YOLO image/label pairs with in-memory caching."""

    def __init__(
        self,
        images_dir: Path,
        labels_dir: Path,
        target_size: Tuple[int, int] = (256, 256),
        cache_images: bool = True,
    ):
        self.images_dir = Path(images_dir)
        self.labels_dir = Path(labels_dir)
        self.target_size = target_size
        self.cache_images = cache_images

        exts = {".jpg", ".jpeg", ".png", ".webp"}
        image_files = sorted([p for p in self.images_dir.iterdir() if p.is_file() and p.suffix.lower() in exts])

        self.samples = []
        for img_p in image_files:
            lbl_p = self.labels_dir / f"{img_p.stem}.txt"
            self.samples.append((img_p, lbl_p))

        self.cache: Dict[int, np.ndarray] = {}

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, torch.Tensor, str]:
        img_p, lbl_p = self.samples[idx]
        stem = img_p.stem

        if self.cache_images and idx in self.cache:
            img_resized = self.cache[idx]
        else:
            img = cv2.imread(str(img_p))
            if img is None:
                img_resized = np.zeros((self.target_size[1], self.target_size[0], 3), dtype=np.uint8)
            else:
                img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
                img_resized = cv2.resize(img_rgb, self.target_size, interpolation=cv2.INTER_LINEAR)
            if self.cache_images:
                self.cache[idx] = img_resized

        img_tensor = torch.from_numpy(img_resized).permute(2, 0, 1).float() / 255.0

        boxes: List[List[float]] = []
        if lbl_p.exists():
            for line in lbl_p.read_text(encoding="utf-8").strip().splitlines():
                parts = line.strip().split()
                if len(parts) >= 5:
                    try:
                        cid = float(parts[0])
                        xc = float(parts[1])
                        yc = float(parts[2])
                        w = float(parts[3])
                        h = float(parts[4])
                        boxes.append([cid, xc, yc, w, h])
                    except ValueError:
                        continue

        boxes_tensor = torch.tensor(boxes, dtype=torch.float32) if boxes else torch.zeros((0, 5), dtype=torch.float32)
        return img_tensor, boxes_tensor, stem


def collate_fn(batch):
    images = torch.stack([item[0] for item in batch])
    targets = [item[1] for item in batch]
    stems = [item[2] for item in batch]
    return images, targets, stems


class Phase88ControlledTrainer:
    """Orchestrates controlled training, validation selection, and comparative evaluation."""

    def __init__(self, seed: int = 42):
        self.seed = seed
        self._set_seed(seed)
        self.device = torch.device("cpu")
        self.model_id = "firesphere-perception-candidate-v0.8.8"
        self.model_version = "0.8.8-candidate"
        self.epochs = 5
        self.batch_size = 32
        self.lr = 1e-3
        self.weight_decay = 1e-4

        for sub in ["checkpoints", "metrics", "visualizations", "benchmark", "manifests", "logs"]:
            (ARTIFACTS_DIR / sub).mkdir(parents=True, exist_ok=True)

    @staticmethod
    def _set_seed(seed: int):
        random.seed(seed)
        np.random.seed(seed)
        torch.manual_seed(seed)
        if torch.cuda.is_available():
            torch.cuda.manual_seed_all(seed)

    def train_candidate(self) -> Dict[str, Any]:
        print("\n" + "=" * 80)
        print(f"PHASE 8.8 CONTROLLED CANDIDATE TRAINING — {self.model_id}")
        print("=" * 80)

        train_dataset = YOLOPerceptionDataset(
            images_dir=PREPARED_8_8 / "yolo" / "images" / "train",
            labels_dir=PREPARED_8_8 / "yolo" / "labels" / "train",
            target_size=(256, 256),
            cache_images=True,
        )
        val_dataset = YOLOPerceptionDataset(
            images_dir=PREPARED_8_8 / "yolo" / "images" / "val",
            labels_dir=PREPARED_8_8 / "yolo" / "labels" / "val",
            target_size=(256, 256),
            cache_images=True,
        )

        train_loader = DataLoader(
            train_dataset,
            batch_size=self.batch_size,
            shuffle=True,
            collate_fn=collate_fn,
            num_workers=0,
        )
        val_loader = DataLoader(
            val_dataset,
            batch_size=self.batch_size,
            shuffle=False,
            collate_fn=collate_fn,
            num_workers=0,
        )

        print(f"[*] Training Dataset:   {len(train_dataset)} images ({len(train_loader)} batches, bs={self.batch_size})")
        print(f"[*] Validation Dataset: {len(val_dataset)} images ({len(val_loader)} batches)")
        print(f"[*] Device:             {self.device}")
        print(f"[*] Target Architecture: BaselineInspectionDetector (1 scale, stride 16, 768 anchors/img)")

        model = BaselineInspectionDetector(num_classes=4, input_size=(256, 256))
        model.to(self.device)

        total_params = sum(p.numel() for p in model.parameters())
        trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
        print(f"[*] Total Parameters:   {total_params:,} (Trainable: {trainable_params:,})")

        optimizer = torch.optim.AdamW(
            model.parameters(),
            lr=self.lr,
            weight_decay=self.weight_decay,
        )

        best_val_loss = float("inf")
        best_epoch = -1
        history: List[Dict[str, Any]] = []

        total_training_start = time.perf_counter()

        for epoch in range(1, self.epochs + 1):
            epoch_start = time.perf_counter()
            model.train()

            train_loss_accum = 0.0
            train_box_loss_accum = 0.0
            train_obj_loss_accum = 0.0
            train_cls_loss_accum = 0.0
            num_batches = 0

            for batch_idx, (images, targets, stems) in enumerate(train_loader):
                images = images.to(self.device)
                optimizer.zero_grad()

                outputs = model(images)
                loss, loss_dict = model.compute_loss(outputs, targets, self.device)

                loss.backward()
                nn.utils.clip_grad_norm_(model.parameters(), max_norm=10.0)
                optimizer.step()

                train_loss_accum += loss_dict["total_loss"]
                train_box_loss_accum += loss_dict["box_loss"]
                train_obj_loss_accum += loss_dict["obj_loss"]
                train_cls_loss_accum += loss_dict["cls_loss"]
                num_batches += 1

                if (batch_idx + 1) % 35 == 0 or (batch_idx + 1) == len(train_loader):
                    print(f"  [Epoch {epoch}/{self.epochs}] Step {batch_idx + 1}/{len(train_loader)} | "
                          f"Loss: {loss_dict['total_loss']:.4f} (box={loss_dict['box_loss']:.4f}, "
                          f"obj={loss_dict['obj_loss']:.4f}, cls={loss_dict['cls_loss']:.4f})")

            avg_train_loss = round(train_loss_accum / max(1, num_batches), 5)
            avg_train_box = round(train_box_loss_accum / max(1, num_batches), 5)
            avg_train_obj = round(train_obj_loss_accum / max(1, num_batches), 5)
            avg_train_cls = round(train_cls_loss_accum / max(1, num_batches), 5)

            # Validation Loss
            model.eval()
            val_loss_accum = 0.0
            val_box_accum = 0.0
            val_obj_accum = 0.0
            val_cls_accum = 0.0
            val_batches = 0

            with torch.no_grad():
                for val_images, val_targets, _ in val_loader:
                    val_images = val_images.to(self.device)
                    val_out = model(val_images)
                    v_loss, v_loss_dict = model.compute_loss(val_out, val_targets, self.device)
                    val_loss_accum += v_loss_dict["total_loss"]
                    val_box_accum += v_loss_dict["box_loss"]
                    val_obj_accum += v_loss_dict["obj_loss"]
                    val_cls_accum += v_loss_dict["cls_loss"]
                    val_batches += 1

            avg_val_loss = round(val_loss_accum / max(1, val_batches), 5)
            avg_val_box = round(val_box_accum / max(1, val_batches), 5)
            avg_val_obj = round(val_obj_accum / max(1, val_batches), 5)
            avg_val_cls = round(val_cls_accum / max(1, val_batches), 5)

            epoch_duration = round(time.perf_counter() - epoch_start, 2)

            is_best = avg_val_loss < best_val_loss
            if is_best:
                best_val_loss = avg_val_loss
                best_epoch = epoch
                CheckpointManager.save_checkpoint(
                    model=model,
                    epoch=epoch,
                    metrics={"val_loss": avg_val_loss, "train_loss": avg_train_loss},
                    output_path=ARTIFACTS_DIR / "checkpoints" / "best.pt",
                    extra_metadata={"model_id": self.model_id},
                )

            # Always save last checkpoint
            CheckpointManager.save_checkpoint(
                model=model,
                epoch=epoch,
                metrics={"val_loss": avg_val_loss, "train_loss": avg_train_loss},
                output_path=ARTIFACTS_DIR / "checkpoints" / "last.pt",
                extra_metadata={"model_id": self.model_id},
            )

            record = {
                "epoch": epoch,
                "train_loss": avg_train_loss,
                "train_box_loss": avg_train_box,
                "train_obj_loss": avg_train_obj,
                "train_cls_loss": avg_train_cls,
                "val_loss": avg_val_loss,
                "val_box_loss": avg_val_box,
                "val_obj_loss": avg_val_obj,
                "val_cls_loss": avg_val_cls,
                "is_best": is_best,
                "duration_seconds": epoch_duration,
            }
            history.append(record)

            print(f"[+] Epoch {epoch}/{self.epochs} Complete in {epoch_duration}s: "
                  f"Train Loss = {avg_train_loss:.4f} | Val Loss = {avg_val_loss:.4f} "
                  f"{'(*BEST*)' if is_best else ''}")

        total_training_time = round(time.perf_counter() - total_training_start, 2)
        best_ckpt_path = ARTIFACTS_DIR / "checkpoints" / "best.pt"
        best_sha256 = compute_sha256(best_ckpt_path)

        print(f"\n[+] Candidate Training Complete in {total_training_time}s.")
        print(f"    - Best Epoch:     Epoch {best_epoch} (Val Loss: {best_val_loss})")
        print(f"    - Checkpoint:     {best_ckpt_path}")
        print(f"    - SHA256:         {best_sha256}")

        training_summary = {
            "model_id": self.model_id,
            "architecture": "BaselineInspectionDetector",
            "seed": self.seed,
            "epochs": self.epochs,
            "best_epoch": best_epoch,
            "best_val_loss": best_val_loss,
            "total_time_seconds": total_training_time,
            "device": str(self.device),
            "history": history,
            "checkpoint_sha256": best_sha256,
        }

        (ARTIFACTS_DIR / "metrics" / "training_config.json").write_text(
            json.dumps(training_summary, indent=2), encoding="utf-8"
        )
        return training_summary

    def run_evaluations_and_comparisons(self) -> Dict[str, Any]:
        print("\n" + "=" * 80)
        print("PHASE 8.8 EVALUATIONS, SWEEPS & COMPARATIVE BENCHMARKING")
        print("=" * 80)

        best_ckpt_path = ARTIFACTS_DIR / "checkpoints" / "best.pt"
        assert best_ckpt_path.exists(), f"Candidate checkpoint missing: {best_ckpt_path}"
        assert CHECKPOINT_8_4.exists(), f"Baseline Phase 8.4 checkpoint missing: {CHECKPOINT_8_4}"

        # Load models
        candidate_model = BaselineInspectionDetector(num_classes=4)
        CheckpointManager.load_checkpoint(best_ckpt_path, candidate_model)
        candidate_model.to(self.device)
        candidate_model.eval()

        baseline_model = BaselineInspectionDetector(num_classes=4)
        CheckpointManager.load_checkpoint(CHECKPOINT_8_4, baseline_model)
        baseline_model.to(self.device)
        baseline_model.eval()

        # ======================================================================
        # 1. EVALUATION ON PHASE 8.8 VALIDATION SET (928 images)
        # ======================================================================
        print("\n[*] Evaluating Candidate Model on Phase 8.8 Validation Set (928 images, tau=0.15)...")
        val_img_dir = PREPARED_8_8 / "yolo" / "images" / "val"
        val_lbl_dir = PREPARED_8_8 / "yolo" / "labels" / "val"

        val_preds_tau15 = {}
        val_preds_floor = {}
        val_gts = {}

        val_images = sorted(list(val_img_dir.glob("*.jpg")))
        for idx, img_p in enumerate(val_images):
            raw = cv2.imread(str(img_p))
            if raw is None:
                continue
            rgb = cv2.cvtColor(raw, cv2.COLOR_BGR2RGB)
            resized = cv2.resize(rgb, (256, 256))
            tensor = torch.from_numpy(resized).permute(2, 0, 1).unsqueeze(0).float() / 255.0

            with torch.no_grad():
                out = candidate_model(tensor)
                # Standard eval at tau=0.15
                preds_15 = candidate_model.decode_predictions(out, conf_threshold=0.15, iou_threshold=0.45)[0]
                # Pre-decode at floor 0.05 for fast sweep
                preds_floor = candidate_model.decode_predictions(out, conf_threshold=0.05, iou_threshold=0.45)[0]

            val_preds_tau15[img_p.stem] = preds_15
            val_preds_floor[img_p.stem] = preds_floor

            # Read ground truth
            lbl_p = val_lbl_dir / f"{img_p.stem}.txt"
            gt_boxes = []
            if lbl_p.exists():
                for line in lbl_p.read_text(encoding="utf-8").strip().splitlines():
                    parts = line.strip().split()
                    if len(parts) == 5:
                        cid = int(parts[0])
                        xc, yc, w, h = float(parts[1]), float(parts[2]), float(parts[3]), float(parts[4])
                        gt_boxes.append({
                            "classId": cid,
                            "className": CLASS_NAMES.get(cid, "UNKNOWN"),
                            "boundingBox": {
                                "xMin": round(max(0.0, xc - w / 2), 4),
                                "yMin": round(max(0.0, yc - h / 2), 4),
                                "xMax": round(min(1.0, xc + w / 2), 4),
                                "yMax": round(min(1.0, yc + h / 2), 4),
                            },
                        })
            val_gts[img_p.stem] = gt_boxes

            if (idx + 1) % 300 == 0 or (idx + 1) == len(val_images):
                print(f"    - Inferred {idx + 1}/{len(val_images)} validation images...")

        val_report = ComprehensiveEvaluator.evaluate(
            predictions_by_image=val_preds_tau15,
            ground_truth_by_image=val_gts,
            model_id=self.model_id,
            model_version=self.model_version,
            dataset_name="phase8_8_validation_split",
            target_classes=CLASS_NAMES,
        )

        val_report_dict = val_report.model_dump()
        (ARTIFACTS_DIR / "metrics" / "validation_metrics.json").write_text(
            json.dumps(val_report_dict, indent=2), encoding="utf-8"
        )

        print(f"[+] Candidate Validation Metrics (tau=0.15):")
        print(f"    - Mean Precision: {val_report.meanPrecision:.4f}")
        print(f"    - Mean Recall:    {val_report.meanRecall:.4f}")
        print(f"    - Mean F1:        {val_report.meanF1:.4f}")
        print(f"    - mAP@50:         {val_report.mAP50:.4f}")
        print(f"    - mAP@50:95:      {val_report.mAP50_95:.4f}")
        for cname, m in val_report.perClassMetrics.items():
            print(f"    * {cname:18s}: AP50={m.ap50:.4f} | Prec={m.precision:.4f} | "
                  f"Rec={m.recall:.4f} | TP={m.truePositives} | FP={m.falsePositives} | FN={m.falseNegatives} | Support={m.support}")

        # ======================================================================
        # 2. VALIDATION CONFIDENCE THRESHOLD SWEEP
        # ======================================================================
        print("\n[*] Running Validation Confidence Threshold Sweep across [0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50]...")
        sweep_thresholds = [0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50]
        sweep_results = []

        for tau in sweep_thresholds:
            filtered_preds = {
                stem: [p for p in preds if p["confidence"] >= tau]
                for stem, preds in val_preds_floor.items()
            }
            sweep_rep = ComprehensiveEvaluator.evaluate(
                predictions_by_image=filtered_preds,
                ground_truth_by_image=val_gts,
                model_id=self.model_id,
                model_version=self.model_version,
                dataset_name="phase8_8_validation_sweep",
                target_classes=CLASS_NAMES,
            )
            sweep_record = {
                "threshold": tau,
                "meanPrecision": sweep_rep.meanPrecision,
                "meanRecall": sweep_rep.meanRecall,
                "meanF1": sweep_rep.meanF1,
                "mAP50": sweep_rep.mAP50,
                "totalPredictions": sweep_rep.totalPredictedObjects,
                "perClass": {
                    cname: {
                        "ap50": cm.ap50,
                        "precision": cm.precision,
                        "recall": cm.recall,
                        "f1": cm.f1Score,
                        "tp": cm.truePositives,
                        "fp": cm.falsePositives,
                        "fn": cm.falseNegatives,
                    }
                    for cname, cm in sweep_rep.perClassMetrics.items()
                },
            }
            sweep_results.append(sweep_record)
            print(f"  [tau={tau:.2f}] Preds={sweep_rep.totalPredictedObjects:4d} | "
                  f"mPrec={sweep_rep.meanPrecision:.3f} | mRec={sweep_rep.meanRecall:.3f} | mF1={sweep_rep.meanF1:.3f} | "
                  f"DoorRec={sweep_rep.perClassMetrics['EXIT_DOOR'].recall:.3f} | "
                  f"SignRec={sweep_rep.perClassMetrics['EXIT_SIGN'].recall:.3f} | "
                  f"ExtRec={sweep_rep.perClassMetrics['FIRE_EXTINGUISHER'].recall:.3f}")

        (ARTIFACTS_DIR / "metrics" / "threshold_sweep.json").write_text(
            json.dumps({"sweep": sweep_results}, indent=2), encoding="utf-8"
        )

        # ======================================================================
        # 3. FINAL EXTERNAL EVALUATION ON UNTOUCHED PHASE 8.3 TEST SET (522 images)
        # ======================================================================
        print("\n" + "=" * 80)
        print("FINAL EXTERNAL EVALUATION: UNTOUCHED PHASE 8.3 TEST SET (522 images)")
        print("=" * 80)
        test_img_dir = PREPARED_8_3 / "yolo" / "images" / "test"
        test_lbl_dir = PREPARED_8_3 / "yolo" / "labels" / "test"
        test_images = sorted(list(test_img_dir.glob("*.jpg")))
        print(f"[*] Discovered {len(test_images)} images in the untouched Phase 8.3 test set.")

        test_preds_candidate = {}
        test_preds_baseline = {}
        test_gts = {}

        for idx, img_p in enumerate(test_images):
            raw = cv2.imread(str(img_p))
            if raw is None:
                continue
            rgb = cv2.cvtColor(raw, cv2.COLOR_BGR2RGB)
            resized = cv2.resize(rgb, (256, 256))
            tensor = torch.from_numpy(resized).permute(2, 0, 1).unsqueeze(0).float() / 255.0

            with torch.no_grad():
                out_cand = candidate_model(tensor)
                cand_dec = candidate_model.decode_predictions(out_cand, conf_threshold=0.15, iou_threshold=0.45)[0]

                out_base = baseline_model(tensor)
                base_dec = baseline_model.decode_predictions(out_base, conf_threshold=0.15, iou_threshold=0.45)[0]

            test_preds_candidate[img_p.stem] = cand_dec
            test_preds_baseline[img_p.stem] = base_dec

            # Ground truth
            lbl_p = test_lbl_dir / f"{img_p.stem}.txt"
            gt_boxes = []
            if lbl_p.exists():
                for line in lbl_p.read_text(encoding="utf-8").strip().splitlines():
                    parts = line.strip().split()
                    if len(parts) == 5:
                        cid = int(parts[0])
                        xc, yc, w, h = float(parts[1]), float(parts[2]), float(parts[3]), float(parts[4])
                        gt_boxes.append({
                            "classId": cid,
                            "className": CLASS_NAMES.get(cid, "UNKNOWN"),
                            "boundingBox": {
                                "xMin": round(max(0.0, xc - w / 2), 4),
                                "yMin": round(max(0.0, yc - h / 2), 4),
                                "xMax": round(min(1.0, xc + w / 2), 4),
                                "yMax": round(min(1.0, yc + h / 2), 4),
                            },
                        })
            test_gts[img_p.stem] = gt_boxes

            if (idx + 1) % 150 == 0 or (idx + 1) == len(test_images):
                print(f"    - Evaluated {idx + 1}/{len(test_images)} held-out test images...")

        # Evaluate candidate
        cand_test_report = ComprehensiveEvaluator.evaluate(
            predictions_by_image=test_preds_candidate,
            ground_truth_by_image=test_gts,
            model_id=self.model_id,
            model_version=self.model_version,
            dataset_name="phase8_3_untouched_test_split",
            target_classes=CLASS_NAMES,
        )

        # Evaluate baseline
        base_test_report = ComprehensiveEvaluator.evaluate(
            predictions_by_image=test_preds_baseline,
            ground_truth_by_image=test_gts,
            model_id="firesphere-perception-baseline-v0.8.0",
            model_version="0.8.0-baseline",
            dataset_name="phase8_3_untouched_test_split",
            target_classes=CLASS_NAMES,
        )

        test_report_dict = cand_test_report.model_dump()
        (ARTIFACTS_DIR / "metrics" / "test_metrics.json").write_text(
            json.dumps(test_report_dict, indent=2), encoding="utf-8"
        )

        print(f"\n[+] Final External Test Evaluation Results (Apples-to-Apples on 522 images):")
        print(f"--------------------------------------------------------------------------------")
        print(f"{'Metric':<24} | {'Phase 8.4 Baseline':<18} | {'Phase 8.8 Candidate':<18} | {'Delta':<10}")
        print(f"--------------------------------------------------------------------------------")
        print(f"{'Mean Precision':<24} | {base_test_report.meanPrecision:<18.4f} | {cand_test_report.meanPrecision:<18.4f} | {cand_test_report.meanPrecision - base_test_report.meanPrecision:+.4f}")
        print(f"{'Mean Recall':<24} | {base_test_report.meanRecall:<18.4f} | {cand_test_report.meanRecall:<18.4f} | {cand_test_report.meanRecall - base_test_report.meanRecall:+.4f}")
        print(f"{'Mean F1':<24} | {base_test_report.meanF1:<18.4f} | {cand_test_report.meanF1:<18.4f} | {cand_test_report.meanF1 - base_test_report.meanF1:+.4f}")
        print(f"{'mAP@50':<24} | {base_test_report.mAP50:<18.4f} | {cand_test_report.mAP50:<18.4f} | {cand_test_report.mAP50 - base_test_report.mAP50:+.4f}")
        print(f"{'mAP@50:95':<24} | {base_test_report.mAP50_95:<18.4f} | {cand_test_report.mAP50_95:<18.4f} | {cand_test_report.mAP50_95 - base_test_report.mAP50_95:+.4f}")
        print(f"--------------------------------------------------------------------------------")
        for cname in ["FIRE_EXTINGUISHER", "EXIT_DOOR", "EXIT_SIGN", "OBSTACLE"]:
            b_m = base_test_report.perClassMetrics[cname]
            c_m = cand_test_report.perClassMetrics[cname]
            print(f"  {cname}:")
            print(f"    - AP50:      Baseline = {b_m.ap50:.4f} | Candidate = {c_m.ap50:.4f} (Delta: {c_m.ap50 - b_m.ap50:+.4f})")
            print(f"    - Recall:    Baseline = {b_m.recall:.4f} | Candidate = {c_m.recall:.4f} (Delta: {c_m.recall - b_m.recall:+.4f})")
            print(f"    - Precision: Baseline = {b_m.precision:.4f} | Candidate = {c_m.precision:.4f} (Delta: {c_m.precision - b_m.precision:+.4f})")
            print(f"    - TP/FP/FN:  Baseline = ({b_m.truePositives}/{b_m.falsePositives}/{b_m.falseNegatives}) | "
                  f"Candidate = ({c_m.truePositives}/{c_m.falsePositives}/{c_m.falseNegatives})")

        # Save model comparison artifact
        comparison_dict = {
            "comparison_version": "1.0.0-phase8.8",
            "test_split_used": "phase8_3_untouched_522_images",
            "baseline_model": {
                "model_id": "firesphere-perception-baseline-v0.8.0",
                "checkpoint": str(CHECKPOINT_8_4),
                "metrics": base_test_report.model_dump(),
            },
            "candidate_model": {
                "model_id": self.model_id,
                "checkpoint": str(best_ckpt_path),
                "metrics": cand_test_report.model_dump(),
            },
            "deltas": {
                "meanPrecision": round(cand_test_report.meanPrecision - base_test_report.meanPrecision, 4),
                "meanRecall": round(cand_test_report.meanRecall - base_test_report.meanRecall, 4),
                "meanF1": round(cand_test_report.meanF1 - base_test_report.meanF1, 4),
                "mAP50": round(cand_test_report.mAP50 - base_test_report.mAP50, 4),
                "mAP50_95": round(cand_test_report.mAP50_95 - base_test_report.mAP50_95, 4),
                "perClassAP50": {
                    cname: round(cand_test_report.perClassMetrics[cname].ap50 - base_test_report.perClassMetrics[cname].ap50, 4)
                    for cname in CLASS_NAMES.values()
                },
            },
        }
        (ARTIFACTS_DIR / "metrics" / "model_comparison.json").write_text(
            json.dumps(comparison_dict, indent=2), encoding="utf-8"
        )

        # ======================================================================
        # 4. INFERENCE BENCHMARKING
        # ======================================================================
        print("\n[*] Benchmarking CPU Inference Latency (100 samples, batch_size=1, 256x256)...")
        benchmark_images = test_images[:100]

        def benchmark_model(m):
            latencies_infer = []
            latencies_total = []
            for img_p in benchmark_images:
                t0 = time.perf_counter()
                raw = cv2.imread(str(img_p))
                rgb = cv2.cvtColor(raw, cv2.COLOR_BGR2RGB)
                resized = cv2.resize(rgb, (256, 256))
                tensor = torch.from_numpy(resized).permute(2, 0, 1).unsqueeze(0).float() / 255.0
                t1 = time.perf_counter()

                with torch.no_grad():
                    out = m(tensor)
                    _ = m.decode_predictions(out, conf_threshold=0.15, iou_threshold=0.45)
                t2 = time.perf_counter()

                latencies_infer.append((t2 - t1) * 1000.0)
                latencies_total.append((t2 - t0) * 1000.0)

            return {
                "infer_mean_ms": round(float(np.mean(latencies_infer)), 2),
                "infer_p50_ms": round(float(np.median(latencies_infer)), 2),
                "infer_p95_ms": round(float(np.percentile(latencies_infer, 95)), 2),
                "total_mean_ms": round(float(np.mean(latencies_total)), 2),
                "total_p50_ms": round(float(np.median(latencies_total)), 2),
                "total_p95_ms": round(float(np.percentile(latencies_total, 95)), 2),
                "fps": round(1000.0 / float(np.mean(latencies_total)), 2),
            }

        cand_bench = benchmark_model(candidate_model)
        base_bench = benchmark_model(baseline_model)

        cand_size_mb = round(best_ckpt_path.stat().st_size / (1024 * 1024), 2)
        base_size_mb = round(CHECKPOINT_8_4.stat().st_size / (1024 * 1024), 2)

        benchmark_report = {
            "device": "cpu",
            "image_size": [256, 256],
            "benchmark_samples": 100,
            "baseline": {
                **base_bench,
                "model_size_mb": base_size_mb,
                "parameter_count": sum(p.numel() for p in baseline_model.parameters()),
            },
            "candidate": {
                **cand_bench,
                "model_size_mb": cand_size_mb,
                "parameter_count": sum(p.numel() for p in candidate_model.parameters()),
            },
        }
        (ARTIFACTS_DIR / "benchmark" / "benchmark_report.json").write_text(
            json.dumps(benchmark_report, indent=2), encoding="utf-8"
        )

        print(f"[+] Benchmark Results:")
        print(f"    - Baseline:  {base_bench['total_mean_ms']}ms/img (p50={base_bench['total_p50_ms']}ms, p95={base_bench['total_p95_ms']}ms, {base_bench['fps']} FPS, {base_size_mb} MB)")
        print(f"    - Candidate: {cand_bench['total_mean_ms']}ms/img (p50={cand_bench['total_p50_ms']}ms, p95={cand_bench['total_p95_ms']}ms, {cand_bench['fps']} FPS, {cand_size_mb} MB)")

        # ======================================================================
        # 5. ERROR ANALYSIS & VISUALIZATIONS
        # ======================================================================
        print("\n[*] Generating Visual Error Analysis Samples (TP, FP, FN)...")
        vis_dir = ARTIFACTS_DIR / "visualizations"
        for sub in ["tp", "fp", "fn"]:
            (vis_dir / sub).mkdir(parents=True, exist_ok=True)

        vis_counts = {"tp": 0, "fp": 0, "fn": 0}

        for stem, preds in list(test_preds_candidate.items())[:150]:
            gts = test_gts.get(stem, [])
            if not gts and not preds:
                continue

            img_p = test_img_dir / f"{stem}.jpg"
            img = cv2.imread(str(img_p))
            if img is None:
                continue
            h_img, w_img = img.shape[:2]

            # Check matches
            matched_gts = set()
            for pred in preds:
                p_cname = pred["className"]
                p_box = pred["boundingBox"]
                best_iou = 0.0
                best_g_idx = -1
                for g_idx, gt in enumerate(gts):
                    if gt["className"] == p_cname and g_idx not in matched_gts:
                        iou = compute_iou(p_box, gt["boundingBox"])
                        if iou > best_iou:
                            best_iou = iou
                            best_g_idx = g_idx

                if best_iou >= 0.50 and best_g_idx != -1:
                    matched_gts.add(best_g_idx)
                    if vis_counts["tp"] < 10:
                        # Draw TP (Green)
                        canvas = img.copy()
                        x1 = int(p_box["xMin"] * w_img)
                        y1 = int(p_box["yMin"] * h_img)
                        x2 = int(p_box["xMax"] * w_img)
                        y2 = int(p_box["yMax"] * h_img)
                        cv2.rectangle(canvas, (x1, y1), (x2, y2), (0, 255, 0), 2)
                        cv2.putText(canvas, f"TP: {p_cname} {pred['confidence']:.2f}", (x1, max(20, y1 - 5)),
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)
                        cv2.imwrite(str(vis_dir / "tp" / f"TP_{p_cname}_{stem}.jpg"), canvas)
                        vis_counts["tp"] += 1
                else:
                    if vis_counts["fp"] < 10:
                        # Draw FP (Red)
                        canvas = img.copy()
                        x1 = int(p_box["xMin"] * w_img)
                        y1 = int(p_box["yMin"] * h_img)
                        x2 = int(p_box["xMax"] * w_img)
                        y2 = int(p_box["yMax"] * h_img)
                        cv2.rectangle(canvas, (x1, y1), (x2, y2), (0, 0, 255), 2)
                        cv2.putText(canvas, f"FP: {p_cname} {pred['confidence']:.2f}", (x1, max(20, y1 - 5)),
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 2)
                        cv2.imwrite(str(vis_dir / "fp" / f"FP_{p_cname}_{stem}.jpg"), canvas)
                        vis_counts["fp"] += 1

            for g_idx, gt in enumerate(gts):
                if g_idx not in matched_gts:
                    if vis_counts["fn"] < 10:
                        # Draw FN (Yellow)
                        canvas = img.copy()
                        g_box = gt["boundingBox"]
                        x1 = int(g_box["xMin"] * w_img)
                        y1 = int(g_box["yMin"] * h_img)
                        x2 = int(g_box["xMax"] * w_img)
                        y2 = int(g_box["yMax"] * h_img)
                        cv2.rectangle(canvas, (x1, y1), (x2, y2), (0, 255, 255), 2)
                        cv2.putText(canvas, f"FN: {gt['className']}", (x1, max(20, y1 - 5)),
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 2)
                        cv2.imwrite(str(vis_dir / "fn" / f"FN_{gt['className']}_{stem}.jpg"), canvas)
                        vis_counts["fn"] += 1

        print(f"[+] Visual Error Samples Generated: TP={vis_counts['tp']}, FP={vis_counts['fp']}, FN={vis_counts['fn']}.")

        # ======================================================================
        # 6. COMPARATIVE GOVERNANCE DECISION
        # ======================================================================
        # Determine governance outcome:
        # Check if EXIT_DOOR or overall mAP improved without extinguisher collapse
        cand_ext_ap = cand_test_report.perClassMetrics["FIRE_EXTINGUISHER"].ap50
        base_ext_ap = base_test_report.perClassMetrics["FIRE_EXTINGUISHER"].ap50

        cand_map50 = cand_test_report.mAP50
        base_map50 = base_test_report.mAP50

        if cand_ext_ap < (base_ext_ap * 0.70):
            gate_decision = "CANDIDATE_REGRESSED"
            decision_rationale = "Fire extinguisher AP collapsed by >30% on held-out test set."
        elif cand_map50 > base_map50:
            gate_decision = "CONTROLLED_CANDIDATE_READY_FOR_NEXT_VALIDATION"
            decision_rationale = (
                f"Candidate model demonstrated higher overall mAP@50 ({cand_map50:.4f} vs {base_map50:.4f}) "
                f"while preserving control class stability."
            )
        else:
            gate_decision = "DATA_EXPANSION_STILL_REQUIRED"
            decision_rationale = (
                f"Data expansion improved physical scene coverage, but minority classes remain "
                f"constrained on the held-out test distribution. Further targeted data collection or architecture needed."
            )

        decision_data = {
            "decision_version": "1.0.0-phase8.8",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "model_id": self.model_id,
            "architecture": "BaselineInspectionDetector",
            "checkpoint_sha256": compute_sha256(best_ckpt_path),
            "production_status": "NOT_APPROVED",
            "gate_decision": gate_decision,
            "decision_rationale": decision_rationale,
            "baseline_metrics": base_test_report.model_dump(),
            "candidate_metrics": cand_test_report.model_dump(),
            "comparison_summary": comparison_dict["deltas"],
        }
        (ARTIFACTS_DIR / "metrics" / "phase8_8_decision.json").write_text(
            json.dumps(decision_data, indent=2), encoding="utf-8"
        )

        # Model Metadata
        model_meta = {
            "model_id": self.model_id,
            "model_version": self.model_version,
            "model_type": "PYTORCH_CONTROLLED_CANDIDATE_DETECTOR",
            "architecture": "BaselineInspectionDetector",
            "class_mapping": CLASS_NAMES,
            "dataset_version": "1.0.0-phase8.8",
            "dataset_location": str(PREPARED_8_8 / "yolo"),
            "training_seed": self.seed,
            "checkpoint_path": str(best_ckpt_path),
            "checkpoint_sha256": compute_sha256(best_ckpt_path),
            "validation_metrics": val_report.model_dump(),
            "test_metrics": cand_test_report.model_dump(),
            "inference_benchmark": cand_bench,
            "production_status": "NOT_APPROVED",
            "governance_decision": gate_decision,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        (ARTIFACTS_DIR / "manifests" / "model_metadata.json").write_text(
            json.dumps(model_meta, indent=2), encoding="utf-8"
        )

        print("\n" + "=" * 80)
        print(f"PHASE 8.8 GATE DECISION: {gate_decision}")
        print(f"Rationale: {decision_rationale}")
        print("PRODUCTION_STATUS: NOT_APPROVED")
        print("=" * 80)

        return decision_data


if __name__ == "__main__":
    trainer = Phase88ControlledTrainer(seed=42)
    trainer.train_candidate()
    trainer.run_evaluations_and_comparisons()
