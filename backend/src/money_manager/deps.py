"""Reusable FastAPI dependencies."""

from typing import Annotated

from fastapi import Depends
from sqlalchemy.orm import Session

from money_manager.db.session import get_session

SessionDep = Annotated[Session, Depends(get_session)]
"""A database session, opened per request and closed afterwards."""
