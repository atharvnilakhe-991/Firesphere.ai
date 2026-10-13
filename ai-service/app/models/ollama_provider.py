"""FireSphere.ai — Ollama / Qwen2.5-VL VLM Provider Integration (Phase 13 Vision Analysis).

Provides local Ollama HTTP API adapter, image evidence validation, base64 payload preparation,
strict structured output parsing, bounded retries, and strict compliance boundaries.

Strict Governance Invariants:
1. Disabled by default (fail-closed).
2. Service starts safely without external credentials or network connectivity.
3. No external hosted API calls (strictly local to Ollama endpoint).
4. Independent AI decisions (AI_APPROVED / AI_REJECTED) are strictly prohibited.
5. Does not bypass the existing CV model approval gate.
6. A successful empty result is distinct from failure, invalid output, or inconclusive evidence.
7. Does not fabricate synthetic observations, bounding boxes, or confidence scores.
8. All outputs are strictly advisory; requires mandatory human officer review.
"""

import base64
import json
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
    OllamaConfigurationException,
    OllamaProviderException,
    UnsupportedInputException,
)
from app.core.logging import logger
from app.cv.file_validator import FileValidator
from app.cv.quality import ImageQualityValidator
from app.intelligence.evidence_validator import EvidenceValidator
from app.models.gemini_schemas import (
    SUPPORTED_HAZARD_CATEGORIES,
    SUPPORTED_OBJECT_CATEGORIES,
    VALID_SEVERITIES,
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
    GeminiRawObservation,
    GeminiRawResponse,
    generate_verification_guidance,
    parse_and_validate_bounding_box,
)
from app.models.ollama_prompts import (
    OLLAMA_INSPECTION_PROMPT,
    OLLAMA_SYSTEM_INSTRUCTION,
)
from app.models.vlm_provider import BaseVLMProvider
from app.storage.evidence_client import evidence_client


class OllamaQwenProvider(BaseVLMProvider):
    """Dedicated provider adapter for local Ollama Vision-Language Models (e.g. qwen2.5vl:3b).

    Encapsulates local HTTP client lifecycle, configurable base URL, model selection,
    bounded retries, image evidence validation, base64 payload preparation,
    structured output schema parsing, and normalized result conversion.
    """

    def __init__(
        self,
        settings_obj: Optional[Settings] = None,
        client: Optional[Any] = None,
    ):
        """Initializes Ollama provider adapter. Supports dependency injection for offline testing.

        Does NOT initiate network connections during initialization.
        """
        self._settings = settings_obj or settings
        self.enabled: bool = getattr(self._settings, "ollama_enabled", False)
        self.base_url: str = getattr(
            self._settings, "ollama_base_url", "http://127.0.0.1:11434"
        ).rstrip("/")
        self.model_id: str = getattr(self._settings, "ollama_model", "qwen2.5vl:3b")
        self.timeout_seconds: int = getattr(self._settings, "ollama_timeout_seconds", 60)
        self.connect_timeout_seconds: int = getattr(
            self._settings, "ollama_connect_timeout_seconds", 10
        )
        self.max_retries: int = getattr(self._settings, "ollama_max_retries", 2)
        self._client: Optional[Any] = client
        self._initialized: bool = client is not None

    @property
    def provider_name(self) -> str:
        """Returns canonical provider identity."""
        return "ollama"

    def is_enabled(self) -> bool:
        """Returns whether the Ollama provider is enabled in configuration."""
        return self.enabled

    def is_configured(self) -> bool:
        """Returns whether valid base URL and model ID are configured."""
        return bool(
            self.base_url
            and self.base_url.strip()
            and self.model_id
            and self.model_id.strip()
        )

    def is_ready(self) -> bool:
        """Returns whether the provider is enabled, configured, and initialized."""
        return self.enabled and self.is_configured() and self._initialized

    def get_status(self) -> str:
        """Returns component readiness status for system health checks.

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
        """Returns configured model ID."""
        return self.model_id

    def get_provider_info(self) -> Dict[str, Any]:
        """Returns public provider metadata. NEVER returns secrets, tokens, or private paths."""
        return {
            "provider": "ollama",
            "model": self.model_id,
            "modelVersion": "ollama-qwen2.5vl-v1",
            "enabled": self.enabled,
            "status": self.get_status(),
            "baseUrl": self.base_url,
            "timeoutSeconds": self.timeout_seconds,
            "connectTimeoutSeconds": self.connect_timeout_seconds,
            "maxRetries": self.max_retries,
            "productionApproved": False,
            "governancePolicy": "ADVISORY_ONLY_HUMAN_OFFICER_VERIFICATION_MANDATORY",
        }

    def initialize(self) -> Optional[Any]:
        """Safely initializes the Ollama HTTP client.

        Raises:
            OllamaConfigurationException: If provider is enabled but base URL is invalid.
            OllamaProviderException: If client instantiation fails.
        """
        if not self.enabled:
            logger.info("OllamaQwenProvider: Provider is disabled (OLLAMA_ENABLED=false). Initialization skipped.")
            return None

        if not self.is_configured():
            logger.error("OllamaQwenProvider: OLLAMA_ENABLED=true but base URL or model is missing.")
            raise OllamaConfigurationException(
                message="Ollama provider is enabled but OLLAMA_BASE_URL or OLLAMA_MODEL is missing or empty."
            )

        if self._client is not None:
            return self._client

        try:
            self._client = httpx.Client(
                base_url=self.base_url,
                timeout=httpx.Timeout(
                    float(self.timeout_seconds),
                    connect=float(self.connect_timeout_seconds),
                ),
            )
            self._initialized = True
            logger.info(
                f"OllamaQwenProvider: Initialized HTTP client | BaseUrl={self.base_url} | "
                f"Model={self.model_id} | Timeout={self.timeout_seconds}s"
            )
            return self._client
        except Exception as e:
            err_type = type(e).__name__
            logger.error(f"OllamaQwenProvider: Client initialization failed ({err_type})")
            raise OllamaProviderException(
                message=f"Ollama client initialization failed: {err_type}."
            ) from None

    def get_client(self) -> Optional[Any]:
        """Returns the initialized client instance, or None if not initialized."""
        return self._client

    def _call_ollama_with_retries(
        self,
        messages: List[Dict[str, Any]],
    ) -> Any:
        """Executes local Ollama chat request with bounded retries for transient errors."""
        if self._client is None:
            raise OllamaProviderException("Ollama client is not initialized.")

        max_attempts = max(1, self.max_retries)
        last_exception: Optional[Exception] = None

        payload = {
            "model": self.model_id,
            "messages": messages,
            "format": "json",
            "stream": False,
            "options": {
                "temperature": 0.1,
            },
        }

        for attempt in range(1, max_attempts + 1):
            try:
                # Support httpx.Client or mock transport
                if hasattr(self._client, "post"):
                    resp = self._client.post(
                        "/api/chat",
                        json=payload,
                    )
                    if hasattr(resp, "status_code") and resp.status_code >= 400:
                        raise httpx.HTTPStatusError(
                            message=f"HTTP {resp.status_code}",
                            request=getattr(resp, "request", None),
                            response=resp,
                        )
                    return resp
                else:
                    raise OllamaProviderException("Injected client does not support HTTP post interface.")

            except Exception as exc:
                last_exception = exc
                err_str = str(exc).lower()
                status_code = getattr(exc, "status_code", None)
                if status_code is None and hasattr(exc, "response") and hasattr(exc.response, "status_code"):
                    status_code = exc.response.status_code

                # Permanent non-retryable errors: 400 Bad Request, 404 Model Not Found, 422 Unprocessable
                is_permanent = (
                    status_code in (400, 404, 422)
                    or "not found" in err_str
                    or "invalid_request" in err_str
                )
                if is_permanent:
                    logger.error(f"OllamaQwenProvider: Permanent non-retryable error ({type(exc).__name__}: {status_code or exc}); aborting retries.")
                    raise

                if attempt < max_attempts:
                    backoff = min(1.0 * (2 ** (attempt - 1)), 5.0)
                    logger.warning(
                        f"OllamaQwenProvider: Transient error on attempt {attempt}/{max_attempts} "
                        f"({type(exc).__name__}). Retrying in {backoff:.1f}s..."
                    )
                    time.sleep(backoff)
                else:
                    logger.error(
                        f"OllamaQwenProvider: All {max_attempts} attempts exhausted ({type(exc).__name__})."
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

        if not raw_bytes or len(raw_bytes) == 0:
            raise UnsupportedInputException(f"Evidence payload for {evidence_id} is empty (0 bytes).")

        # 1. MIME and file size validation
        mime_type, _ = FileValidator.validate_payload(evidence_id, raw_bytes)

        # 2. Matrix decoding and dimensional limits
        np_arr = np.frombuffer(raw_bytes, dtype=np.uint8)
        img_bgr = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        if img_bgr is None or img_bgr.size == 0:
            raise ImageDecodeException(f"Evidence {evidence_id} could not be decoded into a valid image matrix.")

        h, w = img_bgr.shape[:2]
        quality_report = ImageQualityValidator.evaluate(img_bgr)
        if not quality_report.isAcceptable:
            raise ImageQualityException(
                message=f"Evidence {evidence_id} failed critical quality checks: {'; '.join(quality_report.errors)}"
            )

        return raw_bytes, mime_type, (w, h)

    def analyze_evidence(
        self,
        evidence_id: str,
        image_data: Optional[Union[bytes, np.ndarray, Any]] = None,
        context: Optional[Dict[str, Any]] = None,
    ) -> GeminiAnalysisResult:
        """Executes independent multimodal visual inspection analysis using local Ollama.

        Always returns a validated GeminiAnalysisResult envelope.
        Catches internal errors and returns safe failure representations.
        """
        start_time = time.perf_counter()
        eid_str = str(evidence_id)

        # 1. Fail-closed check: Provider disabled
        if not self.enabled:
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.PROVIDER_DISABLED,
                observations=[],
                error="Ollama provider is disabled by configuration (OLLAMA_ENABLED=false).",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="ollama-qwen2.5vl-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 2. Configuration check
        if not self.is_configured():
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.PROVIDER_NOT_CONFIGURED,
                observations=[],
                error="Ollama provider is not configured. OLLAMA_BASE_URL or OLLAMA_MODEL is empty.",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="ollama-qwen2.5vl-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 3. Client initialization
        try:
            self.initialize()
        except Exception as init_err:
            logger.error(f"OllamaQwenProvider: Client initialization failed for evidenceId={eid_str}: {init_err}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Client initialization failed: {type(init_err).__name__}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="ollama-qwen2.5vl-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 4. Resolve and strictly validate image payload
        try:
            image_bytes, mime_type, (img_w, img_h) = self._resolve_and_validate_image(
                evidence_id=eid_str,
                image_data=image_data,
            )
        except (UnsupportedInputException, EvidenceOversizedException, ImageDecodeException, ImageQualityException) as img_err:
            logger.warning(f"OllamaQwenProvider: Image validation rejected for evidenceId={eid_str}: {img_err.message}")
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
                modelVersion="ollama-qwen2.5vl-v1",
                advisoryStatus="ADVISORY_ONLY",
            )
        except Exception as unk_err:
            logger.error(f"OllamaQwenProvider: Unexpected image validation error for evidenceId={eid_str}: {unk_err}")
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
                modelVersion="ollama-qwen2.5vl-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 5. Build base64 payload and chat messages for Ollama API
        try:
            b64_img = base64.b64encode(image_bytes).decode("utf-8")
            messages = [
                {
                    "role": "system",
                    "content": OLLAMA_SYSTEM_INSTRUCTION,
                },
                {
                    "role": "user",
                    "content": OLLAMA_INSPECTION_PROMPT,
                    "images": [b64_img],
                },
            ]
        except Exception as prep_err:
            logger.error(f"OllamaQwenProvider: Error preparing request messages ({type(prep_err).__name__})")
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
                modelVersion="ollama-qwen2.5vl-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 6. Execute model inference with bounded retries
        try:
            raw_response_obj = self._call_ollama_with_retries(messages=messages)
        except Exception as call_err:
            err_type = type(call_err).__name__
            status_code = getattr(call_err, "status_code", None)
            if status_code is None and hasattr(call_err, "response") and hasattr(call_err.response, "status_code"):
                status_code = call_err.response.status_code
            err_desc = (
                f"{err_type} (HTTP {status_code})"
                if isinstance(status_code, int) and 100 <= status_code <= 599
                else err_type
            )
            logger.error(f"OllamaQwenProvider: API call failed for evidenceId={eid_str} ({err_desc})")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                observations=[],
                error=f"Ollama API request failed: {err_desc}",
                executionDurationMs=round((time.perf_counter() - start_time) * 1000, 2),
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="ollama-qwen2.5vl-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)

        # 7. Extract raw JSON text from response
        raw_text: Optional[str] = None
        try:
            if hasattr(raw_response_obj, "json") and callable(raw_response_obj.json):
                j = raw_response_obj.json()
                if isinstance(j, dict):
                    if "message" in j and isinstance(j["message"], dict) and "content" in j["message"]:
                        raw_text = j["message"]["content"]
                    elif "response" in j:
                        raw_text = j["response"]
            elif isinstance(raw_response_obj, dict):
                if "message" in raw_response_obj and isinstance(raw_response_obj["message"], dict):
                    raw_text = raw_response_obj["message"].get("content")
                elif "response" in raw_response_obj:
                    raw_text = raw_response_obj.get("response")
        except Exception as ext_err:
            logger.warning(f"OllamaQwenProvider: Failed to extract response text ({ext_err})")

        if not raw_text or not str(raw_text).strip():
            logger.warning(f"OllamaQwenProvider: Empty content received for evidenceId={evidence_id}")
            return GeminiAnalysisResult(
                evidenceId=evidence_id,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE,
                observations=[],
                error="Ollama returned an empty response with no content.",
                executionDurationMs=duration_ms,
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="ollama-qwen2.5vl-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 8. Parse and validate structured output against FireSphere schema
        clean_text = str(raw_text).strip()
        parsed_model: Optional[GeminiRawResponse] = None

        try:
            parsed_model = GeminiRawResponse.model_validate_json(clean_text)
        except Exception:
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
            except Exception as parse_fallback_err:
                logger.warning(
                    f"OllamaQwenProvider: Schema validation failed for evidenceId={eid_str}: {parse_fallback_err}"
                )
                return GeminiAnalysisResult(
                    evidenceId=eid_str,
                    model=self.model_id,
                    status=GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE,
                    observations=[],
                    error=f"Malformed model response; failed schema validation: {type(parse_fallback_err).__name__}",
                    executionDurationMs=duration_ms,
                    requiresOfficerVerification=True,
                    isSafeInference=False,
                    analysisComponent="VLM",
                    modelVersion="ollama-qwen2.5vl-v1",
                    advisoryStatus="ADVISORY_ONLY",
                )

        # 9. Handle Inconclusive evidence
        if parsed_model.isInconclusive:
            reason = parsed_model.inconclusiveReason or "Model indicated visual evidence is inconclusive."
            logger.info(f"OllamaQwenProvider: Evidence marked INCONCLUSIVE for evidenceId={eid_str} | Reason: {reason}")
            return GeminiAnalysisResult(
                evidenceId=eid_str,
                model=self.model_id,
                status=GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE,
                observations=[],
                rawResponse=parsed_model.model_dump(),
                inconclusiveReason=reason,
                executionDurationMs=duration_ms,
                requiresOfficerVerification=True,
                isSafeInference=False,
                analysisComponent="VLM",
                modelVersion="ollama-qwen2.5vl-v1",
                advisoryStatus="ADVISORY_ONLY",
            )

        # 10. Map structured observations
        mapped_observations: List[GeminiObservation] = []
        for raw_obs in parsed_model.observations:
            # Normalization of taxonomy
            obj_cat = (raw_obs.objectCategory or "GENERIC_FIRE_SAFETY_DEFECT").strip().upper()
            if obj_cat not in SUPPORTED_OBJECT_CATEGORIES:
                obj_cat = "GENERIC_FIRE_SAFETY_DEFECT"

            hazard_cat = raw_obs.potentialHazardCategory
            if hazard_cat:
                hazard_cat = hazard_cat.strip().upper()
                if hazard_cat not in SUPPORTED_HAZARD_CATEGORIES:
                    hazard_cat = "GENERIC_FIRE_SAFETY_DEFECT"

            # Parse bounding box without fabricating synthetic boxes
            bbox = parse_and_validate_bounding_box(
                raw_obs.box_2d,
                image_dims=(img_w, img_h),
                coordinate_convention="gemini_0_1000",
            )

            # Validate confidence: retain only if valid probability, never invent
            valid_conf: Optional[float] = None
            if raw_obs.confidence is not None:
                try:
                    c = float(raw_obs.confidence)
                    if 0.0 <= c <= 1.0 and math.isfinite(c):
                        valid_conf = round(c, 4)
                except (ValueError, TypeError):
                    valid_conf = None

            # Severity proposal normalization
            sev_prop = raw_obs.severityProposal
            if sev_prop:
                sev_prop = sev_prop.strip().upper()
                if sev_prop not in VALID_SEVERITIES:
                    sev_prop = "INFORMATIONAL"

            guidance = generate_verification_guidance(obj_cat, hazard_cat)

            obs = GeminiObservation(
                observationId=str(uuid.uuid4()),
                imageReference=eid_str,
                objectCategory=obj_cat,
                potentialHazardCategory=hazard_cat,
                observationDescription=raw_obs.observationDescription or "",
                evidenceStatus=raw_obs.evidenceStatus or "DETECTED",
                boundingBox=bbox,
                confidence=valid_conf,
                severityProposal=sev_prop,
                modelName=self.model_id,
                modelVersion="ollama-qwen2.5vl-v1",
                analysisStatus="COMPLETED",
                uncertaintyOrLimitations=raw_obs.uncertaintyOrLimitations or [],
                officerReviewRequirement="MANDATORY_OFFICER_REVIEW",
                evidenceId=eid_str,
                analysisComponent="VLM",
                observationType=hazard_cat or obj_cat,
                observationSummary=raw_obs.observationDescription or "",
                visualEvidence=raw_obs.observationDescription or "",
                verificationGuidance=guidance,
                advisoryStatus="ADVISORY_ONLY",
            )
            mapped_observations.append(obs)

        # 11. Final lifecycle status determination
        final_status = (
            GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
            if len(mapped_observations) > 0
            else GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS
        )

        return GeminiAnalysisResult(
            evidenceId=eid_str,
            model=self.model_id,
            status=final_status,
            observations=mapped_observations,
            rawResponse=parsed_model.model_dump(),
            executionDurationMs=duration_ms,
            requiresOfficerVerification=True,
            isSafeInference=False,
            analysisComponent="VLM",
            modelVersion="ollama-qwen2.5vl-v1",
            advisoryStatus="ADVISORY_ONLY",
        )


# Global singleton instance
ollama_provider = OllamaQwenProvider()
