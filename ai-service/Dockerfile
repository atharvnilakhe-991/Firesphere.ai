# ==============================================================================
# FireSphere.ai — AI & Computer Vision Microservice Dockerfile
# Multi-stage production container with non-root security context
# ==============================================================================

# Build Stage
FROM python:3.11-slim AS builder

WORKDIR /build

RUN apt-get update && apt-get install --no-install-recommends -y \
    build-essential \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --user -r requirements.txt

# Final Production Stage
FROM python:3.11-slim AS runner

WORKDIR /app

# Install runtime system libraries for OpenCV and PyTorch
RUN apt-get update && apt-get install --no-install-recommends -y \
    libgl1 \
    libglib2.0-0 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy installed Python packages from builder stage
COPY --from=builder /root/.local /root/.local
ENV PATH=/root/.local/bin:$PATH

# Create dedicated unprivileged user
RUN groupadd -g 10001 firesphere && \
    useradd -u 10001 -g firesphere -s /bin/bash -m firesphere

# Copy application source code
COPY --chown=firesphere:firesphere . /app

USER firesphere

# Expose default HTTP service port
EXPOSE 8000

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    AI_HOST=0.0.0.0 \
    AI_PORT=8000

# Default entrypoint starts the FastAPI API server
# To run worker daemon: docker run ... firesphere-ai python -m app.jobs.worker
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
