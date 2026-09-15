package com.tftdeck.reader.ui.screens

import androidx.compose.runtime.Composable
import com.tftdeck.reader.ui.components.EmptyState

/**
 * 도감 자리 표시(임시).
 *
 * 도감 화면은 다른 작업 묶음(WP-4)이 만든다. 탭과 라우트를 먼저 깔아 두고, 통합 단계에서
 * MainActivity 의 codex 라우트 다섯 곳을 아래 composable 로 바꾼다.
 *
 *   codex                -> CodexScreen(viewModel: CodexViewModel, onOpenChampion, onOpenTrait, onOpenItem, onOpenAugment)
 *   codex/champion/{id}  -> ChampionDetailScreen(id, viewModel, onOpenDeck, onOpenItem)
 *   codex/trait/{id}     -> TraitDetailScreen(id, viewModel, onOpenDeck, onOpenChampion)
 *   codex/item/{id}      -> ItemDetailScreen(id, viewModel, onOpenDeck, onOpenChampion)
 *   codex/augment/{id}   -> AugmentDetailScreen(id, viewModel, onOpenDeck)
 *
 * [kind] 는 "codex"(탭 첫 화면) 또는 "champion" / "trait" / "item" / "augment".
 */
@Composable
fun CodexPlaceholder(kind: String, id: String?) {
    val subject = when (kind) {
        "champion" -> "챔피언"
        "trait" -> "특성"
        "item" -> "아이템"
        "augment" -> "증강체"
        else -> null
    }
    if (subject == null || id == null) {
        EmptyState(
            title = "도감을 준비하고 있습니다",
            detail = "챔피언·특성·아이템·증강체 통계가 곧 이 탭에 들어옵니다. " +
                "지금은 검색 탭에서 이름으로 덱을 찾을 수 있습니다.",
        )
    } else {
        EmptyState(
            title = "$subject 도감을 준비하고 있습니다",
            detail = "선택한 항목($id)의 통계 화면이 들어오면 이 자리에서 바로 열립니다.",
        )
    }
}
