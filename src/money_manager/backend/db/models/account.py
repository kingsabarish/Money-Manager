"""Account ORM models (two-layer: account group -> account)."""

from __future__ import annotations

from sqlalchemy import ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from money_manager.backend.db.base import Base


class AccountGroup(Base):
    """A group of accounts, e.g. Cash, Card, Bank."""

    __tablename__ = "account_groups"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(unique=True, index=True)

    accounts: Mapped[list[Account]] = relationship(
        back_populates="group", order_by="Account.name"
    )


class Account(Base):
    """An individual account belonging to a group."""

    __tablename__ = "accounts"
    __table_args__ = (
        UniqueConstraint("group_id", "name", name="uq_account_group_name"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(index=True)
    group_id: Mapped[int] = mapped_column(
        ForeignKey("account_groups.id", ondelete="RESTRICT")
    )

    group: Mapped[AccountGroup] = relationship(back_populates="accounts")
