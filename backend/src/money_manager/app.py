"""FastAPI application factory."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from money_manager import __version__
from money_manager.db import init_db
from money_manager.routes import accounts, categories, health, transactions


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Create database tables on startup."""
    init_db()
    yield


def create_app() -> FastAPI:
    """Build and return the FastAPI application."""
    app = FastAPI(title="Money Manager", version=__version__, lifespan=lifespan)
    app.include_router(health.router)
    app.include_router(categories.router)
    app.include_router(accounts.router)
    app.include_router(transactions.router)
    return app


app = create_app()
