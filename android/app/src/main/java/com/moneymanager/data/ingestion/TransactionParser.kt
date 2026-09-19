package com.moneymanager.data.ingestion

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.regex.Pattern

enum class TransactionSource {
    SMS,
    GPAY_SPLIT,
}

sealed interface ParsedTransaction {
    data class Expense(
        val amount: BigDecimal,
        val merchant: String,
        val date: LocalDate,
        val accountRef: String?,
        val note: String?,
        val source: TransactionSource,
        val rawText: String,
    ) : ParsedTransaction

    data object IgnoredIncome : ParsedTransaction
    data object IgnoredNoise : ParsedTransaction
    data object Unrecognized : ParsedTransaction
}

object TransactionParser {
    // Noise patterns (OTPs, promotional spam, balance alerts without debits)
    private val OTP_PATTERN = Pattern.compile(
        """(?i)\b(otp|one\s+time\s+password|secret\s+code|verification\s+code|do\s+not\s+share)\b"""
    )
    private val PROMO_PATTERN = Pattern.compile(
        """(?i)\b(pre-approved|apply\s+now|congratulations|cashback\s+offer|loan\s+upto|special\s+offer)\b"""
    )
    private val BALANCE_ONLY_PATTERN = Pattern.compile(
        """(?i)^(?=.*\b(available\s+balance|avail\s+bal|ledger\s+balance)\b)(?!.*\b(debited|spent|paid|purchase)\b).*$"""
    )

    // Credit patterns (strictly ignored so incoming money is never logged as an expense)
    private val CREDIT_PATTERN = Pattern.compile(
        """(?i)\b(credited|credited\s+with|credited\s+to|deposited|refund\s+of|cashback\s+of|received\s+from|sent\s+you)\b"""
    )

    // Debit patterns
    private val DEBIT_INDICATOR = Pattern.compile(
        """(?i)\b(debited|debited\s+by|debited\s+with|spent|paid|transferred\s+to|purchase\s+of|charged\s+to|done\s+on)\b"""
    )

    // Amount extraction: matches currency symbols and standard Indian/Western number formats (e.g. Rs 1,499.00, INR 500, ₹ 250.50)
    private val AMOUNT_PATTERN = Pattern.compile(
        """(?i)(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)"""
    )

    // Account / Card reference extraction: matches "Card ending 1234", "Card XX1234", "A/c **5678", "Acct XX123"
    private val ACCOUNT_REF_PATTERN = Pattern.compile(
        """(?i)(?:card|a/?c|acct|account)\s*(?:no\.?|ending|ending\s+with|number)?\s*[*xX-]*(\d{3,4})"""
    )

    // Merchant extraction patterns for bank SMS
    private val MERCHANT_AT_PATTERN = Pattern.compile(
        """(?i)\b(?:at|to\s+vpa|to\s+upi|to|info:\s*upi/?)\s*([A-Za-z0-9@_&/-][A-Za-z0-9@_.\s&/-]{0,40})"""
    )
    private val MERCHANT_DELIMITER = Pattern.compile(
        """(?i)\s+(?:on|ref|upi|avail|bal|total|using|via)\b"""
    )

    // GPay patterns:
    // Format 1: "Pay <Name> ₹<Amount> for ‘<Note>’" or "Pay <Name> ₹<Amount>"
    private val GPAY_PAY_PATTERN = Pattern.compile(
        """(?i)^Pay\s+(.+?)\s+(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)(?:\s+for\s+(.+))?$"""
    )

    // Format 2: "<Name> requested ₹<Amount> for <Note>" or "<Name> requested ₹<Amount>"
    private val GPAY_REQUESTED_PATTERN = Pattern.compile(
        """(?i)^(.+?)\s+requested\s+(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)(?:\s+for\s+(.+))?$"""
    )

    // Group in title: "in ‘Food’", "in 'Goa'", "in Food"
    private val GPAY_GROUP_IN_TITLE = Pattern.compile(
        """(?i)\bin\s+['"‘’“]?([^'"‘’“”\n]+?)['"‘’”]?$"""
    )

    /**
     * Parses an incoming SMS text.
     */
    fun parseSms(
        text: String,
        timestampEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ParsedTransaction {
        val clean = text.trim()
        if (clean.isBlank()) return ParsedTransaction.Unrecognized

        // 1. Noise check (OTP / Promo / Balance inquiries)
        if (OTP_PATTERN.matcher(clean).find()) return ParsedTransaction.IgnoredNoise
        if (PROMO_PATTERN.matcher(clean).find()) return ParsedTransaction.IgnoredNoise
        if (BALANCE_ONLY_PATTERN.matcher(clean).find()) return ParsedTransaction.IgnoredNoise

        // 2. Credit check (Incoming money is strictly ignored)
        if (CREDIT_PATTERN.matcher(clean).find() && !DEBIT_INDICATOR.matcher(clean).find()) {
            return ParsedTransaction.IgnoredIncome
        }

        // 3. Must have an explicit debit indicator
        if (!DEBIT_INDICATOR.matcher(clean).find()) {
            return ParsedTransaction.Unrecognized
        }

        // 4. Extract Amount
        val amountMatcher = AMOUNT_PATTERN.matcher(clean)
        if (!amountMatcher.find()) return ParsedTransaction.Unrecognized
        val rawAmount = amountMatcher.group(1)?.replace(",", "") ?: return ParsedTransaction.Unrecognized
        val amount = try {
            BigDecimal(rawAmount)
        } catch (_: Exception) {
            return ParsedTransaction.Unrecognized
        }
        if (amount.signum() <= 0) return ParsedTransaction.Unrecognized

        // 5. Extract Account reference (e.g. last 4 digits)
        val accountMatcher = ACCOUNT_REF_PATTERN.matcher(clean)
        val accountRef = if (accountMatcher.find()) accountMatcher.group(1) else null

        // 6. Extract Merchant / Payee
        val merchantMatcher = MERCHANT_AT_PATTERN.matcher(clean)
        var merchant = if (merchantMatcher.find()) {
            var rawMerchant = merchantMatcher.group(1)?.trim() ?: ""
            val delimMatcher = MERCHANT_DELIMITER.matcher(rawMerchant)
            if (delimMatcher.find()) {
                rawMerchant = rawMerchant.substring(0, delimMatcher.start()).trim()
            }
            rawMerchant.trimEnd('.', ',', ';', ' ')
        } else {
            null
        }

        // Clean up UPI suffixes or VPA noise if needed
        merchant = cleanMerchantName(merchant) ?: "Transfer"

        val date = Instant.ofEpochMilli(timestampEpochMs).atZone(zoneId).toLocalDate()

        return ParsedTransaction.Expense(
            amount = amount,
            merchant = merchant,
            date = date,
            accountRef = accountRef,
            note = null,
            source = TransactionSource.SMS,
            rawText = clean,
        )
    }

    /**
     * Parses a Google Pay push notification (specifically split requests).
     */
    fun parseGPayNotification(
        title: String,
        text: String,
        timestampEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ParsedTransaction {
        val cleanTitle = title.trim()
        val cleanText = text.trim()
        val combined = if (cleanText.isNotBlank()) "$cleanTitle $cleanText".trim() else cleanTitle

        // If someone sent you money without a split/request indicator, reject as income
        if (CREDIT_PATTERN.matcher(combined).find() &&
            !combined.contains("split", ignoreCase = true) &&
            !cleanText.startsWith("Pay ", ignoreCase = true)
        ) {
            return ParsedTransaction.IgnoredIncome
        }

        // Extract Group name from Title if present (e.g. "New split request in ‘Food’")
        val groupMatcher = GPAY_GROUP_IN_TITLE.matcher(cleanTitle)
        val groupName = if (groupMatcher.find()) groupMatcher.group(1)?.trim() else null

        // Find match from text first (standard GPay notification body: "Pay MOHAMMED SHAFIQ ₹35.00 for ‘for egg’"),
        // then title ("Rahul requested ₹500 for Goa Dinner"), then combined text.
        val candidates = listOf(cleanText, cleanTitle, combined)
        var matchedRequester: String? = null
        var matchedAmountStr: String? = null
        var matchedNote: String? = null

        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            val payMatcher = GPAY_PAY_PATTERN.matcher(candidate)
            if (payMatcher.find()) {
                matchedRequester = payMatcher.group(1)?.trim()
                matchedAmountStr = payMatcher.group(2)?.replace(",", "")
                matchedNote = payMatcher.group(3)?.trim()
                break
            }
            val reqMatcher = GPAY_REQUESTED_PATTERN.matcher(candidate)
            if (reqMatcher.find()) {
                matchedRequester = reqMatcher.group(1)?.trim()
                matchedAmountStr = reqMatcher.group(2)?.replace(",", "")
                matchedNote = reqMatcher.group(3)?.trim()
                break
            }
        }

        if (matchedRequester != null && matchedAmountStr != null) {
            val amount = try {
                BigDecimal(matchedAmountStr)
            } catch (_: Exception) {
                return ParsedTransaction.Unrecognized
            }

            if (amount.signum() <= 0) return ParsedTransaction.Unrecognized

            val cleanedNote = cleanNote(matchedNote)
            val finalNote = when {
                groupName != null && cleanedNote != null -> {
                    if (cleanedNote.contains(groupName, ignoreCase = true)) cleanedNote
                    else "$groupName - $cleanedNote"
                }
                groupName != null -> groupName
                cleanedNote != null -> cleanedNote
                else -> null
            }

            val requester = matchedRequester.removePrefix("to ").trim()
            val date = Instant.ofEpochMilli(timestampEpochMs).atZone(zoneId).toLocalDate()

            return ParsedTransaction.Expense(
                amount = amount,
                merchant = requester,
                date = date,
                accountRef = null,
                note = finalNote,
                source = TransactionSource.GPAY_SPLIT,
                rawText = combined,
            )
        }

        return ParsedTransaction.Unrecognized
    }

    private fun cleanNote(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var cleaned = raw.trim()
        cleaned = cleaned.trim('\'', '"', '‘', '’', '“', '”', ' ')
        if (cleaned.startsWith("for ", ignoreCase = true)) {
            val rem = cleaned.substring(4).trim().trim('\'', '"', '‘', '’', '“', '”', ' ')
            if (rem.isNotBlank()) cleaned = rem
        }
        return cleaned.takeIf { it.isNotBlank() }
    }

    fun isAccountNumberOrReference(str: String): Boolean {
        val clean =
            str.trim()
                .replace(Regex("""(?i)\ba\s*/\s*c\b"""), " ")
                .replace(Regex("""(?i)^(?:a/?c|acct|acc|account|no\.?|vpa|ref|ref\s*no\.?)\s*"""), " ")
                .trim()

        if (clean.isBlank()) return true

        val base = if (clean.contains("@")) clean.substringBefore("@").trim() else clean
        val baseDigits = base.count { it.isDigit() }
        val baseLetters = base.count { it.isLetter() }

        // All digits or masking chars (e.g. 9876543210 or XX1234 or *5678)
        val nonMaskedLetters = base.count { it.isLetter() && it != 'x' && it != 'X' }
        if (nonMaskedLetters == 0 && baseDigits > 0) return true

        // Digits heavily outnumber letters (like an account number or reference ID)
        if (baseDigits >= 4 && baseDigits > baseLetters) return true

        // Mobile numbers (10+ digits)
        if (baseDigits >= 10) return true

        return false
    }

    fun isReasonableNote(note: String?): Boolean {
        if (note.isNullOrBlank()) return false
        val trimmed = note.trim()
        if (trimmed.length < 2) return false
        if (trimmed.count { it.isLetter() } < 2 && trimmed.count { it.isDigit() } == 0) return false
        if (isAccountNumberOrReference(trimmed)) return false
        if (trimmed.equals("Transfer", ignoreCase = true) ||
            trimmed.equals("Bank Transfer", ignoreCase = true) ||
            trimmed.equals("Unknown Merchant", ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    private fun cleanMerchantName(raw: String?): String? {
        if (raw == null) return null
        var name = raw.trim()

        // Normalize A/c, A/C, a/c before checking slashes
        name = name.replace(Regex("""(?i)\ba\s*/\s*c\b"""), " ")

        if (name.contains("/")) {
            name = name.substringBefore("/")
        }
        if (name.contains("@")) {
            val prefix = name.substringBefore("@").trim()
            if (prefix.length >= 2 && !prefix.all { it.isDigit() }) {
                name = prefix
            } else {
                return null
            }
        }
        name = name.replace(Regex("""(?i)\b(vpa|upi|ref|no|terminal|pos|ltd|pvt|a/?c|acct|acc|account)\b"""), " ").trim()
        name = name.trim(' ', '.', ',', ';', '-', '_')

        if (name.isBlank() || name.length < 2 || isAccountNumberOrReference(name)) {
            return null
        }

        // Must contain at least two letters to be a real merchant name
        if (name.count { it.isLetter() } < 2) {
            return null
        }

        return name
    }
}
