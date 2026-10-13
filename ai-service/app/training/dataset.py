"""PyTorch Dataset implementation for FireSphere YOLO-formatted fire compliance images."""

from pathlib import Path
from typing import List, Optional, Tuple
import cv2
import numpy as np
import torch
from torch.utils.data import Dataset
from app.core.exceptions import FireSphereAIException
from app.cv.preprocessor import CVPreprocessor


class DatasetNotAvailableException(FireSphereAIException):
    """Raised when training is initiated without a valid, populated dataset."""

    def __init__(self, message: str = "DATASET_NOT_AVAILABLE: No annotated fire safety dataset found"):
        super().__init__(
            message=message,
            status_code=400,
            error_code="DATASET_NOT_AVAILABLE",
        )


class FireSafetyDataset(Dataset):
    """PyTorch Dataset loading images and bounding box labels from canonical YOLO layout."""

    def __init__(
        self,
        images_dir: Path,
        annotations_dir: Path,
        split_file: Optional[Path] = None,
        target_size: Tuple[int, int] = (640, 640),
    ):
        self.images_dir = Path(images_dir)
        self.annotations_dir = Path(annotations_dir)
        self.target_size = target_size
        self.preprocessor = CVPreprocessor(
            target_width=target_size[0],
            target_height=target_size[1],
            enable_normalization=True,
        )

        if not self.images_dir.exists() or not self.annotations_dir.exists():
            raise DatasetNotAvailableException(
                f"DATASET_NOT_AVAILABLE: Directory '{self.images_dir}' or '{self.annotations_dir}' does not exist."
            )

        # Load samples from split file or directory
        if split_file and Path(split_file).exists():
            with open(split_file, "r", encoding="utf-8") as f:
                stems = [line.strip() for line in f if line.strip()]
        else:
            image_extensions = {".jpg", ".jpeg", ".png", ".webp"}
            stems = [p.stem for p in self.images_dir.iterdir() if p.is_file() and p.suffix.lower() in image_extensions]

        self.samples: List[str] = sorted(stems)

        if not self.samples:
            raise DatasetNotAvailableException(
                f"DATASET_NOT_AVAILABLE: No images found in '{self.images_dir}'."
            )

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, torch.Tensor, str]:
        stem = self.samples[idx]

        # 1. Locate and read image
        img_candidates = list(self.images_dir.glob(f"{stem}.*"))
        if not img_candidates:
            raise FileNotFoundError(f"Image for stem '{stem}' not found in {self.images_dir}")
        img_path = img_candidates[0]

        with open(img_path, "rb") as f:
            raw_bytes = f.read()

        prep_result = self.preprocessor.preprocess(raw_bytes, evidence_id=stem)
        img_np = prep_result.preprocessed_array  # (H, W, C) float32 RGB [0.0, 1.0]

        # Convert to PyTorch Tensor (C, H, W)
        img_tensor = torch.from_numpy(img_np).permute(2, 0, 1).float()

        # 2. Locate and parse annotations
        annot_path = self.annotations_dir / f"{stem}.txt"
        boxes: List[List[float]] = []

        if annot_path.exists():
            with open(annot_path, "r", encoding="utf-8") as f:
                for line in f:
                    parts = line.strip().split()
                    if len(parts) == 5:
                        try:
                            cid = float(parts[0])
                            xc = float(parts[1])
                            yc = float(parts[2])
                            w = float(parts[3])
                            h = float(parts[4])
                            boxes.append([cid, xc, yc, w, h])
                        except ValueError:
                            continue

        targets_tensor = torch.tensor(boxes, dtype=torch.float32) if boxes else torch.zeros((0, 5), dtype=torch.float32)
        return img_tensor, targets_tensor, stem
