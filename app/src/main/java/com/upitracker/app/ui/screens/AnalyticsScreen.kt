package com.upitracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upitracker.app.ui.theme.CreditGreen
import com.upitracker.app.ui.theme.DebitRed
import com.upitracker.app.viewmodel.MainViewModel
import java.text.NumberFormat
import java.util.Locale

val categoryColors = listOf(Color(0xFF4FC3F7), Color(0xFFAED581), Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFFBA68C8), Color(0xFF4DB6AC))

@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val state by viewModel.dashboardState.collectAsState()
    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val total = state.categoryBreakdown.values.sum().takeIf { it > 0 } ?: 1.0
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Analytics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Text("Income", style = MaterialTheme.typography.labelMedium); Text(fmt.format(state.totalCredit), fontWeight = FontWeight.Bold, color = CreditGreen) } }
                Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Text("Spent", style = MaterialTheme.typography.labelMedium); Text(fmt.format(state.totalDebit), fontWeight = FontWeight.Bold, color = DebitRed) } }
            }
        }
        if (state.categoryBreakdown.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Spending by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        state.categoryBreakdown.entries.sortedByDescending { it.value }.forEachIndexed { i, (cat, amt) ->
                            val pct = (amt / total).toFloat().coerceIn(0f, 1f)
                            val color = categoryColors[i % categoryColors.size]
                            Column { 
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(cat); Text(fmt.format(amt), fontWeight = FontWeight.SemiBold) }
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { Box(Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color)) }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        } else {
            item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text("No spending data yet", color = MaterialTheme.colorScheme.onBackground.copy(0.5f)) } }
        }
    }
}
