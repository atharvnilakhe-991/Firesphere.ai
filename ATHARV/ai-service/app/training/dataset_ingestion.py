"""Dataset ingestion and multi-format class mapping engine for FireSphere fire-safety datasets."""

import hashlib
import json
from pathlib import Path
import shutil
from typing import Any, Dict, List, Optional, Set, Tuple
import xml.etree.ElementTree as ET
import cv2
from pydantic import BaseModel, Field
from app.core.logging import logger
from app.models.model_metadata import FIRE_INSPECTION_CLASSES


class ClassMappingRule(BaseModel):
    """Specification of how a source class maps to a FireSphere target class."""

    sourceClass: str
    targetClass: Optional[str] = None
    mappingType: str = Field(
        description="'DIRECT', 'MERGED', 'PARTIAL', 'UNSUPPORTED', or 'REJECTED'"
    )
    rationale: str = ""


class IngestionItemResult(BaseModel):
    """Result of ingesting an individual image and its associated annotations."""

    sourceFile: str
    targetImageFile: Optional[str] = None
    targetAnnotationFile: Optional[str] = None
    sha256: str
    status: str = Field(description="'SUCCESS', 'SKIPPED', 'REJECTED', 'ERROR'")
    sourceAnnotationsCount: int = 0
    mappedAnnotationsCount: int = 0
    unsupportedAnnotationsCount: int = 0
    issues: List[str] = Field(default_factory=list)


class DatasetIngestionReport(BaseModel):
    """Consolidated summary report of dataset ingestion."""

    sourceDatasetId: str
    sourceDatasetName: str
    annotationFormat: str = "YOLO"
    totalSourceImages: int = 0
    ingestedImages: int = 0
    skippedImages: int = 0
    rejectedImages: int = 0
    totalSourceAnnotations: int = 0
    totalMappedAnnotations: int = 0
    totalUnsupportedAnnotations: int = 0
    classInstanceCounts: Dict[int, int] = Field(default_factory=dict)
    itemResults: List[IngestionItemResult] = Field(default_factory=list)
    errors: List[str] = Field(default_factory=list)


class DatasetIngestionEngine:
    """Imports external candidate fire-safety datasets and maps classes deterministically across YOLO, COCO, and Pascal VOC."""

    SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}

    def __init__(
        self,
        class_mapping_config_path: Optional[Path] = None,
        source_registry_path: Optional[Path] = None,
    ):
        self.class_mappings: Dict[str, Dict[str, ClassMappingRule]] = {}
        self.source_registry: Dict[str, Dict[str, Any]] = {}

        if class_mapping_config_path and Path(class_mapping_config_path).exists():
            self._load_class_mappings(Path(class_mapping_config_path))

        if source_registry_path and Path(source_registry_path).exists():
            self._load_source_registry(Path(source_registry_path))

    def _load_class_mappings(self, path: Path) -> None:
        """Parses mapping specifications from dataset_class_mapping.json."""
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            for sm in data.get("sourceDatasetMappings", []):
                ds_id = sm.get("sourceDatasetId")
                if ds_id:
                    self.class_mappings[ds_id] = {}
                    for m in sm.get("mappings", []):
                        rule = ClassMappingRule(
                            sourceClass=m["sourceClass"],
                            targetClass=m.get("targetClass"),
                            mappingType=m.get("mappingType", "UNSUPPORTED"),
                            rationale=m.get("rationale", ""),
                        )
                        self.class_mappings[ds_id][m["sourceClass"]] = rule
        except Exception as e:
            logger.error("Failed to load class mapping config", error=str(e))

    def _load_source_registry(self, path: Path) -> None:
        """Parses verified candidate datasets from dataset_sources.json."""
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            for src in data.get("sources", []):
                ds_id = src.get("datasetId") or src.get("dataset_name")
                if ds_id:
                    self.source_registry[ds_id] = src
        except Exception as e:
            logger.error("Failed to load source registry config", error=str(e))

    @staticmethod
    def compute_sha256(path: Path) -> str:
        """Computes SHA-256 hex digest of file."""
        hasher = hashlib.sha256()
        with open(path, "rb") as f:
            while chunk := f.read(65536):
                hasher.update(chunk)
        return hasher.hexdigest()

    def map_class_name_to_target_id(
        self, source_dataset_id: str, source_class_name: str
    ) -> Tuple[Optional[int], str]:
        """Resolves source class string to FireSphere integer class ID (0..9) and status.

        Returns:
            Tuple of (class_id_or_none, mapping_type)
        """
        ds_map = self.class_mappings.get(source_dataset_id, {})
        rule = ds_map.get(source_class_name)

        if not rule or rule.mappingType in ("UNSUPPORTED", "REJECTED") or not rule.targetClass:
            return None, (rule.mappingType if rule else "UNSUPPORTED")

        # Reverse lookup target class name to class id
        for cid, meta in FIRE_INSPECTION_CLASSES.items():
            if meta.name == rule.targetClass:
                return cid, rule.mappingType

        return None, "UNSUPPORTED"

    # =========================================================================
    # 1. YOLO INGESTION
    # =========================================================================
    def ingest_yolo_dataset(
        self,
        source_dataset_id: str,
        source_images_dir: Path,
        source_annotations_dir: Path,
        target_images_dir: Path,
        target_annotations_dir: Path,
        source_class_names: List[str],
        allow_empty_annotations: bool = False,
    ) -> DatasetIngestionReport:
        """Ingests a YOLO formatted dataset, remapping class indices according to governance mapping."""
        source_images_dir = Path(source_images_dir)
        source_annotations_dir = Path(source_annotations_dir)
        target_images_dir = Path(target_images_dir)
        target_annotations_dir = Path(target_annotations_dir)

        target_images_dir.mkdir(parents=True, exist_ok=True)
        target_annotations_dir.mkdir(parents=True, exist_ok=True)

        ds_info = self.source_registry.get(source_dataset_id, {})
        report = DatasetIngestionReport(
            sourceDatasetId=source_dataset_id,
            sourceDatasetName=ds_info.get("datasetName", ds_info.get("dataset_name", source_dataset_id)),
            annotationFormat="YOLO_NORMALIZED",
        )

        if not source_images_dir.exists():
            report.errors.append(f"Source images directory does not exist: {source_images_dir}")
            return report

        image_files = [
            p for p in source_images_dir.iterdir()
            if p.is_file() and p.suffix.lower() in self.SUPPORTED_EXTENSIONS
        ]
        report.totalSourceImages = len(image_files)

        for img_path in sorted(image_files):
            stem = img_path.stem
            sha256 = self.compute_sha256(img_path)
            annot_path = source_annotations_dir / f"{stem}.txt"

            item_res = IngestionItemResult(
                sourceFile=img_path.name,
                sha256=sha256,
                status="SUCCESS",
            )

            if not annot_path.exists():
                if not allow_empty_annotations:
                    item_res.status = "SKIPPED"
                    item_res.issues.append(f"Missing annotation file {annot_path.name}")
                    report.skippedImages += 1
                    report.itemResults.append(item_res)
                    continue
                source_lines: List[str] = []
            else:
                try:
                    with open(annot_path, "r", encoding="utf-8") as f:
                        source_lines = [l.strip() for l in f if l.strip()]
                except Exception as e:
                    item_res.status = "ERROR"
                    item_res.issues.append(f"Error reading annotation {annot_path.name}: {e}")
                    report.rejectedImages += 1
                    report.itemResults.append(item_res)
                    continue

            item_res.sourceAnnotationsCount = len(source_lines)
            report.totalSourceAnnotations += len(source_lines)

            valid_target_lines: List[str] = []

            for line_idx, line in enumerate(source_lines, 1):
                tokens = line.split()
                if len(tokens) != 5:
                    item_res.issues.append(f"Malformed YOLO row at line {line_idx}")
                    continue

                try:
                    src_cls_idx = int(tokens[0])
                    xc, yc, w, h = float(tokens[1]), float(tokens[2]), float(tokens[3]), float(tokens[4])
                except ValueError:
                    item_res.issues.append(f"Non-numeric tokens at line {line_idx}")
                    continue

                if src_cls_idx < 0 or src_cls_idx >= len(source_class_names):
                    item_res.issues.append(f"Source class index out of range at line {line_idx}")
                    continue

                # Coordinate bounds verification
                if xc < 0.0 or xc > 1.0 or yc < 0.0 or yc > 1.0 or w <= 0.0 or w > 1.0 or h <= 0.0 or h > 1.0:
                    item_res.issues.append(f"Out-of-bounds bounding box at line {line_idx}")
                    continue

                src_cls_name = source_class_names[src_cls_idx]
                target_cid, mapping_type = self.map_class_name_to_target_id(source_dataset_id, src_cls_name)

                if target_cid is None:
                    item_res.unsupportedAnnotationsCount += 1
                    report.totalUnsupportedAnnotations += 1
                    continue

                valid_target_lines.append(f"{target_cid} {xc:.6f} {yc:.6f} {w:.6f} {h:.6f}")
                item_res.mappedAnnotationsCount += 1
                report.totalMappedAnnotations += 1
                report.classInstanceCounts[target_cid] = report.classInstanceCounts.get(target_cid, 0) + 1

            if not valid_target_lines and not allow_empty_annotations:
                item_res.status = "SKIPPED"
                item_res.issues.append("No valid mapped FireSphere annotations found in image")
                report.skippedImages += 1
                report.itemResults.append(item_res)
                continue

            dest_img_path = target_images_dir / img_path.name
            dest_annot_path = target_annotations_dir / f"{stem}.txt"

            try:
                shutil.copy2(img_path, dest_img_path)
                with open(dest_annot_path, "w", encoding="utf-8") as f:
                    for tl in valid_target_lines:
                        f.write(f"{tl}\n")

                item_res.targetImageFile = dest_img_path.name
                item_res.targetAnnotationFile = dest_annot_path.name
                report.ingestedImages += 1
            except Exception as copy_err:
                item_res.status = "ERROR"
                item_res.issues.append(f"Failed to copy/write target files: {copy_err}")
                report.rejectedImages += 1

            report.itemResults.append(item_res)

        return report

    # =========================================================================
    # 2. COCO JSON INGESTION
    # =========================================================================
    def ingest_coco_dataset(
        self,
        source_dataset_id: str,
        source_images_dir: Path,
        coco_annotations_file: Path,
        target_images_dir: Path,
        target_annotations_dir: Path,
        allow_empty_annotations: bool = False,
    ) -> DatasetIngestionReport:
        """Ingests a COCO JSON dataset, converting absolute pixel bounding boxes [x, y, w, h] to normalized YOLO."""
        source_images_dir = Path(source_images_dir)
        coco_annotations_file = Path(coco_annotations_file)
        target_images_dir = Path(target_images_dir)
        target_annotations_dir = Path(target_annotations_dir)

        target_images_dir.mkdir(parents=True, exist_ok=True)
        target_annotations_dir.mkdir(parents=True, exist_ok=True)

        ds_info = self.source_registry.get(source_dataset_id, {})
        report = DatasetIngestionReport(
            sourceDatasetId=source_dataset_id,
            sourceDatasetName=ds_info.get("datasetName", ds_info.get("dataset_name", source_dataset_id)),
            annotationFormat="COCO_JSON",
        )

        if not coco_annotations_file.exists():
            report.errors.append(f"COCO annotations file does not exist: {coco_annotations_file}")
            return report

        try:
            with open(coco_annotations_file, "r", encoding="utf-8") as f:
                coco_data = json.load(f)
        except Exception as e:
            report.errors.append(f"Failed to parse COCO JSON: {e}")
            return report

        # Index categories: id -> name
        categories = {c["id"]: c["name"] for c in coco_data.get("categories", [])}

        # Index annotations by image_id
        img_id_to_annots: Dict[int, List[Dict[str, Any]]] = {}
        for ann in coco_data.get("annotations", []):
            img_id_to_annots.setdefault(ann["image_id"], []).append(ann)
            report.totalSourceAnnotations += 1

        images = coco_data.get("images", [])
        report.totalSourceImages = len(images)

        for img_entry in sorted(images, key=lambda x: str(x.get("id"))):
            img_id = img_entry["id"]
            file_name = img_entry.get("file_name", "")
            img_path = source_images_dir / file_name
            if not img_path.exists():
                # Try finding basename
                candidate = source_images_dir / Path(file_name).name
                if candidate.exists():
                    img_path = candidate
                else:
                    report.skippedImages += 1
                    report.errors.append(f"Image not found on disk: {file_name}")
                    continue

            sha256 = self.compute_sha256(img_path)
            item_res = IngestionItemResult(
                sourceFile=img_path.name,
                sha256=sha256,
                status="SUCCESS",
            )

            # Image dimensions
            width = img_entry.get("width")
            height = img_entry.get("height")
            if not width or not height or width <= 0 or height <= 0:
                try:
                    mat = cv2.imread(str(img_path))
                    if mat is not None:
                        height, width = mat.shape[:2]
                except Exception:
                    pass

            if not width or not height or width <= 0 or height <= 0:
                item_res.status = "ERROR"
                item_res.issues.append("Unresolvable image dimensions")
                report.rejectedImages += 1
                report.itemResults.append(item_res)
                continue

            source_annots = img_id_to_annots.get(img_id, [])
            item_res.sourceAnnotationsCount = len(source_annots)
            valid_target_lines: List[str] = []

            for ann in source_annots:
                bbox = ann.get("bbox", [])
                if len(bbox) != 4:
                    item_res.issues.append(f"Malformed COCO bbox: {bbox}")
                    continue

                bx, by, bw, bh = bbox
                if bw <= 0 or bh <= 0:
                    item_res.issues.append(f"Non-positive box dimension: {bw}x{bh}")
                    continue

                cat_id = ann.get("category_id")
                cat_name = categories.get(cat_id, "")
                target_cid, mapping_type = self.map_class_name_to_target_id(source_dataset_id, cat_name)

                if target_cid is None:
                    item_res.unsupportedAnnotationsCount += 1
                    report.totalUnsupportedAnnotations += 1
                    continue

                # Convert COCO [x, y, w, h] to normalized YOLO [xc, yc, w, h]
                xc = (bx + bw / 2.0) / float(width)
                yc = (by + bh / 2.0) / float(height)
                nw = float(bw) / float(width)
                nh = float(bh) / float(height)

                if xc < 0.0 or xc > 1.0 or yc < 0.0 or yc > 1.0 or nw <= 0.0 or nw > 1.0 or nh <= 0.0 or nh > 1.0:
                    item_res.issues.append(f"Normalized coordinate out of bounds for box {bbox}")
                    continue

                valid_target_lines.append(f"{target_cid} {xc:.6f} {yc:.6f} {nw:.6f} {nh:.6f}")
                item_res.mappedAnnotationsCount += 1
                report.totalMappedAnnotations += 1
                report.classInstanceCounts[target_cid] = report.classInstanceCounts.get(target_cid, 0) + 1

            if not valid_target_lines and not allow_empty_annotations:
                item_res.status = "SKIPPED"
                item_res.issues.append("No valid mapped FireSphere annotations in image")
                report.skippedImages += 1
                report.itemResults.append(item_res)
                continue

            stem = img_path.stem
            dest_img_path = target_images_dir / img_path.name
            dest_annot_path = target_annotations_dir / f"{stem}.txt"

            try:
                shutil.copy2(img_path, dest_img_path)
                with open(dest_annot_path, "w", encoding="utf-8") as f:
                    for tl in valid_target_lines:
                        f.write(f"{tl}\n")

                item_res.targetImageFile = dest_img_path.name
                item_res.targetAnnotationFile = dest_annot_path.name
                report.ingestedImages += 1
            except Exception as copy_err:
                item_res.status = "ERROR"
                item_res.issues.append(f"Failed copying image/saving annot: {copy_err}")
                report.rejectedImages += 1

            report.itemResults.append(item_res)

        return report

    # =========================================================================
    # 3. PASCAL VOC XML INGESTION
    # =========================================================================
    def ingest_voc_dataset(
        self,
        source_dataset_id: str,
        source_images_dir: Path,
        source_annotations_dir: Path,
        target_images_dir: Path,
        target_annotations_dir: Path,
        allow_empty_annotations: bool = False,
    ) -> DatasetIngestionReport:
        """Ingests a Pascal VOC XML dataset, converting [xmin, ymin, xmax, ymax] to normalized YOLO."""
        source_images_dir = Path(source_images_dir)
        source_annotations_dir = Path(source_annotations_dir)
        target_images_dir = Path(target_images_dir)
        target_annotations_dir = Path(target_annotations_dir)

        target_images_dir.mkdir(parents=True, exist_ok=True)
        target_annotations_dir.mkdir(parents=True, exist_ok=True)

        ds_info = self.source_registry.get(source_dataset_id, {})
        report = DatasetIngestionReport(
            sourceDatasetId=source_dataset_id,
            sourceDatasetName=ds_info.get("datasetName", ds_info.get("dataset_name", source_dataset_id)),
            annotationFormat="PASCAL_VOC_XML",
        )

        xml_files = sorted([
            p for p in source_annotations_dir.iterdir()
            if p.is_file() and p.suffix.lower() == ".xml"
        ])
        report.totalSourceImages = len(xml_files)

        for xml_path in xml_files:
            stem = xml_path.stem
            # Find corresponding image
            img_path = None
            for ext in self.SUPPORTED_EXTENSIONS:
                cand = source_images_dir / f"{stem}{ext}"
                if cand.exists():
                    img_path = cand
                    break

            if not img_path:
                report.skippedImages += 1
                report.errors.append(f"Image for VOC xml '{xml_path.name}' not found")
                continue

            sha256 = self.compute_sha256(img_path)
            item_res = IngestionItemResult(
                sourceFile=img_path.name,
                sha256=sha256,
                status="SUCCESS",
            )

            try:
                tree = ET.parse(xml_path)
                root = tree.getroot()
            except Exception as e:
                item_res.status = "ERROR"
                item_res.issues.append(f"Malformed XML in {xml_path.name}: {e}")
                report.rejectedImages += 1
                report.itemResults.append(item_res)
                continue

            # Read dimensions
            size_elem = root.find("size")
            width = 0
            height = 0
            if size_elem is not None:
                try:
                    width = int(size_elem.find("width").text)
                    height = int(size_elem.find("height").text)
                except Exception:
                    pass

            if width <= 0 or height <= 0:
                try:
                    mat = cv2.imread(str(img_path))
                    if mat is not None:
                        height, width = mat.shape[:2]
                except Exception:
                    pass

            if width <= 0 or height <= 0:
                item_res.status = "ERROR"
                item_res.issues.append("Unable to determine image resolution")
                report.rejectedImages += 1
                report.itemResults.append(item_res)
                continue

            objects = root.findall("object")
            item_res.sourceAnnotationsCount = len(objects)
            report.totalSourceAnnotations += len(objects)

            valid_target_lines: List[str] = []

            for obj in objects:
                name_elem = obj.find("name")
                if name_elem is None or not name_elem.text:
                    item_res.issues.append("Object missing class name")
                    continue
                source_class_name = name_elem.text.strip()

                bndbox = obj.find("bndbox")
                if bndbox is None:
                    item_res.issues.append("Object missing bndbox element")
                    continue

                try:
                    xmin = float(bndbox.find("xmin").text)
                    ymin = float(bndbox.find("ymin").text)
                    xmax = float(bndbox.find("xmax").text)
                    ymax = float(bndbox.find("ymax").text)
                except Exception as coord_err:
                    item_res.issues.append(f"Non-numeric bndbox values: {coord_err}")
                    continue

                if xmax <= xmin or ymax <= ymin:
                    item_res.issues.append(f"Zero or inverted box dimensions: [{xmin}, {ymin}, {xmax}, {ymax}]")
                    continue

                target_cid, mapping_type = self.map_class_name_to_target_id(source_dataset_id, source_class_name)

                if target_cid is None:
                    item_res.unsupportedAnnotationsCount += 1
                    report.totalUnsupportedAnnotations += 1
                    continue

                bw = xmax - xmin
                bh = ymax - ymin
                xc = (xmin + bw / 2.0) / float(width)
                yc = (ymin + bh / 2.0) / float(height)
                nw = bw / float(width)
                nh = bh / float(height)

                if xc < 0.0 or xc > 1.0 or yc < 0.0 or yc > 1.0 or nw <= 0.0 or nw > 1.0 or nh <= 0.0 or nh > 1.0:
                    item_res.issues.append(f"Normalized coordinate out of bounds: xc={xc}, yc={yc}, w={nw}, h={nh}")
                    continue

                valid_target_lines.append(f"{target_cid} {xc:.6f} {yc:.6f} {nw:.6f} {nh:.6f}")
                item_res.mappedAnnotationsCount += 1
                report.totalMappedAnnotations += 1
                report.classInstanceCounts[target_cid] = report.classInstanceCounts.get(target_cid, 0) + 1

            if not valid_target_lines and not allow_empty_annotations:
                item_res.status = "SKIPPED"
                item_res.issues.append("No valid mapped FireSphere annotations found in image")
                report.skippedImages += 1
                report.itemResults.append(item_res)
                continue

            dest_img_path = target_images_dir / img_path.name
            dest_annot_path = target_annotations_dir / f"{stem}.txt"

            try:
                shutil.copy2(img_path, dest_img_path)
                with open(dest_annot_path, "w", encoding="utf-8") as f:
                    for tl in valid_target_lines:
                        f.write(f"{tl}\n")

                item_res.targetImageFile = dest_img_path.name
                item_res.targetAnnotationFile = dest_annot_path.name
                report.ingestedImages += 1
            except Exception as copy_err:
                item_res.status = "ERROR"
                item_res.issues.append(f"Failed to copy image/write annotation: {copy_err}")
                report.rejectedImages += 1

            report.itemResults.append(item_res)

        return report
