"""FireSphere.ai — Phase 3 Deterministic Result Reconciliation & Conflict Resolution.

Coordinates Gemini VLM visual reasoning outputs with Computer Vision baseline detections.
Implements an auditable, deterministic 12-case reconciliation matrix that preserves
source observations, records model agreements/discrepancies, and guarantees that no AI
model autonomously certifies Fire NOC compliance.
"""

from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Set, Tuple, Union
import uuid

from app.core.exceptions import InvalidEvidenceException
from app.core.logging import logger
from app.models.gemini_schemas import (
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
    parse_and_validate_bounding_box,
)
from app.schemas.finding import BoundingBox, Finding, FindingSeverity
from app.schemas.reconciliation import (
    ComponentAnalysisStatus,
    ConflictDetail,
    ConflictType,
    ProviderProvenance,
    ReconciliationStatus,
    map_component_status,
)


class BBoxValidationResult(tuple):
    """Result of bounding box validation, supporting tuple unpacking and dict access."""

    def __new__(cls, box: Optional[BoundingBox], status: str):
        return super().__new__(cls, (box, status))

    @property
    def box(self) -> Optional[BoundingBox]:
        return self[0]

    @property
    def validationStatus(self) -> str:
        return self[1]

    def __getitem__(self, item: Any) -> Any:
        if item == "box":
            return self[0]
        if item == "validationStatus":
            return self[1]
        return super().__getitem__(item)

    def get(self, key: str, default: Any = None) -> Any:
        if key == "box":
            return self[0]
        if key == "validationStatus":
            return self[1]
        return default


class ReconciliationExecutionResult(tuple):
    """Result of cross-model reconciliation.

    Behaves as a 3-tuple (findings, status, conflict) for full backward compatibility
    with existing callers, while exposing properties (findings, status, conflicts, explanation,
    isSafeInference, geminiFindings, vlmFindings) for Stage 8 unified contract.
    """

    def __new__(
        cls,
        findings: List[Finding],
        status: Any,
        conflict: Optional[Any] = None,
        explanation: Optional[str] = None,
        evidence_id: Optional[Any] = None,
    ):
        instance = super().__new__(cls, (findings, status, conflict))
        instance._explanation = explanation
        instance._evidence_id = evidence_id
        return instance

    @property
    def findings(self) -> List[Finding]:
        return self[0]

    @property
    def status(self) -> Any:
        return self[1]

    @property
    def conflict(self) -> Optional[Any]:
        return self[2]

    @property
    def conflicts(self) -> List[Any]:
        return [self[2]] if self[2] is not None else []

    @property
    def explanation(self) -> str:
        if self._explanation:
            return self._explanation
        if self.conflict and getattr(self.conflict, "explanation", None):
            return self.conflict.explanation
        if self.conflict and getattr(self.conflict, "description", None):
            return self.conflict.description
        if self.findings:
            return self.findings[0].explanation
        return f"Reconciliation completed with status {self.status}."

    @property
    def isSafeInference(self) -> bool:
        return False

    @property
    def geminiFindings(self) -> List[Finding]:
        return [f for f in self[0] if getattr(f, "sourceType", None) == "VLM"]

    @property
    def vlmFindings(self) -> List[Finding]:
        return self.geminiFindings

    @property
    def cvFindings(self) -> List[Finding]:
        return [f for f in self[0] if getattr(f, "sourceType", None) == "CV"]

    @property
    def evidenceId(self) -> Optional[Any]:
        return self._evidence_id


class ResultReconciler:
    """Deterministic cross-provider observation reconciler and conflict resolution engine."""

    def __init__(self, iou_threshold: float = 0.40, enable_fuzzy_matching: bool = True):
        self.iou_threshold = iou_threshold
        self.enable_fuzzy_matching = enable_fuzzy_matching

    SEVERITY_MAPPING = {
        "CRITICAL": FindingSeverity.CRITICAL,
        "HIGH": FindingSeverity.HIGH,
        "MEDIUM": FindingSeverity.MEDIUM,
        "LOW": FindingSeverity.LOW,
        "INFORMATIONAL": FindingSeverity.INFORMATIONAL,
    }

    @staticmethod
    def compute_iou(box1: Optional[BoundingBox], box2: Optional[BoundingBox]) -> float:
        """Calculates Intersection over Union (IoU) between two normalized bounding boxes."""
        if not box1 or not box2:
            return 0.0

        x1 = max(box1.xMin, box2.xMin)
        y1 = max(box1.yMin, box2.yMin)
        x2 = min(box1.xMax, box2.xMax)
        y2 = min(box1.yMax, box2.yMax)

        if x2 <= x1 or y2 <= y1:
            return 0.0

        intersection = (x2 - x1) * (y2 - y1)
        area1 = max(0.0, (box1.xMax - box1.xMin) * (box1.yMax - box1.yMin))
        area2 = max(0.0, (box2.xMax - box2.xMin) * (box2.yMax - box2.yMin))
        union = area1 + area2 - intersection

        return intersection / union if union > 0 else 0.0

    @classmethod
    def validate_and_normalize_bbox(cls, box_input: Any) -> BBoxValidationResult:
        """Validates bounding box geometry, scale, and ordering defensively.

        Returns:
            BBoxValidationResult of (Optional[BoundingBox], validation_status: str)
            validation_status is "VALID" or "NORMALIZED" if box is acceptable,
            or "INVALID_COORDINATES" if coordinates are inverted, non-finite, or out-of-bounds.
        """
        if box_input is None:
            return BBoxValidationResult(None, "VALID")

        try:
            if isinstance(box_input, BoundingBox):
                return BBoxValidationResult(box_input, "VALID")

            import math
            # Defensive check for list/tuple of 4
            if isinstance(box_input, (list, tuple)) and len(box_input) == 4:
                if not all(math.isfinite(float(c)) for c in box_input):
                    return BBoxValidationResult(None, "INVALID_COORDINATES")
                y0, x0, y1, x1 = float(box_input[0]), float(box_input[1]), float(box_input[2]), float(box_input[3])
                # Check for inverted coordinates
                if y0 >= y1 or x0 >= x1:
                    return BBoxValidationResult(None, "INVALID_COORDINATES")
                # Check for extreme out of bounds
                if min(y0, x0, y1, x1) < -50.0 or max(y0, x0, y1, x1) > 1050.0:
                    return BBoxValidationResult(None, "INVALID_COORDINATES")

            parsed = parse_and_validate_bounding_box(box_input)
            if parsed is None:
                return BBoxValidationResult(None, "INVALID_COORDINATES")

            # Check if input was in 0-1000 scale that required normalization
            is_normalized = False
            if isinstance(box_input, (list, tuple)) and any(float(c) > 1.05 for c in box_input):
                is_normalized = True
            elif isinstance(box_input, dict) and any(float(v) > 1.05 for v in box_input.values() if isinstance(v, (int, float))):
                is_normalized = True

            status = "NORMALIZED" if is_normalized else "VALID"
            return BBoxValidationResult(parsed, status)
        except Exception:
            return BBoxValidationResult(None, "INVALID_COORDINATES")

    @classmethod
    def objects_match(cls, cat1: str, cat2: str) -> bool:
        """Determines whether two defect or perception categories represent the same underlying physical object."""
        c1 = cat1.upper().replace("-", "_").strip()
        c2 = cat2.upper().replace("-", "_").strip()

        if c1 == c2:
            return True

        # Semantic equivalence patterns for physical equipment
        if "EXTINGUISHER" in c1 and "EXTINGUISHER" in c2:
            return True
        if ("EXIT" in c1 and "SIGN" in c1) and ("EXIT" in c2 and "SIGN" in c2):
            return True
        if ("DOOR" in c1 or "EXIT" in c1 or "OBSTACLE" in c1) and ("DOOR" in c2 or "EXIT" in c2 or "OBSTACLE" in c2):
            return True
        if "PANEL" in c1 and "PANEL" in c2:
            return True
        if "SPRINKLER" in c1 and "SPRINKLER" in c2:
            return True
        if "HOSE" in c1 and "HOSE" in c2:
            return True

        return False

    @classmethod
    def hazards_match(cls, cat1: str, cat2: str) -> bool:
        """Determines whether two observations agree on the exact hazard/defect classification."""
        c1 = cat1.upper().replace("-", "_").strip()
        c2 = cat2.upper().replace("-", "_").strip()
        return c1 == c2

    @classmethod
    def hazards_conflict(cls, cat1: str, cat2: str) -> bool:
        """Determines whether two hazard classifications on the same object are contradictory."""
        c1 = cat1.upper().replace("-", "_").strip()
        c2 = cat2.upper().replace("-", "_").strip()
        if c1 == c2:
            return False
        # Specific contradictory pairs on same object:
        # e.g. Equipment missing (absent) vs equipment present but blocked/damaged/expired
        if ("MISSING" in c1 and any(k in c2 for k in ("BLOCKED", "DAMAGED", "EXPIRED", "OBSTRUCTED"))) or \
           ("MISSING" in c2 and any(k in c1 for k in ("BLOCKED", "DAMAGED", "EXPIRED", "OBSTRUCTED"))):
            return True
        return False

    # Backward compatibility alias
    categories_match = objects_match

    @classmethod
    def map_severity(cls, raw_sev: Optional[str]) -> FindingSeverity:
        """Maps arbitrary raw severity strings to valid FindingSeverity enum safely."""
        if not raw_sev:
            return FindingSeverity.MEDIUM
        return cls.SEVERITY_MAPPING.get(raw_sev.strip().upper(), FindingSeverity.MEDIUM)

    @classmethod
    def _resolve_vlm_label_and_models(
        cls,
        gem_obs: Optional[GeminiObservation],
        vlm_model_info: Optional[Dict[str, str]],
    ) -> Tuple[str, str, str]:
        """Resolves trustworthy human title, model ID, and model version for VLM findings."""
        v_info = vlm_model_info or {}
        prov = (v_info.get("provider") or "").strip().lower()
        obs_model = getattr(gem_obs, "modelName", None) if gem_obs else None
        obs_version = getattr(gem_obs, "modelVersion", None) if gem_obs else None

        if prov in ("ollama", "qwen", "ollama-qwen", "ollama_qwen"):
            vlm_label = "Ollama/Qwen VLM"
            actual_model = obs_model if (obs_model and "gemini" not in obs_model.lower()) else None
            actual_version = obs_version if (obs_version and "gemini" not in obs_version.lower()) else None
            finding_model = actual_model or v_info.get("model") or "qwen2.5vl:3b"
            finding_version = actual_version or v_info.get("version") or v_info.get("modelVersion") or "ollama-qwen2.5vl-v1"
        elif prov == "openai":
            vlm_label = "OpenAI VLM"
            actual_model = obs_model if (obs_model and "gemini" not in obs_model.lower()) else None
            actual_version = obs_version if (obs_version and "gemini" not in obs_version.lower()) else None
            finding_model = actual_model or v_info.get("model") or "gpt-4o-mini"
            finding_version = actual_version or v_info.get("version") or v_info.get("modelVersion") or "openai-gpt4o-mini-v1"
        elif prov in ("google-gemini", "gemini"):
            vlm_label = "Gemini VLM"
            finding_model = "google-gemini"
            finding_version = obs_version or v_info.get("version") or v_info.get("modelVersion") or "gemini-2.5-flash"
        else:
            # Check candidate model names if provider identity is omitted
            candidate_m = (obs_model or v_info.get("model") or "").strip().lower()
            if "qwen" in candidate_m or "ollama" in candidate_m:
                vlm_label = "Ollama/Qwen VLM"
                finding_model = obs_model or v_info.get("model") or "qwen2.5vl:3b"
                finding_version = obs_version or "ollama-qwen2.5vl-v1"
            elif "gpt" in candidate_m or "openai" in candidate_m:
                vlm_label = "OpenAI VLM"
                finding_model = obs_model or v_info.get("model") or "gpt-4o-mini"
                finding_version = obs_version or "openai-gpt4o-mini-v1"
            elif "gemini" in candidate_m:
                vlm_label = "Gemini VLM"
                finding_model = "google-gemini"
                finding_version = obs_version or "gemini-2.5-flash"
            elif "unknown" in candidate_m:
                vlm_label = "VLM"
                finding_model = "unknown"
                finding_version = obs_version or "unknown"
            else:
                # Active local VLM default is Ollama running qwen2.5vl:3b
                vlm_label = "Ollama/Qwen VLM"
                finding_model = obs_model or v_info.get("model") or "qwen2.5vl:3b"
                finding_version = obs_version or v_info.get("version") or "ollama-qwen2.5vl-v1"

        return vlm_label, finding_model, finding_version

    @classmethod
    def reconcile(
        cls,
        evidence_id: Optional[Union[uuid.UUID, str]] = None,
        inspection_id: Optional[Union[uuid.UUID, str]] = None,
        gemini_result: Optional[GeminiAnalysisResult] = None,
        cv_detections: Optional[List[Any]] = None,
        cv_status: str = "SUCCESS",
        cv_model_info: Optional[Dict[str, str]] = None,
        gemini_model_info: Optional[Dict[str, str]] = None,
        is_test_mode: bool = False,
        tenant_id: Optional[str] = "default",
        analysis_run_id: Optional[uuid.UUID] = None,
        vlm_result: Optional[GeminiAnalysisResult] = None,
        vlm_model_info: Optional[Dict[str, str]] = None,
        **kwargs: Any,
    ) -> Any:
        """Reconciles observations across local VLM and CV models for a single evidence item.

        Handles all defined cases deterministically preserving source provenance and uncertainty.
        Returns:
            ReconciliationExecutionResult acting as (List[Finding], ReconciliationStatus, Optional[ConflictDetail])
        """
        run_id = analysis_run_id or uuid.uuid4()
        findings: List[Finding] = []

        active_vlm_res = vlm_result if vlm_result is not None else gemini_result
        active_vlm_info = dict(vlm_model_info or gemini_model_info or {})
        if "vlm_provider_name" in kwargs:
            active_vlm_info["provider"] = kwargs["vlm_provider_name"]
        if "vlm_model_name" in kwargs:
            active_vlm_info["model"] = kwargs["vlm_model_name"]

        active_cv_dets = cv_detections if cv_detections is not None else kwargs.get("cv_findings", [])
        active_cv_info = dict(cv_model_info or {})
        cv_status = kwargs.get("cv_status", cv_status)

        # Defensive resolution and strict validation of evidence_id
        if not evidence_id:
            if active_vlm_res and getattr(active_vlm_res, "evidenceId", None):
                try:
                    evidence_id = uuid.UUID(str(active_vlm_res.evidenceId))
                except Exception:
                    evidence_id = active_vlm_res.evidenceId
            elif active_cv_dets and getattr(active_cv_dets[0], "evidenceId", None):
                try:
                    evidence_id = uuid.UUID(str(active_cv_dets[0].evidenceId))
                except Exception:
                    evidence_id = active_cv_dets[0].evidenceId
            elif "evidenceId" in kwargs:
                try:
                    evidence_id = uuid.UUID(str(kwargs["evidenceId"]))
                except Exception:
                    evidence_id = kwargs["evidenceId"]
            else:
                evidence_id = None
        elif isinstance(evidence_id, str):
            try:
                evidence_id = uuid.UUID(evidence_id.strip())
            except Exception:
                raise InvalidEvidenceException(f"Invalid evidence UUID: '{evidence_id}'")

        if not evidence_id or (isinstance(evidence_id, str) and not evidence_id.strip()):
            raise InvalidEvidenceException("Evidence identifier cannot be empty or null.")

        # Strict validation of inspection_id (Section 4.A, Section 10.K, 10.L)
        raw_iid = inspection_id if inspection_id is not None else kwargs.get("inspectionId")
        if raw_iid is None or (isinstance(raw_iid, str) and not raw_iid.strip()):
            raise InvalidEvidenceException("Inspection identifier cannot be empty or null.")

        if isinstance(raw_iid, str):
            try:
                inspection_id = uuid.UUID(raw_iid.strip())
            except Exception:
                raise InvalidEvidenceException(f"Invalid inspection UUID: '{raw_iid}'")
        elif isinstance(raw_iid, uuid.UUID):
            inspection_id = raw_iid
        else:
            raise InvalidEvidenceException(f"Invalid inspection identifier type: {type(raw_iid).__name__}")

        def _ret(f_list, s_val, c_val=None, expl=None):
            return ReconciliationExecutionResult(f_list, s_val, c_val, expl, str(evidence_id))

        if active_vlm_res and getattr(active_vlm_res, "evidenceId", None):
            if str(active_vlm_res.evidenceId).lower() != str(evidence_id).lower():
                logger.error(
                    f"EVIDENCE_MISMATCH_REJECTION | VLM evidenceId={active_vlm_res.evidenceId} "
                    f"does not match requested evidenceId={evidence_id}"
                )
                raise InvalidEvidenceException(
                    f"Evidence UUID mismatch in VLM result: {active_vlm_res.evidenceId} != {evidence_id}"
                )

        for c_det in active_cv_dets:
            det_eid = getattr(c_det, "evidenceId", None)
            if det_eid and str(det_eid).lower() != str(evidence_id).lower():
                logger.error(
                    f"EVIDENCE_MISMATCH_REJECTION | CV detection evidenceId={det_eid} "
                    f"does not match requested evidenceId={evidence_id}"
                )
                raise InvalidEvidenceException(
                    f"Evidence UUID mismatch in CV detection: {det_eid} != {evidence_id}"
                )

        # 1. Inspect Provider Statuses and Map to Normalized Semantic Statuses
        vlm_status = active_vlm_res.status if active_vlm_res else GeminiAnalysisStatus.PROVIDER_DISABLED
        vlm_observations = active_vlm_res.observations if active_vlm_res else []

        vlm_is_inconclusive = vlm_status == GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE
        vlm_is_failed = vlm_status in (
            GeminiAnalysisStatus.ANALYSIS_FAILED,
            GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE,
        )
        vlm_is_disabled = vlm_status in (
            GeminiAnalysisStatus.GEMINI_DISABLED,
            GeminiAnalysisStatus.PROVIDER_DISABLED,
        )
        vlm_not_configured = vlm_status in (
            GeminiAnalysisStatus.GEMINI_NOT_CONFIGURED,
            GeminiAnalysisStatus.PROVIDER_NOT_CONFIGURED,
        )
        vlm_has_findings = len(vlm_observations) > 0

        cv_is_failed = cv_status in ("FAILED", "ERROR", "CHECKPOINT_LOAD_FAILED")
        cv_is_disabled = cv_status in ("DISABLED", "NOT_CONFIGURED")
        cv_is_gated = cv_status == "NOT_APPROVED_GATED"
        cv_has_findings = len(active_cv_dets) > 0

        vlm_exec_status_str = vlm_status.value if hasattr(vlm_status, "value") else str(vlm_status)
        vlm_comp_status = map_component_status(
            execution_status=vlm_exec_status_str,
            observation_count=len(vlm_observations),
            error=active_vlm_res.error if active_vlm_res else None,
        )
        cv_comp_status = map_component_status(
            execution_status=cv_status,
            observation_count=len(active_cv_dets),
        )

        # Build baseline provenance items with auditable component statuses
        vlm_label, vlm_default_model, vlm_default_version = cls._resolve_vlm_label_and_models(None, active_vlm_info)
        detected_prov = active_vlm_info.get("provider")
        if not detected_prov and active_vlm_res and getattr(active_vlm_res, "model", None):
            res_m = str(active_vlm_res.model).lower()
            if "qwen" in res_m or "ollama" in res_m:
                detected_prov = "ollama"
            elif "gpt" in res_m or "openai" in res_m:
                detected_prov = "openai"
            elif "gemini" in res_m:
                detected_prov = "google-gemini"
            elif "unknown" in res_m:
                detected_prov = "unknown"
        if not detected_prov:
            detected_prov = "ollama"

        vlm_prov_base = {
            "sourceType": "VLM",
            "providerName": detected_prov,
            "modelId": active_vlm_info.get("model", vlm_default_model),
            "modelVersion": active_vlm_info.get("version") or active_vlm_info.get("modelVersion") or vlm_default_version,
            "evidenceId": str(evidence_id),
            "executionStatus": vlm_exec_status_str,
            "componentStatus": vlm_comp_status.value,
            "calibrationStatus": "NOT_CALIBRATED",
            "coordinateConvention": "gemini_0_1000",
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        if active_vlm_res and active_vlm_res.error:
            vlm_prov_base["error"] = active_vlm_res.error

        cv_prov_base = {
            "sourceType": "CV",
            "providerName": "firesphere-cv-baseline",
            "modelId": active_cv_info.get("model", "firesphere-perception-baseline-v0.8.0"),
            "modelVersion": active_cv_info.get("version", "0.8.0"),
            "evidenceId": str(evidence_id),
            "executionStatus": cv_status,
            "componentStatus": cv_comp_status.value,
            "calibrationStatus": "NOT_CALIBRATED",
            "coordinateConvention": "normalized",
            "checkpointMetadata": {
                "checkpointSha256": active_cv_info.get(
                    "checkpointSha256", "380b08db911c42287950c45166f81e35dd74b97d268294a50d27ecf01fbe5b84"
                ),
                "approvalStatus": "NOT_APPROVED",
            },
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

        # Backward compatibility alias
        gemini_prov_base = vlm_prov_base

        # =========================================================================
        # CASE 9: Both providers failed or non-operational
        # =========================================================================
        if (vlm_is_failed or vlm_not_configured) and cv_is_failed:
            logger.error(f"RECONCILIATION_CASE_9 | Both providers failed for evidenceId={evidence_id}")
            conflict = ConflictDetail(
                conflictType=ConflictType.UNRESOLVED_DISAGREEMENT,
                description="Both visual analysis providers failed to process the evidence item.",
                geminiObservation=vlm_prov_base,
                vlmObservation=vlm_prov_base,
                cvObservation=cv_prov_base,
            )
            return _ret([], ReconciliationStatus.FAILED, conflict, "Both visual analysis providers failed to process the evidence item.")

        # If CV failed and VLM is disabled/unavailable, overall run has failed
        if cv_is_failed and (vlm_is_disabled or vlm_not_configured):
            logger.error(f"RECONCILIATION_CASE_9B | CV failed and {vlm_label} unavailable for evidenceId={evidence_id}")
            conflict = ConflictDetail(
                conflictType=ConflictType.UNRESOLVED_DISAGREEMENT,
                description=f"CV baseline provider failed and {vlm_label} was unavailable/disabled.",
                geminiObservation=vlm_prov_base,
                vlmObservation=vlm_prov_base,
                cvObservation=cv_prov_base,
            )
            return _ret([], ReconciliationStatus.FAILED, conflict, f"CV baseline provider failed and {vlm_label} was unavailable/disabled.")

        # =========================================================================
        # CASE 8: One provider fails, other succeeds
        # =========================================================================
        if vlm_is_failed and not cv_is_failed:
            logger.warning(f"RECONCILIATION_CASE_8 | {vlm_label} failed, preserving CV outputs for evidenceId={evidence_id}")
            for cv_det in active_cv_dets:
                finding = cls._create_cv_finding(
                    cv_det=cv_det,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[vlm_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.DISCREPANCY.value,
                    conflict_type=ConflictType.UNRESOLVED_DISAGREEMENT.value,
                )
                findings.append(finding)
            conflict = ConflictDetail(
                conflictType=ConflictType.UNRESOLVED_DISAGREEMENT,
                description=f"{vlm_label} provider failed during analysis; CV baseline preserved.",
                geminiObservation=vlm_prov_base,
                vlmObservation=vlm_prov_base,
                cvObservation=cv_prov_base,
            )
            return _ret(findings, ReconciliationStatus.DISCREPANCY, conflict, f"{vlm_label} provider failed during analysis; CV baseline preserved.")

        if cv_is_failed and not vlm_is_failed:
            logger.warning(f"RECONCILIATION_CASE_8 | CV failed, preserving {vlm_label} outputs for evidenceId={evidence_id}")
            for gem_obs in vlm_observations:
                finding = cls._create_gemini_finding(
                    gem_obs=gem_obs,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[vlm_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.DISCREPANCY.value if cv_is_failed else ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=ConflictType.UNRESOLVED_DISAGREEMENT.value if cv_is_failed else ConflictType.NONE.value,
                    vlm_model_info=active_vlm_info,
                )
                findings.append(finding)
            conflict = ConflictDetail(
                conflictType=ConflictType.UNRESOLVED_DISAGREEMENT,
                description=f"CV pipeline failed: CV baseline provider failed during analysis; {vlm_label} preserved.",
                geminiObservation=vlm_prov_base,
                vlmObservation=vlm_prov_base,
                cvObservation=cv_prov_base,
            )
            return _ret(findings, ReconciliationStatus.DISCREPANCY, conflict, f"CV pipeline failed: CV baseline provider failed during analysis; {vlm_label} preserved.")

        # =========================================================================
        # CASE 7: One provider is inconclusive
        # =========================================================================
        if vlm_is_inconclusive:
            inconclusive_reason = (
                active_vlm_res.inconclusiveReason
                if active_vlm_res and active_vlm_res.inconclusiveReason
                else "Insufficient visual quality or unidentifiable scene."
            )
            logger.info(f"RECONCILIATION_CASE_7 | {vlm_label} is inconclusive for evidenceId={evidence_id} ({inconclusive_reason})")
            inconclusive_prefix = "Gemini" if detected_prov in ("google-gemini", "gemini") else vlm_label.replace(" VLM", "")
            for cv_det in active_cv_dets:
                finding = cls._create_cv_finding(
                    cv_det=cv_det,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[vlm_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.PARTIAL_INCONCLUSIVE.value,
                    conflict_type=ConflictType.UNRESOLVED_DISAGREEMENT.value,
                )
                finding.knownLimitations.append(f"Inconclusive {inconclusive_prefix} evaluation: {inconclusive_reason}")
                findings.append(finding)

            conflict = ConflictDetail(
                conflictType=ConflictType.UNRESOLVED_DISAGREEMENT,
                description=f"Visual evidence marked inconclusive by {vlm_label}: {inconclusive_reason}",
                geminiObservation={"status": "INCONCLUSIVE", "reason": inconclusive_reason},
                vlmObservation={"status": "INCONCLUSIVE", "reason": inconclusive_reason},
                cvObservation=cv_prov_base,
            )
            return _ret(findings, ReconciliationStatus.PARTIAL_INCONCLUSIVE, conflict, f"Inconclusive {inconclusive_prefix} evaluation: {inconclusive_reason}")

        # =========================================================================
        # CASE 10 & 11: Zero observations from both providers or unavailable components
        # =========================================================================
        if not vlm_has_findings and not cv_has_findings:
            logger.debug(f"RECONCILIATION_CASE_10/11 | Zero observations for evidenceId={evidence_id}")
            return _ret([], ReconciliationStatus.NO_OBSERVATIONS, None, "No safety defects or equipment anomalies detected by either provider. Safe condition cannot be certified.")

        # =========================================================================
        # CASE 2: VLM identifies potential hazard and CV returns no observation
        # =========================================================================
        if vlm_has_findings and not cv_has_findings:
            conflict_type = ConflictType.DETECTION_ABSENCE.value if cv_status == "SUCCESS" else ConflictType.NONE.value
            logger.info(f"RECONCILIATION_CASE_2 | {vlm_label} only findings for evidenceId={evidence_id}")
            for gem_obs in vlm_observations:
                finding = cls._create_gemini_finding(
                    gem_obs=gem_obs,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[vlm_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=conflict_type,
                    vlm_model_info=active_vlm_info,
                )
                findings.append(finding)
            expl = f"{vlm_label} identified visual defects; CV detector reported no observations."
            if cv_is_gated:
                expl = f"CV execution was blocked by the Model Approval Gate; {vlm_label} findings preserved."
            elif cv_is_failed:
                expl = f"CV pipeline failed ({cv_status}); {vlm_label} findings preserved."
            return _ret(findings, ReconciliationStatus.SINGLE_PROVIDER, None, expl)

        # =========================================================================
        # CASE 3: CV identifies potential hazard and VLM returns no observation
        # =========================================================================
        if cv_has_findings and not vlm_has_findings:
            is_vlm_analyzed = vlm_status in (
                GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS,
                "ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS",
            )
            conflict_type = ConflictType.DETECTION_ABSENCE.value if is_vlm_analyzed else ConflictType.NONE.value
            logger.info(f"RECONCILIATION_CASE_3 | CV only findings for evidenceId={evidence_id}")
            for cv_det in active_cv_dets:
                finding = cls._create_cv_finding(
                    cv_det=cv_det,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[vlm_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=conflict_type,
                )
                findings.append(finding)
            expl = f"Computer Vision baseline model detected visual defects; {vlm_label} reported no observations."
            return _ret(findings, ReconciliationStatus.SINGLE_PROVIDER, None, expl)

        # =========================================================================
        # CASES 1, 4, 5, B, L: Both providers returned observations
        # =========================================================================
        logger.info(
            f"RECONCILIATION_MULTI_OBSERVATION | VLM={len(vlm_observations)} | "
            f"CV={len(active_cv_dets)} for evidenceId={evidence_id}"
        )

        matched_cv_indices: Set[int] = set()
        matched_vlm_indices: Set[int] = set()
        conflicts_list: List[ConflictDetail] = []

        # Pair observations across providers
        for g_idx, g_obs in enumerate(vlm_observations):
            g_cat = g_obs.potentialHazardCategory or g_obs.objectCategory
            g_box_raw = g_obs.boundingBox
            valid_g_box, g_box_status = cls.validate_and_normalize_bbox(g_box_raw)
            g_sev = getattr(g_obs, "potentialHazardSeverity", None) or getattr(g_obs, "severityProposal", None) or "MEDIUM"

            best_match_cv_idx: Optional[int] = None
            highest_iou = 0.0

            # 1. Spatial overlap check
            for c_idx, c_det in enumerate(active_cv_dets):
                if c_idx in matched_cv_indices:
                    continue
                c_box_raw = getattr(c_det, "boundingBox", None)
                valid_c_box, _ = cls.validate_and_normalize_bbox(c_box_raw)
                iou = cls.compute_iou(valid_g_box, valid_c_box)
                if iou > highest_iou:
                    highest_iou = iou
                    best_match_cv_idx = c_idx

            # 2. Semantic category check if no spatial overlap found (detects distant location mismatch)
            if best_match_cv_idx is None:
                for c_idx, c_det in enumerate(active_cv_dets):
                    if c_idx in matched_cv_indices:
                        continue
                    c_cat = getattr(c_det, "className", None) or getattr(c_det, "findingType", "UNKNOWN")
                    if cls.objects_match(g_cat, c_cat):
                        best_match_cv_idx = c_idx
                        highest_iou = 0.0
                        break

            if best_match_cv_idx is not None:
                c_det = active_cv_dets[best_match_cv_idx]
                c_cat = getattr(c_det, "className", None) or getattr(c_det, "findingType", "UNKNOWN")
                valid_c_box, c_box_status = cls.validate_and_normalize_bbox(getattr(c_det, "boundingBox", None))
                c_sev = getattr(c_det, "severity", None) or ("HIGH" if "MISSING" in c_cat or "CRITICAL" in c_cat else "MEDIUM")

                objects_agree = cls.objects_match(g_cat, c_cat)
                hazards_agree = cls.hazards_match(g_cat, c_cat)
                locations_match = highest_iou >= 0.3
                severities_match = g_sev.upper() == c_sev.upper()

                # Detailed observation provenance
                detailed_vlm_prov = dict(vlm_prov_base)
                detailed_vlm_prov.update({
                    "observationId": getattr(g_obs, "observationId", None),
                    "rawCategory": g_cat,
                    "rawObjectCategory": getattr(g_obs, "objectCategory", None),
                    "rawHazardCategory": getattr(g_obs, "potentialHazardCategory", None),
                    "rawSeverity": g_sev,
                    "rawConfidence": getattr(g_obs, "confidence", None),
                    "boundingBox": valid_g_box.model_dump() if valid_g_box else None,
                    "observationDescription": getattr(g_obs, "observationDescription", None) or getattr(g_obs, "description", None),
                    "uncertaintyMetadata": {
                        "uncertaintyOrLimitations": getattr(g_obs, "uncertaintyOrLimitations", []),
                    },
                })
                detailed_cv_prov = dict(cv_prov_base)
                detailed_cv_prov.update({
                    "observationId": str(getattr(c_det, "detectionId", None) or getattr(c_det, "findingId", None) or uuid.uuid4()),
                    "rawCategory": c_cat,
                    "rawObjectCategory": c_cat,
                    "rawHazardCategory": c_cat,
                    "rawSeverity": c_sev,
                    "rawConfidence": getattr(c_det, "confidence", None),
                    "boundingBox": valid_c_box.model_dump() if valid_c_box else None,
                    "observationDescription": f"CV baseline detection of {c_cat}",
                    "uncertaintyMetadata": {
                        "calibrationStatus": "NOT_CALIBRATED",
                    },
                })

                is_hazard_conflict = cls.hazards_conflict(g_cat, c_cat)

                # CASE B: Same object, but disagree on specific hazard (e.g. BLOCKED vs MISSING)
                if locations_match and objects_agree and is_hazard_conflict:
                    logger.warning(f"RECONCILIATION_CASE_B | Hazard mismatch: {vlm_label}={g_cat} vs CV={c_cat}")
                    conflict_desc = f"Hazard conflict on same object: {vlm_label} identified '{g_cat}' whereas CV baseline identified '{c_cat}'."
                    finding_vlm = Finding(
                        evidenceId=evidence_id,
                        inspectionId=inspection_id,
                        analysisRunId=run_id,
                        tenantId=tenant_id,
                        findingType=g_cat,
                        confidence=round(getattr(g_obs, "confidence", 0.75), 4) if getattr(g_obs, "confidence", None) is not None else 0.75,
                        rawConfidence=getattr(g_obs, "confidence", None),
                        sourceType="VLM",
                        severity=cls.map_severity(g_sev),
                        explanation=(
                            f"Model hazard disagreement detected on same physical object: {vlm_label} flagged '{g_cat}' "
                            f"while CV baseline flagged '{c_cat}'. Both classifications are preserved for officer adjudication."
                        ),
                        model=active_vlm_info.get("model") or getattr(g_obs, "modelName", None) or vlm_default_model,
                        modelVersion=getattr(g_obs, "modelVersion", None) or "0.3.0",
                        modelId=active_vlm_info.get("model") or getattr(g_obs, "modelName", None) or vlm_default_model,
                        boundingBox=valid_g_box or valid_c_box,
                        validationStatus="VALID",
                        isTestData=is_test_mode,
                        reconciliationStatus=ReconciliationStatus.CONFLICT.value,
                        conflictType=ConflictType.HAZARD_MISMATCH.value,
                        conflictDetails={
                            "conflictType": "HAZARD_MISMATCH",
                            "description": conflict_desc,
                            "vlmCategory": g_cat,
                            "cvCategory": c_cat,
                            "geminiCategory": g_cat,
                            "iou": highest_iou,
                        },
                        provenance=[detailed_vlm_prov, detailed_cv_prov],
                    )
                    finding_cv = cls._create_cv_finding(
                        cv_det=c_det,
                        evidence_id=evidence_id,
                        inspection_id=inspection_id,
                        run_id=run_id,
                        tenant_id=tenant_id,
                        is_test_mode=is_test_mode,
                        provenance=[detailed_vlm_prov, detailed_cv_prov],
                        reconciliation_status=ReconciliationStatus.CONFLICT.value,
                        conflict_type=ConflictType.HAZARD_MISMATCH.value,
                    )
                    finding_cv.explanation = (
                        f"Model hazard disagreement detected on same physical object: CV baseline flagged '{c_cat}' "
                        f"while {vlm_label} flagged '{g_cat}'. Both classifications are preserved for officer adjudication."
                    )
                    findings.append(finding_vlm)
                    findings.append(finding_cv)

                    conflict = ConflictDetail(
                        evidenceId=str(evidence_id),
                        conflictType=ConflictType.HAZARD_MISMATCH,
                        description=conflict_desc,
                        cvFinding=finding_cv,
                        vlmObservation=g_obs,
                        geminiObservation=g_obs,
                        explanation=conflict_desc,
                    )
                    conflicts_list.append(conflict)
                    matched_cv_indices.add(best_match_cv_idx)
                    matched_vlm_indices.add(g_idx)

                # CASE 1 / CASE A: Full Agreement (Same object, concordant hazard, high spatial overlap)
                elif locations_match and objects_agree:
                    logger.info(f"RECONCILIATION_CASE_1 | Provider agreement on {g_cat} (IoU={highest_iou:.2f})")
                    conflict_type = ConflictType.SEVERITY_MISMATCH.value if not severities_match else ConflictType.NONE.value
                    finding = Finding(
                        evidenceId=evidence_id,
                        inspectionId=inspection_id,
                        analysisRunId=run_id,
                        tenantId=tenant_id,
                        findingType=g_cat,
                        confidence=round(
                            max(
                                getattr(g_obs, "confidence", 0.85) or 0.85,
                                getattr(c_det, "confidence", 0.85) or 0.85,
                            ),
                            4,
                        ),
                        rawConfidence=getattr(g_obs, "confidence", None),
                        sourceType="ENSEMBLE",
                        severity=cls.map_severity(g_sev),
                        explanation=(
                            f"{vlm_label} and Computer Vision model both identified {g_cat.replace('_', ' ').lower()}. "
                            "Notice: Cross-model agreement reflects consistent visual patterns but does NOT guarantee statutory compliance; "
                            "physical human Fire Officer on-site verification is required."
                        ),
                        model=f"{active_vlm_info.get('model', vlm_default_model)}+{active_cv_info.get('model', 'cv')}",
                        modelVersion="ensemble-v0.3.0",
                        modelId=f"{active_vlm_info.get('model', vlm_default_model)}+{active_cv_info.get('model', 'cv')}",
                        coordinateConvention="normalized",
                        boundingBox=valid_g_box or valid_c_box,
                        validationStatus="VALID",
                        isTestData=is_test_mode,
                        reconciliationStatus=ReconciliationStatus.AGREEMENT.value,
                        conflictType=conflict_type,
                        isCrossValidated=True,
                        contributingProviders=["CV", "VLM"],
                        provenance=[detailed_vlm_prov, detailed_cv_prov],
                    )
                    findings.append(finding)
                    matched_cv_indices.add(best_match_cv_idx)
                    matched_vlm_indices.add(g_idx)

                # CASE 4: Category Mismatch (Location roughly matches, but physical categories differ)
                elif locations_match and not objects_agree:
                    logger.warning(f"RECONCILIATION_CASE_4 | Category mismatch: {vlm_label}={g_cat} vs CV={c_cat}")
                    conflict_desc = f"Category conflict: {vlm_label} identified '{g_cat}' whereas CV baseline identified '{c_cat}'."
                    finding = Finding(
                        evidenceId=evidence_id,
                        inspectionId=inspection_id,
                        analysisRunId=run_id,
                        tenantId=tenant_id,
                        findingType=g_cat,
                        confidence=0.75,
                        rawConfidence=getattr(g_obs, "confidence", None),
                        sourceType="VLM",
                        severity=cls.map_severity(g_sev),
                        explanation=(
                            f"Model disagreement detected: {vlm_label} flagged '{g_cat}' while CV baseline flagged '{c_cat}'. "
                            "Both categories are preserved. Human Fire Officer verification is required to adjudicate."
                        ),
                        model=active_vlm_info.get("model") or getattr(g_obs, "modelName", None) or vlm_default_model,
                        modelVersion=getattr(g_obs, "modelVersion", None) or "0.3.0",
                        modelId=active_vlm_info.get("model") or getattr(g_obs, "modelName", None) or vlm_default_model,
                        boundingBox=valid_g_box or valid_c_box,
                        validationStatus="VALID",
                        isTestData=is_test_mode,
                        reconciliationStatus=ReconciliationStatus.CONFLICT.value,
                        conflictType=ConflictType.CATEGORY_MISMATCH.value,
                        conflictDetails={
                            "conflictType": "CATEGORY_MISMATCH",
                            "description": conflict_desc,
                            "vlmCategory": g_cat,
                            "cvCategory": c_cat,
                            "geminiCategory": g_cat,
                            "iou": highest_iou,
                        },
                        provenance=[detailed_vlm_prov, detailed_cv_prov],
                    )
                    findings.append(finding)
                    matched_cv_indices.add(best_match_cv_idx)
                    matched_vlm_indices.add(g_idx)

                # CASE 5: Location Mismatch (Categories match, but bounding boxes differ)
                elif objects_agree and not locations_match:
                    logger.warning(f"RECONCILIATION_CASE_5 | Location mismatch on {g_cat} (IoU={highest_iou:.2f})")
                    conflict_desc = f"Spatial mismatch: Both models identified '{g_cat}' but at distinct coordinates (IoU={highest_iou:.2f})."
                    finding_vlm = Finding(
                        evidenceId=evidence_id,
                        inspectionId=inspection_id,
                        analysisRunId=run_id,
                        tenantId=tenant_id,
                        findingType=g_cat,
                        confidence=0.70,
                        rawConfidence=getattr(g_obs, "confidence", None),
                        sourceType="VLM",
                        severity=cls.map_severity(g_sev),
                        explanation=(
                            f"Both models identified '{g_cat}', but proposed differing spatial bounding boxes. "
                            "Coordinates are not merged automatically; officer review is required."
                        ),
                        model=active_vlm_info.get("model") or getattr(g_obs, "modelName", None) or vlm_default_model,
                        modelVersion=getattr(g_obs, "modelVersion", None) or "0.3.0",
                        modelId=active_vlm_info.get("model") or getattr(g_obs, "modelName", None) or vlm_default_model,
                        boundingBox=valid_g_box,
                        validationStatus="VALID",
                        isTestData=is_test_mode,
                        reconciliationStatus=ReconciliationStatus.CONFLICT.value,
                        conflictType=ConflictType.LOCATION_MISMATCH.value,
                        conflictDetails={
                            "conflictType": "LOCATION_MISMATCH",
                            "description": conflict_desc,
                            "geminiBoundingBox": valid_g_box.model_dump() if valid_g_box else None,
                            "vlmBoundingBox": valid_g_box.model_dump() if valid_g_box else None,
                            "cvBoundingBox": valid_c_box.model_dump() if valid_c_box else None,
                            "iou": highest_iou,
                        },
                        provenance=[detailed_vlm_prov, detailed_cv_prov],
                    )
                    finding_cv = cls._create_cv_finding(
                        cv_det=c_det,
                        evidence_id=evidence_id,
                        inspection_id=inspection_id,
                        run_id=run_id,
                        tenant_id=tenant_id,
                        is_test_mode=is_test_mode,
                        provenance=[detailed_vlm_prov, detailed_cv_prov],
                        reconciliation_status=ReconciliationStatus.CONFLICT.value,
                        conflict_type=ConflictType.LOCATION_MISMATCH.value,
                    )
                    finding_cv.explanation = (
                        f"Spatial discrepancy on '{c_cat}': CV detected object at coordinates {valid_c_box}, "
                        f"differing from {vlm_label}'s coordinates {valid_g_box} (IoU={highest_iou:.2f}). Both bounding boxes are preserved."
                    )
                    findings.append(finding_vlm)
                    findings.append(finding_cv)
                    conflict = ConflictDetail(
                        evidenceId=str(evidence_id),
                        conflictType=ConflictType.LOCATION_MISMATCH,
                        description=conflict_desc,
                        cvFinding=finding_cv,
                        vlmObservation=g_obs,
                        geminiObservation=g_obs,
                        explanation=conflict_desc,
                    )
                    conflicts_list.append(conflict)
                    matched_cv_indices.add(best_match_cv_idx)
                    matched_vlm_indices.add(g_idx)

        # Append remaining unmatched VLM findings
        for g_idx, g_obs in enumerate(vlm_observations):
            if g_idx not in matched_vlm_indices:
                finding = cls._create_gemini_finding(
                    gem_obs=g_obs,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[vlm_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=ConflictType.DETECTION_ABSENCE.value,
                    vlm_model_info=active_vlm_info,
                )
                findings.append(finding)

        # Append remaining unmatched CV findings
        for c_idx, c_det in enumerate(active_cv_dets):
            if c_idx not in matched_cv_indices:
                finding = cls._create_cv_finding(
                    cv_det=c_det,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[vlm_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=ConflictType.DETECTION_ABSENCE.value,
                )
                findings.append(finding)

        overall_status = (
            ReconciliationStatus.AGREEMENT
            if (findings and all(f.reconciliationStatus == ReconciliationStatus.AGREEMENT.value for f in findings))
            else (
                ReconciliationStatus.CONFLICT
                if any(f.reconciliationStatus == ReconciliationStatus.CONFLICT.value for f in findings)
                else (
                    ReconciliationStatus.NO_OBSERVATIONS
                    if not findings
                    else ReconciliationStatus.SINGLE_PROVIDER
                    if all(f.reconciliationStatus == ReconciliationStatus.SINGLE_PROVIDER.value for f in findings)
                    else ReconciliationStatus.DISCREPANCY
                )
            )
        )

        conflict_obj = conflicts_list[0] if conflicts_list else None
        return _ret(findings, overall_status, conflict_obj)

    @classmethod
    def _create_gemini_finding(
        cls,
        gem_obs: GeminiObservation,
        evidence_id: uuid.UUID,
        inspection_id: uuid.UUID,
        run_id: uuid.UUID,
        tenant_id: Optional[str],
        is_test_mode: bool,
        provenance: List[Dict[str, Any]],
        reconciliation_status: str,
        conflict_type: str,
        vlm_model_info: Optional[Dict[str, str]] = None,
    ) -> Finding:
        """Constructs a normalized Finding from a GeminiObservation or generic VLM observation."""
        cat = gem_obs.potentialHazardCategory or gem_obs.objectCategory
        sev = cls.map_severity(getattr(gem_obs, "potentialHazardSeverity", None) or getattr(gem_obs, "severityProposal", None))
        desc = getattr(gem_obs, "observationDescription", None) or getattr(gem_obs, "description", None) or f"AI observation of {cat.replace('_', ' ').lower()}."
        conf = (
            round(gem_obs.confidence, 4)
            if getattr(gem_obs, "confidence", None) is not None
            else 0.85
        )

        v_label, f_model, f_version = cls._resolve_vlm_label_and_models(gem_obs, vlm_model_info)
        valid_box, box_status = cls.validate_and_normalize_bbox(gem_obs.boundingBox)

        return Finding(
            evidenceId=evidence_id,
            inspectionId=inspection_id,
            analysisRunId=run_id,
            tenantId=tenant_id,
            findingType=cat,
            confidence=conf,
            rawConfidence=gem_obs.confidence,
            sourceType="VLM",
            severity=sev,
            explanation=f"{v_label} identified visual defect: {desc}",
            model=f_model,
            modelVersion=f_version,
            modelId=f_model,
            coordinateConvention="gemini_0_1000" if valid_box else None,
            boundingBox=valid_box,
            validationStatus=box_status if box_status != "VALID" else "VALID",
            isTestData=is_test_mode,
            reconciliationStatus=reconciliation_status,
            conflictType=conflict_type,
            provenance=provenance,
            officerVerificationStatus="UNREVIEWED",
            evidenceReviewed=False,
            modelApprovalStatus="NOT_APPROVED",
            calibrationStatus="NOT_CALIBRATED",
        )

    # Alias for modern multi-provider visual inspection
    _create_vlm_finding = _create_gemini_finding

    @classmethod
    def _create_cv_finding(
        cls,
        cv_det: Any,
        evidence_id: uuid.UUID,
        inspection_id: uuid.UUID,
        run_id: uuid.UUID,
        tenant_id: Optional[str],
        is_test_mode: bool,
        provenance: List[Dict[str, Any]],
        reconciliation_status: str,
        conflict_type: str,
    ) -> Finding:
        """Constructs a normalized Finding from a CV RawDetection."""
        class_name = getattr(cv_det, "className", None) or getattr(cv_det, "findingType", None) or "GENERIC_FIRE_SAFETY_DEFECT"
        conf = getattr(cv_det, "confidence", 0.85)
        raw_box = getattr(cv_det, "boundingBox", None)
        valid_box, box_status = cls.validate_and_normalize_bbox(raw_box)

        return Finding(
            evidenceId=evidence_id,
            inspectionId=inspection_id,
            analysisRunId=run_id,
            tenantId=tenant_id,
            findingType=class_name,
            confidence=conf,
            rawConfidence=getattr(cv_det, "confidence", None),
            sourceType="CV",
            severity=FindingSeverity.HIGH if "MISSING" in class_name else FindingSeverity.MEDIUM,
            explanation=f"Computer Vision baseline model detected potential defect '{class_name}'.",
            model="firesphere-perception-baseline-v0.8.0",
            modelVersion="0.8.0",
            modelId="firesphere-perception-baseline-v0.8.0",
            coordinateConvention="normalized" if valid_box else None,
            boundingBox=valid_box,
            validationStatus=box_status if box_status != "VALID" else "VALID",
            isTestData=is_test_mode,
            reconciliationStatus=reconciliation_status,
            conflictType=conflict_type,
            provenance=provenance,
            officerVerificationStatus="UNREVIEWED",
            evidenceReviewed=False,
            modelApprovalStatus="NOT_APPROVED",
            calibrationStatus="NOT_CALIBRATED",
        )


# Global singleton reconciler
result_reconciler = ResultReconciler()

# Stage 8 Aliases and Helper Functions
CVVLMReconciliationService = ResultReconciler
validate_and_normalize_bbox = ResultReconciler.validate_and_normalize_bbox
objects_match = ResultReconciler.objects_match
hazards_match = ResultReconciler.hazards_match
hazards_conflict = ResultReconciler.hazards_conflict
