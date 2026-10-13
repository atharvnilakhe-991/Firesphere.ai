"""Class distribution and balance analysis for fire safety compliance classes."""

from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Optional, Set
from pydantic import BaseModel, Field
from app.core.logging import logger
from app.models.model_metadata import FIRE_INSPECTION_CLASSES


class ClassDistributionProfile(BaseModel):
    """Detailed breakdown of representation across the 10 cataloged fire defect classes."""

    totalAnnotations: int
    totalImages: int
    classInstanceCounts: Dict[int, int]
    classImageCounts: Dict[int, int]
    classPercentages: Dict[int, float]
    underrepresentedClasses: List[int]
    emptyClasses: List[int]
    imbalanceRatio: float = Field(
        description="Ratio of majority class count to non-zero minority class count"
    )
    recommendedWeights: Dict[int, float] = Field(
        description="Inverse-frequency class weights for loss balancing during training"
    )


class ClassBalanceAnalyzer:
    """Analyzes class distributions and computes loss weights and rebalancing recommendations."""

    @classmethod
    def analyze_from_annotations(
        cls,
        annotations_dir: Path,
        underrepresented_threshold_percent: float = 5.0,
    ) -> ClassDistributionProfile:
        """Parses annotation files to compute per-class instance and image frequencies."""
        annotations_dir = Path(annotations_dir)
        instance_counts: Dict[int, int] = defaultdict(int)
        image_presence: Dict[int, Set[str]] = defaultdict(set)
        total_annotations = 0
        total_images = 0

        if annotations_dir.exists():
            annot_files = [p for p in annotations_dir.iterdir() if p.is_file() and p.suffix.lower() == ".txt"]
            total_images = len(annot_files)

            for p in annot_files:
                try:
                    with open(p, "r", encoding="utf-8") as f:
                        for line in f:
                            tokens = line.strip().split()
                            if len(tokens) >= 5:
                                try:
                                    cid = int(tokens[0])
                                    if cid in FIRE_INSPECTION_CLASSES:
                                        instance_counts[cid] += 1
                                        image_presence[cid].add(p.stem)
                                        total_annotations += 1
                                except ValueError:
                                    continue
                except Exception as e:
                    logger.warning(f"Failed to read annotation file for balance check {p.name}: {e}")

        # Compute percentages and identifying empty / underrepresented classes
        percentages: Dict[int, float] = {}
        empty_classes: List[int] = []
        underrepresented: List[int] = []
        img_counts: Dict[int, int] = {}

        for cid in FIRE_INSPECTION_CLASSES:
            cnt = instance_counts[cid]
            img_counts[cid] = len(image_presence[cid])
            pct = round((cnt / total_annotations * 100.0), 2) if total_annotations > 0 else 0.0
            percentages[cid] = pct

            if cnt == 0:
                empty_classes.append(cid)
            elif pct < underrepresented_threshold_percent:
                underrepresented.append(cid)

        # Imbalance ratio
        non_zero_counts = [c for c in instance_counts.values() if c > 0]
        if non_zero_counts:
            imbalance_ratio = round(max(non_zero_counts) / min(non_zero_counts), 2)
        else:
            imbalance_ratio = 1.0

        # Compute smoothed inverse-frequency weights for loss balancing: w_c = N / (C * n_c)
        num_classes = len(FIRE_INSPECTION_CLASSES)
        weights: Dict[int, float] = {}
        for cid in FIRE_INSPECTION_CLASSES:
            cnt = instance_counts[cid]
            if cnt > 0 and total_annotations > 0:
                raw_w = total_annotations / (num_classes * cnt)
                weights[cid] = round(min(10.0, max(0.1, raw_w)), 3)  # clamp between 0.1 and 10.0
            else:
                weights[cid] = 1.0  # neutral default for unrepresented classes

        return ClassDistributionProfile(
            totalAnnotations=total_annotations,
            totalImages=total_images,
            classInstanceCounts=dict(instance_counts),
            classImageCounts=img_counts,
            classPercentages=percentages,
            underrepresentedClasses=underrepresented,
            emptyClasses=empty_classes,
            imbalanceRatio=imbalance_ratio,
            recommendedWeights=weights,
        )
