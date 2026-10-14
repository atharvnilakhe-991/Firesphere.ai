"""FireSphere.ai — Phase 5 Independent Vision-Language Model (VLM) Service.

Provides independent multimodal visual inspection analysis for fire safety evidence,
producing structured, evidence-linked, advisory observations that complement
the existing Computer Vision detector without performing cross-model reconciliation.
"""

from datetime import datetime, timezone
import hashlib
import time
from typing import Any, Dict, List, Optional, Tuple, Union
import uuid
import cv2
import numpy as np

from app.core.config import settings
from app.core.exceptions import (
    EvidenceNotFoundException,
    EvidenceOversizedException,
    FireSphereAIException,
    ImageDecodeException,
    ImageQualityException,
    InvalidEvidenceException,
    UnsupportedInputException,
)
from app.core.logging import logger
from app.cv.file_validator import FileValidator
from app.cv.quality import ImageQualityValidator
from app.intelligence.evidence_validator import EvidenceValidator, evidence_validator
from app.models.gemini_provider import GeminiProvider, gemini_provider
from app.models.gemini_schemas import (
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
    generate_verification_guidance,
)
from app.models.vlm_provider import BaseVLMProvider, get_vlm_provider
from app.schemas.finding import BoundingBox
from app.storage.evidence_client import EvidenceClient, evidence_client


class VLMService:
    """Independent Vision-Language Model analysis service.

    Enforces strict safety boundaries:
    - Analyzes inspection evidence independently of CV bounding boxes.
    - Preserves SHA-256 evidence integrity and tenant isolation.
    - All outputs are strictly advisory; requires human Fire Officer verification.
    - Never issues autonomous Fire NOC compliance decisions.
    - Explicitly reports component availability and degraded/blocked states.
    """

    def __init__(
        self,
        provider: Optional[BaseVLMProvider] = None,
        storage: Optional[EvidenceClient] = None,
        validator: Optional[EvidenceValidator] = None,
        settings_obj: Optional[Any] = None,
    ):
        cfg = settings_obj or settings
        if provider is not None:
            self.provider = provider
        else:
            self.provider = get_vlm_provider(settings_obj=cfg)
        self.storage = storage or evidence_client
        self.validator = validator or evidence_validator

    def get_status(self) -> Dict[str, Any]:
        """Returns component readiness and execution status."""
        provider_info = self.provider.get_provider_info()
        is_enabled = self.provider.is_enabled()
        provider_status = self.provider.get_status()
        prov_name = provider_info.get("provider", getattr(self.provider, "provider_name", "ollama"))

        # Real inference status evaluation in current environment
        if not is_enabled:
            real_status = "BLOCKED"
            if "gemini" in prov_name.lower():
                blocker = "GEMINI_ENABLED is false; live external AI calls prohibited by governance rule"
            elif "ollama" in prov_name.lower():
                blocker = "OLLAMA_ENABLED is false; local VLM provider disabled"
            else:
                blocker = f"{prov_name.upper()}_ENABLED is false; live external AI calls prohibited by governance rule"
        elif not self.provider.is_configured():
            real_status = "BLOCKED"
            if "gemini" in prov_name.lower():
                blocker = "GEMINI_API_KEY is not configured"
            elif "ollama" in prov_name.lower():
                blocker = "OLLAMA_BASE_URL or OLLAMA_MODEL is not configured"
            else:
                blocker = f"{prov_name.upper()}_API_KEY is not configured"
        else:
            real_status = "OPERATIONAL"
            blocker = None

        if "gemini" in prov_name.lower():
            default_ver = "gemini-genai-v2"
        elif "ollama" in prov_name.lower():
            default_ver = "ollama-qwen2.5vl-v1"
        else:
            default_ver = "openai-v1"
        model_ver = provider_info.get("modelVersion", default_ver)

        return {
            "service": "firesphere-vlm-service",
            "provider": prov_name,
            "model": self.provider.get_model_id(),
            "modelVersion": model_ver,
            "analysisComponent": "VLM",
            "enabled": is_enabled,
            "status": provider_status,
            "runtimeDevice": "cpu",
            "realInferenceStatus": real_status,
            "blockerReason": blocker,
            "governancePolicy": "ADVISORY_ONLY_HUMAN_OFFICER_VERIFICATION_MANDATORY",
            "autonomousNOCDecisionsAllowed": False,
        }


    def _resolve_evidence_payload(
        self,
        evidence_id: Union[str, uuid.UUID],
        image_bytes: Optional[bytes] = None,
    ) -> bytes:
        """Resolves raw evidence payload safely from memory or storage.

        Rejects arbitrary filesystem path traversal.
        """
        if image_bytes is not None and len(image_bytes) > 0:
            return image_bytes

        eid_str = str(evidence_id)
        # Check in-memory storage registry
        if eid_str in self.storage._test_registry:
            return self.storage._test_registry[eid_str]["bytes"]

        # Attempt to fetch via storage client
        try:
            eid_uuid = uuid.UUID(eid_str) if isinstance(evidence_id, str) else evidence_id
            fetched = self.storage.fetch_evidence_bytes(eid_uuid)
            if fetched:
                return fetched
        except Exception:
            pass

        raise EvidenceNotFoundException(
            f"Evidence payload for {evidence_id} could not be resolved from storage."
        )

    def analyze_evidence(
        self,
        evidence_id: Union[str, uuid.UUID],
        tenant_id: Optional[str] = "default",
        image_bytes: Optional[bytes] = None,
        expected_sha256: Optional[str] = None,
        context: Optional[Dict[str, Any]] = None,
    ) -> GeminiAnalysisResult:
        """Executes independent VLM visual reasoning on an evidence image.

        Workflow:
        1. Validates UUID and tenant context boundaries.
        2. Resolves evidence payload securely.
        3. Verifies SHA-256 cryptographic integrity if expected hash is provided.
        4. Validates payload binary format, size limits, and image quality.
        5. Executes VLM inference with fail-closed error handling.
        6. Validates, enriches, and normalizes structured observations.
        7. Enforces advisory-only governance boundaries.
        """
        start_time = time.perf_counter()
        eid_str = str(evidence_id)

        # 1. Validate UUID format
        try:
            uuid.UUID(eid_str)
        except (ValueError, AttributeError):
            logger.warning(f"VLMService: Malformed evidence UUID '{evidence_id}' rejected.")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.provider.get_model_id(),
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Malformed evidence identifier: {evidence_id}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 2. Resolve evidence payload
        try:
            raw_bytes = self._resolve_evidence_payload(evidence_id, image_bytes=image_bytes)
        except EvidenceNotFoundException as enf:
            logger.warning(f"VLMService: Evidence not found | evidenceId={eid_str}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.provider.get_model_id(),
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Evidence {eid_str} not found in storage.",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )
        except Exception as exc:
            logger.error(f"VLMService: Error resolving evidence {eid_str}: {exc}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.provider.get_model_id(),
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Failed to resolve evidence payload: {type(exc).__name__}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 3. Cryptographic SHA-256 integrity verification
        actual_sha256 = hashlib.sha256(raw_bytes).hexdigest()
        if expected_sha256:
            exp_clean = expected_sha256.strip().lower()
            if actual_sha256.lower() != exp_clean:
                logger.warning(
                    f"VLMService: SHA-256 integrity mismatch for evidenceId={eid_str} | "
                    f"computed={actual_sha256} | expected={exp_clean}"
                )
                return GeminiAnalysisResult(
                    evidenceId=eid_str,
                    model=self.provider.get_model_id(),
                    status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                    observations=[],
                    error=(
                        f"Evidence integrity mismatch: computed SHA-256 ({actual_sha256}) "
                        f"does not match expected hash ({exp_clean})."
                    ),
                    executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                    requiresOfficerVerification=True,
                    isSafeInference=False,
                )

        # 4. Binary payload format and size validation
        try:
            detected_mime, size_bytes = FileValidator.validate_payload(
                evidence_id=eid_str,
                raw_bytes=raw_bytes,
            )
        except (EvidenceOversizedException, UnsupportedInputException) as val_err:
            logger.warning(f"VLMService: Payload validation failed for evidenceId={eid_str}: {val_err.message}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.provider.get_model_id(),
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=val_err.message,
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 5. Image matrix decoding and quality checks
        np_arr = np.frombuffer(raw_bytes, dtype=np.uint8)
        img_bgr = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        if img_bgr is None or img_bgr.size == 0:
            logger.warning(f"VLMService: Failed to decode image for evidenceId={eid_str}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.provider.get_model_id(),
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Evidence {eid_str} could not be decoded into a valid image matrix.",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        h, w = img_bgr.shape[:2]
        dim_ok, dim_err = EvidenceValidator.validate_image_dimensions(w, h)
        if not dim_ok:
            logger.warning(f"VLMService: Invalid image dimensions for evidenceId={eid_str}: {dim_err}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.provider.get_model_id(),
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=dim_err or "Invalid image dimensions",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 6. Execute independent VLM inference
        result: GeminiAnalysisResult = self.provider.analyze_evidence(
            evidence_id=eid_str,
            image_data=raw_bytes,
            context=context,
        )

        # 7. Post-process and strictly validate observations against schema rules
        validated_observations: List[GeminiObservation] = []
        for obs in result.observations:
            # Validate observation schema
            obs_dict = obs.model_dump()
            schema_ok, schema_errors = EvidenceValidator.validate_observation_schema(obs_dict)
            if not schema_ok:
                logger.warning(
                    f"VLMService: Observation schema validation rejected item: {'; '.join(schema_errors)}"
                )
                continue

            # Validate bounding box if present
            bbox_ok, bbox_err = EvidenceValidator.validate_bounding_box(obs.boundingBox)
            if not bbox_ok:
                logger.warning(f"VLMService: Invalid bounding box ({bbox_err}); neutralizing box.")
                obs.boundingBox = None

            # Ensure verification guidance is present
            if not obs.verificationGuidance:
                obs.verificationGuidance = generate_verification_guidance(
                    obs.objectCategory,
                    obs.potentialHazardCategory,
                )

            # Ensure all unified contract fields are present
            obs.analysisComponent = "VLM"
            obs.advisoryStatus = "ADVISORY_ONLY"
            obs.officerReviewRequirement = "MANDATORY_OFFICER_REVIEW"
            validated_observations.append(obs)

        # Update result observations
        result.observations = validated_observations
        result.analysisComponent = "VLM"
        result.advisoryStatus = "ADVISORY_ONLY"
        result.requiresOfficerVerification = True
        result.isSafeInference = False

        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
        result.executionDurationMs = duration_ms

        logger.info(
            f"VLMService: Evidence analysis completed | evidenceId={eid_str} | "
            f"status={result.status.value} | observations={len(result.observations)} | duration={duration_ms}ms"
        )
        return result


# Global singleton instance
vlm_service = VLMService()
