package com.upitracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.upitracker.data.repository.AppDatabase
import com.upitracker.data.repository.TransactionRepository
import com.upitracker.data.sms.SMSSyncWorker
import com.upitracker.navigation.AppNavGraph
import com.upitracker.ui.theme.UPITrackerTheme
import com.upitracker.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true) {
            // Start historical SMS sync
            SMSSyncWorker.schedule(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request SMS permissions
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        smsPermissionLauncher.launch(permissionsToRequest.toTypedArray())

        // Initialize database
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "upi_tracker.db")
            .fallbackToDestructiveMigration()
            .build()

        val repository = TransactionRepository(db.transactionDao(), db.budgetDao())
        val viewModel = MainViewModel(repository)
        val auth = FirebaseAuth.getInstance()

        setContent {
            UPITrackerTheme {
                val navController = rememberNavController()
                val isLoggedIn = auth.currentUser != null

                AppNavGraph(
                    navController = navController,
                    mainViewModel = viewModel,
                    isLoggedIn = isLoggedIn
                )
            }
        }
    }
}
