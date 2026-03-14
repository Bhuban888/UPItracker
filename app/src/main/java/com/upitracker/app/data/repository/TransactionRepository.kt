package com.upitracker.app.data.repository

import com.upitracker.app.data.local.AppDatabase
import com.upitracker.app.data.local.CategorySpending
import com.upitracker.app.data.model.Budget
import com.upitracker.app.data.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(private val db: AppDatabase) {
    private val txDao = db.transactionDao()
    private val budgetDao = db.budgetDao()

    fun getAllTransactions(): Flow<List<Transaction>> = txDao.getAllTransactions()
    fun getTotalCreditInRange(start: Long, end: Long): Flow<Double?> = txDao.getTotalCreditInRange(start, end)
    fun getTotalDebitInRange(start: Long, end: Long): Flow<Double?> = txDao.getTotalDebitInRange(start, end)
    fun getSpendingByCategory(start: Long, end: Long): Flow<List<CategorySpending>> = txDao.getSpendingByCategory(start, end)
    fun getBudgetsForCurrentMonth(): Flow<List<Budget>> {
        val cal = Calendar.getInstance()
        return budgetDao.getBudgetsForMonth(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
    }
    suspend fun insert(transaction: Transaction): Long = txDao.insert(transaction)
    suspend fun update(transaction: Transaction) = txDao.update(transaction)
    suspend fun delete(transaction: Transaction) = txDao.delete(transaction)
    suspend fun getByRefId(refId: String): Transaction? = txDao.getByRefId(refId)
    suspend fun upsertBudget(budget: Budget) = budgetDao.insert(budget)
    suspend fun deleteBudget(budget: Budget) = budgetDao.delete(budget)

    fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        return start to cal.timeInMillis
    }
}
