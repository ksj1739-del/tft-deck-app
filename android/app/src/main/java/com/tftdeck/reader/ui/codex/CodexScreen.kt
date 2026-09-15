package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.StatScope
import com.tftdeck.reader.data.StatsScopeMeta
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.data.StatsVersion
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.relativeTime
import kotlinx.coroutines.delay

internal const val TAB_CHAMPION = 0
internal const val TAB_TRAIT = 1
internal const val TAB_ITEM = 2
internal const val TAB_AUGMENT = 3
private val TAB_LABELS = listOf("챔피언", "특성", "아이템", "증강")

/**
 * 도감 탭 첫 화면.
 *
 * 상단 세그먼트(챔피언/특성/아이템/증강) → 스코프 칩 → 표본·신선도 줄 → 탭 내용.
 * 증강은 스코프가 없으므로(중국 덱별 통계와 편집자 티어뿐) 스코프 칩 대신 고지 줄을 보여 준다.
 */
@Composable
fun CodexScreen(
    viewModel: CodexViewModel,
    onOpenChampion: (String) -> Unit,
    onOpenTrait: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenAugment: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    when (val current = state) {
        is StatsState.Loading -> CodexLoading()
        is StatsState.Missing -> MissingStats(viewModel)
        is StatsState.Ready -> ReadyCodex(current, viewModel, onOpenChampion, onOpenTrait, onOpenItem, onOpenAugment)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyCodex(
    ready: StatsState.Ready,
    viewModel: CodexViewModel,
    onOpenChampion: (String) -> Unit,
    onOpenTrait: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenAugment: (String) -> Unit,
) {
    val tab by viewModel.tab.collectAsState()
    val scope by viewModel.scope.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val message by viewModel.syncMessage.collectAsState()

    // 갱신 결과는 신선도 줄에 잠깐 보여 주고 원래 문구로 돌아간다.
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            viewModel.consumeSyncMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CodexHPad, vertical = 6.dp),
        ) {
            TAB_LABELS.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = tab == index,
                    onClick = { viewModel.setTab(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TAB_LABELS.size),
                    icon = {},
                    label = { Text(label, maxLines = 1) },
                )
            }
        }

        if (tab != TAB_AUGMENT) {
            val (scopes, fileVersion) = when (tab) {
                TAB_TRAIT -> ready.traits.scopes to ready.traits.version
                TAB_ITEM -> ready.items.scopes to ready.items.version
                else -> ready.champions.scopes to ready.champions.version
            }
            val available = CodexQuery.availableScopes(scopes)
            val effective = CodexQuery.resolveScope(scope, available)
            ScopeChips(available, effective, viewModel::setScope)
            FreshnessLine(
                text = message ?: freshnessText(ready, fileVersion, effective, scopes[effective]),
                syncing = syncing,
                onRefresh = viewModel::refresh,
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (tab) {
                TAB_TRAIT -> TraitTab(viewModel, onOpenTrait)
                TAB_ITEM -> ItemTab(viewModel, onOpenItem)
                TAB_AUGMENT -> AugmentTab(viewModel, onOpenAugment)
                else -> ChampionTab(viewModel, onOpenChampion)
            }
        }
    }
}

/** '18.2 · 96.7만 판 · 3시간 전'. 이 숫자가 어떤 표본에서 나왔는지 표 위에 늘 보이게 한다. */
@Composable
private fun FreshnessLine(text: String, syncing: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = CodexHPad, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "도감 데이터 갱신", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * 신선도 문구. metatft 스코프는 집계 시각으로 '3시간 전', lol.qq 스코프는 기준일 '9/15 기준'.
 * 앱이 받은 시각보다 데이터가 만들어진 시각이 사용자가 알고 싶은 것이다.
 */
internal fun freshnessText(
    ready: StatsState.Ready,
    fileVersion: StatsVersion,
    scope: String,
    meta: StatsScopeMeta?,
): String {
    val china = StatScope.isChina(scope)
    val parts = mutableListOf<String>()

    val patch = if (china) {
        ready.version.patch.ifBlank { fileVersion.patch }
    } else {
        ready.version.patchGlobal.ifBlank { fileVersion.patchGlobal }
    }
    if (patch.isNotBlank()) parts += if (china) "중국 $patch" else patch

    val sample = meta?.sampleSize ?: 0L
    if (sample > 0) parts += "${formatCountShort(sample)} 판"

    val statDate = meta?.statDate?.takeIf { it.isNotBlank() }
        ?: ready.version.statDate.ifBlank { fileVersion.statDate }
    val updatedAt = isoToEpochMillis(meta?.updatedAt)
        ?: isoToEpochMillis(fileVersion.generatedAt)
        ?: isoToEpochMillis(ready.version.generatedAt)
    parts += when {
        china && statDate.isNotBlank() -> "${formatShortDate(statDate)} 기준"
        updatedAt != null -> relativeTime(updatedAt)
        else -> relativeTime(ready.lastSyncedAt)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun MissingStats(viewModel: CodexViewModel) {
    val syncing by viewModel.syncing.collectAsState()
    val message by viewModel.syncMessage.collectAsState()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(title = "도감 데이터가 아직 없습니다", detail = "설정에서 갱신해 주세요.")
        if (syncing) {
            CircularProgressIndicator()
        } else {
            OutlinedButton(onClick = viewModel::refresh) { Text("지금 받기") }
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
    }
}
