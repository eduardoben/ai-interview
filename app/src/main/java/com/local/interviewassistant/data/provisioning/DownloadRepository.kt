package com.local.interviewassistant.data.provisioning

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Manages dispatching of WorkManager tasks for downloading models.
 */
@Suppress("unused")
class DownloadRepository(private val context: Context) {

    fun startModelDownload(modelId: String) {
        val workManager = WorkManager.getInstance(context)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(DownloadWorker.KEY_MODEL_ID to modelId))
            .build()
            
        workManager.enqueueUniqueWork(
            "download_$modelId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
