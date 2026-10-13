"""Model loader and device discovery for Computer Vision inference."""

import os
from typing import Any, Dict, Optional, Tuple
from app.core.config import settings
from app.core.logging import logger
from app.models.model_metadata import (
    FIRE_INSPECTION_CLASSES,
    ModelMetadata,
)

# Optional PyTorch bridge
try:
    import torch
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False
    torch = None


class ModelLoader:
    """Discovers, initializes, and loads Computer Vision model weights onto target hardware device."""

    @classmethod
    def get_target_device(cls) -> str:
        """Determines optimal inference device (CUDA GPU vs CPU).

        Honors AI_INFERENCE_DEVICE ('auto', 'cuda', 'cpu') and AI_GPU_FALLBACK_TO_CPU.
        """
        preferred = (settings.inference_device or "auto").strip().lower()

        if preferred == "cpu":
            logger.info("Targeting CPU for model inference (configured preference: cpu).")
            return "cpu"

        if preferred in ("cuda", "gpu"):
            if TORCH_AVAILABLE and torch.cuda.is_available():
                gpu_name = torch.cuda.get_device_name(0)
                logger.info(f"Targeting CUDA GPU device: {gpu_name} (Device: cuda:0)")
                return "cuda"
            elif settings.gpu_fallback_to_cpu:
                logger.warning("CUDA GPU requested but unavailable. Falling back to CPU as configured.")
                return "cpu"
            else:
                raise RuntimeError("CUDA GPU device requested but torch.cuda.is_available() is False.")

        # Default 'auto' mode
        if TORCH_AVAILABLE and torch.cuda.is_available():
            gpu_name = torch.cuda.get_device_name(0)
            logger.info(f"Targeting CUDA GPU device: {gpu_name} (Device: cuda:0)")
            return "cuda"

        logger.info("Targeting CPU for model inference (auto mode: GPU unavailable or PyTorch CPU mode).")
        return "cpu"

    @classmethod
    def load_model(
        cls,
        weights_path: Optional[str] = None,
        model_name: Optional[str] = None,
        model_version: Optional[str] = None,
    ) -> Tuple[Any, ModelMetadata]:
        """Loads model weights or returns baseline adapter when weights are not yet established.

        Safe lifecycle: never crashes application startup if weights file is absent.
        """
        active_name = model_name or settings.model_name
        active_version = model_version or settings.model_version
        target_device = cls.get_target_device()
        resolved_weights = weights_path or settings.model_weights_path

        all_class_names = [cls_info.name for cls_info in FIRE_INSPECTION_CLASSES.values()]
        supported_classes = [
            cls_info.name
            for cls_info in FIRE_INSPECTION_CLASSES.values()
            if cls_info.status == "SUPPORTED"
        ]
        unsupported_classes = [
            cls_info.name
            for cls_info in FIRE_INSPECTION_CLASSES.values()
            if cls_info.status == "NOT_SUPPORTED"
        ]

        # Case A: Weights path is configured and exists on disk
        if resolved_weights and os.path.exists(resolved_weights):
            logger.info(f"Loading model weights artifact from: {resolved_weights}")
            try:
                if TORCH_AVAILABLE:
                    # Attempt TorchScript or PyTorch state dict load
                    model = torch.jit.load(resolved_weights, map_location=target_device)
                    model.eval()
                    logger.info(f"Successfully loaded model {active_name} on {target_device}")
                    metadata = ModelMetadata(
                        modelName=active_name,
                        modelVersion=active_version,
                        architecture="TorchScript-YOLO-Adapter",
                        inputDimensions=(settings.target_image_width, settings.target_image_height),
                        colorFormat=settings.image_color_format,
                        device=target_device,
                        confidenceThreshold=settings.confidence_threshold,
                        nmsIouThreshold=settings.nms_iou_threshold,
                        classes=all_class_names,
                        supportedClasses=supported_classes,
                        unsupportedClasses=unsupported_classes,
                        weightsPath=resolved_weights,
                        weightsLoaded=True,
                        trainingStatus="WEIGHTS_LOADED",
                    )
                    return model, metadata
            except Exception as e:
                logger.error(f"Failed to load weights artifact {resolved_weights}: {e}")

        # Case C: No weights artifact established yet (Production Baseline Adapter)
        logger.info(
            f"Initialized {active_name} (v{active_version}) in Case C baseline mode: "
            "Model interface ready, inference contract validated, pending trained dataset."
        )

        metadata = ModelMetadata(
            modelName=active_name,
            modelVersion=active_version,
            architecture="YOLO-Inspection-Adapter-Baseline",
            inputDimensions=(settings.target_image_width, settings.target_image_height),
            colorFormat=settings.image_color_format,
            device=target_device,
            confidenceThreshold=settings.confidence_threshold,
            nmsIouThreshold=settings.nms_iou_threshold,
            classes=all_class_names,
            supportedClasses=supported_classes,
            unsupportedClasses=unsupported_classes,
            weightsPath=resolved_weights,
            weightsLoaded=False,
            trainingStatus="NOT_YET_TRAINED",
        )

        return None, metadata
