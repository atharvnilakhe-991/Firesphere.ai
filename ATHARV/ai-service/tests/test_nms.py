"""Tests for IoU calculation and Non-Maximum Suppression (NMS)."""

from app.models.nms import apply_non_maximum_suppression, compute_iou


def test_compute_iou_identical_boxes():
    """Verifies that identical bounding boxes have an IoU of 1.0."""
    box = {"xMin": 0.1, "yMin": 0.1, "xMax": 0.5, "yMax": 0.5}
    iou = compute_iou(box, box)
    assert abs(iou - 1.0) < 1e-5


def test_compute_iou_disjoint_boxes():
    """Verifies that completely non-overlapping boxes have an IoU of 0.0."""
    box1 = {"xMin": 0.0, "yMin": 0.0, "xMax": 0.2, "yMax": 0.2}
    box2 = {"xMin": 0.5, "yMin": 0.5, "xMax": 0.8, "yMax": 0.8}
    iou = compute_iou(box1, box2)
    assert iou == 0.0


def test_compute_iou_partial_overlap():
    """Verifies that partially overlapping boxes yield accurate IoU values."""
    # Box1: [0, 0, 2, 2] -> Area = 4
    # Box2: [1, 1, 3, 3] -> Area = 4
    # Intersection: [1, 1, 2, 2] -> Area = 1
    # Union: 4 + 4 - 1 = 7. IoU = 1/7 ~= 0.142857
    box1 = {"xMin": 0.0, "yMin": 0.0, "xMax": 2.0, "yMax": 2.0}
    box2 = {"xMin": 1.0, "yMin": 1.0, "xMax": 3.0, "yMax": 3.0}
    iou = compute_iou(box1, box2)
    assert abs(iou - (1.0 / 7.0)) < 1e-4


def test_nms_suppresses_redundant_boxes():
    """Verifies that duplicate detections with IoU > threshold are suppressed, keeping highest confidence."""
    detections = [
        {
            "className": "FIRE_EXTINGUISHER_MISSING",
            "confidence": 0.95,
            "boundingBox": {"xMin": 0.10, "yMin": 0.10, "xMax": 0.40, "yMax": 0.50},
        },
        {
            "className": "FIRE_EXTINGUISHER_MISSING",
            "confidence": 0.82,  # Subsumed redundant detection
            "boundingBox": {"xMin": 0.11, "yMin": 0.09, "xMax": 0.39, "yMax": 0.51},
        },
        {
            "className": "FIRE_EXTINGUISHER_MISSING",
            "confidence": 0.88,  # Different location
            "boundingBox": {"xMin": 0.60, "yMin": 0.60, "xMax": 0.90, "yMax": 0.90},
        },
    ]

    filtered = apply_non_maximum_suppression(detections, iou_threshold=0.45)
    assert len(filtered) == 2
    confidences = [d["confidence"] for d in filtered]
    assert 0.95 in confidences
    assert 0.88 in confidences
    assert 0.82 not in confidences


def test_nms_preserves_different_classes():
    """Verifies that overlapping detections of different classes are both preserved."""
    detections = [
        {
            "className": "EMERGENCY_EXIT_OBSTRUCTED",
            "confidence": 0.90,
            "boundingBox": {"xMin": 0.20, "yMin": 0.20, "xMax": 0.60, "yMax": 0.80},
        },
        {
            "className": "EXIT_SIGNAGE_MISSING",
            "confidence": 0.85,
            "boundingBox": {"xMin": 0.21, "yMin": 0.21, "xMax": 0.59, "yMax": 0.79},
        },
    ]

    filtered = apply_non_maximum_suppression(detections, iou_threshold=0.45)
    assert len(filtered) == 2
    class_names = {d["className"] for d in filtered}
    assert "EMERGENCY_EXIT_OBSTRUCTED" in class_names
    assert "EXIT_SIGNAGE_MISSING" in class_names
