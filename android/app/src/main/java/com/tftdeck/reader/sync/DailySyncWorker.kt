package com.tftdeck.reader.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.IconPack
import com.tftdeck.reader.data.IconSyncResult
import com.tftdeck.reader.data.StatsRepository
import com.tftdeck.reader.data.StatsSyncResult
import com.tftdeck.reader.data.SyncResult
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * 하루 한 번 덱 데이터를 갱신한다. 앱에 담긴 데이터로 시작했으면 [syncNow] 로 한 번 더 곧바로 돈다.
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

        val outcome = repository.sync()

        // 도감 통계와 아이콘 팩도 여기서 함께 갱신한다. 덱 갱신 결과와 무관하게 각자 실패를 삼키고
        // 기존 파일을 유지하므로, 아래 재시도 판단은 덱 결과만 본다.
        syncStats()
        syncIcons()

        return when (outcome) {
            is SyncResult.Updated, SyncResult.UpToDate -> Result.success()
            is SyncResult.Failed -> {
                android.util.Log.w(TAG, "덱 갱신 실패, 기존 데이터 유지: ${outcome.reason}")
                // 일시적 네트워크 문제면 재시도. 계속 실패하면 다음 주기를 기다린다.
                // 실패를 Result.failure로 두면 주기 작업이 취소되므로 success로 끝낸다.
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            }
        }
    }

    /** 도감 통계. 해시가 바뀐 파일만 받는다. 실패해도 기존 파일(없으면 동봉 스냅샷)을 그대로 쓴다. */
    private suspend fun syncStats() {
        try {
            val stats = StatsRepository.get(applicationContext)
            // 빈 프로세스에서 깨어났으면 캐시를 먼저 올려야 해시 비교가 맞다.
            stats.load()
            val result = stats.sync()
            if (result is StatsSyncResult.Failed) {
                android.util.Log.w(TAG, "도감 갱신 실패, 기존 데이터 유지: ${result.reason}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "도감 갱신 중 오류, 기존 데이터 유지", e)
        }
    }

    /** 아이콘 팩. sync() 가 load() 를 먼저 부른다. 실패해도 기존 팩이 남아 원격 아이콘으로 떨어질 뿐이다. */
    private suspend fun syncIcons() {
        try {
            val result = IconPack.get(applicationContext).sync()
            if (result is IconSyncResult.Failed) {
                android.util.Log.w(TAG, "아이콘 팩 갱신 실패, 기존 팩 유지: ${result.reason}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "아이콘 팩 갱신 중 오류, 기존 팩 유지", e)
        }
    }

    companion object {
        private const val TAG = "DailySync"
        private const val WORK_NAME = "daily-deck-sync"
        private const val NOW_WORK_NAME = "first-deck-sync"
        private const val MAX_ATTEMPTS = 3

        /**
         * 지금 한 번 받는다. 설치 직후처럼 앱에 담긴 데이터로 시작했을 때 15분을 기다리지 않게 한다(N3).
         * 하루 한 번 도는 주기 작업과 따로 돌고, 이미 잡혀 있으면(재시도 대기 포함) 그대로 둔다.
         *
         * 네트워크 조건을 걸지 않는다: 오프라인이면 곧바로 실패해 목록 배너가 '새로고침 실패' 를 보여 주고,
         * 재시도(최대 [MAX_ATTEMPTS] 번, 30초부터 늘어남)나 배너의 '다시 시도' 로 다시 받는다. 받은 게 없으면 수백 바이트다.
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailySyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

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
