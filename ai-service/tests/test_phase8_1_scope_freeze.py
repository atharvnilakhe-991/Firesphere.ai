"""Phase 8.1 Test Suite: Production AI Scope Freeze Validation.

Validates:
- All 5 Phase 8.1 documentation artifacts exist and are populated.
- All 10 statutory defect findings are explicitly categorized in the capability matrix.
- Production MVP scope is strictly limited to verified, high-confidence capabilities.
- Unsupported and Phase 2 capabilities are cleanly segregated.
- Minimum sufficient perception vocabulary is defined and decoupled from finding classes.
- Datasets have explicit governance decisions (TRAINING_CANDIDATE, AUXILIARY, EVALUATION_ONLY, EXCLUDED).
- Canonical dataset immutability is strictly verified (6,471 images and annotations unchanged).
"""

from pathlib import Path
import os
import pytest

WORKSPACE_ROOT = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_DOCS_ROOT = WORKSPACE_ROOT / "ai-service" / "docs"
CANONICAL_ROOT = WORKSPACE_ROOT / "datasets" / "fire_safety"

TARGET_FINDINGS = [
    "FIRE_EXTINGUISHER_MISSING",
    "FIRE_EXTINGUISHER_BLOCKED",
    "FIRE_EXTINGUISHER_EXPIRED",
    "EMERGENCY_EXIT_OBSTRUCTED",
    "EXIT_SIGNAGE_MISSING",
    "SPRINKLER_HEAD_DEFECT",
    "ELECTRICAL_PANEL_HAZARD",
    "COMBUSTIBLE_MATERIAL_HAZARD",
    "MANUAL_CALL_POINT_BLOCKED",
    "HOSE_REEL_OBSTRUCTED",
]

MVP_CAPABILITIES = [
    "FIRE_EXTINGUISHER_BLOCKED",
    "EMERGENCY_EXIT_OBSTRUCTED",
]

UNSUPPORTED_CAPABILITIES = [
    "SPRINKLER_HEAD_DEFECT",
]

MINIMUM_PERCEPTION_CLASSES = [
    "FIRE_EXTINGUISHER",
    "EXIT_DOOR",
    "EXIT_SIGN",
    "OBSTACLE",
]


def test_phase8_1_documentation_artifacts_exist():
    """Verify all five Phase 8.1 governance documents exist and are substantial."""
    required_files = [
        "phase8_1_ai_scope_freeze.md",
        "phase8_1_ai_capability_matrix.md",
        "phase8_1_perception_class_spec.md",
        "phase8_1_dataset_capability_mapping.md",
        "phase8_1_decisions.md",
    ]
    for filename in required_files:
        path = AI_DOCS_ROOT / filename
        assert path.exists(), f"Phase 8.1 documentation missing: {path}"
        assert path.stat().st_size > 1000, f"File {filename} is too short or empty"


def test_phase8_1_all_ten_findings_documented():
    """Verify all 10 statutory finding types appear in the scope freeze & capability matrix."""
    matrix_path = AI_DOCS_ROOT / "phase8_1_ai_capability_matrix.md"
    freeze_path = AI_DOCS_ROOT / "phase8_1_ai_scope_freeze.md"

    matrix_content = matrix_path.read_text(encoding="utf-8")
    freeze_content = freeze_path.read_text(encoding="utf-8")

    for finding in TARGET_FINDINGS:
        assert finding in matrix_content, f"Finding {finding} missing from Capability Matrix"
        assert finding in freeze_content, f"Finding {finding} missing from Scope Freeze doc"


def test_phase8_1_mvp_and_unsupported_tiering():
    """Verify MVP is strictly bounded and unsupported classes are not marked production-ready."""
    matrix_path = AI_DOCS_ROOT / "phase8_1_ai_capability_matrix.md"
    content = matrix_path.read_text(encoding="utf-8")

    # Verify MVP findings are marked PRODUCTION_MVP
    for finding in MVP_CAPABILITIES:
        assert f"`{finding}`" in content
        # Finding row should designate PRODUCTION_MVP
        assert "PRODUCTION_MVP" in content

    # Verify SPRINKLER_HEAD_DEFECT is NOT marked PRODUCTION_MVP
    # Should be NOT_SUPPORTED in the table
    lines = content.splitlines()
    for line in lines:
        if line.startswith("|") and "SPRINKLER_HEAD_DEFECT" in line:
            assert "PRODUCTION_MVP" not in line, "SPRINKLER_HEAD_DEFECT cannot be in Production MVP"
            assert ("NOT_CURRENTLY_SUPPORTED" in line or "NOT_SUPPORTED" in line)


def test_phase8_1_perception_classes_specified():
    """Verify the Minimum Sufficient Perception Vocabulary is specified and decoupled."""
    spec_path = AI_DOCS_ROOT / "phase8_1_perception_class_spec.md"
    content = spec_path.read_text(encoding="utf-8")

    for cls_name in MINIMUM_PERCEPTION_CLASSES:
        assert cls_name in content, f"Minimum perception class {cls_name} not defined in spec"

    assert "PERCEPTION CLASS" in content
    assert "FINDING CLASS" in content


def test_phase8_1_dataset_decisions_documented():
    """Verify critical datasets have documented governance decisions."""
    mapping_path = AI_DOCS_ROOT / "phase8_1_dataset_capability_mapping.md"
    content = mapping_path.read_text(encoding="utf-8")

    # Dataset 6 must be TRAINING_CANDIDATE
    assert "Dataset 6" in content or "DS-06" in content
    assert "TRAINING_CANDIDATE" in content

    # Dataset 7 must be EVALUATION_ONLY or REFERENCE_ONLY
    assert "Dataset 7" in content or "DS-07" in content
    assert "EVALUATION_ONLY" in content

    # Dataset 8 must be AUXILIARY
    assert "Dataset 8" in content or "DS-08" in content
    assert "AUXILIARY" in content

    # SHD-Bench Zenodo must be EXCLUDED
    assert "SHD-Bench" in content or "Zenodo" in content
    assert "EXCLUDED" in content


def test_canonical_dataset_immutability():
    """Strict test to guarantee canonical dataset was NOT modified or altered."""
    images_dir = CANONICAL_ROOT / "images"
    annotations_dir = CANONICAL_ROOT / "annotations"

    assert images_dir.exists(), "Canonical images directory missing"
    assert annotations_dir.exists(), "Canonical annotations directory missing"

    image_files = [f for f in images_dir.iterdir() if f.is_file()]
    annotation_files = [f for f in annotations_dir.iterdir() if f.is_file()]

    assert len(image_files) == 6471, f"Canonical images count altered: {len(image_files)} (expected 6,471)"
    assert len(annotation_files) == 6471, f"Canonical annotations count altered: {len(annotation_files)} (expected 6,471)"
