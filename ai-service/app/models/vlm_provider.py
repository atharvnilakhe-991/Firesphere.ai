"""FireSphere.ai — Common Vision-Language Model (VLM) Provider Abstraction.

Defines the minimal common interface and factory for multi-provider VLM visual inspection.
Enforces unified governance invariants, offline testability, and evidence-analysis contracts.
"""

from abc import ABC, abstractmethod
from typing import Any, Dict, Optional, Union
import numpy as np

from app.models.gemini_schemas import GeminiAnalysisResult


class BaseVLMProvider(ABC):
    """Minimal, decoupled interface for Vision-Language Model provider adapters.

    Provides contract consistency across Gemini, OpenAI, and future VLM backends.
    All implementations strictly enforce:
    1. Fail-closed defaults: offline by default.
    2. Credentials and secrets are never logged or exposed.
    3. Output is strictly advisory: requires mandatory officer verification.
    4. Autonomous Fire NOC compliance decisions are strictly prohibited.
    5. Absence of findings never certifies safe condition (isSafeInference=False).
    6. Technical errors return safe failure envelopes without synthetic observations.
    """

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Returns the canonical provider identity (e.g., 'google-gemini', 'openai')."""
        pass

    @abstractmethod
    def is_enabled(self) -> bool:
        """Returns whether the provider is enabled in configuration."""
        pass

    @abstractmethod
    def is_configured(self) -> bool:
        """Returns whether valid credentials have been supplied for this provider."""
        pass

    @abstractmethod
    def is_ready(self) -> bool:
        """Returns whether the provider is enabled, configured, and initialized."""
        pass

    @abstractmethod
    def get_status(self) -> str:
        """Returns component readiness status: 'DISABLED', 'NOT_CONFIGURED', 'CONFIGURED', 'READY'."""
        pass

    @abstractmethod
    def get_model_id(self) -> str:
        """Returns the configured model identifier."""
        pass

    @abstractmethod
    def get_provider_info(self) -> Dict[str, Any]:
        """Returns sanitized public metadata. NEVER returns secrets, tokens, or keys."""
        pass

    @abstractmethod
    def analyze_evidence(
        self,
        evidence_id: str,
        image_data: Optional[Union[bytes, np.ndarray, Any]] = None,
        context: Optional[Dict[str, Any]] = None,
    ) -> GeminiAnalysisResult:
        """Executes multimodal visual inspection analysis on evidence.

        Always returns a validated GeminiAnalysisResult envelope.
        Catches all internal provider errors and returns safe failure representations.
        """
        pass

    def analyze_image(
        self,
        evidence_id: str,
        image_data: Optional[Union[bytes, np.ndarray, Any]] = None,
        context: Optional[Dict[str, Any]] = None,
    ) -> GeminiAnalysisResult:
        """Alias for analyze_evidence to preserve backward compatibility with existing orchestrators."""
        return self.analyze_evidence(
            evidence_id=evidence_id,
            image_data=image_data,
            context=context,
        )


def get_vlm_provider(
    provider_name: Optional[str] = None,
    settings_obj: Optional[Any] = None,
) -> BaseVLMProvider:
    """Factory retrieving the active VLM provider adapter instance based on configuration."""
    from app.core.config import settings
    cfg = settings_obj or settings

    selected = (provider_name or getattr(cfg, "vlm_provider", "ollama")).strip().lower()

    if selected in ("ollama", "qwen", "ollama-qwen", "ollama_qwen"):
        if settings_obj is not None:
            from app.models.ollama_provider import OllamaQwenProvider
            return OllamaQwenProvider(settings_obj=settings_obj)
        from app.models.ollama_provider import ollama_provider
        return ollama_provider
    elif selected == "openai":
        if settings_obj is not None:
            from app.models.openai_provider import OpenAIProvider
            return OpenAIProvider(settings_obj=settings_obj)
        from app.models.openai_provider import openai_provider
        return openai_provider
    elif selected in ("gemini", "google-gemini"):
        if settings_obj is not None:
            from app.models.gemini_provider import GeminiProvider
            return GeminiProvider(settings_obj=settings_obj)
        from app.models.gemini_provider import gemini_provider
        return gemini_provider
    else:
        raise ValueError(
            f"Unsupported or invalid VLM provider '{selected}'. "
            "Supported providers are: 'ollama', 'gemini', 'openai'."
        )
