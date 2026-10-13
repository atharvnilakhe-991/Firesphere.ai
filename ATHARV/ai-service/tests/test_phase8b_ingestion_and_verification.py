"""Phase 8B Test Suite: Real Fire-Safety Dataset Acquisition, Verification & Ingestion Engine.

Validates:
- Section 3 Source registry provenance schema (snake_case and camelCase compatibility)
- License handling, commercial safety, and quarantine isolation (CC BY-NC 4.0)
- Multi-format dataset ingestion: COCO JSON absolute -> normalized YOLO conversion
- Multi-format dataset ingestion: Pascal VOC XML [xmin, ymin, xmax, ymax] -> normalized YOLO conversion
- Multi-format dataset ingestion: YOLO normalized parsing, boundary checks, and class remapping
- Malformed image, corrupt header, and invalid bounding box rejection
- Bounding box annotation validation report generation (Section 10)
- Class balance and distribution reporting with INSUFFICIENT_DATA tags (Section 13)
- JSON and JSONL manifest generation with full Section 15 fields and group_id
- Truthful dataset metadata (dataset_meta.json) with zero fabrication
- Readiness gate strict zero-image guard and Phase 8B status evaluation
"""

import json
from pathlib import Path
import tempfile
import cv2
import numpy as np
import pytest

from app.models.model_metadata import FIRE_INSPECTION_CLASSES
from app.training.dataset_ingestion import DatasetIngestionEngine
from app.training.dataset_manifest import ManifestGenerator
from app.training.dataset_splitter import DatasetSplitter
from app.training.dataset_validator import DatasetValidator
from app.training.readiness_gate import DatasetReadinessGate


def create_test_image(path: Path, width: int = 640, height: int = 480, color=(100, 150, 200)) -> Path:
    """Creates a valid OpenCV-decodable test image."""
    img = np.full((height, width, 3), color, dtype=np.uint8)
    cv2.imwrite(str(path), img)
    return path


def test_section3_source_provenance_registry():
    """Verifies dataset_sources.json adheres strictly to Section 3 schema and truthful image counts."""
    sources_path = Path("ai-service/datasets/fire_safety/dataset_sources.json")
    if not sources_path.exists():
        sources_path = Path("datasets/fire_safety/dataset_sources.json")

    assert sources_path.exists(), "dataset_sources.json must exist"

    with open(sources_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    assert "sources" in data
    sources = data["sources"]
    assert len(sources) >= 6, "Must register at least 6 candidate datasets"

    required_section3_fields = [
        "dataset_name",
        "provider",
        "official_url",
        "access_url",
        "license",
        "license_url",
        "license_verified",
        "commercial_use",
        "redistribution",
        "annotation_format",
        "image_count_claimed",
        "image_count_downloaded",
        "annotation_count",
        "verified_classes",
        "status",
        "download_date",
        "checksum_available",
        "notes",
    ]

    valid_commercial_statuses = {"ALLOWED", "RESTRICTED", "NOT_ALLOWED", "UNKNOWN"}
    valid_redistribution_statuses = {"ALLOWED", "RESTRICTED", "NOT_ALLOWED", "UNKNOWN"}
    valid_statuses = {
        "VERIFIED",
        "UNVERIFIED",
        "LICENSE_INCOMPATIBLE",
        "NOT_SUITABLE",
        "INGESTED",
        "PARTIALLY_INGESTED",
        "QUARANTINED",
    }

    for s in sources:
        for field in required_section3_fields:
            assert field in s, f"Source '{s.get('dataset_name', 'unknown')}' missing Section 3 field '{field}'"

        assert s["commercial_use"] in valid_commercial_statuses
        assert s["redistribution"] in valid_redistribution_statuses
        assert s["status"] in valid_statuses
        assert isinstance(s["license_verified"], bool)
        assert isinstance(s["verified_classes"], list)

        # TRUTH IN AI: Claimed images must NEVER be counted in image_count_downloaded unless verified physical files exist on disk
        if s.get("status") in ("INGESTED", "VERIFIED") and s.get("archive_filename") and s.get("archive_filename") != "None (External)":
            assert s["image_count_downloaded"] == s.get("imageCountVerified", s["image_count_downloaded"]) > 0
        else:
            assert s["image_count_downloaded"] == 0, (
                f"Source '{s['dataset_name']}' has image_count_downloaded > 0 without verified physical files on disk"
            )


def test_license_quarantine_isolation():
    """Verifies that non-commercial licenses (CC BY-NC 4.0) are quarantined from production training."""
    sources_path = Path("ai-service/datasets/fire_safety/dataset_sources.json")
    if not sources_path.exists():
        sources_path = Path("datasets/fire_safety/dataset_sources.json")

    with open(sources_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    quarantined = [s for s in data["sources"] if s.get("status") in ("LICENSE_INCOMPATIBLE", "QUARANTINED")]
    assert len(quarantined) >= 1, "Must identify and quarantine at least 1 license-incompatible source"

    shd = next(s for s in quarantined if "sprinkler" in s["dataset_name"].lower())
    assert shd["license"] == "CC BY-NC 4.0"
    assert shd["commercial_use"] == "NOT_ALLOWED"

    # Quarantine directory must exist
    quarantine_dir = Path("ai-service/datasets/fire_safety/quarantine")
    if not quarantine_dir.exists():
        quarantine_dir = Path("datasets/fire_safety/quarantine")
    assert quarantine_dir.exists()


def test_coco_json_ingestion_engine(tmp_path):
    """Verifies COCO JSON ingestion accurately converts absolute [x, y, w, h] to normalized YOLO coordinates."""
    src_img_dir = tmp_path / "coco_src_images"
    src_img_dir.mkdir()
    tgt_img_dir = tmp_path / "tgt_images"
    tgt_annot_dir = tmp_path / "tgt_annots"
    coco_json_path = tmp_path / "instances.json"

    # Create 640x480 test image
    create_test_image(src_img_dir / "hallway_01.jpg", width=640, height=480)

    # COCO JSON structure:
    # BBox: [100, 50, 200, 150] in 640x480 image
    # center_x = 100 + 100 = 200 -> norm_xc = 200 / 640 = 0.3125
    # center_y = 50 + 75 = 125  -> norm_yc = 125 / 480 = 0.260417
    # norm_w = 200 / 640 = 0.3125
    # norm_h = 150 / 480 = 0.3125
    coco_payload = {
        "images": [
            {"id": 101, "file_name": "hallway_01.jpg", "width": 640, "height": 480}
        ],
        "categories": [
            {"id": 1, "name": "hose_reel"}
        ],
        "annotations": [
            {
                "id": 1001,
                "image_id": 101,
                "category_id": 1,
                "bbox": [100.0, 50.0, 200.0, 150.0],
                "area": 30000.0,
                "iscrowd": 0,
            }
        ],
    }
    with open(coco_json_path, "w", encoding="utf-8") as f:
        json.dump(coco_payload, f)

    # Use project mapping config
    mapping_path = Path("ai-service/datasets/fire_safety/dataset_class_mapping.json")
    if not mapping_path.exists():
        mapping_path = Path("datasets/fire_safety/dataset_class_mapping.json")

    sources_path = Path("ai-service/datasets/fire_safety/dataset_sources.json")
    if not sources_path.exists():
        sources_path = Path("datasets/fire_safety/dataset_sources.json")

    engine = DatasetIngestionEngine(
        class_mapping_config_path=mapping_path,
        source_registry_path=sources_path,
    )

    report = engine.ingest_coco_dataset(
        source_dataset_id="zenodo-fse-infrastructure-v1",
        source_images_dir=src_img_dir,
        coco_annotations_file=coco_json_path,
        target_images_dir=tgt_img_dir,
        target_annotations_dir=tgt_annot_dir,
    )

    assert report.ingestedImages == 1
    assert report.totalMappedAnnotations == 1
    assert (tgt_img_dir / "hallway_01.jpg").exists()
    assert (tgt_annot_dir / "hallway_01.txt").exists()

    content = (tgt_annot_dir / "hallway_01.txt").read_text().strip()
    tokens = content.split()
    assert len(tokens) == 5
    # hose_reel maps to class 9 (HOSE_REEL_OBSTRUCTED)
    assert int(tokens[0]) == 9
    assert pytest.approx(float(tokens[1]), rel=1e-3) == 0.3125
    assert pytest.approx(float(tokens[2]), rel=1e-3) == 0.260417
    assert pytest.approx(float(tokens[3]), rel=1e-3) == 0.3125
    assert pytest.approx(float(tokens[4]), rel=1e-3) == 0.3125


def test_pascal_voc_xml_ingestion_engine(tmp_path):
    """Verifies Pascal VOC XML ingestion accurately converts [xmin, ymin, xmax, ymax] to normalized YOLO coordinates."""
    src_img_dir = tmp_path / "voc_src_images"
    src_img_dir.mkdir()
    src_xml_dir = tmp_path / "voc_src_xmls"
    src_xml_dir.mkdir()
    tgt_img_dir = tmp_path / "tgt_images"
    tgt_annot_dir = tmp_path / "tgt_annots"

    create_test_image(src_img_dir / "panel_01.jpg", width=800, height=600)

    # VOC XML with exposed_breaker: [xmin=200, ymin=150, xmax=600, ymax=450]
    # width = 400, height = 300
    # center_x = 200 + 200 = 400 -> norm_xc = 400 / 800 = 0.50
    # center_y = 150 + 150 = 300 -> norm_yc = 300 / 600 = 0.50
    # norm_w = 400 / 800 = 0.50, norm_h = 300 / 600 = 0.50
    xml_content = """<annotation>
    <folder>images</folder>
    <filename>panel_01.jpg</filename>
    <size>
        <width>800</width>
        <height>600</height>
        <depth>3</depth>
    </size>
    <object>
        <name>exposed_breaker</name>
        <pose>Unspecified</pose>
        <truncated>0</truncated>
        <difficult>0</difficult>
        <bndbox>
            <xmin>200</xmin>
            <ymin>150</ymin>
            <xmax>600</xmax>
            <ymax>450</ymax>
        </bndbox>
    </object>
</annotation>"""
    (src_xml_dir / "panel_01.xml").write_text(xml_content, encoding="utf-8")

    mapping_path = Path("ai-service/datasets/fire_safety/dataset_class_mapping.json")
    if not mapping_path.exists():
        mapping_path = Path("datasets/fire_safety/dataset_class_mapping.json")

    sources_path = Path("ai-service/datasets/fire_safety/dataset_sources.json")
    if not sources_path.exists():
        sources_path = Path("datasets/fire_safety/dataset_sources.json")

    engine = DatasetIngestionEngine(
        class_mapping_config_path=mapping_path,
        source_registry_path=sources_path,
    )

    report = engine.ingest_voc_dataset(
        source_dataset_id="kaggle-electrical-panel-hazard-v1",
        source_images_dir=src_img_dir,
        source_annotations_dir=src_xml_dir,
        target_images_dir=tgt_img_dir,
        target_annotations_dir=tgt_annot_dir,
    )

    assert report.ingestedImages == 1
    assert report.totalMappedAnnotations == 1
    assert (tgt_annot_dir / "panel_01.txt").exists()

    content = (tgt_annot_dir / "panel_01.txt").read_text().strip()
    tokens = content.split()
    assert len(tokens) == 5
    # exposed_breaker maps to class 6 (ELECTRICAL_PANEL_HAZARD)
    assert int(tokens[0]) == 6
    assert pytest.approx(float(tokens[1]), rel=1e-3) == 0.50
    assert pytest.approx(float(tokens[2]), rel=1e-3) == 0.50
    assert pytest.approx(float(tokens[3]), rel=1e-3) == 0.50
    assert pytest.approx(float(tokens[4]), rel=1e-3) == 0.50


def test_malformed_annotation_and_out_of_bounds_rejection(tmp_path):
    """Verifies robust rejection of corrupt, out-of-bounds, negative, or NaN bounding boxes."""
    img_dir = tmp_path / "images"
    annot_dir = tmp_path / "annotations"
    img_dir.mkdir()
    annot_dir.mkdir()

    create_test_image(img_dir / "img_ok.jpg")
    create_test_image(img_dir / "img_bad_bounds.jpg")
    create_test_image(img_dir / "img_neg.jpg")
    create_test_image(img_dir / "img_nan.jpg")

    # Valid annotation
    (annot_dir / "img_ok.txt").write_text("0 0.5 0.5 0.2 0.2\n")
    # Out of bounds
    (annot_dir / "img_bad_bounds.txt").write_text("0 1.5 0.5 0.2 0.2\n")
    # Negative coordinate
    (annot_dir / "img_neg.txt").write_text("0 -0.1 0.5 0.2 0.2\n")
    # NaN
    (annot_dir / "img_nan.txt").write_text("0 nan 0.5 0.2 0.2\n")

    report = DatasetValidator.generate_annotation_validation_report(
        images_dir=img_dir,
        annotations_dir=annot_dir,
    )

    assert report["status"] == "FAILED"
    assert report["valid_annotations"] == 1
    assert report["invalid_annotations"] == 3
    assert report["rejections_by_rule"]["out_of_bounds"] >= 1
    assert report["rejections_by_rule"]["negative_coordinates"] >= 1
    assert report["rejections_by_rule"]["nan_or_inf"] >= 1


def test_class_distribution_report_insufficient_data_tagging(tmp_path):
    """Verifies that empty or sparse classes are tagged INSUFFICIENT_DATA or NOT_RELIABLY_VISUALLY_DETECTABLE."""
    img_dir = tmp_path / "images"
    annot_dir = tmp_path / "annotations"
    img_dir.mkdir()
    annot_dir.mkdir()

    report = DatasetValidator.generate_class_distribution_report(
        images_dir=img_dir,
        annotations_dir=annot_dir,
    )

    assert report["total_images"] == 0
    assert report["total_annotations"] == 0
    # Class 2 is NOT_RELIABLY_VISUALLY_DETECTABLE
    assert report["classes"]["FIRE_EXTINGUISHER_EXPIRED"]["status"] == "NOT_RELIABLY_VISUALLY_DETECTABLE"
    # All other classes are INSUFFICIENT_DATA
    for name in [
        "FIRE_EXTINGUISHER_MISSING",
        "FIRE_EXTINGUISHER_BLOCKED",
        "EMERGENCY_EXIT_OBSTRUCTED",
        "EXIT_SIGNAGE_MISSING",
        "SPRINKLER_HEAD_DEFECT",
        "ELECTRICAL_PANEL_HAZARD",
        "COMBUSTIBLE_MATERIAL_HAZARD",
        "MANUAL_CALL_POINT_BLOCKED",
        "HOSE_REEL_OBSTRUCTED",
    ]:
        assert report["classes"][name]["status"] == "INSUFFICIENT_DATA"


def test_manifest_generator_jsonl_provenance_format(tmp_path):
    """Verifies ManifestGenerator writes JSONL manifests adhering to Section 15 schema."""
    splits_dir = tmp_path / "splits"
    img_dir = tmp_path / "images"
    annot_dir = tmp_path / "annotations"
    manifests_dir = tmp_path / "manifests"

    splits_dir.mkdir()
    img_dir.mkdir()
    annot_dir.mkdir()

    create_test_image(img_dir / "insp_101_sample_1.jpg")
    (annot_dir / "insp_101_sample_1.txt").write_text("3 0.5 0.5 0.4 0.6\n")

    (splits_dir / "train.txt").write_text("insp_101_sample_1\n")
    (splits_dir / "val.txt").write_text("")
    (splits_dir / "test.txt").write_text("")

    groups = {"insp_101_sample_1": "inspection_block_101"}

    manifests = ManifestGenerator.generate_all_manifests(
        splits_dir=splits_dir,
        images_dir=img_dir,
        annotations_dir=annot_dir,
        manifests_dir=manifests_dir,
        source_dataset_id="roboflow-emergency-exit-obstruction-v2",
        groups=groups,
    )

    train_jsonl = manifests_dir / "train.jsonl"
    assert train_jsonl.exists()

    lines = train_jsonl.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 1

    rec = json.loads(lines[0])
    expected_fields = [
        "sample_id",
        "image_path",
        "annotation_path",
        "source_dataset",
        "source_class",
        "mapped_class",
        "split",
        "sha256",
        "group_id",
    ]
    for field in expected_fields:
        assert field in rec, f"Manifest JSONL missing required field '{field}'"

    assert rec["sample_id"] == "insp_101_sample_1"
    assert rec["split"] == "train"
    assert rec["group_id"] == "inspection_block_101"
    assert rec["mapped_class"] == "EMERGENCY_EXIT_OBSTRUCTED"
    assert len(rec["sha256"]) == 64


def test_dataset_meta_json_integrity():
    """Verifies dataset_meta.json reflects real computed values with zero fabricated counts."""
    meta_path = Path("ai-service/datasets/fire_safety/dataset_meta.json")
    if not meta_path.exists():
        meta_path = Path("datasets/fire_safety/dataset_meta.json")

    assert meta_path.exists()
    with open(meta_path, "r", encoding="utf-8") as f:
        meta = json.load(f)

    assert meta["version"] == "0.8.1-phase8b"
    assert meta["total_images"] == meta["usable_images"]
    assert meta["train_images"] + meta["val_images"] + meta["test_images"] == meta["total_images"]
    assert meta["sha256_verified"] is True
    assert meta["duplicate_check"] is True
    assert meta["leakage_check"] is True
    assert meta["status"] in ("DATASET_ACQUISITION_IN_PROGRESS", "DATASET_PARTIALLY_READY")
    assert meta["readyForPhase9"] is False


def test_readiness_gate_phase8b_status():
    """Verifies that DatasetReadinessGate denies Phase 9 and identifies blockers."""
    ds_root = Path("ai-service/datasets/fire_safety")
    if not ds_root.exists():
        ds_root = Path("datasets/fire_safety")

    gate = DatasetReadinessGate(dataset_root=ds_root)
    eval_res = gate.evaluate_readiness()

    assert eval_res.isReadyForPhase9 is False
    assert len(eval_res.blockers) > 0

    # Ensure incomplete / non-compliant datasets strictly prevent READY_FOR_PHASE_9
    assert eval_res.status != "READY_FOR_PHASE_9"
