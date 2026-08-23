"""Schemas for the health check endpoint."""

from pydantic import BaseModel


class HealthResponse(BaseModel):
    """Response body for the health check endpoint."""

    status: str
    version: str
