"""High-level job coordination manager handling submission, idempotency, and retrieval."""

import threading
from typing import Optional, Tuple
import uuid
from app import __version__
from app.core.config import settings
from app.core.logging import logger
from app.jobs.queue import BaseJobQueue, InMemoryJobQueue, RedisJobQueue
from app.jobs.repository import BaseJobRepository, InMemoryJobRepository, RedisJobRepository
from app.jobs.worker import AIJobWorker
from app.schemas.job import (
    AIJobCreateRequest,
    AIJobRecord,
    AIJobResponse,
    AIJobResultResponse,
    JobStatus,
)


class JobManager:
    """Coordinates job lifecycle, idempotency checks, queue dispatch, and status lookups."""

    def __init__(
        self,
        repository: Optional[BaseJobRepository] = None,
        job_queue: Optional[BaseJobQueue] = None,
    ):
        if repository is not None:
            self.repository = repository
        else:
            self.repository = RedisJobRepository() if settings.redis_url else InMemoryJobRepository()

        if job_queue is not None:
            self.queue = job_queue
        else:
            self.queue = RedisJobQueue() if settings.redis_url else InMemoryJobQueue()

        self.worker = AIJobWorker(repository=self.repository, job_queue=self.queue)
        self._worker_thread: Optional[threading.Thread] = None
        self._stop_event: Optional[threading.Event] = None

    def submit_job(
        self,
        request: AIJobCreateRequest,
        correlation_id: Optional[str] = None,
    ) -> Tuple[AIJobResponse, bool]:
        """Submits an AI job.

        Returns tuple of (AIJobResponse, is_duplicate).
        If idempotencyKey is supplied and already recorded, returns existing job without re-dispatching.
        """
        # 1. Idempotency Check
        if request.idempotencyKey:
            existing = self.repository.find_by_idempotency_key(request.idempotencyKey)
            if existing:
                logger.info(
                    f"Idempotent hit: Reusing existing job {existing.jobId} for key '{request.idempotencyKey}'"
                )
                return existing.to_response(), True

        # 2. Create New Job Record
        job_id = uuid.uuid4()
        record = AIJobRecord(
            jobId=job_id,
            inspectionId=request.inspectionId,
            tenantId=request.tenantId or "default",
            status=JobStatus.QUEUED,
            operation=request.operation,
            evidenceIds=request.evidenceIds,
            occupancyContext=request.occupancyContext or "UNKNOWN",
            historicalContext=request.historicalContext or "UNAVAILABLE",
            testMode=request.testMode,
            officerRemarks=request.officerRemarks,
            checklistData=request.checklistData,
            idempotencyKey=request.idempotencyKey,
            correlationId=correlation_id,
            attempt=1,
            maxAttempts=settings.max_retries,
            modelName=settings.model_name,
            modelVersion=settings.model_version,
            serviceVersion=__version__,
        )

        # 3. Persist and Enqueue
        self.repository.save(record)
        self.queue.enqueue(job_id)

        logger.info(
            f"AI job created and queued | jobId={job_id} | inspectionId={request.inspectionId} | "
            f"tenantId={record.tenantId} | evidenceCount={len(request.evidenceIds)}"
        )
        return record.to_response(), False

    def get_job(self, job_id: uuid.UUID) -> Optional[AIJobResponse]:
        """Fetches public status view of an AI job."""
        record = self.repository.get(job_id)
        return record.to_response() if record else None

    def get_job_result(self, job_id: uuid.UUID) -> Optional[AIJobResultResponse]:
        """Fetches complete structured result for a finished AI job."""
        record = self.repository.get(job_id)
        if not record:
            return None

        analysis_data = None
        report_data = None
        if record.resultData:
            analysis_data = record.resultData.get("analysis")
            report_data = record.resultData.get("report")

        return AIJobResultResponse(
            jobId=record.jobId,
            inspectionId=record.inspectionId,
            tenantId=record.tenantId,
            status=record.status,
            analysisRunId=record.analysisRunId,
            reportId=record.reportId,
            createdAt=record.createdAt,
            completedAt=record.completedAt,
            analysis=analysis_data,
            report=report_data,
            errorCode=record.errorCode,
            errorMessage=record.errorMessage,
        )

    def start_background_worker(self) -> None:
        """Starts an in-process background worker thread for standalone/dev deployments."""
        if self._worker_thread and self._worker_thread.is_alive():
            return
        self._stop_event = threading.Event()
        self._worker_thread = threading.Thread(
            target=self.worker.run_worker_loop,
            args=(self._stop_event,),
            daemon=True,
            name="firesphere-ai-worker",
        )
        self._worker_thread.start()
        logger.info("Started in-process background worker thread.")

    def stop_background_worker(self) -> None:
        """Stops the in-process background worker thread."""
        if self._stop_event:
            self._stop_event.set()
        self.worker.stop()
        if self._worker_thread:
            self._worker_thread.join(timeout=2.0)
            self._worker_thread = None

    def reset_for_tests(self) -> None:
        """Clears queue and repository data for test isolation."""
        if hasattr(self.repository, "clear"):
            self.repository.clear()
        if hasattr(self.queue, "clear"):
            self.queue.clear()


# Global job manager singleton instance
job_manager = JobManager()
