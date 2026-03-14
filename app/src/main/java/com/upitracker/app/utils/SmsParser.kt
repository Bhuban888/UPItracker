package com.upitracker.app.utils

import com.upitracker.app.data.model.Transaction
import com.upitracker.app.data.model.TransactionType

object SmsParser {
    private val AMOUNT_PATTERNS = listOf(
        Regex("""(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)"""  , RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:INR|Rs\.)"""  , RegexOption.IGNORE_CASE)
    )
    private val REF_PATTERN = Regex("""(?:UPI Ref|Ref No|RefNo|UTR)[:\s#]*([\w\d]+)""", RegexOption.IGNORE_CASE)
    private val UPI_ID_PATTERN = Regex("""[\w.\-+]+@[\w]+""")

    fun parse(sms: String, sender: String = ""): Transaction? {
        val lower = sms.lowercase()
        if (!listOf("upi","imps","neft","debited","credited","paytm","phonepe","gpay").any { it in lower }) return null
        val amount = extractAmount(sms) ?: return null
        val isCredit = listOf("credited","received","refund","cashback").any { it in lower }
        val type = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT
        val refId = REF_PATTERN.find(sms)?.groupValues?.get(1) ?: ""
        val upiId = UPI_ID_PATTERN.find(sms)?.value ?: ""
        val bank = listOf("HDFC","SBI","ICICI","Axis","Kotak","Paytm","PhonePe").firstOrNull { it.lowercase() in lower } ?: "Bank"
        val description = if (upiId.isNotEmpty()) (if (type == TransactionType.DEBIT) "Paid to $upiId" else "Received from $upiId") else if (type == TransactionType.DEBIT) "UPI Debit" else "UPI Credit"
        return Transaction(amount = amount, type = type, description = description, upiId = upiId, refId = refId, bankName = bank, rawSms = sms, category = suggestCategory(lower))
    }

    private fun extractAmount(sms: String): Double? {
        for (p in AMOUNT_PATTERNS) { val m = p.find(sms); if (m != null) return m.groupValues[1].replace(",","").toDoubleOrNull() }
        return null
    }

    fun suggestCategory(lower: String): String = when {
        listOf("swiggy","zomato","food","restaurant").any { it in lower } -> "Food & Dining"
        listOf("uber","ola","metro","irctc","train").any { it in lower } -> "Travel"
        listOf("amazon","flipkart","myntra","shop").any { it in lower } -> "Shopping"
        listOf("netflix","spotify","prime","movie").any { it in lower } -> "Entertainment"
        listOf("electricity","water","gas","bill","recharge").any { it in lower } -> "Bills & Utilities"
        listOf("hospital","medicine","pharmacy","doctor").any { it in lower } -> "Healthcare"
        else -> "Uncategorized"
    }
}
