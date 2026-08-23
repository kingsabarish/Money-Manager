"""Schemas for account endpoints."""

from pydantic import BaseModel, ConfigDict, Field


class AccountCreate(BaseModel):
    """Request body for creating an account or sub-account."""

    name: str
    parent_id: int | None = None


class AccountUpdate(BaseModel):
    """Request body for updating an account (name only)."""

    name: str | None = None


class SubaccountRead(BaseModel):
    """A sub-account in responses."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    parent_id: int | None


class AccountRead(BaseModel):
    """A top-level account with its nested sub-accounts."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    parent_id: int | None
    # Reads from the ORM ``children`` attribute; serialized as ``subaccounts``.
    subaccounts: list[SubaccountRead] = Field(default=[], validation_alias="children")
