package com.upitracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.upitracker.app.data.model.TransactionType
import com.upitracker.app.ui.components.AddTransactionDialog
import com.upitracker.app.ui.components.TransactionItem
import com.upitracker.app.viewmodel.MainViewModel

@Composable
fun TransactionsScreen(viewModel: MainViewModel) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, null) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Transactions", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = searchQuery, onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search...") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterType == null, onClick = { viewModel.setFilterType(null) }, label = { Text("All") })
                FilterChip(selected = filterType == TransactionType.CREDIT, onClick = { viewModel.setFilterType(if (filterType == TransactionType.CREDIT) null else TransactionType.CREDIT) }, label = { Text("Credits") })
                FilterChip(selected = filterType == TransactionType.DEBIT, onClick = { viewModel.setFilterType(if (filterType == TransactionType.DEBIT) null else TransactionType.DEBIT) }, label = { Text("Debits") })
            }
            Spacer(Modifier.height(8.dp))
            if (transactions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No transactions found", color = MaterialTheme.colorScheme.onBackground.copy(0.5f)) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions, key = { it.id }) { tx -> TransactionItem(tx) { viewModel.deleteTransaction(tx) } }
                }
            }
        }
    }
    if (showAddDialog) { AddTransactionDialog(onDismiss = { showAddDialog = false }, onAdd = { viewModel.addManualTransaction(it); showAddDialog = false }) }
}
