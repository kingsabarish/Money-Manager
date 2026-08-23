"""Enumerations shared across ORM models."""

from enum import StrEnum


class TransactionType(StrEnum):
    """The kind of transaction.

    Only ``EXPENSE`` is wired up end to end for now; ``INCOME`` and
    ``TRANSFER`` exist so the schema has room to grow.
    """

    EXPENSE = "expense"
    INCOME = "income"
    TRANSFER = "transfer"
