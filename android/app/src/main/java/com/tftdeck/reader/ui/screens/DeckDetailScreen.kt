package com.tftdeck.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.data.Placement
import com.tftdeck.reader.ui.AppViewModel
import com.tftdeck.reader.ui.components.BoardSlot
import com.tftdeck.reader.ui.components.EmptyState
import com.tftdeck.reader.ui.components.HexBoard
import com.tftdeck.reader.ui.components.ItemIcons
import com.tftdeck.reader.ui.components.OnlyInChinaBadge
import com.tftdeck.reader.ui.components.TierBadge
import com.tftdeck.reader.ui.components.TraitChip
import com.tftdeck.reader.ui.copyToClipboard
import com.tftdeck.reader.ui.costColor
import com.tftdeck.reader.ui.iconUrl

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeckDetailScreen(
    deckId: String,
    viewModel: AppViewModel,
    overlayRunning: Boolean,
    onStartOverlay: (String) -> kotlin.Unit,
) {
    val deck = viewModel.deck(deckId)
    val assetBase by viewModel.assetBase.collectAsState()
    val pinned by viewModel.pinnedDeckId.collectAsState()
    val showingThisDeck = overlayRunning && pinned == deckId
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    if (deck == null) {
        EmptyState("덱을 찾을 수 없습니다", "목록에서 다시 선택해 주세요.")
        return
    }

    // "final"은 최종 배치(units), 나머지는 레벨별 배치(boards).
    var boardKey by remember(deckId) { mutableStateOf(FINAL) }
    var showCn by remember(deckId) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // --- 머리말 ---------------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TierBadge(deck.tier)
                Spacer(Modifier.width(7.dp))
                deck.finalLevel?.let {
                    Text(
                        "${it}레벨 완성",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (deck.metatft.onlyInChina) OnlyInChinaBadge()
            }
            Text(deck.name, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                deck.traits.forEach { TraitChip(it, assetBase) }
            }
        }

        // --- 덱 코드 / 오버레이 ----------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            deck.teamCode?.let { code ->
                Button(
                    onClick = { copyToClipboard(context, "TFT 덱 코드", code.code) },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 10.dp,
                    ),
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("덱 코드 복사")
                }
            }
            OutlinedButton(
                onClick = {
                    viewModel.pinDeck(deck.id)
                    onStartOverlay(deck.id)
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (showingThisDeck) scheme.primary else scheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Default.PictureInPictureAlt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                // 고정해 둔 덱인지가 아니라 실제로 떠 있는지로 라벨을 정해야 헷갈리지 않는다.
                Text(if (showingThisDeck) "띄우는 중" else "게임 위에 띄우기")
            }
        }

        deck.teamCode?.takeIf { it.isPartial }?.let { code ->
            val omitted = code.omitted.orEmpty()
            Text(
                buildString {
                    append("덱 코드에는 상점에서 뽑는 유닛만 담깁니다.")
                    if (omitted.isNotEmpty()) append(" 제외: ${omitted.joinToString(", ")}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // --- 배치 -----------------------------------------------------------
        Section("배치") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = boardKey == FINAL,
                    onClick = { boardKey = FINAL },
                    label = { Text("최종") },
                )
                deck.boardLevels.forEach { level ->
                    FilterChip(
                        selected = boardKey == level,
                        onClick = { boardKey = level },
                        label = { Text("${level}렙") },
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            HexBoard(slots = deck.slotsFor(boardKey, viewModel), assetBase = assetBase)
        }

        // --- 아이템 ----------------------------------------------------------
        Section("아이템 배분") {
            deck.units.filter { it.items.isNotEmpty() || it.itemsBackup.isNotEmpty() }.forEach { unit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = iconUrl(assetBase, unit.icon),
                            contentDescription = unit.name,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .border(1.5.dp, costColor(unit.cost), RoundedCornerShape(5.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            unit.name + if (unit.star >= 3) " ★3" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (unit.carry) scheme.primary else scheme.onSurface,
                            fontWeight = if (unit.carry) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        ItemIcons(unit.items, assetBase, size = 24)
                    }
                    Text(
                        unit.items.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 38.dp),
                    )
                    if (unit.itemsBackup.isNotEmpty()) {
                        Text(
                            "대체 " + unit.itemsBackup.joinToString(" · ") { it.name },
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 38.dp),
                        )
                    }
                }
            }
        }

        if (deck.itemOrder.isNotEmpty()) {
            Section("조합 재료 우선순위") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    deck.itemOrder.forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.surfaceVariant)
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.primary,
                            )
                            Spacer(Modifier.width(5.dp))
                            AsyncImage(
                                model = iconUrl(assetBase, item.icon),
                                contentDescription = item.name,
                                modifier = Modifier.size(19.dp).clip(RoundedCornerShape(3.dp)),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                item.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // --- 증강체 ----------------------------------------------------------
        if (deck.augments.recommended.isNotEmpty()) {
            Section("증강체") {
                AugmentRow("추천", deck.augments.recommended.map { it.name }, scheme.primary)
                if (deck.augments.alternatives.isNotEmpty()) {
                    Spacer(Modifier.size(6.dp))
                    AugmentRow("차선", deck.augments.alternatives.map { it.name }, scheme.onSurfaceVariant)
                }
            }
        }

        // --- metatft 대조 -----------------------------------------------------
        if (deck.metatft.compared) {
            Section("metatft 대조") {
                Text(
                    if (deck.metatft.onlyInChina) {
                        "metatft에 대응하는 덱이 없습니다 (최고 유사도 ${"%.0f".format(deck.metatft.similarity * 100)}%). 중국 서버에서만 쓰이는 구성일 수 있습니다."
                    } else {
                        "metatft에도 같은 구성이 있습니다 (유사도 ${"%.0f".format(deck.metatft.similarity * 100)}%)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        // --- 작성자 원문 -------------------------------------------------------
        if (!deck.notesCn.isEmpty || deck.nameCn.isNotBlank()) {
            Section("작성자 원문 (중국어)") {
                Text(
                    if (showCn) "접기" else "펼치기",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    modifier = Modifier.clickable { showCn = !showCn },
                )
                AnimatedVisibility(showCn) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (deck.nameCn.isNotBlank()) {
                            Text(deck.nameCn, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        listOf(deck.notesCn.items, deck.notesCn.augments)
                            .filter { it.isNotBlank() }
                            .forEach { note ->
                                Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                        if (deck.author.isNotBlank()) {
                            Text(
                                "작성 ${deck.author}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> kotlin.Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugmentRow(label: String, names: List<String>, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.width(30.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            names.forEach { name ->
                AssistChip(onClick = {}, label = { Text(name, style = MaterialTheme.typography.labelSmall) })
            }
        }
    }
}

private const val FINAL = "final"

/** 최종 배치는 units, 레벨 탭은 boards의 참조를 catalog로 풀어서 만든다. */
private fun Deck.slotsFor(key: String, viewModel: AppViewModel): List<BoardSlot> {
    if (key == FINAL) {
        return units.mapNotNull { unit ->
            val row = unit.row ?: return@mapNotNull null
            val col = unit.col ?: return@mapNotNull null
            BoardSlot(row, col, unit.name, unit.icon, unit.cost, unit.star, unit.carry)
        }
    }
    return boards[key].orEmpty().mapNotNull { placement -> placement.toSlot(viewModel) }
}

private fun Placement.toSlot(viewModel: AppViewModel): BoardSlot? {
    val r = row ?: return null
    val c = col ?: return null
    val champion = viewModel.catalogChampion(id)
    return BoardSlot(
        row = r,
        col = c,
        name = champion?.name ?: id,
        icon = champion?.icon,
        cost = champion?.cost,
        star = star,
        carry = carry,
    )
}
