"""Health check route."""

from fastapi import APIRouter

from money_manager import __version__
from money_manager.models.health import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    """Report that the service is up."""
    return HealthResponse(status="ok", version=__version__)
