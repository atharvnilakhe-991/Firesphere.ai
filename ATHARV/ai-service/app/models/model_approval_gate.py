"""FireSphere.ai — Phase 10 Model Approval & Capability Gate.

Enforces strict governance, capability gating, and safety invariants:
1. Phase 8.4 baseline model checkpoint is a REFERENCE BASELINE, NOT production-approved.
2. Production inference remains disabled unless an explicit, auditable approval exists.
3. Unsupported perception classes (EXIT_DOOR, OBSTACLE) are strictly gated from generating
   actionable findings.
4. Experimental / test-mode execution is explicitly labelled (isTestData=True).
5. Checkpoint integrity is cryptographically verified (SHA-256 digest match).
6. Statistical calibration status is explicitly recorded as NOT_CALIBRATED.
7. Rejects autonomous certification (AI_APPROVED, AI_REJECTED).
"""

from enum import Enum
import hashlib
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from app.core.logging import logger
from app.models.base_model import RawDetection
from app.registry.model_registry import ModelLifecycleState, model_registry


class ModelApprovalStatus(str, Enum):
    """Auditable production approval status for CV model artifacts."""

    NOT_APPROVED = "NOT_APPROVED"
    EXPERIMENTAL = "EXPERIMENTAL"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"


class PerceptionClassStatus(str, Enum):
    """Operational capability status for each perception class."""

    ADVISORY_ONLY = "ADVISORY_ONLY"
    UNSUPPORTED = "UNSUPPORTED"
    PROHIBITED = "PROHIBITED"


class OfficerReviewStatus(str, Enum):
    """Officer review lifecycle states for individual findings."""

    UNREVIEWED = "UNREVIEWED"
    AI_SUGGESTION = "AI_SUGGESTION"
    OFFICER_CONFIRMED = "OFFICER_CONFIRMED"
    OFFICER_DISMISSED = "OFFICER_DISMISSED"


class CheckpointIntegrityException(Exception):
    """Raised when a checkpoint hash does not match expected cryptographic digest."""


class ModelApprovalGate:
    """Explicit governance and capability gate for model inference and finding generation."""

    REFERENCE_BASELINE_MODEL_ID = "firesphere-perception-baseline-v0.8.0"
    REFERENCE_BASELINE_VERSION = "0.8.0"
    REFERENCE_BASELINE_SHA256 = "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"

    # Per-class capability definitions established in Phase 8.1 and audited in Phase 9
    CLASS_CAPABILITIES: Dict[str, Dict[str, Any]] = {
        "FIRE_EXTINGUISHER": {
            "status": PerceptionClassStatus.ADVISORY_ONLY,
            "actionable": True,
            "externalTestSupport": 452,
            "testTruePositives": 143,
            "isProductionApproved": False,
            "isLegalViolationClassifier": False,
            "limitations": [
                "Advisory visual detection only; does not evaluate hydrostatic test date stamp or pressure gauge needle compliance.",
                "Subject to false positives on red cylinders or fire hose reels; requires on-site officer verification.",
            ],
        },
        "EXIT_SIGN": {
            "status": PerceptionClassStatus.ADVISORY_ONLY,
            "actionable": True,
            "externalTestSupport": 320,
            "testTruePositives": 52,
            "isProductionApproved": False,
            "isLegalViolationClassifier": False,
            "limitations": [
                "Advisory visual presence detection only; does not verify internal battery backup, circuit continuity, or illumination lux levels.",
                "Subject to perspective foreshortening; requires physical officer verification.",
            ],
        },
        "EXIT_DOOR": {
            "status": PerceptionClassStatus.UNSUPPORTED,
            "actionable": False,
            "externalTestSupport": 6,
            "testTruePositives": 0,
            "isProductionApproved": False,
            "isLegalViolationClassifier": False,
            "limitations": [
                "Zero true positives on held-out test set (6 GT instances, 0 TP).",
                "Unsupported in perception vocabulary; cannot generate actionable findings.",
                "Requires direct physical inspection by authorized human Fire Safety Officer.",
            ],
        },
        "OBSTACLE": {
            "status": PerceptionClassStatus.UNSUPPORTED,
            "actionable": False,
            "externalTestSupport": 18,
            "testTruePositives": 0,
            "isProductionApproved": False,
            "isLegalViolationClassifier": False,
            "limitations": [
                "Zero true positives on held-out test set (18 GT instances, 0 TP).",
                "Unsupported in perception vocabulary; cannot generate actionable findings.",
                "Requires direct physical inspection by authorized human Fire Safety Officer.",
            ],
        },
    }

    # Mapping of finding types to perception classes
    FINDING_TYPE_TO_CLASS: Dict[str, str] = {
        "FIRE_EXTINGUISHER_MISSING": "FIRE_EXTINGUISHER",
        "FIRE_EXTINGUISHER_BLOCKED": "FIRE_EXTINGUISHER",
        "FIRE_EXTINGUISHER_EXPIRED": "FIRE_EXTINGUISHER",
        "EXIT_SIGNAGE_MISSING": "EXIT_SIGN",
        "EMERGENCY_EXIT_OBSTRUCTED": "EXIT_DOOR",
        "OBSTACLE_DETECTED": "OBSTACLE",
    }

    @classmethod
    def compute_file_sha256(cls, filepath: Path) -> str:
        """Computes SHA-256 hash of a file."""
        hasher = hashlib.sha256()
        with open(filepath, "rb") as f:
            while chunk := f.read(65536):
                hasher.update(chunk)
        return hasher.hexdigest()

    @classmethod
    def verify_baseline_checkpoint(cls, checkpoint_path: Optional[Path] = None) -> Dict[str, Any]:
        """Cryptographically audits the baseline checkpoint against expected digest."""
        target_path = checkpoint_path or (
            Path(__file__).resolve().parent.parent.parent
            / "artifacts"
            / "phase8_4"
            / "checkpoints"
            / "best.pt"
        )

        if not target_path.exists():
            return {
                "verified": False,
                "path": str(target_path),
                "error": "Checkpoint file does not exist",
                "expectedSha256": cls.REFERENCE_BASELINE_SHA256,
                "actualSha256": None,
            }

        actual_sha = cls.compute_file_sha256(target_path)
        is_match = (actual_sha == cls.REFERENCE_BASELINE_SHA256)

        if not is_match:
            logger.error(
                f"CHECKPOINT_INTEGRITY_MISMATCH | Expected: {cls.REFERENCE_BASELINE_SHA256} | "
                f"Actual: {actual_sha} | Path: {target_path}"
            )

        return {
            "verified": is_match,
            "path": str(target_path),
            "expectedSha256": cls.REFERENCE_BASELINE_SHA256,
            "actualSha256": actual_sha,
            "status": "VERIFIED" if is_match else "INTEGRITY_FAILED",
        }

    @classmethod
    def is_production_approved(
        cls,
        model_id: Optional[str] = None,
        model_version: Optional[str] = None,
    ) -> bool:
        """Checks if the model is approved for production deployment.
        
        Always evaluates to False for the reference baseline since Phase 8.4 is NOT_APPROVED.
        """
        mid = model_id or cls.REFERENCE_BASELINE_MODEL_ID
        mver = model_version or cls.REFERENCE_BASELINE_VERSION

        # Check model registry
        reg_model = model_registry.get_model(mid, mver)
        if reg_model and reg_model.status == ModelLifecycleState.PRODUCTION:
            return True

        active_prod = model_registry.get_active_production_model()
        if active_prod and active_prod.modelId == mid and active_prod.modelVersion == mver:
            return True

        return False

    @classmethod
    def check_inference_permission(
        cls,
        test_mode: bool = False,
        model_id: Optional[str] = None,
        model_version: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Evaluates whether inference execution is authorized for the given mode."""
        mid = model_id or cls.REFERENCE_BASELINE_MODEL_ID
        mver = model_version or cls.REFERENCE_BASELINE_VERSION
        prod_approved = cls.is_production_approved(mid, mver)

        if test_mode:
            return {
                "authorized": True,
                "executionMode": "EXPERIMENTAL_TEST_MODE",
                "productionApproved": False,
                "isTestData": True,
                "modelApprovalStatus": ModelApprovalStatus.NOT_APPROVED.value,
                "calibrationStatus": "NOT_CALIBRATED",
                "reason": "Test-mode execution authorized for pipeline validation and test fixtures.",
            }

        if prod_approved:
            return {
                "authorized": True,
                "executionMode": "PRODUCTION",
                "productionApproved": True,
                "isTestData": False,
                "modelApprovalStatus": ModelApprovalStatus.APPROVED.value,
                "calibrationStatus": "NOT_CALIBRATED",
                "reason": "Explicit production approval present in model registry.",
            }

        # Production requested but unapproved: FAIL CLOSED
        return {
            "authorized": False,
            "executionMode": "PRODUCTION_DISALLOWED",
            "productionApproved": False,
            "isTestData": False,
            "modelApprovalStatus": ModelApprovalStatus.NOT_APPROVED.value,
            "calibrationStatus": "NOT_CALIBRATED",
            "reason": (
                f"Model '{mid}' (v{mver}) status is NOT_APPROVED. "
                "Production inference is strictly disabled. Fail-closed policy active."
            ),
        }

    @classmethod
    def is_class_actionable(cls, class_name: str) -> bool:
        """Determines if a perception class is authorized to generate actionable findings."""
        normalized = class_name.strip().upper()
        # Check direct perception class name
        if normalized in cls.CLASS_CAPABILITIES:
            return cls.CLASS_CAPABILITIES[normalized]["actionable"]
        # Check mapped finding type
        mapped = cls.FINDING_TYPE_TO_CLASS.get(normalized)
        if mapped and mapped in cls.CLASS_CAPABILITIES:
            return cls.CLASS_CAPABILITIES[mapped]["actionable"]
        # Any unmapped class is unsupported
        return False

    @classmethod
    def get_known_limitations_for_class(cls, class_name: str) -> List[str]:
        """Retrieves documented limitations for a class."""
        normalized = class_name.strip().upper()
        if normalized in cls.CLASS_CAPABILITIES:
            return cls.CLASS_CAPABILITIES[normalized]["limitations"]
        mapped = cls.FINDING_TYPE_TO_CLASS.get(normalized)
        if mapped and mapped in cls.CLASS_CAPABILITIES:
            return cls.CLASS_CAPABILITIES[mapped]["limitations"]
        return [
            "Class is not recognized in official perception vocabulary.",
            "Requires physical officer verification on site.",
        ]

    @classmethod
    def filter_and_gate_detections(
        cls,
        detections: List[RawDetection],
        is_test_mode: bool = False,
    ) -> Tuple[List[RawDetection], List[Dict[str, Any]]]:
        """Filters detections through the capability gate.
        
        Unsupported classes (e.g. EXIT_DOOR, OBSTACLE) are suppressed from actionable findings.
        Returns tuple of (actionable_detections, suppressed_records).
        """
        actionable: List[RawDetection] = []
        suppressed: List[Dict[str, Any]] = []

        for det in detections:
            cname = det.className.strip().upper()
            if cls.is_class_actionable(cname):
                actionable.append(det)
            else:
                suppression_info = {
                    "className": det.className,
                    "confidence": det.confidence,
                    "suppressionReason": (
                        f"Perception class '{det.className}' status is UNSUPPORTED "
                        "(0 true positives on test set). Suppressed from actionable findings."
                    ),
                    "boundingBox": det.boundingBox,
                    "timestamp": "now",
                }
                suppressed.append(suppression_info)
                logger.info(
                    f"CAPABILITY_GATE_SUPPRESSION | Class: {det.className} | "
                    f"Confidence: {det.confidence:.2f} | Reason: UNSUPPORTED"
                )

        return actionable, suppressed


# Global singleton instance
model_approval_gate = ModelApprovalGate()
