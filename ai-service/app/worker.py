"""Dedicated AI Worker Process Entry Point for Production Deployments."""

import signal
import sys
import threading
import time
from app.core.config import settings
from app.core.logging import logger
from app.jobs.manager import job_manager
from app.models.cv_model_adapter import CVModelAdapter


def run_worker() -> None:
    """Runs a dedicated, standalone AI worker process listening on the queue."""
    logger.info(
        f"Starting dedicated AI worker process | Service={settings.service_name} | "
        f"Concurrency={settings.worker_concurrency} | Device={settings.inference_device}"
    )

    # 1. Initialize and cache model in process memory once
    cv_model = CVModelAdapter(
        model_name=settings.model_name,
        model_version=settings.model_version,
    )
    cv_model.load()

    stop_event = threading.Event()

    def handle_exit(sig, frame):
        logger.info(f"Received shutdown signal ({sig}); stopping worker gracefully...")
        stop_event.set()
        job_manager.worker.stop()

    signal.signal(signal.SIGINT, handle_exit)
    signal.signal(signal.SIGTERM, handle_exit)

    logger.info("AI Worker active. Polling job queue...")
    try:
        job_manager.worker.run_worker_loop(stop_event=stop_event)
    except Exception as e:
        logger.error(f"Worker encountered fatal error: {e}", exc_info=True)
        sys.exit(1)

    logger.info("Worker process terminated gracefully.")


if __name__ == "__main__":
    run_worker()
