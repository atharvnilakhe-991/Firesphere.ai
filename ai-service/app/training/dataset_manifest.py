"""Deterministic dataset manifest generation for FireSphere fire safety splits."""

from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Set
import cv2
from pydantic import BaseModel, Field
from app.models.model_metadata import FIRE_INSPECTION_CLASSES


class ManifestRecord(BaseModel):
    """Deterministic, reproducible metadata entry for a single dataset sample."""

    imageId: str
    imagePath: str
    sha256: str
    width: int
    height: int
    sourceDataset: str = "firesphere-fire-safety-dataset"
    sourceClassNames: List[str] = Field(default_factory=list)
    targetClasses: List[int] = Field(default_factory=list)
    annotationPath: str
    split: str = Field(description="'train', 'val', or 'test'")
    groupId: Optional[str] = None

    def to_jsonl_dict(self) -> Dict[str, Any]:
        """Formats record according to Section 15 JSONL schema."""
        source_cls = self.sourceClassNames[0] if self.sourceClassNames else ""
        mapped_cls = ""
        if self.targetClasses:
            cid = self.targetClasses[0]
            mapped_cls = FIRE_INSPECTION_CLASSES[cid].name if cid in FIRE_INSPECTION_CLASSES else str(cid)

        return {
            "sample_id": self.imageId,
            "image_path": self.imagePath,
            "annotation_path": self.annotationPath,
            "source_dataset": self.sourceDataset,
            "source_class": source_cls,
            "mapped_class": mapped_cls,
            "split": self.split,
            "sha256": self.sha256,
            "group_id": self.groupId or self.imageId,
        }


class DatasetManifest(BaseModel):
    """Consolidated manifest file for a specific split."""

    manifestVersion: str = "1.0.0"
    generatedAt: str = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    split: str
    totalRecords: int = 0
    manifestChecksum: str = ""
    records: List[ManifestRecord] = Field(default_factory=list)


class ManifestGenerator:
    """Generates immutable, cryptographically verifiable split manifests."""

    VALID_IMAGE_EXTENSIONS: Set[str] = {".jpg", ".jpeg", ".png", ".webp"}

    @staticmethod
    def compute_sha256(path: Path) -> str:
        """Computes SHA-256 hash of file."""
        hasher = hashlib.sha256()
        with open(path, "rb") as f:
            while chunk := f.read(65536):
                hasher.update(chunk)
        return hasher.hexdigest()

    @classmethod
    def generate_manifest_for_split(
        cls,
        split_name: str,
        split_items: List[str],
        images_dir: Path,
        annotations_dir: Path,
        source_dataset_id: str = "firesphere-fire-safety-dataset",
        output_manifest_path: Optional[Path] = None,
        groups: Optional[Dict[str, str]] = None,
    ) -> DatasetManifest:
        """Constructs a deterministic manifest for a list of sample file stems in a given split."""
        images_dir = Path(images_dir)
        annotations_dir = Path(annotations_dir)
        groups = groups or {}

        records: List[ManifestRecord] = []

        for stem in sorted(split_items):
            # Locate image file
            img_path: Optional[Path] = None
            for ext in cls.VALID_IMAGE_EXTENSIONS:
                candidate = images_dir / f"{stem}{ext}"
                if candidate.exists():
                    img_path = candidate
                    break

            if not img_path or not img_path.exists():
                continue

            annot_path = annotations_dir / f"{stem}.txt"
            if not annot_path.exists():
                continue

            sha256 = cls.compute_sha256(img_path)

            width = 0
            height = 0
            try:
                mat = cv2.imread(str(img_path))
                if mat is not None:
                    height, width = mat.shape[:2]
            except Exception:
                pass

            target_classes: Set[int] = set()
            try:
                with open(annot_path, "r", encoding="utf-8") as f:
                    for line in f:
                        tokens = line.strip().split()
                        if len(tokens) >= 5:
                            try:
                                cid = int(tokens[0])
                                if cid in FIRE_INSPECTION_CLASSES:
                                    target_classes.add(cid)
                            except ValueError:
                                pass
            except Exception:
                pass

            target_class_names = [
                FIRE_INSPECTION_CLASSES[cid].name
                for cid in sorted(list(target_classes))
            ]

            record = ManifestRecord(
                imageId=stem,
                imagePath=str(img_path.name),
                sha256=sha256,
                width=width,
                height=height,
                sourceDataset=source_dataset_id,
                sourceClassNames=target_class_names,
                targetClasses=sorted(list(target_classes)),
                annotationPath=str(annot_path.name),
                split=split_name,
                groupId=groups.get(stem, stem),
            )
            records.append(record)

        # Compute deterministic manifest checksum across records
        records_serialized = "".join(f"{r.imageId}:{r.sha256}" for r in records)
        manifest_checksum = hashlib.sha256(records_serialized.encode("utf-8")).hexdigest()

        manifest = DatasetManifest(
            split=split_name,
            totalRecords=len(records),
            manifestChecksum=manifest_checksum,
            records=records,
        )

        if output_manifest_path:
            output_manifest_path = Path(output_manifest_path)
            output_manifest_path.parent.mkdir(parents=True, exist_ok=True)
            # Write standard JSON manifest
            with open(output_manifest_path, "w", encoding="utf-8") as f:
                json.dump(manifest.model_dump(), f, indent=2)

            # Also write JSONL manifest (.jsonl)
            jsonl_path = output_manifest_path.with_suffix(".jsonl")
            with open(jsonl_path, "w", encoding="utf-8") as f_jsonl:
                for rec in records:
                    f_jsonl.write(json.dumps(rec.to_jsonl_dict()) + "\n")

        return manifest

    @classmethod
    def generate_all_manifests(
        cls,
        splits_dir: Path,
        images_dir: Path,
        annotations_dir: Path,
        manifests_dir: Path,
        source_dataset_id: str = "firesphere-fire-safety-dataset",
        groups: Optional[Dict[str, str]] = None,
    ) -> Dict[str, DatasetManifest]:
        """Parses split index files (train.txt, val.txt, test.txt) and generates both JSON and JSONL manifests."""
        splits_dir = Path(splits_dir)
        manifests_dir = Path(manifests_dir)
        manifests_dir.mkdir(parents=True, exist_ok=True)

        results: Dict[str, DatasetManifest] = {}

        for split_name in ("train", "val", "test"):
            split_file = splits_dir / f"{split_name}.txt"
            if not split_file.exists():
                items: List[str] = []
            else:
                with open(split_file, "r", encoding="utf-8") as f:
                    items = [l.strip() for l in f if l.strip()]

            out_path = manifests_dir / f"{split_name}.json"
            manifest = cls.generate_manifest_for_split(
                split_name=split_name,
                split_items=items,
                images_dir=images_dir,
                annotations_dir=annotations_dir,
                source_dataset_id=source_dataset_id,
                output_manifest_path=out_path,
                groups=groups,
            )
            results[split_name] = manifest

        return results

