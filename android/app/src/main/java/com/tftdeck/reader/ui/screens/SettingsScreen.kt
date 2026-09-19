package com.tftdeck.reader.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.BuildConfig
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.IconPack
import com.tftdeck.reader.data.IconPackState
import com.tftdeck.reader.data.PlayerProfile
import com.tftdeck.reader.data.ProfileState
import com.tftdeck.reader.data.StatsRepository
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.ingame.sameRiotId
import com.tftdeck.reader.overlay.OverlayService
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.ProfileLink
import com.tftdeck.reader.ui.components.RiotIdForm
import com.tftdeck.reader.ui.components.SectionTitle
import com.tftdeck.reader.ui.deckDataLine
import com.tftdeck.reader.ui.ingame.GameLinkSection
import com.tftdeck.reader.ui.ingame.IngameViewModel
import com.tftdeck.reader.ui.ingame.LastLobbyCard
import com.tftdeck.reader.ui.ingame.ProfileSummaryV2
import com.tftdeck.reader.ui.ingame.SettingSwitchRow
import com.tftdeck.reader.ui.lolchessProfileUrl
import com.tftdeck.reader.ui.metatftProfileUrl
import com.tftdeck.reader.ui.openUrl
import com.tftdeck.reader.ui.patchLabel
import com.tftdeck.reader.ui.profileErrorText
import com.tftdeck.reader.ui.profileLink
import com.tftdeck.reader.ui.relativeTime
import com.tftdeck.reader.ui.theme.FloaColors
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

/**
 * 내 정보 탭.
 * 순서(N16): 게임 위에 띄우기 → 게임 연동 → 내 전적(지난 게임 로비는 있을 때만) → 덱 데이터 → 앱 정보(접힘).
 * 게임 위에서 쓰는 것을 위에 두고, 개발용 정보(원본·덱 코드 수·아이콘 팩)는 '앱 정보' 안으로 넣었다.
 */
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    overlayRunning: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
) {
    val state by viewModel.feedState.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val savedId by viewModel.savedRiotId.collectAsState()
    val savedRegion by viewModel.savedRegion.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val busy by viewModel.profileBusy.collectAsState()
    val ingame: IngameViewModel = composeViewModel()
    val lastLobby by ingame.lastLobby.collectAsState()
    // 권한 화면에서 돌아올 때(ON_RESUME) 뷰모델이 다시 읽는다(GameLinkSection 이 관찰자를 단다). 여기서 직접 읽으면 굳는다.
    val overlayGranted by ingame.overlayPermission.collectAsState()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val ready = state as? FeedState.Ready
    val link = profileLink(profileState, savedId)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {

        // --- 게임 위에 띄우기 ------------------------------------------------
        Group("게임 위에 띄우기") {
            if (!overlayGranted) {
                Text(
                    "게임 위에 덱을 띄우려면 '다른 앱 위에 표시' 를 허용해야 합니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                Text(
                    "목록에서 FloaTFT → 허용 → 뒤로",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                OutlinedButton(onClick = {
                    context.startActivity(OverlayService.permissionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) {
                    Text("권한 허용하기")
                }
            } else {
                // 켜면 '게임 위에 띄우기' 시트(TFT 열고 띄우기·홈 화면에 띄우기)가 열린다. 스위치는 실제로 떠 있는지를 따른다.
                SettingSwitchRow(
                    title = "오버레이 표시",
                    subtitle = "칩을 누르면 덱 목록이 펼쳐지고 어디로든 끌어 옮길 수 있습니다",
                    checked = overlayRunning,
                    onCheckedChange = onToggleOverlay,
                )
            }
        }

        // --- 게임 연동 ----------------------------------------------------------
        Group("게임 연동") {
            GameLinkSection(ingame, riotIdConnected = link is ProfileLink.Linked)
        }

        // --- 내 전적 ----------------------------------------------------------
        Group("내 전적") {
            var editing by rememberSaveable { mutableStateOf(false) }

            if (editing || link !is ProfileLink.Linked) {
                // 연결 전·실패·변경 중에는 입력 한 벌. 연결을 누르면 곧바로 접고, 실패하면 결과가 다시 펼친다.
                RiotIdForm(
                    initialId = savedId,
                    initialRegion = savedRegion,
                    busy = busy,
                    link = link,
                    onConnect = { id, region ->
                        editing = false
                        viewModel.saveProfile(id, region)
                    },
                    extraActions = if (savedId.isNotBlank() || editing) {
                        {
                            if (savedId.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        editing = false
                                        viewModel.clearProfile()
                                    },
                                    enabled = !busy,
                                ) { Text("연결 해제") }
                            }
                            if (editing) {
                                TextButton(onClick = { editing = false }) { Text("취소") }
                            }
                        }
                    } else {
                        null
                    },
                )
                Text(
                    "라이엇 ID 는 이 기기에만 저장되고 metatft 공개 프로필을 조회할 때만 씁니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                // 연결된 뒤에는 한 줄로 접는다(N17): '랄라붕#KR1 · KR · 변경'.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${link.riotId} · ${link.region}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { editing = true }) { Text("변경") }
                }
            }

            // 이 계정으로 받은 요약이 있으면 보이고, 실패했으면 사유를 한 줄로 붙인다.
            // 요약 없이 실패했으면 입력 칸 아래 한 줄로 이미 나온다.
            val summary: PlayerProfile? = profileState.profileOrNull
            if (summary != null) {
                val failure = (profileState as? ProfileState.Failed)?.let { profileErrorText(it.message) }
                Spacer(Modifier.height(12.dp))
                ProfileSummaryV2(
                    summary,
                    failure,
                    busy || profileState is ProfileState.Loading,
                    viewModel::refreshProfile,
                )
            }

            // 연결한 계정의 전적 사이트 바로가기. 매치 상세처럼 앱에 없는 정보는 거기서 본다.
            if (link is ProfileLink.Linked) {
                Row(Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { openUrl(context, lolchessProfileUrl(link.riotId, link.region)) }) {
                        Text("lolchess.gg 전적")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    TextButton(onClick = { openUrl(context, metatftProfileUrl(link.riotId, link.region)) }) {
                        Text("metatft 전적")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // --- 지난 게임 로비(있을 때만) ---------------------------------------------
        // 다른 계정으로 받아 둔 로비는 보여 주지 않는다. 게임 연동을 안 켠 사람에게 늘 빈 칸을 두지 않는다(N16).
        lastLobby
            ?.takeIf { savedId.contains("#") && sameRiotId(it.ownerRiotId, savedId) && it.players.isNotEmpty() }
            ?.let { lobby -> Group("지난 게임 로비") { LastLobbyCard(lobby) } }

        // --- 덱 데이터 ----------------------------------------------------------
        Group("덱 데이터") {
            val line = when {
                syncing -> "새로고침 중…"
                ready != null -> deckDataLine(patchLabel(ready.feed.version), ready.lastSyncedAt, System.currentTimeMillis())
                state is FeedState.Error -> (state as FeedState.Error).message
                else -> "불러오는 중…"
            }
            Text(line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = viewModel::refresh, enabled = !syncing) { Text("새로고침") }
                if (syncing) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }

        // --- 앱 정보(접힘) --------------------------------------------------------
        AppInfo(ready)
    }
}

/** 앱 버전, 데이터 세부, 원본 상태, 출처. 평소에는 접어 둔다. */
@Composable
private fun AppInfo(ready: FeedState.Ready?) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }

    SectionTitle(
        "앱 정보",
        modifier = Modifier.clickable(onClickLabel = if (open) "접기" else "펼치기") { open = !open },
        trailing = {
            Text("버전 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
    )
    if (!open) return

    GroupCard {
        InfoRow("버전", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        ready?.feed?.version?.let { version ->
            if (version.setNumber > 0) InfoRow("시즌", "시즌 ${version.setNumber}")
            InfoRow("덱", "${version.deckCount}개 · 중국 한정 ${version.onlyInChinaCount}개")
            InfoRow("덱 코드", "${version.teamCodeCount}개")
            version.metatftSet?.let { InfoRow("metatft", it) }
        }
        // 도감 통계와 아이콘 팩은 덱과 따로 받는다. 어느 쪽이 낡았는지 여기서 구분해 보여 준다.
        val statsState by StatsRepository.get(context).state.collectAsState()
        val iconState by IconPack.get(context).state.collectAsState()
        InfoRow("도감 데이터", statsStatusText(statsState))
        InfoRow("아이콘 팩", iconPackStatusText(iconState))

        ready?.feed?.version?.sources?.takeIf { it.isNotEmpty() }?.let { sources ->
            Text(
                "원본",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            sources.forEach { (name, status) ->
                InfoRow(SOURCE_LABELS[name] ?: name, if (status == "ok") "정상" else status, valueColor = if (status == "ok") null else scheme.error)
            }
            ready.feed.version.untranslatedIds.takeIf { it.isNotEmpty() }?.let { ids ->
                Text(
                    "한글 이름을 찾지 못한 항목 ${ids.size}개는 원본 ID 로 표시됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Text(
            "덱 통계는 lol.qq.com/tft, 챔피언·아이템 통계와 전적은 metatft.com, 한국어 이름과 아이콘은 Community Dragon 에서 " +
                "가져오며 Riot Games 가 보증하거나 후원하는 앱이 아닙니다",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** 도감 데이터 상태 한 줄. 예: "패치 18.2 · 2026-09-14 기준 · 3시간 전". */
private fun statsStatusText(state: StatsState): String = when (state) {
    StatsState.Loading -> "불러오는 중"
    StatsState.Missing -> "없음 · 새로고침으로 받습니다"
    is StatsState.Ready -> buildList {
        state.version.patchGlobal.takeIf { it.isNotBlank() }?.let { add("패치 $it") }
        state.version.statDate.takeIf { it.isNotBlank() }?.let { add("$it 기준") }
        add(if (state.fromBundle || state.lastSyncedAt == null) "앱에 담긴 데이터" else relativeTime(state.lastSyncedAt))
    }.joinToString(" · ")
}

/** 아이콘 팩 상태 한 줄. 예: "222개 · 0.5MB · 앱에 담긴 데이터". 팩이 없으면 아이콘을 원격에서 받는다. */
private fun iconPackStatusText(state: IconPackState): String {
    if (!state.isReady) return "없음 · 아이콘을 원격에서 받습니다"
    val size = String.format(java.util.Locale.US, "%.1fMB", state.bytes / (1024.0 * 1024.0))
    val synced = if (state.fromBundle || state.lastSyncedAt == null) "앱에 담긴 데이터" else relativeTime(state.lastSyncedAt)
    return "${state.count}개 · $size · $synced"
}

/** 1등 금색, 톱4 강조색, 그 아래는 차분하게. 전적 카드·지난 게임 로비·오버레이 카드가 같은 규칙을 쓴다. */
internal fun placementTint(place: Int): Color = when (place) {
    1 -> FloaColors.Gold
    2, 3, 4 -> FloaColors.Positive
    5, 6 -> FloaColors.OnSurfaceVariant
    else -> FloaColors.Negative
}

/** 설정 묶음: 섹션 제목(onSurface, 파랑 제목 금지) + 한 단 밝은 면(surface) 카드. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionTitle(title)
    GroupCard(content)
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        content = content,
    )
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
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
