package com.tftdeck.reader.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.SyncResult
import java.util.concurrent.TimeUnit

/**
 * 하루 한 번 덱 데이터를 갱신한다.
 *
 * 먼저 version.json만 받아 해시를 비교하므로, 바뀐 게 없으면 통신량은 수백 바이트다.
 * 실패해도 기존 캐시를 그대로 두기 때문에 앱은 계속 동작한다.
 */
class DailySyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = DeckRepository.get(applicationContext)

        // 워커는 빈 프로세스에서 깨어날 수 있다. 캐시를 먼저 올려야
        // 해시 비교가 의미를 갖는다.
        repository.load()

        return when (val outcome = repository.sync()) {
            is SyncResult.Updated, SyncResult.UpToDate -> Result.success()
            is SyncResult.Failed -> {
                android.util.Log.w(TAG, "덱 갱신 실패, 기존 데이터 유지: ${outcome.reason}")
                // 일시적 네트워크 문제면 재시도. 계속 실패하면 다음 주기를 기다린다.
                // 실패를 Result.failure로 두면 주기 작업이 취소되므로 success로 끝낸다.
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            }
        }
    }

    companion object {
        private const val TAG = "DailySync"
        private const val WORK_NAME = "daily-deck-sync"
        private const val MAX_ATTEMPTS = 3

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // 첫 실행은 앱을 켠 직후 부담을 주지 않도록 조금 미룬다.
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // 이미 잡혀 있으면 그대로 둔다. 앱을 열 때마다 주기가 밀리지 않도록.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
