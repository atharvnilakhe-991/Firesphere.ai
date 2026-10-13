"""Phase 8.2 Test Suite: Final Dataset & Capability Manifest Validation.

Validates:
1. Manifest files exist and JSON parses validly.
2. All 10 statutory defect findings exist in the manifest.
3. All frozen perception classes (Primary MVP, Auxiliary, Excluded) exist.
4. Every source dataset has an explicit governance decision.
5. Every training candidate has an explicit approval reason.
6. Every excluded dataset has an explicit exclusion reason.
7. Every source class mapping is explicit with a valid mapping type.
8. No forbidden violation relabeling exists.
9. Dataset 8 is never mapped directly to FIRE_EXTINGUISHER_BLOCKED.
10. Dataset 7 is not marked as a production training candidate.
11. SPRINKLER_HEAD_DEFECT remains strictly unsupported.
12. Regulatory clearances are NOT hardcoded (clearance_rule_source: CONFIGURABLE / NOT_YET_DEFINED).
13. Leakage policy exists and covers exact duplicates, near duplicates, augmentations, and videos.
14. License matrix exists and confirms commercial SaaS compatibility.
15. Phase 8.3 training gate exists and explicitly bars training execution in Phase 8.2.
16. Canonical dataset image and annotation counts remain 100% untouched.
"""

from pathlib import Path
import json
import pytest

WORKSPACE_ROOT = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
MANIFESTS_ROOT = WORKSPACE_ROOT / "datasets" / "fire_safety" / "manifests"
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

PRIMARY_MVP_CLASSES = [
    "FIRE_EXTINGUISHER",
    "EXIT_DOOR",
    "EXIT_SIGN",
    "OBSTACLE",
]

AUXILIARY_CLASSES = [
    "FIRE_HOSE_REEL",
    "MANUAL_CALL_POINT",
    "ELECTRICAL_PANEL",
    "EMPTY_BRACKET_CABINET",
]

VALID_DECISIONS = {
    "TRAINING_CANDIDATE",
    "AUXILIARY",
    "EVALUATION_ONLY",
    "REFERENCE_ONLY",
    "EXCLUDED",
}

VALID_MAPPING_TYPES = {
    "DIRECT",
    "SEMANTIC_EQUIVALENT",
    "PARTIAL",
    "AUXILIARY",
    "INVALID",
}


def test_manifest_files_exist_and_json_parses():
    """Verify all 7 Phase 8.2 JSON manifest files exist and parse validly."""
    manifest_files = [
        "phase8_2_capability_manifest.json",
        "phase8_2_training_candidates.json",
        "phase8_2_excluded_data.json",
        "phase8_2_evaluation_data.json",
        "phase8_2_dataset_statistics.json",
        "phase8_2_leakage_policy.json",
        "phase8_2_license_matrix.json",
    ]
    for fname in manifest_files:
        fpath = MANIFESTS_ROOT / fname
        assert fpath.exists(), f"Manifest file missing: {fpath}"
        with open(fpath, "r", encoding="utf-8") as f:
            data = json.load(f)
            assert isinstance(data, dict), f"Manifest {fname} did not parse as a JSON object"


def test_phase8_2_documentation_files_exist():
    """Verify Phase 8.2 markdown documentation exists in both docs locations."""
    docs = [
        "phase8_2_dataset_capability_manifest.md",
        "phase8_2_training_gate.md",
    ]
    for doc in docs:
        ai_path = AI_DOCS_ROOT / doc
        root_path = WORKSPACE_ROOT / "docs" / doc
        assert ai_path.exists(), f"AI Service doc missing: {ai_path}"
        assert root_path.exists(), f"Root doc missing: {root_path}"
        assert ai_path.stat().st_size > 1000, f"Doc {doc} too small"


def test_all_ten_findings_exist_in_manifest():
    """Verify all 10 target findings are present in the capability manifest."""
    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    findings = data.get("finding_capabilities", [])
    finding_names = [f["finding_name"] for f in findings]

    for expected in TARGET_FINDINGS:
        assert expected in finding_names, f"Target finding missing from manifest: {expected}"


def test_all_frozen_perception_classes_exist():
    """Verify all frozen perception classes (Primary MVP and Auxiliary) exist."""
    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    pclasses = data.get("perception_classes", {})
    primary_names = [c["class_name"] for c in pclasses.get("primary_mvp", [])]
    aux_names = [c["class_name"] for c in pclasses.get("auxiliary_secondary", [])]

    for p in PRIMARY_MVP_CLASSES:
        assert p in primary_names, f"Primary MVP perception class missing: {p}"

    for a in AUXILIARY_CLASSES:
        assert a in aux_names, f"Auxiliary perception class missing: {a}"


def test_every_source_has_a_decision():
    """Verify every dataset source has exactly one valid governance decision."""
    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    sources = data.get("dataset_sources", [])
    assert len(sources) >= 7, "Expected at least 7 audited dataset sources"

    for src in sources:
        decision = src.get("decision")
        assert decision in VALID_DECISIONS, f"Source {src.get('source_id')} has invalid decision: {decision}"


def test_every_training_candidate_has_a_reason():
    """Verify every training candidate has an explicit approval reason."""
    candidates_path = MANIFESTS_ROOT / "phase8_2_training_candidates.json"
    with open(candidates_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    candidates = data.get("approved_training_candidates", [])
    assert len(candidates) >= 4, "Expected at least 4 approved training candidates"

    for cand in candidates:
        reason = cand.get("approval_reason")
        assert reason and len(reason) > 20, f"Candidate {cand.get('target_class')} missing valid reason"


def test_every_excluded_dataset_has_a_reason():
    """Verify every excluded source has explicit exclusion reasons."""
    excluded_path = MANIFESTS_ROOT / "phase8_2_excluded_data.json"
    with open(excluded_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    items = data.get("excluded_items", [])
    assert len(items) >= 3, "Expected at least 3 excluded items"

    for item in items:
        reasons = item.get("reasons", [])
        assert len(reasons) > 0, f"Item {item.get('source_id')} has no exclusion reasons"


def test_every_source_class_mapping_is_explicit():
    """Verify every source class mapping defines a valid mapping type and decision."""
    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    mappings = data.get("source_class_mappings", [])
    assert len(mappings) >= 15, "Expected at least 15 source class mappings"

    for m in mappings:
        mtype = m.get("mapping_type")
        assert mtype in VALID_MAPPING_TYPES, f"Invalid mapping type: {mtype} in {m}"
        assert m.get("decision") in VALID_DECISIONS, f"Invalid decision in mapping: {m}"
        assert m.get("reason"), f"Missing reason in mapping: {m}"


def test_no_forbidden_violation_relabeling_exists():
    """Verify generic equipment is never coercively mapped to violation labels."""
    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    mappings = data.get("source_class_mappings", [])
    for m in mappings:
        src = m.get("source_class", "").lower()
        target = m.get("target_class", "")

        # Generic extinguisher must not be mapped to BLOCKED or EXPIRED
        if src in ["fire_extinguisher", "fire extinguisher", "shell"]:
            assert target != "FIRE_EXTINGUISHER_BLOCKED", f"Forbidden relabeling found: {m}"
            assert target != "FIRE_EXTINGUISHER_EXPIRED", f"Forbidden relabeling found: {m}"

        # Generic exit door must not be mapped directly to EMERGENCY_EXIT_OBSTRUCTED
        if src == "emergency exit door":
            assert target != "EMERGENCY_EXIT_OBSTRUCTED", f"Forbidden relabeling found: {m}"


def test_dataset8_never_mapped_directly_to_fire_extinguisher_blocked():
    """Verify Dataset 8 is never mapped directly to FIRE_EXTINGUISHER_BLOCKED."""
    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    mappings = data.get("source_class_mappings", [])
    for m in mappings:
        if m.get("dataset_id") == "fire_safetynet_condition_check":
            assert m.get("target_class") != "FIRE_EXTINGUISHER_BLOCKED", "Dataset 8 must never be mapped to BLOCKED"
            assert m.get("decision") == "AUXILIARY"


def test_dataset7_not_marked_production_training_candidate():
    """Verify Dataset 7 is NOT marked as an approved production training candidate."""
    candidates_path = MANIFESTS_ROOT / "phase8_2_training_candidates.json"
    with open(candidates_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    for cand in data.get("approved_training_candidates", []):
        for src in cand.get("approved_sources", []):
            assert "my-first-project" not in src.get("dataset_id", "").lower(), "Dataset 7 cannot be in approved training candidates"

    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        mdata = json.load(f)

    for src in mdata.get("dataset_sources", []):
        if "my-first-project" in src.get("source_id", ""):
            assert src.get("decision") == "EVALUATION_ONLY"


def test_sprinkler_head_defect_remains_unsupported():
    """Verify SPRINKLER_HEAD_DEFECT is marked NOT_SUPPORTED and Zenodo dataset is EXCLUDED."""
    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    for fnd in data.get("finding_capabilities", []):
        if fnd.get("finding_name") == "SPRINKLER_HEAD_DEFECT":
            assert fnd.get("status") == "NOT_SUPPORTED"

    for src in data.get("dataset_sources", []):
        if "sprinkler" in src.get("source_id", ""):
            assert src.get("decision") == "EXCLUDED"


def test_regulatory_clearance_dimensions_not_hardcoded():
    """Verify regulatory clearances are marked NOT_HARDCODED and CONFIGURABLE / NOT_YET_DEFINED."""
    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    for fnd in data.get("finding_capabilities", []):
        source = fnd.get("clearance_rule_source")
        status = fnd.get("clearance_rule_status")
        assert source == "CONFIGURABLE / NOT_YET_DEFINED", f"Finding {fnd['finding_name']} has hardcoded rule source: {source}"
        assert status == "NOT_HARDCODED", f"Finding {fnd['finding_name']} has hardcoded rule status: {status}"


def test_leakage_and_split_policy_present():
    """Verify leakage policy covers duplicates, near duplicates, augmentations, and split seed 42."""
    policy_path = MANIFESTS_ROOT / "phase8_2_leakage_policy.json"
    with open(policy_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    controls = data.get("leakage_controls", {})
    assert "exact_duplicate_policy" in controls
    assert "near_duplicate_policy" in controls
    assert "augmentation_lineage_policy" in controls
    assert "sequential_video_frame_policy" in controls
    assert "physical_scene_grouping_policy" in controls

    split = data.get("split_policy", {})
    assert split.get("random_seed") == 42


def test_license_matrix_verifies_commercial_eligibility():
    """Verify license matrix exists and excludes non-commercial licenses."""
    lic_path = MANIFESTS_ROOT / "phase8_2_license_matrix.json"
    with open(lic_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    sources = data.get("sources", [])
    for s in sources:
        if s.get("license") == "CC BY-NC 4.0":
            assert s.get("commercial_use_allowed") is False
            assert s.get("training_use_decision") == "EXCLUDED"


def test_phase8_3_training_gate_present():
    """Verify Phase 8.3 training gate exists and explicitly bars training execution in Phase 8.2."""
    gate_doc = AI_DOCS_ROOT / "phase8_2_training_gate.md"
    assert gate_doc.exists()
    content = gate_doc.read_text(encoding="utf-8")
    assert "PHASE 8.3 MAY NOT START UNTIL:" in content
    assert "TRAINING EXECUTION IN PHASE 8.2: STRICTLY PROHIBITED" in content

    manifest_path = MANIFESTS_ROOT / "phase8_2_capability_manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    gate = data.get("phase8_3_training_gate", {})
    assert gate.get("training_execution_permitted") is False


def test_canonical_dataset_integrity_unchanged():
    """Strict test to verify canonical dataset images, annotations, and split counts remain unchanged."""
    images_dir = CANONICAL_ROOT / "images"
    annotations_dir = CANONICAL_ROOT / "annotations"

    img_count = len([f for f in images_dir.iterdir() if f.is_file()])
    ann_count = len([f for f in annotations_dir.iterdir() if f.is_file()])

    assert img_count == 6471, f"Canonical image count altered: {img_count} (expected 6,471)"
    assert ann_count == 6471, f"Canonical annotation count altered: {ann_count} (expected 6,471)"

    # Verify split file line counts
    train_lines = len((CANONICAL_ROOT / "splits" / "train.txt").read_text(encoding="utf-8").splitlines())
    val_lines = len((CANONICAL_ROOT / "splits" / "val.txt").read_text(encoding="utf-8").splitlines())
    test_lines = len((CANONICAL_ROOT / "splits" / "test.txt").read_text(encoding="utf-8").splitlines())

    assert train_lines == 4522, f"Train split lines altered: {train_lines}"
    assert val_lines == 1293, f"Val split lines altered: {val_lines}"
    assert test_lines == 656, f"Test split lines altered: {test_lines}"
