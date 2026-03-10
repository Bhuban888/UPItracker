package com.upitracker

// ============================================================
// ALL IMPORTS
// ============================================================
import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import androidx.work.*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

// ============================================================
// THEME & COLORS
// ============================================================
val Primary        = Color(0xFF00C853)
val Secondary      = Color(0xFF1DB954)
val Background     = Color(0xFF0A0A0A)
val Surface        = Color(0xFF121212)
val SurfaceVariant = Color(0xFF1E1E1E)
val OnBackground   = Color(0xFFFFFFFF)
val OnSurface      = Color(0xFFE0E0E0)
val TextSecondary  = Color(0xFF9E9E9E)
val DividerColor   = Color(0xFF2A2A2A)
val ErrorColor     = Color(0xFFFF5252)
val IncomeColor    = Color(0xFF00C853)
val ExpenseColor   = Color(0xFFFF5252)
val TransferColor  = Color(0xFF448AFF)
val ChartColors    = listOf(
    Color(0xFF00C853), Color(0xFF448AFF), Color(0xFFFF6D00),
    Color(0xFFAA00FF), Color(0xFFFFD600), Color(0xFF00B0FF), Color(0xFFFF4081)
)

@Composable
fun UPITrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Primary, secondary = Secondary,
            background = Background, surface = Surface,
            onPrimary = Color.Black, onBackground = OnBackground,
            onSurface = OnSurface, error = ErrorColor,
            surfaceVariant = SurfaceVariant
        ),
        content = content
    )
}

// ============================================================
// DATA MODELS
// ============================================================
enum class TransactionType { DEBIT, CREDIT, TRANSFER }

enum class TransactionCategory(val label: String, val emoji: String) {
    FOOD("Food & Dining", "🍔"), SHOPPING("Shopping", "🛍️"),
    TRANSPORT("Transport", "🚗"), UTILITIES("Bills & Utilities", "💡"),
    HEALTH("Health", "🏥"), ENTERTAINMENT("Entertainment", "🎬"),
    EDUCATION("Education", "📚"), TRAVEL("Travel", "✈️"),
    RECHARGE("Recharge", "📱"), GROCERIES("Groceries", "🛒"),
    TRANSFER("Transfer", "🔄"), INVESTMENT("Investment", "📈"),
    SALARY("Salary", "💼"), RENT("Rent", "🏠"), OTHER("Other", "💰")
}

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val category: TransactionCategory = TransactionCategory.OTHER,
    val merchant: String = "",
    val description: String = "",
    val bankName: String = "",
    val accountLast4: String = "",
    val upiId: String = "",
    val referenceNumber: String = "",
    val balance: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val rawSms: String = "",
    val isManual: Boolean = false,
    val userId: String = ""
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: TransactionCategory,
    val monthlyLimit: Double,
    val month: Int,
    val year: Int,
    val userId: String = ""
)

data class CategorySpend(
    val category: TransactionCategory,
    val amount: Double,
    val percentage: Float,
    val count: Int
)

// ============================================================
// ROOM DATABASE
// ============================================================
class Converters {
    @TypeConverter fun fromTransactionType(v: TransactionType) = v.name
    @TypeConverter fun toTransactionType(v: String) = TransactionType.valueOf(v)
    @TypeConverter fun fromCategory(v: TransactionCategory) = v.name
    @TypeConverter fun toCategory(v: String) = TransactionCategory.valueOf(v)
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(t: Transaction): Long
    @Update suspend fun update(t: Transaction)
    @Delete suspend fun delete(t: Transaction)
    @Query("SELECT * FROM transactions WHERE userId=:uid ORDER BY timestamp DESC")
    fun getAll(uid: String): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions WHERE userId=:uid ORDER BY timestamp DESC LIMIT 20")
    fun getRecent(uid: String): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions WHERE userId=:uid AND strftime('%m',datetime(timestamp/1000,'unixepoch'))=:m AND strftime('%Y',datetime(timestamp/1000,'unixepoch'))=:y ORDER BY timestamp DESC")
    fun getByMonth(uid: String, m: String, y: String): Flow<List<Transaction>>
    @Query("SELECT SUM(amount) FROM transactions WHERE userId=:uid AND type='DEBIT' AND strftime('%m-%Y',datetime(timestamp/1000,'unixepoch'))=:my")
    fun totalExpense(uid: String, my: String): Flow<Double?>
    @Query("SELECT SUM(amount) FROM transactions WHERE userId=:uid AND type='CREDIT' AND strftime('%m-%Y',datetime(timestamp/1000,'unixepoch'))=:my")
    fun totalIncome(uid: String, my: String): Flow<Double?>
    @Query("SELECT * FROM transactions WHERE userId=:uid AND referenceNumber=:ref LIMIT 1")
    suspend fun getByRef(uid: String, ref: String): Transaction?
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(b: Budget)
    @Delete suspend fun delete(b: Budget)
    @Query("SELECT * FROM budgets WHERE userId=:uid AND month=:m AND year=:y")
    fun getForMonth(uid: String, m: Int, y: Int): Flow<List<Budget>>
}

@Database(entities = [Transaction::class, Budget::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
}

// ============================================================
// SMS PARSER
// ============================================================
object SMSParser {
    private val amountPatterns = listOf(
        Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:Rs\\.?|INR|₹)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:debited|credited|paid|received)\\s+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
    )
    private val debitKw = listOf("debited","debit","paid","payment","sent","withdrawn","purchase","charged","deducted")
    private val creditKw = listOf("credited","credit","received","refund","cashback","deposited","salary","reversed")
    private val bankMap = mapOf(
        "SBI" to listOf("SBIINB","SBIPSG","SBIUPI","SBIBNK"),
        "HDFC" to listOf("HDFCBK","HDFCBN","HDFCRD","HDFC"),
        "ICICI" to listOf("ICICIB","ICICRD","ICICI"),
        "AXIS" to listOf("AXISBK","AXISBN","AXIS"),
        "KOTAK" to listOf("KOTAKB","KOTAK"),
        "PNB" to listOf("PNBSMS","PNB"),
        "PAYTM" to listOf("PAYTMB","PYTMBA","PAYTM"),
        "GPAY" to listOf("GPAY","GOOGPAY"),
        "PHONEPE" to listOf("PHONEPE","PHNPE"),
        "YESBANK" to listOf("YESBNK","YESBK"),
        "INDUSIND" to listOf("INDBNK","INDUSB"),
        "FEDERAL" to listOf("FEDBNK"),
        "IDFC" to listOf("IDFCFB","IDFC"),
        "BOB" to listOf("BOBSMS"),
        "CANARA" to listOf("CANBNK")
    )
    private val catKw = mapOf(
        TransactionCategory.FOOD to listOf("zomato","swiggy","restaurant","cafe","food","pizza","burger","dominos","mcdonalds","kfc"),
        TransactionCategory.GROCERIES to listOf("bigbasket","grofers","blinkit","grocery","dmart","reliance fresh"),
        TransactionCategory.SHOPPING to listOf("amazon","flipkart","myntra","meesho","snapdeal","nykaa","ajio"),
        TransactionCategory.TRANSPORT to listOf("uber","ola","rapido","metro","irctc","petrol","fuel","parking"),
        TransactionCategory.UTILITIES to listOf("electricity","water","gas","bescom","tata power","msedcl","bill payment"),
        TransactionCategory.ENTERTAINMENT to listOf("netflix","hotstar","prime","spotify","bookmyshow","pvr","inox"),
        TransactionCategory.HEALTH to listOf("pharmeasy","1mg","netmeds","hospital","clinic","doctor","pharmacy"),
        TransactionCategory.EDUCATION to listOf("udemy","coursera","byju","unacademy","school","college","fees","tuition"),
        TransactionCategory.TRAVEL to listOf("makemytrip","goibibo","cleartrip","flight","oyo","airbnb"),
        TransactionCategory.RECHARGE to listOf("recharge","airtel","jio","vodafone","bsnl","broadband"),
        TransactionCategory.SALARY to listOf("salary","payroll","stipend","wages"),
        TransactionCategory.INVESTMENT to listOf("mutual fund","zerodha","groww","upstox","sip","investment","lic"),
        TransactionCategory.RENT to listOf("rent","maintenance","society","landlord","pg","hostel")
    )

    fun parse(body: String, sender: String, userId: String): Transaction? {
        val amount = amountPatterns.firstNotNullOfOrNull {
            val m = it.matcher(body); if (m.find()) m.group(1)?.replace(",","")?.toDoubleOrNull() else null
        } ?: return null
        val lower = body.lowercase()
        val debitScore = debitKw.count { lower.contains(it) }
        val creditScore = creditKw.count { lower.contains(it) }
        val type = if (creditScore > debitScore) TransactionType.CREDIT else TransactionType.DEBIT
        val merchant = listOf(
            Pattern.compile("(?:to|at|from)\\s+([A-Z][A-Za-z0-9\\s&.'-]{2,25})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("UPI-([A-Za-z0-9@._-]+)", Pattern.CASE_INSENSITIVE)
        ).firstNotNullOfOrNull { val m = it.matcher(body); if (m.find()) m.group(1)?.trim() else null } ?: ""
        val bank = bankMap.entries.firstOrNull { (_, ids) -> ids.any { sender.uppercase().contains(it) } }?.key ?: sender.take(10)
        val ref = Pattern.compile("(?:ref|txn id|utr|rrn)\\s*[:#]?\\s*([A-Z0-9]{8,20})", Pattern.CASE_INSENSITIVE).let {
            val m = it.matcher(body); if (m.find()) m.group(1) ?: "" else ""
        }
        val text = "$lower $merchant"
        val category = catKw.entries.firstOrNull { (_, kws) -> kws.any { text.contains(it) } }?.key
            ?: if (type == TransactionType.TRANSFER) TransactionCategory.TRANSFER else TransactionCategory.OTHER
        return Transaction(amount=amount, type=type, category=category, merchant=merchant,
            description=body.take(200), bankName=bank, referenceNumber=ref, rawSms=body, userId=userId)
    }

    fun isFinancialSMS(body: String, sender: String): Boolean {
        val lower = body.lowercase()
        val hasAmount = amountPatterns.any { it.matcher(body).find() }
        val hasKw = (debitKw + creditKw).any { lower.contains(it) }
        val isBank = bankMap.values.flatten().any { sender.uppercase().contains(it) }
        return hasAmount && (hasKw || isBank)
    }
}

// ============================================================
// SMS BROADCAST RECEIVER
// ============================================================
class SMSBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "upi_tracker.db").build()
        Telephony.Sms.Intents.getMessagesFromIntent(intent)?.forEach { sms ->
            val sender = sms.originatingAddress ?: return@forEach
            val body = sms.messageBody ?: return@forEach
            if (SMSParser.isFinancialSMS(body, sender)) {
                CoroutineScope(Dispatchers.IO).launch {
                    SMSParser.parse(body, sender, uid)?.let { db.txDao().insert(it) }
                }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) SMSSyncWorker.schedule(context)
    }
}

// ============================================================
// SMS SYNC WORKER (background historical import)
// ============================================================
class SMSSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "upi_tracker.db").build()
        val dao = db.txDao()
        val uri = Uri.parse("content://sms/inbox")
        val since = System.currentTimeMillis() - 6L * 30 * 24 * 60 * 60 * 1000
        applicationContext.contentResolver.query(
            uri, arrayOf("address","body","date"), "date>?", arrayOf(since.toString()), "date DESC"
        )?.use { cursor ->
            val aIdx = cursor.getColumnIndex("address")
            val bIdx = cursor.getColumnIndex("body")
            while (cursor.moveToNext()) {
                val sender = cursor.getString(aIdx) ?: continue
                val body = cursor.getString(bIdx) ?: continue
                if (SMSParser.isFinancialSMS(body, sender)) {
                    SMSParser.parse(body, sender, uid)?.let { t ->
                        if (t.referenceNumber.isEmpty() || dao.getByRef(uid, t.referenceNumber) == null)
                            dao.insert(t)
                    }
                }
            }
        }
        return Result.success()
    }
    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "sms_sync", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SMSSyncWorker>().build()
            )
        }
    }
}

// ============================================================
// REPOSITORY
// ============================================================
class Repository(private val txDao: TransactionDao, private val budgetDao: BudgetDao) {
    private val auth = FirebaseAuth.getInstance()
    private val uid get() = auth.currentUser?.uid ?: ""
    fun getAll() = txDao.getAll(uid)
    fun getRecent() = txDao.getRecent(uid)
    fun getByMonth(m: String, y: String) = txDao.getByMonth(uid, m, y)
    fun totalExpense(my: String) = txDao.totalExpense(uid, my)
    fun totalIncome(my: String) = txDao.totalIncome(uid, my)
    fun budgetsForMonth(m: Int, y: Int) = budgetDao.getForMonth(uid, m, y)
    suspend fun add(t: Transaction) = txDao.insert(t.copy(userId = uid))
    suspend fun delete(t: Transaction) = txDao.delete(t)
    suspend fun saveBudget(b: Budget) = budgetDao.upsert(b.copy(userId = uid))
}

// ============================================================
// VIEWMODEL
// ============================================================
data class HomeState(
    val recentTxns: List<Transaction> = emptyList(),
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val categorySpends: List<CategorySpend> = emptyList(),
    val loading: Boolean = true
)

class MainViewModel(private val repo: Repository) : ViewModel() {
    private val _home = MutableStateFlow(HomeState())
    val home = _home.asStateFlow()
    private val _allTxns = MutableStateFlow<List<Transaction>>(emptyList())
    val allTxns = _allTxns.asStateFlow()
    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets = _budgets.asStateFlow()
    private val _monthlyTxns = MutableStateFlow<List<Transaction>>(emptyList())
    val monthlyTxns = _monthlyTxns.asStateFlow()
    val snack = MutableSharedFlow<String>()

    init { load() }

    private fun load() {
        val cal = Calendar.getInstance()
        val m = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val y = cal.get(Calendar.YEAR).toString()
        val my = "$m-$y"
        viewModelScope.launch {
            combine(repo.getByMonth(m, y), repo.totalExpense(my), repo.totalIncome(my)) { txns, exp, inc ->
                _monthlyTxns.value = txns
                HomeState(recentTxns = txns.take(20), income = inc ?: 0.0, expense = exp ?: 0.0,
                    categorySpends = calcCategorySpends(txns), loading = false)
            }.collect { _home.value = it }
        }
        viewModelScope.launch { repo.getAll().collect { _allTxns.value = it } }
        viewModelScope.launch {
            val cal2 = Calendar.getInstance()
            repo.budgetsForMonth(cal2.get(Calendar.MONTH)+1, cal2.get(Calendar.YEAR)).collect { _budgets.value = it }
        }
    }

    fun addTransaction(amount: Double, type: TransactionType, category: TransactionCategory, merchant: String, desc: String) {
        viewModelScope.launch {
            repo.add(Transaction(amount=amount, type=type, category=category, merchant=merchant, description=desc, isManual=true))
            snack.emit("Transaction added!")
        }
    }

    fun delete(t: Transaction) { viewModelScope.launch { repo.delete(t); snack.emit("Deleted") } }

    fun saveBudget(cat: TransactionCategory, amount: Double) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            repo.saveBudget(Budget(category=cat, monthlyLimit=amount, month=cal.get(Calendar.MONTH)+1, year=cal.get(Calendar.YEAR)))
            snack.emit("Budget saved for ${cat.label}!")
        }
    }

    fun spentForCategory(cat: TransactionCategory) =
        _monthlyTxns.value.filter { it.type == TransactionType.DEBIT && it.category == cat }.sumOf { it.amount }

    private fun calcCategorySpends(txns: List<Transaction>): List<CategorySpend> {
        val debits = txns.filter { it.type == TransactionType.DEBIT }
        val total = debits.sumOf { it.amount }
        return debits.groupBy { it.category }.map { (cat, list) ->
            val amt = list.sumOf { it.amount }
            CategorySpend(cat, amt, if (total > 0) (amt / total * 100).toFloat() else 0f, list.size)
        }.sortedByDescending { it.amount }
    }

    fun weeklyData(): List<Pair<String, Double>> {
        val sdf = SimpleDateFormat("EEE", Locale.getDefault())
        return _monthlyTxns.value.filter { it.type == TransactionType.DEBIT }
            .groupBy { sdf.format(Date(it.timestamp)) }
            .map { (day, list) -> day to list.sumOf { it.amount } }
    }
}

// ============================================================
// NAVIGATION
// ============================================================
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Transactions : Screen("transactions")
    object Analytics : Screen("analytics")
    object Budget : Screen("budget")
    object Add : Screen("add")
    object Profile : Screen("profile")
}

@Composable
fun AppNav(nav: NavHostController, vm: MainViewModel, loggedIn: Boolean) {
    NavHost(nav, if (loggedIn) Screen.Home.route else Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen { nav.navigate(Screen.Home.route) { popUpTo(Screen.Login.route) { inclusive = true } } }
        }
        composable(Screen.Home.route) {
            HomeScreen(vm,
                onTxns = { nav.navigate(Screen.Transactions.route) },
                onAnalytics = { nav.navigate(Screen.Analytics.route) },
                onBudget = { nav.navigate(Screen.Budget.route) },
                onAdd = { nav.navigate(Screen.Add.route) },
                onProfile = { nav.navigate(Screen.Profile.route) }
            )
        }
        composable(Screen.Transactions.route) { TransactionsScreen(vm) { nav.popBackStack() } }
        composable(Screen.Analytics.route) { AnalyticsScreen(vm) { nav.popBackStack() } }
        composable(Screen.Budget.route) { BudgetScreen(vm) { nav.popBackStack() } }
        composable(Screen.Add.route) { AddTransactionScreen(vm) { nav.popBackStack() } }
        composable(Screen.Profile.route) {
            ProfileScreen(onLogout = { nav.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } } }) { nav.popBackStack() }
        }
    }
}

// ============================================================
// HELPER: FORMAT AMOUNT
// ============================================================
fun fmt(amount: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amount)

// ============================================================
// BOTTOM NAV BAR
// ============================================================
@Composable
fun BottomBar(current: String, onNav: (String) -> Unit) {
    NavigationBar(containerColor = Surface) {
        listOf(
            Triple("home", Icons.Default.Home, "Home"),
            Triple("transactions", Icons.Default.List, "Transactions"),
            Triple("analytics", Icons.Default.BarChart, "Analytics"),
            Triple("profile", Icons.Default.Person, "Profile")
        ).forEach { (route, icon, label) ->
            NavigationBarItem(
                selected = current == route,
                onClick = { onNav(route) },
                icon = { Icon(icon, null) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Primary, selectedTextColor = Primary,
                    indicatorColor = Primary.copy(alpha = 0.15f)
                )
            )
        }
    }
}

// ============================================================
// TRANSACTION ITEM COMPOSABLE
// ============================================================
@Composable
fun TxnItem(t: Transaction, onDelete: (() -> Unit)? = null) {
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceVariant),
                contentAlignment = Alignment.Center) {
                Text(t.category.emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(t.merchant.ifEmpty { t.category.label }, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = OnBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sdf.format(Date(t.timestamp)), fontSize = 11.sp, color = TextSecondary)
                if (t.bankName.isNotEmpty()) Text(t.bankName, fontSize = 10.sp, color = Primary)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (t.type == TransactionType.CREDIT) "+" else "-"}${fmt(t.amount)}",
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = when (t.type) { TransactionType.CREDIT -> IncomeColor; TransactionType.DEBIT -> ExpenseColor; else -> TransferColor }
            )
            if (t.isManual) Text("Manual", fontSize = 10.sp, color = TextSecondary)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = DividerColor, thickness = 0.5.dp)
}

// ============================================================
// LOGIN SCREEN
// ============================================================
enum class LoginStep { PHONE, OTP, LOADING }

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    var step by remember { mutableStateOf(LoginStep.PHONE) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    val auth = remember { FirebaseAuth.getInstance() }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(c: PhoneAuthCredential) {
                auth.signInWithCredential(c).addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { error = "Auto-verify failed"; step = LoginStep.OTP }
            }
            override fun onVerificationFailed(e: FirebaseException) {
                error = e.message ?: "Verification failed"; step = LoginStep.PHONE
            }
            override fun onCodeSent(vId: String, t: PhoneAuthProvider.ForceResendingToken) {
                verificationId = vId; step = LoginStep.OTP
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Box(Modifier.fillMaxWidth().height(280.dp).background(
            Brush.verticalGradient(listOf(Primary.copy(alpha = 0.12f), Color.Transparent))
        ))
        Column(
            Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Primary, Color(0xFF00695C)))),
                contentAlignment = Alignment.Center
            ) { Text("₹", fontSize = 42.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
            Text("UPI Tracker", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = OnBackground)
            Text("Smart expense management for India 🇮🇳", fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant)) {
                Column(Modifier.padding(24.dp)) {
                    AnimatedContent(step, label = "") { s ->
                        when (s) {
                            LoginStep.PHONE, LoginStep.LOADING -> Column {
                                Text("Welcome! 👋", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnBackground)
                                Text("Enter your mobile number", fontSize = 13.sp, color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                                OutlinedTextField(
                                    value = phone, onValueChange = { if (it.length <= 10) phone = it },
                                    label = { Text("Mobile Number") },
                                    prefix = { Text("+91 ", color = Primary, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.Phone, null, tint = Primary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = DividerColor, focusedLabelColor = Primary)
                                )
                                if (error.isNotEmpty()) Text(error, color = ErrorColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = {
                                    if (phone.length != 10) { error = "Enter valid 10-digit number"; return@Button }
                                    step = LoginStep.LOADING; error = ""
                                    PhoneAuthProvider.verifyPhoneNumber(
                                        PhoneAuthOptions.newBuilder(auth).setPhoneNumber("+91$phone")
                                            .setTimeout(60L, TimeUnit.SECONDS).setActivity(activity).setCallbacks(callbacks).build()
                                    )
                                }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                                    enabled = step != LoginStep.LOADING,
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                                    if (step == LoginStep.LOADING) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    else Text("Send OTP", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                            LoginStep.OTP -> Column {
                                Text("Verify OTP 🔐", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnBackground)
                                Text("Code sent to +91 $phone", fontSize = 13.sp, color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                                OutlinedTextField(
                                    value = otp, onValueChange = { if (it.length <= 6) otp = it },
                                    label = { Text("6-Digit OTP") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = Primary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = DividerColor, focusedLabelColor = Primary)
                                )
                                if (error.isNotEmpty()) Text(error, color = ErrorColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = {
                                    if (otp.length != 6) { error = "Enter 6-digit OTP"; return@Button }
                                    auth.signInWithCredential(PhoneAuthProvider.getCredential(verificationId, otp))
                                        .addOnSuccessListener { onSuccess() }
                                        .addOnFailureListener { error = "Invalid OTP" }
                                }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                                    Text("Verify & Login", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                TextButton(onClick = { step = LoginStep.PHONE; otp = ""; error = "" }, Modifier.fillMaxWidth()) {
                                    Text("← Change Number", color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🔒 Secure", "📲 Auto-detect", "📊 Analytics").forEach {
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(SurfaceVariant).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(it, fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ============================================================
// HOME SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onTxns: () -> Unit, onAnalytics: () -> Unit,
               onBudget: () -> Unit, onAdd: () -> Unit, onProfile: () -> Unit) {
    val state by vm.home.collectAsState()
    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.snack.collect { snackState.showSnackbar(it) } }
    val cal = Calendar.getInstance()
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val greeting = if (hour < 12) "Good Morning" else if (hour < 17) "Good Afternoon" else "Good Evening"

    Scaffold(snackbarHost = { SnackbarHost(snackState) }, containerColor = Background,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = Primary, contentColor = Color.Black, shape = CircleShape) {
                Icon(Icons.Default.Add, null)
            }
        },
        bottomBar = {
            BottomBar("home") { route ->
                when (route) {
                    "transactions" -> onTxns(); "analytics" -> onAnalytics(); "profile" -> onProfile()
                }
            }
        }
    ) { pad ->
        if (state.loading) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Primary) }; return@Scaffold }
        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), modifier = Modifier.fillMaxSize().padding(pad)) {
            // Header
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("$greeting! 👋", fontSize = 13.sp, color = TextSecondary)
                        Text("UPI Tracker", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnBackground)
                    }
                    IconButton(onClick = onProfile) { Icon(Icons.Default.Person, null, tint = Primary) }
                }
            }
            // Balance card
            item {
                val savings = state.income - state.expense
                Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp), RoundedCornerShape(20.dp),
                    CardDefaults.cardColors(Color.Transparent)) {
                    Box(Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF1A2744), Color(0xFF0D1B2A))), RoundedCornerShape(20.dp))
                        .padding(20.dp)) {
                        Column {
                            Text("Monthly Overview", fontSize = 13.sp, color = TextSecondary)
                            Spacer(Modifier.height(6.dp))
                            Text(fmt(savings), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                                color = if (savings >= 0) IncomeColor else ExpenseColor)
                            Text("Net Savings", fontSize = 12.sp, color = TextSecondary)
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Column {
                                    Text("⬆️ Income", fontSize = 12.sp, color = TextSecondary)
                                    Text(fmt(state.income), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = IncomeColor)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("⬇️ Expense", fontSize = 12.sp, color = TextSecondary)
                                    Text(fmt(state.expense), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ExpenseColor)
                                }
                            }
                        }
                    }
                }
            }
            // Quick actions
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), Arrangement.spacedBy(10.dp)) {
                    listOf(Triple("📊","Analytics",onAnalytics), Triple("📋","Transactions",onTxns), Triple("🎯","Budgets",onBudget))
                        .forEach { (emoji, label, action) ->
                            Card(Modifier.weight(1f).clickable(onClick = action), RoundedCornerShape(14.dp),
                                CardDefaults.cardColors(SurfaceVariant)) {
                                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(emoji, fontSize = 22.sp)
                                    Text(label, fontSize = 11.sp, color = TextSecondary, textAlign = TextAlign.Center)
                                }
                            }
                        }
                }
            }
            // Category spends
            if (state.categorySpends.isNotEmpty()) {
                item {
                    Column(Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp)) {
                        Text("Top Spending", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnBackground)
                        Spacer(Modifier.height(10.dp))
                        state.categorySpends.take(4).forEachIndexed { i, spend ->
                            Column(Modifier.padding(bottom = 8.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("${spend.category.emoji} ${spend.category.label}", fontSize = 13.sp, color = OnSurface)
                                    Text(fmt(spend.amount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnBackground)
                                }
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(SurfaceVariant)) {
                                    Box(Modifier.fillMaxWidth(spend.percentage / 100f).fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp)).background(ChartColors.getOrElse(i) { Primary }))
                                }
                            }
                        }
                    }
                }
            }
            // Recent transactions header
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Recent Transactions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnBackground)
                    TextButton(onClick = onTxns) { Text("See All", color = Primary) }
                }
            }
            items(state.recentTxns) { TxnItem(it) }
            if (state.recentTxns.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 44.sp)
                        Text("No transactions yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.padding(top = 8.dp))
                        Text("Your UPI transactions will appear here automatically", fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// ============================================================
// TRANSACTIONS SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val all by vm.allTxns.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<TransactionType?>(null) }
    val filtered = all.filter { t ->
        (query.isEmpty() || t.merchant.contains(query, true) || t.description.contains(query, true) || t.bankName.contains(query, true)) &&
                (filter == null || t.type == filter)
    }
    Scaffold(containerColor = Background, topBar = {
        TopAppBar(title = { Text("Transactions", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = OnBackground) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = OnBackground))
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(value = query, onValueChange = { query = it },
                placeholder = { Text("Search...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Clear, null, tint = TextSecondary) } },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp), shape = RoundedCornerShape(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = DividerColor,
                    focusedContainerColor = SurfaceVariant, unfocusedContainerColor = SurfaceVariant))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "All", TransactionType.DEBIT to "Expense", TransactionType.CREDIT to "Income").forEach { (t, label) ->
                    FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.Black))
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceVariant).padding(10.dp),
                Arrangement.SpaceBetween) {
                Text("${filtered.size} transactions", fontSize = 12.sp, color = TextSecondary)
                Text("Spent: ${fmt(filtered.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount })}",
                    fontSize = 12.sp, color = ExpenseColor, fontWeight = FontWeight.SemiBold)
            }
            LazyColumn {
                items(filtered, key = { it.id }) { t ->
                    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart) { vm.delete(t); true } else false
                    })
                    SwipeToDismissBox(state = dismissState, backgroundContent = {
                        Box(Modifier.fillMaxSize().background(ErrorColor.copy(0.8f)).padding(end = 20.dp), Alignment.CenterEnd) {
                            Icon(Icons.Default.Delete, null, tint = Color.White)
                        }
                    }) { TxnItem(t) }
                }
                if (filtered.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 36.sp)
                        Text("No transactions found", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

// ============================================================
// ANALYTICS SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.home.collectAsState()
    val weekly = vm.weeklyData()
    Scaffold(containerColor = Background, topBar = {
        TopAppBar(title = { Text("Analytics", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = OnBackground) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = OnBackground))
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.spacedBy(12.dp)) {
                Card(Modifier.weight(1f), RoundedCornerShape(16.dp), CardDefaults.cardColors(SurfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("⬆️ Income", fontSize = 13.sp, color = TextSecondary)
                        Text(fmt(state.income), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = IncomeColor)
                    }
                }
                Card(Modifier.weight(1f), RoundedCornerShape(16.dp), CardDefaults.cardColors(SurfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("⬇️ Expense", fontSize = 13.sp, color = TextSecondary)
                        Text(fmt(state.expense), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ExpenseColor)
                    }
                }
            }
            // Savings rate
            val rate = if (state.income > 0) ((state.income - state.expense) / state.income * 100).toInt() else 0
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(16.dp), CardDefaults.cardColors(SurfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("💰 Savings Rate", fontSize = 14.sp, color = TextSecondary)
                            Text("$rate% of income saved", fontSize = 13.sp, color = OnBackground)
                        }
                        Text(fmt(state.income - state.expense), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = if (state.income >= state.expense) IncomeColor else ExpenseColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { (rate.coerceIn(0,100) / 100f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (rate >= 20) IncomeColor else if (rate >= 10) Color(0xFFFFD600) else ExpenseColor,
                        trackColor = DividerColor)
                }
            }
            // Weekly chart
            if (weekly.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val maxVal = weekly.maxOfOrNull { it.second } ?: 1.0
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(16.dp), CardDefaults.cardColors(SurfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📅 Weekly Spending", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnBackground)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth().height(110.dp), Arrangement.SpaceEvenly, Alignment.Bottom) {
                            weekly.forEach { (day, amt) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                    Box(Modifier.width(28.dp).fillMaxHeight((amt / maxVal).toFloat().coerceIn(0.05f, 1f))
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(Primary.copy(0.85f)))
                                    Spacer(Modifier.height(4.dp))
                                    Text(day.take(3), fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
            // Category breakdown
            if (state.categorySpends.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(16.dp), CardDefaults.cardColors(SurfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🏷️ Category Breakdown", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnBackground)
                        Spacer(Modifier.height(10.dp))
                        state.categorySpends.forEachIndexed { i, spend ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                        .background(ChartColors.getOrElse(i) { Primary }.copy(0.15f)), Alignment.Center) {
                                        Text(spend.category.emoji, fontSize = 16.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(spend.category.label, fontSize = 13.sp, color = OnBackground)
                                        Text("${spend.count} txns", fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(fmt(spend.amount), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnBackground)
                                    Text("${spend.percentage.toInt()}%", fontSize = 12.sp, color = ChartColors.getOrElse(i) { Primary })
                                }
                            }
                            if (i < state.categorySpends.lastIndex) HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================================================
// BUDGET SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(vm: MainViewModel, onBack: () -> Unit) {
    val budgets by vm.budgets.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selCat by remember { mutableStateOf(TransactionCategory.FOOD) }
    var amount by remember { mutableStateOf("") }
    var catExpanded by remember { mutableStateOf(false) }

    Scaffold(containerColor = Background, topBar = {
        TopAppBar(title = { Text("Budget Planner 🎯", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = OnBackground) } },
            actions = { IconButton({ showDialog = true }) { Icon(Icons.Default.Add, null, tint = Primary) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = OnBackground))
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            if (budgets.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎯", fontSize = 52.sp)
                        Text("No budgets set", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.padding(top = 8.dp))
                        Text("Tap + to set a monthly budget", color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showDialog = true }, colors = ButtonDefaults.buttonColors(Primary)) {
                            Text("Set Budget", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                budgets.forEach { b ->
                    val spent = vm.spentForCategory(b.category)
                    val progress = (spent / b.monthlyLimit).toFloat().coerceIn(0f, 1f)
                    val remaining = b.monthlyLimit - spent
                    val pColor = if (progress > 0.9f) ExpenseColor else if (progress > 0.7f) Color(0xFFFFD600) else Primary
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), RoundedCornerShape(16.dp),
                        CardDefaults.cardColors(SurfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.category.emoji, fontSize = 22.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(b.category.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnBackground)
                                }
                                if (spent > b.monthlyLimit)
                                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(ExpenseColor.copy(0.15f)).padding(6.dp, 2.dp)) {
                                        Text("OVER", fontSize = 10.sp, color = ExpenseColor, fontWeight = FontWeight.Bold)
                                    }
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = pColor, trackColor = DividerColor)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Spent: ${fmt(spent)}", fontSize = 12.sp, color = TextSecondary)
                                Text(if (spent > b.monthlyLimit) "Over by ${fmt(-remaining)}" else "Left: ${fmt(remaining)}",
                                    fontSize = 12.sp, color = if (spent > b.monthlyLimit) ExpenseColor else IncomeColor)
                            }
                            Text("Budget: ${fmt(b.monthlyLimit)}", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }

        if (showDialog) {
            AlertDialog(onDismissRequest = { showDialog = false }, containerColor = SurfaceVariant,
                title = { Text("Set Monthly Budget", color = OnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExposedDropdownMenuBox(catExpanded, { catExpanded = it }) {
                            OutlinedTextField(value = "${selCat.emoji} ${selCat.label}", onValueChange = {}, readOnly = true,
                                label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = DividerColor, focusedLabelColor = Primary))
                            ExposedDropdownMenu(catExpanded, { catExpanded = false }, Modifier.background(SurfaceVariant)) {
                                TransactionCategory.entries.forEach { cat ->
                                    DropdownMenuItem(text = { Text("${cat.emoji} ${cat.label}", color = OnBackground) },
                                        onClick = { selCat = cat; catExpanded = false })
                                }
                            }
                        }
                        OutlinedTextField(value = amount, onValueChange = { amount = it },
                            label = { Text("Monthly Limit (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = DividerColor, focusedLabelColor = Primary))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        amount.toDoubleOrNull()?.let { if (it > 0) { vm.saveBudget(selCat, it); showDialog = false; amount = "" } }
                    }, colors = ButtonDefaults.buttonColors(Primary)) { Text("Save", color = Color.Black, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton({ showDialog = false }) { Text("Cancel", color = TextSecondary) } }
            )
        }
    }
}

// ============================================================
// ADD TRANSACTION SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(vm: MainViewModel, onBack: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.DEBIT) }
    var cat by remember { mutableStateOf(TransactionCategory.OTHER) }
    var catExpanded by remember { mutableStateOf(false) }
    var amtError by remember { mutableStateOf("") }

    Scaffold(containerColor = Background, topBar = {
        TopAppBar(title = { Text("Add Transaction", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = OnBackground) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = OnBackground))
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(6.dp))
            // Type selector
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)) {
                TransactionType.entries.forEach { t ->
                    val sel = type == t
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) when(t) { TransactionType.DEBIT -> ExpenseColor; TransactionType.CREDIT -> IncomeColor; else -> TransferColor } else Color.Transparent)
                        .clickable { type = t }.padding(vertical = 12.dp), Alignment.Center) {
                        Text(when(t) { TransactionType.DEBIT -> "💸 Expense"; TransactionType.CREDIT -> "💰 Income"; else -> "🔄 Transfer" },
                            color = if (sel) Color.White else TextSecondary, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            OutlinedTextField(value = amount, onValueChange = { amount = it; amtError = "" }, label = { Text("Amount (₹)") },
                leadingIcon = { Text("  ₹", color = Primary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = amtError.isNotEmpty(), supportingText = { if (amtError.isNotEmpty()) Text(amtError, color = ErrorColor) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = DividerColor, focusedLabelColor = Primary))
            OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant / Person (Optional)") },
                leadingIcon = { Icon(Icons.Default.Store, null, tint = Primary) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = DividerColor, focusedLabelColor = Primary))
            ExposedDropdownMenuBox(catExpanded, { catExpanded = it }) {
                OutlinedTextField(value = "${cat.emoji} ${cat.label}", onValueChange = {}, readOnly = true,
                    label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = DividerColor, focusedLabelColor = Primary))
                ExposedDropdownMenu(catExpanded, { catExpanded = false }, Modifier.background(SurfaceVariant)) {
                    TransactionCategory.entries.forEach { c ->
                        DropdownMenuItem(text = { Text("${c.emoji} ${c.label}", color = OnBackground) }, onClick = { cat = c; catExpanded = false })
                    }
                }
            }
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Note (Optional)") },
                leadingIcon = { Icon(Icons.Default.Notes, null, tint = Primary) }, modifier = Modifier.fillMaxWidth(), maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = DividerColor, focusedLabelColor = Primary))
            Button(onClick = {
                val a = amount.toDoubleOrNull()
                if (a == null || a <= 0) { amtError = "Enter a valid amount"; return@Button }
                vm.addTransaction(a, type, cat, merchant, desc); onBack()
            }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(Primary)) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Add Transaction", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================================================
// PROFILE SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit, onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    var showLogout by remember { mutableStateOf(false) }

    Scaffold(containerColor = Background, topBar = {
        TopAppBar(title = { Text("Profile", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = OnBackground) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background, titleContentColor = OnBackground))
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(80.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Primary, Color(0xFF00695C)))),
                Alignment.Center) { Text("₹", fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            Text("UPI Tracker User", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnBackground)
            Text(user?.phoneNumber ?: "-", fontSize = 14.sp, color = Primary)
            Spacer(Modifier.height(24.dp))
            listOf(
                "Account" to listOf(Triple(Icons.Default.Phone, "Mobile", user?.phoneNumber ?: "-"),
                    Triple(Icons.Default.Verified, "Status", "Verified ✅")),
                "Features" to listOf(Triple(Icons.Default.Sms, "SMS Detection", "Enabled"),
                    Triple(Icons.Default.Notifications, "Budget Alerts", "Enabled"),
                    Triple(Icons.Default.DarkMode, "Theme", "Dark")),
                "Security" to listOf(Triple(Icons.Default.Cloud, "Cloud Sync", "Firebase"),
                    Triple(Icons.Default.Lock, "Encryption", "AES-256"))
            ).forEach { (title, items) ->
                Text(title, fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(SurfaceVariant)) {
                    items.forEachIndexed { i, (icon, label, value) ->
                        Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(label, fontSize = 14.sp, color = OnBackground)
                            }
                            Text(value, fontSize = 13.sp, color = TextSecondary)
                        }
                        if (i < items.lastIndex) HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showLogout = true }, Modifier.fillMaxWidth().height(50.dp), RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(ErrorColor.copy(0.12f))) {
                Icon(Icons.Default.Logout, null, tint = ErrorColor)
                Spacer(Modifier.width(8.dp))
                Text("Logout", color = ErrorColor, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("UPI Tracker v1.0 • Made for India 🇮🇳", fontSize = 12.sp, color = TextSecondary)
        }
    }

    if (showLogout) AlertDialog(onDismissRequest = { showLogout = false }, containerColor = SurfaceVariant,
        title = { Text("Logout?", color = OnBackground) }, text = { Text("Sure you want to logout?", color = TextSecondary) },
        confirmButton = { Button(onClick = { auth.signOut(); onLogout() }, colors = ButtonDefaults.buttonColors(ErrorColor)) { Text("Logout", color = Color.White) } },
        dismissButton = { TextButton({ showLogout = false }) { Text("Cancel", color = TextSecondary) } })
}

// ============================================================
// MAIN ACTIVITY
// ============================================================
class MainActivity : ComponentActivity() {
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.READ_SMS] == true) SMSSyncWorker.schedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perms = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(perms.toTypedArray())

        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "upi_tracker.db")
            .fallbackToDestructiveMigration().build()
        val vm = MainViewModel(Repository(db.txDao(), db.budgetDao()))
        val auth = FirebaseAuth.getInstance()

        setContent {
            UPITrackerTheme {
                val nav = rememberNavController()
                AppNav(nav, vm, auth.currentUser != null)
            }
        }
    }
}
