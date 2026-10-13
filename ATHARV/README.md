# FireSphere.ai — Member 4 Submission (ATHARV)

## Overview & Scope of Responsibility
This directory houses the complete engineering deliverables for **Member 4** of the FireSphere.ai platform:
* **AI & Computer Vision Core**: Automated defect detection, multi-hazard classification, OpenCV preprocessing, letterbox transformation, coordinate projection, and Non-Maximum Suppression (NMS).
* **Intelligence Layer**: Grounded explainability engine (WHAT, WHERE, CONFIDENCE, WHY, VERIFY), deterministic severity evaluation, context-aware operational risk scoring, and physical officer verification checklists.
* **Deterministic Report Generation**: Evidence-traceable advisory inspection report drafting, executive summary compilation, and officer review lifecycle.
* **Asynchronous Integration Tier**: Decoupled FastAPI endpoints, Redis FIFO job queues, MinIO/S3 evidence storage client with SHA-256 verification, bounded retry policies, and fault isolation.
* **Dataset Hygiene & Governance**: Dataset validation, class mapping, deduplication (SHA-256 / dHash), group-aware splitting, and empirical model decision gates.

---

## Directory Structure

```
ATHARV/
├── README.md                           # Member 4 deliverable overview (this file)
├── .gitignore                          # Protective gitignore for Python, caches, and datasets
├── fsm_door_names.txt                  # Phase 8.8 exit door semantic audit dataset inventory
├── docs/                               # Phase 8, 9, and 10 technical documentation
│   ├── phase8_*                        # Perception specs, dataset audits, training reports, model cards
│   ├── phase9_*                        # Class capability matrix, threshold calibration, model selection
│   └── phase10_*                       # API contracts, backend integration specs, security & handoff
└── ai-service/                         # Microservice implementation
    ├── app/                            # Application source code
    │   ├── api/                        # FastAPI endpoints (health, inspection, report, jobs, metrics)
    │   ├── core/                       # Config, logging, security, custom exceptions
    │   ├── cv/                         # OpenCV preprocessing, letterbox, quality, bbox transforms
    │   ├── evaluation/                 # Evaluators, ECE calibration, threshold selection
    │   ├── intelligence/               # Severity engine, risk scoring, explainability, verification
    │   ├── models/                     # PyTorch CV model adapters, NMS, registry loader
    │   ├── pipelines/                  # End-to-end inspection and reporting pipelines
    │   ├── queue/                      # Redis & memory FIFO queue abstractions, retry policies
    │   ├── registry/                   # Model registry and metadata governance
    │   ├── reporting/                  # Deterministic report drafting, limitations, review states
    │   ├── schemas/                    # Pydantic schemas (Finding, Report, Inspection, Job, Run)
    │   ├── services/                   # Business logic coordination services
    │   ├── storage/                    # S3 / MinIO evidence client with cryptographic checks
    │   ├── training/                   # Dataset validators, splitters, trainers, readiness gates
    │   ├── main.py                     # API service entry point
    │   └── worker.py                   # Async queue worker daemon
    ├── artifacts/                      # Model checkpoints, evaluation gates, and visual audit samples
    ├── datasets/                       # Dataset class mappings, split manifests, and validation reports
    ├── docs/                           # In-service technical documentation and guidelines
    ├── tests/                          # Automated pytest suite (424 passing tests)
    ├── .dockerignore                   # Docker exclusion rules
    ├── .env.example                    # Safe environment variable template (no secrets)
    ├── Dockerfile                      # Production container specification
    ├── docker-compose.dev.yml          # Local container development environment
    ├── pytest.ini                      # Pytest execution configuration
    ├── README.md                       # Comprehensive service documentation & quickstart
    └── requirements.txt                # Pinned production dependencies
```

---

## Quality Assurance & Verification
* **Automated Tests**: 424 automated unit and integration tests passing (`pytest ai-service/tests`).
* **Governance Boundaries**: Fully enforces fail-closed operations, zero autonomous NOC issuance, zero hallucinated regulatory citations, and advisory-only status for human fire officers.
* **Security & Secrets**: Zero hardcoded credentials; safe `.env.example` template provided.
