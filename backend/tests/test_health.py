"""Tests for the health check endpoint."""

from fastapi.testclient import TestClient

from money_manager import __version__
from money_manager.app import create_app


def test_health_ok() -> None:
    """The health endpoint reports status ok and the current version."""
    client = TestClient(create_app())

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "version": __version__}
