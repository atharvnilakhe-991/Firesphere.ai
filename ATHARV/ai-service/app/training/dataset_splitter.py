"""Deterministic dataset splitting engine with group-aware leakage prevention."""

from pathlib import Path
import random
from typing import Dict, List, Optional, Set, Tuple
from pydantic import BaseModel, Field
from app.core.logging import logger


class DatasetSplitResult(BaseModel):
    """Metadata and manifest result of dataset partitioning."""

    trainCount: int
    valCount: int
    testCount: int
    totalCount: int
    trainPercent: float
    valPercent: float
    testPercent: float
    seed: int
    groupedBy: Optional[str] = None
    groupCount: Optional[int] = None
    trainFiles: List[str]
    valFiles: List[str]
    testFiles: List[str]


class DatasetSplitter:
    """Partitions dataset samples into Train/Val/Test subsets deterministically."""

    @classmethod
    def split(
        cls,
        items: List[str],
        train_ratio: float = 0.70,
        val_ratio: float = 0.20,
        test_ratio: float = 0.10,
        seed: int = 42,
        groups: Optional[Dict[str, str]] = None,
    ) -> DatasetSplitResult:
        """Splits item identifiers into train, validation, and test subsets.

        Args:
            items: List of item identifiers or file stems.
            train_ratio: Proportion for training (e.g. 0.70).
            val_ratio: Proportion for validation (e.g. 0.20).
            test_ratio: Proportion for testing (e.g. 0.10).
            seed: Fixed integer seed for exact reproducibility.
            groups: Optional mapping from item -> group_id (e.g. inspectionId).
                   When provided, entire groups are assigned together to prevent data leakage.
        """
        if not items:
            return DatasetSplitResult(
                trainCount=0,
                valCount=0,
                testCount=0,
                totalCount=0,
                trainPercent=0.0,
                valPercent=0.0,
                testPercent=0.0,
                seed=seed,
                trainFiles=[],
                valFiles=[],
                testFiles=[],
            )

        total_ratio = train_ratio + val_ratio + test_ratio
        if abs(total_ratio - 1.0) > 1e-4:
            raise ValueError(f"Split ratios must sum to 1.0 (got {total_ratio:.4f})")

        rng = random.Random(seed)

        # 1. Group-based splitting
        if groups is not None:
            group_to_items: Dict[str, List[str]] = {}
            for item in sorted(items):
                gid = groups.get(item, item)  # fallback to item itself if not grouped
                group_to_items.setdefault(gid, []).append(item)

            unique_groups = sorted(list(group_to_items.keys()))
            rng.shuffle(unique_groups)

            n_groups = len(unique_groups)
            n_train_g = int(round(n_groups * train_ratio))
            n_val_g = int(round(n_groups * val_ratio))

            train_groups = set(unique_groups[:n_train_g])
            val_groups = set(unique_groups[n_train_g:n_train_g + n_val_g])
            test_groups = set(unique_groups[n_train_g + n_val_g:])

            train_files: List[str] = []
            val_files: List[str] = []
            test_files: List[str] = []

            for gid, gitems in group_to_items.items():
                if gid in train_groups:
                    train_files.extend(gitems)
                elif gid in val_groups:
                    val_files.extend(gitems)
                else:
                    test_files.extend(gitems)

            # Guarantee zero intersection between groups
            assert len(train_groups.intersection(val_groups)) == 0
            assert len(train_groups.intersection(test_groups)) == 0
            assert len(val_groups.intersection(test_groups)) == 0

            grouped_by_desc = "inspectionId / buildingGroup"
            group_count_val = n_groups

        # 2. Standard uniform item splitting
        else:
            shuffled_items = sorted(list(set(items)))
            rng.shuffle(shuffled_items)

            n_items = len(shuffled_items)
            n_train = int(round(n_items * train_ratio))
            n_val = int(round(n_items * val_ratio))

            train_files = shuffled_items[:n_train]
            val_files = shuffled_items[n_train:n_train + n_val]
            test_files = shuffled_items[n_train + n_val:]

            grouped_by_desc = None
            group_count_val = None

        total = len(train_files) + len(val_files) + len(test_files)
        tr_pct = round((len(train_files) / total) * 100.0, 1) if total else 0.0
        val_pct = round((len(val_files) / total) * 100.0, 1) if total else 0.0
        te_pct = round((len(test_files) / total) * 100.0, 1) if total else 0.0

        logger.info(
            f"Dataset partitioned | Total={total} | Train={len(train_files)} ({tr_pct}%) | "
            f"Val={len(val_files)} ({val_pct}%) | Test={len(test_files)} ({te_pct}%) | Seed={seed}"
        )

        return DatasetSplitResult(
            trainCount=len(train_files),
            valCount=len(val_files),
            testCount=len(test_files),
            totalCount=total,
            trainPercent=tr_pct,
            valPercent=val_pct,
            testPercent=te_pct,
            seed=seed,
            groupedBy=grouped_by_desc,
            groupCount=group_count_val,
            trainFiles=sorted(train_files),
            valFiles=sorted(val_files),
            testFiles=sorted(test_files),
        )

    @classmethod
    def save_splits(cls, split_result: DatasetSplitResult, output_dir: Path) -> Tuple[Path, Path, Path]:
        """Writes train.txt, val.txt, and test.txt index manifests to disk."""
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        train_path = output_dir / "train.txt"
        val_path = output_dir / "val.txt"
        test_path = output_dir / "test.txt"

        with open(train_path, "w", encoding="utf-8") as f:
            f.write("\n".join(split_result.trainFiles) + ("\n" if split_result.trainFiles else ""))

        with open(val_path, "w", encoding="utf-8") as f:
            f.write("\n".join(split_result.valFiles) + ("\n" if split_result.valFiles else ""))

        with open(test_path, "w", encoding="utf-8") as f:
            f.write("\n".join(split_result.testFiles) + ("\n" if split_result.testFiles else ""))

        logger.info(f"Split manifests persisted to {output_dir}")
        return train_path, val_path, test_path
