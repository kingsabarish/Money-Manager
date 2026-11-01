package com.moneymanager.data.account

import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.TransactionEntity
import com.moneymanager.data.repository.FakeAccountDao
import com.moneymanager.data.repository.FakeTransactionDao
import com.moneymanager.data.repository.InMemoryDb
import com.moneymanager.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AccountResolutionEngineTest {
    private lateinit var db: InMemoryDb
    private lateinit var accountDao: FakeAccountDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var engine: AccountResolutionEngine

    @Before
    fun setUp() {
        db = InMemoryDb()
        accountDao = FakeAccountDao(db)
        transactionDao = FakeTransactionDao(db)
        engine = AccountResolutionEngine(accountDao, transactionDao)
    }

    @Test
    fun `resolves account using explicit accountRef matching account name`() = runTest {
        db.accounts.value = listOf(
            AccountEntity(id = 1, name = "Accounts", parentId = null),
            AccountEntity(id = 2, name = "HDFC Card 4884", parentId = null),
            AccountEntity(id = 3, name = "Cash", parentId = null),
        )

        val sms = "A transaction of Rs. 350.00 was made using your HDFC Bank Pixel Play Credit Card... SMS BLOCKPCC 4884"
        val resolved = engine.resolveAccount(rawText = sms, accountRef = "4884")

        assertEquals(2L, resolved.id)
        assertEquals("HDFC Card 4884", resolved.name)
    }

    @Test
    fun `resolves Card account when user has Accounts, Card, Cash for HDFC Credit Card SMS`() = runTest {
        db.accounts.value = listOf(
            AccountEntity(id = 1, name = "Accounts", parentId = null),
            AccountEntity(id = 2, name = "Card", parentId = null),
            AccountEntity(id = 3, name = "Cash", parentId = null),
        )

        val sms = "A transaction of Rs. 350.00 was made using your HDFC Bank Pixel Play Credit Card at wl0505241a0037198@unionbank via UPI 129904356266 on 19/09/26 at 21:49. Not you? Block your Card: https://1.hdfc.bank.in/HDFCBK/s/qm2WJ0PP or SMS BLOCKPCC 4884 to 8433642286"
        val resolved = engine.resolveAccount(rawText = sms, accountRef = "4884")

        assertEquals(2L, resolved.id)
        assertEquals("Card", resolved.name)
    }

    @Test
    fun `resolves Card account when user has Accounts, Card, Cash for ICICI Bank Card SMS`() = runTest {
        db.accounts.value = listOf(
            AccountEntity(id = 1, name = "Accounts", parentId = null),
            AccountEntity(id = 2, name = "Card", parentId = null),
            AccountEntity(id = 3, name = "Cash", parentId = null),
        )

        val sms = "INR 1,080.90 spent using ICICI Bank Card XX4006 on 10-Sep-26 on ANGAALAMMAM FUE. Avl Limit: INR 21,919.10. If not you, call 1800 2662/SMS BLOCK 4006 to 9215676766."
        val resolved = engine.resolveAccount(rawText = sms, accountRef = "4006")

        assertEquals(2L, resolved.id)
        assertEquals("Card", resolved.name)
    }

    @Test
    fun `resolves specific account when user has custom names like Pixel Credit Card, HDFC Savings, Cash`() = runTest {
        db.accounts.value = listOf(
            AccountEntity(id = 10, name = "HDFC Savings", parentId = null),
            AccountEntity(id = 20, name = "Pixel Credit Card", parentId = null),
            AccountEntity(id = 30, name = "Petty Cash", parentId = null),
        )

        val sms = "A transaction of Rs. 350.00 was made using your HDFC Bank Pixel Play Credit Card at merchant..."
        val resolved = engine.resolveAccount(rawText = sms, accountRef = null)

        assertEquals(20L, resolved.id)
        assertEquals("Pixel Credit Card", resolved.name)
    }

    @Test
    fun `resolves bank account for debit SMS when user has Checking, Credit Card, Cash`() = runTest {
        db.accounts.value = listOf(
            AccountEntity(id = 1, name = "Checking", parentId = null),
            AccountEntity(id = 2, name = "Credit Card", parentId = null),
            AccountEntity(id = 3, name = "Cash", parentId = null),
        )

        val sms = "Rs 500.00 debited from your A/c XX1234 on 19-Sep-26 at SWIGGY"
        val resolved = engine.resolveAccount(rawText = sms, accountRef = "1234")

        assertEquals(1L, resolved.id)
        assertEquals("Checking", resolved.name)
    }

    @Test
    fun `resolves account based on payee transaction history`() = runTest {
        db.accounts.value = listOf(
            AccountEntity(id = 1, name = "Primary Bank", parentId = null),
            AccountEntity(id = 2, name = "Secondary Bank", parentId = null),
            AccountEntity(id = 3, name = "Cash", parentId = null),
        )

        // Previous transactions with "Kavin Prashad" were paid with Secondary Bank (id = 2)
        db.transactions.value = listOf(
            TransactionEntity(
                id = 101,
                type = TransactionType.EXPENSE,
                date = LocalDate.now(),
                amount = BigDecimal("50.00"),
                categoryId = 1,
                accountId = 2,
                note = "Moong dall",
                merchant = "Kavin Prashad",
                isApproved = true,
            ),
            TransactionEntity(
                id = 102,
                type = TransactionType.EXPENSE,
                date = LocalDate.now(),
                amount = BigDecimal("30.00"),
                categoryId = 1,
                accountId = 2,
                note = "Carrot",
                merchant = "Kavin Prashad",
                isApproved = true,
            ),
        )

        val text = "Pay Kavin Prashad Rs 40.00 for veggies"
        val resolved = engine.resolveAccount(rawText = text, merchant = "Kavin Prashad")

        assertEquals(2L, resolved.id)
        assertEquals("Secondary Bank", resolved.name)
    }

    @Test
    fun `falls back to most used account when no keywords or history match`() = runTest {
        db.accounts.value = listOf(
            AccountEntity(id = 1, name = "Savings", parentId = null),
            AccountEntity(id = 2, name = "Current", parentId = null),
        )

        // Current (id = 2) is used more often
        db.transactions.value = listOf(
            TransactionEntity(id = 1, type = TransactionType.EXPENSE, date = LocalDate.now(), amount = BigDecimal("10"), categoryId = 1, accountId = 2, note = null, isApproved = true),
            TransactionEntity(id = 2, type = TransactionType.EXPENSE, date = LocalDate.now(), amount = BigDecimal("20"), categoryId = 1, accountId = 2, note = null, isApproved = true),
            TransactionEntity(id = 3, type = TransactionType.EXPENSE, date = LocalDate.now(), amount = BigDecimal("30"), categoryId = 1, accountId = 1, note = null, isApproved = true),
        )

        val text = "Transfer Rs 100"
        val resolved = engine.resolveAccount(rawText = text)

        assertEquals(2L, resolved.id)
        assertEquals("Current", resolved.name)
    }
}

