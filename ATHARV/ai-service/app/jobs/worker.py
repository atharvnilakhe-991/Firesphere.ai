"""Asynchronous AI job worker process executing pipeline and report drafting tasks."""

from datetime import datetime, timezone
import threading
import time
from typing import Optional
import uuid
from app.core.config import settings
from app.core.error_codes import AIErrorCode
from app.core.exceptions import FireSphereAIException
from app.core.logging import logger
from app.core.metrics import metrics
from app.jobs.queue import BaseJobQueue, InMemoryJobQueue, RedisJobQueue
from app.jobs.repository import BaseJobRepository, InMemoryJobRepository, RedisJobRepository
from app.jobs.retry import RetryPolicy
from app.pipelines.inspection_pipeline import InspectionPipeline
from app.reporting.deterministic_generator import DeterministicReportGenerator
from app.schemas.job import AIJobRecord, JobStatus


class AIJobWorker:
    """Worker process consuming AI jobs from queue and executing CV inference and report drafting.

    Ensures strict separation between jobId, runId, reportId, and inspectionId.
    Enforces tenant context propagation, bounded retries, and GPU inference safety.
    """

    def __init__(
        self,
        repository: Optional[BaseJobRepository] = None,
        job_queue: Optional[BaseJobQueue] = None,
        pipeline: Optional[InspectionPipeline] = None,
        report_generator: Optional[DeterministicReportGenerator] = None,
    ):
        if repository is not None:
            self.repository = repository
        else:
            self.repository = RedisJobRepository() if settings.redis_url else InMemoryJobRepository()

        if job_queue is not None:
            self.queue = job_queue
        else:
            self.queue = RedisJobQueue() if settings.redis_url else InMemoryJobQueue()

        self.pipeline = pipeline or InspectionPipeline()
        self.report_generator = report_generator or DeterministicReportGenerator()
        self._running = False

    def process_one_job(self, timeout_seconds: float = 1.0) -> bool:
        """Dequeues and processes a single AI job. Returns True if a job was processed."""
        job_id = self.queue.dequeue(timeout_seconds=timeout_seconds)
        if not job_id:
            return False

        job = self.repository.get(job_id)
        if not job:
            logger.warning(f"Worker dequeued job {job_id} but record was not found in repository.")
            return False

        if job.status in (JobStatus.COMPLETED, JobStatus.CANCELLED):
            logger.info(f"Skipping already terminated job {job_id} (status={job.status}).")
            return True

        # Transition to RUNNING
        job.status = JobStatus.RUNNING
        job.startedAt = datetime.now(timezone.utc)
        self.repository.update(job)

        start_time = time.perf_counter()
        logger.info(
            f"AI_JOB_STARTED | jobId={job.jobId} | inspectionId={job.inspectionId} | "
            f"tenantId={job.tenantId} | attempt={job.attempt}/{job.maxAttempts} | "
            f"correlationId={job.correlationId or 'none'}"
        )
        metrics.inc_counter("ai_jobs_total", labels={"tenant": job.tenantId})

        try:
            # 1. Execute Phase 1-4 Computer Vision & Intelligence Pipeline
            inf_start = time.perf_counter()
            ai_run = self.pipeline.execute(
                inspection_id=job.inspectionId,
                evidence_ids=job.evidenceIds,
                test_mode=job.testMode,
                occupancy_context=job.occupancyContext,
                historical_context=job.historicalContext,
                tenant_id=job.tenantId,
            )
            inf_duration = time.perf_counter() - inf_start
            metrics.observe_duration("ai_inference_duration_seconds", inf_duration)

            # Extract distinct runId
            analysis_run_id = ai_run.runId

            # 2. Execute Phase 5 Deterministic Report Drafting Layer
            rpt_start = time.perf_counter()
            report_draft = self.report_generator.generate_draft(
                inspection_id=job.inspectionId,
                run_id=analysis_run_id,
                findings=ai_run.findings,
                evidence_ids=job.evidenceIds,
                occupancy_context=job.occupancyContext,
                historical_context=job.historicalContext,
                officer_remarks=job.officerRemarks,
                checklist_data=job.checklistData,
                is_test_data=job.testMode,
                model_name=ai_run.model,
                model_version=ai_run.modelVersion,
                execution_duration_ms=ai_run.executionDurationMs,
            )
            rpt_duration = time.perf_counter() - rpt_start
            metrics.observe_duration("ai_report_generation_duration_seconds", rpt_duration)

            # Extract distinct reportId
            report_id = report_draft.reportId

            # Build serializable analysis dict
            analysis_dict = {
                "runId": str(analysis_run_id),
                "status": "COMPLETED",
                "findings": [f.model_dump(mode="json") for f in ai_run.findings],
                "model": ai_run.model,
                "modelVersion": ai_run.modelVersion,
                "executionDurationMs": ai_run.executionDurationMs,
            }
            report_dict = report_draft.model_dump(mode="json")

            # 3. Mark Job COMPLETED
            job.status = JobStatus.COMPLETED
            job.completedAt = datetime.now(timezone.utc)
            job.analysisRunId = analysis_run_id
            job.reportId = report_id
            job.resultData = {
                "analysis": analysis_dict,
                "report": report_dict,
            }
            job.errorCode = None
            job.errorMessage = None
            self.repository.update(job)

            total_duration = time.perf_counter() - start_time
            logger.info(
                f"AI_JOB_COMPLETED | jobId={job.jobId} | runId={analysis_run_id} | "
                f"reportId={report_id} | findings={len(ai_run.findings)} | "
                f"duration={total_duration:.3f}s"
            )
            metrics.inc_counter("ai_jobs_completed_total", labels={"tenant": job.tenantId})
            metrics.observe_duration("ai_job_duration_seconds", total_duration)
            return True

        except Exception as exc:
            total_duration = time.perf_counter() - start_time
            is_retryable = RetryPolicy.is_retryable(exc)
            can_retry = is_retryable and (job.attempt < job.maxAttempts)

            err_code = getattr(exc, "error_code", None) or AIErrorCode.INTERNAL_ERROR.value
            err_msg = str(exc) or "An internal error occurred during AI job execution."

            if can_retry:
                job.attempt += 1
                job.status = JobStatus.RETRYING
                job.errorCode = err_code
                job.errorMessage = f"Attempt failed ({err_code}): {err_msg}"
                backoff_delay = RetryPolicy.compute_backoff_seconds(job.attempt)
                job.nextRetryAt = datetime.now(timezone.utc)
                self.repository.update(job)

                logger.warning(
                    f"AI_JOB_RETRIED | jobId={job.jobId} | nextAttempt={job.attempt}/{job.maxAttempts} | "
                    f"backoffDelay={backoff_delay}s | error={err_code}"
                )
                metrics.inc_counter("ai_jobs_retried_total", labels={"tenant": job.tenantId})
                self.queue.enqueue(job.jobId)
            else:
                job.status = JobStatus.FAILED
                job.completedAt = datetime.now(timezone.utc)
                job.errorCode = err_code
                job.errorMessage = err_msg
                self.repository.update(job)

                logger.error(
                    f"AI_JOB_FAILED | jobId={job.jobId} | error={err_code} | "
                    f"duration={total_duration:.3f}s | fatal={'non-retryable' if not is_retryable else 'exhausted'}",
                    exc_info=False,
                )
                metrics.inc_counter("ai_jobs_failed_total", labels={"tenant": job.tenantId})

            return True

    def run_worker_loop(self, stop_event: Optional[threading.Event] = None) -> None:
        """Continuous execution loop for worker daemon process."""
        self._running = True
        logger.info("AI job worker loop started, listening for queued jobs...")
        while self._running:
            if stop_event and stop_event.is_set():
                logger.info("Stop event received; terminating worker loop.")
                break
            try:
                processed = self.process_one_job(timeout_seconds=1.0)
                if not processed:
                    time.sleep(0.1)
            except Exception as e:
                logger.error(f"Unexpected error in worker loop: {e}", exc_info=True)
                time.sleep(1.0)

    def stop(self) -> None:
        """Signals the worker loop to stop gracefully."""
        self._running = False


if __name__ == "__main__":
    logger.info("Starting standalone FireSphere AI Worker daemon...")
    worker = AIJobWorker()
    try:
        worker.run_worker_loop()
    except KeyboardInterrupt:
        logger.info("Worker stopped by user.")
        worker.stop()
