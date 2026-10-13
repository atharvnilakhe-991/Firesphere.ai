"""Confidence calibration algorithms, fitting engines, and Expected Calibration Error (ECE)."""

import math
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
from pydantic import BaseModel, Field
from app.core.logging import logger
from app.intelligence.confidence import BaseConfidenceCalibrator, ConfidenceInterpreter


class CalibrationMetrics(BaseModel):
    """Statistical measurement of model confidence calibration."""

    expectedCalibrationError: float = Field(description="ECE across M confidence bins")
    maximumCalibrationError: float = Field(description="MCE (worst bin deviation)")
    brierScore: float = Field(description="Mean squared error of probabilistic predictions")
    binCount: int = 10
    binAccuracies: List[float] = Field(default_factory=list)
    binConfidences: List[float] = Field(default_factory=list)
    binCounts: List[int] = Field(default_factory=list)
    calibrationStatus: str = Field(description="'CALIBRATED' or 'NOT_CALIBRATED'")


class CalibrationEvaluator:
    """Calculates Expected Calibration Error (ECE) and reliability diagram data."""

    @classmethod
    def compute_ece(
        cls,
        confidences: List[float],
        accuracies: List[int],  # 1 if correct detection, 0 if false positive
        num_bins: int = 10,
    ) -> CalibrationMetrics:
        """Computes ECE, MCE, and Brier score over validation predictions."""
        if not confidences or not accuracies or len(confidences) != len(accuracies):
            return CalibrationMetrics(
                expectedCalibrationError=0.0,
                maximumCalibrationError=0.0,
                brierScore=0.0,
                binCount=num_bins,
                calibrationStatus="NOT_CALIBRATED",
            )

        conf_arr = np.array(confidences, dtype=float)
        acc_arr = np.array(accuracies, dtype=float)
        n = len(conf_arr)

        bins = np.linspace(0.0, 1.0, num_bins + 1)
        bin_accs: List[float] = []
        bin_confs: List[float] = []
        bin_counts: List[int] = []

        ece = 0.0
        mce = 0.0

        for i in range(num_bins):
            b_min, b_max = bins[i], bins[i + 1]
            if i == num_bins - 1:
                mask = (conf_arr >= b_min) & (conf_arr <= b_max)
            else:
                mask = (conf_arr >= b_min) & (conf_arr < b_max)

            cnt = int(np.sum(mask))
            bin_counts.append(cnt)

            if cnt > 0:
                avg_acc = float(np.mean(acc_arr[mask]))
                avg_conf = float(np.mean(conf_arr[mask]))
                bin_accs.append(round(avg_acc, 4))
                bin_confs.append(round(avg_conf, 4))

                diff = abs(avg_acc - avg_conf)
                ece += (cnt / n) * diff
                mce = max(mce, diff)
            else:
                bin_accs.append(0.0)
                bin_confs.append(round((b_min + b_max) / 2.0, 4))

        brier = float(np.mean((conf_arr - acc_arr) ** 2))

        return CalibrationMetrics(
            expectedCalibrationError=round(ece, 4),
            maximumCalibrationError=round(mce, 4),
            brierScore=round(brier, 4),
            binCount=num_bins,
            binAccuracies=bin_accs,
            binConfidences=bin_confs,
            binCounts=bin_counts,
            calibrationStatus="CALIBRATED" if n >= 50 else "NOT_CALIBRATED",
        )


class FittedTemperatureCalibrator(BaseConfidenceCalibrator):
    """Post-hoc temperature scaling: p_cal = sigma(logit / T)."""

    def __init__(self, temperature: float = 1.0, is_fitted: bool = False):
        self.temperature = max(0.01, temperature)
        self.is_fitted = is_fitted
        self.status = "CALIBRATED_TEMPERATURE_SCALING" if is_fitted else "NOT_CALIBRATED"

    def fit(self, raw_confidences: List[float], labels: List[int]) -> float:
        """Finds optimal temperature parameter T using grid search minimizing NLL."""
        if len(raw_confidences) < 10:
            logger.warning("Insufficient samples to fit temperature scaling (<10 samples).")
            self.is_fitted = False
            self.status = "NOT_CALIBRATED"
            return 1.0

        # Transform probabilities to log-odds
        eps = 1e-6
        confs = np.clip(np.array(raw_confidences), eps, 1.0 - eps)
        logits = np.log(confs / (1.0 - confs))
        y = np.array(labels)

        best_t = 1.0
        best_nll = float("inf")

        for t_cand in np.linspace(0.1, 5.0, 100):
            scaled_logits = logits / t_cand
            scaled_probs = 1.0 / (1.0 + np.exp(-scaled_logits))
            nll = -np.mean(y * np.log(scaled_probs + eps) + (1.0 - y) * np.log(1.0 - scaled_probs + eps))
            if nll < best_nll:
                best_nll = nll
                best_t = float(t_cand)

        self.temperature = round(best_t, 3)
        self.is_fitted = True
        self.status = "CALIBRATED_TEMPERATURE_SCALING"
        logger.info(f"Fitted temperature scaling | T={self.temperature} | Best NLL={best_nll:.4f}")
        return self.temperature

    def calibrate(self, raw_confidence: float) -> Tuple[float, str]:
        conf = ConfidenceInterpreter.validate_bounds(raw_confidence)
        if not self.is_fitted:
            return conf, "NOT_CALIBRATED"

        eps = 1e-6
        c_clamped = min(1.0 - eps, max(eps, conf))
        logit = math.log(c_clamped / (1.0 - c_clamped))
        scaled_logit = logit / self.temperature
        calibrated = 1.0 / (1.0 + math.exp(-scaled_logit))
        return round(float(np.clip(calibrated, 0.0, 1.0)), 4), self.status


class FittedPlattCalibrator(BaseConfidenceCalibrator):
    """Logistic calibration: p_cal = 1 / (1 + exp(A * conf + B))."""

    def __init__(self, a: float = 1.0, b: float = 0.0, is_fitted: bool = False):
        self.a = a
        self.b = b
        self.is_fitted = is_fitted
        self.status = "CALIBRATED_PLATT" if is_fitted else "NOT_CALIBRATED"

    def fit(self, confidences: List[float], labels: List[int]) -> Tuple[float, float]:
        """Fits parameters A and B via logistic regression."""
        if len(confidences) < 10:
            self.is_fitted = False
            self.status = "NOT_CALIBRATED"
            return 1.0, 0.0

        x = np.array(confidences)
        y = np.array(labels)

        # Simple gradient descent for logistic regression
        a, b = 1.0, 0.0
        lr = 0.05
        for _ in range(200):
            p = 1.0 / (1.0 + np.exp(-(a * x + b)))
            err = p - y
            grad_a = np.mean(err * x)
            grad_b = np.mean(err)
            a -= lr * grad_a
            b -= lr * grad_b

        self.a = round(float(a), 4)
        self.b = round(float(b), 4)
        self.is_fitted = True
        self.status = "CALIBRATED_PLATT"
        return self.a, self.b

    def calibrate(self, raw_confidence: float) -> Tuple[float, str]:
        conf = ConfidenceInterpreter.validate_bounds(raw_confidence)
        if not self.is_fitted:
            return conf, "NOT_CALIBRATED"
        cal = 1.0 / (1.0 + math.exp(-(self.a * conf + self.b)))
        return round(float(np.clip(cal, 0.0, 1.0)), 4), self.status
