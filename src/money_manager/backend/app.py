"""FastAPI application factory."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from money_manager import __version__
from money_manager.backend.db import init_db
from money_manager.backend.routes import health


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Create database tables on startup."""
    init_db()
    yield


def create_app() -> FastAPI:
    """Build and return the FastAPI application."""
    app = FastAPI(title="Money Manager", version=__version__, lifespan=lifespan)
    app.include_router(health.router)
    return app


app = create_app()
