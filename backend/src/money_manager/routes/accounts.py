"""Account CRUD routes (two-layer: account -> sub-account)."""

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, selectinload

from money_manager.db.models.account import Account
from money_manager.db.models.transaction import Transaction
from money_manager.deps import SessionDep
from money_manager.models.account import (
    AccountCreate,
    AccountRead,
    AccountUpdate,
)

router = APIRouter(prefix="/accounts", tags=["accounts"])

_DUPLICATE = "An account with that name already exists"


def _get_or_404(session: Session, account_id: int) -> Account:
    account = session.get(Account, account_id)
    if account is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Account not found")
    return account


@router.get("", response_model=list[AccountRead])
def list_accounts(session: SessionDep) -> list[Account]:
    """List top-level accounts, each with its nested sub-accounts."""
    stmt = (
        select(Account)
        .where(Account.parent_id.is_(None))
        .options(selectinload(Account.children))
        .order_by(Account.name)
    )
    return list(session.scalars(stmt))


@router.post("", response_model=AccountRead, status_code=status.HTTP_201_CREATED)
def create_account(body: AccountCreate, session: SessionDep) -> Account:
    """Create a top-level account, or a sub-account when ``parent_id`` is set."""
    if body.parent_id is not None:
        parent = _get_or_404(session, body.parent_id)
        if parent.parent_id is not None:
            raise HTTPException(
                status.HTTP_422_UNPROCESSABLE_ENTITY,
                "Accounts are limited to two levels; parent must be top-level",
            )

    # Enforce name uniqueness within the same parent. The DB unique constraint
    # cannot cover top-level accounts because SQLite treats NULL parent_ids as
    # distinct, so check explicitly.
    parent_filter = (
        Account.parent_id.is_(None)
        if body.parent_id is None
        else Account.parent_id == body.parent_id
    )
    duplicate = session.scalar(
        select(Account.id)
        .where(parent_filter)
        .where(Account.name == body.name)
        .limit(1)
    )
    if duplicate is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, _DUPLICATE)

    account = Account(name=body.name, parent_id=body.parent_id)
    session.add(account)
    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise HTTPException(status.HTTP_409_CONFLICT, _DUPLICATE) from exc
    session.refresh(account)
    return account


@router.get("/{account_id}", response_model=AccountRead)
def get_account(account_id: int, session: SessionDep) -> Account:
    """Fetch a single account with its sub-accounts."""
    return _get_or_404(session, account_id)


@router.patch("/{account_id}", response_model=AccountRead)
def update_account(
    account_id: int,
    body: AccountUpdate,
    session: SessionDep,
) -> Account:
    """Update an account's name."""
    account = _get_or_404(session, account_id)
    if body.name is not None:
        account.name = body.name
    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise HTTPException(status.HTTP_409_CONFLICT, _DUPLICATE) from exc
    session.refresh(account)
    return account


@router.delete("/{account_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_account(account_id: int, session: SessionDep) -> None:
    """Delete an account.

    Refuses (409) if it still has sub-accounts or is used by any transaction.
    """
    account = _get_or_404(session, account_id)

    has_children = session.scalar(
        select(Account.id).where(Account.parent_id == account_id).limit(1)
    )
    if has_children is not None:
        raise HTTPException(
            status.HTTP_409_CONFLICT,
            "Delete or reassign its sub-accounts first",
        )

    in_use = session.scalar(
        select(Transaction.id).where(Transaction.account_id == account_id).limit(1)
    )
    if in_use is not None:
        raise HTTPException(
            status.HTTP_409_CONFLICT, "Account is used by one or more transactions"
        )

    session.delete(account)
    session.commit()
