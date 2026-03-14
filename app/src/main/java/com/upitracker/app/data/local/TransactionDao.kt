package com.upitracker.app.data.local

import androidx.room.*
import com.upitracker.app.data.model.Budget
import com.upitracker.app.data.model.Transaction
import com.upitracker.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT' AND timestamp BETWEEN :start AND :end")
    fun getTotalCreditInRange(start: Long, end: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND timestamp BETWEEN :start AND :end")
    fun getTotalDebitInRange(start: Long, end: Long): Flow<Double?>

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'DEBIT' AND timestamp BETWEEN :start AND :end GROUP BY category")
    fun getSpendingByCategory(start: Long, end: Long): Flow<List<CategorySpending>>

    @Query("SELECT * FROM transactions WHERE refId = :refId LIMIT 1")
    suspend fun getByRefId(refId: String): Transaction?
}

data class CategorySpending(val category: String, val total: Double)

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year")
    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<Budget>>
}
