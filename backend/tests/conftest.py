"""Shared pytest fixtures: an isolated in-memory database and test client."""

from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from money_manager.app import create_app
from money_manager.db.session import get_session, init_db


@pytest.fixture
def client() -> Iterator[TestClient]:
    """Yield a TestClient backed by a fresh in-memory SQLite database.

    A StaticPool keeps every connection pointed at the same in-memory database
    so tables created by ``init_db`` are visible to request handlers. The
    ``get_session`` dependency is overridden to use this engine.
    """
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    init_db(engine)
    testing_session = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)

    def override_get_session() -> Iterator[Session]:
        session = testing_session()
        try:
            yield session
        finally:
            session.close()

    app = create_app()
    app.dependency_overrides[get_session] = override_get_session
    # Not used as a context manager: the real-engine lifespan must not run.
    yield TestClient(app)
    app.dependency_overrides.clear()
    engine.dispose()
