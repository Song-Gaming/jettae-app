package com.example.hyundai.data.calendar

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.hyundai.appContainer
import com.example.hyundai.data.remote.ApiError
import java.util.concurrent.TimeUnit

/** 네트워크가 있을 때 15분마다 일정 캐시를 갱신한다 */
class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (val result = applicationContext.appContainer.calendarRepository.refresh()) {
            is SyncResult.Success, SyncResult.NeedsLink -> Result.success()
            is SyncResult.Failed -> when (result.error) {
                ApiError.Network, ApiError.Upstream -> Result.retry()
                // WorkManager는 재시도 간격을 Retry-After에 맞출 수 없어, 이번 회차는 건너뛰고 15분 뒤에 다시 한다
                else -> Result.success()
            }
        }

    companion object {
        private const val WORK_NAME = "calendar-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
