"""Base pipeline abstract class."""

from abc import ABC, abstractmethod
from typing import Any


class BasePipeline(ABC):
    """Abstract base class for all AI processing pipelines."""

    @abstractmethod
    def execute(self, *args: Any, **kwargs: Any) -> Any:
        """Executes the pipeline workflow."""
        pass
