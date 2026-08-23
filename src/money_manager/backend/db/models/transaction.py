"""Transaction (entry) ORM model."""

from __future__ import annotations

import datetime as dt
from decimal import Decimal

from sqlalchemy import Date, DateTime, ForeignKey, Numeric, Text
from sqlalchemy import Enum as SAEnum
from sqlalchemy.orm import Mapped, mapped_column

from money_manager.backend.db.base import Base
from money_manager.backend.db.models.enums import TransactionType


def _now() -> dt.datetime:
    """Return the current timezone-aware UTC time."""
    return dt.datetime.now(dt.UTC)


class Transaction(Base):
    """A single money entry.

    Only expense entries are created for now, but ``type`` and the nullable
    foreign keys leave room for income and transfers later.
    """

    __tablename__ = "transactions"

    id: Mapped[int] = mapped_column(primary_key=True)
    type: Mapped[TransactionType] = mapped_column(
        SAEnum(TransactionType), default=TransactionType.EXPENSE, index=True
    )
    date: Mapped[dt.date] = mapped_column(Date, index=True)
    amount: Mapped[Decimal] = mapped_column(Numeric(14, 2))
    category_id: Mapped[int | None] = mapped_column(
        ForeignKey("categories.id", ondelete="RESTRICT"), default=None
    )
    account_id: Mapped[int | None] = mapped_column(
        ForeignKey("accounts.id", ondelete="RESTRICT"), default=None
    )
    note: Mapped[str | None] = mapped_column(default=None)
    description: Mapped[str | None] = mapped_column(Text, default=None)

    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), default=_now
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), default=_now, onupdate=_now
    )
