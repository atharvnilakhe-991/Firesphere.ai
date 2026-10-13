"""Model checkpoint management, saving, and SHA-256 integrity hashing."""

import hashlib
from pathlib import Path
from typing import Any, Dict, Optional
import torch
import torch.nn as nn
from app.core.logging import logger


class CheckpointManager:
    """Safely saves, verifies, and restores model weights and training state."""

    @staticmethod
    def compute_sha256(file_path: Path) -> str:
        """Computes SHA-256 digest of saved model weight artifact."""
        hasher = hashlib.sha256()
        with open(file_path, "rb") as f:
            while chunk := f.read(65536):
                hasher.update(chunk)
        return hasher.hexdigest()

    @classmethod
    def save_checkpoint(
        cls,
        model: nn.Module,
        epoch: int,
        metrics: Dict[str, float],
        output_path: Path,
        optimizer: Optional[torch.optim.Optimizer] = None,
        extra_metadata: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """Serializes model state and computes artifact cryptographic checksum."""
        output_path = Path(output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)

        payload = {
            "epoch": epoch,
            "model_state_dict": model.state_dict(),
            "metrics": metrics,
            "metadata": extra_metadata or {},
        }
        if optimizer:
            payload["optimizer_state_dict"] = optimizer.state_dict()

        torch.save(payload, str(output_path))
        checksum = cls.compute_sha256(output_path)

        logger.info(
            f"Model checkpoint saved | Path={output_path.name} | Epoch={epoch} | SHA256={checksum[:12]}..."
        )
        return {
            "checkpointPath": str(output_path),
            "epoch": epoch,
            "metrics": metrics,
            "sha256": checksum,
        }

    @classmethod
    def load_checkpoint(
        cls,
        checkpoint_path: Path,
        model: nn.Module,
        optimizer: Optional[torch.optim.Optimizer] = None,
        expected_checksum: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Restores model weights with optional cryptographic verification."""
        checkpoint_path = Path(checkpoint_path)
        if not checkpoint_path.exists():
            raise FileNotFoundError(f"Checkpoint not found at '{checkpoint_path}'")

        if expected_checksum:
            actual_checksum = cls.compute_sha256(checkpoint_path)
            if actual_checksum.lower() != expected_checksum.strip().lower():
                raise ValueError(
                    f"Checkpoint integrity verification failed: expected {expected_checksum}, got {actual_checksum}"
                )

        payload = torch.load(str(checkpoint_path), map_location="cpu", weights_only=False)
        model.load_state_dict(payload["model_state_dict"])

        if optimizer and "optimizer_state_dict" in payload:
            optimizer.load_state_dict(payload["optimizer_state_dict"])

        logger.info(f"Loaded checkpoint from '{checkpoint_path.name}' (Epoch={payload.get('epoch')})")
        return payload
