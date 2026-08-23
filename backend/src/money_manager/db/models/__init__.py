"""ORM models.

Importing this package registers every model on ``Base.metadata`` so that
``init_db`` can create all tables.
"""

from money_manager.db.models.account import Account
from money_manager.db.models.category import Category
from money_manager.db.models.enums import TransactionType
from money_manager.db.models.transaction import Transaction

__all__ = [
    "Account",
    "Category",
    "Transaction",
    "TransactionType",
]
