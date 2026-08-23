"""Account ORM model (two-layer: account -> sub-account)."""

from __future__ import annotations

from sqlalchemy import ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from money_manager.db.base import Base


class Account(Base):
    """A money account, e.g. Cash, Bank.

    Top-level accounts have ``parent_id is None``. A sub-account points at a
    top-level account via ``parent_id``. The two-layer depth limit is enforced
    in the route layer, not the schema.
    """

    __tablename__ = "accounts"
    __table_args__ = (
        UniqueConstraint("parent_id", "name", name="uq_account_parent_name"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(index=True)
    parent_id: Mapped[int | None] = mapped_column(
        ForeignKey("accounts.id", ondelete="RESTRICT"), default=None
    )

    parent: Mapped[Account | None] = relationship(
        back_populates="children", remote_side="Account.id"
    )
    children: Mapped[list[Account]] = relationship(
        back_populates="parent", order_by="Account.name"
    )
