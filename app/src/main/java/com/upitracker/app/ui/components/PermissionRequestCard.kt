package com.upitracker.app.ui.components

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.upitracker.app.data.model.Transaction
import com.upitracker.app.data.model.TransactionType
import com.upitracker.app.ui.screens.CATEGORIES

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestCard() {
    val permissions = buildList {
        add(Manifest.permission.RECEIVE_SMS); add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val state = rememberMultiplePermissionsState(permissions)
    if (!state.allPermissionsGranted) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sms, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("SMS Permission Required", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Allow to auto-detect UPI transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(0.8f))
                }
                Button(onClick = { state.launchMultiplePermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Allow") }
            }
        }
    }
}

@Composable
fun AddTransactionDialog(onDismiss: () -> Unit, onAdd: (Transaction) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isCredit by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(CATEGORIES.first()) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Transaction") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount (₹)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Type: ", modifier = Modifier.padding(end = 8.dp))
                FilterChip(selected = !isCredit, onClick = { isCredit = false }, label = { Text("Debit") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = isCredit, onClick = { isCredit = true }, label = { Text("Credit") })
            }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(value = selectedCategory, onValueChange = {}, readOnly = true, label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { CATEGORIES.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { selectedCategory = c; expanded = false }) } }
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onAdd(Transaction(amount = amount.toDoubleOrNull() ?: 0.0, type = if (isCredit) TransactionType.CREDIT else TransactionType.DEBIT, description = description.ifEmpty { "Manual Entry" }, category = selectedCategory)) }, enabled = amount.isNotEmpty()) { Text("Add") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
