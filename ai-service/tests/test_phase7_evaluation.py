"""Automated tests for Phase 7 Comprehensive Evaluation, Calibration, and Threshold Optimization."""

import pytest
from app.evaluation.calibration import (
    CalibrationEvaluator,
    FittedPlattCalibrator,
    FittedTemperatureCalibrator,
)
from app.evaluation.comprehensive_evaluator import ComprehensiveEvaluator
from app.evaluation.threshold_selector import ThresholdSelector


def test_comprehensive_evaluator_perfect_matches():
    """Verifies that identical predictions and ground truths yield 1.0 mAP and clean confusion matrix."""
    preds = {
        "img_001": [
            {
                "className": "FIRE_EXTINGUISHER_MISSING",
                "confidence": 0.95,
                "boundingBox": {"xMin": 0.1, "yMin": 0.1, "xMax": 0.4, "yMax": 0.4},
            }
        ]
    }
    gts = {
        "img_001": [
            {
                "className": "FIRE_EXTINGUISHER_MISSING",
                "boundingBox": {"xMin": 0.1, "yMin": 0.1, "xMax": 0.4, "yMax": 0.4},
            }
        ]
    }

    report = ComprehensiveEvaluator.evaluate(preds, gts)
    assert report.evaluationStatus == "COMPLETED"
    assert report.totalGroundTruthObjects == 1
    assert report.totalPredictedObjects == 1
    assert report.mAP50 == 1.0
    assert report.meanPrecision == 1.0
    assert report.meanRecall == 1.0

    ext_metric = report.perClassMetrics["FIRE_EXTINGUISHER_MISSING"]
    assert ext_metric.truePositives == 1
    assert ext_metric.falsePositives == 0
    assert ext_metric.falseNegatives == 0
    assert ext_metric.precision == 1.0
    assert ext_metric.recall == 1.0
    assert report.confusionMatrix is not None


def test_comprehensive_evaluator_no_test_data_honest_status():
    """Verifies that empty test benchmarks return NO_TEST_DATA status with zero metrics."""
    report = ComprehensiveEvaluator.evaluate(
        predictions_by_image={},
        ground_truth_by_image={},
    )
    assert report.evaluationStatus == "NO_TEST_DATA"
    assert report.mAP50 == 0.0
    assert report.mAP50_95 == 0.0
    assert len(report.warnings) > 0


def test_calibration_evaluator_ece_and_brier_scores():
    """Verifies Expected Calibration Error (ECE) and reliability bin calculation."""
    # 10 well-calibrated samples
    confs = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95]
    accs = [0, 0, 0, 0, 1, 1, 1, 1, 1, 1]

    metrics = CalibrationEvaluator.compute_ece(confs, accs, num_bins=5)
    assert metrics.expectedCalibrationError >= 0.0
    assert metrics.maximumCalibrationError >= 0.0
    assert metrics.brierScore >= 0.0
    assert len(metrics.binCounts) == 5


def test_fitted_temperature_calibrator():
    """Verifies post-hoc temperature scaling parameter search and probability calibration."""
    calibrator = FittedTemperatureCalibrator()
    assert calibrator.calibrate(0.8)[1] == "NOT_CALIBRATED"

    # Synthetic overconfident predictions: high confidence but 50% accuracy
    confs = [0.9] * 20
    labels = [1, 0] * 10

    t = calibrator.fit(confs, labels)
    assert t > 1.0, "Overconfident predictions should result in temperature T > 1.0"
    calibrated_val, status = calibrator.calibrate(0.9)
    assert status == "CALIBRATED_TEMPERATURE_SCALING"
    # Calibrated probability should be softened closer to 0.50
    assert calibrated_val < 0.9


def test_threshold_selector_optimizes_for_safety_recall():
    """Verifies that HIGH_RECALL_SAFETY objective selects thresholds minimizing false negatives."""
    # Synthetic validation set: detections at varying confidence
    preds = {
        "img_1": [
            {"className": "EMERGENCY_EXIT_OBSTRUCTED", "confidence": 0.35, "boundingBox": {"xMin": 0.1, "yMin": 0.1, "xMax": 0.5, "yMax": 0.5}},
            {"className": "EMERGENCY_EXIT_OBSTRUCTED", "confidence": 0.85, "boundingBox": {"xMin": 0.6, "yMin": 0.6, "xMax": 0.9, "yMax": 0.9}},
        ]
    }
    gts = {
        "img_1": [
            {"className": "EMERGENCY_EXIT_OBSTRUCTED", "boundingBox": {"xMin": 0.1, "yMin": 0.1, "xMax": 0.5, "yMax": 0.5}},
            {"className": "EMERGENCY_EXIT_OBSTRUCTED", "boundingBox": {"xMin": 0.6, "yMin": 0.6, "xMax": 0.9, "yMax": 0.9}},
        ]
    }

    decisions = ThresholdSelector.optimize_thresholds(preds, gts, objective="HIGH_RECALL_SAFETY")
    exit_decision = decisions[3]  # EMERGENCY_EXIT_OBSTRUCTED is class 3
    assert exit_decision.classId == 3
    assert exit_decision.objective == "HIGH_RECALL_SAFETY"
    # Threshold must be low enough (<= 0.35) to capture both true hazards
    assert exit_decision.recommendedThreshold <= 0.35
    assert exit_decision.recallAtThreshold == 1.0
    assert "safety recall" in exit_decision.rationale
