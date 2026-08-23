package com.moneymanager.domain.model

/**
 * A money account, e.g. Cash, Bank. Two-layer: a top-level account has
 * [parentId] == null; a sub-account points at a top-level account via
 * [parentId]. The two-level depth limit is enforced in the repository layer.
 */
data class Account(
    val id: Long,
    val name: String,
    val parentId: Long?,
) {
    val isTopLevel: Boolean get() = parentId == null
}
