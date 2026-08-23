"""Tests for account and account-group CRUD endpoints."""

from fastapi.testclient import TestClient


def test_create_group_and_account(client: TestClient) -> None:
    """An account can be created within a group and appears nested in listing."""
    cash = client.post("/account-groups", json={"name": "Cash"}).json()

    resp = client.post("/accounts", json={"name": "Wallet", "group_id": cash["id"]})
    assert resp.status_code == 201
    assert resp.json()["group_id"] == cash["id"]

    groups = client.get("/account-groups").json()
    assert groups[0]["accounts"][0]["name"] == "Wallet"


def test_account_requires_existing_group(client: TestClient) -> None:
    """Creating an account under a missing group is a 404."""
    resp = client.post("/accounts", json={"name": "Wallet", "group_id": 999})
    assert resp.status_code == 404


def test_duplicate_group_name_conflicts(client: TestClient) -> None:
    """Account group names must be unique."""
    client.post("/account-groups", json={"name": "Cash"})
    assert client.post("/account-groups", json={"name": "Cash"}).status_code == 409


def test_duplicate_account_within_group_conflicts(client: TestClient) -> None:
    """Account names must be unique within a group."""
    cash = client.post("/account-groups", json={"name": "Cash"}).json()
    client.post("/accounts", json={"name": "Wallet", "group_id": cash["id"]})
    resp = client.post("/accounts", json={"name": "Wallet", "group_id": cash["id"]})
    assert resp.status_code == 409


def test_delete_group_rejected_when_it_has_accounts(client: TestClient) -> None:
    """A group containing accounts cannot be deleted."""
    cash = client.post("/account-groups", json={"name": "Cash"}).json()
    client.post("/accounts", json={"name": "Wallet", "group_id": cash["id"]})
    assert client.delete(f"/account-groups/{cash['id']}").status_code == 409


def test_delete_account_then_group(client: TestClient) -> None:
    """An account, then its now-empty group, can be deleted."""
    cash = client.post("/account-groups", json={"name": "Cash"}).json()
    wallet = client.post(
        "/accounts", json={"name": "Wallet", "group_id": cash["id"]}
    ).json()
    assert client.delete(f"/accounts/{wallet['id']}").status_code == 204
    assert client.delete(f"/account-groups/{cash['id']}").status_code == 204
