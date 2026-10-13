"""Core configuration module for FireSphere AI Service."""

from typing import Optional
from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict



class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # Service identification and execution mode
    service_name: str = Field(default="firesphere-ai-service", alias="AI_SERVICE_NAME")
    service_env: str = Field(default="development", alias="AI_SERVICE_ENV")
    service_mode: str = Field(
        default="combined",
        alias="AI_SERVICE_MODE",
        description="Execution mode: 'api' (HTTP API only), 'worker' (dedicated worker loop), or 'combined' (in-process worker thread for development)",
    )
    host: str = Field(default="0.0.0.0", alias="AI_HOST")
    port: int = Field(default=8000, alias="AI_PORT")
    log_level: str = Field(default="INFO", alias="AI_LOG_LEVEL")

    # API Routing
    api_prefix: str = Field(default="/internal/ai", alias="AI_API_PREFIX")

    # Storage & Evidence Access
    storage_endpoint: Optional[str] = Field(default=None, alias="AI_STORAGE_ENDPOINT")
    storage_bucket: Optional[str] = Field(default=None, alias="AI_STORAGE_BUCKET")

    # Model and Inference configuration (Phase 3 CV Integration)
    model_name: str = Field(default="firesphere-inspection-cv", alias="AI_MODEL_NAME")
    model_version: str = Field(default="0.3.0-phase3", alias="AI_MODEL_VERSION")
    confidence_threshold: float = Field(default=0.50, alias="AI_CONFIDENCE_THRESHOLD")
    confidence_very_low: float = Field(default=0.30, alias="AI_CONFIDENCE_VERY_LOW")
    confidence_low: float = Field(default=0.50, alias="AI_CONFIDENCE_LOW")
    confidence_medium: float = Field(default=0.70, alias="AI_CONFIDENCE_MEDIUM")
    confidence_high: float = Field(default=0.85, alias="AI_CONFIDENCE_HIGH")
    nms_iou_threshold: float = Field(
        default=0.45,
        alias="AI_NMS_IOU_THRESHOLD",
        description="IoU threshold for Non-Maximum Suppression (NMS)",
    )
    model_weights_path: Optional[str] = Field(
        default=None,
        alias="AI_MODEL_WEIGHTS_PATH",
        description="Local filesystem path to trained model weights artifact (.pt, .onnx)",
    )
    inference_device: str = Field(
        default="auto",
        alias="AI_INFERENCE_DEVICE",
        description="Preferred inference device ('auto', 'cuda', 'cpu')",
    )

    # Computer Vision & Preprocessing Parameters (Phase 2)
    max_image_size_bytes: int = Field(
        default=20 * 1024 * 1024,
        alias="AI_MAX_IMAGE_SIZE_BYTES",
        description="Maximum allowed image size in bytes (default 20MB)",
    )
    target_image_width: int = Field(
        default=640,
        alias="AI_TARGET_IMAGE_WIDTH",
        description="Target model input width for letterbox resizing",
    )
    target_image_height: int = Field(
        default=640,
        alias="AI_TARGET_IMAGE_HEIGHT",
        description="Target model input height for letterbox resizing",
    )
    min_image_width: int = Field(
        default=64,
        alias="AI_MIN_IMAGE_WIDTH",
        description="Minimum acceptable image width in pixels",
    )
    min_image_height: int = Field(
        default=64,
        alias="AI_MIN_IMAGE_HEIGHT",
        description="Minimum acceptable image height in pixels",
    )
    image_color_format: str = Field(
        default="RGB",
        alias="AI_IMAGE_COLOR_FORMAT",
        description="Target color space for preprocessed arrays ('RGB' or 'BGR')",
    )
    blur_threshold: float = Field(
        default=30.0,
        alias="AI_BLUR_THRESHOLD",
        description="Laplacian variance threshold below which an image is flagged as blurry",
    )
    enable_normalization: bool = Field(
        default=True,
        alias="AI_ENABLE_NORMALIZATION",
        description="Whether to normalize pixel values from uint8 [0, 255] to float32 [0.0, 1.0]",
    )

    # Security & Execution Flags
    enable_mock_evidence_loader: bool = Field(
        default=True,
        alias="AI_ENABLE_MOCK_EVIDENCE_LOADER",
        description="Allows running pipeline validation without external S3/MinIO in dev/test",
    )

    # Phase 6 Service-to-Service Authentication
    service_auth_token: Optional[str] = Field(
        default=None,
        alias="AI_SERVICE_AUTH_TOKEN",
        description="Shared secret token for authenticating backend service requests",
    )
    auth_enabled: bool = Field(
        default=False,
        alias="AI_AUTH_ENABLED",
        description="Whether service-to-service authentication is strictly enforced",
    )

    # Phase 6 Redis & Queue Configuration
    redis_url: Optional[str] = Field(
        default=None,
        alias="REDIS_URL",
        description="Redis connection URL (e.g., redis://localhost:6379/0)",
    )

    # Phase 6 S3 / MinIO Object Storage Configuration
    s3_endpoint: Optional[str] = Field(
        default=None,
        alias="S3_ENDPOINT",
        description="S3 / MinIO storage endpoint URL (e.g., http://localhost:9000)",
    )
    s3_access_key: Optional[str] = Field(
        default=None,
        alias="S3_ACCESS_KEY",
        description="S3 / MinIO access key identifier",
    )
    s3_secret_key: Optional[str] = Field(
        default=None,
        alias="S3_SECRET_KEY",
        description="S3 / MinIO secret access key",
    )
    s3_bucket: Optional[str] = Field(
        default=None,
        alias="S3_BUCKET",
        description="S3 / MinIO bucket for inspection evidence",
    )
    s3_region: str = Field(
        default="us-east-1",
        alias="S3_REGION",
        description="S3 storage region",
    )
    s3_secure: bool = Field(
        default=False,
        alias="S3_SECURE",
        description="Whether to use TLS/SSL HTTPS for S3 communication",
    )

    # Phase 6 Timeouts (Seconds)
    job_timeout_seconds: int = Field(
        default=300,
        alias="AI_JOB_TIMEOUT_SECONDS",
        description="Maximum execution time allowed for an async AI job",
    )
    inference_timeout_seconds: int = Field(
        default=30,
        alias="AI_INFERENCE_TIMEOUT_SECONDS",
        description="Maximum execution time allowed for model inference step",
    )
    evidence_timeout_seconds: int = Field(
        default=15,
        alias="AI_EVIDENCE_TIMEOUT_SECONDS",
        description="Maximum wait time for downloading an evidence item from object storage",
    )
    http_timeout_seconds: int = Field(
        default=30,
        alias="AI_HTTP_TIMEOUT_SECONDS",
        description="HTTP request timeout for outgoing network calls",
    )

    # Phase 6 Bounded Retries & Worker Concurrency
    max_retries: int = Field(
        default=3,
        alias="AI_MAX_RETRIES",
        description="Maximum retry attempts for transiently failed AI jobs",
    )
    worker_concurrency: int = Field(
        default=2,
        alias="AI_WORKER_CONCURRENCY",
        description="Maximum concurrent AI jobs processed per worker process",
    )

    # Phase 6 Model Warmup & GPU Safety
    model_warmup: bool = Field(
        default=False,
        alias="AI_MODEL_WARMUP",
        description="Whether to execute a synthetic forward pass at service startup to warm up GPU/CPU",
    )
    gpu_fallback_to_cpu: bool = Field(
        default=True,
        alias="AI_GPU_FALLBACK_TO_CPU",
        description="Whether to fall back cleanly to CPU if CUDA GPU is requested but unavailable",
    )

    # Gemini VLM Integration Configuration (Phase 1)
    gemini_enabled: bool = Field(
        default=False,
        alias="GEMINI_ENABLED",
        description="Whether Gemini VLM provider is enabled for visual reasoning",
    )
    gemini_api_key: Optional[str] = Field(
        default=None,
        alias="GEMINI_API_KEY",
        description="Google Gemini API authentication key",
    )
    gemini_model: str = Field(
        default="gemini-2.5-flash",
        alias="GEMINI_MODEL",
        description="Configured Gemini model ID supporting image understanding and structured output",
    )
    gemini_timeout_seconds: int = Field(
        default=30,
        ge=1,
        le=300,
        alias="GEMINI_TIMEOUT_SECONDS",
        description="Timeout in seconds for Gemini API requests",
    )
    gemini_max_retries: int = Field(
        default=3,
        ge=0,
        le=10,
        alias="GEMINI_MAX_RETRIES",
        description="Maximum bounded retries for transient Gemini API errors",
    )

    @model_validator(mode="after")
    def validate_gemini_credentials(self) -> "Settings":
        """Enforces that an API key is provided when Gemini is explicitly enabled."""
        if self.gemini_enabled and not (self.gemini_api_key and self.gemini_api_key.strip()):
            raise ValueError(
                "GEMINI_ENABLED is set to true, but GEMINI_API_KEY is missing or empty. "
                "Provide a valid API key via GEMINI_API_KEY or disable Gemini."
            )
        return self

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        populate_by_name=True,
        extra="ignore",
    )


# Global singleton instance
settings = Settings()
