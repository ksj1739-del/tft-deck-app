package com.tftdeck.reader.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.ProfileRepository
import com.tftdeck.reader.data.ProfileState
import com.tftdeck.reader.ingame.sameRiotId
import com.tftdeck.reader.overlay.OverlayService
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.formatDate
import com.tftdeck.reader.ui.ingame.GameLinkSection
import com.tftdeck.reader.ui.ingame.IngameViewModel
import com.tftdeck.reader.ui.ingame.LastLobbyCard
import com.tftdeck.reader.ui.ingame.ProfileSummaryV2
import com.tftdeck.reader.ui.lolchessProfileUrl
import com.tftdeck.reader.ui.metatftProfileUrl
import com.tftdeck.reader.ui.openUrl
import com.tftdeck.reader.ui.relativeTime
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

/**
 * 내 정보 탭.
 * 순서: 내 전적 → 지난 게임 로비 → 게임 연동 → 오버레이 → 덱 데이터 → 원본 상태 → 출처.
 * 자주 보는 전적이 맨 위, 한 번 정하면 잘 안 바꾸는 설정이 아래로 간다.
 */
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    overlayRunning: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
) {
    val state by viewModel.feedState.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val pinned by viewModel.pinnedDeckId.collectAsState()
    val savedId by viewModel.savedRiotId.collectAsState()
    val savedRegion by viewModel.savedRegion.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val busy by viewModel.profileBusy.collectAsState()
    val ingame: IngameViewModel = composeViewModel()
    val lastLobby by ingame.lastLobby.collectAsState()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val ready = state as? FeedState.Ready

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {

        // --- 내 전적 ----------------------------------------------------------
        Group("내 전적") {
            var input by remember(savedId) { mutableStateOf(savedId) }
            var regionPick by remember(savedRegion) { mutableStateOf(savedRegion) }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("라이엇 ID") },
                placeholder = { Text("이름#태그") },
                supportingText = { Text("게임 안 프로필에 보이는 '이름#태그'를 그대로 적습니다") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
            )

            Spacer(Modifier.size(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProfileRepository.REGIONS.forEach { code ->
                    FilterChip(
                        selected = regionPick == code,
                        onClick = { regionPick = code },
                        label = { Text(code) },
                    )
                }
            }

            Spacer(Modifier.size(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.saveProfile(input, regionPick) },
                    enabled = !busy && input.contains("#"),
                ) {
                    Text(if (savedId.isBlank()) "연결" else "다시 조회")
                }
                if (savedId.isNotBlank()) {
                    OutlinedButton(onClick = viewModel::clearProfile, enabled = !busy) {
                        Text("연결 해제")
                    }
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.size(12.dp))
            when (val ps = profileState) {
                is ProfileState.Ready -> ProfileSummaryV2(ps.profile, null, busy, viewModel::refreshProfile)
                is ProfileState.Loading -> ps.previous?.let { ProfileSummaryV2(it, null, true, viewModel::refreshProfile) }
                is ProfileState.Failed -> ProfileSummaryV2(ps.previous, ps.message, busy, viewModel::refreshProfile)
                ProfileState.NotConfigured -> Text(
                    "라이엇 ID를 연결하면 티어와 최근 등수가 오버레이 오른쪽에 함께 뜹니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            // 연결한 계정의 전적 사이트 바로가기. 매치 상세처럼 앱에 없는 정보는 거기서 본다.
            if (savedId.contains("#")) {
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openUrl(context, lolchessProfileUrl(savedId, savedRegion)) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("lolchess.gg 전적")
                    }
                    OutlinedButton(onClick = { openUrl(context, metatftProfileUrl(savedId, savedRegion)) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("metatft 전적")
                    }
                }
            }

            Spacer(Modifier.size(8.dp))
            Text(
                "전적은 metatft의 공개 프로필에서 가져옵니다. 라이엇 ID는 이 기기에만 저장되고 조회할 때만 쓰입니다.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // --- 지난 게임 로비 ------------------------------------------------------
        Group("지난 게임 로비") {
            // 다른 계정으로 받아 둔 로비는 보여 주지 않는다.
            val lobby = lastLobby?.takeIf { savedId.contains("#") && sameRiotId(it.ownerRiotId, savedId) }
            LastLobbyCard(lobby)
        }

        // --- 게임 연동 ----------------------------------------------------------
        Group("게임 연동") {
            GameLinkSection(ingame)
        }

        // --- 오버레이 --------------------------------------------------------
        Group("게임 위에 띄우기") {
            val granted = OverlayService.canDrawOverlays(context)

            if (!granted) {
                Text(
                    "다른 앱 위에 덱을 띄우려면 시스템 권한이 필요합니다. 이 권한은 오버레이 창에만 쓰입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Button(onClick = {
                    context.startActivity(
                        OverlayService.permissionIntent(context)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) {
                    Text("권한 설정 열기")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("오버레이 표시", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "전체 덱 목록으로 시작합니다",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = overlayRunning, onCheckedChange = onToggleOverlay)
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    "접힌 상태에서는 작은 칩으로 떠 있고, 누르면 전체 덱 목록이 펼쳐집니다. " +
                        "목록에서 덱을 고르면 요약으로 들어가고 뒤로 누르면 목록으로 돌아옵니다. " +
                        "어느 상태든 끌어서 옮길 수 있습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                viewModel.deck(pinned.orEmpty())?.let { deck ->
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "덱 상세의 '게임 위에 띄우기'로 켜면 그 덱이 바로 열립니다 (최근: ${deck.name})",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        // --- 데이터 ----------------------------------------------------------
        Group("덱 데이터") {
            InfoRow("마지막 갱신", ready?.lastSyncedAt?.let { formatDate(it) } ?: "아직 없음")
            InfoRow("상태", relativeTime(ready?.lastSyncedAt))
            ready?.feed?.version?.let { version ->
                InfoRow("시즌", "${version.set} · 패치 ${version.patch}")
                InfoRow("덱", "${version.deckCount}개 (중국 한정 ${version.onlyInChinaCount}개)")
                InfoRow("덱 코드", "${version.teamCodeCount}개")
                version.metatftSet?.let { InfoRow("metatft", it) }
            }
            // 통합: 도감·아이콘 팩 상태 행 (StatsRepository.state, IconPack.state)

            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = viewModel::refresh, enabled = !syncing) {
                    Text(if (syncing) "갱신 중..." else "지금 갱신")
                }
                if (syncing) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "평소에는 하루 한 번 자동으로 갱신합니다. 바뀐 게 없으면 수백 바이트만 주고받습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // --- 원본 상태 --------------------------------------------------------
        ready?.feed?.version?.sources?.takeIf { it.isNotEmpty() }?.let { sources ->
            Group("원본") {
                sources.forEach { (name, status) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            SOURCE_LABELS[name] ?: name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (status == "ok") "정상" else status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (status == "ok") scheme.primary else scheme.error,
                        )
                    }
                }
                ready.feed.version.untranslatedIds.takeIf { it.isNotEmpty() }?.let { ids ->
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "한글 이름을 찾지 못한 항목 ${ids.size}개는 원본 ID로 표시됩니다. 새 시즌 직후에 잠깐 나타날 수 있습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Group("출처") {
            Text(
                "덱 통계는 lol.qq.com/tft, 챔피언·아이템 통계와 전적은 metatft.com, " +
                    "한국어 이름과 아이콘은 Community Dragon에서 가져옵니다. " +
                    "Riot Games가 보증하거나 후원하는 앱이 아닙니다.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.size(20.dp))
    }
}

/** 1등 금색, 톱4 강조색, 그 아래는 차분하게. 전적 카드·지난 게임 로비·오버레이 카드가 같은 규칙을 쓴다. */
internal fun placementTint(place: Int): Color = when (place) {
    1 -> Color(0xFFCF9B1F)
    2, 3, 4 -> Color(0xFF17705C)
    5, 6 -> Color(0xFF6E7C78)
    else -> Color(0xFFBA5A56)
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private val SOURCE_LABELS = mapOf(
    "lolqq" to "lol.qq 덱",
    "lolqqWinrate" to "lol.qq 승률 덱",
    "lolqqDatasearch" to "lol.qq 데이터 검색",
    "lolqqDetail" to "lol.qq 덱 상세",
    "lolqqStatic" to "lol.qq 도감",
    "metatft" to "metatft 대조",
    "metatftStats" to "metatft 통계",
    "namesKo" to "한국어 이름",
    "namesEn" to "영문 이름",
    "teamPlanner" to "덱 코드",
    "modeRegistry" to "시즌 확인",
)
