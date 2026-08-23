"""Schemas for account and account-group endpoints."""

from pydantic import BaseModel, ConfigDict, Field


class AccountGroupCreate(BaseModel):
    """Request body for creating an account group."""

    name: str


class AccountGroupUpdate(BaseModel):
    """Request body for updating an account group."""

    name: str | None = None


class AccountCreate(BaseModel):
    """Request body for creating an account within a group."""

    name: str
    group_id: int


class AccountUpdate(BaseModel):
    """Request body for updating an account (name and/or group)."""

    name: str | None = None
    group_id: int | None = None


class AccountRead(BaseModel):
    """An account in responses."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    group_id: int


class AccountGroupRead(BaseModel):
    """An account group with its nested accounts."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    accounts: list[AccountRead] = Field(default=[])
