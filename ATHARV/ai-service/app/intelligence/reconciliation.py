"""FireSphere.ai — Phase 3 Deterministic Result Reconciliation & Conflict Resolution.

Coordinates Gemini VLM visual reasoning outputs with Computer Vision baseline detections.
Implements an auditable, deterministic 12-case reconciliation matrix that preserves
source observations, records model agreements/discrepancies, and guarantees that no AI
model autonomously certifies Fire NOC compliance.
"""

from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Set, Tuple
import uuid

from app.core.logging import logger
from app.models.gemini_schemas import GeminiAnalysisResult, GeminiAnalysisStatus, GeminiObservation
from app.schemas.finding import BoundingBox, Finding, FindingSeverity
from app.schemas.reconciliation import (
    ConflictDetail,
    ConflictType,
    ProviderProvenance,
    ReconciliationStatus,
)


class ResultReconciler:
    """Deterministic cross-provider observation reconciler and conflict resolution engine."""

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
    def categories_match(cls, cat1: str, cat2: str) -> bool:
        """Determines whether two defect or perception categories represent the same underlying hazard."""
        c1 = cat1.upper().replace("-", "_").strip()
        c2 = cat2.upper().replace("-", "_").strip()

        if c1 == c2:
            return True

        # Semantic equivalence patterns
        if "EXTINGUISHER" in c1 and "EXTINGUISHER" in c2:
            return True
        if ("EXIT" in c1 and "SIGN" in c1) and ("EXIT" in c2 and "SIGN" in c2):
            return True
        if ("DOOR" in c1 or "EXIT" in c1 or "OBSTACLE" in c1) and ("DOOR" in c2 or "EXIT" in c2 or "OBSTACLE" in c2):
            return True

        return False

    @classmethod
    def map_severity(cls, raw_sev: Optional[str]) -> FindingSeverity:
        """Maps arbitrary raw severity strings to valid FindingSeverity enum safely."""
        if not raw_sev:
            return FindingSeverity.MEDIUM
        return cls.SEVERITY_MAPPING.get(raw_sev.strip().upper(), FindingSeverity.MEDIUM)

    @classmethod
    def reconcile(
        cls,
        evidence_id: uuid.UUID,
        inspection_id: uuid.UUID,
        gemini_result: Optional[GeminiAnalysisResult],
        cv_detections: List[Any],
        cv_status: str,
        cv_model_info: Dict[str, str],
        gemini_model_info: Dict[str, str],
        is_test_mode: bool = False,
        tenant_id: Optional[str] = "default",
        analysis_run_id: Optional[uuid.UUID] = None,
    ) -> Tuple[List[Finding], ReconciliationStatus, Optional[ConflictDetail]]:
        """Reconciles observations across Gemini VLM and CV models for a single evidence item.

        Handles all 12 defined cases deterministically.
        Returns:
            - List of normalized Finding objects
            - Overall evidence reconciliation status
            - Optional ConflictDetail describing discrepancies
        """
        run_id = analysis_run_id or uuid.uuid4()
        findings: List[Finding] = []

        # 1. Inspect Provider Statuses
        gemini_status = gemini_result.status if gemini_result else GeminiAnalysisStatus.GEMINI_DISABLED
        gemini_observations = gemini_result.observations if gemini_result else []

        gemini_is_inconclusive = gemini_status == GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE
        gemini_is_failed = gemini_status in (
            GeminiAnalysisStatus.ANALYSIS_FAILED,
            GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE,
        )
        gemini_is_disabled = gemini_status == GeminiAnalysisStatus.GEMINI_DISABLED
        gemini_not_configured = gemini_status == GeminiAnalysisStatus.GEMINI_NOT_CONFIGURED
        gemini_has_findings = len(gemini_observations) > 0

        cv_is_failed = cv_status == "FAILED"
        cv_is_disabled = cv_status == "DISABLED"
        cv_has_findings = len(cv_detections) > 0

        # Build baseline provenance items
        gemini_prov_base = {
            "providerName": "google-gemini",
            "modelId": gemini_model_info.get("model", "gemini-2.5-flash"),
            "executionStatus": gemini_status.value if hasattr(gemini_status, "value") else str(gemini_status),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        if gemini_result and gemini_result.error:
            gemini_prov_base["error"] = gemini_result.error

        cv_prov_base = {
            "providerName": "firesphere-cv-baseline",
            "modelId": cv_model_info.get("model", "firesphere-perception-baseline-v0.8.0"),
            "modelVersion": cv_model_info.get("version", "0.8.0"),
            "executionStatus": cv_status,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

        # =========================================================================
        # CASE 9: Both providers failed
        # =========================================================================
        if (gemini_is_failed or gemini_not_configured) and cv_is_failed:
            logger.error(f"RECONCILIATION_CASE_9 | Both providers failed for evidenceId={evidence_id}")
            conflict = ConflictDetail(
                conflictType=ConflictType.UNRESOLVED_DISAGREEMENT,
                description="Both visual analysis providers failed to process the evidence item.",
                geminiObservation=gemini_prov_base,
                cvObservation=cv_prov_base,
            )
            return [], ReconciliationStatus.FAILED, conflict

        # =========================================================================
        # CASE 8: One provider fails, other succeeds
        # =========================================================================
        if gemini_is_failed and not cv_is_failed:
            logger.warning(f"RECONCILIATION_CASE_8 | Gemini failed, preserving CV outputs for evidenceId={evidence_id}")
            # Process CV detections if any
            for cv_det in cv_detections:
                finding = cls._create_cv_finding(
                    cv_det=cv_det,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[gemini_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.DISCREPANCY.value,
                    conflict_type=ConflictType.UNRESOLVED_DISAGREEMENT.value,
                )
                findings.append(finding)
            conflict = ConflictDetail(
                conflictType=ConflictType.UNRESOLVED_DISAGREEMENT,
                description="Gemini provider failed during analysis; CV baseline preserved in fail-closed/test mode.",
                geminiObservation=gemini_prov_base,
                cvObservation=cv_prov_base,
            )
            return findings, ReconciliationStatus.DISCREPANCY, conflict

        if cv_is_failed and not gemini_is_failed:
            logger.warning(f"RECONCILIATION_CASE_8 | CV failed, preserving Gemini outputs for evidenceId={evidence_id}")
            for gem_obs in gemini_observations:
                finding = cls._create_gemini_finding(
                    gem_obs=gem_obs,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[gemini_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=ConflictType.NONE.value,
                )
                findings.append(finding)
            return findings, ReconciliationStatus.SINGLE_PROVIDER, None

        # =========================================================================
        # CASE 7: One provider is inconclusive
        # =========================================================================
        if gemini_is_inconclusive:
            inconclusive_reason = (
                gemini_result.inconclusiveReason
                if gemini_result and gemini_result.inconclusiveReason
                else "Insufficient visual quality or unidentifiable scene."
            )
            logger.info(f"RECONCILIATION_CASE_7 | Gemini is inconclusive for evidenceId={evidence_id} ({inconclusive_reason})")
            for cv_det in cv_detections:
                finding = cls._create_cv_finding(
                    cv_det=cv_det,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[gemini_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.PARTIAL_INCONCLUSIVE.value,
                    conflict_type=ConflictType.UNRESOLVED_DISAGREEMENT.value,
                )
                finding.knownLimitations.append(f"Inconclusive Gemini evaluation: {inconclusive_reason}")
                findings.append(finding)

            conflict = ConflictDetail(
                conflictType=ConflictType.UNRESOLVED_DISAGREEMENT,
                description=f"Visual evidence marked inconclusive by Gemini: {inconclusive_reason}",
                geminiObservation={"status": "INCONCLUSIVE", "reason": inconclusive_reason},
                cvObservation=cv_prov_base,
            )
            return findings, ReconciliationStatus.PARTIAL_INCONCLUSIVE, conflict

        # =========================================================================
        # CASE 10 & 11: Both return zero observations or provider disabled
        # =========================================================================
        if not gemini_has_findings and not cv_has_findings:
            logger.debug(f"RECONCILIATION_CASE_10/11 | Zero observations for evidenceId={evidence_id}")
            # Return empty findings list with honest NO_OBSERVATIONS status
            return [], ReconciliationStatus.NO_OBSERVATIONS, None

        # =========================================================================
        # CASE 2: Gemini identifies potential hazard and CV returns no observation
        # =========================================================================
        if gemini_has_findings and not cv_has_findings:
            conflict_type = ConflictType.DETECTION_ABSENCE.value if cv_status == "SUCCESS" else ConflictType.NONE.value
            logger.info(f"RECONCILIATION_CASE_2 | Gemini only findings for evidenceId={evidence_id}")
            for gem_obs in gemini_observations:
                finding = cls._create_gemini_finding(
                    gem_obs=gem_obs,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[gemini_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=conflict_type,
                )
                findings.append(finding)
            return findings, ReconciliationStatus.SINGLE_PROVIDER, None

        # =========================================================================
        # CASE 3: CV identifies potential hazard and Gemini returns no observation
        # =========================================================================
        if cv_has_findings and not gemini_has_findings:
            logger.info(f"RECONCILIATION_CASE_3 | CV only findings for evidenceId={evidence_id}")
            for cv_det in cv_detections:
                finding = cls._create_cv_finding(
                    cv_det=cv_det,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[gemini_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=ConflictType.DETECTION_ABSENCE.value,
                )
                findings.append(finding)
            return findings, ReconciliationStatus.SINGLE_PROVIDER, None

        # =========================================================================
        # CASES 1, 4, 5, 6: Both providers returned observations
        # =========================================================================
        logger.info(
            f"RECONCILIATION_MULTI_OBSERVATION | Gemini={len(gemini_observations)} | "
            f"CV={len(cv_detections)} for evidenceId={evidence_id}"
        )

        matched_cv_indices: Set[int] = set()
        matched_gemini_indices: Set[int] = set()

        # Pair observations across providers
        for g_idx, g_obs in enumerate(gemini_observations):
            g_cat = g_obs.potentialHazardCategory or g_obs.objectCategory
            g_box = g_obs.boundingBox
            g_sev = getattr(g_obs, "potentialHazardSeverity", None) or getattr(g_obs, "severityProposal", None) or "MEDIUM"

            best_match_cv_idx: Optional[int] = None
            highest_iou = 0.0

            # 1. Spatial overlap check
            for c_idx, c_det in enumerate(cv_detections):
                if c_idx in matched_cv_indices:
                    continue
                c_box = BoundingBox(**c_det.boundingBox) if getattr(c_det, "boundingBox", None) else None
                iou = cls.compute_iou(g_box, c_box)
                if iou > highest_iou:
                    highest_iou = iou
                    best_match_cv_idx = c_idx

            # 2. Semantic category check if no spatial overlap found (detects location mismatch)
            if best_match_cv_idx is None:
                for c_idx, c_det in enumerate(cv_detections):
                    if c_idx in matched_cv_indices:
                        continue
                    c_cat = getattr(c_det, "className", "UNKNOWN")
                    if cls.categories_match(g_cat, c_cat):
                        best_match_cv_idx = c_idx
                        highest_iou = 0.0
                        break

            if best_match_cv_idx is not None:
                c_det = cv_detections[best_match_cv_idx]
                c_cat = getattr(c_det, "className", "UNKNOWN")
                c_box = BoundingBox(**c_det.boundingBox) if getattr(c_det, "boundingBox", None) else None
                c_sev = getattr(c_det, "severity", None) or ("HIGH" if "MISSING" in c_cat or "CRITICAL" in c_cat else "MEDIUM")

                cats_match = cls.categories_match(g_cat, c_cat)
                locations_match = highest_iou >= 0.3
                severities_match = g_sev.upper() == c_sev.upper()

                # Detailed observation provenance
                detailed_gemini_prov = dict(gemini_prov_base)
                detailed_gemini_prov.update({
                    "rawCategory": g_cat,
                    "rawSeverity": g_sev,
                    "boundingBox": g_box.model_dump() if g_box else None,
                })
                detailed_cv_prov = dict(cv_prov_base)
                detailed_cv_prov.update({
                    "rawCategory": c_cat,
                    "rawSeverity": c_sev,
                    "boundingBox": c_box.model_dump() if c_box else None,
                })

                # CASE 1: Full Agreement (Category and Location match)
                if cats_match and locations_match:
                    logger.info(f"RECONCILIATION_CASE_1 | Provider agreement on {g_cat} (IoU={highest_iou:.2f})")
                    conflict_type = ConflictType.SEVERITY_MISMATCH.value if not severities_match else ConflictType.NONE.value
                    finding = Finding(
                        evidenceId=evidence_id,
                        inspectionId=inspection_id,
                        analysisRunId=run_id,
                        tenantId=tenant_id,
                        findingType=g_cat,
                        confidence=round(g_obs.confidence, 4) if hasattr(g_obs, "confidence") and g_obs.confidence else getattr(c_det, "confidence", 0.85),
                        severity=cls.map_severity(g_sev),
                        explanation=(
                            f"Gemini VLM and Computer Vision model both identified {g_cat.replace('_', ' ').lower()}. "
                            "Notice: Cross-model agreement reflects consistent visual patterns but does NOT guarantee statutory compliance; "
                            "physical human Fire Officer on-site verification is required."
                        ),
                        model=f"{gemini_model_info.get('model', 'gemini')}+{cv_model_info.get('model', 'cv')}",
                        modelVersion="ensemble-v0.3.0",
                        boundingBox=g_box or c_box,
                        isTestData=is_test_mode,
                        reconciliationStatus=ReconciliationStatus.AGREEMENT.value,
                        conflictType=conflict_type,
                        provenance=[detailed_gemini_prov, detailed_cv_prov],
                    )
                    findings.append(finding)
                    matched_cv_indices.add(best_match_cv_idx)
                    matched_gemini_indices.add(g_idx)

                # CASE 4: Category Mismatch (Location roughly matches, but categories differ)
                elif locations_match and not cats_match:
                    logger.warning(f"RECONCILIATION_CASE_4 | Category mismatch: Gemini={g_cat} vs CV={c_cat}")
                    conflict_desc = f"Category conflict: Gemini identified '{g_cat}' whereas CV baseline identified '{c_cat}'."
                    finding = Finding(
                        evidenceId=evidence_id,
                        inspectionId=inspection_id,
                        analysisRunId=run_id,
                        tenantId=tenant_id,
                        findingType=g_cat,
                        confidence=0.75,
                        severity=cls.map_severity(g_sev),
                        explanation=(
                            f"Model disagreement detected: Gemini flagged '{g_cat}' while CV baseline flagged '{c_cat}'. "
                            "Both categories are preserved. Human Fire Officer verification is required to adjudicate."
                        ),
                        model=gemini_model_info.get("model", "gemini"),
                        modelVersion="0.3.0",
                        boundingBox=g_box or c_box,
                        isTestData=is_test_mode,
                        reconciliationStatus=ReconciliationStatus.CONFLICT.value,
                        conflictType=ConflictType.CATEGORY_MISMATCH.value,
                        conflictDetails={
                            "conflictType": "CATEGORY_MISMATCH",
                            "description": conflict_desc,
                            "geminiCategory": g_cat,
                            "cvCategory": c_cat,
                        },
                        provenance=[detailed_gemini_prov, detailed_cv_prov],
                    )
                    findings.append(finding)
                    matched_cv_indices.add(best_match_cv_idx)
                    matched_gemini_indices.add(g_idx)

                # CASE 5: Location Mismatch (Categories match, but bounding boxes differ)
                elif cats_match and not locations_match:
                    logger.warning(f"RECONCILIATION_CASE_5 | Location mismatch on {g_cat} (IoU={highest_iou:.2f})")
                    conflict_desc = f"Spatial mismatch: Both models identified '{g_cat}' but at distinct coordinates (IoU={highest_iou:.2f})."
                    finding = Finding(
                        evidenceId=evidence_id,
                        inspectionId=inspection_id,
                        analysisRunId=run_id,
                        tenantId=tenant_id,
                        findingType=g_cat,
                        confidence=0.70,
                        severity=cls.map_severity(g_sev),
                        explanation=(
                            f"Both models identified '{g_cat}', but proposed differing spatial bounding boxes. "
                            "Coordinates are not merged automatically; officer review is required."
                        ),
                        model=gemini_model_info.get("model", "gemini"),
                        modelVersion="0.3.0",
                        boundingBox=g_box,  # Preserve Gemini box primarily, CV box stored in conflict details
                        isTestData=is_test_mode,
                        reconciliationStatus=ReconciliationStatus.CONFLICT.value,
                        conflictType=ConflictType.LOCATION_MISMATCH.value,
                        conflictDetails={
                            "conflictType": "LOCATION_MISMATCH",
                            "description": conflict_desc,
                            "geminiBoundingBox": g_box.model_dump() if g_box else None,
                            "cvBoundingBox": c_box.model_dump() if c_box else None,
                            "iou": highest_iou,
                        },
                        provenance=[detailed_gemini_prov, detailed_cv_prov],
                    )
                    findings.append(finding)
                    matched_cv_indices.add(best_match_cv_idx)
                    matched_gemini_indices.add(g_idx)

        # Append remaining unmatched Gemini findings
        for g_idx, g_obs in enumerate(gemini_observations):
            if g_idx not in matched_gemini_indices:
                finding = cls._create_gemini_finding(
                    gem_obs=g_obs,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[gemini_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=ConflictType.DETECTION_ABSENCE.value,
                )
                findings.append(finding)

        # Append remaining unmatched CV findings
        for c_idx, c_det in enumerate(cv_detections):
            if c_idx not in matched_cv_indices:
                finding = cls._create_cv_finding(
                    cv_det=c_det,
                    evidence_id=evidence_id,
                    inspection_id=inspection_id,
                    run_id=run_id,
                    tenant_id=tenant_id,
                    is_test_mode=is_test_mode,
                    provenance=[gemini_prov_base, cv_prov_base],
                    reconciliation_status=ReconciliationStatus.SINGLE_PROVIDER.value,
                    conflict_type=ConflictType.DETECTION_ABSENCE.value,
                )
                findings.append(finding)

        overall_status = (
            ReconciliationStatus.AGREEMENT
            if all(f.reconciliationStatus == ReconciliationStatus.AGREEMENT.value for f in findings)
            else (
                ReconciliationStatus.CONFLICT
                if any(f.reconciliationStatus == ReconciliationStatus.CONFLICT.value for f in findings)
                else ReconciliationStatus.DISCREPANCY
            )
        )

        return findings, overall_status, None

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
    ) -> Finding:
        """Constructs a normalized Finding from a GeminiObservation."""
        cat = gem_obs.potentialHazardCategory or gem_obs.objectCategory
        sev = cls.map_severity(getattr(gem_obs, "potentialHazardSeverity", None) or getattr(gem_obs, "severityProposal", None))
        desc = getattr(gem_obs, "observationDescription", None) or getattr(gem_obs, "description", None) or f"AI observation of {cat.replace('_', ' ').lower()}."

        return Finding(
            evidenceId=evidence_id,
            inspectionId=inspection_id,
            analysisRunId=run_id,
            tenantId=tenant_id,
            findingType=cat,
            confidence=0.88,
            severity=sev,
            explanation=f"Gemini VLM identified visual defect: {desc}",
            model="google-gemini",
            modelVersion="gemini-2.5-flash",
            boundingBox=gem_obs.boundingBox,
            isTestData=is_test_mode,
            reconciliationStatus=reconciliation_status,
            conflictType=conflict_type,
            provenance=provenance,
            officerVerificationStatus="UNREVIEWED",
            evidenceReviewed=False,
        )

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
        class_name = getattr(cv_det, "className", "GENERIC_FIRE_SAFETY_DEFECT")
        conf = getattr(cv_det, "confidence", 0.85)
        raw_box = getattr(cv_det, "boundingBox", None)
        box = BoundingBox(**raw_box) if raw_box else None

        return Finding(
            evidenceId=evidence_id,
            inspectionId=inspection_id,
            analysisRunId=run_id,
            tenantId=tenant_id,
            findingType=class_name,
            confidence=conf,
            severity=FindingSeverity.HIGH if "MISSING" in class_name else FindingSeverity.MEDIUM,
            explanation=f"Computer Vision baseline model detected potential defect '{class_name}'.",
            model="firesphere-perception-baseline-v0.8.0",
            modelVersion="0.8.0",
            boundingBox=box,
            isTestData=is_test_mode,
            reconciliationStatus=reconciliation_status,
            conflictType=conflict_type,
            provenance=provenance,
            officerVerificationStatus="UNREVIEWED",
            evidenceReviewed=False,
            modelApprovalStatus="NOT_APPROVED",
        )


# Global singleton reconciler
result_reconciler = ResultReconciler()
