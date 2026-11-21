package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val workRequest = OneTimeWorkRequestBuilder<RescheduleAllWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "snapfade_boot_reschedule",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
