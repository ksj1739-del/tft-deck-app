package com.tftdeck.reader.ingame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tftdeck.reader.overlay.OverlayService

/**
 * 재부팅·앱 업데이트 뒤 게임 연동(TFT 감지)을 다시 켠다(S3).
 *
 * 예전에는 앱 화면을 한 번 열어야 감지가 되살아나서, 폰을 켜고 바로 TFT 를 하면 칩도 알림도 없었다.
 * 두 방송 모두 백그라운드에서 포그라운드 서비스를 시작해도 되는 예외이고, 서비스 유형 specialUse 는
 * Android 15 가 BOOT_COMPLETED 에서 막는 유형에 들지 않는다. 직접 띄운 오버레이 창은 되살리지 않는다(감지만).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = IngamePrefs.get(context)
        // 권한 확인은 감지를 켜 둔 사람에게만 한다.
        val restart = shouldRestartWatch(
            action = intent.action,
            detectEnabled = prefs.detectEnabled.value,
            usageGranted = { hasUsageStatsPermission(context) },
        )
        if (restart) OverlayService.startWatch(context)
    }

    companion object {
        internal const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        internal const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"

        /** 부팅 완료·앱 업데이트 방송이고, 감지를 켜 두었고, 사용 기록 권한이 아직 있으면 감지를 다시 켠다. */
        internal fun shouldRestartWatch(action: String?, detectEnabled: Boolean, usageGranted: () -> Boolean): Boolean =
            (action == ACTION_BOOT_COMPLETED || action == ACTION_MY_PACKAGE_REPLACED) && detectEnabled && usageGranted()
    }
}
