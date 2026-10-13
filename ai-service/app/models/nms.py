"""Non-Maximum Suppression (NMS) and IoU calculation utilities."""

from typing import List, Dict, Any, Tuple
import numpy as np


def compute_iou(box1: Dict[str, float], box2: Dict[str, float]) -> float:
    """Computes Intersection over Union (IoU) between two bounding boxes.

    Boxes must have 'xMin', 'yMin', 'xMax', 'yMax' keys.
    """
    x1 = max(box1["xMin"], box2["xMin"])
    y1 = max(box1["yMin"], box2["yMin"])
    x2 = min(box1["xMax"], box2["xMax"])
    y2 = min(box1["yMax"], box2["yMax"])

    intersection_w = max(0.0, x2 - x1)
    intersection_h = max(0.0, y2 - y1)
    intersection_area = intersection_w * intersection_h

    area1 = max(0.0, box1["xMax"] - box1["xMin"]) * max(0.0, box1["yMax"] - box1["yMin"])
    area2 = max(0.0, box2["xMax"] - box2["xMin"]) * max(0.0, box2["yMax"] - box2["yMin"])

    union_area = area1 + area2 - intersection_area
    if union_area <= 0.0:
        return 0.0

    return float(intersection_area / union_area)


def apply_non_maximum_suppression(
    detections: List[Dict[str, Any]],
    iou_threshold: float = 0.45,
) -> List[Dict[str, Any]]:
    """Applies Non-Maximum Suppression to filter overlapping detections of the same class.

    Expects each detection to be a dict containing:
    'className': str,
    'confidence': float,
    'boundingBox': {'xMin': float, 'yMin': float, 'xMax': float, 'yMax': float}
    """
    if not detections:
        return []

    # Group detections by class name
    by_class: Dict[str, List[Dict[str, Any]]] = {}
    for det in detections:
        cls_name = det.get("className", "UNKNOWN")
        by_class.setdefault(cls_name, []).append(det)

    survived_detections: List[Dict[str, Any]] = []

    for cls_name, cls_dets in by_class.items():
        # Sort descending by confidence score
        sorted_dets = sorted(cls_dets, key=lambda d: d.get("confidence", 0.0), reverse=True)
        keep: List[Dict[str, Any]] = []

        while sorted_dets:
            best = sorted_dets.pop(0)
            keep.append(best)

            # Suppress any remaining detection that overlaps with best by > iou_threshold
            best_box = best.get("boundingBox")
            if not best_box:
                continue

            filtered_dets: List[Dict[str, Any]] = []
            for candidate in sorted_dets:
                cand_box = candidate.get("boundingBox")
                if not cand_box:
                    filtered_dets.append(candidate)
                    continue

                iou = compute_iou(best_box, cand_box)
                if iou <= iou_threshold:
                    filtered_dets.append(candidate)

            sorted_dets = filtered_dets

        survived_detections.extend(keep)

    return survived_detections
