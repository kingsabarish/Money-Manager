"""Tests for category CRUD endpoints."""

from fastapi.testclient import TestClient


def test_create_top_level_and_subcategory(client: TestClient) -> None:
    """A top-level category can hold a nested subcategory."""
    food = client.post("/categories", json={"name": "Food"}).json()
    assert food["parent_id"] is None
    assert food["type"] == "expense"

    resp = client.post("/categories", json={"name": "Lunch", "parent_id": food["id"]})
    assert resp.status_code == 201
    assert resp.json()["parent_id"] == food["id"]


def test_third_level_is_rejected(client: TestClient) -> None:
    """A subcategory cannot itself be given children (two-layer limit)."""
    food = client.post("/categories", json={"name": "Food"}).json()
    lunch = client.post(
        "/categories", json={"name": "Lunch", "parent_id": food["id"]}
    ).json()

    resp = client.post(
        "/categories", json={"name": "Sandwich", "parent_id": lunch["id"]}
    )
    assert resp.status_code == 422


def test_list_nests_subcategories(client: TestClient) -> None:
    """Listing returns top-level categories with their subcategories nested."""
    food = client.post("/categories", json={"name": "Food"}).json()
    client.post("/categories", json={"name": "Lunch", "parent_id": food["id"]})
    client.post("/categories", json={"name": "Dinner", "parent_id": food["id"]})

    listing = client.get("/categories").json()
    assert len(listing) == 1
    names = {sub["name"] for sub in listing[0]["subcategories"]}
    assert names == {"Lunch", "Dinner"}


def test_duplicate_name_conflicts(client: TestClient) -> None:
    """Two top-level categories cannot share a name."""
    client.post("/categories", json={"name": "Food"})
    resp = client.post("/categories", json={"name": "Food"})
    assert resp.status_code == 409


def test_update_category(client: TestClient) -> None:
    """A category can be renamed."""
    food = client.post("/categories", json={"name": "Food"}).json()
    resp = client.patch(f"/categories/{food['id']}", json={"name": "Groceries"})
    assert resp.status_code == 200
    assert resp.json()["name"] == "Groceries"


def test_delete_rejected_when_it_has_children(client: TestClient) -> None:
    """A category with subcategories cannot be deleted."""
    food = client.post("/categories", json={"name": "Food"}).json()
    client.post("/categories", json={"name": "Lunch", "parent_id": food["id"]})

    resp = client.delete(f"/categories/{food['id']}")
    assert resp.status_code == 409


def test_delete_leaf_category(client: TestClient) -> None:
    """A category with no children or transactions can be deleted."""
    food = client.post("/categories", json={"name": "Food"}).json()
    assert client.delete(f"/categories/{food['id']}").status_code == 204
    assert client.get(f"/categories/{food['id']}").status_code == 404
