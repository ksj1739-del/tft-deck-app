package com.tftdeck.reader

import android.app.Application
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.sync.DailySyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TftApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 캐시(없으면 동봉 스냅샷)를 먼저 올린다. 첫 화면이 비지 않도록.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            DeckRepository.get(this@TftApp).load()
        }

        DailySyncWorker.schedule(this)
    }
}
