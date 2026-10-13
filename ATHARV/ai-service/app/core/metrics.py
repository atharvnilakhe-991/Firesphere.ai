"""Observability and metrics collection module for FireSphere AI Service."""

from collections import defaultdict
import threading
import time
from typing import Any, Dict, List, Optional


class MetricsCollector:
    """Thread-safe in-memory metrics collector with Prometheus exposition capability.

    Provides production observability hooks without introducing mandatory heavy external dependencies.
    """

    def __init__(self):
        self._lock = threading.Lock()
        self._counters: Dict[str, int] = defaultdict(int)
        self._gauges: Dict[str, float] = defaultdict(float)
        self._durations: Dict[str, List[float]] = defaultdict(list)
        self._max_history = 1000

    def inc_counter(self, name: str, value: int = 1, labels: Optional[Dict[str, str]] = None) -> None:
        """Increments a counter metric with optional labels."""
        metric_key = self._format_key(name, labels)
        with self._lock:
            self._counters[metric_key] += value

    def set_gauge(self, name: str, value: float, labels: Optional[Dict[str, str]] = None) -> None:
        """Sets a gauge metric."""
        metric_key = self._format_key(name, labels)
        with self._lock:
            self._gauges[metric_key] = value

    def observe_duration(self, name: str, duration_seconds: float, labels: Optional[Dict[str, str]] = None) -> None:
        """Records an execution duration sample."""
        metric_key = self._format_key(name, labels)
        with self._lock:
            hist = self._durations[metric_key]
            hist.append(round(duration_seconds, 4))
            if len(hist) > self._max_history:
                del hist[: len(hist) - self._max_history]

    def get_summary(self) -> Dict[str, Any]:
        """Returns JSON-serializable snapshot of collected metrics."""
        with self._lock:
            dur_stats = {}
            for k, samples in self._durations.items():
                if samples:
                    dur_stats[k] = {
                        "count": len(samples),
                        "avgSeconds": round(sum(samples) / len(samples), 4),
                        "minSeconds": min(samples),
                        "maxSeconds": max(samples),
                        "lastSeconds": samples[-1],
                    }
                else:
                    dur_stats[k] = {"count": 0, "avgSeconds": 0.0}

            return {
                "counters": dict(self._counters),
                "gauges": dict(self._gauges),
                "durations": dur_stats,
            }

    def to_prometheus_format(self) -> str:
        """Renders metrics in standard Prometheus text exposition format."""
        lines = []
        with self._lock:
            # Counters
            for key, val in sorted(self._counters.items()):
                lines.append(f"# TYPE {key.split('{')[0]} counter")
                lines.append(f"{key} {val}")

            # Gauges
            for key, val in sorted(self._gauges.items()):
                lines.append(f"# TYPE {key.split('{')[0]} gauge")
                lines.append(f"{key} {val}")

            # Duration summaries
            for key, samples in sorted(self._durations.items()):
                base_name = key.split("{")[0]
                if samples:
                    total = sum(samples)
                    count = len(samples)
                    lines.append(f"# TYPE {base_name} summary")
                    lines.append(f"{base_name}_sum {total:.4f}")
                    lines.append(f"{base_name}_count {count}")

        return "\n".join(lines) + "\n"

    def reset_for_tests(self) -> None:
        """Resets all metrics for clean unit testing."""
        with self._lock:
            self._counters.clear()
            self._gauges.clear()
            self._durations.clear()

    @staticmethod
    def _format_key(name: str, labels: Optional[Dict[str, str]]) -> str:
        if not labels:
            return name
        label_str = ",".join(f'{k}="{v}"' for k, v in sorted(labels.items()))
        return f"{name}{{{label_str}}}"


# Global singleton collector
metrics = MetricsCollector()
