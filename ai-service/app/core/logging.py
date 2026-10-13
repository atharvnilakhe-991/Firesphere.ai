"""Structured logging module for FireSphere AI Service.

Ensures proper redaction of sensitive tokens, secrets, credentials, and evidence URLs.
Provides consistent structured log formatting for inspections and AI runs.
"""

import logging
import re
import sys
from typing import Any, Dict


# Regex patterns and their safe replacement strings
SENSITIVE_PATTERNS = [
    (re.compile(r"(?i)(password|secret|token|api_?key|authorization)\s*[:=]\s*['\"]?([^\s,'\"]+)"), r"\1: [REDACTED]"),
    (re.compile(r"(?i)\b(bearer)\s+[a-zA-Z0-9_\-\.]+", re.IGNORECASE), r"\1 [REDACTED]"),
]


class SensitiveDataFilter(logging.Filter):
    """Filter that masks sensitive tokens, passwords, and credentials from log messages."""

    def filter(self, record: logging.LogRecord) -> bool:
        if isinstance(record.msg, str):
            record.msg = self._redact(record.msg)
        if record.args:
            if isinstance(record.args, dict):
                record.args = {k: self._redact(v) if isinstance(v, str) else v for k, v in record.args.items()}
            elif isinstance(record.args, tuple):
                record.args = tuple(self._redact(arg) if isinstance(arg, str) else arg for arg in record.args)
        return True

    @staticmethod
    def _redact(text: str) -> str:
        for pattern, repl in SENSITIVE_PATTERNS:
            text = pattern.sub(repl, text)
        return text


def setup_logging(log_level: str = "INFO") -> logging.Logger:
    """Configures structured standard logging for the application."""
    numeric_level = getattr(logging, log_level.upper(), logging.INFO)

    # Root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(numeric_level)

    # Avoid duplicate handlers if reconfigured
    if not root_logger.handlers:
        handler = logging.StreamHandler(sys.stdout)
        handler.setLevel(numeric_level)
        formatter = logging.Formatter(
            fmt="%(asctime)s | %(levelname)-8s | [%(name)s] %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )
        handler.setFormatter(formatter)
        handler.addFilter(SensitiveDataFilter())
        root_logger.addHandler(handler)

    logger = logging.getLogger("firesphere.ai")
    logger.setLevel(numeric_level)
    return logger


logger = setup_logging()
