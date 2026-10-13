"""Operational threshold selection engine balancing safety recall and false alarms."""

from typing import Any, Dict, List, Optional
import numpy as np
from pydantic import BaseModel, Field
from app.models.model_metadata import FIRE_INSPECTION_CLASSES
from app.models.nms import compute_iou


class ClassThresholdDecision(BaseModel):
    """Documented rationale and metric trade-offs for an operational detection threshold."""

    classId: int
    className: str
    recommendedThreshold: float
    objective: str = Field(description="'HIGH_RECALL_SAFETY', 'MAX_F1', or 'HIGH_PRECISION'")
    precisionAtThreshold: float
    recallAtThreshold: float
    f1AtThreshold: float
    falsePositiveTradeoff: str
    falseNegativeTradeoff: str
    rationale: str


class ThresholdSelector:
    """Analyzes validation detections to establish class-specific confidence thresholds."""

    CANDIDATE_THRESHOLDS = [round(t, 2) for t in np.arange(0.20, 0.95, 0.05).tolist()]

    @classmethod
    def optimize_thresholds(
        cls,
        predictions_by_image: Dict[str, List[Dict[str, Any]]],
        ground_truth_by_image: Dict[str, List[Dict[str, Any]]],
        objective: str = "HIGH_RECALL_SAFETY",
        iou_threshold: float = 0.50,
    ) -> Dict[int, ClassThresholdDecision]:
        """Calculates optimal threshold per fire-safety class based on operational objectives."""
        decisions: Dict[int, ClassThresholdDecision] = {}

        for cid, meta in FIRE_INSPECTION_CLASSES.items():
            cname = meta.name

            best_thresh = 0.50
            best_score = -1.0
            best_prec = 0.0
            best_rec = 0.0
            best_f1 = 0.0

            # Evaluate each candidate threshold
            for t in cls.CANDIDATE_THRESHOLDS:
                tp, fp, fn = 0, 0, 0

                for img_key, gts in ground_truth_by_image.items():
                    cls_gts = [g for g in gts if g.get("className") == cname]
                    preds = [
                        p for p in predictions_by_image.get(img_key, [])
                        if p.get("className") == cname and p.get("confidence", 0.0) >= t
                    ]

                    matched_gt = set()
                    for p in preds:
                        best_iou = 0.0
                        best_idx = -1
                        for g_idx, g in enumerate(cls_gts):
                            if g_idx in matched_gt:
                                continue
                            iou = compute_iou(p["boundingBox"], g["boundingBox"])
                            if iou > best_iou:
                                best_iou = iou
                                best_idx = g_idx

                        if best_iou >= iou_threshold and best_idx != -1:
                            tp += 1
                            matched_gt.add(best_idx)
                        else:
                            fp += 1

                    fn += len(cls_gts) - len(matched_gt)

                prec = tp / (tp + fp) if (tp + fp) > 0 else 0.0
                rec = tp / (tp + fn) if (tp + fn) > 0 else 0.0
                f1 = (2 * prec * rec) / (prec + rec) if (prec + rec) > 0 else 0.0

                # Score metric based on objective
                if objective == "HIGH_RECALL_SAFETY":
                    # F2-score weights recall twice as much as precision (prioritizing safety)
                    score = (5 * prec * rec) / (4 * prec + rec) if (4 * prec + rec) > 0 else 0.0
                elif objective == "HIGH_PRECISION":
                    # F0.5-score weights precision higher
                    score = (1.25 * prec * rec) / (0.25 * prec + rec) if (0.25 * prec + rec) > 0 else 0.0
                else:  # MAX_F1
                    score = f1

                if score > best_score:
                    best_score = score
                    best_thresh = t
                    best_prec = round(prec, 4)
                    best_rec = round(rec, 4)
                    best_f1 = round(f1, 4)

            # Document tradeoff explanation
            if objective == "HIGH_RECALL_SAFETY":
                fp_desc = "Allows moderate advisory false detections for on-site officer review."
                fn_desc = "Strictly minimizes missed fire hazards (false negatives)."
                rationale = (
                    f"Selected threshold {best_thresh} maximizes safety recall ({best_rec:.2f}) "
                    f"to prevent overlooking critical life-safety defects."
                )
            else:
                fp_desc = "Suppresses marginal visual defect false alarms."
                fn_desc = "May miss subtle or partially occluded defects."
                rationale = f"Selected threshold {best_thresh} optimizes balanced detection performance."

            decisions[cid] = ClassThresholdDecision(
                classId=cid,
                className=cname,
                recommendedThreshold=best_thresh,
                objective=objective,
                precisionAtThreshold=best_prec,
                recallAtThreshold=best_rec,
                f1AtThreshold=best_f1,
                falsePositiveTradeoff=fp_desc,
                falseNegativeTradeoff=fn_desc,
                rationale=rationale,
            )

        return decisions
