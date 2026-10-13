# FireSphere.ai — Phase 7: Production Deployment & Containerization Guide

**Document Version:** `1.0.0-phase7`  
**Service Version:** `0.7.0-phase7`  
**Classification:** `INFRASTRUCTURE_READY_MODEL_PENDING`  
**Status:** Approved for Staging & Production Infrastructure Deployment

---

## 1. Architectural Overview

FireSphere.ai employs a decoupled, asynchronous multi-container microservice topology. The AI/ML service (Member 4) is designed to operate in segregated execution modes to ensure high HTTP availability for API consumers while scaling computational workloads (OpenCV preprocessing, PyTorch tensor inference, report compilation) independently.

```mermaid
graph TB
    subgraph Ingress & Gateway
        Kong[API Gateway / Ingress Controller]
    end

    subgraph Internal Network (VPC)
        SpringBoot[Spring Boot Core Backend]
        
        subgraph AI Service Tier
            API1[ai-service (API Mode - Pod 1)]
            API2[ai-service (API Mode - Pod 2)]
            Worker1[ai-worker (Worker Mode - Pod 1)]
            Worker2[ai-worker (Worker Mode - Pod 2)]
        end

        subgraph Messaging & Storage
            Redis[(Redis Job Queue & Cache)]
            MinIO[(MinIO / S3 Object Storage)]
        end
    end

    Kong -->|Bearer Auth / TLS| SpringBoot
    SpringBoot -->|Service Token Auth| API1
    SpringBoot -->|Service Token Auth| API2
    API1 -->|RPUSH Job Payload| Redis
    API2 -->|RPUSH Job Payload| Redis
    Worker1 -->|BRPOP Job Payload| Redis
    Worker2 -->|BRPOP Job Payload| Redis
    Worker1 -->|Fetch Evidence & SHA-256| MinIO
    Worker2 -->|Fetch Evidence & SHA-256| MinIO
    Worker1 -->|Update Job State| Redis
    Worker2 -->|Update Job State| Redis
```

### Execution Modes (`AI_SERVICE_MODE`)

| Mode | Entry Point | Primary Responsibility | Resource Profile |
| :--- | :--- | :--- | :--- |
| `api` | `uvicorn app.main:app` | Validates requests, authenticates service tokens, manages job records, serves `/health`, `/ready`, `/metrics`, and `/docs`. Dispatches jobs to Redis without executing GPU/CPU inference. | CPU: 0.5 - 2 cores, RAM: 512MB - 1GB. Low latency ($< 10\text{ ms}$). |
| `worker` | `python -m app.worker` | Dequeues jobs from Redis (`BRPOP`), pulls evidence from MinIO/S3, verifies SHA-256 hashes, executes OpenCV decoding, runs PyTorch inference, executes Phase 4 intelligence and Phase 5 report drafting, and commits results. | CPU: 2 - 8 cores, RAM: 2GB - 8GB, GPU: 1x NVIDIA RTX/T4/A10G (if available) with automated CPU fallback. |
| `combined` | `uvicorn app.main:app` | Runs HTTP API and starts in-process worker thread (used in development, integration testing, and single-node staging environments). | CPU: 2 - 4 cores, RAM: 2GB - 4GB. |

---

## 2. Containerization & Docker Security

The AI service adheres to the principle of least privilege, minimal attack surface, and container security best practices.

### 2.1 Multi-Stage Production Dockerfile

```dockerfile
# ==============================================================================
# Stage 1: Build & Dependencies
# ==============================================================================
FROM python:3.11-slim-bookworm AS builder

WORKDIR /build

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --user --no-warn-script-location -r requirements.txt

# ==============================================================================
# Stage 2: Runtime Container
# ==============================================================================
FROM python:3.11-slim-bookworm AS runner

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/home/appuser/.local/bin:$PATH" \
    AI_SERVICE_MODE=api \
    AI_HOST=0.0.0.0 \
    AI_PORT=8000

# Install runtime dependencies for OpenCV and image decoding
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-privileged user and group
RUN groupadd -g 10001 appgroup && \
    useradd -u 10001 -g appgroup -s /sbin/nologin -d /home/appuser -m appuser

# Copy installed Python packages from builder
COPY --from=builder --chown=appuser:appgroup /root/.local /home/appuser/.local

# Copy application source code
COPY --chown=appuser:appgroup app/ /app/app/
COPY --chown=appuser:appgroup datasets/ /app/datasets/
COPY --chown=appuser:appgroup artifacts/ /app/artifacts/

# Set security context and non-root execution
USER 10001:10001

EXPOSE 8000

# Health check probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8000/internal/ai/health || exit 1

# Default entry point (overridden in deployment manifests for worker mode)
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 2.2 Container Security Controls

1. **Non-Root Execution**: Runs under UID `10001:10001` (`appuser`). Root execution is explicitly forbidden.
2. **Minimal Base Image**: Built on `debian-slim` to eliminate unnecessary toolchains (`gcc`, `curl`, build libraries) in the final runtime layer.
3. **Read-Only Root Filesystem**: The container can be executed with `readOnlyRootFilesystem: true` in Kubernetes, mounting an ephemeral `tmpfs` volume at `/tmp` for transient scratch data.
4. **Dropped Linux Capabilities**: All capabilities dropped (`ALL`), with no privilege escalation permitted (`allowPrivilegeEscalation: false`).
5. **No Embedded Secrets**: Image layers contain zero API keys, service tokens, or private certificates.

---

## 3. Environment Configuration & Secret Management

All operational parameters are externally injectable via environment variables conforming to Twelve-Factor App principles.

### Environment Variable Specification

| Variable Name | Required | Default Value | Description |
| :--- | :---: | :--- | :--- |
| `AI_SERVICE_NAME` | No | `firesphere-ai-service` | Microservice identifier for logs and tracing. |
| `AI_SERVICE_ENV` | No | `production` | Deployment environment (`development`, `staging`, `production`). |
| `AI_SERVICE_MODE` | Yes | `api` | Operational mode: `api`, `worker`, or `combined`. |
| `AI_HOST` | No | `0.0.0.0` | Bind host for HTTP server. |
| `AI_PORT` | No | `8000` | Bind port for HTTP server. |
| `AI_LOG_LEVEL` | No | `INFO` | Logging verbosity (`DEBUG`, `INFO`, `WARNING`, `ERROR`). |
| `AI_SERVICE_TOKEN` | Yes | *(None)* | Secret token required in `X-AI-Service-Token` or `Authorization: Bearer <token>`. |
| `AI_QUEUE_DRIVER` | No | `redis` | Queue backend driver (`memory`, `redis`). |
| `REDIS_HOST` | Conditional | `localhost` | Redis server hostname (required if `AI_QUEUE_DRIVER=redis`). |
| `REDIS_PORT` | Conditional | `6379` | Redis server port. |
| `REDIS_PASSWORD` | Conditional | *(None)* | Redis authentication password (injected via secret). |
| `REDIS_DB` | No | `0` | Redis database index. |
| `REDIS_QUEUE_NAME` | No | `firesphere:ai:jobs` | Redis FIFO list name. |
| `AI_STORAGE_DRIVER` | No | `minio` | Evidence storage backend (`memory`, `filesystem`, `minio`, `s3`). |
| `STORAGE_ENDPOINT` | Conditional | `http://minio:9000` | S3 / MinIO API endpoint. |
| `STORAGE_ACCESS_KEY`| Conditional | *(None)* | Storage access key ID (injected via secret). |
| `STORAGE_SECRET_KEY`| Conditional | *(None)* | Storage secret access key (injected via secret). |
| `STORAGE_BUCKET_NAME`| No | `firesphere-evidence` | Evidence object storage bucket name. |
| `STORAGE_REGION` | No | `us-east-1` | S3 / MinIO region identifier. |
| `STORAGE_SECURE` | No | `false` | Use TLS (`true`) or plain HTTP (`false`) for S3 endpoint. |
| `AI_MODEL_WEIGHTS_PATH`| No | `/app/artifacts/models/latest.pt` | Path to verified PyTorch/TorchScript weights file. |
| `AI_INFERENCE_DEVICE` | No | `auto` | Target inference hardware (`auto`, `cuda`, `cpu`). |
| `AI_WORKER_CONCURRENCY`| No | `2` | Number of parallel worker threads/processes. |
| `AI_WORKER_POLL_TIMEOUT`| No | `2` | Blocking dequeue timeout in seconds for `BRPOP`. |

### Secret Injection Guidelines

* **Kubernetes**: Mount secrets via Kubernetes `Secret` objects using `envFrom` or individual `valueFrom.secretKeyRef`.
* **HashiCorp Vault / AWS Secrets Manager**: Inject secrets dynamically via CSI driver or sidecar injector into container environment variables at pod startup.
* **Prohibited Practice**: Hardcoding default passwords, tokens, or S3 credentials in `.env` files committed to Git. The service startup validator fails fast if `AI_SERVICE_ENV=production` and `AI_SERVICE_TOKEN` is unset or blank.

---

## 4. Multi-Container Orchestration (Docker Compose Staging)

For staging, integration testing, and local system verification, the following `docker-compose.yml` provides a production-identical topology:

```yaml
version: '3.8'

services:
  redis:
    image: redis:7-alpine
    container_name: firesphere-redis
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:-staging_redis_pass_change_me}"]
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD:-staging_redis_pass_change_me}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - firesphere-internal

  minio:
    image: minio/minio:RELEASE.2024-01-18T22-51-28Z
    container_name: firesphere-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${STORAGE_ACCESS_KEY:-firesphere_admin}
      MINIO_ROOT_PASSWORD: ${STORAGE_SECRET_KEY:-staging_minio_pass_change_me}
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - firesphere-internal

  ai-api:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: firesphere-ai-api
    environment:
      AI_SERVICE_MODE: "api"
      AI_SERVICE_ENV: "staging"
      AI_SERVICE_TOKEN: "${AI_SERVICE_TOKEN:-stg_service_token_firesphere_2026}"
      AI_QUEUE_DRIVER: "redis"
      REDIS_HOST: "redis"
      REDIS_PORT: "6379"
      REDIS_PASSWORD: "${REDIS_PASSWORD:-staging_redis_pass_change_me}"
    ports:
      - "8000:8000"
    depends_on:
      redis:
        condition: service_healthy
    networks:
      - firesphere-internal

  ai-worker:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: firesphere-ai-worker
    command: ["python", "-m", "app.worker"]
    environment:
      AI_SERVICE_MODE: "worker"
      AI_SERVICE_ENV: "staging"
      AI_SERVICE_TOKEN: "${AI_SERVICE_TOKEN:-stg_service_token_firesphere_2026}"
      AI_QUEUE_DRIVER: "redis"
      REDIS_HOST: "redis"
      REDIS_PORT: "6379"
      REDIS_PASSWORD: "${REDIS_PASSWORD:-staging_redis_pass_change_me}"
      AI_STORAGE_DRIVER: "minio"
      STORAGE_ENDPOINT: "http://minio:9000"
      STORAGE_ACCESS_KEY: "${STORAGE_ACCESS_KEY:-firesphere_admin}"
      STORAGE_SECRET_KEY: "${STORAGE_SECRET_KEY:-staging_minio_pass_change_me}"
      STORAGE_BUCKET_NAME: "firesphere-evidence"
      AI_INFERENCE_DEVICE: "auto"
    depends_on:
      redis:
        condition: service_healthy
      minio:
        condition: service_healthy
    networks:
      - firesphere-internal

volumes:
  redis_data:
  minio_data:

networks:
  firesphere-internal:
    driver: bridge
```

---

## 5. Kubernetes Production Manifests

### 5.1 AI API Service Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: firesphere-ai-api
  namespace: firesphere
  labels:
    app.kubernetes.io/name: firesphere-ai-api
    app.kubernetes.io/component: api
spec:
  replicas: 3
  selector:
    matchLabels:
      app.kubernetes.io/name: firesphere-ai-api
  template:
    metadata:
      labels:
        app.kubernetes.io/name: firesphere-ai-api
        app.kubernetes.io/component: api
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/metrics"
        prometheus.io/port: "8000"
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
      containers:
        - name: api
          image: registry.firesphere.ai/ai-service:0.7.0-phase7
          imagePullPolicy: IfNotPresent
          env:
            - name: AI_SERVICE_MODE
              value: "api"
            - name: AI_SERVICE_ENV
              value: "production"
            - name: AI_SERVICE_TOKEN
              valueFrom:
                secretKeyRef:
                  name: firesphere-ai-secrets
                  key: service-token
            - name: REDIS_HOST
              value: "redis-master.firesphere.svc.cluster.local"
            - name: REDIS_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: firesphere-redis-secrets
                  key: redis-password
          ports:
            - containerPort: 8000
              name: http
          resources:
            requests:
              cpu: "500m"
              memory: "512Mi"
            limits:
              cpu: "2000m"
              memory: "1536Mi"
          livenessProbe:
            httpGet:
              path: /internal/ai/health
              port: http
            initialDelaySeconds: 15
            periodSeconds: 10
            timeoutSeconds: 3
          readinessProbe:
            httpGet:
              path: /internal/ai/ready
              port: http
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
          securityContext:
            readOnlyRootFilesystem: true
            allowPrivilegeEscalation: false
            capabilities:
              drop: ["ALL"]
          volumeMounts:
            - name: tmp-dir
              mountPath: /tmp
      volumes:
        - name: tmp-dir
          emptyDir: {}
```

### 5.2 AI Worker Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: firesphere-ai-worker
  namespace: firesphere
  labels:
    app.kubernetes.io/name: firesphere-ai-worker
    app.kubernetes.io/component: worker
spec:
  replicas: 4
  selector:
    matchLabels:
      app.kubernetes.io/name: firesphere-ai-worker
  template:
    metadata:
      labels:
        app.kubernetes.io/name: firesphere-ai-worker
        app.kubernetes.io/component: worker
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
      containers:
        - name: worker
          image: registry.firesphere.ai/ai-service:0.7.0-phase7
          command: ["python", "-m", "app.worker"]
          env:
            - name: AI_SERVICE_MODE
              value: "worker"
            - name: AI_SERVICE_ENV
              value: "production"
            - name: REDIS_HOST
              value: "redis-master.firesphere.svc.cluster.local"
            - name: REDIS_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: firesphere-redis-secrets
                  key: redis-password
            - name: STORAGE_ENDPOINT
              value: "https://s3.ap-south-1.amazonaws.com"
            - name: STORAGE_ACCESS_KEY
              valueFrom:
                secretKeyRef:
                  name: firesphere-storage-secrets
                  key: access-key
            - name: STORAGE_SECRET_KEY
              valueFrom:
                secretKeyRef:
                  name: firesphere-storage-secrets
                  key: secret-key
            - name: STORAGE_BUCKET_NAME
              value: "firesphere-production-evidence"
            - name: STORAGE_SECURE
              value: "true"
            - name: AI_INFERENCE_DEVICE
              value: "cuda"
          resources:
            requests:
              cpu: "2000m"
              memory: "2048Mi"
            limits:
              cpu: "4000m"
              memory: "8192Mi"
              nvidia.com/gpu: "1"  # Optional: omit if running CPU-only nodes
          securityContext:
            readOnlyRootFilesystem: false
            allowPrivilegeEscalation: false
            capabilities:
              drop: ["ALL"]
```

---

## 6. Observability, Monitoring & Health Probes

### 6.1 Probes

* **Liveness Probe** (`/internal/ai/health`): Returns HTTP 200 `{"status": "ok"}` as long as the event loop is responsive.
* **Readiness Probe** (`/internal/ai/ready`): Evaluates subsystem connectivity (Redis queue reachable, storage provider accessible, model loader operational). Returns HTTP 503 if a mandatory dependency is unavailable.

### 6.2 Prometheus Metrics (`/metrics`)

The API exposes standard Prometheus-compatible metrics:
* `firesphere_ai_jobs_total{status="submitted|completed|failed"}`: Total job throughput counter.
* `firesphere_ai_queue_depth`: Current length of Redis job queue.
* `firesphere_ai_job_duration_seconds`: Histogram measuring end-to-end execution latency.
* `firesphere_ai_cv_preprocessing_seconds`: Histogram measuring OpenCV decoding and tensor transformation.
* `firesphere_ai_inference_seconds`: Histogram measuring PyTorch model forward pass.

### 6.3 Horizontal Pod Autoscaling (HPA)

* **API Pods**: Scale based on CPU utilization ($> 70\%$) or incoming HTTP request rate ($> 50\text{ req/s/pod}$).
* **Worker Pods**: Scale based on Prometheus custom metric `firesphere_ai_queue_depth` ($> 10\text{ jobs/worker}$).
