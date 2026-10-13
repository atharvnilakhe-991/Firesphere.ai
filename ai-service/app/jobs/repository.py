"""Job repository abstraction and implementations (In-Memory and Redis)."""

from abc import ABC, abstractmethod
import json
import threading
from typing import Dict, Optional
import uuid
from app.core.config import settings
from app.core.logging import logger
from app.schemas.job import AIJobRecord


class BaseJobRepository(ABC):
    """Abstract base class for persistent storage of AI job records."""

    @abstractmethod
    def save(self, job: AIJobRecord) -> None:
        """Saves a new job record."""
        pass

    @abstractmethod
    def get(self, job_id: uuid.UUID) -> Optional[AIJobRecord]:
        """Retrieves an existing job record by UUID."""
        pass

    @abstractmethod
    def find_by_idempotency_key(self, idempotency_key: str) -> Optional[AIJobRecord]:
        """Finds an existing job record matching the given idempotency key."""
        pass

    @abstractmethod
    def update(self, job: AIJobRecord) -> None:
        """Updates an existing job record state."""
        pass


class InMemoryJobRepository(BaseJobRepository):
    """Thread-safe in-memory job repository for testing and standalone execution."""

    def __init__(self):
        self._lock = threading.Lock()
        self._jobs: Dict[str, AIJobRecord] = {}
        self._idempotency_index: Dict[str, str] = {}

    def save(self, job: AIJobRecord) -> None:
        with self._lock:
            jid_str = str(job.jobId)
            self._jobs[jid_str] = job.model_copy(deep=True)
            if job.idempotencyKey:
                self._idempotency_index[job.idempotencyKey] = jid_str

    def get(self, job_id: uuid.UUID) -> Optional[AIJobRecord]:
        with self._lock:
            job = self._jobs.get(str(job_id))
            return job.model_copy(deep=True) if job else None

    def find_by_idempotency_key(self, idempotency_key: str) -> Optional[AIJobRecord]:
        with self._lock:
            job_id_str = self._idempotency_index.get(idempotency_key)
            if not job_id_str:
                return None
            job = self._jobs.get(job_id_str)
            return job.model_copy(deep=True) if job else None

    def update(self, job: AIJobRecord) -> None:
        with self._lock:
            jid_str = str(job.jobId)
            self._jobs[jid_str] = job.model_copy(deep=True)
            if job.idempotencyKey:
                self._idempotency_index[job.idempotencyKey] = jid_str

    def clear(self) -> None:
        """Clears all records for clean unit testing."""
        with self._lock:
            self._jobs.clear()
            self._idempotency_index.clear()


class RedisJobRepository(BaseJobRepository):
    """Production Redis-backed job repository.

    Stores serialized job JSON under key 'firesphere:ai:jobs:{jobId}' and
    maps idempotency keys under 'firesphere:ai:idempotency:{key}'.
    Falls back gracefully to in-memory storage if Redis is unreachable in dev/test.
    """

    def __init__(self, redis_url: Optional[str] = None):
        self.redis_url = redis_url or settings.redis_url
        self._fallback = InMemoryJobRepository()
        self._redis_client = None
        self._init_redis()

    def _init_redis(self) -> None:
        if not self.redis_url:
            return
        try:
            import redis
            client = redis.from_url(self.redis_url, decode_responses=True, socket_timeout=3.0)
            client.ping()
            self._redis_client = client
            logger.info("Connected to Redis job repository.")
        except Exception as e:
            logger.warning(
                f"Redis unavailable ({type(e).__name__}); falling back to in-memory job repository."
            )
            self._redis_client = None

    def save(self, job: AIJobRecord) -> None:
        if self._redis_client is not None:
            try:
                jid_str = str(job.jobId)
                data_str = job.model_dump_json()
                self._redis_client.set(f"firesphere:ai:jobs:{jid_str}", data_str, ex=86400 * 7)
                if job.idempotencyKey:
                    self._redis_client.set(
                        f"firesphere:ai:idempotency:{job.idempotencyKey}",
                        jid_str,
                        ex=86400 * 7,
                    )
                return
            except Exception as e:
                logger.warning(f"Redis save failed ({e}); updating fallback.")

        self._fallback.save(job)

    def get(self, job_id: uuid.UUID) -> Optional[AIJobRecord]:
        if self._redis_client is not None:
            try:
                jid_str = str(job_id)
                data_str = self._redis_client.get(f"firesphere:ai:jobs:{jid_str}")
                if data_str:
                    return AIJobRecord.model_validate_json(data_str)
            except Exception as e:
                logger.warning(f"Redis get failed ({e}); checking fallback.")

        return self._fallback.get(job_id)

    def find_by_idempotency_key(self, idempotency_key: str) -> Optional[AIJobRecord]:
        if self._redis_client is not None:
            try:
                jid_str = self._redis_client.get(f"firesphere:ai:idempotency:{idempotency_key}")
                if jid_str:
                    return self.get(uuid.UUID(jid_str))
            except Exception as e:
                logger.warning(f"Redis idempotency lookup failed ({e}); checking fallback.")

        return self._fallback.find_by_idempotency_key(idempotency_key)

    def update(self, job: AIJobRecord) -> None:
        self.save(job)
