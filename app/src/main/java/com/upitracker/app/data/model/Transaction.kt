package com.upitracker.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType { CREDIT, DEBIT }

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val description: String,
    val upiId: String = "",
    val refId: String = "",
    val bankName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val rawSms: String = "",
    val category: String = "Uncategorized",
    val note: String = ""
)
