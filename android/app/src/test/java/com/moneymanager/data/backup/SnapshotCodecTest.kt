package com.moneymanager.data.backup

import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.local.entity.TransactionEntity
import com.moneymanager.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SnapshotCodecTest {
    private val categories =
        listOf(
            CategoryEntity(id = 1, name = "Food", parentId = null),
            CategoryEntity(id = 2, name = "Groceries", parentId = 1),
        )
    private val accounts =
        listOf(
            AccountEntity(id = 10, name = "Cash", parentId = null),
            AccountEntity(id = 11, name = "Wallet", parentId = 10),
        )
    private val transactions =
        listOf(
            TransactionEntity(
                id = 100,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 8, 23),
                amount = BigDecimal("12.34"),
                categoryId = 2,
                accountId = 11,
                note = "lunch",
            ),
        )

    @Test
    fun `entities survive a JSON round trip unchanged`() {
        val snapshot =
            SnapshotCodec.toSnapshot(
                exportedAtEpochMs = 1_700_000_000_000,
                categories = categories,
                accounts = accounts,
                transactions = transactions,
            )

        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))

        assertEquals(categories.toSet(), SnapshotCodec.categoryEntities(decoded).toSet())
        assertEquals(accounts.toSet(), SnapshotCodec.accountEntities(decoded).toSet())
        assertEquals(transactions, SnapshotCodec.transactionEntities(decoded))
    }

    @Test
    fun `amount precision is preserved as text`() {
        val tx =
            transactions[0].copy(amount = BigDecimal("1000000.05"))
        val snapshot =
            SnapshotCodec.toSnapshot(1L, categories, accounts, listOf(tx))

        val decoded = SnapshotCodec.transactionEntities(SnapshotCodec.decode(SnapshotCodec.encode(snapshot)))

        assertEquals(0, BigDecimal("1000000.05").compareTo(decoded[0].amount))
    }

    @Test
    fun `parents are ordered before children for foreign-key-safe insert`() {
        val snapshot =
            SnapshotCodec.toSnapshot(1L, categories, accounts, transactions)

        val orderedCats = SnapshotCodec.categoryEntities(snapshot)
        val orderedAccts = SnapshotCodec.accountEntities(snapshot)

        assertTrue(orderedCats.first().parentId == null)
        assertTrue(orderedAccts.first().parentId == null)
    }
}
