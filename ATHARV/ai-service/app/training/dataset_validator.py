"""Dataset validation engine for FireSphere fire safety object detection datasets."""

from collections import defaultdict
import hashlib
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple
import cv2
from pydantic import BaseModel, Field
from app.core.logging import logger
from app.models.model_metadata import FIRE_INSPECTION_CLASSES


class DatasetValidationReport(BaseModel):
    """Structured report produced by validating a fire safety dataset."""

    datasetName: str = "firesphere-fire-safety-dataset"
    status: str = Field(description="'VALID' or 'INVALID'")
    totalImages: int = 0
    totalAnnotations: int = 0
    validImages: int = 0
    invalidImages: int = 0
    validAnnotations: int = 0
    invalidAnnotations: int = 0
    emptyAnnotationImages: int = 0
    classDistribution: Dict[int, int] = Field(default_factory=dict)
    duplicateImages: List[Dict[str, str]] = Field(default_factory=list)
    leakageDetections: List[Dict[str, str]] = Field(default_factory=list)
    missingAnnotations: List[str] = Field(default_factory=list)
    missingImages: List[str] = Field(default_factory=list)
    validationIssues: List[str] = Field(default_factory=list)

    def generate_summary_text(self) -> str:
        """Renders standard human-readable dataset validation report."""
        lines = [
            "============================================================",
            f"FireSphere.ai Dataset Validation Report: {self.datasetName}",
            "============================================================",
            f"Status: {self.status}",
            "",
            "Image & Annotation Totals:",
            f"  Total Images:        {self.totalImages}",
            f"  Valid Images:        {self.validImages}",
            f"  Invalid/Corrupt:     {self.invalidImages}",
            f"  Total Annotations:   {self.totalAnnotations}",
            f"  Valid Bounding Boxes:{self.validAnnotations}",
            f"  Invalid Bounding Boxes:{self.invalidAnnotations}",
            f"  Empty (Negative) Img:{self.emptyAnnotationImages}",
            "",
            "Class Distribution (0..9):",
        ]

        for cid, meta in FIRE_INSPECTION_CLASSES.items():
            count = self.classDistribution.get(cid, 0)
            status_flag = " [UNDERREPRESENTED]" if count < 10 and count > 0 else ""
            status_flag = " [EMPTY]" if count == 0 else status_flag
            lines.append(f"  Class {cid} ({meta.name}): {count}{status_flag}")

        lines.extend([
            "",
            f"Duplicates Detected: {len(self.duplicateImages)}",
            f"Split Leakages:      {len(self.leakageDetections)}",
            f"Missing Annotations: {len(self.missingAnnotations)}",
            f"Missing Images:      {len(self.missingImages)}",
            f"Validation Issues:   {len(self.validationIssues)}",
        ])

        if self.validationIssues:
            lines.append("\nTop Issues Detected:")
            for issue in self.validationIssues[:10]:
                lines.append(f"  - {issue}")
            if len(self.validationIssues) > 10:
                lines.append(f"  - ... and {len(self.validationIssues) - 10} more issues.")

        lines.append("============================================================")
        return "\n".join(lines)


class DatasetValidator:
    """Validates YOLO-format datasets for fire compliance inspection."""

    VALID_IMAGE_EXTENSIONS: Set[str] = {".jpg", ".jpeg", ".png", ".webp"}

    @staticmethod
    def compute_sha256(path: Path) -> str:
        """Computes SHA-256 hash of a file for content duplicate detection."""
        hasher = hashlib.sha256()
        with open(path, "rb") as f:
            while chunk := f.read(65536):
                hasher.update(chunk)
        return hasher.hexdigest()

    @classmethod
    def validate_dataset(
        cls,
        images_dir: Path,
        annotations_dir: Path,
        splits_dir: Optional[Path] = None,
        dataset_name: str = "firesphere-fire-safety-dataset",
    ) -> DatasetValidationReport:
        """Performs comprehensive validation of images, annotations, duplicates, and split leakage."""
        images_dir = Path(images_dir)
        annotations_dir = Path(annotations_dir)

        report = DatasetValidationReport(
            datasetName=dataset_name,
            status="VALID",
        )

        if not images_dir.exists():
            report.status = "INVALID"
            report.validationIssues.append(f"Images directory does not exist: {images_dir}")
            return report

        if not annotations_dir.exists():
            report.status = "INVALID"
            report.validationIssues.append(f"Annotations directory does not exist: {annotations_dir}")
            return report

        # Discover all image and annotation files
        image_files = [p for p in images_dir.iterdir() if p.is_file() and p.suffix.lower() in cls.VALID_IMAGE_EXTENSIONS]
        annot_files = [p for p in annotations_dir.iterdir() if p.is_file() and p.suffix.lower() == ".txt"]

        report.totalImages = len(image_files)

        image_stem_map: Dict[str, Path] = {p.stem: p for p in image_files}
        annot_stem_map: Dict[str, Path] = {p.stem: p for p in annot_files}

        # 1. Missing Pair Checks
        for stem in image_stem_map:
            if stem not in annot_stem_map:
                report.missingAnnotations.append(f"{stem}.txt")
                report.validationIssues.append(f"Missing annotation file for image '{stem}'")

        for stem in annot_stem_map:
            if stem not in image_stem_map:
                report.missingImages.append(f"{stem}")
                report.validationIssues.append(f"Missing image file for annotation '{stem}.txt'")

        # 2. Duplicate Image Content Detection via SHA-256
        seen_hashes: Dict[str, str] = {}
        for img_path in image_files:
            try:
                img_hash = cls.compute_sha256(img_path)
                if img_hash in seen_hashes:
                    orig_file = seen_hashes[img_hash]
                    report.duplicateImages.append({
                        "original": orig_file,
                        "duplicate": img_path.name,
                        "sha256": img_hash,
                    })
                    report.validationIssues.append(
                        f"Duplicate image content: '{img_path.name}' identical to '{orig_file}'"
                    )
                else:
                    seen_hashes[img_hash] = img_path.name
            except Exception as e:
                report.invalidImages += 1
                report.validationIssues.append(f"Could not hash image '{img_path.name}': {e}")

        # 3. Image Integrity & Decode Validation
        for img_path in image_files:
            try:
                mat = cv2.imread(str(img_path))
                if mat is None or mat.size == 0 or mat.shape[0] == 0 or mat.shape[1] == 0:
                    report.invalidImages += 1
                    report.validationIssues.append(f"Corrupt image payload: cannot decode '{img_path.name}'")
                else:
                    report.validImages += 1
            except Exception as decode_err:
                report.invalidImages += 1
                report.validationIssues.append(f"Decode error on '{img_path.name}': {decode_err}")

        # 4. Annotation Parsing & Bounding Box Coordinates Validation
        class_counts: Dict[int, int] = defaultdict(int)

        for annot_path in annot_files:
            try:
                with open(annot_path, "r", encoding="utf-8") as f:
                    lines = [line.strip() for line in f if line.strip()]

                if not lines:
                    report.emptyAnnotationImages += 1
                    continue

                for line_idx, line in enumerate(lines, 1):
                    report.totalAnnotations += 1
                    tokens = line.split()

                    # YOLO requires exactly 5 values: class_id x_center y_center width height
                    if len(tokens) != 5:
                        report.invalidAnnotations += 1
                        report.validationIssues.append(
                            f"Malformed YOLO format in {annot_path.name}:{line_idx} (expected 5 tokens, got {len(tokens)})"
                        )
                        continue

                    try:
                        cls_id = int(tokens[0])
                        xc = float(tokens[1])
                        yc = float(tokens[2])
                        w = float(tokens[3])
                        h = float(tokens[4])
                    except ValueError:
                        report.invalidAnnotations += 1
                        report.validationIssues.append(
                            f"Non-numeric tokens in {annot_path.name}:{line_idx}: '{line}'"
                        )
                        continue

                    # Validate class ID
                    if cls_id not in FIRE_INSPECTION_CLASSES:
                        report.invalidAnnotations += 1
                        report.validationIssues.append(
                            f"Unsupported class ID {cls_id} in {annot_path.name}:{line_idx} (catalog is 0..9)"
                        )
                        continue

                    # Validate coordinate ranges: 0.0 <= xc, yc, w, h <= 1.0 and positive box
                    if xc < 0.0 or xc > 1.0 or yc < 0.0 or yc > 1.0:
                        report.invalidAnnotations += 1
                        report.validationIssues.append(
                            f"Center coordinate out of bounds [0.0, 1.0] in {annot_path.name}:{line_idx}: xc={xc}, yc={yc}"
                        )
                        continue

                    if w <= 0.0 or w > 1.0 or h <= 0.0 or h > 1.0:
                        report.invalidAnnotations += 1
                        report.validationIssues.append(
                            f"Box dimension out of bounds (0.0, 1.0] in {annot_path.name}:{line_idx}: w={w}, h={h}"
                        )
                        continue

                    # Boundary extent checks
                    if (xc - w / 2.0 < -0.05) or (xc + w / 2.0 > 1.05) or (yc - h / 2.0 < -0.05) or (yc + h / 2.0 > 1.05):
                        report.invalidAnnotations += 1
                        report.validationIssues.append(
                            f"Bounding box extends excessively beyond image canvas in {annot_path.name}:{line_idx}"
                        )
                        continue

                    # Valid box
                    report.validAnnotations += 1
                    class_counts[cls_id] += 1

            except Exception as annot_err:
                report.validationIssues.append(f"Failed to read annotation file '{annot_path.name}': {annot_err}")

        report.classDistribution = dict(class_counts)

        # 5. Split Leakage Detection (if splits_dir provided)
        if splits_dir and Path(splits_dir).exists():
            split_files = {"train": splits_dir / "train.txt", "val": splits_dir / "val.txt", "test": splits_dir / "test.txt"}
            split_sets: Dict[str, Set[str]] = {}

            for sname, spath in split_files.items():
                if spath.exists():
                    with open(spath, "r", encoding="utf-8") as f:
                        split_sets[sname] = {line.strip() for line in f if line.strip()}

            # Check pairwise intersections
            pairs = [("train", "val"), ("train", "test"), ("val", "test")]
            for s1, s2 in pairs:
                if s1 in split_sets and s2 in split_sets:
                    overlap = split_sets[s1].intersection(split_sets[s2])
                    for item in overlap:
                        report.leakageDetections.append({
                            "splitA": s1,
                            "splitB": s2,
                            "identifier": item,
                        })
                        report.validationIssues.append(
                            f"DATA LEAKAGE: '{item}' appears in both {s1} and {s2} splits!"
                        )

        # Final Status determination
        has_fatal_errors = (
            report.invalidImages > 0
            or report.invalidAnnotations > 0
            or len(report.missingAnnotations) > 0
            or len(report.missingImages) > 0
            or len(report.leakageDetections) > 0
        )
        report.status = "INVALID" if has_fatal_errors else "VALID"

        logger.info(
            f"Dataset validation completed | Status={report.status} | Images={report.totalImages} | "
            f"Annotations={report.totalAnnotations} | Issues={len(report.validationIssues)}"
        )
        return report

    @classmethod
    def generate_annotation_validation_report(
        cls,
        images_dir: Path,
        annotations_dir: Path,
        output_report_path: Optional[Path] = None,
    ) -> Dict[str, Any]:
        """Generates detailed bounding box annotation validation report per Section 10."""
        images_dir = Path(images_dir)
        annotations_dir = Path(annotations_dir)

        annot_files = (
            [p for p in annotations_dir.iterdir() if p.is_file() and p.suffix.lower() == ".txt"]
            if annotations_dir.exists()
            else []
        )

        total_annotations = 0
        valid_annotations = 0
        invalid_annotations = 0
        rejection_counts = {
            "negative_coordinates": 0,
            "nan_or_inf": 0,
            "zero_area_boxes": 0,
            "out_of_bounds": 0,
            "invalid_class_id": 0,
            "malformed_syntax": 0,
            "missing_annotation_file": 0,
        }
        rejection_details: List[Dict[str, Any]] = []

        # Check for images without annotations
        if images_dir.exists():
            for img_p in images_dir.iterdir():
                if img_p.is_file() and img_p.suffix.lower() in cls.VALID_IMAGE_EXTENSIONS:
                    ann_p = annotations_dir / f"{img_p.stem}.txt"
                    if not ann_p.exists():
                        rejection_counts["missing_annotation_file"] += 1
                        invalid_annotations += 1
                        rejection_details.append({
                            "file": img_p.name,
                            "reason": "missing_annotation_file",
                            "message": f"Image '{img_p.name}' missing corresponding annotation file",
                        })

        for annot_p in annot_files:
            try:
                with open(annot_p, "r", encoding="utf-8") as f:
                    lines = [l.strip() for l in f if l.strip()]

                for l_idx, line in enumerate(lines, 1):
                    total_annotations += 1
                    tokens = line.split()
                    if len(tokens) != 5:
                        invalid_annotations += 1
                        rejection_counts["malformed_syntax"] += 1
                        rejection_details.append({
                            "file": annot_p.name,
                            "line": l_idx,
                            "reason": "malformed_syntax",
                            "raw": line,
                        })
                        continue

                    try:
                        cid = int(tokens[0])
                        xc = float(tokens[1])
                        yc = float(tokens[2])
                        w = float(tokens[3])
                        h = float(tokens[4])
                    except ValueError:
                        invalid_annotations += 1
                        rejection_counts["nan_or_inf"] += 1
                        rejection_details.append({
                            "file": annot_p.name,
                            "line": l_idx,
                            "reason": "nan_or_inf",
                            "raw": line,
                        })
                        continue

                    import math
                    if any(math.isnan(v) or math.isinf(v) for v in (xc, yc, w, h)):
                        invalid_annotations += 1
                        rejection_counts["nan_or_inf"] += 1
                        rejection_details.append({
                            "file": annot_p.name,
                            "line": l_idx,
                            "reason": "nan_or_inf",
                            "raw": line,
                        })
                        continue

                    if xc < 0.0 or yc < 0.0 or w < 0.0 or h < 0.0:
                        invalid_annotations += 1
                        rejection_counts["negative_coordinates"] += 1
                        rejection_details.append({
                            "file": annot_p.name,
                            "line": l_idx,
                            "reason": "negative_coordinates",
                            "raw": line,
                        })
                        continue

                    if w == 0.0 or h == 0.0:
                        invalid_annotations += 1
                        rejection_counts["zero_area_boxes"] += 1
                        rejection_details.append({
                            "file": annot_p.name,
                            "line": l_idx,
                            "reason": "zero_area_boxes",
                            "raw": line,
                        })
                        continue

                    if cid not in FIRE_INSPECTION_CLASSES:
                        invalid_annotations += 1
                        rejection_counts["invalid_class_id"] += 1
                        rejection_details.append({
                            "file": annot_p.name,
                            "line": l_idx,
                            "reason": "invalid_class_id",
                            "class_id": cid,
                        })
                        continue

                    if (xc - w / 2.0 < -0.05) or (xc + w / 2.0 > 1.05) or (yc - h / 2.0 < -0.05) or (yc + h / 2.0 > 1.05):
                        invalid_annotations += 1
                        rejection_counts["out_of_bounds"] += 1
                        rejection_details.append({
                            "file": annot_p.name,
                            "line": l_idx,
                            "reason": "out_of_bounds",
                            "raw": line,
                        })
                        continue

                    valid_annotations += 1
            except Exception as e:
                invalid_annotations += 1
                rejection_counts["malformed_syntax"] += 1
                rejection_details.append({
                    "file": annot_p.name,
                    "reason": "read_error",
                    "error": str(e),
                })

        report_data = {
            "report_name": "annotation_validation_report",
            "status": "PASSED" if invalid_annotations == 0 else "FAILED",
            "total_annotations": total_annotations,
            "valid_annotations": valid_annotations,
            "invalid_annotations": invalid_annotations,
            "rejections_by_rule": rejection_counts,
            "details_count": len(rejection_details),
            "rejection_samples": rejection_details[:50],
        }

        if output_report_path:
            out_p = Path(output_report_path)
            out_p.parent.mkdir(parents=True, exist_ok=True)
            import json
            with open(out_p, "w", encoding="utf-8") as f:
                json.dump(report_data, f, indent=2)

        return report_data

    @classmethod
    def generate_class_distribution_report(
        cls,
        images_dir: Path,
        annotations_dir: Path,
        splits_dir: Optional[Path] = None,
        output_report_path: Optional[Path] = None,
    ) -> Dict[str, Any]:
        """Generates class balance and sample count report per Section 13."""
        images_dir = Path(images_dir)
        annotations_dir = Path(annotations_dir)

        image_files = (
            [p for p in images_dir.iterdir() if p.is_file() and p.suffix.lower() in cls.VALID_IMAGE_EXTENSIONS]
            if images_dir.exists()
            else []
        )
        annot_files = (
            [p for p in annotations_dir.iterdir() if p.is_file() and p.suffix.lower() == ".txt"]
            if annotations_dir.exists()
            else []
        )

        total_images = len(image_files)
        total_annotations = 0
        instances_per_class: Dict[int, int] = {cid: 0 for cid in FIRE_INSPECTION_CLASSES}
        images_per_class: Dict[int, int] = {cid: 0 for cid in FIRE_INSPECTION_CLASSES}

        for annot_p in annot_files:
            cids_in_img = set()
            try:
                with open(annot_p, "r", encoding="utf-8") as f:
                    for line in f:
                        toks = line.strip().split()
                        if len(toks) >= 5:
                            try:
                                cid = int(toks[0])
                                if cid in FIRE_INSPECTION_CLASSES:
                                    instances_per_class[cid] += 1
                                    cids_in_img.add(cid)
                                    total_annotations += 1
                            except ValueError:
                                pass
            except Exception:
                pass

            for cid in cids_in_img:
                images_per_class[cid] += 1

        # Per split breakdown
        train_instances: Dict[int, int] = {cid: 0 for cid in FIRE_INSPECTION_CLASSES}
        val_instances: Dict[int, int] = {cid: 0 for cid in FIRE_INSPECTION_CLASSES}
        test_instances: Dict[int, int] = {cid: 0 for cid in FIRE_INSPECTION_CLASSES}

        if splits_dir and Path(splits_dir).exists():
            for sname, sdict in [("train", train_instances), ("val", val_instances), ("test", test_instances)]:
                sfile = Path(splits_dir) / f"{sname}.txt"
                if sfile.exists():
                    with open(sfile, "r", encoding="utf-8") as sf:
                        for stem in sf:
                            stem = stem.strip()
                            if not stem:
                                continue
                            af = annotations_dir / f"{stem}.txt"
                            if af.exists():
                                with open(af, "r", encoding="utf-8") as f:
                                    for line in f:
                                        toks = line.strip().split()
                                        if len(toks) >= 5:
                                            try:
                                                cid = int(toks[0])
                                                if cid in sdict:
                                                    sdict[cid] += 1
                                            except ValueError:
                                                pass

        class_details: Dict[str, Any] = {}
        rare_classes: List[str] = []

        for cid, meta in FIRE_INSPECTION_CLASSES.items():
            cnt = instances_per_class[cid]
            if cid == 2:  # FIRE_EXTINGUISHER_EXPIRED
                status = "NOT_RELIABLY_VISUALLY_DETECTABLE"
            elif cnt < 50:
                status = "INSUFFICIENT_DATA"
                rare_classes.append(meta.name)
            else:
                status = "READY"

            class_details[meta.name] = {
                "class_id": cid,
                "name": meta.name,
                "instances": cnt,
                "images": images_per_class[cid],
                "train_instances": train_instances[cid],
                "val_instances": val_instances[cid],
                "test_instances": test_instances[cid],
                "status": status,
            }

        unsupported_classes = [
            "fire_hydrant_outdoor",
            "electrical_panel_closed_compliant",
            "sprinkler_normal_compliant",
            "clear_exit_compliant",
        ]

        report_data = {
            "total_images": total_images,
            "total_annotations": total_annotations,
            "instances_per_class": {str(cid): cnt for cid, cnt in instances_per_class.items()},
            "images_per_class": {str(cid): cnt for cid, cnt in images_per_class.items()},
            "train_instances": {str(cid): cnt for cid, cnt in train_instances.items()},
            "validation_instances": {str(cid): cnt for cid, cnt in val_instances.items()},
            "test_instances": {str(cid): cnt for cid, cnt in test_instances.items()},
            "classes": class_details,
            "rare_classes": rare_classes,
            "unsupported_classes": unsupported_classes,
        }

        if output_report_path:
            out_p = Path(output_report_path)
            out_p.parent.mkdir(parents=True, exist_ok=True)
            import json
            with open(out_p, "w", encoding="utf-8") as f:
                json.dump(report_data, f, indent=2)

        return report_data

