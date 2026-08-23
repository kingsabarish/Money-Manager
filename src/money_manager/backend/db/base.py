"""Declarative base shared by all ORM models."""

from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """Base class for all ORM models.

    Domain models (transactions, categories, budgets, ...) subclass this so
    that ``Base.metadata`` knows about every table for ``init_db``.
    """
