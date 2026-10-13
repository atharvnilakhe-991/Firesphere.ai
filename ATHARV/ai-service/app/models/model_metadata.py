"""Model metadata, class definitions, and inspection catalog."""

from typing import Dict, List, Optional, Tuple
from pydantic import BaseModel, Field


class FireSafetyClass(BaseModel):
    """Specification of an individual fire hazard / defect category."""

    classId: int = Field(description="Numerical identifier index in model output tensor")
    name: str = Field(description="Standardized defect name matching findingType")
    description: str = Field(description="Operational summary of the hazard")
    status: str = Field(default="NOT_SUPPORTED", description="'SUPPORTED' or 'NOT_SUPPORTED'")
    trainingDatasetAvailable: bool = Field(default=False, description="True if verified training dataset exists")


# Official FireSphere Detection Class Catalog
FIRE_INSPECTION_CLASSES: Dict[int, FireSafetyClass] = {
    0: FireSafetyClass(
        classId=0,
        name="FIRE_EXTINGUISHER_MISSING",
        description="Designated extinguisher wall bracket is vacant or absent",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
    1: FireSafetyClass(
        classId=1,
        name="FIRE_EXTINGUISHER_BLOCKED",
        description="Clear access to fire extinguisher is impeded (<1m clearance)",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
    2: FireSafetyClass(
        classId=2,
        name="FIRE_EXTINGUISHER_EXPIRED",
        description="Extinguisher pressure gauge in red zone or maintenance tag expired",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
    3: FireSafetyClass(
        classId=3,
        name="EMERGENCY_EXIT_OBSTRUCTED",
        description="Emergency exit route, door, or corridor visibly blocked",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
    4: FireSafetyClass(
        classId=4,
        name="EXIT_SIGNAGE_MISSING",
        description="Required illuminated emergency exit signage is unlit or missing",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
    5: FireSafetyClass(
        classId=5,
        name="SPRINKLER_HEAD_DEFECT",
        description="Sprinkler head shows corrosion, mechanical damage, or obstruction",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
    6: FireSafetyClass(
        classId=6,
        name="ELECTRICAL_PANEL_HAZARD",
        description="Exposed electrical wiring or combustible material near electrical panels",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
    7: FireSafetyClass(
        classId=7,
        name="COMBUSTIBLE_MATERIAL_HAZARD",
        description="Excessive combustible materials stored near ignition sources",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
    8: FireSafetyClass(
        classId=8,
        name="MANUAL_CALL_POINT_BLOCKED",
        description="Manual fire alarm call point or pull station is obstructed",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
    9: FireSafetyClass(
        classId=9,
        name="HOSE_REEL_OBSTRUCTED",
        description="Fire hose reel cabinet is locked, blocked, or inoperable",
        status="NOT_SUPPORTED",
        trainingDatasetAvailable=False,
    ),
}


class ModelMetadata(BaseModel):
    """Metadata describing the active computer vision model runtime."""

    modelName: str = Field(description="Name/identifier of the model")
    modelVersion: str = Field(description="Semantic version of the model")
    architecture: str = Field(description="Neural network architecture family (e.g. YOLOv8, YOLOv11)")
    inputDimensions: Tuple[int, int] = Field(description="(width, height) expected by model tensor")
    colorFormat: str = Field(description="Input color format (e.g. RGB)")
    device: str = Field(description="Inference device: 'cuda', 'cpu', etc.")
    confidenceThreshold: float = Field(description="Minimum detection confidence threshold")
    nmsIouThreshold: float = Field(description="IoU threshold for Non-Maximum Suppression")
    classes: List[str] = Field(description="Full list of cataloged defect class names")
    supportedClasses: List[str] = Field(description="Classes currently supported with trained weights")
    unsupportedClasses: List[str] = Field(description="Classes currently pending training datasets")
    weightsPath: Optional[str] = Field(default=None, description="Path to active model weights artifact")
    weightsLoaded: bool = Field(default=False, description="True if trained weights artifact is loaded")
    trainingStatus: str = Field(default="NOT_YET_TRAINED", description="Model training status")
