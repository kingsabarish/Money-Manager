package com.moneymanager.data.account

import com.moneymanager.data.local.dao.AccountDao
import com.moneymanager.data.local.dao.TransactionDao
import com.moneymanager.data.local.entity.AccountEntity
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountResolutionEngine
    @Inject
    constructor(
        private val accountDao: AccountDao,
        private val transactionDao: TransactionDao,
    ) {
        /**
         * Resolves the most suitable account following a 3-tier priority hierarchy:
         * 1. Priority 1 (Highest): Direct reference match (accountRef) or explicit account name / tokens in text.
         * 2. Priority 2 (Medium): Payee / Merchant transaction history (account previously used for this merchant).
         * 3. Priority 3 (Lowest / Fallback): Semantic scoring of payment instruments (Card vs Bank vs Cash)
         *    and fallback to most frequently used account.
         */
        suspend fun resolveAccount(
            rawText: String,
            accountRef: String? = null,
            merchant: String? = null,
            amount: BigDecimal? = null,
        ): AccountEntity {
            val allAccounts = accountDao.getAll()
            if (allAccounts.isEmpty()) {
                val newId = accountDao.insert(AccountEntity(name = "Bank", parentId = null))
                return accountDao.getById(newId)!!
            }
            if (allAccounts.size == 1) {
                return allAccounts.first()
            }

            val textLower = rawText.lowercase()
            val isCard = isCardMessage(textLower)
            val isCash = isCashMessage(textLower)
            val isBank = isBankMessage(textLower) && !isCard

            // =========================================================================
            // PRIORITY 1: Explicit Account Reference or Direct Account Name / Token Match
            // =========================================================================
            // 1a. Direct accountRef match (e.g. "4884", "4006", "1234") in account name
            if (!accountRef.isNullOrBlank()) {
                val refMatch = allAccounts.firstOrNull { it.name.contains(accountRef, ignoreCase = true) }
                if (refMatch != null) return refMatch
            }

            // 1b. Direct account name match or full token overlap in message text
            val candidateMatches = mutableListOf<Pair<AccountEntity, Int>>()
            for (acc in allAccounts) {
                val cleanName = cleanAccountName(acc.name)
                val words = cleanName.split(Regex("""\s+""")).filter { it.length >= 3 }
                if (cleanName.length >= 3) {
                    val fullRegex = Regex("""\b${Regex.escape(cleanName)}\b""")
                    if (fullRegex.containsMatchIn(textLower)) {
                        candidateMatches.add(acc to (cleanName.length * 10))
                    } else if (words.size >= 2) {
                        // Check if all significant words of the account name appear in the text
                        // (e.g. "Pixel Credit Card" -> words "pixel", "credit", "card" all in "HDFC Bank Pixel Play Credit Card")
                        val matchedCount = words.count { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(textLower) }
                        if (matchedCount == words.size) {
                            candidateMatches.add(acc to (matchedCount * 25))
                        }
                    }
                }
            }

            if (candidateMatches.isNotEmpty()) {
                val bestCandidate = candidateMatches.maxByOrNull { (acc, baseScore) ->
                    var score = baseScore
                    if (isCard && isCardAccount(acc.name)) score += 100
                    if (isBank && isBankAccount(acc.name)) score += 100
                    if (isCash && isCashAccount(acc.name)) score += 100
                    score
                }
                if (bestCandidate != null) {
                    return bestCandidate.first
                }
            }

            // =========================================================================
            // PRIORITY 2: Payee / Merchant Transaction History
            // =========================================================================
            if (!merchant.isNullOrBlank()) {
                val recentAccountIds = transactionDao.getRecentAccountIdsForMerchant(merchant)
                if (recentAccountIds.isNotEmpty()) {
                    val mostFrequentId = recentAccountIds.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                    if (mostFrequentId != null) {
                        val historicalAccount = allAccounts.firstOrNull { it.id == mostFrequentId }
                        if (historicalAccount != null) {
                            val contradicts =
                                (isCard && isCashAccount(historicalAccount.name)) ||
                                    (isCash && isCardAccount(historicalAccount.name))
                            if (!contradicts) {
                                return historicalAccount
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // PRIORITY 3: Semantic Keyword & Account Type Classification
            // =========================================================================
            val scoredAccounts = allAccounts.map { acc ->
                var score = 0
                val accNameLower = acc.name.lowercase()

                if (isCard) {
                    if (isCardAccount(accNameLower)) {
                        score += 80
                        if (accNameLower.contains("pixel") && textLower.contains("pixel")) {
                            score += 40
                        }
                        for (bank in COMMON_BANKS) {
                            if (textLower.contains(bank) && accNameLower.contains(bank)) {
                                score += 30
                            }
                        }
                    } else {
                        score -= 50
                    }
                } else if (isCash) {
                    if (isCashAccount(accNameLower)) {
                        score += 80
                    }
                } else if (isBank) {
                    if (isBankAccount(accNameLower)) {
                        score += 50
                        for (bank in COMMON_BANKS) {
                            if (textLower.contains(bank) && accNameLower.contains(bank)) {
                                score += 30
                            }
                        }
                    }
                }

                acc to score
            }

            val bestScored = scoredAccounts.filter { it.second > 0 }.maxByOrNull { it.second }
            if (bestScored != null) {
                return bestScored.first
            }

            // =========================================================================
            // FALLBACK: Most used account in transaction history or first top-level
            // =========================================================================
            val mostUsedId = transactionDao.getMostUsedAccountId()
            if (mostUsedId != null) {
                val mostUsed = allAccounts.firstOrNull { it.id == mostUsedId }
                if (mostUsed != null) return mostUsed
            }

            return allAccounts.firstOrNull { it.parentId == null } ?: allAccounts.first()
        }

        fun cleanAccountName(name: String): String =
            name.lowercase().replace(Regex("""[^a-z0-9\s]"""), "").trim()

        fun isCardMessage(text: String): Boolean =
            CARD_INDICATORS.any { text.contains(it) }

        fun isBankMessage(text: String): Boolean =
            BANK_INDICATORS.any { text.contains(it) }

        fun isCashMessage(text: String): Boolean =
            CASH_INDICATORS.any { text.contains(it) }

        fun isCardAccount(name: String): Boolean {
            val lower = name.lowercase()
            return lower.contains("card") || lower.contains("credit") || lower.contains("cc") ||
                lower.contains("pixel") || lower.contains("visa") || lower.contains("mastercard") ||
                lower.contains("rupay") || lower.contains("amex") || lower.contains("platinum")
        }

        fun isBankAccount(name: String): Boolean {
            val lower = name.lowercase()
            return lower.contains("account") || lower.contains("bank") || lower.contains("savings") ||
                lower.contains("current") || lower.contains("checking") || lower.contains("salary")
        }

        fun isCashAccount(name: String): Boolean {
            val lower = name.lowercase()
            return lower.contains("cash") || lower.contains("petty") || lower.contains("wallet")
        }

        companion object {
            val CARD_INDICATORS = listOf(
                "credit card", "creditcard", "card ending", "card xx", "card no",
                "card", "pixel play", "pixel card", "visa", "mastercard",
                "rupay", "amex", "diners", "forex card"
            )

            val BANK_INDICATORS = listOf(
                "a/c", "acct", "account", "savings", "current", "checking",
                "debited from a/c", "credited to a/c", "bank", "imps", "neft", "rtgs"
            )

            val CASH_INDICATORS = listOf(
                "cash", "atm wdl", "atm withdrawal", "withdrawn from atm"
            )

            val COMMON_BANKS = listOf(
                "hdfc", "icici", "sbi", "axis", "kotak", "pnb", "bob", "canara",
                "indusind", "yes bank", "idfc", "chase", "citi", "wells fargo", "bofa"
            )
        }
    }

