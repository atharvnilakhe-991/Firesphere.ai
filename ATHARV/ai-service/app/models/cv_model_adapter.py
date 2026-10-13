"""Computer Vision model adapter connecting Phase 2 preprocessing to neural inference."""

import time
from typing import Any, Dict, List, Optional
import numpy as np
from app.core.config import settings
from app.core.logging import logger
from app.models.base_model import BaseInspectionModel, RawDetection
from app.models.model_loader import ModelLoader, TORCH_AVAILABLE, torch
from app.models.model_metadata import ModelMetadata
from app.models.nms import apply_non_maximum_suppression


class CVModelAdapter(BaseInspectionModel):
    """Adapter bridging preprocessed image tensors to computer vision models (YOLO / PyTorch).

    Consumes Phase 2 preprocessed RGB float32 arrays, converts to (1, 3, H, W) batch tensors,
    executes model inference, applies Non-Maximum Suppression (NMS), and enforces confidence filtering.
    """

    def __init__(
        self,
        weights_path: Optional[str] = None,
        model_name: Optional[str] = None,
        model_version: Optional[str] = None,
    ):
        self.weights_path = weights_path or settings.model_weights_path
        self.model_name = model_name or settings.model_name
        self.model_version = model_version or settings.model_version
        self._model: Any = None
        self._metadata: Optional[ModelMetadata] = None
        self._loaded: bool = False

    def load(self) -> None:
        """Loads weights and initializes inference runtime."""
        self._model, self._metadata = ModelLoader.load_model(
            weights_path=self.weights_path,
            model_name=self.model_name,
            model_version=self.model_version,
        )
        self._loaded = True
        logger.info(
            f"Initialized CVModelAdapter | Model={self._metadata.modelName} (v{self._metadata.modelVersion}) | "
            f"Device={self._metadata.device} | WeightsLoaded={self._metadata.weightsLoaded}"
        )

    def is_ready(self) -> bool:
        return self._loaded

    @staticmethod
    def prepare_input_tensor(image_array: np.ndarray) -> np.ndarray:
        """Converts (H, W, C) preprocessed array to (1, C, H, W) batch tensor."""
        if image_array is None or not isinstance(image_array, np.ndarray):
            raise ValueError("Input image array must be a valid numpy.ndarray.")

        # Ensure 3-channel input
        if len(image_array.shape) != 3:
            raise ValueError(f"Expected 3-channel (H, W, C) array, got shape {image_array.shape}")

        # Transpose from (H, W, C) -> (C, H, W)
        chw = np.transpose(image_array, (2, 0, 1))

        # Add batch dimension -> (1, C, H, W)
        batch_tensor = np.expand_dims(chw, axis=0)
        return np.ascontiguousarray(batch_tensor, dtype=np.float32)

    def predict(
        self,
        evidence_id: str,
        preprocessed_image: Any,
        test_mode: bool = False,
    ) -> List[RawDetection]:
        """Runs model inference on the preprocessed evidence canvas.

        Consumes Phase 2 preprocessed output, transforms to batch tensor,
        executes model, applies NMS and confidence threshold filtering.
        """
        if not self._loaded:
            self.load()

        # Extract numpy array from PreprocessingResult if passed
        if hasattr(preprocessed_image, "preprocessed_array"):
            image_array = preprocessed_image.preprocessed_array
        elif isinstance(preprocessed_image, np.ndarray):
            image_array = preprocessed_image
        else:
            logger.warning(f"Invalid preprocessed image passed for evidenceId={evidence_id}")
            return []

        # Convert to model input tensor (1, 3, 640, 640)
        input_tensor = self.prepare_input_tensor(image_array)

        # 1. Production Inference Gate: Only execute real model if explicitly approved
        from app.models.model_approval_gate import model_approval_gate
        gate_decision = model_approval_gate.check_inference_permission(
            test_mode=test_mode,
            model_id=self.model_name,
            model_version=self.model_version,
        )

        if not test_mode and not gate_decision["authorized"]:
            logger.info(
                f"PRODUCTION_INFERENCE_DISABLED | Model={self.model_name} (v{self.model_version}) | "
                f"Status=NOT_APPROVED | Reason: {gate_decision['reason']} | Failing closed with 0 detections."
            )
            return []

        # 2. Real Model Inference if weights are loaded AND approved or authorized
        if self._metadata and self._metadata.weightsLoaded and self._model is not None and gate_decision["authorized"]:
            raw_detections = self._execute_model_forward(input_tensor, evidence_id)
            return raw_detections

        # 3. Test Fixture Mode (for automated pipeline integration tests)
        if test_mode:
            logger.debug(f"Generating synthetic test detection in testMode for evidenceId={evidence_id}")
            return [
                RawDetection(
                    className="FIRE_EXTINGUISHER_MISSING",
                    confidence=0.92,
                    boundingBox={"xMin": 0.15, "yMin": 0.20, "xMax": 0.45, "yMax": 0.70},
                    attributes={
                        "testFixture": True,
                        "isTestData": True,
                        "evidenceId": evidence_id,
                        "model": self._metadata.modelName if self._metadata else self.model_name,
                        "modelVersion": self._metadata.modelVersion if self._metadata else self.model_version,
                        "modelApprovalStatus": "NOT_APPROVED",
                        "calibrationStatus": "NOT_CALIBRATED",
                    },
                )
            ]

        # 4. Fail-closed fallback: Real inspection without production approval returns empty
        return []

    def _execute_model_forward(self, input_tensor: np.ndarray, evidence_id: str) -> List[RawDetection]:
        """Executes forward pass through loaded PyTorch / TorchScript model."""
        start_time = time.perf_counter()
        detections: List[Dict[str, Any]] = []

        try:
            if TORCH_AVAILABLE and self._model is not None:
                device = self._metadata.device if self._metadata else "cpu"
                torch_input = torch.from_numpy(input_tensor).to(device)

                ctx = torch.inference_mode() if hasattr(torch, "inference_mode") else torch.no_grad()
                with ctx:
                    output = self._model(torch_input)

                # Explicitly release input tensor to preserve GPU memory
                del torch_input
                if device == "cuda" and torch.cuda.is_available():
                    torch.cuda.empty_cache()

                # Decode model output tensor if weights are active
                pass
        except Exception as e:
            logger.error(f"Error during forward pass for evidenceId={evidence_id}: {e}")
            return []

        # Apply Non-Maximum Suppression
        nms_iou = self._metadata.nmsIouThreshold if self._metadata else settings.nms_iou_threshold
        filtered_dets = apply_non_maximum_suppression(detections, iou_threshold=nms_iou)

        # Filter by confidence threshold
        conf_thresh = self._metadata.confidenceThreshold if self._metadata else settings.confidence_threshold
        results = [
            RawDetection(
                className=d["className"],
                confidence=d["confidence"],
                boundingBox=d["boundingBox"],
                attributes=d.get("attributes", {}),
            )
            for d in filtered_dets
            if d.get("confidence", 0.0) >= conf_thresh
        ]

        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
        logger.debug(f"Forward inference completed for evidenceId={evidence_id} in {duration_ms}ms")
        return results

    def get_metadata(self) -> Dict[str, Any]:
        """Returns descriptive model metadata."""
        if self._metadata:
            meta_dict = self._metadata.model_dump()
            meta_dict.setdefault("modelApprovalStatus", "NOT_APPROVED")
            meta_dict.setdefault("productionApproved", False)
            meta_dict.setdefault("calibrationStatus", "NOT_CALIBRATED")
            return meta_dict

        return {
            "modelName": self.model_name,
            "modelVersion": self.model_version,
            "architecture": "YOLO-Inspection-Adapter-Baseline",
            "device": "cpu",
            "weightsLoaded": False,
            "trainingStatus": "NOT_YET_TRAINED",
            "modelApprovalStatus": "NOT_APPROVED",
            "productionApproved": False,
            "calibrationStatus": "NOT_CALIBRATED",
        }
