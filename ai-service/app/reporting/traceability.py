"""Traceability engine connecting every AI report statement to structured source evidence."""

from typing import List, Optional
import uuid
from app.schemas.report import TraceabilityRecord


class TraceabilityEngine:
    """Manages audit-grade statement-to-source traceability records."""

    def __init__(self):
        self._records: List[TraceabilityRecord] = []
        self._counter: int = 1

    def record_statement(
        self,
        report_section: str,
        statement_text: str,
        source_type: str,
        source_id: str,
        evidence_id: Optional[uuid.UUID] = None,
        finding_id: Optional[uuid.UUID] = None,
        source_field: str = "explanation",
    ) -> TraceabilityRecord:
        """Creates and indexes an audit traceability link for a report statement."""
        stmt_id = f"stmt-{self._counter:03d}"
        self._counter += 1

        record = TraceabilityRecord(
            statementId=stmt_id,
            reportSection=report_section,
            statementText=statement_text,
            sourceType=source_type,
            sourceId=source_id,
            evidenceId=evidence_id,
            findingId=finding_id,
            sourceField=source_field,
        )
        self._records.append(record)
        return record

    def get_records(self) -> List[TraceabilityRecord]:
        """Returns all registered traceability records in generation order."""
        return list(self._records)
