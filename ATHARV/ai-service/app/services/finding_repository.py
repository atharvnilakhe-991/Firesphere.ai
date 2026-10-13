"""FireSphere.ai — Finding Repository for Inspection Analysis and Officer Verification.

Provides in-memory and persistence abstraction for AI findings, supporting retrieval,
updates, concurrency control, and audit history preservation across runs and reviews.
"""

from datetime import datetime, timezone
import threading
from typing import Dict, List, Optional
import uuid
from app.core.logging import logger
from app.schemas.finding import Finding


class BaseFindingRepository:
    """Abstract interface for storing and retrieving inspection findings.

    Persistence Contract:
    - Implementations must indicate whether records are durable across process restarts via `is_durable`.
    - Production durable persistence is owned by the Spring Boot backend PostgreSQL store.
    """

    is_durable: bool = False

    def save_finding(self, finding: Finding) -> Finding:
        raise NotImplementedError

    def get_finding(self, finding_id: uuid.UUID) -> Optional[Finding]:
        raise NotImplementedError

    def get_findings_by_inspection(self, inspection_id: uuid.UUID) -> List[Finding]:
        raise NotImplementedError

    def update_finding(self, finding: Finding) -> Finding:
        raise NotImplementedError


class InMemoryFindingRepository(BaseFindingRepository):
    """Thread-safe in-memory finding repository.

    CRITICAL PERSISTENCE BOUNDARY:
    - This repository is an ephemeral in-process cache intended for local pipeline execution
      and automated testing.
    - Data DOES NOT survive process restarts or horizontal scaling instances.
    - Official durable persistence is owned by the Spring Boot backend PostgreSQL store
      (tables: ai_analysis_runs, ai_findings, ai_officer_reviews, ai_audit_trail).
    """

    is_durable: bool = False

    def __init__(self):
        self._lock = threading.Lock()
        self._findings_by_id: Dict[str, Finding] = {}
        self._findings_by_inspection: Dict[str, List[str]] = {}

    def save_finding(self, finding: Finding) -> Finding:
        """Stores a finding record."""
        fid_str = str(finding.findingId)
        iid_str = str(finding.inspectionId) if finding.inspectionId else "unassigned"

        with self._lock:
            self._findings_by_id[fid_str] = finding
            if iid_str not in self._findings_by_inspection:
                self._findings_by_inspection[iid_str] = []
            if fid_str not in self._findings_by_inspection[iid_str]:
                self._findings_by_inspection[iid_str].append(fid_str)

        return finding

    def get_finding(self, finding_id: uuid.UUID) -> Optional[Finding]:
        """Retrieves a finding by UUID."""
        fid_str = str(finding_id)
        with self._lock:
            return self._findings_by_id.get(fid_str)

    def get_findings_by_inspection(self, inspection_id: uuid.UUID) -> List[Finding]:
        """Retrieves all findings linked to an inspection session."""
        iid_str = str(inspection_id)
        with self._lock:
            finding_ids = self._findings_by_inspection.get(iid_str, [])
            return [self._findings_by_id[fid] for fid in finding_ids if fid in self._findings_by_id]

    def update_finding(self, finding: Finding) -> Finding:
        """Updates an existing finding record."""
        fid_str = str(finding.findingId)
        with self._lock:
            self._findings_by_id[fid_str] = finding
        return finding

    def clear(self) -> None:
        """Clears repository contents for testing."""
        with self._lock:
            self._findings_by_id.clear()
            self._findings_by_inspection.clear()


# Global finding repository singleton
finding_repository = InMemoryFindingRepository()
