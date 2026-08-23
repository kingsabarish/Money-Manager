"""Schemas for category endpoints."""

from pydantic import BaseModel, ConfigDict, Field

from money_manager.db.models.enums import TransactionType


class CategoryCreate(BaseModel):
    """Request body for creating a category or subcategory."""

    name: str
    parent_id: int | None = None
    type: TransactionType = TransactionType.EXPENSE


class CategoryUpdate(BaseModel):
    """Request body for updating a category (name/type only)."""

    name: str | None = None
    type: TransactionType | None = None


class SubcategoryRead(BaseModel):
    """A subcategory in responses."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    parent_id: int | None
    type: TransactionType


class CategoryRead(BaseModel):
    """A top-level category with its nested subcategories."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    parent_id: int | None
    type: TransactionType
    # Reads from the ORM ``children`` attribute; serialized as ``subcategories``.
    subcategories: list[SubcategoryRead] = Field(
        default=[], validation_alias="children"
    )
