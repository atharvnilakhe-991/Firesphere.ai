"""Automated tests for Phase 7 Model Registry and Promotion Lifecycle Governance."""

import pytest
from app.registry.model_registry import (
    ModelArtifactMetadata,
    ModelLifecycleState,
    ModelPromotionException,
    model_registry,
)


@pytest.fixture(autouse=True)
def clean_registry():
    """Resets model registry before and after tests."""
    model_registry.clear_for_tests()
    yield
    model_registry.clear_for_tests()


def test_model_registration_and_immutability():
    """Verifies that model artifacts are registered with metadata and remain immutable."""
    meta = ModelArtifactMetadata(
        modelId="firesphere-yolo",
        modelVersion="1.0.0",
        architecture="yolov8n",
        datasetVersion="0.1.0",
        trainingRunId="run-777",
        classList=["FIRE_EXTINGUISHER_MISSING"],
        torchVersion="2.1.0",
        cudaAvailable=False,
        sha256Checksum="a1b2c3d4e5f60000000000000000000000000000000000000000000000000000",
        status=ModelLifecycleState.EXPERIMENTAL,
    )

    registered = model_registry.register_model(meta)
    assert registered.modelId == "firesphere-yolo"
    assert registered.status == ModelLifecycleState.EXPERIMENTAL
    assert len(registered.auditTrail) == 1

    # Attempting to re-register the same model:version must raise ValueError (immutability)
    with pytest.raises(ValueError, match="immutable"):
        model_registry.register_model(meta)


def test_model_promotion_lifecycle_governance():
    """Verifies auditable, gated state transitions: EXPERIMENTAL -> VALIDATED -> STAGING -> PRODUCTION."""
    meta = ModelArtifactMetadata(
        modelId="firesphere-yolo",
        modelVersion="1.0.0",
        architecture="yolov8n",
        datasetVersion="0.1.0",
        trainingRunId="run-777",
        classList=["FIRE_EXTINGUISHER_MISSING"],
        torchVersion="2.1.0",
        cudaAvailable=False,
        sha256Checksum="abcdef1234560000000000000000000000000000000000000000000000000000",
        status=ModelLifecycleState.EXPERIMENTAL,
    )
    model_registry.register_model(meta)

    # 1. Illegal leap from EXPERIMENTAL directly to PRODUCTION must fail
    with pytest.raises(ModelPromotionException, match="must first be VALIDATED"):
        model_registry.promote_model(
            model_id="firesphere-yolo",
            version="1.0.0",
            target_state=ModelLifecycleState.PRODUCTION,
            approved_by="officer_admin",
            audit_reason="Skipping validation checks",
        )

    # 2. EXPERIMENTAL -> VALIDATED succeeds
    m_val = model_registry.promote_model(
        model_id="firesphere-yolo",
        version="1.0.0",
        target_state=ModelLifecycleState.VALIDATED,
        approved_by="qa_lead",
        audit_reason="Passed mAP benchmark threshold",
    )
    assert m_val.status == ModelLifecycleState.VALIDATED

    # 3. VALIDATED -> STAGING succeeds
    m_stg = model_registry.promote_model(
        model_id="firesphere-yolo",
        version="1.0.0",
        target_state=ModelLifecycleState.STAGING,
        approved_by="devops_lead",
        audit_reason="Staging environment dry-run passed",
    )
    assert m_stg.status == ModelLifecycleState.STAGING

    # 4. STAGING -> PRODUCTION succeeds
    m_prod = model_registry.promote_model(
        model_id="firesphere-yolo",
        version="1.0.0",
        target_state=ModelLifecycleState.PRODUCTION,
        approved_by="chief_fire_officer",
        audit_reason="Formal regulatory verification approved",
    )
    assert m_prod.status == ModelLifecycleState.PRODUCTION
    assert len(m_prod.auditTrail) == 4

    # Active production query
    active = model_registry.get_active_production_model()
    assert active is not None
    assert active.modelVersion == "1.0.0"

    # 5. PRODUCTION -> RETIRED succeeds
    m_ret = model_registry.promote_model(
        model_id="firesphere-yolo",
        version="1.0.0",
        target_state=ModelLifecycleState.RETIRED,
        approved_by="chief_fire_officer",
        audit_reason="Superseded by v2.0.0",
    )
    assert m_ret.status == ModelLifecycleState.RETIRED

    # Promoting a RETIRED model fails
    with pytest.raises(ModelPromotionException, match="Cannot promote a RETIRED"):
        model_registry.promote_model(
            model_id="firesphere-yolo",
            version="1.0.0",
            target_state=ModelLifecycleState.PRODUCTION,
            approved_by="admin",
            audit_reason="Attempting to re-activate retired artifact",
        )
