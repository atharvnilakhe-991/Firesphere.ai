"""FireSphere.ai — OpenAI VLM Provider Integration (Phase 11B Vision Analysis).

Provides secure configuration loading, OpenAI vision API adapter,
image evidence validation, base64 payload preparation, strict structured output
request formatting, bounded retries, and strict compliance boundaries.

Strict Governance Invariants:
1. Disabled by default (fail-closed).
2. Service starts safely without external credentials or network connectivity.
3. API credentials and secrets are NEVER logged, serialized, or exposed.
4. Independent AI decisions (AI_APPROVED / AI_REJECTED) are strictly prohibited.
5. Does not bypass the existing CV model approval gate.
6. A successful empty result is distinct from failure, invalid output, or inconclusive evidence.
7. Does not fabricate synthetic observations, bounding boxes, or confidence scores.
"""

import base64
import math
import time
from typing import Any, Dict, List, Optional, Tuple, Union
import uuid
import cv2
import httpx
import numpy as np

from app.core.config import Settings, settings
from app.core.exceptions import (
    EvidenceOversizedException,
    ImageDecodeException,
    ImageQualityException,
    OpenAIConfigurationException,
    OpenAIProviderException,
    UnsupportedInputException,
)
from app.core.logging import logger
from app.cv.file_validator import FileValidator
from app.cv.quality import ImageQualityValidator
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
from app.models.openai_prompts import (
    OPENAI_INSPECTION_PROMPT,
    OPENAI_SYSTEM_INSTRUCTION,
)
from app.models.vlm_provider import BaseVLMProvider
from app.storage.evidence_client import evidence_client

# Safe optional import of official openai SDK
try:
    import openai
    OPENAI_AVAILABLE = True
except ImportError:  # pragma: no cover
    openai = None
    OPENAI_AVAILABLE = False


# Strict Structured Output JSON Schema for OpenAI models supporting json_schema
OPENAI_STRUCTURED_RESPONSE_SCHEMA = {
    "type": "object",
    "properties": {
        "isInconclusive": {
            "type": "boolean",
            "description": "True if the photograph does not provide sufficient visible evidence to evaluate",
        },
        "inconclusiveReason": {
            "type": ["string", "null"],
            "description": "Physical explanation why visual evidence is inconclusive or null",
        },
        "observations": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "objectCategory": {
                        "type": "string",
                        "description": "Detected physical equipment or object class",
                    },
                    "potentialHazardCategory": {
                        "type": ["string", "null"],
                        "description": "Suggested fire safety hazard type or NONE",
                    },
                    "observationDescription": {
                        "type": "string",
                        "description": "Objective natural-language reasoning of visible evidence",
                    },
                    "evidenceStatus": {
                        "type": "string",
                        "description": "Observation certainty: DETECTED, ABSENT, or UNCERTAIN",
                    },
                    "box_2d": {
                        "type": ["array", "null"],
                        "items": {"type": "number"},
                        "description": "2D bounding box [ymin, xmin, ymax, xmax] normalized on 0-1000 scale or null",
                    },
                    "confidence": {
                        "type": ["number", "null"],
                        "description": "Model prediction confidence score [0.0 - 1.0] if available",
                    },
                    "uncertaintyOrLimitations": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Documented optical limitations, occlusions, or image-quality caveats",
                    },
                    "severityProposal": {
                        "type": ["string", "null"],
                        "description": "Suggested severity proposal: INFORMATIONAL, LOW, MEDIUM, HIGH, CRITICAL",
                    },
                },
                "required": [
                    "objectCategory",
                    "potentialHazardCategory",
                    "observationDescription",
                    "evidenceStatus",
                    "box_2d",
                    "confidence",
                    "uncertaintyOrLimitations",
                    "severityProposal",
                ],
                "additionalProperties": False,
            },
            "description": "List of structured visual observations extracted from the evidence",
        },
    },
    "required": ["isInconclusive", "inconclusiveReason", "observations"],
    "additionalProperties": False,
}


class OpenAIProvider(BaseVLMProvider):
    """Dedicated provider adapter for OpenAI Vision-Language Models (e.g. gpt-4o-mini, gpt-4o).

    Encapsulates client lifecycle, safe credential access, configurable model selection,
    bounded retries, image evidence validation, base64 payload preparation,
    structured output schema enforcement, and normalized result conversion.
    """

    def __init__(
        self,
        settings_obj: Optional[Settings] = None,
        client: Optional[Any] = None,
    ):
        """Initializes OpenAI provider adapter. Supports dependency injection for offline testing.

        Does NOT initiate remote network connections or validate credentials during initialization.
        """
        self._settings = settings_obj or settings
        self.enabled: bool = getattr(self._settings, "openai_enabled", False)
        self.model_id: str = getattr(self._settings, "openai_model", "gpt-4o-mini")
        self.timeout_seconds: int = getattr(self._settings, "openai_timeout_seconds", 30)
        self.max_retries: int = getattr(self._settings, "openai_max_retries", 3)
        self.structured_outputs: bool = getattr(self._settings, "openai_structured_outputs", True)
        self._api_key: Optional[str] = getattr(self._settings, "openai_api_key", None)
        self._client: Optional[Any] = client
        self._initialized: bool = client is not None

    @property
    def provider_name(self) -> str:
        """Returns the canonical provider identity."""
        return "openai"

    def is_enabled(self) -> bool:
        """Returns whether the OpenAI provider is enabled in configuration."""
        return self.enabled

    def is_configured(self) -> bool:
        """Returns whether an API key has been supplied."""
        return bool(self._api_key and self._api_key.strip())

    def is_ready(self) -> bool:
        """Returns whether the provider is enabled, configured, and initialized."""
        return self.enabled and self.is_configured() and self._initialized

    def get_status(self) -> str:
        """Returns an honest component readiness status for system health checks.

        Returns one of: 'DISABLED', 'NOT_CONFIGURED', 'CONFIGURED', 'READY'.
        """
        if not self.enabled:
            return "DISABLED"
        if not self.is_configured():
            return "NOT_CONFIGURED"
        if self._initialized and self._client is not None:
            return "READY"
        return "CONFIGURED"

    def get_model_id(self) -> str:
        """Returns the configured OpenAI model ID."""
        return self.model_id

    def get_provider_info(self) -> Dict[str, Any]:
        """Returns public provider metadata. NEVER returns credentials or secret tokens."""
        return {
            "provider": "openai",
            "model": self.model_id,
            "modelVersion": "openai-v1",
            "enabled": self.enabled,
            "status": self.get_status(),
            "sdkAvailable": OPENAI_AVAILABLE,
            "timeoutSeconds": self.timeout_seconds,
            "maxRetries": self.max_retries,
            "structuredOutputs": self.structured_outputs,
            "productionApproved": False,
            "governancePolicy": "ADVISORY_ONLY_HUMAN_OFFICER_VERIFICATION_MANDATORY",
        }

    def initialize(self) -> Optional[Any]:
        """Safely initializes the OpenAI client.

        Raises:
            OpenAIConfigurationException: If provider is enabled but credentials are missing.
            OpenAIProviderException: If client instantiation fails.
        """
        if not self.enabled:
            logger.info("OpenAIProvider: Provider is disabled (OPENAI_ENABLED=false). Initialization skipped.")
            return None

        if not self.is_configured():
            logger.error("OpenAIProvider: OPENAI_ENABLED=true but OPENAI_API_KEY is not configured or empty.")
            raise OpenAIConfigurationException(
                message="OpenAI provider is enabled but OPENAI_API_KEY is missing or empty. Provide valid credentials."
            )

        if self._client is not None:
            return self._client

        try:
            if OPENAI_AVAILABLE and openai is not None:
                self._client = openai.OpenAI(
                    api_key=self._api_key.strip(),
                    timeout=float(self.timeout_seconds),
                    max_retries=0,  # We manage application-level retries explicitly to avoid multiplication
                )
            else:
                # HTTP fallback when openai SDK package is not installed
                self._client = httpx.Client(
                    base_url="https://api.openai.com/v1",
                    headers={
                        "Authorization": f"Bearer {self._api_key.strip()}",
                        "Content-Type": "application/json",
                    },
                    timeout=float(self.timeout_seconds),
                )
            self._initialized = True
            logger.info(
                f"OpenAIProvider: Initialized client successfully | Model={self.model_id} | "
                f"Timeout={self.timeout_seconds}s | MaxRetries={self.max_retries}"
            )
            return self._client
        except Exception as e:
            err_type = type(e).__name__
            logger.error(f"OpenAIProvider: Client initialization failed ({err_type})")
            raise OpenAIProviderException(
                message=f"OpenAI client initialization failed: {err_type}."
            ) from None

    def get_client(self) -> Optional[Any]:
        """Returns the initialized client instance, or None if not initialized."""
        return self._client

    def _build_response_format(self) -> Dict[str, Any]:
        """Builds the response format specification based on model capabilities."""
        if self.structured_outputs:
            return {
                "type": "json_schema",
                "json_schema": {
                    "name": "fire_safety_inspection",
                    "strict": True,
                    "schema": OPENAI_STRUCTURED_RESPONSE_SCHEMA,
                },
            }
        return {"type": "json_object"}

    def _call_openai_with_retries(
        self,
        messages: List[Dict[str, Any]],
        response_format: Dict[str, Any],
    ) -> Any:
        """Executes chat completion with bounded retries for transient errors."""
        if self._client is None:
            raise OpenAIProviderException("OpenAI client is not initialized.")

        max_attempts = max(1, self.max_retries)
        last_exception: Optional[Exception] = None

        for attempt in range(1, max_attempts + 1):
            try:
                # 1. If client exposes official SDK chat.completions.create
                if hasattr(self._client, "chat") and hasattr(self._client.chat, "completions"):
                    response = self._client.chat.completions.create(
                        model=self.model_id,
                        messages=messages,
                        response_format=response_format,
                        temperature=0.1,
                        timeout=float(self.timeout_seconds),
                    )
                    return response

                # 2. If client is an HTTP client or mock transport
                elif isinstance(self._client, httpx.Client) or hasattr(self._client, "post"):
                    resp = self._client.post(
                        "/chat/completions" if hasattr(self._client, "base_url") and str(self._client.base_url).startswith("https://api.openai.com/v1") else "https://api.openai.com/v1/chat/completions",
                        json={
                            "model": self.model_id,
                            "messages": messages,
                            "response_format": response_format,
                            "temperature": 0.1,
                        },
                    )
                    if hasattr(resp, "status_code") and resp.status_code >= 400:
                        raise httpx.HTTPStatusError(
                            message=f"HTTP {resp.status_code}",
                            request=getattr(resp, "request", None),
                            response=resp,
                        )
                    return resp

                else:
                    raise OpenAIProviderException("Injected client does not support standard OpenAI call interface.")

            except Exception as exc:
                last_exception = exc
                err_str = str(exc).lower()
                status_code = getattr(exc, "status_code", None)
                if status_code is None and hasattr(exc, "response") and hasattr(exc.response, "status_code"):
                    status_code = exc.response.status_code

                # Permanent/non-retryable errors: 401 unauthorized, 403 forbidden, 400 bad request
                is_permanent = (
                    status_code in (400, 401, 403)
                    or any(
                        sig in err_str
                        for sig in [
                            "401", "403", "unauthorized", "invalid_api_key",
                            "permission_denied", "400", "invalid_request_error",
                        ]
                    )
                )
                if is_permanent:
                    logger.error(f"OpenAIProvider: Non-retryable error ({type(exc).__name__}); aborting retries.")
                    raise

                if attempt < max_attempts:
                    backoff = min(1.0 * (2 ** (attempt - 1)), 5.0)
                    logger.warning(
                        f"OpenAIProvider: Transient error on attempt {attempt}/{max_attempts} "
                        f"({type(exc).__name__}). Retrying in {backoff:.1f}s..."
                    )
                    time.sleep(backoff)
                else:
                    logger.error(
                        f"OpenAIProvider: All {max_attempts} attempts exhausted ({type(exc).__name__})."
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
        raw_bytes: Optional[bytes] = None

        if isinstance(image_data, bytes):
            raw_bytes = image_data
        elif isinstance(image_data, np.ndarray):
            success, enc_buf = cv2.imencode(".jpg", image_data)
            if not success:
                raise ImageDecodeException("Failed to encode numpy array into JPEG format.")
            raw_bytes = enc_buf.tobytes()
        elif hasattr(image_data, "preprocessed_array") and isinstance(image_data.preprocessed_array, np.ndarray):
            arr = image_data.preprocessed_array
            if arr.dtype == np.float32:
                arr = (arr * 255.0).clip(0, 255).astype(np.uint8)
            success, enc_buf = cv2.imencode(".jpg", arr)
            if not success:
                raise ImageDecodeException("Failed to encode preprocessed array into JPEG format.")
            raw_bytes = enc_buf.tobytes()
        elif image_data is None:
            try:
                eid_uuid = uuid.UUID(evidence_id) if isinstance(evidence_id, str) else evidence_id
                raw_bytes = evidence_client.fetch_evidence_bytes(eid_uuid)
            except Exception as e:
                raise UnsupportedInputException(
                    message=f"Could not resolve image bytes for evidence {evidence_id}: {type(e).__name__}"
                ) from None
        else:
            raise UnsupportedInputException(
                message=f"Unsupported image data type: {type(image_data).__name__}"
            )

        # Validate payload size and binary magic signatures
        detected_mime, size_bytes = FileValidator.validate_payload(
            evidence_id=evidence_id,
            raw_bytes=raw_bytes,
        )

        # Decode image to verify geometry and quality
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
        """Executes OpenAI multimodal visual inspection analysis on evidence.

        Enforces strict safety and governance boundaries:
        - Advisory only: Requires mandatory authorized officer verification.
        - NEVER issues autonomous AI_APPROVED / AI_REJECTED decisions.
        - Explicitly distinguishes detections, empty observations, inconclusive,
          invalid schema, and technical failures.
        - Preserves evidence ID and model provenance throughout.
        - Bounded retries and sanitized logging (zero secret leakage).
        """
        start_time = time.perf_counter()
        eid_str = str(evidence_id)

        # 1. Check if provider is enabled
        if not self.enabled:
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.PROVIDER_DISABLED,
                observations=[],
                error="OpenAI provider is disabled in configuration.",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="openai-v1",
                advisoryStatus="ADVISORY_ONLY",
            )


        # 2. Check if credentials are configured
        if not self.is_configured():
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.PROVIDER_NOT_CONFIGURED,
                observations=[],
                error="OpenAI provider is enabled but OPENAI_API_KEY is not configured.",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="openai-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 3. Ensure client is initialized
        try:
            self.initialize()
        except (OpenAIConfigurationException, OpenAIProviderException) as init_err:
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Provider initialization failed: {init_err.message}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="openai-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 4. Resolve and validate image payload
        try:
            image_bytes, mime_type, dimensions = self._resolve_and_validate_image(
                evidence_id=evidence_id,
                image_data=image_data,
            )
        except (UnsupportedInputException, EvidenceOversizedException, ImageDecodeException, ImageQualityException) as img_err:
            logger.warning(f"OpenAIProvider: Image validation rejected for evidenceId={eid_str}: {img_err.message}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Image validation rejected: {img_err.message}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="openai-v1",
                advisoryStatus="ADVISORY_ONLY",
            )
        except Exception as unk_err:
            logger.error(f"OpenAIProvider: Unexpected image validation error for evidenceId={eid_str}: {unk_err}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Image validation failed: {type(unk_err).__name__}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="openai-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 5. Build base64 data URI and chat messages
        try:
            b64_img = base64.b64encode(image_bytes).decode("utf-8")
            image_url_data = f"data:{mime_type};base64,{b64_img}"

            messages = [
                {
                    "role": "system",
                    "content": OPENAI_SYSTEM_INSTRUCTION,
                },
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "text",
                            "text": OPENAI_INSPECTION_PROMPT,
                        },
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": image_url_data,
                                "detail": "high",
                            },
                        },
                    ],
                },
            ]
            response_format = self._build_response_format()
        except Exception as prep_err:
            logger.error(f"OpenAIProvider: Error preparing request messages ({type(prep_err).__name__})")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Failed to prepare API request: {type(prep_err).__name__}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="openai-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 6. Execute model inference with bounded retries
        try:
            raw_response_obj = self._call_openai_with_retries(
                messages=messages,
                response_format=response_format,
            )
        except Exception as call_err:
            err_type = type(call_err).__name__
            status_code = getattr(call_err, "status_code", None)
            if status_code is None and hasattr(call_err, "response") and hasattr(call_err.response, "status_code"):
                status_code = call_err.response.status_code
            err_desc = f"{err_type} (HTTP {status_code})" if isinstance(status_code, int) and 100 <= status_code <= 599 else err_type
            logger.error(f"OpenAIProvider: API call failed for evidenceId={eid_str} ({err_desc})")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"OpenAI API request failed: {err_desc}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="openai-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)

        # 7. Extract raw JSON text from response
        raw_text: Optional[str] = None
        try:
            if hasattr(raw_response_obj, "choices") and len(raw_response_obj.choices) > 0:
                choice = raw_response_obj.choices[0]
                if hasattr(choice, "message") and hasattr(choice.message, "content"):
                    raw_text = choice.message.content
                elif isinstance(choice, dict) and "message" in choice and "content" in choice["message"]:
                    raw_text = choice["message"]["content"]
            elif isinstance(raw_response_obj, dict) and "choices" in raw_response_obj and len(raw_response_obj["choices"]) > 0:
                raw_text = raw_response_obj["choices"][0]["message"]["content"]
            elif hasattr(raw_response_obj, "json") and callable(raw_response_obj.json):
                j = raw_response_obj.json()
                if "choices" in j and len(j["choices"]) > 0:
                    raw_text = j["choices"][0]["message"]["content"]
        except Exception as ext_err:
            logger.warning(f"OpenAIProvider: Failed to extract response text ({ext_err})")

        if not raw_text or not str(raw_text).strip():
            logger.warning(f"OpenAIProvider: Empty content received for evidenceId={evidence_id}")
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE,
                observations=[],
                error="OpenAI returned an empty response with no content.",
                executionDurationMs=duration_ms,
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="openai-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 8. Parse and validate structured output against FireSphere schema
        clean_text = str(raw_text).strip()
        parsed_model: Optional[GeminiRawResponse] = None

        try:
            parsed_model = GeminiRawResponse.model_validate_json(clean_text)
        except Exception as parse_err:
            # Fallback: remove markdown code fencing if model emitted it
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
                    f"OpenAIProvider: Response schema validation failed for evidenceId={evidence_id}: "
                    f"{type(fallback_err).__name__}"
                )
                return GeminiAnalysisResult(
                    evidenceId=eid_str,
                    model=self.model_id,
                    status=GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE,
                    observations=[],
                    error=f"Model response failed schema validation: {type(fallback_err).__name__}",
                    executionDurationMs=duration_ms,
                    requiresOfficerVerification=True,
                    isSafeInference=False,
                    analysisComponent="VLM",
                    modelVersion="openai-v1",
                    advisoryStatus="ADVISORY_ONLY",
                )

        # 9. Handle explicit inconclusive status
        if parsed_model.isInconclusive:
            reason = parsed_model.inconclusiveReason or "Model indicated insufficient visual evidence to evaluate."
            logger.info(f"OpenAIProvider: Visual evidence marked INCONCLUSIVE for evidenceId={evidence_id} | Reason: {reason}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE,
                observations=[],
                inconclusiveReason=reason,
                rawResponse=parsed_model.model_dump(),
                executionDurationMs=duration_ms,
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="openai-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 10. Process and validate observations
        validated_observations: List[GeminiObservation] = []

        for raw_obs in parsed_model.observations:
            # Normalize objectCategory
            raw_obj = raw_obs.objectCategory.strip().upper()
            if raw_obj in SUPPORTED_OBJECT_CATEGORIES:
                object_category = raw_obj
            else:
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
                imageReference=eid_str,
                evidenceId=eid_str,
                objectCategory=object_category,
                potentialHazardCategory=hazard_category,
                observationDescription=raw_obs.observationDescription,
                evidenceStatus=raw_obs.evidenceStatus,
                boundingBox=bounding_box,
                confidence=conf_score,
                severityProposal=severity_prop,
                modelName=self.model_id,
                modelVersion="openai-v1",
                analysisStatus="COMPLETED",
                analysisComponent="VLM",
                advisoryStatus="ADVISORY_ONLY",
                uncertaintyOrLimitations=raw_obs.uncertaintyOrLimitations,
                officerReviewRequirement="MANDATORY_OFFICER_REVIEW",
            )
            validated_observations.append(obs)

        # 11. Determine explicit outcome status
        if not validated_observations:
            final_status = GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS
            logger.info(
                f"OpenAIProvider: Analysis COMPLETED with 0 observations for evidenceId={evidence_id} in {duration_ms}ms"
            )
        else:
            final_status = GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
            logger.info(
                f"OpenAIProvider: Analysis COMPLETED with {len(validated_observations)} observations "
                f"for evidenceId={evidence_id} in {duration_ms}ms"
            )

        return GeminiAnalysisResult(
            evidenceId=eid_str,
            model=self.model_id,
            status=final_status,
            observations=validated_observations,
            rawResponse=parsed_model.model_dump(),
            executionDurationMs=duration_ms,
            requiresOfficerVerification=True,
            isSafeInference=False,
            analysisComponent="VLM",
            modelVersion="openai-v1",
            advisoryStatus="ADVISORY_ONLY",
        )


# Global singleton instance
openai_provider = OpenAIProvider()
