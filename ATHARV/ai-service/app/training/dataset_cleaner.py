"""Dataset cleaning, deduplication, and quality control engine for FireSphere fire safety datasets."""

from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple
import cv2
import numpy as np
from pydantic import BaseModel, Field
from app.core.logging import logger
from app.models.model_metadata import FIRE_INSPECTION_CLASSES


class RejectionRecord(BaseModel):
    """Itemized record of a rejected image or annotation."""

    fileName: str
    rejectionReason: str = Field(
        description="Reason code: CORRUPT_IMAGE, DIMENSION_OUT_OF_BOUNDS, DUPLICATE_SHA256, NEAR_DUPLICATE_DHASH, ORPHAN_ANNOTATION, MISSING_ANNOTATION, MALFORMED_ANNOTATION, INVALID_BBOX"
    )
    details: str
    sha256: Optional[str] = None
    timestamp: str = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


class CleaningReport(BaseModel):
    """Summary and itemized details of dataset cleaning operations."""

    datasetName: str = "firesphere-fire-safety-dataset"
    timestamp: str = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    totalEvaluatedImages: int = 0
    totalEvaluatedAnnotations: int = 0
    usableImages: int = 0
    usableAnnotations: int = 0
    rejectedImagesCount: int = 0
    rejectedAnnotationsCount: int = 0
    exactDuplicatesCount: int = 0
    perceptualNearDuplicatesCount: int = 0
    rejectionsByReason: Dict[str, int] = Field(default_factory=dict)
    rejections: List[RejectionRecord] = Field(default_factory=list)


class DatasetCleaner:
    """Performs deterministic hygiene, deduplication, and rejection auditing on fire safety datasets."""

    VALID_IMAGE_EXTENSIONS: Set[str] = {".jpg", ".jpeg", ".png", ".webp"}

    def __init__(
        self,
        min_width: int = 300,
        min_height: int = 300,
        max_dimension: int = 8192,
        min_aspect_ratio: float = 0.2,
        max_aspect_ratio: float = 5.0,
        dhash_distance_threshold: int = 4,
    ):
        self.min_width = min_width
        self.min_height = min_height
        self.max_dimension = max_dimension
        self.min_aspect_ratio = min_aspect_ratio
        self.max_aspect_ratio = max_aspect_ratio
        self.dhash_distance_threshold = dhash_distance_threshold

    @staticmethod
    def compute_sha256(path: Path) -> str:
        """Computes cryptographic SHA-256 hash of file bytes."""
        hasher = hashlib.sha256()
        with open(path, "rb") as f:
            while chunk := f.read(65536):
                hasher.update(chunk)
        return hasher.hexdigest()

    @staticmethod
    def compute_dhash(image_path: Path, hash_size: int = 8) -> Optional[int]:
        """Computes difference hash (dHash) for perceptual near-duplicate detection.

        Resizes to (hash_size + 1, hash_size) grayscale and compares adjacent horizontal pixels.
        Returns a 64-bit integer representing the visual gradient pattern.
        """
        try:
            mat = cv2.imread(str(image_path), cv2.IMREAD_GRAYSCALE)
            if mat is None:
                return None
            resized = cv2.resize(mat, (hash_size + 1, hash_size), interpolation=cv2.INTER_AREA)
            diff = resized[:, 1:] > resized[:, :-1]
            decimal_hash = 0
            for bit in diff.flatten():
                decimal_hash = (decimal_hash << 1) | int(bit)
            return decimal_hash
        except Exception:
            return None

    @staticmethod
    def hamming_distance(hash1: int, hash2: int) -> int:
        """Calculates bitwise Hamming distance between two 64-bit integer hashes."""
        x = hash1 ^ hash2
        return bin(x).count("1")

    def clean_dataset(
        self,
        images_dir: Path,
        annotations_dir: Path,
        rejection_report_path: Optional[Path] = None,
        quarantine_dir: Optional[Path] = None,
    ) -> CleaningReport:
        """Evaluates and cleans images and annotations, recording all rejections deterministically."""
        images_dir = Path(images_dir)
        annotations_dir = Path(annotations_dir)

        report = CleaningReport()

        if not images_dir.exists() or not annotations_dir.exists():
            return report

        image_files = sorted([
            p for p in images_dir.iterdir()
            if p.is_file() and p.suffix.lower() in self.VALID_IMAGE_EXTENSIONS
        ])
        annot_files = sorted([
            p for p in annotations_dir.iterdir()
            if p.is_file() and p.suffix.lower() == ".txt"
        ])

        report.totalEvaluatedImages = len(image_files)
        report.totalEvaluatedAnnotations = len(annot_files)

        image_stems = {p.stem: p for p in image_files}
        annot_stems = {p.stem: p for p in annot_files}

        rejected_stems: Set[str] = set()

        # 1. Missing pair checks (Orphan annotations and unannotated images)
        for stem, p in annot_stems.items():
            if stem not in image_stems:
                rejected_stems.add(stem)
                rec = RejectionRecord(
                    fileName=p.name,
                    rejectionReason="ORPHAN_ANNOTATION",
                    details=f"Annotation exists without corresponding image file '{stem}'",
                )
                report.rejections.append(rec)
                report.rejectedAnnotationsCount += 1
                report.rejectionsByReason["ORPHAN_ANNOTATION"] = (
                    report.rejectionsByReason.get("ORPHAN_ANNOTATION", 0) + 1
                )

        # 2. Image integrity, dimension, and duplicate checks
        seen_sha256: Dict[str, str] = {}
        dhashes: Dict[str, int] = {}

        for img_path in image_files:
            stem = img_path.stem
            try:
                sha = self.compute_sha256(img_path)
            except Exception as e:
                rejected_stems.add(stem)
                rec = RejectionRecord(
                    fileName=img_path.name,
                    rejectionReason="CORRUPT_IMAGE",
                    details=f"Cannot compute hash: {e}",
                )
                report.rejections.append(rec)
                report.rejectedImagesCount += 1
                report.rejectionsByReason["CORRUPT_IMAGE"] = report.rejectionsByReason.get("CORRUPT_IMAGE", 0) + 1
                continue

            # Exact duplicate check via SHA-256
            if sha in seen_sha256:
                rejected_stems.add(stem)
                report.exactDuplicatesCount += 1
                rec = RejectionRecord(
                    fileName=img_path.name,
                    rejectionReason="DUPLICATE_SHA256",
                    details=f"Exact duplicate of '{seen_sha256[sha]}'",
                    sha256=sha,
                )
                report.rejections.append(rec)
                report.rejectedImagesCount += 1
                report.rejectionsByReason["DUPLICATE_SHA256"] = (
                    report.rejectionsByReason.get("DUPLICATE_SHA256", 0) + 1
                )
                continue
            seen_sha256[sha] = img_path.name

            # OpenCV decode and dimension validation
            try:
                mat = cv2.imread(str(img_path))
                if mat is None or mat.size == 0:
                    rejected_stems.add(stem)
                    rec = RejectionRecord(
                        fileName=img_path.name,
                        rejectionReason="CORRUPT_IMAGE",
                        details="OpenCV imread failed: empty matrix or unreadable bytes",
                        sha256=sha,
                    )
                    report.rejections.append(rec)
                    report.rejectedImagesCount += 1
                    report.rejectionsByReason["CORRUPT_IMAGE"] = (
                        report.rejectionsByReason.get("CORRUPT_IMAGE", 0) + 1
                    )
                    continue

                h, w = mat.shape[:2]
                if w < self.min_width or h < self.min_height:
                    rejected_stems.add(stem)
                    rec = RejectionRecord(
                        fileName=img_path.name,
                        rejectionReason="DIMENSION_OUT_OF_BOUNDS",
                        details=f"Resolution {w}x{h} below minimum threshold {self.min_width}x{self.min_height}",
                        sha256=sha,
                    )
                    report.rejections.append(rec)
                    report.rejectedImagesCount += 1
                    report.rejectionsByReason["DIMENSION_OUT_OF_BOUNDS"] = (
                        report.rejectionsByReason.get("DIMENSION_OUT_OF_BOUNDS", 0) + 1
                    )
                    continue

                if w > self.max_dimension or h > self.max_dimension:
                    rejected_stems.add(stem)
                    rec = RejectionRecord(
                        fileName=img_path.name,
                        rejectionReason="DIMENSION_OUT_OF_BOUNDS",
                        details=f"Resolution {w}x{h} exceeds maximum allowed {self.max_dimension}",
                        sha256=sha,
                    )
                    report.rejections.append(rec)
                    report.rejectedImagesCount += 1
                    report.rejectionsByReason["DIMENSION_OUT_OF_BOUNDS"] = (
                        report.rejectionsByReason.get("DIMENSION_OUT_OF_BOUNDS", 0) + 1
                    )
                    continue

                aspect_ratio = float(w) / float(h)
                if aspect_ratio < self.min_aspect_ratio or aspect_ratio > self.max_aspect_ratio:
                    rejected_stems.add(stem)
                    rec = RejectionRecord(
                        fileName=img_path.name,
                        rejectionReason="DIMENSION_OUT_OF_BOUNDS",
                        details=f"Extreme aspect ratio {aspect_ratio:.2f} not in [{self.min_aspect_ratio}, {self.max_aspect_ratio}]",
                        sha256=sha,
                    )
                    report.rejections.append(rec)
                    report.rejectedImagesCount += 1
                    report.rejectionsByReason["DIMENSION_OUT_OF_BOUNDS"] = (
                        report.rejectionsByReason.get("DIMENSION_OUT_OF_BOUNDS", 0) + 1
                    )
                    continue

            except Exception as cv_err:
                rejected_stems.add(stem)
                rec = RejectionRecord(
                    fileName=img_path.name,
                    rejectionReason="CORRUPT_IMAGE",
                    details=f"OpenCV decode exception: {cv_err}",
                    sha256=sha,
                )
                report.rejections.append(rec)
                report.rejectedImagesCount += 1
                report.rejectionsByReason["CORRUPT_IMAGE"] = (
                    report.rejectionsByReason.get("CORRUPT_IMAGE", 0) + 1
                )
                continue

            # Perceptual hash for near duplicates
            dh = self.compute_dhash(img_path)
            if dh is not None:
                for prev_name, prev_dh in dhashes.items():
                    dist = self.hamming_distance(dh, prev_dh)
                    if dist <= self.dhash_distance_threshold:
                        report.perceptualNearDuplicatesCount += 1
                        logger.warning(
                            f"Perceptual near-duplicate detected: {img_path.name} similar to {prev_name} (hamming_dist={dist})"
                        )
                        break
                dhashes[img_path.name] = dh

        # 3. Annotation validation for surviving images
        for annot_path in annot_files:
            stem = annot_path.stem
            if stem in rejected_stems:
                continue

            try:
                with open(annot_path, "r", encoding="utf-8") as f:
                    lines = [l.strip() for l in f if l.strip()]

                if not lines:
                    continue  # Valid negative sample

                has_invalid_box = False
                for line_idx, line in enumerate(lines, 1):
                    tokens = line.split()
                    if len(tokens) != 5:
                        has_invalid_box = True
                        rec = RejectionRecord(
                            fileName=annot_path.name,
                            rejectionReason="MALFORMED_ANNOTATION",
                            details=f"Row {line_idx} does not have 5 tokens: '{line}'",
                        )
                        report.rejections.append(rec)
                        break

                    try:
                        cid = int(tokens[0])
                        xc = float(tokens[1])
                        yc = float(tokens[2])
                        w = float(tokens[3])
                        h = float(tokens[4])
                    except ValueError:
                        has_invalid_box = True
                        rec = RejectionRecord(
                            fileName=annot_path.name,
                            rejectionReason="MALFORMED_ANNOTATION",
                            details=f"Non-numeric values in row {line_idx}: '{line}'",
                        )
                        report.rejections.append(rec)
                        break

                    if cid not in FIRE_INSPECTION_CLASSES:
                        has_invalid_box = True
                        rec = RejectionRecord(
                            fileName=annot_path.name,
                            rejectionReason="INVALID_BBOX",
                            details=f"Unsupported class ID {cid} (valid 0..9)",
                        )
                        report.rejections.append(rec)
                        break

                    if xc < 0.0 or xc > 1.0 or yc < 0.0 or yc > 1.0 or w <= 0.0 or w > 1.0 or h <= 0.0 or h > 1.0:
                        has_invalid_box = True
                        rec = RejectionRecord(
                            fileName=annot_path.name,
                            rejectionReason="INVALID_BBOX",
                            details=f"Bounding box coordinates out of bounds: {tokens}",
                        )
                        report.rejections.append(rec)
                        break

                if has_invalid_box:
                    rejected_stems.add(stem)
                    report.rejectedAnnotationsCount += 1
                    report.rejectionsByReason["INVALID_BBOX"] = (
                        report.rejectionsByReason.get("INVALID_BBOX", 0) + 1
                    )

            except Exception as e:
                rejected_stems.add(stem)
                rec = RejectionRecord(
                    fileName=annot_path.name,
                    rejectionReason="MALFORMED_ANNOTATION",
                    details=f"Failed reading annotation file: {e}",
                )
                report.rejections.append(rec)
                report.rejectedAnnotationsCount += 1
                report.rejectionsByReason["MALFORMED_ANNOTATION"] = (
                    report.rejectionsByReason.get("MALFORMED_ANNOTATION", 0) + 1
                )

        # 4. Quarantine or remove rejected files if requested
        if quarantine_dir is not None:
            quarantine_dir = Path(quarantine_dir)
            quarantine_dir.mkdir(parents=True, exist_ok=True)
            for stem in rejected_stems:
                img_p = image_stems.get(stem)
                if img_p and img_p.exists():
                    img_p.rename(quarantine_dir / img_p.name)
                annot_p = annot_stems.get(stem)
                if annot_p and annot_p.exists():
                    annot_p.rename(quarantine_dir / annot_p.name)

        # 5. Summarize usable items
        report.usableImages = len([s for s in image_stems if s not in rejected_stems])
        report.usableAnnotations = len([s for s in annot_stems if s not in rejected_stems])

        # Write rejection report if path specified
        if rejection_report_path:
            rejection_report_path = Path(rejection_report_path)
            rejection_report_path.parent.mkdir(parents=True, exist_ok=True)
            with open(rejection_report_path, "w", encoding="utf-8") as f:
                json.dump(report.model_dump(), f, indent=2)

        return report
