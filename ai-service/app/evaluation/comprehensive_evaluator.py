"""Comprehensive evaluation engine computing mAP@50, mAP@50:95, and Confusion Matrices."""

from collections import defaultdict
from typing import Any, Dict, List, Optional, Set, Tuple
import numpy as np
from pydantic import BaseModel, Field
from app.models.model_metadata import FIRE_INSPECTION_CLASSES
from app.models.nms import compute_iou


class ClassDetailedMetric(BaseModel):
    """Fine-grained metrics for a single inspection class."""

    classId: int
    className: str
    support: int = 0
    truePositives: int = 0
    falsePositives: int = 0
    falseNegatives: int = 0
    precision: float = 0.0
    recall: float = 0.0
    f1Score: float = 0.0
    ap50: float = 0.0
    ap50_95: float = 0.0


class ConfusionMatrixData(BaseModel):
    """Multi-class confusion matrix including background false detections."""

    classNames: List[str]
    matrix: List[List[int]] = Field(
        description="2D matrix of shape (C+1, C+1) where last index is background"
    )


class ComprehensiveEvaluationReport(BaseModel):
    """Rigorous evaluation report detailing mAP, class APs, and confusion matrices."""

    modelId: str
    modelVersion: str
    datasetName: str
    evaluationStatus: str = Field(description="'COMPLETED', 'NO_TEST_DATA', or 'NOT_EVALUATED'")
    totalTestImages: int = 0
    totalGroundTruthObjects: int = 0
    totalPredictedObjects: int = 0
    mAP50: float = 0.0
    mAP50_95: float = 0.0
    meanPrecision: float = 0.0
    meanRecall: float = 0.0
    meanF1: float = 0.0
    perClassMetrics: Dict[str, ClassDetailedMetric] = Field(default_factory=dict)
    confusionMatrix: Optional[ConfusionMatrixData] = None
    warnings: List[str] = Field(default_factory=list)


class ComprehensiveEvaluator:
    """Evaluates multi-class fire detection models across standard COCO/YOLO metric protocols."""

    IOU_THRESHOLDS_50_95 = [round(x, 2) for x in np.arange(0.50, 0.96, 0.05).tolist()]

    @classmethod
    def compute_average_precision(
        cls,
        precisions: List[float],
        recalls: List[float],
    ) -> float:
        """Computes 101-point interpolated Average Precision (AP)."""
        if not precisions or not recalls:
            return 0.0

        # Monotonically decreasing precision envelope
        mrec = [0.0] + list(recalls) + [1.0]
        mpre = [0.0] + list(precisions) + [0.0]

        for i in range(len(mpre) - 2, -1, -1):
            mpre[i] = max(mpre[i], mpre[i + 1])

        # 101-point interpolation
        recall_levels = np.linspace(0.0, 1.0, 101)
        interpolated_precisions = []

        for r in recall_levels:
            idx = 0
            while idx < len(mrec) and mrec[idx] < r:
                idx += 1
            if idx < len(mpre):
                interpolated_precisions.append(mpre[idx])
            else:
                interpolated_precisions.append(0.0)

        return float(round(float(np.mean(interpolated_precisions)), 4))

    @classmethod
    def evaluate(
        cls,
        predictions_by_image: Dict[str, List[Dict[str, Any]]],
        ground_truth_by_image: Dict[str, List[Dict[str, Any]]],
        model_id: str = "firesphere-inspection-cv",
        model_version: str = "0.3.0-phase3",
        dataset_name: str = "held-out-test-dataset",
        target_classes: Optional[Dict[int, str]] = None,
    ) -> ComprehensiveEvaluationReport:
        """Evaluates bounding-box detections against ground-truth labels across images."""
        all_image_keys = set(predictions_by_image.keys()).union(ground_truth_by_image.keys())

        total_gt = sum(len(gts) for gts in ground_truth_by_image.values())
        total_pred = sum(len(preds) for preds in predictions_by_image.values())

        if total_gt == 0 and total_pred == 0:
            return ComprehensiveEvaluationReport(
                modelId=model_id,
                modelVersion=model_version,
                datasetName=dataset_name,
                evaluationStatus="NO_TEST_DATA",
                warnings=["No ground truth or prediction objects provided for evaluation."],
            )

        # Catalog classes mapping
        if target_classes is not None:
            active_class_map = target_classes
        else:
            active_class_map = {c.classId: c.name for c in FIRE_INSPECTION_CLASSES.values()}

        per_class_results: Dict[str, ClassDetailedMetric] = {}

        # Confusion Matrix initialization
        num_classes = len(active_class_map)
        cm = np.zeros((num_classes + 1, num_classes + 1), dtype=int)
        class_name_list = [active_class_map[i] for i in sorted(active_class_map.keys())] + ["BACKGROUND"]

        aps_50: List[float] = []
        aps_50_95: List[float] = []
        precisions_50: List[float] = []
        recalls_50: List[float] = []
        f1s_50: List[float] = []

        for cid in sorted(active_class_map.keys()):
            cname = active_class_map[cid]

            # Collect all ground truths and predictions for this specific class
            cls_gt_count = 0
            cls_preds: List[Tuple[float, bool, str, Dict[str, float]]] = []  # (conf, matched, img, bbox)

            # Evaluate at IoU 0.50
            tp_50 = 0
            fp_50 = 0
            fn_50 = 0

            # Store matches across multiple IoU thresholds for AP50:95
            iou_aps: List[float] = []

            for iou_thresh in cls.IOU_THRESHOLDS_50_95:
                thresh_tp = 0
                thresh_fp = 0
                thresh_gt = 0

                for img_key in all_image_keys:
                    preds = [p for p in predictions_by_image.get(img_key, []) if p.get("className") == cname]
                    gts = [g for g in ground_truth_by_image.get(img_key, []) if g.get("className") == cname]

                    thresh_gt += len(gts)
                    sorted_preds = sorted(preds, key=lambda x: x.get("confidence", 0.0), reverse=True)
                    matched_gts: Set[int] = set()

                    for pred in sorted_preds:
                        best_iou = 0.0
                        best_idx = -1
                        for g_idx, gt in enumerate(gts):
                            if g_idx in matched_gts:
                                continue
                            iou = compute_iou(pred["boundingBox"], gt["boundingBox"])
                            if iou > best_iou:
                                best_iou = iou
                                best_idx = g_idx

                        if best_iou >= iou_thresh and best_idx != -1:
                            thresh_tp += 1
                            matched_gts.add(best_idx)
                        else:
                            thresh_fp += 1

                p = thresh_tp / (thresh_tp + thresh_fp) if (thresh_tp + thresh_fp) > 0 else 0.0
                r = thresh_tp / thresh_gt if thresh_gt > 0 else 0.0
                iou_aps.append(cls.compute_average_precision([p], [r]))

                # Record primary metrics at threshold 0.50
                if abs(iou_thresh - 0.50) < 1e-4:
                    tp_50 = thresh_tp
                    fp_50 = thresh_fp
                    fn_50 = thresh_gt - thresh_tp
                    cls_gt_count = thresh_gt

            # Populate Confusion Matrix at 0.50 threshold
            cm[cid, cid] += tp_50
            cm[num_classes, cid] += fn_50  # Missed (predicted as background)
            cm[cid, num_classes] += fp_50  # False alarm (ground truth was background)

            prec = round(tp_50 / (tp_50 + fp_50), 4) if (tp_50 + fp_50) > 0 else 0.0
            rec = round(tp_50 / (tp_50 + fn_50), 4) if (tp_50 + fn_50) > 0 else 0.0
            f1 = round(2 * (prec * rec) / (prec + rec), 4) if (prec + rec) > 0 else 0.0
            ap50 = iou_aps[0] if iou_aps else 0.0
            ap50_95 = round(float(np.mean(iou_aps)), 4) if iou_aps else 0.0

            per_class_results[cname] = ClassDetailedMetric(
                classId=cid,
                className=cname,
                support=cls_gt_count,
                truePositives=tp_50,
                falsePositives=fp_50,
                falseNegatives=fn_50,
                precision=prec,
                recall=rec,
                f1Score=f1,
                ap50=ap50,
                ap50_95=ap50_95,
            )

            if cls_gt_count > 0:
                aps_50.append(ap50)
                aps_50_95.append(ap50_95)
                precisions_50.append(prec)
                recalls_50.append(rec)
                f1s_50.append(f1)

        mean_ap50 = round(float(np.mean(aps_50)), 4) if aps_50 else 0.0
        mean_ap50_95 = round(float(np.mean(aps_50_95)), 4) if aps_50_95 else 0.0
        mean_prec = round(float(np.mean(precisions_50)), 4) if precisions_50 else 0.0
        mean_rec = round(float(np.mean(recalls_50)), 4) if recalls_50 else 0.0
        mean_f1 = round(float(np.mean(f1s_50)), 4) if f1s_50 else 0.0

        return ComprehensiveEvaluationReport(
            modelId=model_id,
            modelVersion=model_version,
            datasetName=dataset_name,
            evaluationStatus="COMPLETED",
            totalTestImages=len(all_image_keys),
            totalGroundTruthObjects=total_gt,
            totalPredictedObjects=total_pred,
            mAP50=mean_ap50,
            mAP50_95=mean_ap50_95,
            meanPrecision=mean_prec,
            meanRecall=mean_rec,
            meanF1=mean_f1,
            perClassMetrics=per_class_results,
            confusionMatrix=ConfusionMatrixData(
                classNames=class_name_list,
                matrix=cm.tolist(),
            ),
        )
