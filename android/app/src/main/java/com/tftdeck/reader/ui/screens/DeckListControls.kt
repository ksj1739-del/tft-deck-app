package com.tftdeck.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.DeckFeed
import com.tftdeck.reader.data.DeckKeys
import com.tftdeck.reader.data.DeckSort
import com.tftdeck.reader.data.DeckToken
import com.tftdeck.reader.data.SearchAxis
import com.tftdeck.reader.data.TokenCandidate
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.ItemRoleCount
import com.tftdeck.reader.ui.ListBanner
import com.tftdeck.reader.ui.bucketLabel
import com.tftdeck.reader.ui.bucketShortLabel
import com.tftdeck.reader.ui.components.FloaFilterChip
import com.tftdeck.reader.ui.components.SectionTitle
import com.tftdeck.reader.ui.components.TokenCandidateRow
import com.tftdeck.reader.ui.components.TokenSearchField
import com.tftdeck.reader.ui.gradeColor
import com.tftdeck.reader.ui.iconUrl

/**
 * 덱 목록 위 조작부(L8·V7). 예전 네 줄(구간 칩·검색·정렬 칩·필터 칩 35개, 309dp)을 두 줄로 줄였다:
 *  1줄 [구간 ▾][정렬][필터][조건 초기화•] ··· [보이는 수/이 구간 수]  — 높이 48dp(칩 32dp, 누름 영역 48dp)
 *  2줄 [검색 줄][S A B C D]                                          — 높이 40dp
 * 정렬·필터는 360dp 폰에서도 한 줄에 들어가도록 아이콘 버튼(44dp)이다. 정렬이 기본(등급순)이 아니거나 필터가 켜져 있으면
 * 아이콘이 파랑(선택됨)이 되고 필터에는 켜진 수가 붙는다. 주 특성·마무리 레벨·중국 한정만·편집 덱만·숨긴 덱 보기는 필터 시트로 옮겼다.
 * 검색 줄을 눌렀는데 비어 있으면 예시 조건이, 치는 중이면 후보(아이템은 핵심·대체 수, 도감 버튼)가 아래에 붙는다(결정 1).
 */
@Composable
internal fun DeckListControls(
    viewModel: AppViewModel,
    feed: DeckFeed,
    assetBase: String,
    shown: Int,
    total: Int,
    onOpenCodex: ((SearchAxis, String) -> Unit)?,
) {
    val hasBuckets = feed.buckets.isNotEmpty()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SCREEN_GUTTER),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasBuckets) {
                BucketChip(viewModel, feed)
                Spacer(Modifier.width(8.dp))
                SortButton(viewModel)
            }
            FilterButton(viewModel) { sheetOpen = true }
            Spacer(Modifier.width(8.dp))
            ResetChip(viewModel)
            Text(
                listCountText(shown, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .semantics { contentDescription = "이 구간 덱 ${total}개 중 ${shown}개 표시" },
            )
        }
        SearchRow(viewModel, assetBase, showGrades = hasBuckets, onOpenCodex = onOpenCodex)
    }

    if (sheetOpen) FilterSheet(viewModel, feed, assetBase, onDismiss = { sheetOpen = false })
}

/** 구간 드롭다운. 칩에는 짧은 이름(골드~에메), 메뉴에는 긴 이름. */
@Composable
private fun BucketChip(viewModel: AppViewModel, feed: DeckFeed) {
    val bucket by viewModel.bucket.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        DropChip(label = bucketShortLabel(bucket), description = "구간 ${bucketLabel(bucket)}", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DeckKeys.BUCKET_ORDER.filter { it in feed.buckets }.forEach { key ->
                DropdownMenuItem(
                    text = { Text(feed.buckets[key]?.label?.takeIf { it.isNotBlank() } ?: bucketLabel(key)) },
                    onClick = {
                        open = false
                        viewModel.setBucket(key)
                    },
                    modifier = if (key == bucket) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier,
                )
            }
        }
    }
}

/** 칩 모양 드롭다운 단추: FloaFilterChip 과 같은 면·모서리·높이에 ▾ 아이콘. */
@Composable
private fun DropChip(label: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .height(CHIP_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = description, role = Role.DropdownList, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 정렬 메뉴: 등급·픽률·상승(표본은 숨김). 고른 정렬을 다시 누르면 방향이 뒤집힌다. */
@Composable
private fun SortButton(viewModel: AppViewModel) {
    val sort by viewModel.sort.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(
            icon = Icons.AutoMirrored.Filled.Sort,
            description = "정렬 ${sort.label}",
            active = sort != DeckSort(),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            viewModel.sortModesVisible.forEach { mode ->
                val selected = mode == sort.mode
                DropdownMenuItem(
                    text = { Text(if (selected) sort.label else mode.forwardLabel) },
                    onClick = {
                        open = false
                        viewModel.tapSort(mode)
                    },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Filled.SwapVert, contentDescription = "다시 누르면 방향 바꾸기", modifier = Modifier.size(20.dp)) }
                    } else {
                        null
                    },
                    modifier = if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun FilterButton(viewModel: AppViewModel, onOpen: () -> Unit) {
    val count by viewModel.activeSheetFilterCount.collectAsState()
    ToolButton(
        icon = Icons.Filled.FilterAlt,
        description = if (count > 0) "필터 ${count}개 켜짐" else "필터",
        active = count > 0,
        badge = count,
        onClick = onOpen,
    )
}

/** 44dp 아이콘 단추. [active] 면 파랑(선택됨), [badge] 가 있으면 오른쪽 위에 숫자. */
@Composable
private fun ToolButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit, badge: Int = 0) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(TOOL_BUTTON)
            .clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (active) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("$badge", style = MaterialTheme.typography.labelSmall, color = scheme.onPrimary, maxLines = 1)
            }
        }
    }
}

/** '조건 초기화'(늘 보임). 되돌릴 조건이 없으면 흐리게 막고, 저장된 조건이 기본과 다르면 오른쪽 위에 점(N11). */
@Composable
private fun ResetChip(viewModel: AppViewModel) {
    val modified by viewModel.hasActiveFilter.collectAsState()
    Box {
        FloaFilterChip(selected = false, label = "조건 초기화", onClick = viewModel::clearFilters, enabled = modified)
        if (modified) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** 2줄: 검색 줄 + 등급 칸. 그 아래 후보(치는 중) 또는 예시(눌렀는데 비었을 때). */
@Composable
private fun SearchRow(
    viewModel: AppViewModel,
    assetBase: String,
    showGrades: Boolean,
    onOpenCodex: ((SearchAxis, String) -> Unit)?,
) {
    val tokens by viewModel.listTokens.collectAsState()
    val query by viewModel.listQuery.collectAsState()
    val candidates by viewModel.listCandidates.collectAsState()
    val roles by viewModel.listCandidateRoles.collectAsState()
    val examples by viewModel.searchExamples.collectAsState()
    val grades by viewModel.gradeFilter.collectAsState()
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // 고르면 조건으로 쌓고 키보드를 내려 좁혀진 목록을 보여 준다.
    val pick: (DeckToken) -> Unit = { token ->
        viewModel.addListToken(token)
        focusManager.clearFocus()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_GUTTER),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TokenSearchField(
                tokens = tokens,
                query = query,
                onQueryChange = viewModel::setListQuery,
                onRemoveToken = viewModel::removeListToken,
                onClearAll = viewModel::clearListTokens,
                // 키보드의 검색 키는 덱이 남는 첫 후보를 고른다. 친 글자가 없으면 키보드만 내린다.
                onSubmit = {
                    val first = candidates.firstOrNull { it.deckCount > 0 } ?: candidates.firstOrNull()
                    if (first != null) pick(first.token) else focusManager.clearFocus()
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.hasFocus },
            )
            if (showGrades) {
                Spacer(Modifier.width(8.dp))
                GradeCells(grades, viewModel::toggleGrade)
            }
        }
        when {
            query.isNotBlank() && candidates.isNotEmpty() ->
                CandidatePanel(candidates, roles, assetBase, onPick = { pick(it.token) }, onOpenCodex = onOpenCodex)
            focused && query.isBlank() && tokens.isEmpty() && examples.isNotEmpty() ->
                ExampleRow(examples, onPick = pick)
        }
    }
}

/** 등급 칸 S~D(사용자 결정 3): 28dp, 간격 6dp, 상자 테두리 없음. 켜짐 = 등급색 0.35 채움, 꺼짐 = 채움 없이 흐리게. */
@Composable
private fun GradeCells(grades: Set<String>, onToggle: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DeckKeys.GRADE_FILTER_ALL.forEach { grade ->
            val on = grade in grades
            Box(
                Modifier
                    .size(width = GRADE_CELL, height = 40.dp)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(grade) })
                    .semantics { contentDescription = "$grade 등급" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(GRADE_CELL)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) gradeColor(grade).copy(alpha = 0.35f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        grade,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alpha(if (on) 1f else 0.4f),
                    )
                }
            }
        }
    }
}

/** 후보 목록. 아이템 후보는 핵심·대체 덱 수를, 도감이 있는 항목은 도감 단추를 붙인다(검색 탭에서 옮김). */
@Composable
private fun CandidatePanel(
    candidates: List<TokenCandidate>,
    roles: Map<String, ItemRoleCount>,
    assetBase: String,
    onPick: (TokenCandidate) -> Unit,
    onOpenCodex: ((SearchAxis, String) -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        candidates.forEach { candidate ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TokenCandidateRow(candidate, assetBase, onClick = { onPick(candidate) }, modifier = Modifier.weight(1f))
                roles[candidate.token.key]?.let { role ->
                    Text(
                        "핵심 ${role.core} · 대체 ${role.backup}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                if (onOpenCodex != null) {
                    val axis = candidate.token.axis
                    val id = candidate.token.id
                    if (axis != null && id != null && axis in CODEX_AXES) {
                        Box(
                            Modifier
                                .size(TOOL_BUTTON)
                                .clickable(onClickLabel = "${candidate.token.name} 도감") { onOpenCodex(axis, id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "${candidate.token.name} 도감",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        // 단추가 없는 줄도 '덱 N' 이 같은 자리에서 끝나게 한다.
                        Spacer(Modifier.width(TOOL_BUTTON))
                    }
                }
            }
        }
    }
}

/** 예시 조건 칩. 누르면 바로 조건으로 들어간다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExampleRow(examples: List<DeckToken>, onPick: (DeckToken) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("예시", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEach { token -> FloaFilterChip(selected = false, label = token.name, onClick = { onPick(token) }) }
        }
    }
}

/** 필터 시트: 덱 종류(중국 한정만·편집 덱만), 주 특성, 마무리 레벨, 숨긴 덱. 예전 가로 스크롤 칩 줄을 옮긴 것(L8·N13). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(viewModel: AppViewModel, feed: DeckFeed, assetBase: String, onDismiss: () -> Unit) {
    val onlyChina by viewModel.onlyChina.collectAsState()
    val editorialOnly by viewModel.editorialOnly.collectAsState()
    val tiers by viewModel.availableTiers.collectAsState()
    val tierFilter by viewModel.tierFilter.collectAsState()
    val levels by viewModel.availableLevels.collectAsState()
    val levelFilter by viewModel.levelFilter.collectAsState()
    val mainTraits by viewModel.availableMainTraits.collectAsState()
    val mainTrait by viewModel.mainTraitFilter.collectAsState()
    val hidden by viewModel.hiddenSet.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val hasBuckets = feed.buckets.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SCREEN_GUTTER)
                .padding(bottom = 24.dp),
        ) {
            Text("필터", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)

            SectionTitle("덱 종류")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FloaFilterChip(selected = onlyChina, label = "중국 한정만", onClick = viewModel::toggleOnlyChina)
                if (hasBuckets) {
                    FloaFilterChip(selected = editorialOnly, label = "편집 덱만", onClick = viewModel::toggleEditorialOnly)
                } else {
                    // v1 피드는 통계 등급이 없어 편집 등급으로 거른다(예전 화면과 같은 동작).
                    tiers.forEach { tier ->
                        FloaFilterChip(selected = tier in tierFilter, label = tier, onClick = { viewModel.toggleTier(tier) })
                    }
                }
            }

            if (mainTraits.isNotEmpty()) {
                SectionTitle("주 특성")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mainTraits.forEach { trait ->
                        FloaFilterChip(
                            selected = trait.id == mainTrait,
                            label = trait.name,
                            onClick = { viewModel.toggleMainTrait(trait.id) },
                            leading = trait.icon?.let { icon ->
                                {
                                    AsyncImage(
                                        model = iconUrl(assetBase, icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            if (levels.isNotEmpty()) {
                SectionTitle("마무리 레벨")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    levels.forEach { level ->
                        FloaFilterChip(selected = level in levelFilter, label = "${level}레벨", onClick = { viewModel.toggleLevel(level) })
                    }
                }
            }

            SectionTitle("숨긴 덱")
            if (hidden.isNotEmpty() || showHidden) {
                FloaFilterChip(selected = showHidden, label = "숨긴 덱 보기 ${hidden.size}", onClick = viewModel::toggleShowHidden)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "카드를 길게 누르면 맨 위에 고정하거나 숨길 수 있습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 문구(순수 함수)
// ---------------------------------------------------------------------------

/** 조작부 오른쪽 끝의 수: '보이는 수/이 구간 수'(N11). */
internal fun listCountText(shown: Int, total: Int): String = "$shown/$total"

/** 목록 끝 안내: 'C·D 등급 덱 14개 숨김'. [off] 는 꺼 둔 등급(S → D 순). */
internal fun hiddenByGradeText(off: List<String>, count: Int): String =
    "${off.joinToString("·")} 등급 덱 ${count}개 숨김"

/** 검색 결과 위 안내와 누름 글자: 조건 밖 덱을 잠시 함께 보거나 뺀다. */
internal fun outsideLinkText(count: Int, showing: Boolean): Pair<String, String> =
    if (showing) "등급·구간 조건 밖 ${count}개 포함" to "빼기" else "등급·구간 조건 밖 ${count}개 더" to "보기"

/** 빈 목록의 둘째 줄: 무엇이 막았는지와 다음 행동(규칙 10). */
internal fun emptyListDetail(hasTokens: Boolean, off: List<String>, hiddenByGrade: Int, outside: Int): String = when {
    hasTokens && outside > 0 -> "검색 조건에 맞는 덱 ${outside}개가 등급·구간 조건 밖에 있습니다"
    !hasTokens && hiddenByGrade > 0 && off.isNotEmpty() -> "${off.joinToString("·")} 등급이 꺼져 있어 ${hiddenByGrade}개가 숨겨졌습니다"
    hasTokens -> "검색 칩을 빼거나 조건을 초기화해 보세요"
    else -> "필터를 줄이거나 조건을 초기화해 보세요"
}

/** 배너 글과 누름 글자(한 줄, 마침표 없음). */
internal fun listBannerText(banner: ListBanner): Pair<String, String?> = when (banner) {
    ListBanner.Syncing -> "새로고침 중…" to null
    is ListBanner.Failed -> "새로고침 실패 · ${banner.reason}" to "다시 시도"
    is ListBanner.Bundled ->
        (if (banner.patch.isBlank()) "앱에 담긴 데이터" else "앱에 담긴 데이터(패치 ${banner.patch})") to "새로고침"
    is ListBanner.Stale ->
        (if (banner.hours < 72) "${banner.hours}시간 전 데이터" else "${banner.hours / 24}일 전 데이터") to "새로고침"
}

/** 도감 상세가 있는 검색 축. 조합 재료는 아이템 도감의 일부라 단추를 달지 않는다. */
private val CODEX_AXES = setOf(SearchAxis.CHAMPION, SearchAxis.TRAIT, SearchAxis.ITEM, SearchAxis.AUGMENT)

/** 화면 좌우 여백(MASTER 규칙 9). */
internal val SCREEN_GUTTER = 16.dp
private val CHIP_HEIGHT = 32.dp
private val TOOL_BUTTON = 44.dp
private val GRADE_CELL = 28.dp
