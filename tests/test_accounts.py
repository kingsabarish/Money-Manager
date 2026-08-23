"""Tests for account CRUD endpoints (two-layer: account -> sub-account)."""

from fastapi.testclient import TestClient


def test_create_top_level_and_subaccount(client: TestClient) -> None:
    """A top-level account can hold a nested sub-account."""
    cash = client.post("/accounts", json={"name": "Cash"}).json()
    assert cash["parent_id"] is None

    resp = client.post("/accounts", json={"name": "Wallet", "parent_id": cash["id"]})
    assert resp.status_code == 201
    assert resp.json()["parent_id"] == cash["id"]


def test_third_level_is_rejected(client: TestClient) -> None:
    """A sub-account cannot itself be given children (two-layer limit)."""
    cash = client.post("/accounts", json={"name": "Cash"}).json()
    wallet = client.post(
        "/accounts", json={"name": "Wallet", "parent_id": cash["id"]}
    ).json()

    resp = client.post("/accounts", json={"name": "Coins", "parent_id": wallet["id"]})
    assert resp.status_code == 422


def test_list_nests_subaccounts(client: TestClient) -> None:
    """Listing returns top-level accounts with their sub-accounts nested."""
    cash = client.post("/accounts", json={"name": "Cash"}).json()
    client.post("/accounts", json={"name": "Wallet", "parent_id": cash["id"]})
    client.post("/accounts", json={"name": "Piggy Bank", "parent_id": cash["id"]})

    listing = client.get("/accounts").json()
    assert len(listing) == 1
    names = {sub["name"] for sub in listing[0]["subaccounts"]}
    assert names == {"Wallet", "Piggy Bank"}


def test_duplicate_name_conflicts(client: TestClient) -> None:
    """Two top-level accounts cannot share a name."""
    client.post("/accounts", json={"name": "Cash"})
    resp = client.post("/accounts", json={"name": "Cash"})
    assert resp.status_code == 409


def test_update_account(client: TestClient) -> None:
    """An account can be renamed."""
    cash = client.post("/accounts", json={"name": "Cash"}).json()
    resp = client.patch(f"/accounts/{cash['id']}", json={"name": "Petty Cash"})
    assert resp.status_code == 200
    assert resp.json()["name"] == "Petty Cash"


def test_delete_rejected_when_it_has_children(client: TestClient) -> None:
    """An account with sub-accounts cannot be deleted."""
    cash = client.post("/accounts", json={"name": "Cash"}).json()
    client.post("/accounts", json={"name": "Wallet", "parent_id": cash["id"]})

    resp = client.delete(f"/accounts/{cash['id']}")
    assert resp.status_code == 409


def test_delete_leaf_account(client: TestClient) -> None:
    """An account with no children or transactions can be deleted."""
    cash = client.post("/accounts", json={"name": "Cash"}).json()
    assert client.delete(f"/accounts/{cash['id']}").status_code == 204
    assert client.get(f"/accounts/{cash['id']}").status_code == 404
