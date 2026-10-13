"""Main FastAPI application entry point for FireSphere AI Service."""

from contextlib import asynccontextmanager
import time
from typing import AsyncGenerator
from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import JSONResponse, PlainTextResponse
from fastapi.exceptions import RequestValidationError
from app.api.router import api_router
from app.api.v1.health import router as health_router
from app.core.config import settings
from app.core.exceptions import (
    FireSphereAIException,
    firesphere_exception_handler,
    unhandled_exception_handler,
    validation_exception_handler,
)
from app.core.logging import logger
from app.core.metrics import metrics


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Lifespan event handler for startup and shutdown procedures."""
    logger.info(
        f"Starting {settings.service_name} | Environment={settings.service_env} | "
        f"ModelVersion={settings.model_version}"
    )
    from app.models.cv_model_adapter import CVModelAdapter
    from app.jobs.manager import job_manager

    cv_model = CVModelAdapter(
        model_name=settings.model_name,
        model_version=settings.model_version,
    )
    cv_model.load()

    # Optional Model Warmup
    if settings.model_warmup:
        logger.info("Executing synthetic model warmup forward pass...")
        import numpy as np
        dummy_img = np.zeros((settings.target_image_height, settings.target_image_width, 3), dtype=np.uint8)
        try:
            _ = cv_model.predict(dummy_img, confidence_threshold=0.99)
            logger.info("AI_MODEL_WARMUP_COMPLETED | Warmup forward pass executed successfully.")
        except Exception as e:
            logger.warning(f"Model warmup failed ({e}); continuing startup.")

    # Start in-process background worker if in combined or worker mode
    if settings.service_mode in ("worker", "combined"):
        job_manager.start_background_worker()
        logger.info(f"Started background worker thread (service_mode={settings.service_mode}).")
    else:
        logger.info(f"Running in API-only mode (service_mode={settings.service_mode}); worker disabled.")

    logger.info(f"{settings.service_name} startup completed and ready for requests.")

    yield

    logger.info(f"Shutting down {settings.service_name}...")
    if settings.service_mode in ("worker", "combined"):
        job_manager.stop_background_worker()


# Create FastAPI instance with descriptive OpenAPI documentation
app = FastAPI(
    title="FireSphere.ai - Computer Vision & Inspection AI Service",
    description=(
        "Internal AI microservice for automated fire safety inspection analysis, "
        "evidence-linked computer vision findings, and explainable report drafting.\n\n"
        "**Strict Governance Rule**: This AI service NEVER autonomously approves or rejects "
        "a Fire NOC. All findings are advisory and require authorized human Fire Officer verification."
    ),
    version=settings.model_version,
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
)


# Request timing and logging middleware
@app.middleware("http")
async def log_requests_middleware(request: Request, call_next):
    """Logs incoming HTTP request execution duration and status code."""
    start_time = time.perf_counter()
    path = request.url.path
    method = request.method

    response: Response = await call_next(request)

    duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
    logger.info(
        f"HTTP {method} {path} completed with status {response.status_code} in {duration_ms}ms"
    )
    return response


# Register centralized exception handlers
async def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    """Formats HTTPException into standard FireSphere JSON error structure."""
    if isinstance(exc.detail, dict):
        return JSONResponse(status_code=exc.status_code, content=exc.detail)
    return JSONResponse(
        status_code=exc.status_code,
        content={"status": "FAILED", "errorCode": "HTTP_ERROR", "message": str(exc.detail)},
    )

app.add_exception_handler(HTTPException, http_exception_handler)
app.add_exception_handler(FireSphereAIException, firesphere_exception_handler)
app.add_exception_handler(RequestValidationError, validation_exception_handler)
app.add_exception_handler(Exception, unhandled_exception_handler)

# Mount health endpoints at root (/health, /ready)
app.include_router(health_router, prefix="", tags=["Health"])

# Prometheus / Observability Metrics Endpoint
@app.get(
    "/metrics",
    response_class=PlainTextResponse,
    tags=["Observability"],
    summary="Prometheus Metrics",
    description="Exposes in-memory service metrics formatted for Prometheus scraping.",
)
async def get_metrics():
    """Returns application observability metrics in Prometheus text exposition format."""
    return PlainTextResponse(metrics.to_prometheus_format(), media_type="text/plain; version=0.0.4")

# Mount approved internal AI API endpoints (/internal/ai/inspection-analysis)
app.include_router(api_router, prefix=settings.api_prefix)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=(settings.service_env == "development"),
    )
