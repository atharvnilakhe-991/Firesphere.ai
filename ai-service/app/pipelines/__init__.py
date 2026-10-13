"""Pipelines package for AI processing workflows."""

from app.pipelines.base import BasePipeline
from app.pipelines.inspection_pipeline import InspectionPipeline

__all__ = ["BasePipeline", "InspectionPipeline"]
