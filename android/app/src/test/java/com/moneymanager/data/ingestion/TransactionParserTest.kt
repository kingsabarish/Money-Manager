package com.moneymanager.data.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class TransactionParserTest {
    private val zone = ZoneId.of("UTC")
    private val timestamp = 1_700_000_000_000L // 2023-11-14

    @Test
    fun `parses HDFC card spent SMS`() {
        val sms = "Alert: Rs 1,499.00 spent on your HDFC Bank Card ending 1234 at RELIANCE RETAIL on 19-Sep-26. Avail Bal: Rs 45,000."
        val result = TransactionParser.parseSms(sms, timestamp, zone)

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("1499.00"), expense.amount)
        assertEquals("RELIANCE RETAIL", expense.merchant)
        assertEquals("1234", expense.accountRef)
        assertEquals(TransactionSource.SMS, expense.source)
    }

    @Test
    fun `parses SBI UPI debit SMS`() {
        val sms = "Dear SBI User, your A/c ending 5678 debited by Rs 250.50 on 19Sep26 by UPI transfer to CHAI POINT Ref no 42621."
        val result = TransactionParser.parseSms(sms, timestamp, zone)

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("250.50"), expense.amount)
        assertEquals("CHAI POINT", expense.merchant)
        assertEquals("5678", expense.accountRef)
    }

    @Test
    fun `parses ICICI UPI info SMS with VPA handle`() {
        val sms = "Dear Customer, Acct XX4321 is debited with INR 500.00 on 19-Sep-26. Info: UPI/swiggy@icici/Order. Total Avail.bal Rs 12,000."
        val result = TransactionParser.parseSms(sms, timestamp, zone)

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("500.00"), expense.amount)
        assertEquals("swiggy", expense.merchant)
        assertEquals("4321", expense.accountRef)
    }

    @Test
    fun `parses Axis bank card spent SMS`() {
        val sms = "INR 350.00 spent on Axis Bank Card no. XX9876 on 19-09-2026 at MCDONALDS."
        val result = TransactionParser.parseSms(sms, timestamp, zone)

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("350.00"), expense.amount)
        assertEquals("MCDONALDS", expense.merchant)
        assertEquals("9876", expense.accountRef)
    }

    @Test
    fun `strictly rejects incoming salary and credit SMS`() {
        val salarySms = "Dear Customer, your A/c XX1234 is credited with INR 75,000.00 on 19-Sep-26 by Salary. Bal: Rs 80,000."
        val result = TransactionParser.parseSms(salarySms, timestamp, zone)
        assertEquals(ParsedTransaction.IgnoredIncome, result)

        val refundSms = "Refund of Rs 450.00 credited to your account ending 5678."
        val result2 = TransactionParser.parseSms(refundSms, timestamp, zone)
        assertEquals(ParsedTransaction.IgnoredIncome, result2)

        val cashbackSms = "Cashback of Rs 25.00 credited for your transaction."
        val result3 = TransactionParser.parseSms(cashbackSms, timestamp, zone)
        assertEquals(ParsedTransaction.IgnoredIncome, result3)
    }

    @Test
    fun `strictly rejects OTP messages even if they mention an amount`() {
        val otpSms = "482910 is your OTP for transaction of Rs 1,499.00 at Swiggy. Do not share OTP with anyone."
        val result = TransactionParser.parseSms(otpSms, timestamp, zone)
        assertEquals(ParsedTransaction.IgnoredNoise, result)
    }

    @Test
    fun `strictly rejects promotional loan offers`() {
        val promoSms = "Congratulations! You have pre-approved loan of Rs 5,00,000. Apply now."
        val result = TransactionParser.parseSms(promoSms, timestamp, zone)
        assertEquals(ParsedTransaction.IgnoredNoise, result)
    }

    @Test
    fun `strictly rejects balance inquiries without a debit`() {
        val balSms = "Dear customer, available balance in your A/c XX1234 is Rs 14,230.50."
        val result = TransactionParser.parseSms(balSms, timestamp, zone)
        assertEquals(ParsedTransaction.IgnoredNoise, result)
    }

    @Test
    fun `parses GPay split request notification with note`() {
        val result = TransactionParser.parseGPayNotification(
            title = "Rahul requested ₹500 for Goa Dinner",
            text = "Tap to pay now",
            timestampEpochMs = timestamp,
            zoneId = zone,
        )

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("500"), expense.amount)
        assertEquals("Rahul", expense.merchant)
        assertEquals("Goa Dinner", expense.note)
        assertEquals(TransactionSource.GPAY_SPLIT, expense.source)
    }

    @Test
    fun `parses GPay split request notification without note`() {
        val result = TransactionParser.parseGPayNotification(
            title = "Alice requested ₹1,200.50",
            text = "",
            timestampEpochMs = timestamp,
            zoneId = zone,
        )

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("1200.50"), expense.amount)
        assertEquals("Alice", expense.merchant)
        assertEquals(null, expense.note)
        assertEquals(TransactionSource.GPAY_SPLIT, expense.source)
    }

    @Test
    fun `parses real-world GPay group split request notification with group in title and note in text`() {
        val result = TransactionParser.parseGPayNotification(
            title = "New split request in ‘Food’",
            text = "Pay MOHAMMED SHAFIQ ₹35.00 for ‘for egg’",
            timestampEpochMs = timestamp,
            zoneId = zone,
        )

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("35.00"), expense.amount)
        assertEquals("MOHAMMED SHAFIQ", expense.merchant)
        assertEquals("Food - egg", expense.note)
        assertEquals(TransactionSource.GPAY_SPLIT, expense.source)
    }

    @Test
    fun `parses real-world GPay group split request notification without inner note`() {
        val result = TransactionParser.parseGPayNotification(
            title = "New split request in ‘Dinner’",
            text = "Pay Alice ₹150.00",
            timestampEpochMs = timestamp,
            zoneId = zone,
        )

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("150.00"), expense.amount)
        assertEquals("Alice", expense.merchant)
        assertEquals("Dinner", expense.note)
        assertEquals(TransactionSource.GPAY_SPLIT, expense.source)
    }

    @Test
    fun `rejects GPay incoming money receipt as IgnoredIncome`() {
        val result = TransactionParser.parseGPayNotification(
            title = "Alice sent you ₹500",
            text = "Payment received",
            timestampEpochMs = timestamp,
            zoneId = zone,
        )
        assertEquals(ParsedTransaction.IgnoredIncome, result)
    }

    @Test
    fun `parses bank transfer to friend account number without polluting merchant or note`() {
        val sms = "INR 500.00 debited from A/c XX1234 on 19-Sep-26 transferred to A/c 987654321012 ref 83921"
        val result = TransactionParser.parseSms(sms, timestamp, zone)

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("500.00"), expense.amount)
        assertEquals("A/c 987654321012", expense.merchant)
        assertEquals(null, expense.note)
        assertEquals(false, TransactionParser.isReasonableNote(expense.merchant))
    }

    @Test
    fun `parses bank transfer to friend phone VPA without polluting merchant or note`() {
        val sms = "INR 200.00 debited from A/c XX1234 on 19-Sep-26 transferred to 9876543210@paytm ref 12345"
        val result = TransactionParser.parseSms(sms, timestamp, zone)

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("200.00"), expense.amount)
        assertEquals("9876543210@paytm", expense.merchant)
        assertEquals(null, expense.note)
        assertEquals(false, TransactionParser.isReasonableNote(expense.merchant))
    }

    @Test
    fun `verifies isReasonableNote strictly filters references and account numbers`() {
        assertEquals(false, TransactionParser.isReasonableNote("987654321012"))
        assertEquals(false, TransactionParser.isReasonableNote("A/c 987654321012"))
        assertEquals(false, TransactionParser.isReasonableNote("XX1234"))
        assertEquals(false, TransactionParser.isReasonableNote("ref 83921"))
        assertEquals(false, TransactionParser.isReasonableNote("Transfer"))
        assertEquals(false, TransactionParser.isReasonableNote("A"))
        assertEquals(false, TransactionParser.isReasonableNote(null))
        assertEquals(false, TransactionParser.isReasonableNote(""))

        assertEquals(true, TransactionParser.isReasonableNote("SWIGGY"))
        assertEquals(true, TransactionParser.isReasonableNote("Dinner with friends"))
        assertEquals(true, TransactionParser.isReasonableNote("Uber Ride"))
    }

    @Test
    fun `parses HDFC Pixel Play Credit Card transaction via UPI SMS`() {
        val sms = "A transaction of Rs. 350.00 was made using your HDFC Bank Pixel Play Credit Card at wl0505241a0037198@unionbank via UPI 129904356266 on 19/09/26 at 21:49. Not you? Block your Card: https://1.hdfc.bank.in/HDFCBK/s/qm2WJ0PP or SMS BLOCKPCC 4884 to 8433642286"
        val result = TransactionParser.parseSms(sms, timestamp, zone)

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("350.00"), expense.amount)
        assertEquals("wl0505241a0037198@unionbank", expense.merchant)
        assertEquals("4884", expense.accountRef)
        assertEquals(null, expense.note)
    }

    @Test
    fun `parses ICICI Bank Card SMS with after-date merchant and available limit`() {
        val sms = "INR 1,080.90 spent using ICICI Bank Card XX4006 on 10-Sep-26 on ANGAALAMMAM FUE. Avl Limit: INR 21,919.10. If not you, call 1800 2662/SMS BLOCK 4006 to 9215676766."
        val result = TransactionParser.parseSms(sms, timestamp, zone)

        assertTrue(result is ParsedTransaction.Expense)
        val expense = result as ParsedTransaction.Expense
        assertEquals(BigDecimal("1080.90"), expense.amount)
        assertEquals("ANGAALAMMAM FUE", expense.merchant)
        assertEquals("4006", expense.accountRef)
        assertEquals(null, expense.note)
    }
}

