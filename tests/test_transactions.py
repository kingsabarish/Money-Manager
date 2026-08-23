"""Tests for transaction (entry) CRUD endpoints."""

from fastapi.testclient import TestClient


def _make_category_and_account(client: TestClient) -> tuple[int, int]:
    """Create a category and an account, returning their ids."""
    food = client.post("/categories", json={"name": "Food"}).json()
    lunch = client.post(
        "/categories", json={"name": "Lunch", "parent_id": food["id"]}
    ).json()
    cash = client.post("/account-groups", json={"name": "Cash"}).json()
    wallet = client.post(
        "/accounts", json={"name": "Wallet", "group_id": cash["id"]}
    ).json()
    return lunch["id"], wallet["id"]


def test_create_expense(client: TestClient) -> None:
    """An expense entry can be created and read back."""
    category_id, account_id = _make_category_and_account(client)

    resp = client.post(
        "/transactions",
        json={
            "amount": "12.50",
            "category_id": category_id,
            "account_id": account_id,
            "date": "2026-08-23",
            "note": "Ramen",
        },
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["type"] == "expense"
    assert float(body["amount"]) == 12.50
    assert body["date"] == "2026-08-23"


def test_date_defaults_to_today(client: TestClient) -> None:
    """Omitting the date fills in a value server-side."""
    category_id, account_id = _make_category_and_account(client)
    resp = client.post(
        "/transactions",
        json={"amount": "5", "category_id": category_id, "account_id": account_id},
    )
    assert resp.status_code == 201
    assert resp.json()["date"]


def test_list_newest_first(client: TestClient) -> None:
    """Transactions are listed newest first."""
    category_id, account_id = _make_category_and_account(client)
    base = {"amount": "1", "category_id": category_id, "account_id": account_id}
    client.post("/transactions", json={**base, "date": "2026-01-01"})
    client.post("/transactions", json={**base, "date": "2026-06-01"})

    dates = [t["date"] for t in client.get("/transactions").json()]
    assert dates == ["2026-06-01", "2026-01-01"]


def test_list_filters(client: TestClient) -> None:
    """The list route filters by account, category and date range."""
    category_id, account_id = _make_category_and_account(client)
    other_group = client.post("/account-groups", json={"name": "Bank"}).json()
    other_account = client.post(
        "/accounts", json={"name": "Checking", "group_id": other_group["id"]}
    ).json()["id"]

    base = {"amount": "1", "category_id": category_id}
    client.post(
        "/transactions",
        json={**base, "account_id": account_id, "date": "2026-01-15"},
    )
    client.post(
        "/transactions",
        json={**base, "account_id": account_id, "date": "2026-06-15"},
    )
    client.post(
        "/transactions",
        json={**base, "account_id": other_account, "date": "2026-06-15"},
    )

    by_account = client.get("/transactions", params={"account_id": account_id}).json()
    assert len(by_account) == 2

    in_range = client.get(
        "/transactions",
        params={"date_from": "2026-06-01", "date_to": "2026-06-30"},
    ).json()
    assert len(in_range) == 2
    assert all(t["date"] == "2026-06-15" for t in in_range)

    by_type = client.get("/transactions", params={"type": "expense"}).json()
    assert len(by_type) == 3
    assert client.get("/transactions", params={"type": "income"}).json() == []


def test_update_and_delete(client: TestClient) -> None:
    """An entry can be updated and then deleted."""
    category_id, account_id = _make_category_and_account(client)
    created = client.post(
        "/transactions",
        json={"amount": "10", "category_id": category_id, "account_id": account_id},
    ).json()

    updated = client.patch(f"/transactions/{created['id']}", json={"note": "updated"})
    assert updated.status_code == 200
    assert updated.json()["note"] == "updated"

    assert client.delete(f"/transactions/{created['id']}").status_code == 204
    assert client.get(f"/transactions/{created['id']}").status_code == 404


def test_income_and_transfer_rejected(client: TestClient) -> None:
    """Only expense transactions are accepted for now."""
    category_id, account_id = _make_category_and_account(client)
    for kind in ("income", "transfer"):
        resp = client.post(
            "/transactions",
            json={
                "type": kind,
                "amount": "1",
                "category_id": category_id,
                "account_id": account_id,
            },
        )
        assert resp.status_code == 422


def test_missing_references_and_bad_amount(client: TestClient) -> None:
    """Missing category/account or non-positive amount are rejected."""
    category_id, account_id = _make_category_and_account(client)

    assert (
        client.post(
            "/transactions",
            json={"amount": "1", "category_id": 999, "account_id": account_id},
        ).status_code
        == 422
    )
    assert (
        client.post(
            "/transactions",
            json={"amount": "0", "category_id": category_id, "account_id": account_id},
        ).status_code
        == 422
    )
