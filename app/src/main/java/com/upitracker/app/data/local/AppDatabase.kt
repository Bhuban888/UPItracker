package com.upitracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.upitracker.app.data.model.Budget
import com.upitracker.app.data.model.Transaction
import com.upitracker.app.data.model.TransactionType

class TransactionTypeConverter {
    @TypeConverter fun fromType(type: TransactionType): String = type.name
    @TypeConverter fun toType(value: String): TransactionType = TransactionType.valueOf(value)
}

@Database(entities = [Transaction::class, Budget::class], version = 1, exportSchema = false)
@TypeConverters(TransactionTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    companion object { const val DATABASE_NAME = "upi_tracker_db" }
}
