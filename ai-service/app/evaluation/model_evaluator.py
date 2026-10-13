"""Evaluation metrics, precision/recall calculations, and benchmark interfaces."""

from typing import Dict, List, Any, Optional
from pydantic import BaseModel, Field
from app.models.nms import compute_iou


class ClassEvaluationMetric(BaseModel):
    """Evaluation metrics for a single inspection class."""

    className: str
    truePositives: int = 0
    falsePositives: int = 0
    falseNegatives: int = 0
    precision: float = 0.0
    recall: float = 0.0
    f1Score: float = 0.0


class ModelEvaluationReport(BaseModel):
    """Aggregate benchmark evaluation report across all classes."""

    modelName: str
    modelVersion: str
    datasetName: str = "NOT_AVAILABLE"
    status: str = "BENCHMARK_NOT_YET_ESTABLISHED"
    totalEvaluated: int = 0
    meanPrecision: float = 0.0
    meanRecall: float = 0.0
    meanF1Score: float = 0.0
    classMetrics: Dict[str, ClassEvaluationMetric] = Field(default_factory=dict)
    warnings: List[str] = Field(default_factory=list)


class ModelEvaluator:
    """Computes precision, recall, F1, and IoU matching for computer vision models."""

    @classmethod
    def evaluate_detections(
        cls,
        predictions: List[Dict[str, Any]],
        ground_truth: List[Dict[str, Any]],
        iou_threshold: float = 0.50,
        model_name: str = "firesphere-inspection-cv",
        model_version: str = "0.3.0-phase3",
    ) -> ModelEvaluationReport:
        """Evaluates predicted bounding boxes against ground truth annotations.

        predictions & ground_truth items:
        {'className': str, 'confidence': float, 'boundingBox': {'xMin': float, ...}}
        """
        if not ground_truth and not predictions:
            return ModelEvaluationReport(
                modelName=model_name,
                modelVersion=model_version,
                status="NO_DATA_TO_EVALUATE",
                warnings=["Neither predictions nor ground truth provided."],
            )

        # Collect unique classes
        all_classes = set(
            [p["className"] for p in predictions] + [g["className"] for g in ground_truth]
        )

        class_metrics: Dict[str, ClassEvaluationMetric] = {}

        for cls_name in all_classes:
            cls_preds = [p for p in predictions if p.get("className") == cls_name]
            cls_gts = [g for g in ground_truth if g.get("className") == cls_name]

            matched_gt = set()
            tp = 0
            fp = 0

            # Match each prediction with the highest overlapping ground truth
            for pred in sorted(cls_preds, key=lambda p: p.get("confidence", 0.0), reverse=True):
                best_iou = 0.0
                best_gt_idx = -1

                for idx, gt in enumerate(cls_gts):
                    if idx in matched_gt:
                        continue
                    iou = compute_iou(pred["boundingBox"], gt["boundingBox"])
                    if iou > best_iou:
                        best_iou = iou
                        best_gt_idx = idx

                if best_iou >= iou_threshold and best_gt_idx != -1:
                    tp += 1
                    matched_gt.add(best_gt_idx)
                else:
                    fp += 1

            fn = len(cls_gts) - len(matched_gt)

            precision = round(tp / (tp + fp), 4) if (tp + fp) > 0 else 0.0
            recall = round(tp / (tp + fn), 4) if (tp + fn) > 0 else 0.0
            f1 = (
                round(2 * (precision * recall) / (precision + recall), 4)
                if (precision + recall) > 0
                else 0.0
            )

            class_metrics[cls_name] = ClassEvaluationMetric(
                className=cls_name,
                truePositives=tp,
                falsePositives=fp,
                falseNegatives=fn,
                precision=precision,
                recall=recall,
                f1Score=f1,
            )

        # Compute aggregate means
        n_classes = len(class_metrics)
        m_prec = round(sum(m.precision for m in class_metrics.values()) / n_classes, 4) if n_classes else 0.0
        m_rec = round(sum(m.recall for m in class_metrics.values()) / n_classes, 4) if n_classes else 0.0
        m_f1 = round(sum(m.f1Score for m in class_metrics.values()) / n_classes, 4) if n_classes else 0.0

        return ModelEvaluationReport(
            modelName=model_name,
            modelVersion=model_version,
            datasetName="TEST_BENCHMARK",
            status="EVALUATION_COMPLETED",
            totalEvaluated=len(predictions),
            meanPrecision=m_prec,
            meanRecall=m_rec,
            meanF1Score=m_f1,
            classMetrics=class_metrics,
        )
