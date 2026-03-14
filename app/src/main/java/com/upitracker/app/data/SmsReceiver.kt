package com.upitracker.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.upitracker.app.data.repository.TransactionRepository
import com.upitracker.app.utils.SmsParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: TransactionRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages.forEach { sms ->
            val body = sms.messageBody ?: return@forEach
            val transaction = SmsParser.parse(body, sms.originatingAddress ?: "") ?: return@forEach
            CoroutineScope(Dispatchers.IO).launch {
                if (transaction.refId.isNotEmpty() && repository.getByRefId(transaction.refId) != null) return@launch
                repository.insert(transaction)
            }
        }
    }
}
