"""FireSphere.ai — Phase 8.9 Pretrained Transfer-Learning Trainer & Evaluator.

Executes:
1. Pre-training verification of the authorized MobileNetV2 checkpoint.
2. Two-Stage Transfer Learning Protocol (Stage A: Warmup frozen backbone, Stage B: Deep backbone fine-tuning).
3. Epoch-level loss logging and best checkpoint selection.
4. Validation-set evaluation and threshold sweep.
5. Final External Test Evaluation on the untouched Phase 8.3 522-image test set.
6. Apples-to-apples comparison against the Phase 8.4 authoritative baseline.
7. CPU latency benchmarking and error analysis visualization generation.
8. Serialization of all Phase 8.9 machine-readable artifacts.
"""

from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import random
import sys
import time
from typing import Any, Dict, List, Optional, Tuple

import cv2
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(line_buffering=True)

WORKSPACE_ROOT = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
if str(AI_SERVICE_DIR) not in sys.path:
    sys.path.insert(0, str(AI_SERVICE_DIR))

from app.evaluation.comprehensive_evaluator import ComprehensiveEvaluator
from app.models.baseline_detector import BaselineInspectionDetector, CLASS_NAMES
from app.models.pretrained_detector import PretrainedInspectionDetector
from app.models.nms import compute_iou
from app.training.checkpoints import CheckpointManager

PREPARED_8_8 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_8"
PREPARED_8_3 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3"
ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts" / "phase8_9"
PRETRAINED_CKPT = AI_SERVICE_DIR / "artifacts" / "phase8_10" / "pretrained" / "mobilenet_v2-b0353104.pth"
CHECKPOINT_8_4 = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"
CHECKPOINT_8_8 = AI_SERVICE_DIR / "artifacts" / "phase8_8" / "checkpoints" / "best.pt"

EXPECTED_LOCAL_SHA256 = "b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889"
EXPECTED_LOCAL_SIZE = 14212972


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


class Phase89TransferLearningTrainer:
    """Orchestrates controlled transfer-learning training, validation selection, and comparative evaluation."""

    def __init__(self, seed: int = 42):
        self.seed = seed
        self._set_seed(seed)
        self.device = torch.device("cpu")
        self.model_id = "firesphere-perception-pretrained-v0.8.9"
        self.model_version = "0.8.9-candidate"
        self.stage_a_epochs = 2
        self.stage_b_epochs = 3
        self.total_epochs = self.stage_a_epochs + self.stage_b_epochs
        self.batch_size = 32

        for sub in ["checkpoints", "metrics", "visualizations", "benchmark", "manifests", "logs"]:
            (ARTIFACTS_DIR / sub).mkdir(parents=True, exist_ok=True)

    @staticmethod
    def _set_seed(seed: int):
        random.seed(seed)
        np.random.seed(seed)
        torch.manual_seed(seed)
        if torch.cuda.is_available():
            torch.cuda.manual_seed_all(seed)

    def verify_pretrained_checkpoint(self) -> Dict[str, Any]:
        """Strict pre-training integrity audit of the authorized MobileNetV2 checkpoint."""
        print("\n" + "=" * 80)
        print("PRE-TRAINING CHECKPOINT INTEGRITY VERIFICATION")
        print("=" * 80)

        assert PRETRAINED_CKPT.exists(), f"Pretrained checkpoint missing at {PRETRAINED_CKPT}"
        actual_size = PRETRAINED_CKPT.stat().st_size
        assert actual_size == EXPECTED_LOCAL_SIZE, f"Size mismatch: expected {EXPECTED_LOCAL_SIZE}, got {actual_size}"

        actual_sha = compute_sha256(PRETRAINED_CKPT)
        assert actual_sha == EXPECTED_LOCAL_SHA256, f"SHA-256 mismatch: expected {EXPECTED_LOCAL_SHA256}, got {actual_sha}"

        state_dict = torch.load(PRETRAINED_CKPT, map_location="cpu", weights_only=True)
        assert isinstance(state_dict, dict) and len(state_dict) == 314

        model = PretrainedInspectionDetector(num_classes=4)
        load_res = model.load_pretrained_backbone(state_dict, strict=False)
        assert load_res["loaded_tensors"] == 312
        assert load_res["missing_keys"] == 0
        assert load_res["unexpected_keys"] == 0

        print(f"[+] Checkpoint verified: {PRETRAINED_CKPT}")
        print(f"    - SHA-256: {actual_sha}")
        print(f"    - Size:    {actual_size:,} bytes")
        print(f"    - Backbone Tensors Loaded: {load_res['loaded_tensors']} (0 missing, 0 unexpected)")

        return {
            "checkpoint_path": str(PRETRAINED_CKPT),
            "sha256": actual_sha,
            "size_bytes": actual_size,
            "provenance_limitation": "Full 64-character SHA-256 not published by official PyTorch (8-character prefix b0353104 officially verified). User explicitly authorized research experiment.",
            "authorized_by_user": True,
        }

    def train_candidate(self) -> Dict[str, Any]:
        provenance_audit = self.verify_pretrained_checkpoint()

        print("\n" + "=" * 80)
        print(f"PHASE 8.9 CONTROLLED TRANSFER LEARNING — {self.model_id}")
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

        # Instantiate model and load pretrained weights
        model = PretrainedInspectionDetector(num_classes=4, input_size=(256, 256))
        state_dict = torch.load(PRETRAINED_CKPT, map_location="cpu", weights_only=True)
        model.load_pretrained_backbone(state_dict, strict=False)
        model.to(self.device)

        best_val_loss = float("inf")
        best_epoch = -1
        history: List[Dict[str, Any]] = []

        total_training_start = time.perf_counter()

        # Training Loop across Stage A (Epochs 1-2) and Stage B (Epochs 3-5)
        current_stage = "stage_a"
        freeze_stats = model.set_freeze_stage("stage_a")
        optimizer = torch.optim.AdamW(
            [p for p in model.parameters() if p.requires_grad],
            lr=1e-3,
            weight_decay=1e-4,
        )
        print(f"\n[*] Starting STAGE A (Epochs 1-{self.stage_a_epochs}): Backbone Frozen (Trainable: {freeze_stats['trainable']:,}, Frozen: {freeze_stats['frozen']:,})")

        for epoch in range(1, self.total_epochs + 1):
            if epoch > self.stage_a_epochs and current_stage == "stage_a":
                current_stage = "stage_b"
                freeze_stats = model.set_freeze_stage("stage_b")
                param_groups = model.get_parameter_groups(lr_head=1e-3, lr_backbone=1e-4, weight_decay=1e-4)
                optimizer = torch.optim.AdamW(param_groups)
                print(f"\n[*] Starting STAGE B (Epochs {self.stage_a_epochs + 1}-{self.total_epochs}): Partial Unfreeze (Trainable: {freeze_stats['trainable']:,}, Frozen: {freeze_stats['frozen']:,})")
                print("    - Backbone lr: 1e-4 (0.1x), Neck/Heads lr: 1e-3 (1.0x)")

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
                    print(f"  [Epoch {epoch}/{self.total_epochs} ({current_stage.upper()})] Step {batch_idx + 1}/{len(train_loader)} | "
                          f"Loss: {loss_dict['total_loss']:.4f} (box={loss_dict['box_loss']:.4f}, "
                          f"obj={loss_dict['obj_loss']:.4f}, cls={loss_dict['cls_loss']:.4f})")

            avg_train_loss = round(train_loss_accum / max(1, num_batches), 5)
            avg_train_box = round(train_box_loss_accum / max(1, num_batches), 5)
            avg_train_obj = round(train_obj_loss_accum / max(1, num_batches), 5)
            avg_train_cls = round(train_cls_loss_accum / max(1, num_batches), 5)

            # Validation pass
            model.eval()
            val_loss_accum = 0.0
            val_box_accum = 0.0
            val_obj_accum = 0.0
            val_cls_accum = 0.0
            val_batches = 0

            with torch.no_grad():
                for images, targets, stems in val_loader:
                    images = images.to(self.device)
                    outputs = model(images)
                    _, val_dict = model.compute_loss(outputs, targets, self.device)

                    val_loss_accum += val_dict["total_loss"]
                    val_box_accum += val_dict["box_loss"]
                    val_obj_accum += val_dict["obj_loss"]
                    val_cls_accum += val_dict["cls_loss"]
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
                    metrics={"val_loss": avg_val_loss},
                    output_path=ARTIFACTS_DIR / "checkpoints" / "best.pt",
                    optimizer=optimizer,
                    extra_metadata={"stage": current_stage, "model_id": self.model_id},
                )

            # Save last checkpoint
            CheckpointManager.save_checkpoint(
                model=model,
                epoch=epoch,
                metrics={"val_loss": avg_val_loss},
                output_path=ARTIFACTS_DIR / "checkpoints" / "last.pt",
                optimizer=optimizer,
                extra_metadata={"stage": current_stage, "model_id": self.model_id},
            )

            record = {
                "epoch": epoch,
                "stage": current_stage,
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

            print(f"[+] Epoch {epoch}/{self.total_epochs} Complete in {epoch_duration}s: "
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
            "architecture": "PretrainedInspectionDetector (MobileNetV2 + Bi-Scale Neck)",
            "seed": self.seed,
            "epochs": self.total_epochs,
            "stage_a_epochs": self.stage_a_epochs,
            "stage_b_epochs": self.stage_b_epochs,
            "best_epoch": best_epoch,
            "best_val_loss": best_val_loss,
            "total_time_seconds": total_training_time,
            "device": str(self.device),
            "provenance_audit": provenance_audit,
            "history": history,
            "checkpoint_sha256": best_sha256,
        }

        (ARTIFACTS_DIR / "metrics" / "training_config.json").write_text(
            json.dumps(training_summary, indent=2), encoding="utf-8"
        )
        return training_summary

    def run_evaluations_and_comparisons(self) -> Dict[str, Any]:
        print("\n" + "=" * 80)
        print("PHASE 8.9 EVALUATIONS, SWEEPS & COMPARATIVE BENCHMARKING")
        print("=" * 80)

        best_ckpt_path = ARTIFACTS_DIR / "checkpoints" / "best.pt"
        assert best_ckpt_path.exists(), f"Candidate checkpoint missing: {best_ckpt_path}"
        assert CHECKPOINT_8_4.exists(), f"Baseline Phase 8.4 checkpoint missing: {CHECKPOINT_8_4}"

        candidate_model = PretrainedInspectionDetector(num_classes=4)
        CheckpointManager.load_checkpoint(best_ckpt_path, candidate_model)
        candidate_model.to(self.device)
        candidate_model.eval()

        baseline_model = BaselineInspectionDetector(num_classes=4)
        CheckpointManager.load_checkpoint(CHECKPOINT_8_4, baseline_model)
        baseline_model.to(self.device)
        baseline_model.eval()

        # Phase 8.8 reference model
        phase8_8_model = None
        if CHECKPOINT_8_8.exists():
            phase8_8_model = BaselineInspectionDetector(num_classes=4)
            CheckpointManager.load_checkpoint(CHECKPOINT_8_8, phase8_8_model)
            phase8_8_model.to(self.device)
            phase8_8_model.eval()

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
                preds_15 = candidate_model.decode_predictions(out, conf_threshold=0.15, iou_threshold=0.45)[0]
                preds_floor = candidate_model.decode_predictions(out, conf_threshold=0.05, iou_threshold=0.45)[0]

            val_preds_tau15[img_p.stem] = preds_15
            val_preds_floor[img_p.stem] = preds_floor

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
            filtered_preds = {}
            for stem, preds in val_preds_floor.items():
                filtered_preds[stem] = [d for d in preds if d["confidence"] >= tau]

            rep = ComprehensiveEvaluator.evaluate(
                predictions_by_image=filtered_preds,
                ground_truth_by_image=val_gts,
                model_id=self.model_id,
                model_version=f"sweep_tau_{tau:.2f}",
                dataset_name="phase8_8_validation_split",
                target_classes=CLASS_NAMES,
            )

            sweep_results.append({
                "threshold": tau,
                "precision": round(rep.meanPrecision, 4),
                "recall": round(rep.meanRecall, 4),
                "f1": round(rep.meanF1, 4),
                "mAP50": round(rep.mAP50, 4),
                "extinguisher_ap50": round(rep.perClassMetrics["FIRE_EXTINGUISHER"].ap50, 4),
                "door_ap50": round(rep.perClassMetrics["EXIT_DOOR"].ap50, 4),
                "sign_ap50": round(rep.perClassMetrics["EXIT_SIGN"].ap50, 4),
                "obstacle_ap50": round(rep.perClassMetrics["OBSTACLE"].ap50, 4),
            })
            print(f"    - tau={tau:.2f} | Prec={rep.meanPrecision:.4f} | Rec={rep.meanRecall:.4f} | F1={rep.meanF1:.4f} | mAP50={rep.mAP50:.4f} | "
                  f"Ext={rep.perClassMetrics['FIRE_EXTINGUISHER'].ap50:.4f} | Door={rep.perClassMetrics['EXIT_DOOR'].ap50:.4f} | "
                  f"Sign={rep.perClassMetrics['EXIT_SIGN'].ap50:.4f} | Obst={rep.perClassMetrics['OBSTACLE'].ap50:.4f}")

        (ARTIFACTS_DIR / "metrics" / "confidence_sweep_metrics.json").write_text(
            json.dumps({"threshold_sweep": sweep_results}, indent=2), encoding="utf-8"
        )

        # ======================================================================
        # 3. UNTOUCHED PHASE 8.3 EXTERNAL TEST SET EVALUATION (522 images)
        # ======================================================================
        print("\n[*] Evaluating Candidate Model Strictly ONCE on Untouched Phase 8.3 Test Set (522 images)...")
        test_img_dir = PREPARED_8_3 / "yolo" / "images" / "test"
        test_lbl_dir = PREPARED_8_3 / "yolo" / "labels" / "test"
        test_images = sorted(list(test_img_dir.glob("*.jpg")))
        print(f"[*] Discovered {len(test_images)} images in the untouched Phase 8.3 test set.")

        test_preds_candidate = {}
        test_preds_baseline = {}
        test_preds_phase8_8 = {}
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

                if phase8_8_model:
                    out_p88 = phase8_8_model(tensor)
                    p88_dec = phase8_8_model.decode_predictions(out_p88, conf_threshold=0.15, iou_threshold=0.45)[0]
                    test_preds_phase8_8[img_p.stem] = p88_dec

            test_preds_candidate[img_p.stem] = cand_dec
            test_preds_baseline[img_p.stem] = base_dec

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

        # Evaluate Candidate on untouched test set
        cand_test_report = ComprehensiveEvaluator.evaluate(
            predictions_by_image=test_preds_candidate,
            ground_truth_by_image=test_gts,
            model_id=self.model_id,
            model_version=self.model_version,
            dataset_name="phase8_3_untouched_test_split",
            target_classes=CLASS_NAMES,
        )

        # Evaluate Baseline on untouched test set
        base_test_report = ComprehensiveEvaluator.evaluate(
            predictions_by_image=test_preds_baseline,
            ground_truth_by_image=test_gts,
            model_id="firesphere-perception-baseline-v0.8.0",
            model_version="0.8.0-baseline",
            dataset_name="phase8_3_untouched_test_split",
            target_classes=CLASS_NAMES,
        )

        # Evaluate Phase 8.8 reference on untouched test set if available
        p88_test_report = None
        if test_preds_phase8_8:
            p88_test_report = ComprehensiveEvaluator.evaluate(
                predictions_by_image=test_preds_phase8_8,
                ground_truth_by_image=test_gts,
                model_id="firesphere-perception-candidate-v0.8.8",
                model_version="0.8.8-candidate",
                dataset_name="phase8_3_untouched_test_split",
                target_classes=CLASS_NAMES,
            )

        test_report_dict = cand_test_report.model_dump()
        (ARTIFACTS_DIR / "metrics" / "test_metrics.json").write_text(
            json.dumps(test_report_dict, indent=2), encoding="utf-8"
        )

        print(f"\n[+] Final External Test Evaluation Results (Apples-to-Apples on 522 images):")
        print(f"-----------------------------------------------------------------------------------------")
        print(f"{'Metric':<22} | {'Phase 8.4 Baseline':<18} | {'Phase 8.8 Ref':<15} | {'Phase 8.9 Candidate':<18} | {'Delta vs 8.4':<10}")
        print(f"-----------------------------------------------------------------------------------------")
        p88_mp = p88_test_report.meanPrecision if p88_test_report else 0.0
        p88_mr = p88_test_report.meanRecall if p88_test_report else 0.0
        p88_mf1 = p88_test_report.meanF1 if p88_test_report else 0.0
        p88_map50 = p88_test_report.mAP50 if p88_test_report else 0.0
        p88_map50_95 = p88_test_report.mAP50_95 if p88_test_report else 0.0

        print(f"{'Mean Precision':<22} | {base_test_report.meanPrecision:<18.4f} | {p88_mp:<15.4f} | {cand_test_report.meanPrecision:<18.4f} | {cand_test_report.meanPrecision - base_test_report.meanPrecision:+.4f}")
        print(f"{'Mean Recall':<22} | {base_test_report.meanRecall:<18.4f} | {p88_mr:<15.4f} | {cand_test_report.meanRecall:<18.4f} | {cand_test_report.meanRecall - base_test_report.meanRecall:+.4f}")
        print(f"{'Mean F1':<22} | {base_test_report.meanF1:<18.4f} | {p88_mf1:<15.4f} | {cand_test_report.meanF1:<18.4f} | {cand_test_report.meanF1 - base_test_report.meanF1:+.4f}")
        print(f"{'mAP@50':<22} | {base_test_report.mAP50:<18.4f} | {p88_map50:<15.4f} | {cand_test_report.mAP50:<18.4f} | {cand_test_report.mAP50 - base_test_report.mAP50:+.4f}")
        print(f"{'mAP@50:95':<22} | {base_test_report.mAP50_95:<18.4f} | {p88_map50_95:<15.4f} | {cand_test_report.mAP50_95:<18.4f} | {cand_test_report.mAP50_95 - base_test_report.mAP50_95:+.4f}")
        print(f"-----------------------------------------------------------------------------------------")
        for cname in ["FIRE_EXTINGUISHER", "EXIT_DOOR", "EXIT_SIGN", "OBSTACLE"]:
            b_m = base_test_report.perClassMetrics[cname]
            c_m = cand_test_report.perClassMetrics[cname]
            p_m = p88_test_report.perClassMetrics[cname] if p88_test_report else None
            p_ap = p_m.ap50 if p_m else 0.0
            print(f"  {cname}:")
            print(f"    - AP50:      Baseline = {b_m.ap50:.4f} | Phase 8.8 = {p_ap:.4f} | Candidate = {c_m.ap50:.4f} (Delta vs 8.4: {c_m.ap50 - b_m.ap50:+.4f})")
            print(f"    - Recall:    Baseline = {b_m.recall:.4f} | Candidate = {c_m.recall:.4f} (Delta: {c_m.recall - b_m.recall:+.4f})")
            print(f"    - Precision: Baseline = {b_m.precision:.4f} | Candidate = {c_m.precision:.4f} (Delta: {c_m.precision - b_m.precision:+.4f})")
            print(f"    - TP/FP/FN:  Baseline = ({b_m.truePositives}/{b_m.falsePositives}/{b_m.falseNegatives}) | "
                  f"Candidate = ({c_m.truePositives}/{c_m.falsePositives}/{c_m.falseNegatives})")

        # Save model comparison artifact
        comparison_dict = {
            "comparison_version": "1.0.0-phase8.9",
            "test_split_used": "phase8_3_untouched_522_images",
            "baseline_model": {
                "model_id": "firesphere-perception-baseline-v0.8.0",
                "checkpoint": str(CHECKPOINT_8_4),
                "metrics": base_test_report.model_dump(),
            },
            "phase8_8_reference": {
                "model_id": "firesphere-perception-candidate-v0.8.8",
                "checkpoint": str(CHECKPOINT_8_8),
                "metrics": p88_test_report.model_dump() if p88_test_report else None,
            },
            "candidate_model": {
                "model_id": self.model_id,
                "checkpoint": str(best_ckpt_path),
                "metrics": cand_test_report.model_dump(),
            },
            "deltas_vs_phase8_4_baseline": {
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
                if g_idx not in matched_gts and vis_counts["fn"] < 10:
                    canvas = img.copy()
                    g_box = gt["boundingBox"]
                    x1 = int(g_box["xMin"] * w_img)
                    y1 = int(g_box["yMin"] * h_img)
                    x2 = int(g_box["xMax"] * w_img)
                    y2 = int(g_box["yMax"] * h_img)
                    cv2.rectangle(canvas, (x1, y1), (x2, y2), (255, 0, 0), 2)
                    cv2.putText(canvas, f"FN: {gt['className']}", (x1, max(20, y1 - 5)),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 0, 0), 2)
                    cv2.imwrite(str(vis_dir / "fn" / f"FN_{gt['className']}_{stem}.jpg"), canvas)
                    vis_counts["fn"] += 1

        print(f"[+] Error Visualizations generated: {vis_counts['tp']} TPs, {vis_counts['fp']} FPs, {vis_counts['fn']} FNs.")

        # ======================================================================
        # 6. GATE DECISION DETERMINATION
        # ======================================================================
        delta_map50 = cand_test_report.mAP50 - base_test_report.mAP50
        ext_ap_delta = cand_test_report.perClassMetrics["FIRE_EXTINGUISHER"].ap50 - base_test_report.perClassMetrics["FIRE_EXTINGUISHER"].ap50

        # Evaluate against Phase 8.9 criteria
        # If Extinguisher suffered major regression or overall mAP50 regressed materially
        if ext_ap_delta < -0.05 or delta_map50 < -0.02:
            gate_decision = "CANDIDATE_REGRESSED"
        elif delta_map50 <= 0.005:
            gate_decision = "CANDIDATE_NOT_IMPROVED"
        elif cand_test_report.mAP50 > base_test_report.mAP50 and ext_ap_delta >= -0.02:
            gate_decision = "CANDIDATE_PROMISING_NOT_APPROVED"
        else:
            gate_decision = "CANDIDATE_NOT_IMPROVED"

        best_sha256 = compute_sha256(best_ckpt_path)
        decision_data = {
            "phase": "8.9",
            "gate_decision": gate_decision,
            "production_status": "NOT_APPROVED",
            "provenance_limitation": "Full SHA-256 not officially published by PyTorch; research authorization granted by user.",
            "authoritative_baseline": "firesphere-perception-baseline-v0.8.0 (Phase 8.4)",
            "candidate_model": self.model_id,
            "candidate_checkpoint": str(best_ckpt_path),
            "candidate_sha256": best_sha256,
            "external_test_522_results": {
                "baseline_map50": round(base_test_report.mAP50, 4),
                "candidate_map50": round(cand_test_report.mAP50, 4),
                "delta_map50": round(delta_map50, 4),
                "baseline_extinguisher_ap50": round(base_test_report.perClassMetrics["FIRE_EXTINGUISHER"].ap50, 4),
                "candidate_extinguisher_ap50": round(cand_test_report.perClassMetrics["FIRE_EXTINGUISHER"].ap50, 4),
                "delta_extinguisher_ap50": round(ext_ap_delta, 4),
                "baseline_door_ap50": round(base_test_report.perClassMetrics["EXIT_DOOR"].ap50, 4),
                "candidate_door_ap50": round(cand_test_report.perClassMetrics["EXIT_DOOR"].ap50, 4),
                "baseline_sign_ap50": round(base_test_report.perClassMetrics["EXIT_SIGN"].ap50, 4),
                "candidate_sign_ap50": round(cand_test_report.perClassMetrics["EXIT_SIGN"].ap50, 4),
                "baseline_obstacle_ap50": round(base_test_report.perClassMetrics["OBSTACLE"].ap50, 4),
                "candidate_obstacle_ap50": round(cand_test_report.perClassMetrics["OBSTACLE"].ap50, 4),
            },
            "evaluation_timestamp": datetime.now(timezone.utc).isoformat(),
        }

        (ARTIFACTS_DIR / "metrics" / "phase8_9_decision.json").write_text(
            json.dumps(decision_data, indent=2), encoding="utf-8"
        )

        print(f"\n" + "=" * 80)
        print(f"PHASE 8.9 FINAL DECISION GATE: {gate_decision}")
        print(f"PRODUCTION STATUS:             NOT_APPROVED")
        print("=" * 80)

        return decision_data


def main():
    trainer = Phase89TransferLearningTrainer(seed=42)
    best_ckpt = ARTIFACTS_DIR / "checkpoints" / "best.pt"
    train_cfg = ARTIFACTS_DIR / "metrics" / "training_config.json"
    if best_ckpt.exists() and train_cfg.exists():
        print(f"[*] Found completed training artifacts: {best_ckpt} and {train_cfg}")
        print("[*] Skipping re-training and proceeding directly to evaluations and comparisons.")
    else:
        trainer.train_candidate()
    trainer.run_evaluations_and_comparisons()


if __name__ == "__main__":
    main()
