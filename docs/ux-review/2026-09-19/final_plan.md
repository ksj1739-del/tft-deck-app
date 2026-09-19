# FloaTFT UI/UX 적대 검토 — 최종 판정과 이번 릴리스 계획

- 기준 코드: main 754d53c. 데이터: `android/app/src/main/assets/decks.json` (덱 77 = metatft 54 + 중국 한정 23).
- 검토 보고서 8편: overlay_flow(F1~F14) · overlay_readability(R1~R13) · overlay_search(Q1~Q16) · overlay_state(S1~S14) · app_list_card(L1~L16) · app_detail(D1~D18) · app_visual(V1~V30, 규칙 제안 R1~R10) · app_flows(N1~N20). 발견 141건.
- 검증 방법: 치명·높음 전부를 코드 줄과 스크린샷으로 확인했고, 중간 이하는 표본 검사했다. 데이터 주장(별칭·등급 분포·운영 어휘·표본)은 decks.json 을 직접 집계해 다시 셌다.
- 뒤집지 않은 사용자 결정: metatft 주 목록 + lol.qq 중국 한정 덱, 목적별 별칭, 오버레이 다중 선택 검색, 중국 한정 덱 반 S 반 A, 그래파이트 + 파랑 포인트 하나, 오버레이 우선, 오버레이 설명 최대 2줄, 등급 다중 선택(C·D 기본 해제), 조회 조건 저장 + 늘 보이는 '조건 초기화', 첫 실행 안내 1회, 카드 표본 줄 제거, metatft 고정 컷(S<4.25 A<4.5 B<4.75 C<5.0).

---

## 1. 판정 요약

### 1.1 수

| 보고서 | 발견 | 채택 | 보류(다음 릴리스) | 사용자 결정 대기 | 통째 기각 |
|---|---|---|---|---|---|
| overlay_flow F | 14 | 13 | 1 (F5) | 0 | 0 |
| overlay_readability R | 13 | 13 | 0 | 0 | 0 |
| overlay_search Q | 16 | 15 | 1 (Q12) | 0 | 0 |
| overlay_state S | 14 | 9 | 5 (S6·S9·S10·S11·S13) | 0 | 0 |
| app_list_card L | 16 | 15 | 0 | 1 (L4) | 0 |
| app_detail D | 18 | 18 | 0 | 0 | 0 |
| app_visual V | 30 | 28 | 1 (V22) | 1 (V23, 결정 1에 연동) | 0 |
| app_flows N | 20 | 19 | 1 (N9) | 0 | 0 |
| **합계** | **141** | **130** | **9** | **2** | **0** |

- 발견 자체가 틀린 것은 없었다. 치명·높음 41건(F5 R5 Q2 S4 L4 D7 V8 N6)은 전부 코드 줄 또는 스크린샷과 일치했다(예: R1 별칭 어긋남은 보고된 7개보다 많은 10개, R5 가로 목록은 화면 높이 약 85%, L8 목록 위 조작부 309dp, D3 meta 덱 finalLevel 9 는 lol.qq 대표 덱에서 복사된 값).
- 다만 **고칠 방법 가운데 13개는 일부 기각**했다(1.2). 사용자 결정과 충돌하거나, 규모 대비 효과가 낮거나, 다른 전역 규칙과 부딪히는 것들이다.
- '채택'이라도 이번 릴리스 범위 밖으로 넘긴 세부 항목은 4절에 적었다.

### 1.2 일부 기각한 제안과 이유

| 발견 | 기각한 제안 | 이유 | 대신 채택한 것 |
|---|---|---|---|
| L1 | 목록을 metatft 구역과 중국 한정 구역으로 나누어 표시 | 사용자 결정(중국 한정 덱을 같은 목록에서 반 S 반 A 로 섞음)과 충돌 | 같은 배지 크기의 테두리형(채움 없는) 등급 배지 + '중국' 글자 배지 (R2·L1·V5 통합) |
| V7 | 등급 S~D 칸을 필터 시트 안으로 숨김 | 사용자 결정(등급 다중 선택은 늘 보이는 조회 조건) 위반 | 칸은 유지하되 테두리 상자를 없애고 두 줄 조작부로 압축 (A2) |
| R2 | 중국 한정 덱 배지에 '中' 9sp 덧글자 + 길게 눌러 범례 | 9sp 는 새 오버레이 최소 글자(본문 11sp, 표식 10sp) 아래이고, 길게 누르기는 게임 중 발견 불가 | 테두리형 배지 + '중국' 10sp 글자 배지 (O4) |
| L7 / R10 | '중국 한정'·캐리 얼굴에 금색(Gold) 테두리 | 색 뜻 규칙(V10: Gold = 1등·3성 하나) 충돌 | 중국 한정은 글자 배지, 캐리는 크기(40dp vs 20~26dp)와 순서로 구분 |
| R6 | 같은 보드 덱 묶음(7쌍)을 하나로 합침 | 같은 보드라도 구간별 통계·출처가 다른 별개 행이고, 합치면 metatft 기준 목록이 아님 | 별칭 중복 해소 규칙(둘째 캐리 → 운영 → 번호)과 행에서 접미사 제거 (C1·O4) |
| F8 / Q9 | 구간·등급·'모두 지우기'에 '되돌리기' 토스트 | 게임 화면 위에 토스트를 더 띄우면 오조작 표면이 늘고, 시스템 토스트는 오버레이 위치와 무관하게 뜸 | 구간은 메뉴 안으로(O1), ⊗는 입력 글자부터 지우고 12dp 띄움(O2) |
| F1 | 칩을 화면 가장자리에 자석처럼 붙임(edge snapping) | 사용자가 둔 자리를 바꾸는 동작이라 예측 가능성이 떨어지고 S13(뒤로 제스처 영역) 을 키움 | 칩 자리에서 화면 중앙 쪽으로 자라는 배치 + '접기'를 칩 자리에 고정 (O1) |
| F6 | 펼친 직후 300ms 동안 누름 무시 | 게임 중 지연은 그 자체가 오조작 원인. 원인(머리줄이 맨 위로 튐)을 없애면 불필요 | 가로 목록 최대 높이 200dp + 앵커 기준 배치 (O1·O4) |
| V28 | 오버레이 뒤 배경 블러(RenderEffect) | API 31 미만 미지원, 게임 GPU 부하, 실기기 검증 불가 | 불투명 100% (O4) |
| S2-3 | 빠른 설정(Quick Settings) 타일 | 규모 M, 실기기 검증 필요. 알림 3상태 + '지금 보이기'로 충분 | 알림 개편 (O1) — 타일은 다음 릴리스 |
| R8 | 'TOP4' 를 '순방'으로 바꿈 | '순방'은 커뮤니티 은어, 앱 전체 용어 규칙(TOP4 유지)과 충돌 | 구간 이름만 고침('골드 이하' → '실버 이하') |
| N13 | 목록 '촘촘히 보기' 밀도 전환 | 카드 개편(A2)으로 첫 화면에 3장이 들어오면 필요성이 줄고, 설정 항목 하나가 늘어남 | 카드 182dp 로 압축 + 필터 접근 개선 (A2); 밀도 전환은 다음 릴리스 |
| Q6-1 | 자모 분해 접두 매칭 | 규모 M, DeckSearch 검색 경로 전체에 걸림. 6-2·6-3 으로 되튐 대부분이 사라짐 | 사용자 지정만 남으면 직전 후보 유지 + hintLocales ko-KR (O2) |

### 1.3 중복 통합

| 묶음 | 통합한 발견 | 담당 |
|---|---|---|
| 별칭 오류·중복 | R1 · L13 · R6(접미사) · D14 | WP-C1 |
| 두 등급 체계 표시 | R2 · L1 · V5 | WP-P2(배지 컴포넌트) → O4 · A2 · A3 적용 |
| 검색 끝내기 | Q1 · Q2 · F12 · Q11 | WP-O2 |
| 닫은 뒤 되살리기 | S1 · S2 · S5 · F7 · N8 | WP-O1 (N8 은 A4) |
| 출처 줄·표본 줄 | D1 · D8 · V1 · V2 · N14 · L3 | WP-A2 · A3 |
| 머리줄 정리 | F2 · F3 · F8 | WP-O1 |
| 카드 배경 토큰 | L6 · V9 | WP-P2(Theme) → A2 |
| 글자 크기 체계 | V4 · R3 · L15 | WP-P2 → O4 |
| 상세 구간 칩이 전역을 바꿈 | D5 · N12 | WP-A3 |
| 레벨 정보 불일치 | D3 · R7 · D9 | WP-C1 + A3 |
| 이미지 자리 표시 | L12 · V16 | WP-P2(Components) |
| 용어 통일 | V12 · N19 · D17 | WP-T |
| 티어 카드 기본값 | R9 · F10 | WP-O1 |
| 검색 탭 중복 | N10 · V23 | 사용자 결정 1 |
| 첫 실행 안내 | N1 · N2 · V27 | WP-A4 |
| 게임 연동 흐름 | N6 · N7 · S3 | WP-A4 |

---

## 2. 이번 릴리스 작업 묶음

### 2.0 진행 순서와 파일 소유

```
Wave 0 (병렬 3)  WP-P1 오버레이 파일 분할 | WP-P2 디자인 토큰·공용 컴포넌트 | WP-C1 수집기·데이터
   └ 세 묶음 모두 main 에 합친 뒤 (P1 → P2 순으로 합치면 충돌 없음; C1 은 collector/·data/·assets 만 건드림)
Wave 1 (병렬 6)  WP-O1 오버레이 창·머리줄·알림 | WP-O2 오버레이 검색 | WP-O4 오버레이 목록·요약·글자
                  WP-A2 덱 목록·카드 | WP-A3 덱 상세 | WP-A4 첫 실행·설정·연동·동기화
Wave 2 (단독 1)  WP-T 용어·문구 일괄 치환 (Wave 1 전부 합친 뒤)
```

- 각 WP 는 git worktree 하나에서 작업하고, 아래 '수정 파일'에 적힌 파일만 만진다. 같은 wave 안에서 파일이 겹치는 WP 는 없다.
- 공용 파일 소유: `Theme.kt`·`Components.kt`·`StatChips.kt`·`UiUtils.kt`·`MASTER.md` → P2. `Models.kt` → T (Wave 1 은 Models.kt 를 고치지 않고, 필요한 계산은 각 WP 파일 안의 private 함수로 둔다). `DeckSearch.kt`·`TokenSearch.kt`·`OverlayListFilter.kt` → O2. `OverlayContent.kt`(분할 뒤 루트만) → O1. `strings.xml` → O1 (O1 외 Wave 1 WP 는 새 문구를 코드 리터럴로 넣고 T 가 strings.xml 로 옮긴다).
- 시그니처 고정: P1 이 뽑은 컴포저블·클래스와 P2 의 공용 컴포넌트, 그리고 `StatsRow`·`OverlayRootView`·`rememberOverlaySearchState`·`DeckListView`·`DeckSummaryView` 처럼 다른 WP 파일이 호출하는 함수의 시그니처는 Wave 1 동안 바꾸지 않는다. 매개변수를 더할 때는 기본값을 반드시 두고, 삭제·이름 변경은 하지 않는다(대신 `@Deprecated` 로 두고 T 가 지운다). 시그니처를 꼭 바꿔야 하면 T 로 미룬다. `AppViewModel.kt`·`DeckRepository.kt` → A2 (A4 는 새 ViewModel 메서드가 필요하면 `SetupViewModel.kt` 를 새로 만들고, 동기화 상태는 기존 `FeedState`(`lastSyncedAt`·`fromBundle`) 로만 읽는다). `DailySyncWorker.kt`·`TftApp.kt`·`MainActivity.kt`·`AndroidManifest.xml` → A4. `OverlayService.kt`·`OverlayMemory.kt` → O1. `ProfileCard.kt` → O4.
- 분할 뒤 오버레이 파일 소유: `OverlayContent.kt`(루트)·`OverlayHeader.kt`·`OverlayChip.kt`·`OverlayMenu.kt`(신규)·`OverlayPlacement.kt` → O1. `OverlaySearchBar.kt`·`OverlayRootView.kt` → O2. `OverlayDeckList.kt`·`OverlaySummary.kt`·`OverlayFaces.kt`·`OverlayTheme.kt` → O4.
- 설계 문서 `docs/benchmark/2026-09-15/design.md` 는 코드와 함께 바꾼다: §6.3(상세 순서) → A3, §7(오버레이) → O1·O4, §13.3/§13.4(빌드업 표기) → C1·A3. 같은 파일을 셋이 건드리므로 각 WP 는 자기 절만 고치고, 합칠 때 절 단위로 충돌을 푼다.
- 수용 기준의 '에뮬레이터'는 Pixel 7 API 34 기준(가로 2400×1080, 세로 동일)이며, 기존 스크린샷 폴더(`scratchpad/ovl/shots`, `shots_app`, `shots_firstrun`, `shots_query`)와 같은 장면을 다시 찍어 비교한다.
- 규모: S = 반나절 이하, M = 하루, L = 이틀 이상(에이전트 1개 기준).

### WP-P1 오버레이 파일 분할 (Wave 0) — 규모 M

- **목표**: 1,299줄 `OverlayContent.kt` 와 `OverlayWindow.kt` 를 책임별 파일로 나누어 Wave 1 의 O1·O2·O4 가 서로 다른 파일을 만지게 한다. **동작 변화 0.**
- **포함 id**: 없음(준비 작업).
- **수정 파일**: `overlay/OverlayContent.kt`(루트 컴포저블만 남김), 신규 `overlay/OverlayChip.kt`, `overlay/OverlayHeader.kt`, `overlay/OverlaySearchBar.kt`, `overlay/OverlayDeckList.kt`, `overlay/OverlaySummary.kt`, `overlay/OverlayFaces.kt`, `overlay/OverlayTheme.kt`; `overlay/OverlayWindow.kt` → `overlay/OverlayPlacement.kt` + `overlay/OverlayRootView.kt`(OverlayWindow.kt 삭제). 테스트 파일의 import 만 수정.
- **명세**:
  - `OverlayContent.kt`: `OverlayContent(...)` 루트와 상태 연결만. 157~239줄의 검색 상태 블록은 `OverlaySearchBar.kt` 의 `rememberOverlaySearchState(search, listed, decksShown, searching, onSearchEnd, onListAnchor): OverlaySearchState` 로 뽑는다(질의·토큰·후보·pickCandidate·onSubmit 을 담는 class).
  - `OverlayChip.kt`: `CollapsedChip`. `OverlayHeader.kt`: `PanelHeader`, `CloseButton`, `HEADER_ALIAS_MIN_WIDTH`. `OverlaySearchBar.kt`: `OverlaySearchBar`, `GradeToggles`, `CandidateList`. `OverlayDeckList.kt`: `DeckListView`, `DeckRow`, `ChinaDot`, `DECK_LIST_MAX`. `OverlaySummary.kt`: `DeckSummaryView`, `LevelChips`. `OverlayFaces.kt`: 얼굴 FlowRow·아이템 줄. `OverlayTheme.kt`: `OverlayScrim`, `PANEL_SCREEN_MARGIN`, `gradeTint`, 오버레이 전용 글자 상수(현재 값 그대로).
  - `OverlayPlacement.kt`: `overlayWindowFlags`, `clampOverlayPosition`, `liftAboveIme`, `overlayPositionKeys`, `hideHeaderWhileSearching`. `OverlayRootView.kt`: `OverlayRootView`.
  - 함수 시그니처·가시성·`internal` 은 유지. `OverlayService.kt` 는 import 만 바뀐다.
- **수용 기준**: `./gradlew :app:testDebugUnitTest` 전부 통과(OverlayPlacementTest·OverlayWindowFlagsTest 는 import 만 바꿈). 에뮬레이터에서 접힘/목록/요약/검색/넓게 5장면 스크린샷이 분할 전과 픽셀 단위로 같다. `git diff --stat` 에 동작 코드 변경 없음(이동만).

### WP-P2 디자인 토큰·공용 컴포넌트 (Wave 0) — 규모 M

- **목표**: 글자 6단계, 등급 배지 한 모양, 선택 표시 한 모양, 숫자·구간 표기 함수, 색 뜻 정리를 토큰과 공용 컴포넌트에 넣어 Wave 1 이 가져다 쓰게 한다. MASTER.md 를 확정본(5절)으로 바꾼다.
- **포함 id**: V4, V5, V6, V10, V11, V13, V14, V15, L6/V9(토큰), L12/V16(자리 표시), L14, L15, R8(구간 이름), V18(칩 대비), D16(섹션 제목 컴포넌트).
- **수정 파일**: `ui/theme/Theme.kt`, `ui/components/Components.kt`, `ui/components/StatChips.kt`(공용 함수만; `StatsRow` 재구성은 A2), `ui/UiUtils.kt`, `design-system/floatft/MASTER.md`, 신규 단위 테스트 `UiUtilsFormatTest.kt`.
- **명세**:
  - Theme 글자(현재 10.5/11.5/12/13/13.5/14/15/16/21 → 6단계): `labelSmall` 11sp Medium lh14, `labelMedium` 12sp Medium lh16, `labelLarge` 14sp Medium lh20, `bodySmall` 12sp lh18, `bodyMedium` 14sp lh20, `bodyLarge` 16sp lh24, `titleSmall` 14sp SemiBold lh20, `titleMedium` 16sp SemiBold lh22, `titleLarge` 20sp Bold lh26, `headlineSmall` 20sp Bold, `displaySmall` 28sp Bold lh34. 모든 스타일에 `fontFeatureSettings = "tnum"`. `LineBreak.Paragraph` 대신 `LineBreak(strategy = Simple, strictness = Normal, wordBreak = Phrase)` 를 bodySmall·bodyMedium·titleMedium 에 적용(V15; API 33 미만은 기본 동작).
  - 색: `surface` = `SurfaceContainer`(카드 면, `#1B1F26` 계열로 Background 보다 한 단계 밝게), `surfaceContainerHighest` 는 그보다 한 단계 더 밝게. `TierS` `#F472B6`(Negative 와 구분), `TierC` `#A3E635`(Positive `#4ADE80` 와 구분), 나머지 등급색 유지. `KrCyan #22D3EE` 삭제(사용처는 A2·A3 에서 없앰; P2 는 상수를 `@Deprecated` 로 남겨 Wave 1 이 끝나면 T 가 지운다). `Shapes.extraLarge` 24 → 16dp.
  - `GradeBadge(grade: String, style: GradeBadgeStyle = Filled, modifier)`: 높이 20dp, 최소 폭 20dp, 모서리 6dp, 좌우 6dp, 글자 11sp Bold. `Filled` = `gradeColor(grade)` 채움 + `Background` 색 글자. `Outlined` = 1.5dp `gradeColor` 테두리 + 같은 색 글자, 채움 없음(중국 한정 덱). `Editorial` = Outlined + 오른쪽에 `TextBadge("편")`. 기존 `TierBadge` 는 이 함수로 위임하고 `@Deprecated`.
  - `TextBadge(text, emphasis: Boolean = false)`: 높이 20dp, 모서리 6dp, `surfaceVariant` 채움, `onSurfaceVariant` 11sp Medium(emphasis 면 `primary` 글자). `OnlyInChinaBadge`·`OutlineBadge` 는 여기로 위임.
  - `SectionTitle(text, trailing: @Composable? = null)`: `titleSmall` `onSurface`, 위 24dp 아래 8dp. 파랑(primary) 제목 금지.
  - `TraitChip`: `surfaceVariant` 채움 + `onSurface` 12sp 글자, 앞에 특성 아이콘 16dp(색 채움·색 글자 제거, V18·R13).
  - `FloaFilterChip(selected, label, onClick, leading)`: 높이 32dp, 모서리 8dp, 미선택 = `surfaceVariant` 채움·테두리 없음·`onSurfaceVariant` 글자, 선택 = `secondaryContainer`/`onSecondaryContainer`·테두리 없음. 취소선·체크 아이콘 없음. `BucketChips` 는 이 칩으로 바꾼다.
  - `UnitPortrait`: 이미지 아래에 항상 `InitialMark`(첫 글자 + 비용색 바탕)를 깔고 이미지가 오면 덮는다. `ItemIcons`: 아이템 자리마다 1dp `outlineVariant` 빈 상자를 깔고 이미지가 오면 덮는다(L12/V16).
  - `UiUtils`: `bucketLabel("low")` → `'실버 이하'`; 신규 `bucketShortLabel` = 전체/마스터+/다이아+/골드~에메/실버 이하; `formatPct(v)` 소수 1자리, 0.1% 미만은 `'<0.1%'`; `formatAvg(v)` 소수 2자리, `formatAvgRank(v)` = `'4.12등'`; 신규 `formatGames(n)` = 1만 미만 `'9,847판'`, 1만~100만 `'58.5만 판'`, 100만 이상 `'108만 판'`; `formatDelta(v)` 음수 부호 U+2212, 항상 ▲/▼ 와 짝; `formatPick` 은 `formatPct` 로 위임(L14). `tierColor` 의 한 칸 밀린 매핑(S→TierA) 제거.
  - MASTER.md: 5절 확정본으로 교체(토큰 표·크기·배지·선택·숫자·용어·여백·문장). 덱 카드 '4수치 고정' 조항은 "L4 결정 보류" 표시.
- **수용 기준**: `UiUtilsFormatTest`(formatPct 경계 0.04/0.1/12.345, formatGames 9847/584959/1080000, formatDelta −0.13, bucketLabel low) 통과. 앱이 빌드되고 기존 화면이 새 토큰으로 깨지지 않음(글자 크기 바뀐 화면은 Wave 1 이 정리하므로 P2 단계에서는 잘림·겹침만 없으면 됨). `grep -rn "\.sp" ui/ overlay/` 결과에 Theme.kt·OverlayTheme.kt 밖 리터럴이 늘지 않음.

### WP-C1 수집기·데이터 (Wave 0) — 규모 M (수집·검증 재실행 포함)

- **목표**: 별칭이 보드·캐리와 어긋나는 10개를 없애고 별칭 중복 규칙을 고정하며, 설명의 운영 어휘를 한 체계로 통일하고, meta 덱 마무리 레벨을 metatft 값으로 바꾸고, 구간 이름을 고친다. `verify.py` 가 이것을 계속 지키게 한다.
- **포함 id**: R1, L13, R6(접미사·중복), R7, D3(데이터 쪽), D9(데이터 쪽), R8(수집기 라벨), D14(데이터 쪽 별칭), D12(작가 증강 fallback 표시 데이터).
- **수정 파일**: `collector/fetch_decks.py`, `collector/metatft_comps.py`, `collector/deck_merge.py`, `collector/verify.py`, `data/**`(수집 결과), `android/app/src/main/assets/decks.json`, 단위 테스트 중 '골드 이하' 문자열을 가진 것(`MetaDeckTest`·`OverlayListFilterTest` 등 grep 으로 찾음), `docs/benchmark/2026-09-15/design.md` §13.3·§13.4.
- **명세**:
  - 별칭 규칙(`assign_aliases`, 1305~1416): 형식은 `'{대표 특성} {1순위 캐리}'`. 대표 특성 = metatft `nameParts` 의 특성이 덱 `traits` 에 있으면 그것, 아니면 `traits` 중 활성 단계가 가장 높은 것. 1순위 캐리 = `ranked_carries` 첫째. `nameParts` 의 유닛명은 그 유닛이 캐리 상위 2 안에 있을 때만 쓴다(아니면 버림). 캐리가 보드에 없으면 다음 순위로 내려간다.
  - 중복 해소 순서: 같은 별칭이 둘 이상이면 (1) 둘째 캐리를 덧붙임 `'{특성} {캐리1}·{캐리2}'`, (2) 그래도 겹치면 운영 짧은말 `' · 빠른 9레벨'`/`' · 6레벨 리롤'`/`' · 최종 N레벨'`, (3) 그래도 겹치면 번호 `' 2'`. 기존 `' · {운영 짧게}'` 우선 규칙과 `candidates` 3단은 이 순서로 교체.
  - 운영 어휘(`operation_text`, `operation_short`, `deck_summary`): 허용 집합 {`빠른 8레벨`, `빠른 9레벨`, `N레벨 리롤`, `표준 운영`, `최종 N레벨`}. metatft `levelling` 의 `Standard` → `표준 운영`(현 `'표준'`), `Fast 8/9` → `빠른 8/9레벨`, `Slow roll N`/`Reroll N` → `N레벨 리롤`. 중국 덱: `rollLevel ≤ 7` → `'N레벨 리롤'`; `rollLevel == 8` 이고 8레벨 도달 라운드가 4-1 이전이면 `'빠른 8레벨'`; 그 밖은 `'최종 N레벨'`. `'N레벨 완성'` 은 금지 어휘.
  - 요약 첫 문장 = `'{운영} · {주 특성1} {n}'` 형식, 둘째 문장(있으면) = 캐리 아이템 한 줄. 두 줄 초과 금지(글자 수 ≤ 44).
  - meta 덱 `finalLevel`: `dict(rep)` 복사(1133) 대신 metatft `finalLevels` 에서 점유율 최대 레벨(동률이면 낮은 쪽). `finalLevels` 가 없으면 `null`. group 덱(945)은 편집값 → `occ.num` 유지.
  - `metatft_comps.py` BUCKET_RANKS `low` 주석과 `deck_merge.py` BUCKETS 의 `'골드 이하'` → `'실버 이하'`. `UiUtils.bucketLabel` 은 P2 가 맞춘다.
  - `showsEditorialGrade`/editorial 덱: 편집 덱(예: 14317)이 현재 patch 의 어떤 출처에도 없으면 목록에서 제외(`list_order` 2114 앞에서 필터).
  - `verify.py`(179~189 확장) — 실패 조건: (a) 별칭 속 챔피언이 보드에 없음(두 낱말 이름 '불타는 묘목'·'장로 드래곤'·'마스터 이' 는 유닛 사전에서 가장 긴 이름부터 맞춤), (b) 별칭 첫 챔피언의 `carryRank > 2`, (c) 별칭 특성이 `traits` 에 없음, (d) 운영 어휘가 허용 집합 밖 또는 `'표준'`·`'N레벨 완성'` 포함, (e) `bucketLabel` 에 `'골드 이하'`, (f) 출처 없는 editorial 덱, (g) meta 덱 `finalLevel` 이 `finalLevels` 키에 없음, (h) 별칭·이름 중복, (i) 요약 44자 초과 또는 빈 값.
  - 실행: `python collector/fetch_decks.py --snapshot` 을 **라이브로 다시 수집**(캐시가 아니라 lol.qq·metatft 현재값)하고 `python collector/verify.py` 가 문제 0건이어야 한다. `data/` 와 `assets/decks.json` 을 함께 커밋한다. 수집 결과에서 덱 수·구간별 등급 분포(S·A 반반 규칙)를 커밋 메시지에 적는다.
- **수용 기준**: `verify.py` 0건. decks.json 을 다음 스크립트 기준으로 재집계했을 때 별칭 챔피언 불일치 0, 별칭 중복 0, 설명에 '표준'·'완성' 0, 기본 목록(goldem, S~B)에서 같은 요약 문장이 두 덱 이상인 경우 0. `./gradlew :app:testDebugUnitTest` 통과. 앱 목록에서 '주문술사 카직스' 류 특성 불일치 별칭이 없음(수집 뒤 목록 스크린샷 1장).

### WP-O1 오버레이 창 배치·머리줄·되살리기·알림 (Wave 1) — 규모 L

- **목표**: 게임 중 오조작을 만드는 세 원인(칩과 '접기' 자리가 다름, 드문 기능이 자주 누르는 자리 옆에 붙음, 머리줄이 맨 위로 튐)을 없애고, 닫은 창을 이번 판 안에서 되살릴 수 있게 하며, 알림이 창 상태를 정확히 말하게 한다.
- **포함 id**: F1, F2, F3, F6, F7, F8(b·c: 구간·등급을 메뉴로), F11, F13, F14, S1, S2(1·2), S4(4: 닫음 규칙 저장), S5, S7, S8, S14, R9/F10(티어 카드 기본 꺼짐 — 권장안으로 진행), Q7(머리줄 canFocus), Q16.
- **수정 파일**: `overlay/OverlayService.kt`, `overlay/OverlayContent.kt`(루트), `overlay/OverlayHeader.kt`, `overlay/OverlayChip.kt`, 신규 `overlay/OverlayMenu.kt`, `overlay/OverlayPlacement.kt`, `overlay/OverlayMemory.kt`, `res/values/strings.xml`, `OverlayPlacementTest.kt`·`OverlayMemoryTest.kt`(확장), `docs/benchmark/2026-09-15/design.md` §7(창·머리줄·알림 절만; 목록·요약 절은 O4).
- **명세**:
  - 머리줄(`PanelHeader`) 구성: `[접기 44×36][← (요약일 때만)][제목 weight(1f)][⋯ 44×36]`. 다섯 버튼(앱 열기·티어·넓게·복사·닫기)은 머리줄에서 빼고 `⋯` 메뉴로 옮긴다. 켜고 끔에 따라 버튼 줄이 움직이지 않는다(폭 고정). `CloseButton` 의 3초 대기 상태 삭제.
  - 제목: 목록일 때 `'덱 {n}'` + `bucketShortLabel` 을 `TextBadge` 로(예: `덱 12  골드~에메`), 요약일 때 `GradeBadge` + 별칭. `HEADER_ALIAS_MIN_WIDTH` 유지.
  - `⋯` 메뉴(`OverlayMenu.kt`): `Popup` 이 아니라 패널 안 `Box` 로 그린다(오버레이 창 위의 `Popup` 은 창 토큰 문제로 기기마다 다르게 동작하고, 새 창이 생기면 앵커 배치·터치 플래그 계산이 흔들린다). 항목(각 44dp 높이, 12sp 글자): `넓게 보기` 토글, `티어 카드` 토글, `코드 복사`(누르면 항목 글자가 1초간 `'복사됨 ✓'`; F14 — 시스템 클립보드 미리보기는 못 막으므로 문구로만 알림), `구간 ▸`(펼치면 5개 라디오, 선택 시 `DeckPrefs.setBucket`), `앱에서 열기`, `오버레이 닫기`. 메뉴 밖 누름·뒤로 → 메뉴 닫힘. 메뉴가 열려 있는 동안 목록 스크롤·검색 줄 누름은 먹지 않는다.
  - 배치(`OverlayPlacement.kt`): 신규 `expandedPlacement(anchor: IntOffset, chipSize: IntSize, panelSize: IntSize, area: IntRect, corner: Quadrant): IntOffset` — 칩이 있는 사분면을 보고 패널이 칩 자리에서 화면 중앙 쪽으로 자란다(왼쪽 위 칩 → 오른쪽 아래로, 오른쪽 아래 칩 → 왼쪽 위로). 머리줄은 칩과 같은 변(위 또는 아래)에 두고, `접기` 버튼은 칩 자리와 `|Δ| ≤ 4dp` 안에서 겹치도록 머리줄 좌우 순서를 사분면에 따라 거울로 뒤집는다. 크기가 바뀌어도(요약↔목록↔검색) 앵커 기준으로 다시 놓아 `접기` 자리는 고정. 드래그는 앵커를 옮기고 `moveBy`(661~668)는 앵커를 덮어쓰지 않고 더한다. `clampOverlayPosition` 은 `PANEL_SCREEN_MARGIN 8dp` 유지.
  - 칩(`OverlayChip.kt`): 누르면 마지막 상태(목록/요약)로 펼침, **길게 누르면 목록으로** 펼침. 칩 크기 유지(30dp 얼굴 + 9dp 패딩).
  - 숨김·복귀(F11, S8): `applyVisibility`(434~438)가 숨길 때 `expanded=false` 로 접는다. `detachOverlay`(653)만 접던 것을 옮김. '자동 표시' 끄면(S8) 자동으로 붙은 창은 떼고 직접 띄운 창만 남긴다(`autoAttached` 플래그를 `OverlayService` 에 두고 `attach` 경로별로 세팅).
  - 새 판(F13): `GameSession` 이 새 `Foreground.since` 를 주면 요약이 아니라 **접힌 칩**으로 시작하고 선택 덱은 유지(선택 해제는 하지 않음 — S9 는 다음 릴리스).
  - 티어 카드(R9/F10): `KEY_SHOW_PROFILE` 기본 `false`(131·161). 켠 사용자는 그대로.
  - 닫음 규칙(S1, S4-4, F7): `closeOverlay`(638) 는 `dismissedSince = Foreground.since`, `dismissedAt = now` 를 `OverlayMemory` 에 저장(프로세스 재시작에도 유지). 자동 붙임(425~426) 차단 조건 = `dismissedSince == 현재 since && now − dismissedAt < 40분 && 그 사이 Result 없음`. 40분 지나면 다시 붙이도록 `Handler.postDelayed` 로 재검사. 알림의 `지금 보이기` 는 `forceVisible` 로 이 규칙을 무시하고 즉시 붙인다.
  - 알림 3상태(S7, S2, S5): (1) 창 보임: 본문 `'덱 12 · 골드~에메 · 오버레이 표시 중'`, 동작 `[감추기][앱 열기]`; (2) 창 닫힘/숨김(감지 중): 본문 `'TFT 감지 중 · 오버레이 닫힘'`, 동작 `[지금 보이기][앱 열기]`; (3) 감지 꺼짐(직접 띄움): 본문 `'오버레이 켜짐'`, 동작 `[닫기][앱 열기]`. `'감지 끄기'` 는 `'감지 잠시 끄기'` 로 바꾸고 `ACTION_STOP_WATCH` 로 **서비스만 멈춘다**(`detectEnabled` 설정은 바꾸지 않음; 앱을 다시 열거나 재부팅하면 다시 감지). `contentIntent` 는 창이 없을 때 `ACTION_SHOW`, 있을 때 MainActivity. `updateNotification()` 을 `applyVisibility`·`observeDecks`·`closeOverlay` 에서 호출해 문구를 상태와 맞춘다.
  - 회전(Q16): `onConfigurationChanged`(794~807) 에서 검색 중이면 `endSearch()` 를 부르고 앵커 기준으로 다시 놓는다.
  - API<30 가로 검색(S14): `liftAboveIme` 가 WindowMetrics 를 못 쓰면 검색 중 `y=0` 으로 올리고 검색이 끝나면 앵커로 되돌린다.
  - Q7: 머리줄과 메뉴 버튼에 `focusProperties { canFocus = false }` — 하드웨어 Enter 가 구간 칩을 누르지 않게.
  - strings.xml: 위 문구를 전부 리소스로. 마침표 없는 한 줄, `'…'` 사용.
- **수용 기준**:
  - `OverlayPlacementTest`: 네 사분면 × 3크기(칩→목록→요약)에서 `접기` 중심과 칩 중심 거리 ≤ 4dp; 드래그 뒤 앵커 누적; clamp 여백 8dp.
  - `OverlayMemoryTest`: dismissed 규칙(같은 판 40분 안 = 차단, 40분 뒤 = 허용, Result 뒤 = 허용, 프로세스 재시작 뒤에도 값 유지).
  - 에뮬레이터(가로): 칩 4모서리 각각에서 펼침→목록→요약→접기 순으로 스크린샷, '접기' 자리가 칩 자리와 같음. 메뉴 열고 구간 바꾸기, 코드 복사 '복사됨 ✓'. 알림 3상태 문구 확인(창 보임/닫힘/감지 꺼짐). X 로 닫은 뒤 알림 `지금 보이기` 로 즉시 복귀. 자동 표시 끄기 뒤 다른 앱에서 창이 뜨지 않음. 회전 뒤 검색 종료·위치 복귀.

### WP-O2 오버레이 검색 (Wave 1) — 규모 M

- **목표**: 검색을 끝내는 길이 늘 창 안에 있고, 키보드가 스스로 내려가면 검색도 끝나며, 후보·칩·등급 칸을 게임 중 손가락으로 틀리지 않게 만든다. 오버레이 조작이 앱 조건을 조용히 바꾸는 일을 없앤다.
- **포함 id**: Q1/F12, Q2, Q3, Q4, Q5, Q6(2·3), Q7(입력 쪽), Q8, Q9, Q10(당장안)/F8a, Q11, Q13, Q15/F4b, R12, N13('모두 지우기' 문구 제거), N19(힌트 문구).
- **수정 파일**: `overlay/OverlaySearchBar.kt`, `overlay/OverlayRootView.kt`, `ui/components/TokenSearch.kt`, `data/DeckSearch.kt`, `overlay/OverlayListFilter.kt`, `DeckSearchTest.kt`·`OverlayListFilterTest.kt`(확장), 신규 `OverlaySearchStateTest.kt`.
- **명세**:
  - 끝내기(Q1/F12, Q2, Q11): `OverlayRootView` 인셋 리스너를 검색 중에는 항상 달고, IME 가 보였다가 사라지면 150ms 뒤 재확인 후 `endSearch()`. 검색 줄 오른쪽 끝에 `완료` 버튼 44×32dp(항상 보임, 가로에서 머리줄이 숨어도). 패널 안 빈 곳(후보·칩 밖) 누름도 `endSearch()`. 창 밖 첫 누름은 삼키지 않는다(사용자 결정 5 권장안).
  - 후보(Q3): 머리줄이 숨는 가로(<480dp)에서 `CandidateList` 최대 높이 84dp(28dp 행 3줄), 세로는 200dp 유지. 후보 8개 → 세로 스크롤 가능하되 첫 3개는 스크롤 없이 닿음.
  - 후보 수(Q4, Q13): `OverlayCandidate(token, deckCount, hiddenByGrade)` — `deckCount` 는 등급 조건 **전** 수, `hiddenByGrade` 는 꺼진 등급 때문에 빠진 수. 표시 `'덱 5'` / `'덱 0 · 꺼진 등급 5'`. `alpha 0.5` 흐리기는 `deckCount==0 && hiddenByGrade==0` 일 때만. 빈 목록 안내(`overlayEmptyListMessage`, 36) 는 `'모두 지우기'` 대신 `'조건에 맞는 덱이 없습니다 · 꺼진 등급 S 3 · B 2'` 처럼 등급별 수를 적고, 버튼은 `⊗`(칩 전부 지우기) 를 가리킨다(R12).
  - 제출(Q5, Q7): `onSubmit` 은 `deckCount>0` 인 첫 비(非)사용자지정 후보를 고르고, 없으면 검색 줄 아래 `'맞는 덱 없음'` 을 2초 보이고 아무것도 고르지 않는다. 하드웨어 Enter 는 `onPreviewKeyEvent` 로 잡아 같은 동작 뒤 소비(구간 칩으로 새지 않음).
  - 한글 조합(Q6-2·6-3): `KeyboardOptions(hintLocales = LocaleList(Locale("ko","KR")), autoCorrectEnabled = false, imeAction = Search)`. 새 후보가 사용자 지정 하나뿐이면 직전 후보 목록을 유지하고 사용자 지정만 맨 아래에 갱신.
  - 칩(Q8): 칩 전체(24dp 높이)가 '빼기' 동작. 글자 부분 누름으로 키보드가 뜨지 않게 `TokenChip` 의 `clickable` 을 칩 루트로 옮기고 `×` 아이콘은 장식.
  - ⊗(Q9): 입력 글자가 있으면 글자만 지우고, 없을 때 칩 전부 지우기. S 칸과 12dp 띄움. 아이콘 `Icons.Filled.Backspace` 로(취소 모양 아님).
  - 등급 칸(Q10, F8a): 28×28dp, 간격 6dp. 켜짐 = `gradeColor` 0.35 채움 + 흰 글자, 꺼짐 = 채움 없음·`alpha 0.4`·취소선 없음. 마지막 하나를 끄려 하면 무시하지 않고 칸 아래 `'등급 하나는 켜 두어야 합니다'` 를 2초 보인다(`DeckPrefs.toggleGrade` 103~107 은 그대로 두고 UI 에서 안내). 입력 중(IME 보임)에는 등급 칸을 숨겨 후보 줄 자리를 준다. 오버레이의 등급 변경이 앱에 저장되는 것은 사용자 결정(조건 저장·공유)이므로 유지하되, 등급 칸 위에 `'앱과 같은 조건'` 11sp 라벨 한 줄.
  - 누름 수(Q15/F4b): 검색 줄은 목록 상태에서 항상 보이고(현재), 요약 상태 머리줄의 `←` 가 목록+검색 줄로 바로 간다. 후보를 고른 뒤 키보드를 유지(연속 추가), `완료` 로만 내림.
  - 편집 중 칩 줄은 한 줄 가로 스크롤(높이 튐 방지), 편집이 끝나면 FlowRow.
  - 힌트: `'챔피언·특성·아이템·증강'`(N19). `DeckSearch.suggestTokens` 는 `within` 밖 수를 함께 돌려주는 `suggestTokensWithHidden(query, listed, all)` 을 추가(기존 함수는 유지).
- **수용 기준**:
  - `DeckSearchTest`: `suggestTokensWithHidden` 이 (deckCount, hiddenByGrade) 를 올바르게 셈(등급 필터로 사라진 덱 수), 빈 질의 → 빈 목록 유지.
  - `OverlaySearchStateTest`: submit 이 deckCount 0·사용자지정을 건너뜀; 후보가 사용자지정만이면 직전 후보 유지; ⊗ 두 단계 동작.
  - 에뮬레이터(가로·Gboard 한국어): '드레이븐' 을 한 글자씩 입력해도 후보 줄 높이가 변하지 않음; IME `∨` 로 키보드를 내리면 검색이 끝나고 머리줄이 돌아옴; `완료` 로 종료; 후보 3개가 스크롤 없이 보임; 마지막 등급 칸 끄기 시 안내 문구; 하드웨어 Enter 뒤 구간이 바뀌지 않음(`DeckPrefs` 값 확인).

### WP-O4 오버레이 목록·요약·글자·티어 카드 (Wave 1) — 규모 M

- **목표**: 게임 중 한눈에 읽히는 글자 크기·대비·밀도로 바꾸고, 두 등급 체계를 모양으로 구분하며, 요약에 '무슨 덱인지' 한 줄을 넣는다.
- **포함 id**: R2(오버레이 쪽), R3, R4, R5, R6(접미사 표시), R10(일부: 새 유닛·3성·캐리 강조), R11, R13, F9, V28, Q14, F10(요약 크기).
- **수정 파일**: `overlay/OverlayDeckList.kt`, `overlay/OverlaySummary.kt`, `overlay/OverlayFaces.kt`, `overlay/OverlayTheme.kt`, `overlay/ProfileCard.kt`, `docs/benchmark/2026-09-15/design.md` §7(목록·요약 절).
- **명세**:
  - `OverlayTheme.kt` 에 `OverlayType` 객체: `title` 12sp Bold lh15, `body` 12sp lh15, `label` 11sp Medium, `badge` 11sp Bold, `mark` 10sp(얼굴 위 첫 글자), `star` 8sp(3성 별). 오버레이 안 `.sp` 리터럴은 이 객체만 쓴다. `OverlayScrim` 불투명 1.0(0.95 → 1.0, R11/V28).
  - 등급 배지: P2 `GradeBadge` 를 쓰되 오버레이 크기 20dp 유지. metatft = Filled, 중국 한정 = Outlined + 뒤에 `'중국'` `TextBadge`(10sp 글자 허용, `ChinaDot` 5dp 삭제). 고정(pinned) 덱이 꺼진 등급이면 배지 `alpha 0.45` + 행 오른쪽 핀 아이콘(Q14).
  - 목록 행(가로, 사용자 결정 4 권장안): 한 행 = `[GradeBadge][별칭 12sp Bold weight(1f)][캐리 얼굴 2개 20dp]` / `[설명 12sp maxLines 2 lh15]`, 높이 44~59dp. 얼굴 전체 줄은 `넓게 보기` 에서만. 세로(앱 위)는 현재 얼굴 줄 유지(22→24dp). 별칭의 `' · …'` 접미사는 행에서 떼고 설명 첫 줄에 운영으로 나온다(R6). `DECK_LIST_MAX` 가로 200dp(세로 300dp 유지) — 가로에서 약 4행 + 스크롤(R5).
  - 요약(`DeckSummaryView`): 첫 줄 `[GradeBadge] {별칭}` 12sp Bold, 둘째 줄 `{운영} · {주 특성1 n} · {주 특성2 n}` 12sp(R4). 레벨 칩 아래 캡션 11sp `'{L}렙 {round} 도달 · 롤다운 {r}렙'` (`BuildupPlanner.timings`·`rollLevel` 공개 함수로 조합; `'주 리롤'` 문구 삭제). 레벨 사이 새로 들어오는 유닛은 얼굴 왼쪽 위 6dp `Positive` 점, 3성은 `Gold` 별 8sp, 캐리는 40dp 로 크게(R10 일부; 금색 테두리는 쓰지 않음).
  - 특성 칩: P2 `TraitChip`(`onSurface` 글자)로 교체(R13).
  - `ProfileCard.kt`: 최근 게임 6 → 5칩, 칩 18dp 간격 3dp, 불투명 1.0, `'톱4'` → `'TOP4'`, LP 는 `'1,234 LP'` 로 띄어쓰기. `CHIP_WIDTH 15dp` → 18dp.
- **수용 기준**:
  - 에뮬레이터(가로 2400×1080): 목록 첫 화면에 4행이 보이고 창 높이 ≤ 화면의 45%; 행 글자 최소 11sp(`OverlayType` 외 `.sp` 리터럴 grep 0); 중국 한정 덱은 테두리 배지 + '중국' 배지; 요약 둘째 줄이 운영·특성으로 시작; 뒤 게임 화면이 글자 뒤로 비치지 않음(스크린샷 픽셀 검사: 패널 영역 알파 255).
  - 기존 `OverlayListFilterTest` 통과(정렬·필터 동작 변경 없음).

### WP-A2 덱 목록·카드·조작부 (Wave 1) — 규모 L

- **목표**: 카드에서 사용자가 지적한 '굳이 싶은 정보'(표본 줄·출처 배지·중복 칩·변형 수)를 빼고 한 카드를 182dp 안에 넣어 첫 화면에 3장이 보이게 한다. 목록 위 조작부 309dp 를 두 줄로 줄이고, 걸러져 있음을 늘 보이게 한다. 등급 배지·카드 면·칩은 P2 토큰을 쓴다.
- **포함 id**: L1(표시), L2, L3, L5/V17, L6/V9, L7, L8, L9/V29, L11, L12/V16(적용), L14, L15/V5, L16, V1, V3, V7, V18(적용), N11, N13(일부: 필터 접근·고정/숨김 노출), N3(배너 표시 규칙; 동기화 자체는 A4). L10(앱바 제목)은 `MainActivity` 소유인 A4 로.
- **수정 파일**: `ui/components/DeckCardV2.kt`, `ui/components/StatChips.kt`(`StatsRow` 재구성; `SampleLabel`·`SourceBadges`·`OpsChip` 은 `@Deprecated` 만), `ui/screens/DeckListScreen.kt`, `ui/AppViewModel.kt`(목록 부분), `data/DeckRepository.kt`(`syncing: StateFlow<Boolean>`·`lastError: String?` 추가 — 한국어 사유: 네트워크 없음/서버 오류/형식 오류; `ready()` 즉시 교체(139)는 유지), 신규 `ui/screens/DeckListControls.kt`, `GradeFilterTest`·`QueryPersistTest`(확장).
- **명세**:
  - 카드 면: `scheme.surfaceContainerHighest` 채움, 테두리 없음, 모서리 12dp, 안쪽 여백 12dp, 카드 간 8dp. 고정 덱만 `primary` 60% 1.5dp 테두리. 화면 좌우 여백 16dp(`ScreenPadding` 14 → 16).
  - 1줄: `[GradeBadge(Filled | Outlined 중국 한정)][별칭 titleMedium weight(1f)][▲/▼ 추세 + 평균 등수 '4.12등' labelLarge]`. 편집 덱은 `Editorial` 스타일.
  - 2줄(bodySmall onSurfaceVariant, 한 줄): `'{운영} · {주 특성1 n} · {주 특성2 n}'` + 필요할 때만 `TextBadge('중국')`/`TextBadge('표본 적음')`(n<300). 난이도가 있으면 `' · 난이도 쉬움/어려움'`. 이것이 카드의 유일한 보조 글자 줄이다(규칙 R2). 카드에서 `SourceBadges`·`OpsLine`·`SampleLabel`·`'변형 N개'` 호출을 없앤다(L2, L3, L11, V1, V3). 단 `StatChips.kt` 의 `SampleLabel`·`SourceBadges`·`OpsChip`·`opsTexts` 함수 자체는 A3 의 `DeckDetailScreen.kt` 가 아직 부르므로 지우지 않고 `@Deprecated` 만 붙인다(T 가 지움). `StatsRow` 는 기존 시그니처를 유지하고 `valueStyle: TextStyle = MaterialTheme.typography.titleMedium`(16sp; A3 는 `titleLarge` 20sp 로 호출)·`compact` 매개변수를 기본값과 함께 더한다.
  - 3줄: 캐리 얼굴 40dp ×(1~2) + 각 아래 아이템 12dp ×3(폭을 얼굴에 맞춤, L5/V17), 오른쪽에 나머지 유닛 얼굴 한 줄 22dp(넘치면 `'+N'`). 두 줄로 꺾이지 않게 `Row` + `weight`.
  - 4줄(`StatsRow` 1단계 재구성 — L4 결정 전까지 4수치 유지): 값 16sp SemiBold `tnum`, 라벨 11sp `onSurfaceVariant`, 네 칸 등폭. 중국 한정 덱의 픽률은 `'–'`(V11). 형식은 P2 `formatPct`·`formatAvg`.
  - 카드 높이 목표 ≤ 182dp(1080×2400 에서 첫 화면 3장). 이미지 자리 표시는 P2 컴포넌트 적용(L12/V16).
  - 조작부(`DeckListControls.kt`) 두 줄, 총 높이 ≤ 96dp(L8, V7):
    - 1줄: `[구간 ▾ (드롭다운, bucketShortLabel)][정렬 ▾ (등급/평균 등수/픽률/승률 — 표본 정렬은 UI 에서 숨김)][필터 ▾ (시트: 중국 한정만·편집 덱만·마무리 레벨·숨긴 덱 보기)][조건 초기화 — 늘 보임][12/77 labelMedium]`.
    - 2줄: `[검색 줄 weight(1f)][S A B C D 28dp 칸, 간격 6dp]`(사용자 결정 3 권장안). 주 특성 칩 ~23개 가로 스크롤은 필터 시트 안으로.
    - 기본 상태(C·D 꺼짐)가 '걸러짐'이므로 목록 끝에 footer `'C·D 등급 덱 N개 숨김 · 보기'`(누르면 등급 켬, N11). 저장된 조건이 기본과 다르면 `조건 초기화` 옆 점 표시.
  - 배너(L9/V29, N3 표시 규칙): 항상 보이던 `FeedBanner` 를 상태형으로. `fromBundle && !syncing` → `'앱에 담긴 데이터(패치 18.2) · 새로고침'`, `syncing` → `'새로고침 중…'`, `stale(>36h)` → `'{n}시간 전 데이터 · 새로고침'`, `degraded(마지막 동기화 실패)` → `'새로고침 실패 · 다시 시도'`. 정상이면 배너 없음. 배너 전체가 눌려 `refresh()`.
  - 빈 상태(L16, N20): `'조건에 맞는 덱이 없습니다'` + `[조건 초기화]` 버튼. 로딩은 카드 모양 스켈레톤 3장(스피너 대신).
  - 앱바 제목(`'FloaTFT'` → `'덱'`, L10)은 `MainActivity` 의 `TopAppBar` 가 `app_name` 을 쓰므로 A4 가 고친다.
  - `AppViewModel`: `hiddenByGradeCount`, `bannerState`, `sortModesVisible`(SAMPLE 제외) 를 노출. 데이터 모델·정렬 로직은 바꾸지 않는다.
- **수용 기준**:
  - 에뮬레이터(세로 1080×2400): 첫 화면에 조작부 + 카드 3장이 보임(카드 높이 ≤ 182dp 를 레이아웃 인스펙터로 확인); 카드에 'n=', 'metatft', 'KR' 글자가 없음; 중국 한정 덱은 테두리 배지 + '중국' 배지; C·D 꺼짐 footer 표시·누르면 켜짐; 배너는 번들 상태에서만 보이고 누르면 새로고침.
  - `GradeFilterTest`·`QueryPersistTest` 통과(조건 저장·초기화 동작 유지).
  - `grep -n "\.sp" DeckCardV2.kt StatChips.kt DeckListScreen.kt DeckListControls.kt` 0건.

### WP-A3 덱 상세 (Wave 1) — 규모 L

- **목표**: 머리말의 출처·수치 3줄을 걷어내 행동 버튼을 첫 화면 안에 두고, 섹션을 쓰는 순서로 재배치하며, 같은 수치의 반복과 거짓 표시('실측 배치' 스위치·'작가 추천'·'유사도 100%')를 없앤다. 구간 칩이 목록·오버레이 조건을 바꾸지 않게 한다.
- **포함 id**: D1~D18, V2, V8, V19, V20, V21, V30(상세 쪽), N12, N14, N15(b: 상세→상세 이동), D6(변형: 화면 안 고정 띠).
- **수정 파일**: `ui/screens/DeckDetailScreen.kt`, 신규 `ui/screens/DeckSourceSheet.kt`, `docs/benchmark/2026-09-15/design.md` §6.3.
- **명세**:
  - 머리말(위에서): `[GradeBadge][추세 ▲▼][별칭 titleLarge 20sp]` / `'{운영} · 마무리 8렙 57% · 9렙 38%'`(metatft `finalLevels`; 중국 덱은 `'최종 N레벨'`) / 주 특성 ≤4 `TraitChip` + `'+N'` / `StatsRow`(값 `titleLarge` 20sp) / `'골드~에메랄드 기준 ▾  ⓘ'` — 구간은 **상세 안에서만** 바뀌는 로컬 상태(`DeckPrefs.setBucket` 호출 삭제, D5·N12), `ⓘ` 는 출처 시트. 긴 원래 이름·KR 배지·특성 8개·비교 줄·정밀 줄 삭제(D1, D2, D4, V2, N14).
  - 버튼: `[오버레이로 보기][코드 복사]` 각 `weight(1f).height(44)`, 같은 글자색(V19). 스크롤이 머리말을 지나면 화면 위에 고정 띠 `[별칭][코드 복사]` 40dp(D6).
  - 섹션 순서(D7): ① 캐리·아이템(`KeyUnitsSection` 흡수: 캐리별 아이템 3 + 대체 아이템) ② 레벨별 구성(빌드업, D9: 레벨 칩 `FloaFilterChip`, 캡션 `'{L}레벨 {round} 도달 · 롤다운 {r}레벨'`, `'글로벌 플래+ 3일'` 삭제) ③ 추천 증강(단일 목록; 편집 덱일 때만 `'작가'` 태그, fallback 은 `'통계 상위'` 로만; 흐리기 삭제, 단계 칩은 `'2-1'` 같은 라운드 글자로만, D12·V20) ④ 배치(`useMeasured` 는 `hasCoords && positions.isNotEmpty()` 일 때만 스위치 노출, 아니면 스위치 없이 편집 배치, D10) ⑤ 더 보기(접힘): 비슷한 구성(변형은 대표 대비 **다른 유닛만** 인라인 diff `'−카직스 +럭스'`, 누르면 화면이 뛰지 않음 — `bringIntoView` 삭제, D13), 상대하기 어려운 덱(`target` 별칭 + `›`, 누를 수 있음 표시, D14) ⑥ 데이터 출처(시트, D8·D15·D18).
  - `DeckSourceSheet.kt`: metatft 표본 n·기준일·구간, 지역별 막대(KR/글로벌/중국), 중국 참고 주의 문구, 섹션별 출처 표(레벨·증강·배치·변형이 각각 어디서 왔는지), 배지 뜻(채움=metatft, 테두리=중국 한정, '편'=편집), 중국어 원문 두 곳을 한 곳에. 본문 섹션의 출처 캡션 9개 삭제(D8). `'유사도'`·`'글로벌 비교'` 섹션 삭제(D15).
  - 섹션 제목은 P2 `SectionTitle`(D16, V8 — 파랑 제목 금지), 간격 16 → 24/8.
  - 문구: `'주 리롤 N렙'` → `'N레벨 리롤'`, `'胜率阵容 상세 집계'` → `'중국 승률 조합 집계'`, `'metatft 집계'` 는 출처 시트로, `'9레벨 완성'` 삭제(D3·D17). 상세 안에서 `'렙'` 은 쓰지 않는다(레벨 칩만 `'8렙'` 허용은 오버레이).
  - 상대 덱·변형에서 다른 상세로 갈 때 `popUpTo(detail) { inclusive = true }` 로 스택이 쌓이지 않게(N15b).
- **수용 기준**:
  - 에뮬레이터(세로): 첫 화면 안에 두 버튼이 보임(스크롤 0 에서 버튼 하단 y < 화면 높이 80%); 머리말에 같은 평균 등수가 두 번 나오지 않음; 구간 칩을 바꿔도 목록·오버레이 구간이 안 바뀜(`DeckPrefs` 값 확인); `hasCoords` 없는 덱에서 스위치가 없음; 편집 덱이 아닌 덱에 '작가' 글자 없음; 출처 시트 열림.
  - `grep -n "\.sp\|胜率\|유사도\|주 리롤" DeckDetailScreen.kt DeckSourceSheet.kt` 0건.

### WP-A4 첫 실행·설정·게임 연동·동기화 (Wave 1) — 규모 L

- **목표**: 첫 실행 안내가 틀린 ID 를 '연결됨'이라 하지 않고, 자동 띄우기까지 한 번에 안내하며, 설치 직후 데이터가 곧바로 새로고침되고, 오버레이·연동을 켜는 길이 덱 탭에서 한 번에 닿게 한다. 재부팅 뒤 연동이 멈추지 않게 한다.
- **포함 id**: N1, N2, N3(동기화), N4, N5, N6, N7, N8, N15(a: 오버레이에서 연 상세의 뒤로), N20, V24, V25, V26(일부), V27, S3, S12, L10(덱 탭 앱바 제목 `'FloaTFT'` → `'덱'`; `app_name` 리소스는 그대로 두고 `TopAppBar` 에 탭 이름을 준다), N16·N17(최소: 그룹 순서·연결 뒤 한 줄), N18(최소: 최신 여부 표시 통일).
- **수정 파일**: `MainActivity.kt`, `ui/components/FirstRunDialog.kt`, `ui/screens/SettingsScreen.kt`, `ui/ingame/GameLinkSection.kt`, `ui/ingame/IngameViewModel.kt`, `ui/components/ProfileSummaryV2.kt`, `TftApp.kt`, `sync/DailySyncWorker.kt`, `data/ProfileRepository.kt`(오류 문구만), 신규 `ingame/BootReceiver.kt`, `AndroidManifest.xml`, `ingame/GameSession.kt`(S12), 신규 `ui/SetupViewModel.kt`(필요 시), `ui/screens/CodexScreen.kt`(최신 여부 표시 한 줄), `GameDetectorTest`(확장) + 신규 `FirstRunStateTest.kt`.
- **명세**:
  - 첫 실행(N1, N2, V27): `connected` 는 `savedId.contains("#")` 대신 `profileState is Ready`(60). 조회 실패는 입력 칸 아래 빨간 한 줄(`'소환사를 찾지 못했습니다 · 이름#태그 를 확인해 주세요'`), 대화상자는 닫히지 않음. 지역 선택 `[KR][JP][NA]…` 드롭다운(84 의 고정값 제거). 셋째 단계 `'게임 중 자동으로 띄우기'` 스위치 → 사용 기록 권한 화면으로 보내고(`pendingEnable`) 돌아오면 자동으로 켬; 안내 `'목록에서 FloaTFT → 허용 → 뒤로'`. 알림 권한은 이유(`'닫은 오버레이를 다시 띄울 때 씁니다'`) 한 줄과 함께 요청. 버튼 `[나중에][완료]`, 닫으면 스낵바 `'내 정보 탭에서 언제든 설정할 수 있습니다'`. 글꼴 `headlineSmall`, 모서리 16dp, `OutlinedButton`.
  - 동기화(N3): `TftApp` 은 번들에서 로드했으면(`fromBundle`) 15분 대기 없이 `OneTimeWorkRequest` 로 즉시 동기화(`DailySyncWorker` 의 주기 작업은 그대로). 성공은 `FeedState.lastSyncedAt` 변화로 감지해 스낵바 `'덱 데이터를 새로고침했습니다 (패치 18.2)'`. `syncing`·`lastError` 상태는 A2 가 `DeckRepository` 에 넣으므로 A4 는 만지지 않는다(합친 뒤 T 단계에서 스낵바를 `lastError` 와 연결).
  - 덱 탭 앱바(N4, N5): 오른쪽 오버레이 아이콘 → 시트 `[TFT 열고 띄우기 (getLaunchIntentForPackage)][홈 화면에 띄우기][오버레이 끄기]`. 권한이 없으면 설정으로 보내고 돌아왔을 때(`onResume`) 권한이 생겼으면 자동으로 이어서 띄움. 토스트 삭제. `startOverlay` 의 알림 권한 요청은 시트 안에서.
  - 게임 연동(N6, N7, N8): `'사용 기록 접근 허용'` 버튼 삭제, 스위치가 권한 흐름을 맡음(`setDetectEnabled` 의 `pendingEnable` 경로로 통일). 자동 표시는 라디오 `'TFT 가 앞에 있을 때만 (추천)' / '항상'`. 감지가 꺼져 있으면 하위 행 숨김. 꺼진 행은 제목·설명 모두 `alpha 0.38`(V25). 알림 권한 거부 상태면 행 아래 `'알림이 꺼져 있어 닫은 오버레이를 알림에서 되살릴 수 없습니다 · 설정'`.
  - 설정 순서(N16 최소): 게임 위에 띄우기 → 게임 연동 → 내 전적 → 덱 데이터(2줄: `'패치 18.2 · 3시간 전 새로고침'` + `[새로고침]`) → 앱 정보(접힘: 원본·출처·버전). 3문장 설명문은 한 문장으로(V26). 연결된 전적은 한 줄 `'{이름}#{태그} · KR · 변경'`(N17 최소). 전적 카드 평균 등수 색은 `onSurface`(V24), 줄임표 `'…'`.
  - `BootReceiver`(S3): `BOOT_COMPLETED`·`MY_PACKAGE_REPLACED` 수신 → `detectEnabled && 사용 기록 권한` 이면 `OverlayService.startWatch()`. 매니페스트 `RECEIVE_BOOT_COMPLETED`. **실기기에서 확인**(에뮬레이터 재부팅으로 1차).
  - `GameSession`(S12): TFT 가 앞에 있으면 결과 알림을 `IMPORTANCE_LOW` 채널로 조용히(소리·팝업 없음).
  - 오버레이에서 연 상세의 뒤로(N15a): `moveTaskToBack(true)` 로 게임으로 복귀(앱 홈으로 가지 않음).
  - 문구(N20): 오류·빈 상태는 `'무엇이 · 왜 · 다음 행동'` 한 줄(예: `'전적을 불러오지 못했습니다 · 네트워크 확인 뒤 다시 시도'`), 마침표 없음.
- **수용 기준**:
  - `FirstRunStateTest`: 잘못된 ID → 연결 안 됨·오류 문구; 권한 복귀 시 `pendingEnable` 소비.
  - 에뮬레이터: 앱 삭제 후 설치 → 첫 실행 3단계 진행(틀린 ID 로 '연결됨' 안 뜸, 권한 화면 다녀오면 스위치 켜짐) → 덱 탭 배너가 1분 안에 사라지고 스낵바 표시(네트워크 있음) / 오프라인이면 `'새로고침 실패'` 배너; 덱 탭 앱바 시트에서 오버레이 띄우기; `adb reboot` 뒤 앱을 열지 않고 TFT(또는 대체 패키지) 실행 시 오버레이가 뜸; 결과 알림 채널 확인.

### WP-T 용어·문구 일괄 치환 (Wave 2) — 규모 S

- **목표**: Wave 1 이 끝난 코드 전체에서 같은 뜻의 말을 하나로 맞추고, 남은 `.sp` 리터럴과 낡은 컴포넌트를 지운다.
- **포함 id**: V12, N19, D17, V30(남은 것), R8(앱 쪽 문구), V4(잔여).
- **수정 파일**: `data/Models.kt`, `res/values/strings.xml`, 그 밖에 grep 으로 잡히는 모든 `.kt`(문자열만), `ui/theme/Theme.kt`(`@Deprecated` 제거), `ui/components/Components.kt`(`TierBadge` 등 제거), `ui/components/StatChips.kt`(`@Deprecated` 된 `SampleLabel`·`SourceBadges`·`OpsChip`·`opsTexts` 삭제 — 남은 호출부는 이 단계에서 함께 정리).
- **명세**: 치환표 — `유닛`→`챔피언`(`DeckToken.axisLabel`), `증강체`→`증강`, `시너지`→`특성`(`SearchAxis` 라벨), `갱신`→`새로고침`, `플래티넘+`→`플래+`, `톱4`→`TOP4`, `글로벌 플래+ 3일`→삭제, `주 리롤`→`N레벨 리롤`, `골드 이하`→`실버 이하`, 패치 표기 `'패치 18.2'`(`v`·`Set` 접두 제거). `'…'` 통일, 한 줄 문구 끝 마침표 제거. A4 가 코드 리터럴로 넣은 문자열을 `strings.xml` 로 옮긴다. `.sp` 리터럴 감사: Theme.kt·OverlayTheme.kt 외 0건.
- **수용 기준**: `grep -rn "유닛\|증강체\|시너지\|갱신\|톱4\|골드 이하\|주 리롤" android/app/src/main` 0건(도감 데이터의 원문 필드 제외); 단위 테스트 전부 통과; 스크린샷 4장(목록·상세·검색·오버레이)에서 용어 확인.

### 2.9 요약표

| WP | Wave | 규모 | 포함 id | 파일(요지) |
|---|---|---|---|---|
| P1 오버레이 파일 분할 | 0 | M | — | OverlayContent.kt 분할 8파일, OverlayWindow.kt → Placement/RootView |
| P2 토큰·공용 컴포넌트 | 0 | M | V4 V5 V6 V10 V11 V13 V14 V15 L6/V9 L12/V16 L14 L15 R8 V18 D16 | Theme.kt, Components.kt, StatChips.kt(공용), UiUtils.kt, MASTER.md |
| C1 수집기·데이터 | 0 | M | R1 L13 R6 R7 D3 D9 R8 D14 D12(데이터) | collector/*.py, data/, assets/decks.json |
| O1 창·머리줄·알림 | 1 | L | F1 F2 F3 F6 F7 F8b·c F11 F13 F14 S1 S2 S4-4 S5 S7 S8 S14 R9/F10 Q7 Q16 | OverlayService/Content/Header/Chip/Menu/Placement/Memory, strings.xml |
| O2 검색 | 1 | M | Q1/F12 Q2 Q3 Q4 Q5 Q6-2·3 Q7 Q8 Q9 Q10/F8a Q11 Q13 Q15/F4b R12 N13(문구) N19 | OverlaySearchBar/RootView, TokenSearch, DeckSearch, OverlayListFilter |
| O4 목록·요약·글자 | 1 | M | R2 R3 R4 R5 R6 R10 R11 R13 F9 F10 V28 Q14 | OverlayDeckList/Summary/Faces/Theme, ProfileCard |
| A2 목록·카드 | 1 | L | L1~L3 L5~L9 L11 L12 L14~L16 V1 V3 V7 V17 V18 N11 N13 N3(배너) | DeckCardV2, StatChips(StatsRow), DeckListScreen, DeckListControls, AppViewModel |
| A3 상세 | 1 | L | D1~D18 V2 V8 V19 V20 V21 V30 N12 N14 N15b | DeckDetailScreen, DeckSourceSheet |
| A4 첫 실행·설정·연동 | 1 | L | N1~N8 N15a N20 V24~V27 S3 S12 L10 N16~N18(최소) | MainActivity, FirstRunDialog, Settings, GameLink, IngameVM, TftApp, DailySyncWorker, DeckRepository, BootReceiver, Manifest, GameSession |
| T 용어 치환 | 2 | S | V12 N19 D17 V30 R8 V4(잔여) | Models.kt, strings.xml, 전역 문자열 |

에이전트 배분: Wave 0 은 3개, Wave 1 은 6개(A2·A3·A4·O1 이 L 이므로 이 넷을 먼저 띄우고 O2·O4 를 뒤에 붙여도 됨), Wave 2 는 1개.

---

## 3. 사용자 결정 필요

아래 다섯 개만 사용자가 정한다. 그 밖의 선택은 전부 권장안으로 진행한다(예: 티어 카드 기본 꺼짐 = 권장안으로 진행, O1).

### 결정 1 · 검색 탭을 없애고 목록 검색 줄에 합칠까 (N10·V23)
- **권장안: 없앤다.** 검색 탭의 고유 기능(핵심/대체 아이템 묶음, 도감 버튼, 예시 칩)을 목록 검색 줄에 옮긴다: 검색 줄 아래 예시 칩 3개(비어 있을 때), 후보에 `핵심`/`대체` 구분 표시, 결과 위 `'조건 밖 N개 더'` 링크(등급·구간 조건을 잠시 풀어 보기), 검색 줄 오른쪽 도감 아이콘.
- **이유**: 같은 검색어에 두 곳의 덱 수가 다른 것(N10)이 '틀린 정보'로 보이고, 탭 하나가 목록의 부분집합이다. 오버레이 검색과 규칙이 하나로 맞는다(다중 선택 토큰).
- **결정하면**: A2 가 `SearchScreen.kt` 의 기능을 목록 검색 줄로 옮기고 파일을 지우며(규모 +S), `MainActivity` 의 탭을 3개(덱/도감/내 정보)로 줄이는 것은 그 파일을 가진 A4 가 맡는다. 미루면 검색 탭은 그대로 두고 V23 만 보류.

### 결정 2 · 카드의 4수치 줄을 카드 전용 변형으로 바꿀까 (L4, MASTER §6 개정)
- **권장안: 바꾼다.** 카드에는 `평균 등수 4.12등` + `TOP4 58.5%` 두 값과 픽률 하나(`픽 3.1%`)만 두고, 승률은 상세로 보낸다. 현재 데이터에서 평균 등수와 TOP4 의 상관이 r = −0.993 이라 같은 신호 두 번이고, 승률은 1등률이라 등급과 다른 축이지만 카드에서 읽는 사람이 드물다.
- **이유**: 사용자가 '굳이 싶은 정보'로 꼽은 축과 같고, 카드 높이를 20dp 더 줄인다. 다만 MASTER §6 '4수치 고정' 조항을 사용자가 서명해 둔 것이므로 판정자가 바꾸지 않는다.
- **결정하면**: A2 의 `StatsRow` 1단계 재구성 위에 2단계(값 3개, 승률 제거) 를 얹는다(규모 +S). 미루면 4수치 유지(A2 1단계만).

### 결정 3 · 등급 칸 5개를 요약 칩 하나로 접을까 (Q10·V7·L8)
- **권장안: 칸 5개를 유지**하되 28dp·간격 6dp 로 키우고 상자 테두리를 없앤다(O2·A2 에 이미 반영).
- **이유**: 사용자 결정(등급 다중 선택은 늘 보이는 조회 조건)과 맞고, 게임 중 한 번에 켜고 끄는 조작은 칸이 빠르다. 요약 칩(`'S A B'`)은 누름 수가 하나 늘고 상태를 읽기 어렵다.
- **결정하면**: 칩으로 가면 O2·A2 명세의 등급 칸 부분을 `[등급 ▾]` 드롭다운으로 바꾼다(규모 ±0).

### 결정 4 · 가로 오버레이 목록 줄에서 얼굴 줄을 어떻게 할까 (R5·F9·R3)
- **권장안: 캐리 2얼굴 + 설명 2줄**로 가고, 얼굴 전체 줄은 `넓게 보기` 에서만 보인다(O4 에 반영).
- **이유**: 가로에서 한 행 92dp(얼굴 줄 포함) → 44~59dp 로 줄어 4행이 보이고, 별칭·설명이 '텍스트만 읽고 알기' 원칙을 채운다. 얼굴 9개는 12sp 글자와 겨루어 오히려 덱을 못 알아보게 했다(R3 스크린샷).
- **결정하면**: 얼굴 줄을 남기면 `DECK_LIST_MAX` 가로 200dp 에 2행만 들어가므로 260dp 로 올린다(F6·R5 효과가 줄어듦).

### 결정 5 · 검색 중 창 밖 첫 누름을 삼킬까 (Q2·F4)
- **권장안: 삼키지 않는다.** 대신 `완료` 버튼과 IME 내려감 감지로 검색을 끝낸다(O2).
- **이유**: 창 밖 누름을 삼키면 게임의 누름 하나가 사라지는 것이고, 그 자체가 오조작이다. 검색이 끝나는 길을 창 안에 두는 편이 확실하다.
- **결정하면**: 삼키기로 가면 검색 중 창 플래그에서 `FLAG_NOT_TOUCH_MODAL` 을 빼고(창이 터치 모달이 되어 창 밖 누름도 이 창이 받음) `OverlayRootView.dispatchTouchEvent` 에서 창 밖 좌표의 `ACTION_DOWN` 을 `endSearch()` 뒤 소비한다(O2 범위, 규모 S). 단 그 한 번의 누름은 게임에 닿지 않으며, 검색을 끝내려는 사용자에게는 이것이 곧 '누름 하나 손실'이다.

---

## 4. 다음 릴리스로 넘긴 것

| 발견 | 내용 | 넘긴 이유 | 준비 조건 |
|---|---|---|---|
| F5 | 티어 카드·요약 밑 투명 창 영역이 게임 누름을 먹음 | 창을 둘로 나누어야(패널 창 + 카드 창) 함, 규모 L·실기기 검증 | O1 의 앵커 배치가 안정된 뒤 |
| Q6-1 | 한글 자모 분해 접두 매칭 | 규모 M, DeckSearch 매칭 경로 전체 | O2 뒤, `HangulJamo.kt` + 테스트 |
| Q12 | 검색 토큰을 창 닫음·재시작 뒤에도 유지 | 판 경계(S9) 없이는 지난 판 조건이 남는 부작용 | S9 와 함께 |
| S2-3 | 빠른 설정 타일 | 알림 3상태로 대체, 실기기 필요 | O1 알림 개편 뒤 사용 데이터 |
| S4(1~3) | 프로세스 종료 뒤 직접 띄운 창 복구, 죽음 알림 | 규모 M, `restoreAfterRestart` 재설계 | — |
| S6 | 게임 중 동기화가 끝나면 보던 덱이 목록으로 튐 | `DeckRepository.sync` 즉시 교체를 '다음 판까지 보류'로 바꾸는 설계 | A4 의 `syncing` 상태 뒤 |
| S9 | 판 경계: 검색 칩·펼침·레벨을 새 판에 초기화 | 판 시작 신호(rating_changes 는 15분 늦음)가 불확실 | 게임 감지 신호 개선 |
| S10 | 오버레이에 데이터 나이 표시 | 낮음, 머리줄 공간 | 메뉴에 한 줄로 |
| S11 | 권한 회수·감지 불명 때 알림 문구 | 낮음, 알림 4번째 상태 | O1 뒤 |
| S13 | 가장자리 칩 드래그가 뒤로 제스처와 충돌 | 시스템 제스처 제외 영역 API 실기기 검증 | — |
| N9 | 앱 조회 조건 → 오버레이 공유 규칙 정리 | 사용자 결정(조건 저장·공유) 범위를 다시 정해야 함 | 사용자 결정 |
| N13 | 목록 '촘촘히 보기' 밀도 전환 | A2 카드 182dp 로 효과 대부분 확보 | A2 뒤 사용 데이터 |
| N16~N18 | 내 정보 7묶음 재편, 전적 폼 접기, 도감 조작 줄 7개 | A4 에 최소만 넣음; 도감 재설계는 별도 | — |
| V22 | 도감 표기·필터 모양 | 낮음 | N18 과 함께 |
| V23 | 검색 탭 빈 화면 | 결정 1 에 따라 소멸 또는 보류 | 결정 1 |
| R10(나머지) | 요약 레벨 사이 변화 애니메이션·강조 | 규모 M | O4 뒤 |
| V28(블러) | 오버레이 배경 블러 | API 31+ 전용, GPU 부하 | 불투명 100% 로 대체됨 |

---

## 5. MASTER.md 전역 규칙 확정본 (P2 가 그대로 옮긴다)

app_visual 의 규칙 제안 R1~R10 을 사용자 결정과 맞춰 확정한 것. 기존 MASTER 조항과 다르면 이 글이 우선한다.

**규칙 1 · 글자 크기는 여섯 단계.** 11 · 12 · 14 · 16 · 20 · 28sp. 11sp 는 세 낱말 이하 라벨에만. `.sp` 리터럴은 `Theme.kt` 와 `OverlayTheme.kt` 에만 있고, 화면 코드는 `MaterialTheme.typography.*` 또는 `OverlayType.*` 만 쓴다. 반 단계(10.5·11.5·13.5) 금지. 모든 스타일에 `tnum`.

**규칙 2 · 보조 글자 줄은 카드·섹션마다 하나.** 카드는 `운영 · 주 특성 · (중국/표본 적음 배지)` 한 줄만. 표본 수·출처 이름·약어(n=, metatft, KR)는 카드와 섹션에 두지 않고 '데이터 출처' 시트에만 둔다.

**규칙 3 · 색은 뜻 하나.** `primary`(파랑) = 누를 수 있음·선택됨. 제목은 `onSurface`. `Positive`/`Negative` 는 반드시 ▲/▼ 와 함께. `Gold` = 1등·3성. 등급색 5개는 등급 배지에만 쓴다. `KrCyan` 삭제. 배경은 그래파이트 3단(`Background` < `Surface` < `SurfaceContainerHighest`), 카드 면은 바탕보다 반드시 한 단 밝다.

**규칙 4 · 모서리와 아이콘.** 모서리 6(배지) · 8(칩·버튼) · 12(카드) · 16(시트·대화상자) · 원(얼굴). 아이콘 16 · 20 · 24dp, `Filled` 계열만.

**규칙 5 · 배지 한 모양.** 높이 20dp, 최소 폭 20dp, 모서리 6dp, 좌우 6dp, 글자 11sp Bold. 등급 배지 = 등급색 채움 + `Background` 색 글자(metatft). 중국 한정 덱 = 같은 크기의 1.5dp 테두리 + 등급색 글자, 채움 없음, 옆에 `'중국'` 글자 배지. 편집 덱 = 테두리형 + `'편'`. 그 밖의 배지(`중국`·`표본 적음`·`편`·구간)는 `surfaceVariant` 채움 + `onSurfaceVariant` 글자. 등급을 색만으로, 또는 글자만으로 구분하지 않는다.

**규칙 6 · 선택 표시 한 모양.** 선택 = `secondaryContainer` 채움 + `onSecondaryContainer` 글자, 테두리 없음. 미선택 = `surfaceVariant` 채움, 테두리 없음. 취소선·체크 아이콘·굵기 변화로 선택을 나타내지 않는다. 오버레이 등급 칸만 예외로 켜짐 = 등급색 0.35 채움, 꺼짐 = `alpha 0.4`.

**규칙 7 · 숫자.** 비율은 소수 1자리(`58.5%`), 0.1% 미만은 `<0.1%`. 평균 등수는 소수 2자리, 문장 안에서는 `4.12등`. 판 수는 `9,847판` / `58.5만 판` / `108만 판`. 음수 부호는 U+2212, 변화량은 ▲/▼ 와 함께. `n=` 표기 금지. 패치는 `패치 18.2`.

**규칙 8 · 용어.** 챔피언(유닛 ✕) · 특성(시너지 ✕) · 증강(증강체 ✕) · 아이템 · 레벨(`렙` 은 오버레이 레벨 칩에만) · TOP4(순방·톱4 ✕) · 평균 등수 · 픽률 · 승률(=1등률) · 구간: 전체/마스터+/다이아+/골드~에메랄드/실버 이하(짧게: 골드~에메) · 지역: 한국/글로벌/중국 · 새로고침(갱신 ✕) · 오버레이 · 게임 연동 · 자동으로 띄우기 · 조건 초기화 · 운영 어휘 {빠른 8레벨, 빠른 9레벨, N레벨 리롤, 표준 운영, 최종 N레벨}.

**규칙 9 · 여백.** 화면 좌우 16dp. 간격은 4 · 8 · 12 · 16 · 24 만. 카드 안 12dp, 카드 사이 8dp, 섹션 제목 위 24dp 아래 8dp. 누름 영역 최소 44×36dp(오버레이 머리줄 버튼 포함). 예외는 오버레이 검색 줄뿐: 등급 칸 28dp·간격 6dp, `완료` 44×32dp.

**규칙 10 · 문장.** 한 줄 안내·라벨은 마침표 없이, `'…'`(U+2026) 사용, 어절 단위 줄바꿈(`wordBreak = Phrase`), 설명문은 `~합니다` 한 문장. 오류·빈 상태는 `'무엇이 · 왜 · 다음 행동'` 한 줄. 버튼은 동사형 2~4글자(`새로고침`·`조건 초기화`·`완료`).

**오버레이 추가 규칙.** 창은 불투명 100%. 본문 최소 11sp, 덱 설명 12sp 최대 2줄. 머리줄 = `[접기][←][제목][⋯]` 고정, 드문 기능은 `⋯` 메뉴. `접기` 는 칩 자리(±4dp). 패널은 칩에서 화면 중앙 쪽으로 자라며 가로 목록 최대 200dp. 게임 위에 토스트를 띄우지 않는다. 티어 카드는 기본 꺼짐.

**덱 카드.** 1줄 배지·별칭·추세·평균 등수, 2줄 운영·주 특성, 3줄 캐리 40dp + 아이템 12dp + 나머지 얼굴 한 줄, 4줄 수치. 수치 줄은 현행 4수치(평균 등수·TOP4·승률·픽률)를 유지하되 **"L4 결정 보류"** — 결정 2 가 나면 3값(평균 등수·TOP4·픽률)으로 고친다. 카드 높이 ≤ 182dp.

---

## 부록 · 검증 근거 요약

- R1 재집계(decks.json 77덱): 별칭 챔피언이 상위 2 캐리가 아닌 덱 9, 보드에 없는 덱 1, 특성 불일치 1(`주문술사 카직스`). 두 낱말 이름(불타는 묘목·장로 드래곤·마스터 이)은 오검출이므로 verify.py 는 가장 긴 이름부터 맞춘다.
- R2: goldem 기본 목록 S 13개 중 중국 한정·편집 8개. meta S 평균 등수 4.01~4.22, group 2.07~2.96(척도가 다른 값이 같은 배지). 편집 덱 14317 은 현재 출처 없음.
- R6: 같은 유닛 집합 7쌍, 같은 요약 문장 3쌍. R7: 기본 목록 설명에 `'표준'` 3, `'N레벨 완성'` 16.
- R5/F6: 가로 목록 스크린샷 높이 ≈ 화면의 85%, 4.5행. L8: 조작부 309dp. D3: meta 덱 `finalLevel` 9 는 `dict(rep)`(fetch_decks.py 1133) 가 lol.qq 대표 덱 값을 복사한 것, metatft 상위는 8.
- Q2/결정 5: 현재 검색 중 플래그는 `FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH` 라 창 밖 누름은 게임에 그대로 가고 오버레이는 `ACTION_OUTSIDE` 만 받는다. 삼키려면 `FLAG_NOT_TOUCH_MODAL` 을 빼야 하며, 그러면 그 누름은 게임에 닿지 않는다.

