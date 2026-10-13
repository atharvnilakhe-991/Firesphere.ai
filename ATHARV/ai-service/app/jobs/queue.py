"""Job queue abstraction and implementations (In-Memory and Redis)."""

from abc import ABC, abstractmethod
import queue
from typing import Optional
import uuid
from app.core.config import settings
from app.core.logging import logger


class BaseJobQueue(ABC):
    """Abstract base class for asynchronous AI job queues."""

    @abstractmethod
    def enqueue(self, job_id: uuid.UUID) -> None:
        """Enqueues a job ID for worker processing."""
        pass

    @abstractmethod
    def dequeue(self, timeout_seconds: float = 1.0) -> Optional[uuid.UUID]:
        """Dequeues the next pending job ID, blocking up to timeout_seconds."""
        pass

    @abstractmethod
    def get_queue_length(self) -> int:
        """Returns approximate count of queued pending jobs."""
        pass


class InMemoryJobQueue(BaseJobQueue):
    """Thread-safe in-memory FIFO queue for testing and standalone execution."""

    def __init__(self):
        self._queue: queue.Queue = queue.Queue()

    def enqueue(self, job_id: uuid.UUID) -> None:
        self._queue.put(str(job_id))

    def dequeue(self, timeout_seconds: float = 1.0) -> Optional[uuid.UUID]:
        try:
            jid_str = self._queue.get(block=True, timeout=timeout_seconds)
            return uuid.UUID(jid_str)
        except queue.Empty:
            return None

    def get_queue_length(self) -> int:
        return self._queue.qsize()

    def clear(self) -> None:
        """Empties queue for unit tests."""
        while not self._queue.empty():
            try:
                self._queue.get_nowait()
            except queue.Empty:
                break


class RedisJobQueue(BaseJobQueue):
    """Production Redis-backed job queue utilizing LPUSH and BRPOP operations.

    Falls back to in-memory queue if Redis is not configured or unavailable.
    """

    QUEUE_KEY = "firesphere:ai:job_queue"

    def __init__(self, redis_url: Optional[str] = None):
        self.redis_url = redis_url or settings.redis_url
        self._fallback = InMemoryJobQueue()
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
            logger.info("Connected to Redis job queue.")
        except Exception as e:
            logger.warning(
                f"Redis unavailable ({type(e).__name__}); falling back to in-memory job queue."
            )
            self._redis_client = None

    def enqueue(self, job_id: uuid.UUID) -> None:
        if self._redis_client is not None:
            try:
                self._redis_client.lpush(self.QUEUE_KEY, str(job_id))
                return
            except Exception as e:
                logger.warning(f"Redis enqueue failed ({e}); falling back.")

        self._fallback.enqueue(job_id)

    def dequeue(self, timeout_seconds: float = 1.0) -> Optional[uuid.UUID]:
        if self._redis_client is not None:
            try:
                # BRPOP returns tuple (key, value) or None
                timeout_int = max(1, int(timeout_seconds))
                result = self._redis_client.brpop(self.QUEUE_KEY, timeout=timeout_int)
                if result:
                    _, jid_str = result
                    return uuid.UUID(jid_str)
                return None
            except Exception as e:
                logger.warning(f"Redis dequeue failed ({e}); checking fallback.")

        return self._fallback.dequeue(timeout_seconds)

    def get_queue_length(self) -> int:
        if self._redis_client is not None:
            try:
                return self._redis_client.llen(self.QUEUE_KEY)
            except Exception:
                pass
        return self._fallback.get_queue_length()
