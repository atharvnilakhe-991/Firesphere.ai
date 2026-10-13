"""Service-to-service authentication middleware and dependency."""

import hmac
from typing import Optional
from fastapi import Header, HTTPException, Security, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from app.core.config import settings
from app.core.error_codes import AIErrorCode
from app.core.logging import logger

bearer_scheme = HTTPBearer(auto_error=False)


def verify_service_token(
    x_ai_service_token: Optional[str] = Header(default=None, alias="X-AI-Service-Token"),
    bearer_auth: Optional[HTTPAuthorizationCredentials] = Security(bearer_scheme),
) -> bool:
    """Verifies service-to-service authentication token.

    Checks either 'X-AI-Service-Token' header or 'Authorization: Bearer <token>'.
    Uses constant-time comparison to prevent timing attacks.
    Never logs incoming or configured authentication tokens.
    """
    configured_token = settings.service_auth_token

    # In development or test, if no token is configured and auth is not strictly required, allow request
    if not configured_token:
        if settings.service_env == "production" or settings.auth_enabled:
            logger.error("Authentication rejected: AI_SERVICE_AUTH_TOKEN is required in production mode.")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={
                    "status": "FAILED",
                    "errorCode": AIErrorCode.UNAUTHORIZED_SERVICE.value,
                    "message": "Service authentication token is not configured on server.",
                },
            )
        # Permissive development mode with clear log notice (no secrets logged)
        return True

    # Extract incoming token
    provided_token: Optional[str] = None
    if x_ai_service_token:
        provided_token = x_ai_service_token.strip()
    elif bearer_auth and bearer_auth.credentials:
        provided_token = bearer_auth.credentials.strip()

    if not provided_token:
        logger.warning("Service authentication failed: Missing service token header.")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                "status": "FAILED",
                "errorCode": AIErrorCode.UNAUTHORIZED_SERVICE.value,
                "message": "Missing required service authentication token (X-AI-Service-Token or Bearer).",
            },
        )

    # Constant-time comparison
    is_valid = hmac.compare_digest(provided_token.encode("utf-8"), configured_token.encode("utf-8"))
    if not is_valid:
        logger.warning("Service authentication failed: Invalid service token provided.")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                "status": "FAILED",
                "errorCode": AIErrorCode.UNAUTHORIZED_SERVICE.value,
                "message": "Invalid service authentication token.",
            },
        )

    return True
