"""FireSphere.ai — Gemini VLM Provider Integration (Phase 2 Vision Analysis).

Provides secure configuration loading, official Google GenAI SDK client
initialization, image evidence validation, multimodal visual reasoning,
structured observation parsing, bounded retries, and strict compliance boundaries.

Strict Governance Invariants:
1. Disabled by default (fail-closed).
2. Service starts safely without external credentials or network connectivity.
3. API credentials and secrets are NEVER logged, serialized, or exposed.
4. Independent AI decisions (AI_APPROVED / AI_REJECTED) are strictly prohibited.
5. Does not bypass the existing CV model approval gate.
6. A successful empty result is distinct from failure, invalid output, or inconclusive evidence.
7. Does not fabricate synthetic observations, bounding boxes, or confidence scores.
"""

import math
import time
from typing import Any, Dict, List, Optional, Tuple, Union
import uuid
import cv2
import numpy as np

from app.core.config import Settings, settings
from app.core.exceptions import (
    EvidenceOversizedException,
    GeminiConfigurationException,
    GeminiProviderException,
    ImageDecodeException,
    ImageQualityException,
    UnsupportedInputException,
)
from app.core.logging import logger
from app.cv.file_validator import FileValidator
from app.cv.quality import ImageQualityValidator
from app.models.gemini_prompts import (
    GEMINI_INSPECTION_PROMPT,
    GEMINI_SYSTEM_INSTRUCTION,
)
from app.models.gemini_schemas import (
    SUPPORTED_HAZARD_CATEGORIES,
    SUPPORTED_OBJECT_CATEGORIES,
    VALID_SEVERITIES,
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
    GeminiRawObservation,
    GeminiRawResponse,
    parse_and_validate_bounding_box,
)
from app.models.vlm_provider import BaseVLMProvider
from app.schemas.finding import BoundingBox
from app.storage.evidence_client import evidence_client

# Safe optional import of the official Google GenAI SDK
try:
    from google import genai
    from google.genai import types
    GENAI_AVAILABLE = True
except ImportError:  # pragma: no cover
    genai = None
    types = None
    GENAI_AVAILABLE = False


class GeminiProvider(BaseVLMProvider):
    """Dedicated provider adapter for Google Gemini Vision-Language Models.

    Encapsulates SDK client lifecycle, safe credential access, configurable model selection,
    bounded retries, image evidence validation, and structured vision analysis.
    """

    @property
    def provider_name(self) -> str:
        """Returns the canonical provider identity."""
        return "google-gemini"


    def __init__(
        self,
        settings_obj: Optional[Settings] = None,
        client: Optional[Any] = None,
    ):
        """Initializes provider adapter. Supports dependency injection for testing.

        Does NOT initiate network connections or validate remote credentials during init.
        """
        self._settings = settings_obj or settings
        self.enabled: bool = self._settings.gemini_enabled
        self.model_id: str = self._settings.gemini_model
        self.timeout_seconds: int = self._settings.gemini_timeout_seconds
        self.max_retries: int = self._settings.gemini_max_retries
        self._api_key: Optional[str] = self._settings.gemini_api_key
        self._client: Optional[Any] = client
        self._initialized: bool = client is not None

    def is_enabled(self) -> bool:
        """Returns whether the Gemini provider is enabled in configuration."""
        return self.enabled

    def is_configured(self) -> bool:
        """Returns whether an API key has been supplied."""
        return bool(self._api_key and self._api_key.strip())

    def is_ready(self) -> bool:
        """Returns whether the provider is enabled, configured, and initialized."""
        return self.enabled and self.is_configured() and self._initialized

    def get_status(self) -> str:
        """Returns an honest component readiness status for system health checks.

        Returns one of: 'DISABLED', 'READY', 'NOT_CONFIGURED', 'CONFIGURED'.
        """
        if not self.enabled:
            return "DISABLED"
        if not self.is_configured():
            return "NOT_CONFIGURED"
        if self._initialized and self._client is not None:
            return "READY"
        return "CONFIGURED"

    def get_model_id(self) -> str:
        """Returns the configured Gemini model ID."""
        return self.model_id

    def get_provider_info(self) -> Dict[str, Any]:
        """Returns public provider metadata. NEVER returns credentials or secret tokens."""
        return {
            "provider": "google-gemini",
            "model": self.model_id,
            "enabled": self.enabled,
            "status": self.get_status(),
            "sdkAvailable": GENAI_AVAILABLE,
            "timeoutSeconds": self.timeout_seconds,
            "maxRetries": self.max_retries,
            "productionApproved": False,
            "governancePolicy": "ADVISORY_ONLY_HUMAN_OFFICER_VERIFICATION_MANDATORY",
        }

    def initialize(self) -> Optional[Any]:
        """Safely initializes the official Google GenAI SDK client.

        Raises:
            GeminiConfigurationException: If provider is enabled but credentials are missing.
            GeminiProviderException: If client instantiation fails.
        """
        if not self.enabled:
            logger.info("GeminiProvider: Provider is disabled (GEMINI_ENABLED=false). Initialization skipped.")
            return None

        if not self.is_configured():
            logger.error("GeminiProvider: GEMINI_ENABLED=true but GEMINI_API_KEY is not configured or empty.")
            raise GeminiConfigurationException(
                message="Gemini provider is enabled but GEMINI_API_KEY is missing or empty. Provide valid credentials."
            )

        if not GENAI_AVAILABLE:
            logger.error("GeminiProvider: google-genai SDK package is not installed.")
            raise GeminiProviderException(
                message="Official google-genai SDK is not installed in the current environment."
            )

        if self._client is not None:
            return self._client

        try:
            # Instantiate official GenAI client with configured API key and timeout options
            # Convert timeout from seconds to milliseconds for google-genai SDK (e.g., 30s -> 30000ms)
            timeout_ms = float(self.timeout_seconds * 1000)
            http_retry_opts = types.HttpRetryOptions(attempts=max(1, self.max_retries)) if (types and hasattr(types, "HttpRetryOptions")) else None
            http_opts = types.HttpOptions(
                timeout=timeout_ms,
                retry_options=http_retry_opts,
            ) if types else None
            self._client = genai.Client(
                api_key=self._api_key.strip(),
                http_options=http_opts,
            )
            self._initialized = True
            logger.info(
                f"GeminiProvider: Initialized official client successfully | Model={self.model_id} | "
                f"Timeout={self.timeout_seconds}s ({timeout_ms:.0f}ms) | MaxRetries={self.max_retries}"
            )
            return self._client
        except Exception as e:
            # Strictly sanitize error message to avoid secret or URL token leakage
            err_type = type(e).__name__
            logger.error(f"GeminiProvider: Client initialization failed ({err_type})")
            raise GeminiProviderException(
                message=f"Gemini client initialization failed: {err_type}."
            ) from None

    def get_client(self) -> Optional[Any]:
        """Returns the initialized client instance, or None if not initialized."""
        return self._client

    def _call_gemini_with_retries(self, contents: List[Any], config: Any) -> Any:
        """Executes client.models.generate_content with bounded retries for transient errors."""
        if self._client is None:
            raise GeminiProviderException("Gemini client is not initialized.")

        max_attempts = max(1, self.max_retries)
        last_exception: Optional[Exception] = None

        for attempt in range(1, max_attempts + 1):
            try:
                response = self._client.models.generate_content(
                    model=self.model_id,
                    contents=contents,
                    config=config,
                )
                return response
            except Exception as exc:
                last_exception = exc
                err_str = str(exc).lower()

                # Permanent/non-retryable errors: authentication, permission, bad argument
                is_permanent = any(
                    sig in err_str
                    for sig in [
                        "401", "403", "unauthorized", "permissiondenied",
                        "api_key_invalid", "400", "invalidargument",
                    ]
                )
                if is_permanent:
                    logger.error(f"GeminiProvider: Non-retryable error ({type(exc).__name__}); aborting retries.")
                    raise

                if attempt < max_attempts:
                    backoff = min(1.0 * (2 ** (attempt - 1)), 5.0)
                    logger.warning(
                        f"GeminiProvider: Transient error on attempt {attempt}/{max_attempts} "
                        f"({type(exc).__name__}). Retrying in {backoff:.1f}s..."
                    )
                    time.sleep(backoff)
                else:
                    logger.error(
                        f"GeminiProvider: All {max_attempts} attempts exhausted ({type(exc).__name__})."
                    )
                    raise last_exception

    def _resolve_and_validate_image(
        self,
        evidence_id: str,
        image_data: Optional[Union[bytes, np.ndarray, Any]] = None,
    ) -> Tuple[bytes, str, Tuple[int, int]]:
        """Resolves raw evidence bytes, checks MIME signatures and dimensions.

        Returns (image_bytes, detected_mime_type, (width, height)).
        """
        # 1. Resolve raw bytes
        raw_bytes: Optional[bytes] = None

        if isinstance(image_data, bytes):
            raw_bytes = image_data
        elif isinstance(image_data, np.ndarray):
            # Encode numpy matrix to JPEG bytes
            success, enc_buf = cv2.imencode(".jpg", image_data)
            if not success:
                raise ImageDecodeException("Failed to encode numpy array into JPEG format.")
            raw_bytes = enc_buf.tobytes()
        elif hasattr(image_data, "preprocessed_array") and isinstance(image_data.preprocessed_array, np.ndarray):
            # PreprocessingResult passed
            arr = image_data.preprocessed_array
            if arr.dtype == np.float32:
                arr = (arr * 255.0).clip(0, 255).astype(np.uint8)
            success, enc_buf = cv2.imencode(".jpg", arr)
            if not success:
                raise ImageDecodeException("Failed to encode preprocessed array into JPEG format.")
            raw_bytes = enc_buf.tobytes()
        elif image_data is None:
            # Attempt to fetch from evidence client
            try:
                import uuid as _uuid
                eid_uuid = _uuid.UUID(evidence_id) if isinstance(evidence_id, str) else evidence_id
                raw_bytes = evidence_client.fetch_evidence_bytes(eid_uuid)
            except Exception as e:
                raise UnsupportedInputException(
                    message=f"Could not resolve image bytes for evidence {evidence_id}: {type(e).__name__}"
                ) from None
        else:
            raise UnsupportedInputException(
                message=f"Unsupported image data type: {type(image_data).__name__}"
            )

        # 2. Validate payload size and binary magic signatures
        detected_mime, size_bytes = FileValidator.validate_payload(
            evidence_id=evidence_id,
            raw_bytes=raw_bytes,
        )

        # 3. Decode image to verify geometry and quality
        np_arr = np.frombuffer(raw_bytes, dtype=np.uint8)
        img_bgr = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        if img_bgr is None or img_bgr.size == 0:
            raise ImageDecodeException(
                message=f"Evidence {evidence_id} could not be decoded into a valid image matrix."
            )

        h, w = img_bgr.shape[:2]
        quality_report = ImageQualityValidator.evaluate(img_bgr)
        if not quality_report.isAcceptable:
            raise ImageQualityException(
                message=f"Evidence {evidence_id} failed critical quality checks: {'; '.join(quality_report.errors)}"
            )

        return raw_bytes, detected_mime, (w, h)

    def analyze_evidence(
        self,
        evidence_id: str,
        image_data: Optional[Union[bytes, np.ndarray, Any]] = None,
        context: Optional[Dict[str, Any]] = None,
    ) -> GeminiAnalysisResult:
        """Executes Gemini multimodal visual inspection analysis on evidence.

        Enforces strict safety and governance boundaries:
        - Advisory only: Requires mandatory authorized officer verification.
        - NEVER issues autonomous AI_APPROVED / AI_REJECTED Fire NOC decisions.
        - Explicitly distinguishes successful detections, successful empty,
          inconclusive evidence, invalid response, and technical failures.
        - Strict coordinate conversion and bounding box validation.
        - Bounded retries and sanitized error logging (never logs secrets or image bytes).
        """
        start_time = time.perf_counter()

        # 1. Check if Gemini provider is enabled
        if not self.enabled:
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.GEMINI_DISABLED,
                observations=[],
                error="Gemini provider is disabled in configuration.",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 2. Check if credentials are configured
        if not self.is_configured():
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.GEMINI_NOT_CONFIGURED,
                observations=[],
                error="Gemini provider is enabled but GEMINI_API_KEY is not configured.",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 3. Ensure SDK client is initialized
        try:
            self.initialize()
        except (GeminiConfigurationException, GeminiProviderException) as init_err:
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Provider initialization failed: {init_err.message}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 4. Resolve and validate image payload
        try:
            image_bytes, mime_type, dimensions = self._resolve_and_validate_image(
                evidence_id=evidence_id,
                image_data=image_data,
            )
        except (UnsupportedInputException, EvidenceOversizedException, ImageDecodeException, ImageQualityException) as img_err:
            logger.warning(f"GeminiProvider: Image validation failed for evidenceId={evidence_id} ({type(img_err).__name__})")
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Image validation rejected: {img_err.message}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )
        except Exception as unk_err:
            logger.error(f"GeminiProvider: Unexpected image validation error for evidenceId={evidence_id}: {unk_err}")
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Image validation failed: {type(unk_err).__name__}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 5. Build prompt, image part, and generation configuration
        try:
            image_part = types.Part.from_bytes(data=image_bytes, mime_type=mime_type)
            contents = [image_part, GEMINI_INSPECTION_PROMPT]
            # Convert timeout from seconds to milliseconds for google-genai SDK (e.g., 30s -> 30000ms)
            timeout_ms = float(self.timeout_seconds * 1000)
            # Explicit retry policy: Request-level attempts set to 1 to prevent accidental
            # N x M multiplication between SDK tenacity retries and application-level retries.
            # _call_gemini_with_retries serves as the single bounded retry controller.
            gen_http_retry_opts = types.HttpRetryOptions(attempts=1) if (types and hasattr(types, "HttpRetryOptions")) else None
            gen_config = types.GenerateContentConfig(
                system_instruction=GEMINI_SYSTEM_INSTRUCTION,
                response_mime_type="application/json",
                response_schema=GeminiRawResponse,
                temperature=0.1,
                http_options=types.HttpOptions(
                    timeout=timeout_ms,
                    retry_options=gen_http_retry_opts,
                ) if types else None,
            )
        except Exception as prep_err:
            logger.error(f"GeminiProvider: Error preparing GenAI request contents ({type(prep_err).__name__})")
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Failed to prepare API request: {type(prep_err).__name__}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 6. Execute model inference with bounded retries and timeout
        try:
            raw_response_obj = self._call_gemini_with_retries(contents=contents, config=gen_config)
        except Exception as call_err:
            err_type = type(call_err).__name__
            status_code = getattr(call_err, "code", None)
            err_desc = f"{err_type} (HTTP {status_code})" if isinstance(status_code, int) and 100 <= status_code <= 599 else err_type
            logger.error(f"GeminiProvider: API call failed for evidenceId={evidence_id} ({err_desc})")
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Gemini API request failed: {err_desc}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)

        # 7. Extract raw JSON response text
        raw_text = getattr(raw_response_obj, "text", None)
        if not raw_text or not str(raw_text).strip():
            logger.warning(f"GeminiProvider: Empty text response received for evidenceId={evidence_id}")
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE,
                observations=[],
                error="Gemini returned an empty response with no content.",
                executionDurationMs=duration_ms,
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 8. Parse and validate structured output
        clean_text = str(raw_text).strip()
        parsed_model: Optional[GeminiRawResponse] = None

        try:
            parsed_model = GeminiRawResponse.model_validate_json(clean_text)
        except Exception as parse_err:
            # Fallback: remove possible markdown code fencing if returned by model
            sanitized_json = clean_text
            if sanitized_json.startswith("```json"):
                sanitized_json = sanitized_json[7:]
            elif sanitized_json.startswith("```"):
                sanitized_json = sanitized_json[3:]
            if sanitized_json.endswith("```"):
                sanitized_json = sanitized_json[:-3]
            sanitized_json = sanitized_json.strip()

            try:
                parsed_model = GeminiRawResponse.model_validate_json(sanitized_json)
            except Exception as fallback_err:
                logger.warning(
                    f"GeminiProvider: Response schema validation failed for evidenceId={evidence_id}: "
                    f"{type(fallback_err).__name__}"
                )
                return GeminiAnalysisResult(
                    evidenceId=evidence_id,
                    model=self.model_id,
                    status=GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE,
                    observations=[],
                    error=f"Model response failed schema validation: {type(fallback_err).__name__}",
                    executionDurationMs=duration_ms,
                    requiresOfficerVerification=True,
                    isSafeInference=False,
                )

        # 9. Handle explicit inconclusive status
        if parsed_model.isInconclusive:
            reason = parsed_model.inconclusiveReason or "Model indicated insufficient visual evidence to evaluate."
            logger.info(f"GeminiProvider: Visual evidence marked INCONCLUSIVE for evidenceId={evidence_id} | Reason: {reason}")
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE,
                observations=[],
                inconclusiveReason=reason,
                rawResponse=parsed_model.model_dump(),
                executionDurationMs=duration_ms,
                requiresOfficerVerification=True,
                isSafeInference=False,
            )

        # 10. Process and validate observations
        validated_observations: List[GeminiObservation] = []

        for raw_obs in parsed_model.observations:
            # Normalize objectCategory
            raw_obj = raw_obs.objectCategory.strip().upper()
            if raw_obj in SUPPORTED_OBJECT_CATEGORIES:
                object_category = raw_obj
            else:
                # Map or categorize unrecognized object
                if "EXTINGUISHER" in raw_obj:
                    object_category = "FIRE_EXTINGUISHER"
                elif "SIGN" in raw_obj:
                    object_category = "EXIT_SIGN"
                elif "DOOR" in raw_obj:
                    object_category = "EXIT_DOOR"
                else:
                    object_category = "GENERIC_FIRE_SAFETY_DEFECT"

            # Normalize potentialHazardCategory
            hazard_category: Optional[str] = None
            if raw_obs.potentialHazardCategory:
                norm_haz = raw_obs.potentialHazardCategory.strip().upper()
                if norm_haz in SUPPORTED_HAZARD_CATEGORIES and norm_haz != "NONE":
                    hazard_category = norm_haz
                elif norm_haz != "NONE":
                    hazard_category = "GENERIC_FIRE_SAFETY_DEFECT"

            # Parse and validate bounding box coordinates
            bounding_box = parse_and_validate_bounding_box(
                box_raw=raw_obs.box_2d,
                image_dims=dimensions,
            )

            # Validate confidence score [0.0 - 1.0] without fabricating scores
            conf_score = raw_obs.confidence
            if conf_score is not None:
                if not (0.0 <= conf_score <= 1.0) or math.isnan(conf_score) or math.isinf(conf_score):
                    conf_score = None

            # Validate severity proposal
            severity_prop: Optional[str] = None
            if raw_obs.severityProposal:
                norm_sev = raw_obs.severityProposal.strip().upper()
                if norm_sev in VALID_SEVERITIES:
                    severity_prop = norm_sev

            obs = GeminiObservation(
                observationId=str(uuid.uuid4()),
                imageReference=evidence_id,
                objectCategory=object_category,
                potentialHazardCategory=hazard_category,
                observationDescription=raw_obs.observationDescription,
                evidenceStatus=raw_obs.evidenceStatus,
                boundingBox=bounding_box,
                confidence=conf_score,
                severityProposal=severity_prop,
                modelName=self.model_id,
                modelVersion="gemini-genai-v2",
                analysisStatus="COMPLETED",
                uncertaintyOrLimitations=raw_obs.uncertaintyOrLimitations,
                officerReviewRequirement="MANDATORY_OFFICER_REVIEW",
            )
            validated_observations.append(obs)

        # 11. Determine explicit analysis outcome status
        if not validated_observations:
            final_status = GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS
            logger.info(
                f"GeminiProvider: Analysis COMPLETED with 0 observations for evidenceId={evidence_id} in {duration_ms}ms"
            )
        else:
            final_status = GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
            logger.info(
                f"GeminiProvider: Analysis COMPLETED with {len(validated_observations)} observations "
                f"for evidenceId={evidence_id} in {duration_ms}ms"
            )

        return GeminiAnalysisResult(
            evidenceId=evidence_id,
            model=self.model_id,
            status=final_status,
            observations=validated_observations,
            rawResponse=parsed_model.model_dump(),
            executionDurationMs=duration_ms,
            requiresOfficerVerification=True,
            isSafeInference=False,
        )

    # Alias for backward-compatibility with orchestrator
    analyze_image = analyze_evidence


# Global singleton provider instance
gemini_provider = GeminiProvider()
