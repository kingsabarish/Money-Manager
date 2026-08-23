"""Tests for the database engine and session setup."""

from pathlib import Path

import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from money_manager.db import create_db_engine, get_database_url, init_db


def test_default_database_url_is_local_sqlite(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Without configuration, the database URL points at local SQLite."""
    monkeypatch.delenv("MONEY_MANAGER_DATABASE_URL", raising=False)

    assert get_database_url() == "sqlite:///./data/money_manager.db"


def test_database_url_can_be_overridden(monkeypatch: pytest.MonkeyPatch) -> None:
    """The database URL is read from the environment when set."""
    monkeypatch.setenv("MONEY_MANAGER_DATABASE_URL", "sqlite:///:memory:")

    assert get_database_url() == "sqlite:///:memory:"


def test_init_db_creates_the_sqlite_file(tmp_path: Path) -> None:
    """init_db creates the database file (and its parent directory)."""
    db_path = tmp_path / "nested" / "money.db"
    engine = create_db_engine(f"sqlite:///{db_path}")

    init_db(engine)

    assert db_path.exists()


def test_session_can_execute_queries(tmp_path: Path) -> None:
    """A session opened on the engine can run queries."""
    engine = create_db_engine(f"sqlite:///{tmp_path / 'money.db'}")

    with Session(engine) as session:
        result = session.execute(text("SELECT 1")).scalar_one()

    assert result == 1
