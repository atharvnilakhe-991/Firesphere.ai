"""Consolidated API router for FireSphere AI Service."""

from fastapi import APIRouter
from app.api.v1.health import router as health_router
from app.api.v1.inspection import router as inspection_router
from app.api.v1.report import router as report_router
from app.api.v1.jobs import router as jobs_router

# Router mounted under internal prefix: /internal/ai
api_router = APIRouter()

# Inspection analysis, report drafting, and async job endpoints mounted under /internal/ai
api_router.include_router(inspection_router, prefix="", tags=["Inspection Analysis"])
api_router.include_router(report_router, prefix="", tags=["Inspection Report Drafting"])
api_router.include_router(jobs_router, prefix="", tags=["AI Jobs"])

