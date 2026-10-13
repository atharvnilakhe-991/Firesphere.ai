"""Tests for model evaluation metrics, precision, recall, and benchmark interfaces."""

from app.evaluation.model_evaluator import ModelEvaluator


def test_evaluator_perfect_matches():
    """Verifies that identical predicted and ground truth boxes yield 100% precision and recall."""
    box = {"xMin": 0.2, "yMin": 0.2, "xMax": 0.5, "yMax": 0.5}
    predictions = [{"className": "FIRE_EXTINGUISHER_MISSING", "confidence": 0.95, "boundingBox": box}]
    ground_truth = [{"className": "FIRE_EXTINGUISHER_MISSING", "boundingBox": box}]

    report = ModelEvaluator.evaluate_detections(predictions, ground_truth, iou_threshold=0.50)

    assert report.status == "EVALUATION_COMPLETED"
    metric = report.classMetrics["FIRE_EXTINGUISHER_MISSING"]
    assert metric.truePositives == 1
    assert metric.falsePositives == 0
    assert metric.falseNegatives == 0
    assert metric.precision == 1.0
    assert metric.recall == 1.0
    assert metric.f1Score == 1.0


def test_evaluator_false_positive():
    """Verifies that an ungrounded prediction results in a false positive."""
    pred_box = {"xMin": 0.1, "yMin": 0.1, "xMax": 0.3, "yMax": 0.3}
    gt_box = {"xMin": 0.7, "yMin": 0.7, "xMax": 0.9, "yMax": 0.9}

    predictions = [{"className": "EMERGENCY_EXIT_OBSTRUCTED", "confidence": 0.85, "boundingBox": pred_box}]
    ground_truth = [{"className": "EMERGENCY_EXIT_OBSTRUCTED", "boundingBox": gt_box}]

    report = ModelEvaluator.evaluate_detections(predictions, ground_truth, iou_threshold=0.50)

    metric = report.classMetrics["EMERGENCY_EXIT_OBSTRUCTED"]
    assert metric.truePositives == 0
    assert metric.falsePositives == 1
    assert metric.falseNegatives == 1
    assert metric.precision == 0.0
    assert metric.recall == 0.0


def test_evaluator_empty_data():
    """Verifies that empty evaluation inputs are handled cleanly without exceptions."""
    report = ModelEvaluator.evaluate_detections([], [])
    assert report.status == "NO_DATA_TO_EVALUATE"
    assert len(report.warnings) > 0
