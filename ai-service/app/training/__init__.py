"""FireSphere AI Model Training, Dataset Curation, and Evaluation Package."""

from app import __version__
from app.training.checkpoints import CheckpointManager
from app.training.class_balance import ClassBalanceAnalyzer, ClassDistributionProfile
from app.training.config import TrainingConfig
from app.training.dataset import FireSafetyDataset, DatasetNotAvailableException
from app.training.dataset_cleaner import DatasetCleaner, CleaningReport, RejectionRecord
from app.training.dataset_ingestion import (
    DatasetIngestionEngine,
    DatasetIngestionReport,
    ClassMappingRule,
    IngestionItemResult,
)
from app.training.dataset_manifest import DatasetManifest, ManifestGenerator, ManifestRecord
from app.training.dataset_splitter import DatasetSplitter, DatasetSplitResult
from app.training.dataset_validator import DatasetValidator, DatasetValidationReport
from app.training.readiness_gate import (
    DatasetReadinessGate,
    DatasetReadinessEvaluation,
    GateCheckItem,
)
from app.training.trainer import ModelTrainer

__all__ = [
    "__version__",
    "CheckpointManager",
    "ClassBalanceAnalyzer",
    "ClassDistributionProfile",
    "TrainingConfig",
    "FireSafetyDataset",
    "DatasetNotAvailableException",
    "DatasetCleaner",
    "CleaningReport",
    "RejectionRecord",
    "DatasetIngestionEngine",
    "DatasetIngestionReport",
    "ClassMappingRule",
    "IngestionItemResult",
    "DatasetManifest",
    "ManifestGenerator",
    "ManifestRecord",
    "DatasetSplitter",
    "DatasetSplitResult",
    "DatasetValidator",
    "DatasetValidationReport",
    "DatasetReadinessGate",
    "DatasetReadinessEvaluation",
    "GateCheckItem",
    "ModelTrainer",
]
