package com.moneymanager.domain.model

/**
 * A spending/earning category. Two-layer: a top-level category has
 * [parentId] == null; a subcategory points at a top-level category via
 * [parentId]. The two-level depth limit is enforced in the repository layer.
 */
data class Category(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val type: TransactionType = TransactionType.EXPENSE,
) {
    val isTopLevel: Boolean get() = parentId == null
}
