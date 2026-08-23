"""Schemas for transaction (entry) endpoints."""

import datetime as dt
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field

from money_manager.db.models.enums import TransactionType

# Positive monetary amount with at most 2 decimal places.
Amount = Field(gt=0, max_digits=14, decimal_places=2)


class TransactionCreate(BaseModel):
    """Request body for creating an entry.

    ``date`` defaults to today when omitted. Only ``EXPENSE`` is accepted for
    now; other types are rejected in the route layer.
    """

    type: TransactionType = TransactionType.EXPENSE
    date: dt.date | None = None
    amount: Decimal = Amount
    category_id: int
    account_id: int
    note: str | None = None
    description: str | None = None


class TransactionUpdate(BaseModel):
    """Request body for updating an entry; all fields optional."""

    date: dt.date | None = None
    amount: Decimal | None = Field(default=None, gt=0, max_digits=14, decimal_places=2)
    category_id: int | None = None
    account_id: int | None = None
    note: str | None = None
    description: str | None = None


class TransactionRead(BaseModel):
    """An entry in responses."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    type: TransactionType
    date: dt.date
    amount: Decimal
    category_id: int | None
    account_id: int | None
    note: str | None
    description: str | None
    created_at: dt.datetime
    updated_at: dt.datetime
