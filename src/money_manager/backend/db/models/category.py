"""Category ORM model (two-layer: category -> subcategory)."""

from __future__ import annotations

from sqlalchemy import Enum as SAEnum
from sqlalchemy import ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from money_manager.backend.db.base import Base
from money_manager.backend.db.models.enums import TransactionType


class Category(Base):
    """A spending/earning category.

    Top-level categories have ``parent_id is None``. A subcategory points at a
    top-level category via ``parent_id``. The two-layer depth limit is enforced
    in the route layer, not the schema.
    """

    __tablename__ = "categories"
    __table_args__ = (
        UniqueConstraint("parent_id", "name", name="uq_category_parent_name"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(index=True)
    parent_id: Mapped[int | None] = mapped_column(
        ForeignKey("categories.id", ondelete="RESTRICT"), default=None
    )
    type: Mapped[TransactionType] = mapped_column(
        SAEnum(TransactionType), default=TransactionType.EXPENSE
    )

    parent: Mapped[Category | None] = relationship(
        back_populates="children", remote_side="Category.id"
    )
    children: Mapped[list[Category]] = relationship(
        back_populates="parent", order_by="Category.name"
    )
