"""Configuration specification for model training runs."""

from pathlib import Path
from typing import Optional
from pydantic import BaseModel, Field


class TrainingConfig(BaseModel):
    """Hyperparameters and execution configuration for fire safety model training."""

    experimentId: str = Field(default="exp-001", description="Unique experiment run identifier")
    modelArchitecture: str = Field(default="yolov8-firesphere-nano", description="Underlying CV architecture")
    datasetDir: Path = Field(default=Path("datasets/fire_safety"), description="Path to canonical dataset root")
    checkpointDir: Path = Field(default=Path("checkpoints"), description="Directory to store model checkpoints")
    epochs: int = Field(default=50, ge=1, le=1000)
    batchSize: int = Field(default=16, ge=1, le=256)
    imageWidth: int = Field(default=640, ge=64, le=1920)
    imageHeight: int = Field(default=640, ge=64, le=1920)
    learningRate: float = Field(default=0.001, gt=0.0, le=1.0)
    weightDecay: float = Field(default=0.0005, ge=0.0)
    numWorkers: int = Field(default=0, ge=0, le=16)
    seed: int = Field(default=42)
    device: str = Field(default="auto", description="'auto', 'cpu', or 'cuda'")
    earlyStoppingPatience: int = Field(default=10, ge=1)
    saveBestOnly: bool = Field(default=True)
