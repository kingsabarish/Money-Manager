"""FastAPI application factory."""

from fastapi import FastAPI

from money_manager import __version__
from money_manager.backend.routes import health


def create_app() -> FastAPI:
    """Build and return the FastAPI application."""
    app = FastAPI(title="Money Manager", version=__version__)
    app.include_router(health.router)
    return app


app = create_app()
