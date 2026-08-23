"""Database engine and session management.

Defaults to a file-based SQLite database so the app runs with no external
services on a headless server. Override the location with the
``MONEY_MANAGER_DATABASE_URL`` environment variable (e.g. to point at a
mounted volume inside the Docker container).
"""

import os
from collections.abc import Iterator
from pathlib import Path

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session, sessionmaker

from money_manager.backend.db.base import Base

DEFAULT_DATABASE_URL = "sqlite:///./data/money_manager.db"

_SQLITE_FILE_PREFIX = "sqlite:///"


def get_database_url() -> str:
    """Return the configured database URL, falling back to local SQLite."""
    return os.getenv("MONEY_MANAGER_DATABASE_URL", DEFAULT_DATABASE_URL)


def _connect_args(url: str) -> dict[str, object]:
    """Return driver connect args needed for the given URL.

    SQLite needs ``check_same_thread=False`` because FastAPI runs sync
    endpoints in a thread pool, so a connection may be used off the thread
    that created it.
    """
    if url.startswith("sqlite"):
        return {"check_same_thread": False}
    return {}


def _ensure_sqlite_dir(url: str) -> None:
    """Create the parent directory for a file-based SQLite database."""
    if not url.startswith(_SQLITE_FILE_PREFIX):
        return
    path = url[len(_SQLITE_FILE_PREFIX) :]
    if not path or path == ":memory:":
        return
    Path(path).parent.mkdir(parents=True, exist_ok=True)


def create_db_engine(url: str | None = None) -> Engine:
    """Create a SQLAlchemy engine for ``url`` (defaults to the configured URL)."""
    resolved = url or get_database_url()
    _ensure_sqlite_dir(resolved)
    return create_engine(resolved, connect_args=_connect_args(resolved))


engine = create_db_engine()
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def init_db(bind: Engine | None = None) -> None:
    """Create all tables registered on ``Base.metadata``.

    Safe to call on every startup: existing tables are left untouched.
    """
    Base.metadata.create_all(bind=bind or engine)


def get_session() -> Iterator[Session]:
    """Yield a database session, closing it when the request finishes.

    Intended for use as a FastAPI dependency (``Depends(get_session)``).
    """
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()
