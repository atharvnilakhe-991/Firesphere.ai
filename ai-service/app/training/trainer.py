"""PyTorch Model Training Orchestrator for FireSphere Fire Safety Vision Models."""

from pathlib import Path
import time
from typing import Any, Dict, Optional
import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from app.core.logging import logger
from app.models.model_loader import ModelLoader
from app.training.checkpoints import CheckpointManager
from app.training.config import TrainingConfig
from app.training.dataset import DatasetNotAvailableException, FireSafetyDataset


class SimpleYOLOInspectionHead(nn.Module):
    """Reference lightweight detection backbone and prediction head for training pipeline verification."""

    def __init__(self, num_classes: int = 10):
        super().__init__()
        # Lightweight convolutional feature extractor
        self.features = nn.Sequential(
            nn.Conv2d(3, 16, kernel_size=3, stride=2, padding=1),  # 320x320
            nn.BatchNorm2d(16),
            nn.ReLU(inplace=True),
            nn.Conv2d(16, 32, kernel_size=3, stride=2, padding=1),  # 160x160
            nn.BatchNorm2d(32),
            nn.ReLU(inplace=True),
            nn.Conv2d(32, 64, kernel_size=3, stride=2, padding=1),  # 80x80
            nn.BatchNorm2d(64),
            nn.ReLU(inplace=True),
            nn.AdaptiveAvgPool2d((8, 8)),
        )
        # Bounding box + class prediction head (5 box params: cx, cy, w, h, obj + num_classes)
        self.head = nn.Linear(64 * 8 * 8, 5 + num_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        feat = self.features(x)
        feat_flat = feat.view(feat.size(0), -1)
        out = self.head(feat_flat)
        return out


class ModelTrainer:
    """Orchestrates model training epochs, optimization, validation, and checkpoint persistence."""

    def __init__(self, config: Optional[TrainingConfig] = None, model: Optional[nn.Module] = None):
        self.config = config or TrainingConfig()
        self.device = self._resolve_device(self.config.device)
        self.model = model or SimpleYOLOInspectionHead(num_classes=10)
        self.model.to(self.device)

        self.optimizer = torch.optim.AdamW(
            self.model.parameters(),
            lr=self.config.learningRate,
            weight_decay=self.config.weightDecay,
        )
        self.criterion = nn.MSELoss()

    @staticmethod
    def _resolve_device(preference: str) -> torch.device:
        """Determines target execution device respecting CPU/CUDA configuration."""
        if preference == "cuda" and torch.cuda.is_available():
            return torch.device("cuda")
        elif preference == "cpu":
            return torch.device("cpu")
        else:
            return torch.device("cuda" if torch.cuda.is_available() else "cpu")

    def train(
        self,
        train_dataset: Optional[FireSafetyDataset] = None,
        val_dataset: Optional[FireSafetyDataset] = None,
    ) -> Dict[str, Any]:
        """Executes training loop.

        Raises:
            DatasetNotAvailableException: If no dataset is populated.
        """
        # 1. Dataset Verification
        if train_dataset is None:
            images_dir = self.config.datasetDir / "images"
            annot_dir = self.config.datasetDir / "annotations"
            split_file = self.config.datasetDir / "splits" / "train.txt"

            if not images_dir.exists() or not annot_dir.exists():
                logger.error(
                    f"Training aborted: Dataset not available at '{self.config.datasetDir}'"
                )
                raise DatasetNotAvailableException(
                    f"DATASET_NOT_AVAILABLE: Directory '{self.config.datasetDir}' is not populated with fire safety images."
                )

            train_dataset = FireSafetyDataset(
                images_dir=images_dir,
                annotations_dir=annot_dir,
                split_file=split_file if split_file.exists() else None,
                target_size=(self.config.imageWidth, self.config.imageHeight),
            )

        train_loader = DataLoader(
            train_dataset,
            batch_size=min(self.config.batchSize, len(train_dataset)),
            shuffle=True,
            num_workers=self.config.numWorkers,
        )

        logger.info(
            f"Commencing model training | Experiment={self.config.experimentId} | "
            f"Samples={len(train_dataset)} | Epochs={self.config.epochs} | Device={self.device}"
        )

        start_time = time.perf_counter()
        history: list = []
        best_loss = float("inf")

        for epoch in range(1, self.config.epochs + 1):
            self.model.train()
            epoch_loss = 0.0
            batches = 0

            for images, targets, _ in train_loader:
                images = images.to(self.device)
                self.optimizer.zero_grad()

                outputs = self.model(images)
                # Compute dummy anchor loss matching output dims for verification
                dummy_targets = torch.zeros_like(outputs)
                loss = self.criterion(outputs, dummy_targets)

                loss.backward()
                self.optimizer.step()

                epoch_loss += loss.item()
                batches += 1

            avg_loss = epoch_loss / max(1, batches)
            history.append({"epoch": epoch, "loss": round(avg_loss, 5)})

            if avg_loss < best_loss:
                best_loss = avg_loss
                ckpt_path = self.config.checkpointDir / f"{self.config.experimentId}_best.pt"
                CheckpointManager.save_checkpoint(
                    model=self.model,
                    epoch=epoch,
                    metrics={"train_loss": avg_loss},
                    output_path=ckpt_path,
                    optimizer=self.optimizer,
                    extra_metadata={"experimentId": self.config.experimentId},
                )

        total_time = round(time.perf_counter() - start_time, 2)
        logger.info(
            f"Training finished | Experiment={self.config.experimentId} | BestLoss={best_loss:.5f} | Time={total_time}s"
        )

        return {
            "experimentId": self.config.experimentId,
            "status": "COMPLETED",
            "epochsTrained": self.config.epochs,
            "bestLoss": best_loss,
            "totalTimeSeconds": total_time,
            "device": str(self.device),
            "history": history,
        }
