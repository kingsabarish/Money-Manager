"""Account CRUD routes (two-layer: account group -> account)."""

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, selectinload

from money_manager.backend.db.models.account import Account, AccountGroup
from money_manager.backend.db.models.transaction import Transaction
from money_manager.backend.deps import SessionDep
from money_manager.backend.models.account import (
    AccountCreate,
    AccountGroupCreate,
    AccountGroupRead,
    AccountGroupUpdate,
    AccountRead,
    AccountUpdate,
)

router = APIRouter(tags=["accounts"])

_DUPLICATE_GROUP = "An account group with that name already exists"
_DUPLICATE_ACCOUNT = "An account with that name already exists in this group"


def _get_group_or_404(session: Session, group_id: int) -> AccountGroup:
    group = session.get(AccountGroup, group_id)
    if group is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Account group not found")
    return group


def _get_account_or_404(session: Session, account_id: int) -> Account:
    account = session.get(Account, account_id)
    if account is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Account not found")
    return account


# --- Account groups -------------------------------------------------------


@router.get("/account-groups", response_model=list[AccountGroupRead])
def list_account_groups(
    session: SessionDep,
) -> list[AccountGroup]:
    """List account groups, each with its nested accounts."""
    stmt = (
        select(AccountGroup)
        .options(selectinload(AccountGroup.accounts))
        .order_by(AccountGroup.name)
    )
    return list(session.scalars(stmt))


@router.post(
    "/account-groups",
    response_model=AccountGroupRead,
    status_code=status.HTTP_201_CREATED,
)
def create_account_group(body: AccountGroupCreate, session: SessionDep) -> AccountGroup:
    """Create an account group."""
    group = AccountGroup(name=body.name)
    session.add(group)
    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise HTTPException(status.HTTP_409_CONFLICT, _DUPLICATE_GROUP) from exc
    session.refresh(group)
    return group


@router.patch("/account-groups/{group_id}", response_model=AccountGroupRead)
def update_account_group(
    group_id: int,
    body: AccountGroupUpdate,
    session: SessionDep,
) -> AccountGroup:
    """Rename an account group."""
    group = _get_group_or_404(session, group_id)
    if body.name is not None:
        group.name = body.name
    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise HTTPException(status.HTTP_409_CONFLICT, _DUPLICATE_GROUP) from exc
    session.refresh(group)
    return group


@router.delete("/account-groups/{group_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_account_group(group_id: int, session: SessionDep) -> None:
    """Delete a group; refuses (409) if it still contains accounts."""
    group = _get_group_or_404(session, group_id)
    has_accounts = session.scalar(
        select(Account.id).where(Account.group_id == group_id).limit(1)
    )
    if has_accounts is not None:
        raise HTTPException(
            status.HTTP_409_CONFLICT, "Delete or move its accounts first"
        )
    session.delete(group)
    session.commit()


# --- Accounts -------------------------------------------------------------


@router.get("/accounts", response_model=list[AccountRead])
def list_accounts(session: SessionDep, group_id: int | None = None) -> list[Account]:
    """List accounts, optionally filtered by group."""
    stmt = select(Account).order_by(Account.name)
    if group_id is not None:
        stmt = stmt.where(Account.group_id == group_id)
    return list(session.scalars(stmt))


@router.post(
    "/accounts", response_model=AccountRead, status_code=status.HTTP_201_CREATED
)
def create_account(body: AccountCreate, session: SessionDep) -> Account:
    """Create an account within an existing group."""
    _get_group_or_404(session, body.group_id)
    account = Account(name=body.name, group_id=body.group_id)
    session.add(account)
    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise HTTPException(status.HTTP_409_CONFLICT, _DUPLICATE_ACCOUNT) from exc
    session.refresh(account)
    return account


@router.patch("/accounts/{account_id}", response_model=AccountRead)
def update_account(
    account_id: int,
    body: AccountUpdate,
    session: SessionDep,
) -> Account:
    """Update an account's name and/or move it to another group."""
    account = _get_account_or_404(session, account_id)
    if body.group_id is not None:
        _get_group_or_404(session, body.group_id)
        account.group_id = body.group_id
    if body.name is not None:
        account.name = body.name
    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise HTTPException(status.HTTP_409_CONFLICT, _DUPLICATE_ACCOUNT) from exc
    session.refresh(account)
    return account


@router.delete("/accounts/{account_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_account(account_id: int, session: SessionDep) -> None:
    """Delete an account; refuses (409) if used by any transaction."""
    account = _get_account_or_404(session, account_id)
    in_use = session.scalar(
        select(Transaction.id).where(Transaction.account_id == account_id).limit(1)
    )
    if in_use is not None:
        raise HTTPException(
            status.HTTP_409_CONFLICT, "Account is used by one or more transactions"
        )
    session.delete(account)
    session.commit()
