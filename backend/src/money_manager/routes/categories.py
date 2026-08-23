"""Category CRUD routes (two-layer: category -> subcategory)."""

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, selectinload

from money_manager.db.models.category import Category
from money_manager.db.models.transaction import Transaction
from money_manager.deps import SessionDep
from money_manager.models.category import (
    CategoryCreate,
    CategoryRead,
    CategoryUpdate,
)

router = APIRouter(prefix="/categories", tags=["categories"])


def _get_or_404(session: Session, category_id: int) -> Category:
    category = session.get(Category, category_id)
    if category is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Category not found")
    return category


@router.get("", response_model=list[CategoryRead])
def list_categories(session: SessionDep) -> list[Category]:
    """List top-level categories, each with its nested subcategories."""
    stmt = (
        select(Category)
        .where(Category.parent_id.is_(None))
        .options(selectinload(Category.children))
        .order_by(Category.name)
    )
    return list(session.scalars(stmt))


@router.post("", response_model=CategoryRead, status_code=status.HTTP_201_CREATED)
def create_category(body: CategoryCreate, session: SessionDep) -> Category:
    """Create a top-level category, or a subcategory when ``parent_id`` is set."""
    if body.parent_id is not None:
        parent = _get_or_404(session, body.parent_id)
        if parent.parent_id is not None:
            raise HTTPException(
                status.HTTP_422_UNPROCESSABLE_ENTITY,
                "Categories are limited to two levels; parent must be top-level",
            )

    # Enforce name uniqueness within the same parent. The DB unique constraint
    # cannot cover top-level categories because SQLite treats NULL parent_ids
    # as distinct, so check explicitly.
    parent_filter = (
        Category.parent_id.is_(None)
        if body.parent_id is None
        else Category.parent_id == body.parent_id
    )
    duplicate = session.scalar(
        select(Category.id)
        .where(parent_filter)
        .where(Category.name == body.name)
        .limit(1)
    )
    if duplicate is not None:
        raise HTTPException(
            status.HTTP_409_CONFLICT, "A category with that name already exists"
        )

    category = Category(name=body.name, parent_id=body.parent_id, type=body.type)
    session.add(category)
    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise HTTPException(
            status.HTTP_409_CONFLICT, "A category with that name already exists"
        ) from exc
    session.refresh(category)
    return category


@router.get("/{category_id}", response_model=CategoryRead)
def get_category(category_id: int, session: SessionDep) -> Category:
    """Fetch a single category with its subcategories."""
    return _get_or_404(session, category_id)


@router.patch("/{category_id}", response_model=CategoryRead)
def update_category(
    category_id: int,
    body: CategoryUpdate,
    session: SessionDep,
) -> Category:
    """Update a category's name and/or type."""
    category = _get_or_404(session, category_id)
    if body.name is not None:
        category.name = body.name
    if body.type is not None:
        category.type = body.type
    try:
        session.commit()
    except IntegrityError as exc:
        session.rollback()
        raise HTTPException(
            status.HTTP_409_CONFLICT, "A category with that name already exists"
        ) from exc
    session.refresh(category)
    return category


@router.delete("/{category_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_category(category_id: int, session: SessionDep) -> None:
    """Delete a category.

    Refuses (409) if it still has subcategories or is used by any transaction.
    """
    category = _get_or_404(session, category_id)

    has_children = session.scalar(
        select(Category.id).where(Category.parent_id == category_id).limit(1)
    )
    if has_children is not None:
        raise HTTPException(
            status.HTTP_409_CONFLICT,
            "Delete or reassign its subcategories first",
        )

    in_use = session.scalar(
        select(Transaction.id).where(Transaction.category_id == category_id).limit(1)
    )
    if in_use is not None:
        raise HTTPException(
            status.HTTP_409_CONFLICT, "Category is used by one or more transactions"
        )

    session.delete(category)
    session.commit()
