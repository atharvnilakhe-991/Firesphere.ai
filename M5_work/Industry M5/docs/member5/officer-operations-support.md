# Officer Operations Support Specification & Technical Guide

**Project:** FireSphere.ai — AI-Powered Smart Fire Administration Application  
**Module:** Officer Operations Support (`ai.firesphere.operations.officer`)  
**Role:** Member 5 — Operations, GIS, Integrations, Notifications, Testing and QA  
**Date:** October 2026  
**Status:** Implemented, Fully Tested (25/25 Module Tests Passing, 298/301 Full Suite Tests Passing, 3 Skipped PostGIS)

---

## 1. Executive Summary & Purpose

The **Officer Operations Support** module is an independently testable, decoupled operational system built for Fire Officers, Station Captains, and Dispatch Commanders. It addresses field operations, inspection task management, officer workload balancing, SLA breach detection, and automated escalation management.

### Key Capabilities:
1. **Operational Task Tracking**: Comprehensive lifecycle tracking (`UNASSIGNED` $\rightarrow$ `ASSIGNED` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED`, `CANCELLED`, `ESCALATED`) for fire safety inspections, hazard verifications, hydrant surveys, evacuation checks, and patrols.
2. **Deterministic Overdue & SLA Logic**: Utilizes an injectable `java.time.Clock` bean for deterministic evaluation of task deadlines, overdue thresholds, and approaching milestones without system clock coupling.
3. **Workload Analysis & Capacity Status**: Real-time aggregation of an officer's assigned, pending, in-progress, completed, overdue, and high-priority tasks with capacity categorizations (`OPTIMAL`, `HEAVY`, `OVERLOADED`).
4. **Operational Alert Engine**: Evaluates overdue inspections, high-priority unassigned tasks, and approaching deadlines. Employs fingerprint-based deduplication to prevent alert floods and seamlessly bridges alerts to Phase 7's `NotificationService`.
5. **Supervisory Escalation Workflow**: Configurable escalation triggers (`firesphere.officer-operations.*`) that automatically or manually reassign breached or high-priority stale tasks to supervisory roles (`STATION_CAPTAIN`) while preventing duplicate escalations.
6. **Multi-Tenant Isolation & Role Security**: Complete data isolation via `TenantContext` and role/badge access control via `OfficerSecurityValidator`.

---

## 2. Architecture & Domain Models

### Package Structure:
```
ai.firesphere.operations.officer
├── alert
│   ├── OfficerEscalationService.java      # SLA breach evaluation, deduplication, supervisor escalation
│   └── OperationalAlertService.java        # Alert evaluation, fingerprint deduplication, push notifications
├── config
│   ├── OfficerOperationsConfig.java        # Injectable java.time.Clock bean configuration
│   └── OfficerOperationsProperties.java    # Configurable SLA, threshold, and role properties
├── controller
│   └── OfficerOperationsController.java    # REST API endpoints (/api/v1/officer-operations)
├── dto
│   ├── EscalationResultDTO.java
│   ├── OfficerTaskCreateRequest.java
│   ├── OfficerTaskResponseDTO.java
│   ├── OfficerTaskStatusUpdateRequest.java
│   ├── OfficerWorkloadSummaryDTO.java
│   └── OperationalAlertDTO.java
├── model
│   ├── EscalationRecord.java               # Immutable record of escalation event
│   ├── OfficerTask.java                    # Core domain entity with deadline & status logic
│   ├── OfficerTaskStatus.java              # State enum (UNASSIGNED, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED, ESCALATED)
│   ├── OfficerTaskType.java                # Type enum (INSPECTION, HAZARD_VERIFICATION, HYDRANT_SURVEY, EVACUATION_CHECK, OPERATIONAL_PATROL)
│   ├── OfficerWorkloadSummary.java         # Workload metrics container
│   └── OperationalAlert.java               # Operational alert record
├── repository
│   ├── InMemoryOfficerTaskRepository.java  # Thread-safe, tenant-isolated repository
│   └── OfficerTaskRepository.java          # Repository contract interface
├── security
│   └── OfficerSecurityValidator.java       # RBAC & officer badge ownership verification
└── service
    ├── OfficerOperationsService.java       # High-level operations contract
    └── OfficerOperationsServiceImpl.java   # Implementation with sorting, validation, and orchestration
```

---

## 3. Configuration Properties

Configured in `application.yml` or overridden via properties:

```yaml
firesphere:
  officer-operations:
    deadline-approaching-threshold-minutes: 120 # Alerts when deadline is within 2 hours
    overdue-escalation-threshold-minutes: 60     # Auto-escalates when overdue by 60 mins
    high-priority-escalation-minutes: 30         # Auto-escalates critical/high priority tasks after 30 mins
    default-escalated-role: "STATION_CAPTAIN"    # Role receiving escalated tasks
    max-active-tasks-per-officer: 10             # Threshold for OVERLOADED capacity status
    auto-escalation-enabled: true               # Feature toggle for automated escalation
```

---

## 4. REST API Specifications

Base URL: `/api/v1/officer-operations`

| Method | Endpoint | Description | Status Codes |
|---|---|---|---|
| `POST` | `/tasks` | Create new operational task or inspection assignment | `201 CREATED`, `400 BAD REQUEST`, `409 CONFLICT` |
| `GET` | `/tasks/{id}` | Get task by UUID | `200 OK`, `404 NOT FOUND`, `403 FORBIDDEN` |
| `GET` | `/tasks/reference/{reference}` | Get task by unique reference identifier | `200 OK`, `404 NOT FOUND`, `403 FORBIDDEN` |
| `GET` | `/tasks` | Filter tasks by `officerBadge`, `status`, `priority`, `fromDate`, `toDate` | `200 OK`, `400 BAD REQUEST` |
| `PUT` | `/tasks/{id}/status` | Update operational task status | `200 OK`, `400 BAD REQUEST`, `404 NOT FOUND` |
| `GET` | `/overdue` | List all tasks whose deadline has passed | `200 OK` |
| `GET` | `/workload/{officerBadge}` | Retrieve workload metrics and capacity status for an officer | `200 OK`, `400 BAD REQUEST`, `403 FORBIDDEN` |
| `POST` | `/alerts/evaluate` | Evaluate operational SLA triggers and generate deduplicated alerts | `200 OK` |
| `POST` | `/escalations/evaluate` | Evaluate overdue/stale tasks and execute auto-escalations | `200 OK` |
| `POST` | `/tasks/{id}/escalate` | Manually escalate a task with reason | `200 OK`, `400 BAD REQUEST`, `404 NOT FOUND` |
| `GET` | `/escalations/history` | Retrieve escalation history records for current tenant | `200 OK` |

---

## 5. Security & Multi-Tenancy

- **Tenant Isolation**: All operations resolve tenant ID from `TenantContext.getRequiredTenantId()`. Repository operations strictly index by `tenantId`, guaranteeing that no officer or dispatcher can access data from another tenant.
- **RBAC & Badge Verification**: `OfficerSecurityValidator` checks the active `Authentication` principal:
  - Supervisory roles (`ROLE_ADMIN`, `ROLE_DISPATCHER`, `ROLE_COMMANDER`, `ROLE_CHIEF`, `ROLE_STATION_CAPTAIN`) have universal visibility across all officer tasks.
  - Officer roles (`ROLE_OFFICER`) can only query and modify tasks matching their own badge/identity. Unauthorized access triggers `AccessDeniedException` (mapped to HTTP 403).

---

## 6. Testing & Verification

Comprehensive test suites validate the implementation:
1. `OfficerOperationsServiceTest`: 15 unit tests covering task lifecycle, deterministic overdue logic via `Clock.fixed(...)`, workload computation, alert generation and deduplication, auto and manual escalation, duplicate escalation prevention, security validation, and tenant isolation.
2. `OfficerOperationsControllerTest`: 10 integration tests validating Spring MVC REST controllers, HTTP status codes (`201`, `200`, `400`, `404`, `409`), payload validation, and tenant resolution headers.

### Full Regression Test Summary:
- **Total Tests Run**: 301
- **Passed**: 298
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 3 (Real PostGIS Docker Testcontainers paused per specification)
- **Maven Build Status**: `BUILD SUCCESS`
