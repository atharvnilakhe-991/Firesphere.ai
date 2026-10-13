"""Deterministic dataset readiness gate for Phase 8 -> Phase 9 transition."""

from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field
from app.core.logging import logger
from app.models.model_metadata import FIRE_INSPECTION_CLASSES
from app.training.dataset_validator import DatasetValidator


class GateCheckItem(BaseModel):
    """Result of an individual readiness verification check."""

    checkId: str
    description: str
    passed: bool
    details: str


class DatasetReadinessEvaluation(BaseModel):
    """Complete readiness gate evaluation for Phase 9 training."""

    evaluatedAt: str = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    status: str = Field(
        description="Exact readiness state: 'DATASET_NOT_AVAILABLE', 'DATASET_ACQUISITION_IN_PROGRESS', 'DATASET_PARTIALLY_READY', 'DATASET_VALIDATION_FAILED', 'DATASET_READY_FOR_PHASE_9'"
    )
    isReadyForPhase9: bool = False
    totalImages: int = 0
    totalAnnotations: int = 0
    coveredClassesCount: int = 0
    missingClassesCount: int = 10
    checks: List[GateCheckItem] = Field(default_factory=list)
    blockers: List[str] = Field(default_factory=list)
    recommendations: List[str] = Field(default_factory=list)


class DatasetReadinessGate:
    """Evaluates whether the dataset meets strict criteria for Phase 9 model training."""

    STATUS_NOT_AVAILABLE = "DATASET_NOT_AVAILABLE"
    STATUS_ACQUISITION_IN_PROGRESS = "DATASET_ACQUISITION_IN_PROGRESS"
    STATUS_PARTIALLY_READY = "DATASET_PARTIALLY_READY"
    STATUS_VALIDATION_FAILED = "DATASET_VALIDATION_FAILED"
    STATUS_BLOCKED = "DATASET_BLOCKED"
    STATUS_READY_FOR_PHASE_9 = "READY_FOR_PHASE_9"
    READY_FOR_PHASE_9 = "READY_FOR_PHASE_9"

    def __init__(
        self,
        dataset_root: Path,
        min_samples_per_class: int = 50,
        min_total_images: int = 200,
    ):
        self.dataset_root = Path(dataset_root)
        self.min_samples_per_class = min_samples_per_class
        self.min_total_images = min_total_images

    def evaluate_readiness(self) -> DatasetReadinessEvaluation:
        """Executes full audit against dataset sources, class feasibility, hygiene, and coverage."""
        checks: List[GateCheckItem] = []
        blockers: List[str] = []
        recommendations: List[str] = []

        sources_file = self.dataset_root / "dataset_sources.json"
        class_mapping_file = self.dataset_root / "dataset_class_mapping.json"
        images_dir = self.dataset_root / "images"
        annotations_dir = self.dataset_root / "annotations"
        splits_dir = self.dataset_root / "splits"

        # Check 1: Candidate Dataset Source Registry
        sources_exist = sources_file.exists()
        sources_data: Dict[str, Any] = {}
        has_verified_sources = False
        if sources_exist:
            try:
                with open(sources_file, "r", encoding="utf-8") as f:
                    sources_data = json.load(f)
                num_sources = len(sources_data.get("sources", []))
                verified_sources = [
                    s for s in sources_data.get("sources", [])
                    if s.get("licenseStatus") == "VERIFIED"
                ]
                has_verified_sources = len(verified_sources) > 0
                checks.append(GateCheckItem(
                    checkId="G1_SOURCE_REGISTRY",
                    description="Candidate dataset source registry exists and contains verified sources",
                    passed=has_verified_sources,
                    details=f"Found {num_sources} candidate sources ({len(verified_sources)} with verified licenses)",
                ))
            except Exception as e:
                checks.append(GateCheckItem(
                    checkId="G1_SOURCE_REGISTRY",
                    description="Candidate dataset source registry exists and contains verified sources",
                    passed=False,
                    details=f"Error reading sources registry: {e}",
                ))
        else:
            checks.append(GateCheckItem(
                checkId="G1_SOURCE_REGISTRY",
                description="Candidate dataset source registry exists and contains verified sources",
                passed=False,
                details="dataset_sources.json is missing",
            ))

        # Check 2: Target Class Feasibility Specification
        mapping_exist = class_mapping_file.exists()
        has_all_10_feasibility = False
        if mapping_exist:
            try:
                with open(class_mapping_file, "r", encoding="utf-8") as f:
                    mapping_data = json.load(f)
                feasibility_list = mapping_data.get("targetClassFeasibility", [])
                has_all_10_feasibility = len(feasibility_list) == len(FIRE_INSPECTION_CLASSES)
                checks.append(GateCheckItem(
                    checkId="G2_CLASS_FEASIBILITY",
                    description="All 10 target classes analyzed for visual detectability & feasibility",
                    passed=has_all_10_feasibility,
                    details=f"Analyzed {len(feasibility_list)}/10 classes",
                ))
            except Exception as e:
                checks.append(GateCheckItem(
                    checkId="G2_CLASS_FEASIBILITY",
                    description="All 10 target classes analyzed for visual detectability & feasibility",
                    passed=False,
                    details=f"Error reading class mapping: {e}",
                ))
        else:
            checks.append(GateCheckItem(
                checkId="G2_CLASS_FEASIBILITY",
                description="All 10 target classes analyzed for visual detectability & feasibility",
                passed=False,
                details="dataset_class_mapping.json is missing",
            ))

        # Check 3: Image and Annotation Data Presence
        image_files = []
        annot_files = []
        if images_dir.exists():
            image_files = [
                p for p in images_dir.iterdir()
                if p.is_file() and p.suffix.lower() in DatasetValidator.VALID_IMAGE_EXTENSIONS
            ]
        if annotations_dir.exists():
            annot_files = [
                p for p in annotations_dir.iterdir()
                if p.is_file() and p.suffix.lower() == ".txt"
            ]

        has_data = len(image_files) > 0 and len(annot_files) > 0
        checks.append(GateCheckItem(
            checkId="G3_DATA_PRESENCE",
            description="Verified images and annotations populated in dataset folders",
            passed=has_data,
            details=f"Found {len(image_files)} images and {len(annot_files)} annotations",
        ))

        # Check 4: Data Hygiene & Validation
        val_passed = False
        class_distribution: Dict[int, int] = {}
        if has_data:
            val_report = DatasetValidator.validate_dataset(
                images_dir=images_dir,
                annotations_dir=annotations_dir,
                splits_dir=splits_dir if splits_dir.exists() else None,
            )
            val_passed = (
                val_report.status == "VALID"
                and val_report.invalidImages == 0
                and val_report.invalidAnnotations == 0
                and len(val_report.duplicateImages) == 0
                and len(val_report.leakageDetections) == 0
            )
            class_distribution = val_report.classDistribution
            checks.append(GateCheckItem(
                checkId="G4_HYGIENE_VALIDATION",
                description="Zero corrupt images, zero invalid bounding boxes, and zero split leakages",
                passed=val_passed,
                details=f"Validation report status: {val_report.status} (invalid images: {val_report.invalidImages}, invalid annots: {val_report.invalidAnnotations}, duplicates: {len(val_report.duplicateImages)}, leakages: {len(val_report.leakageDetections)})",
            ))
            if not val_passed:
                blockers.append("Dataset validation failed: contains corrupt files, invalid boxes, or leakage.")
        else:
            checks.append(GateCheckItem(
                checkId="G4_HYGIENE_VALIDATION",
                description="Zero corrupt images, zero invalid bounding boxes, and zero split leakages",
                passed=False,
                details="Cannot validate hygiene: no images or annotations present",
            ))

        # Check 5: Class Coverage & Representation
        covered_classes = [cid for cid, count in class_distribution.items() if count > 0]
        missing_classes = [cid for cid in FIRE_INSPECTION_CLASSES if cid not in class_distribution or class_distribution[cid] == 0]
        well_represented_classes = [cid for cid, count in class_distribution.items() if count >= self.min_samples_per_class]

        coverage_adequate = len(covered_classes) >= 5 and len(image_files) >= self.min_total_images
        checks.append(GateCheckItem(
            checkId="G5_CLASS_COVERAGE",
            description="Adequate sample counts and coverage across detectable target classes",
            passed=coverage_adequate,
            details=f"{len(covered_classes)}/10 classes present ({len(well_represented_classes)} meet min threshold {self.min_samples_per_class})",
        ))

        # Check 6: Dataset Metadata & Manifests
        meta_file = self.dataset_root / "dataset_meta.json"
        manifests_dir = self.dataset_root / "manifests"
        has_meta_and_manifests = meta_file.exists() and manifests_dir.exists()
        checks.append(GateCheckItem(
            checkId="G6_METADATA_AND_MANIFESTS",
            description="Dataset metadata and split manifests generated and available",
            passed=has_meta_and_manifests,
            details=f"Metadata exists: {meta_file.exists()}, Manifests dir exists: {manifests_dir.exists()}",
        ))

        # Check 7: Quality and Feasibility Reports
        validation_dir = self.dataset_root / "validation"
        has_validation_reports = validation_dir.exists() and (validation_dir / "class_distribution.json").exists()
        checks.append(GateCheckItem(
            checkId="G7_QUALITY_REPORT",
            description="Quality audit and class distribution reports compiled",
            passed=has_validation_reports,
            details=f"Validation reports directory present: {has_validation_reports}",
        ))

        # Determine Final Status
        if not sources_exist and not has_data:
            final_status = self.STATUS_NOT_AVAILABLE
            blockers.append("No dataset sources registered and no image data available.")
            recommendations.append("Populate dataset_sources.json and initiate data acquisition.")
        elif not has_data:
            final_status = self.STATUS_ACQUISITION_IN_PROGRESS
            blockers.append("Candidate sources and class mapping are defined, but raw image collection and annotation are actively underway.")
            recommendations.append("Execute dataset ingestion from candidate sources or complete municipal field pilot annotation.")
        elif not val_passed:
            final_status = self.STATUS_VALIDATION_FAILED
            blockers.append("Existing dataset contains hygiene or formatting defects.")
            recommendations.append("Run DatasetCleaner to resolve corrupted images, invalid bounding boxes, or leakage.")
        elif not coverage_adequate:
            final_status = self.STATUS_PARTIALLY_READY
            blockers.append(f"Insufficient class coverage or image volume: {len(image_files)}/{self.min_total_images} images, {len(covered_classes)}/10 classes represented.")
            recommendations.append(f"Collect additional annotations for missing classes: {[FIRE_INSPECTION_CLASSES[cid].name for cid in missing_classes[:5]]}.")
        else:
            final_status = self.STATUS_READY_FOR_PHASE_9

        return DatasetReadinessEvaluation(
            status=final_status,
            isReadyForPhase9=(final_status == self.STATUS_READY_FOR_PHASE_9 and len(image_files) > 0),
            totalImages=len(image_files),
            totalAnnotations=sum(class_distribution.values()) if class_distribution else 0,
            coveredClassesCount=len(covered_classes),
            missingClassesCount=len(missing_classes),
            checks=checks,
            blockers=blockers,
            recommendations=recommendations,
        )
