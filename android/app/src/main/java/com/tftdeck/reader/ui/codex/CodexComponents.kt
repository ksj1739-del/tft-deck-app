package com.tftdeck.reader.ui.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.tftdeck.reader.ui.theme.FloaColors
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.CodexPrevStat
import com.tftdeck.reader.data.CodexStat
import com.tftdeck.reader.data.ItemWearerStat
import com.tftdeck.reader.data.StatScope
import com.tftdeck.reader.data.StatsState
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.costColor
import com.tftdeck.reader.ui.traitStyleColor

// ---------------------------------------------------------------------------
// 치수
// ---------------------------------------------------------------------------

// 폰 폭(360dp)에서 가로 스크롤 없이 이름 + 4열이 들어가도록 잡은 폭.
// 글자는 12sp 아래로 내리지 않는다(테마의 label 스타일은 10.5~11.5sp라 표에는 쓰지 않는다).
internal val CodexHPad = 14.dp
internal val RowMinHeight = 52.dp
internal val ColGrade = 36.dp
internal val ColAvg = 52.dp
internal val ColTop4 = 50.dp
internal val ColCount = 56.dp
internal const val DIM_ALPHA = 0.5f
internal val DetailPadding = PaddingValues(horizontal = CodexHPad, vertical = 12.dp)
internal val DetailGap = 22.dp

/** 밝은 배지 위에 얹는 어두운 글자색. 밝은 테마·어두운 테마 모두에서 읽힌다. */
internal val BadgeInk = FloaColors.Background
private val UnstyledGlyphBackground = FloaColors.OutlineVariant

// ---------------------------------------------------------------------------
// 배지·칸
// ---------------------------------------------------------------------------

/** 통계 등급(S~D) 또는 편집자 티어. 값이 없으면 "-" 회색. */
@Composable
fun GradeBadge(grade: String?, modifier: Modifier = Modifier, large: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val label = grade?.trim()?.takeIf { it.isNotEmpty() }
    val shape = RoundedCornerShape(if (large) 8.dp else 6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (label == null) scheme.surfaceVariant else gradeColor(label))
            .padding(horizontal = if (large) 10.dp else 6.dp, vertical = if (large) 2.dp else 1.dp)
            .clearAndSetSemantics { contentDescription = if (label == null) "등급 없음" else "등급 $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label ?: "-",
            color = if (label == null) scheme.onSurfaceVariant else BadgeInk,
            fontSize = if (large) 16.sp else 12.sp,
            lineHeight = if (large) 20.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 표의 숫자 칸. 숫자는 오른쪽 정렬해 자릿수가 맞게 한다. */
@Composable
fun StatCell(
    text: String,
    width: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    bold: Boolean = false,
    align: TextAlign = TextAlign.End,
) {
    Text(
        text = text,
        modifier = modifier.width(width),
        style = MaterialTheme.typography.bodySmall,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

/** 목록 표의 고정 네 칸: 등급 | 평균 등수 | TOP4 | 게임 수. 폭은 [CodexTableHeader]와 같다. */
@Composable
fun StandardStatCells(stat: CodexStat?) {
    Box(Modifier.width(ColGrade), contentAlignment = Alignment.Center) { GradeBadge(stat?.grade) }
    StatCell(formatAvg(stat?.avg), ColAvg, bold = true)
    StatCell(formatPct(stat?.top4Share), ColTop4)
    StatCell(formatCountShort(stat?.n), ColCount)
}

@Composable
fun CodexTableHeader(
    firstLabel: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = CodexHPad,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderText(firstLabel, Modifier.weight(1f), TextAlign.Start)
        HeaderText("등급", Modifier.width(ColGrade), TextAlign.Center)
        HeaderText("평균 등수", Modifier.width(ColAvg), TextAlign.End)
        HeaderText("TOP4", Modifier.width(ColTop4), TextAlign.End)
        HeaderText("게임 수", Modifier.width(ColCount), TextAlign.End)
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        textAlign = align,
        maxLines = 2,
    )
}

/**
 * 상세 화면의 스코프 비교 표: 범위 | 평균 | TOP4 | 승률 | 픽률 | 등급.
 * 게임 수는 범위 이름 아래에 둔다. 표본을 늘 보이게 해야 숫자를 믿을지 판단할 수 있다.
 */
@Composable
fun ScopeTable(
    scopes: List<String>,
    selected: String?,
    minSample: Int,
    statFor: (String) -> CodexStat?,
    onSelect: ((String) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderText("범위", Modifier.weight(1f), TextAlign.Start)
            HeaderText("평균 등수", Modifier.width(44.dp), TextAlign.End)
            HeaderText("TOP4", Modifier.width(48.dp), TextAlign.End)
            HeaderText("승률", Modifier.width(48.dp), TextAlign.End)
            HeaderText("픽률", Modifier.width(48.dp), TextAlign.End)
            HeaderText("등급", Modifier.width(40.dp), TextAlign.Center)
        }
        scopes.forEach { key ->
            val stat = statFor(key)
            val dim = stat == null || stat.isLowSample(minSample)
            val isSelected = key == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) scheme.primaryContainer else Color.Transparent)
                    .then(if (onSelect != null) Modifier.clickable { onSelect(key) } else Modifier)
                    .alpha(if (dim) DIM_ALPHA else 1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        scopeLabel(key),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Text(
                        stat?.let { "${formatCountShort(it.n)} 판" } ?: "기록 없음",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                StatCell(formatAvg(stat?.avg), 44.dp, bold = true)
                StatCell(formatPct(stat?.top4Share), 48.dp)
                StatCell(formatPct(stat?.win), 48.dp)
                StatCell(formatPct(stat?.pick), 48.dp)
                Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { GradeBadge(stat?.grade) }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        if (!trailing.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 아이콘
// ---------------------------------------------------------------------------

/** 둥근 네모 아이콘. 이미지가 오기 전에도 자리가 비어 보이지 않게 바탕을 깐다. */
@Composable
fun CodexIcon(
    url: String?,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    corner: Dp = 6.dp,
    borderWidth: Dp = 1.5.dp,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, shape) else Modifier),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 이미지와 이름을 나란히. */
@Composable
fun IconWithName(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    borderColor: Color? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodexIcon(url, name, iconSize, borderColor = borderColor)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 2)
            if (!caption.isNullOrBlank()) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 특성 아이콘. CommunityDragon 특성 아이콘은 흰 실루엣이라 밝은 바탕에서 사라진다.
 * 게임처럼 등급색 바탕에 어두운 실루엣으로 그린다(등급을 모르면 어두운 바탕에 밝은 실루엣).
 */
@Composable
fun TraitGlyph(
    url: String?,
    style: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val styled = style in 1..4
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(if (styled) traitStyleColor(style) else UnstyledGlyphBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                colorFilter = ColorFilter.tint(if (styled) BadgeInk else FloaColors.OnSurface),
                modifier = Modifier.fillMaxSize().padding(size * 0.16f),
            )
        }
    }
}

@Composable
fun TraitNameChip(name: String, iconUrl: String?, style: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TraitGlyph(iconUrl, style, 18.dp)
        Spacer(Modifier.width(5.dp))
        Text(name, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface, maxLines = 1)
    }
}

/** 챔피언 초상 + 이름 한 칸(격자용). */
@Composable
fun ChampionTile(
    url: String?,
    name: String,
    cost: Int?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Column(
        modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CodexIcon(url, name, size, borderColor = costColor(cost))
        Spacer(Modifier.height(3.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 색 점 + 짧은 라벨(희귀도, 버프/너프). 색 글자는 밝은 테마에서 읽기 어려워 점으로만 색을 준다. */
@Composable
fun LabelChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.22f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(color, 6.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** 착용 챔피언 줄: 아바타 24dp + 이름. 다른 스코프로 대신했으면 끝에 지역을 적는다. */
@Composable
fun WearerStrip(
    wearers: List<ItemWearerStat>,
    fallbackScope: String?,
    ready: StatsState.Ready,
    assetBase: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shown = wearers.take(CodexQuery.WEARER_LIMIT)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            shown.forEach { wearer ->
                val champion = ready.championsById[wearer.id]
                CodexIcon(
                    codexIconUrl(assetBase, champion?.icon),
                    wearer.name,
                    24.dp,
                    borderColor = costColor(champion?.cost),
                    corner = 12.dp,
                    borderWidth = 1.dp,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            shown.joinToString(" · ") { wearer -> wearer.name.ifBlank { ready.championsById[wearer.id]?.name ?: wearer.id } },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (fallbackScope != null) {
            Spacer(Modifier.width(4.dp))
            Text(scopeRegionLabel(fallbackScope), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** 아이템 한 줄: 아이콘·이름 | 평균 등수 / Δ · 게임 수. */
@Composable
fun ItemStatLine(
    url: String?,
    name: String,
    avg: Double?,
    n: Int,
    delta: Double?,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodexIcon(url, name, 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(rankText(avg), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Row {
                if (delta != null && delta.isFinite()) {
                    Text(formatDelta(delta), style = MaterialTheme.typography.bodySmall, color = deltaColor(delta, scheme.onSurfaceVariant))
                    Text(" · ", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text("${formatCountShort(n)} 판", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** 지난 패치 대비 한 줄: 평균 4.30 → 4.37 (+0.07), 픽률 15.0% → 13.9%. */
@Composable
fun PrevChangeRow(scope: String, prev: CodexPrevStat, current: CodexStat?) {
    val scheme = MaterialTheme.colorScheme
    val prevAvg = prev.avg
    val prevPick = prev.pick
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(scopeLabel(scope), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface, modifier = Modifier.width(92.dp))
        Column(Modifier.weight(1f)) {
            if (prevAvg != null) {
                val diff = current?.avg?.let { it - prevAvg }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "평균 ${formatAvg(prevAvg)} → ${formatAvg(current?.avg)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                    )
                    if (diff != null && diff.isFinite()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatDelta(diff),
                            style = MaterialTheme.typography.bodySmall,
                            color = deltaColor(diff, scheme.onSurfaceVariant),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (prevPick != null) {
                Text(
                    "픽률 ${formatPct(prevPick)} → ${formatPct(current?.pick)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 작은 막대 추세(오래된 것 → 최신). 빠진 날은 옅은 짧은 막대. */
@Composable
fun TrendBars(values: List<Double?>, modifier: Modifier = Modifier) {
    val finite = values.mapNotNull { value -> value?.takeIf { it.isFinite() } }
    if (finite.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val min = finite.min()
    val span = (finite.max() - min).takeIf { it > 1e-9 }
    Row(
        modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { raw ->
            val value = raw?.takeIf { it.isFinite() }
            val fraction = when {
                value == null -> 0.12f
                span == null -> 0.6f
                else -> (0.25 + 0.75 * (value - min) / span).toFloat()
            }
            Box(
                Modifier
                    .width(7.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (value == null) scheme.outlineVariant else scheme.primary)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 필터·검색·탭
// ---------------------------------------------------------------------------

@Composable
fun ScopeChips(
    scopes: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = CodexHPad,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        scopes.forEach { key ->
            FilterChip(
                selected = key == selected,
                onClick = { onSelect(key) },
                label = { Text(scopeLabel(key)) },
            )
        }
    }
}

@Composable
fun FilterLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun CodexSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = if (showIcon) {
            { Icon(Icons.Default.Search, contentDescription = null) }
        } else {
            null
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "지우기")
                }
            }
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun CodexSubTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    TabRow(
        selectedTabIndex = selected.coerceIn(0, titles.lastIndex),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = index == selected,
                onClick = { onSelect(index) },
                text = { Text(title, style = MaterialTheme.typography.titleSmall) },
            )
        }
    }
}

/** 화면 위 고지 한 줄(데이터 한계 안내). */
@Composable
fun NoticeLine(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(scheme.secondaryContainer)
            .padding(horizontal = CodexHPad, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSecondaryContainer)
    }
}

// ---------------------------------------------------------------------------
// 덱 연결
// ---------------------------------------------------------------------------

@Composable
fun DeckLinkRow(link: DeckLink, onOpenDeck: ((String) -> Unit)?, caption: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onOpenDeck != null) Modifier.clickable { onOpenDeck(link.id) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (link.tier.isNotBlank()) {
            GradeBadge(link.tier)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(link.name, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!caption.isNullOrBlank()) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onOpenDeck != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

/** "이 챔피언 덱 N개" 섹션. 누르면 덱 상세로 간다. */
@Composable
fun DeckLinkSection(title: String, links: List<DeckLink>, emptyText: String, onOpenDeck: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("$title ${links.size}개")
        if (links.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        links.forEach { DeckLinkRow(it, onOpenDeck) }
    }
}

// ---------------------------------------------------------------------------
// 상태
// ---------------------------------------------------------------------------

@Composable
fun CodexLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** 목록 안에서 파생 목록을 계산하는 동안. */
@Composable
fun ListLoading() {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun CodexMissing() = EmptyState(title = "도감 데이터가 아직 없습니다", detail = "설정에서 갱신해 주세요.")

@Composable
fun CodexNotFound(what: String) =
    EmptyState(title = "${what}을(를) 찾을 수 없습니다", detail = "도감 데이터가 갱신되며 빠졌을 수 있습니다.")

// ---------------------------------------------------------------------------
// 표기 도우미
// ---------------------------------------------------------------------------

/** "4.18등". 값이 없으면 "-". */
internal fun rankText(avg: Double?): String = avg?.takeIf { it.isFinite() }?.let { "${formatAvg(it)}등" } ?: "-"

/** 스코프 키 맵을 화면 순서대로(모르는 키는 뒤에). null 값은 뺀다. */
internal fun <T> orderedEntries(map: Map<String, T?>): List<Pair<String, T>> {
    val known = StatScope.ORDER.mapNotNull { key -> map[key]?.let { key to it } }
    val extra = map.entries
        .filter { it.key !in StatScope.ORDER }
        .sortedBy { it.key }
        .mapNotNull { entry -> entry.value?.let { entry.key to it } }
    return known + extra
}
