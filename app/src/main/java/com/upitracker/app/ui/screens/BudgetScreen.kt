package com.upitracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upitracker.app.data.model.Budget
import com.upitracker.app.viewmodel.MainViewModel
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

val CATEGORIES = listOf("Food & Dining", "Travel", "Shopping", "Entertainment", "Bills & Utilities", "Healthcare", "Uncategorized")

@Composable
fun BudgetScreen(viewModel: MainViewModel) {
    val state by viewModel.dashboardState.collectAsState()
    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null) } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Budget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            if (state.budgets.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text("No budgets set. Tap + to add.", color = MaterialTheme.colorScheme.onBackground.copy(0.5f)) } }
            } else {
                items(state.budgets) { budget ->
                    val spent = state.categoryBreakdown[budget.category] ?: 0.0
                    val progress = (spent / budget.limit).toFloat().coerceIn(0f, 1f)
                    val over = spent > budget.limit
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(budget.category, fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = { viewModel.deleteBudget(budget) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${fmt.format(spent)} spent", color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                                Text("of ${fmt.format(budget.limit)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        var cat by remember { mutableStateOf(CATEGORIES.first()) }
        var amt by remember { mutableStateOf("") }
        var exp by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Set Budget") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = exp, onExpandedChange = { exp = it }) {
                    OutlinedTextField(value = cat, onValueChange = {}, readOnly = true, label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = exp, onDismissRequest = { exp = false }) { CATEGORIES.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { cat = c; exp = false }) } }
                }
                OutlinedTextField(value = amt, onValueChange = { amt = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Monthly Limit (₹)") }, modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = {
            TextButton(onClick = { val cal = Calendar.getInstance(); viewModel.upsertBudget(Budget(cat, amt.toDoubleOrNull() ?: 0.0, cal.get(Calendar.MONTH)+1, cal.get(Calendar.YEAR))); showAdd = false }, enabled = amt.isNotEmpty()) { Text("Set") }
        }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } })
    }
}
