"""Transaction (entry) CRUD routes.

Only expense entries are supported for now; income and transfer types are
rejected until they are built out.
"""

from datetime import date as date_cls

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from money_manager.db.models.account import Account
from money_manager.db.models.category import Category
from money_manager.db.models.enums import TransactionType
from money_manager.db.models.transaction import Transaction
from money_manager.deps import SessionDep
from money_manager.models.transaction import (
    TransactionCreate,
    TransactionRead,
    TransactionUpdate,
)

router = APIRouter(prefix="/transactions", tags=["transactions"])


def _get_or_404(session: Session, transaction_id: int) -> Transaction:
    transaction = session.get(Transaction, transaction_id)
    if transaction is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Transaction not found")
    return transaction


def _validate_category(session: Session, category_id: int) -> None:
    if session.get(Category, category_id) is None:
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_ENTITY,
            f"Category {category_id} does not exist",
        )


def _validate_account(session: Session, account_id: int) -> None:
    if session.get(Account, account_id) is None:
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_ENTITY,
            f"Account {account_id} does not exist",
        )


@router.get("", response_model=list[TransactionRead])
def list_transactions(
    session: SessionDep,
    type: TransactionType | None = None,
    category_id: int | None = None,
    account_id: int | None = None,
    date_from: date_cls | None = None,
    date_to: date_cls | None = None,
) -> list[Transaction]:
    """List entries, newest first, optionally filtered.

    Filters combine with AND: ``type``, ``category_id``, ``account_id`` and an
    inclusive ``date_from``/``date_to`` range.
    """
    stmt = select(Transaction)
    if type is not None:
        stmt = stmt.where(Transaction.type == type)
    if category_id is not None:
        stmt = stmt.where(Transaction.category_id == category_id)
    if account_id is not None:
        stmt = stmt.where(Transaction.account_id == account_id)
    if date_from is not None:
        stmt = stmt.where(Transaction.date >= date_from)
    if date_to is not None:
        stmt = stmt.where(Transaction.date <= date_to)
    stmt = stmt.order_by(Transaction.date.desc(), Transaction.id.desc())
    return list(session.scalars(stmt))


@router.post("", response_model=TransactionRead, status_code=status.HTTP_201_CREATED)
def create_transaction(body: TransactionCreate, session: SessionDep) -> Transaction:
    """Create an expense entry."""
    if body.type is not TransactionType.EXPENSE:
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_ENTITY,
            "Only expense transactions are supported for now",
        )
    _validate_category(session, body.category_id)
    _validate_account(session, body.account_id)

    transaction = Transaction(
        type=body.type,
        date=body.date or date_cls.today(),
        amount=body.amount,
        category_id=body.category_id,
        account_id=body.account_id,
        note=body.note,
        description=body.description,
    )
    session.add(transaction)
    session.commit()
    session.refresh(transaction)
    return transaction


@router.get("/{transaction_id}", response_model=TransactionRead)
def get_transaction(transaction_id: int, session: SessionDep) -> Transaction:
    """Fetch a single entry."""
    return _get_or_404(session, transaction_id)


@router.patch("/{transaction_id}", response_model=TransactionRead)
def update_transaction(
    transaction_id: int,
    body: TransactionUpdate,
    session: SessionDep,
) -> Transaction:
    """Update fields on an entry."""
    transaction = _get_or_404(session, transaction_id)

    if body.category_id is not None:
        _validate_category(session, body.category_id)
        transaction.category_id = body.category_id
    if body.account_id is not None:
        _validate_account(session, body.account_id)
        transaction.account_id = body.account_id
    if body.date is not None:
        transaction.date = body.date
    if body.amount is not None:
        transaction.amount = body.amount
    if body.note is not None:
        transaction.note = body.note
    if body.description is not None:
        transaction.description = body.description

    session.commit()
    session.refresh(transaction)
    return transaction


@router.delete("/{transaction_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_transaction(transaction_id: int, session: SessionDep) -> None:
    """Delete an entry."""
    transaction = _get_or_404(session, transaction_id)
    session.delete(transaction)
    session.commit()
