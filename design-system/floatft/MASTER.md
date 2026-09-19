# FloaTFT 디자인 시스템 (MASTER)

작성 2026-09-16, 개정 2026-09-19. 처음 판은 `ui-ux-pro-max` 스킬 2.13.0 의 검색 결과를 FloaTFT(한국어 TFT 덱 도우미, Android · Jetpack Compose · Material 3)에 맞게 **검증하고 조정한 것**이다. 2026-09-19 판은 UI/UX 적대 검토의 최종 판정(`docs/ux-review/2026-09-19/final_plan.md` §5)을 전역 규칙으로 옮기고, 토큰 값과 대비를 새로 계산했다. 예전 조항과 §2 가 다르면 §2 가 우선한다. 화면별 예외는 `pages/<화면>.md` 에 두고, 없으면 이 문서를 따른다.

코드 위치: 색·글자·모양 토큰 `ui/theme/Theme.kt`, 공용 컴포넌트 `ui/components/Components.kt`·`StatChips.kt`, 숫자·구간 표기 `ui/UiUtils.kt`(단위 테스트 `UiUtilsFormatTest`), 오버레이 글자 `overlay/OverlayTheme.kt` 의 `OverlayType`.

## 1. 방향

| 항목 | 결정 | 근거 |
|---|---|---|
| 제품 유형 | 게임 도우미 | color 검색 1위 `Gaming` 팔레트(제품 유형 일치) |
| 스타일 | 다크 **Data-Dense Dashboard** + Minimalism/Swiss | style 검색 `data-dense-dashboard`(다크 지원, 성능 부담 낮음, 접근성 위험 낮음), 2회차 `--design-system` 의 Minimalism & Swiss |
| 제외한 추천 | 1회차 `3D & Hyperrealism`, 랜딩 페이지 패턴(Hero/Feature/CTA) | 오버레이·밀집 목록에 무겁고 접근성 위험이 높다. 랜딩 패턴은 앱 화면에 해당하지 않아 설계 문서의 앱 구조를 따른다 |
| 다이얼 | variance 3~5 · motion 2~3(은은) · density 8(대시보드) | 게임 위에서 빠르게 읽는 정보 앱 |
| 색 방향 (2026-09-19 개정) | 무채색 그래파이트 바탕 + 포인트 파랑 하나 | 사용자 요청("보라 계열을 빼고 요즘 앱처럼 모던·깔끔하게"). 크롬은 무채색으로 물러나고 색은 등급·수치 같은 의미에 쓴다. 파랑은 TFT 육각 보드의 색이기도 하다 |

## 2. 전역 규칙 (2026-09-19 확정)

app_visual 의 규칙 제안 R1~R10 을 사용자 결정과 맞춰 확정한 것이다(final_plan.md §5). `>` 로 시작하는 줄은 옮기면서 코드에 맞춰 덧붙인 구현 메모다.

**규칙 1 · 글자 크기는 여섯 단계.** 11 · 12 · 14 · 16 · 20 · 28sp. 11sp 는 세 낱말 이하 라벨에만. `.sp` 리터럴은 `Theme.kt` 와 `OverlayTheme.kt` 에만 있고, 화면 코드는 `MaterialTheme.typography.*` 또는 `OverlayType.*` 만 쓴다. 반 단계(10.5·11.5·13.5) 금지. 모든 스타일에 `tnum`.

> 스타일별 크기는 §4. 쓰지 않는 display·headline 칸도 28·20sp 로 묶어 M3 기본값이 새지 않게 했다.

**규칙 2 · 보조 글자 줄은 카드·섹션마다 하나.** 카드는 `운영 · 주 특성 · (중국/표본 적음 배지)` 한 줄만. 표본 수·출처 이름·약어(n=, metatft, KR)는 카드와 섹션에 두지 않고 '데이터 출처' 시트에만 둔다.

**규칙 3 · 색은 뜻 하나.** `primary`(파랑) = 누를 수 있음·선택됨. 제목은 `onSurface`. `Positive`/`Negative` 는 반드시 ▲/▼ 와 함께. `Gold` = 1등·3성. 등급색 5개는 등급 배지에만 쓴다. `KrCyan` 삭제. 배경은 그래파이트 3단(`Background` < `Surface` < `SurfaceContainerHighest`), 카드 면은 바탕보다 반드시 한 단 밝다.

> 3단은 colorScheme 기준 `background` #0C0E12 < `surface`(= `surfaceContainer`) #1B1F26 < `surfaceContainerHighest`(카드 면) #22262E. 코드 이름 `FloaColors.SurfaceContainer`·`FloaColors.SurfaceContainerHighest`. `FloaColors.Surface`(#171A20)는 오버레이 패널 전용이다.
> 캐리 얼굴은 파랑 대신 2dp `onSurface` 테두리(파랑은 3코스트 테두리와도 겹쳤다). `KrCyan` 은 화면에 나오지 않고 `StatChips.kt` 에 `@Deprecated` 로만 남아 있다(용어 정리 단계에서 지운다).

**규칙 4 · 모서리와 아이콘.** 모서리 6(배지) · 8(칩·버튼) · 12(카드) · 16(시트·대화상자) · 원(얼굴). 아이콘 16 · 20 · 24dp, `Filled` 계열만.

> 앱의 챔피언 초상(`UnitPortrait`)은 아직 둥근 네모(6dp, 36dp 미만은 4dp)다. 원은 오버레이 접힘 칩의 얼굴뿐이고, 초상을 원으로 바꾸는 일은 이번 릴리스 작업 묶음에 없다.

**규칙 5 · 배지 한 모양.** 높이 20dp, 최소 폭 20dp, 모서리 6dp, 좌우 6dp, 글자 11sp Bold. 등급 배지 = 등급색 채움 + `Background` 색 글자(metatft). 중국 한정 덱 = 같은 크기의 1.5dp 테두리 + 등급색 글자, 채움 없음, 옆에 `'중국'` 글자 배지. 편집 덱 = 테두리형 + `'편'`. 그 밖의 배지(`중국`·`표본 적음`·`편`·구간)는 `surfaceBright` 채움 + `onSurfaceVariant` 글자. 등급을 색만으로, 또는 글자만으로 구분하지 않는다.

> 판정 원문은 글자 배지 채움이 `surfaceVariant` 였다. 새 카드 면(#22262E)과 오버레이 머리줄(#242932) 위에서 `surfaceVariant`(#1F232A)는 1.04~1.08:1 로 배지 모양이 사라져(V18 이 '구분 안 됨'으로 짚은 1.07 과 같은 수준) `surfaceBright`(#2C313B)로 바꿨다: 카드 위 1.17, 머리줄 위 1.12, 바탕 위 1.48. 위 글자 대비는 onSurfaceVariant 5.11, primary 4.71.
> 글자 배지(`TextBadge`)의 글자는 11sp **Medium**, 등급 배지(`GradeBadge`)는 11sp Bold. 강조(`emphasis`)는 primary 글자('편'). 오버레이는 글자 배지에 10sp 를 쓸 수 있다.

**규칙 6 · 선택 표시 한 모양.** 선택 = `secondaryContainer` 채움 + `onSecondaryContainer` 글자, 테두리 없음. 미선택 = `surfaceVariant` 채움, 테두리 없음. 취소선·체크 아이콘·굵기 변화로 선택을 나타내지 않는다. 오버레이 등급 칸만 예외로 켜짐 = 등급색 0.35 채움, 꺼짐 = `alpha 0.4`.

**규칙 7 · 숫자.** 비율은 소수 1자리(`58.5%`), 0.1% 미만은 `<0.1%`. 평균 등수는 소수 2자리, 문장 안에서는 `4.12등`. 판 수는 `9,847판` / `58.5만 판` / `108만 판`. 음수 부호는 U+2212, 변화량은 ▲/▼ 와 함께. `n=` 표기 금지. 패치는 `패치 18.2`.

> 표기 함수는 §6. 화면 코드는 `String.format` 대신 이 함수만 쓴다. 변화량은 `▲ +0.07` / `▼ −0.13`(기호와 수 사이 U+00A0), 0 이면 기호 없이 `0.00`.

**규칙 8 · 용어.** 챔피언(유닛 ✕) · 특성(시너지 ✕) · 증강(증강체 ✕) · 아이템 · 레벨(`렙` 은 오버레이 레벨 칩에만) · TOP4(순방·톱4 ✕) · 평균 등수 · 픽률 · 승률(=1등률) · 구간: 전체/마스터+/다이아+/골드~에메랄드/실버 이하(짧게: 골드~에메) · 지역: 한국/글로벌/중국 · 새로고침(갱신 ✕) · 오버레이 · 게임 연동 · 자동으로 띄우기 · 조건 초기화 · 운영 어휘 {빠른 8레벨, 빠른 9레벨, N레벨 리롤, 표준 운영, 최종 N레벨}.

**규칙 9 · 여백.** 화면 좌우 16dp. 간격은 4 · 8 · 12 · 16 · 24 만. 카드 안 12dp, 카드 사이 8dp, 섹션 제목 위 24dp 아래 8dp. 누름 영역 최소 44×36dp(오버레이 머리줄 버튼 포함). 예외는 오버레이 검색 줄뿐: 등급 칸 28dp·간격 6dp, `완료` 44×32dp.

**규칙 10 · 문장.** 한 줄 안내·라벨은 마침표 없이, `'…'`(U+2026) 사용, 어절 단위 줄바꿈(`wordBreak = Phrase`), 설명문은 `~합니다` 한 문장. 오류·빈 상태는 `'무엇이 · 왜 · 다음 행동'` 한 줄. 버튼은 동사형 2~4글자(`새로고침`·`조건 초기화`·`완료`).

> 어절 줄바꿈은 `bodySmall`·`bodyMedium`·`titleMedium` 에 `LineBreak(Simple, Normal, Phrase)` + `ko` 로 걸었다. API 33 미만은 기본 동작이고, 실제로 어절에서 끊기는지는 API 33 이상 기기에서 확인한다.

**오버레이 추가 규칙.** 창은 불투명 100%. 본문 최소 11sp, 덱 설명 12sp 최대 2줄. 머리줄 = `[접기][←][제목][⋯]` 고정, 드문 기능은 `⋯` 메뉴. `접기` 는 칩 자리(±4dp). 패널은 칩에서 화면 중앙 쪽으로 자라며 가로 목록 최대 200dp. 게임 위에 토스트를 띄우지 않는다. 티어 카드는 기본 꺼짐.

**덱 카드.** 1줄 배지·별칭·추세·평균 등수, 2줄 운영·주 특성, 3줄 캐리 40dp + 아이템 12dp + 나머지 얼굴 한 줄, 4줄 수치. **L4 결정: 카드는 평균 등수·TOP4·픽률 3값(2026-09-19 확정).** 승률은 상세에서 본다. 카드 높이 ≤ 182dp.

## 3. 색 토큰 (다크 전용)

대비는 WCAG 상대 휘도 공식으로 직접 계산했다. 글자 4.5:1, 의미 있는 비텍스트(경계·아이콘·포커스) 3:1. 표의 면 이름: 바탕 #0C0E12 · 면(surface) #1B1F26 · 흐린면(surfaceVariant) #1F232A · 카드(surfaceContainerHighest) #22262E · 올린면(surfaceContainerHigh) #242932 · 배지(surfaceBright) #2C313B.

| 토큰(colorScheme) | 코드 | 값 | 용도 | 확인한 대비 |
|---|---|---|---|---|
| background | `FloaColors.Background` | `#0C0E12` | 화면 바탕(그래파이트) | — |
| surface · surfaceContainer | `FloaColors.SurfaceContainer` | `#1B1F26` | 바탕보다 한 단 밝은 면: 앱바·내비게이션 바·메뉴·설정 묶음 | 바탕과 1.17 |
| surfaceContainerHighest | `FloaColors.SurfaceContainerHighest` | `#22262E` | 덱 카드 면(테두리 없음) | 바탕과 1.27 · 면과 1.09 |
| surfaceContainerHigh | `FloaColors.SurfaceElevated` | `#242932` | 대화상자·검색 후보·오버레이 머리줄 | 바탕과 1.32 |
| surfaceContainerLow | — | `#101216` | 바텀시트(M3 기본) | — |
| surfaceVariant | `FloaColors.SurfaceVariant` | `#1F232A` | 흐린 영역, 입력칸, 미선택 칩, 특성 칩 | 바탕과 1.23 |
| surfaceBright | `FloaColors.SurfaceBright` | `#2C313B` | 글자 배지 채움 | 카드와 1.17 · 올린면과 1.12 · 바탕과 1.48 |
| (오버레이 패널) | `FloaColors.Surface` | `#171A20` | 오버레이 패널·티어 카드 바탕. 앱 화면에는 쓰지 않는다 | — |
| onSurface | `FloaColors.OnSurface` | `#E8EBF0` | 본문·제목·캐리 테두리 | 바탕 16.16 · 면 13.83 · 카드 12.69 · 올린면 12.22 · 배지 10.92 |
| onSurfaceVariant | `FloaColors.OnSurfaceVariant` | `#9AA3AF` | 보조 글자 | 바탕 7.57 · 면 6.48 · 흐린면 6.18 · 카드 5.94 · 올린면 5.72 · 배지 5.11 |
| primary | `FloaColors.Primary` | `#5B9BFF` | 누를 수 있음·선택됨: 채움, 링크·텍스트 버튼 글자, 강조 배지 글자 | 바탕 6.97 · 면 5.96 · 카드 5.47 · 올린면 5.27 · 배지 4.71 |
| onPrimary | `FloaColors.OnPrimary` | `#06101F` | primary 채움 위 글자 | 6.88 |
| primaryContainer / onPrimaryContainer | | `#172A47` / `#CFE1FF` | 검색으로 고른 유닛 칸 등 강조 배경 | 10.88 |
| secondaryContainer / onSecondaryContainer | `FloaColors.SecondaryContainer` / `OnSecondaryContainer` | `#1D2B42` / `#D6E4FF` | 선택된 칩·탭 표시기 | 11.11 (채움은 바탕과 1.36, 흐린면과 1.11) |
| secondary | `FloaColors.Secondary` | `#8DBBFF` | 오버레이 강조 글자·아이콘, 포커스 링 | 바탕 9.83 · 카드 7.72 · 올린면 7.43 |
| tertiary(accent) | `FloaColors.Accent` | `#F43F5E` | 강조 채움(핵심 배지) | 채움 전용 |
| onTertiary | | `#0C0E12` | accent 위 글자 | 5.26 |
| accentText | `FloaColors.AccentText` | `#FB7185` | 강조 글자(rose) | 바탕 7.18 · 카드 5.63 |
| outline | `FloaColors.Outline` | `#6B7482` | 의미 있는 경계(버튼 테두리, 입력칸, 스위치) | 바탕 4.09 · 면 3.50 · 카드 3.21 · 올린면 3.09 |
| outlineVariant | `FloaColors.OutlineVariant` | `#2A2F38` | 장식 구분선, 아이템 자리 표시 | 장식 전용(바탕 1.44 · 카드 1.13) |
| error | `FloaColors.Error` | `#F87171` | 오류 글자 | 면 5.98 · 카드 5.48 |
| gold | `FloaColors.Gold` | `#FBBF24` | 1등·3성 | 카드 9.08 |
| positive / negative | `FloaColors.Positive` / `Negative` | `#4ADE80` / `#F87171` | 좋음(상승·순방) / 나쁨(하락·하위), ▲/▼ 와 함께 | 카드 8.70 / 5.48 |

등급 팔레트(등급 배지 전용). 채움형은 등급색 위에 `Background` 글자, 테두리형(중국 한정·편집)은 등급색 글자와 1.5dp 등급색 테두리다.

| 등급 | 색 | 채움 위 어두운 글자 | 테두리형 글자 · 바탕 | 카드 | 오버레이 패널 |
|---|---|---|---|---|---|
| S | `#F472B6` | 7.29 | 7.29 | 5.73 | 6.58 |
| A | `#FB923C` | 8.53 | 8.53 | 6.70 | 7.70 |
| B | `#FACC15` | 12.61 | 12.61 | 9.90 | 11.38 |
| C | `#A3E635` | 12.81 | 12.81 | 10.06 | 11.56 |
| D | `#94A3B8` | 7.53 | 7.53 | 5.91 | 6.80 |
| 등급 없음 | `#8D9AB0` | 6.79 | 6.79 | 5.33 | 6.13 |

- 2026-09-19 에 S 를 `#FB7185` → `#F472B6`(Negative `#F87171` 과 ΔE 10.5 → 37.9), C 를 `#4ADE80` → `#A3E635`(Positive 와 같은 값이던 것을 ΔE 41.0)으로 옮겼다. 등급과 좋고 나쁨이 같은 색으로 읽히지 않게 한다(V10).
- 편집 등급(SS~C)도 같은 팔레트를 쓴다(SS = S 색). 예전처럼 한 칸 밀지 않고 모양(테두리형 + `'편'`)으로 구분한다.

**금지**
- primary 채움 위에 흰 글자를 쓰지 않는다(2.77). `onPrimary` 를 쓴다.
- rose(`#F43F5E`) 위에 흰 글자를 쓰지 않는다(3.67). 어두운 글자를 쓴다.
- `#EF4444` 를 카드 위 글자로 쓰지 않는다(4.03). `#F87171` 을 쓴다.
- 카드 면 위에 `surfaceVariant` 채움 배지를 얹지 않는다(1.04, 모양이 사라짐). 글자 배지는 `TextBadge`(surfaceBright)를 쓴다.
- 보라 계열(색상각 250~300°)을 크롬 색으로 되살리지 않는다. 보라는 게임의 4코스트 표시에만 남는다.
- 화면 코드에 hex 를 직접 쓰지 않는다. 위 토큰(`MaterialTheme.colorScheme` + `FloaColors`)만 쓴다.
- 색만으로 뜻을 전하지 않는다. 상승·하락은 ▲▼, 등급은 글자, 순방은 숫자를 함께 쓴다.

## 4. 글자

| 역할 | 글꼴 | 비고 |
|---|---|---|
| 한글 본문·제목 | 시스템 기본(Noto Sans CJK) | 스킬의 게이밍 조합(Russo One / Chakra Petch)은 한글 글리프가 없다 |
| 숫자·등급 글자·영문 라벨 | Chakra Petch(`Gaming Bold` 조합의 본문체) | 적용 전까지는 시스템 글꼴 + 고정폭 숫자(`fontFeatureSettings = "tnum"`) |
| 워드마크 `FloaTFT` | Russo One | 로고 이미지에 포함하거나 제목에서만 |

크기 여섯 단계(규칙 1). 모든 스타일에 `tnum`.

| 크기 | 스타일 | 굵기 · 줄 높이 | 쓰는 곳 |
|---|---|---|---|
| 11sp | `labelSmall` | Medium · 14 | 배지, 수치 라벨(세 낱말 이하, 문장 금지) |
| 12sp | `labelMedium` / `bodySmall` | Medium · 16 / Regular · 18 | 특성 칩 / 보조 글자 줄·설명 |
| 14sp | `labelLarge` / `bodyMedium` / `titleSmall` | Medium · 20 / Regular · 20 / SemiBold · 20 | 선택 칩 / 본문 / 섹션 제목 |
| 16sp | `bodyLarge` / `titleMedium` | Regular · 24 / SemiBold · 22 | 큰 본문 / 카드 별칭·수치 값 |
| 20sp | `titleLarge` / `headlineSmall` | Bold · 26 | 상세 제목, 대화상자 제목 |
| 28sp | `displaySmall` | Bold · 34 | 크게 강조할 숫자 |

- `bodySmall`·`bodyMedium`·`titleMedium` 은 어절 단위 줄바꿈(규칙 10).
- 오버레이는 앱 테마 밖에서 그리므로 `OverlayType` 한 곳에서 크기를 정한다. 본문 최소 11sp, 얼굴 위 첫 글자 10sp, 3성 별 8sp 만 예외.

## 5. 모양 · 간격 · 누름 영역

- 모서리(규칙 4): 배지 6 · 칩·버튼 8 · 카드 12 · 시트·대화상자 16(`Shapes.extraLarge` 16) · 얼굴 원(§2 규칙 4 메모).
- 간격(규칙 9): `4 · 8 · 12 · 16 · 24`. 화면 좌우 16dp(`ScreenPadding`), 카드 안 12dp, 카드 사이 8dp, 섹션 제목 위 24dp·아래 8dp(`SectionTitle`). 배지 안 좌우 6dp 는 배지 한 모양(규칙 5)의 값이다.
- 누름 영역 최소 44×36dp. 선택 칩은 보이는 높이 32dp 에 누름 영역 48dp(`minimumInteractiveComponentSize`). 인접한 누름 대상 사이 8dp 이상. 예외는 오버레이 검색 줄(규칙 9).
- 아이콘 16 / 20 / 24dp, Material Icons `Filled` 한 계열. 이모지를 아이콘으로 쓰지 않는다.

## 6. 공용 컴포넌트 · 표기 함수

화면은 아래를 가져다 쓰고 같은 모양을 새로 그리지 않는다. 배지·칩은 오버레이(앱 테마 밖)에서도 같은 색이 나오도록 `FloaColors` 를 직접 읽는다.

| 이름 | 규칙 | 모양 |
|---|---|---|
| `GradeBadge(grade, style = Filled, modifier)` | 5 | 20dp · 모서리 6 · 좌우 6 · 11sp Bold. `Filled`(metatft) / `Outlined`(중국 한정, 1.5dp) / `Editorial`(테두리형 + `'편'`) |
| `TextBadge(text, emphasis = false, modifier, textStyle = labelSmall)` | 5 | 20dp · 모서리 6 · surfaceBright · onSurfaceVariant 11sp Medium(강조면 primary) |
| `SectionTitle(text, modifier, trailing)` | 3·9 | titleSmall · onSurface · 위 24 아래 8 · 제목 의미(heading). 파랑 제목 금지 |
| `FloaFilterChip(selected, label, onClick, modifier, enabled, leading)` | 6 | 32dp · 모서리 8 · 테두리 없음. 미선택 surfaceVariant/onSurfaceVariant, 선택 secondaryContainer/onSecondaryContainer |
| `BucketChips(...)` | 6 | 구간 5개를 `FloaFilterChip` 한 줄로 |
| `TraitChip(trait, assetBase, modifier)` | 3 | surfaceVariant · onSurface 12sp · 앞에 16dp 특성 아이콘(단계색은 아이콘 칸에만) |
| `UnitPortrait(...)` | 3 | 코스트색 바탕 + 이름 첫 글자를 깔고 이미지가 덮음, 캐리 2dp onSurface 테두리, 새 유닛 왼쪽 위 6dp Positive 점 |
| `ItemIcons(items, assetBase, size, spacing)` | — | 자리마다 1dp outlineVariant 빈 상자를 깔고 이미지가 덮음 |
| `formatPct` · `formatPick` | 7 | `58.5%`, `<0.1%`, 값 없음 `-` |
| `formatAvg` · `formatAvgRank` | 7 | `4.12`(수치 칸) · `4.12등`(문장) |
| `formatGames` | 7 | `9,847판` · `58.5만 판` · `108만 판` |
| `formatDelta` | 3·7 | `▲ +0.07` · `▼ −0.13` · `0.00` |
| `bucketLabel` · `bucketShortLabel` | 8 | `골드~에메랄드`·`실버 이하` / `골드~에메` |

- 예전 컴포넌트 `TierBadge`·`OnlyInChinaBadge`·`OutlineBadge`·`tierColor` 는 위 것으로 위임하고 `@Deprecated` 다. 새 코드에서 쓰지 않는다.
- 목록: `LazyColumn` + 안정 키(`key = id`), 불변 컬렉션.
- 덱 카드: §2 '덱 카드'. 카드 면 `surfaceContainerHighest`, 테두리 없음, 고정한 덱만 primary 60% 1.5dp 테두리. 카드 전체가 하나의 누름 영역.
- 버튼: 주 행동 `primary` 채움 + `onPrimary` 글자, 보조는 테두리 버튼.
- 탭·하단 내비게이션: 선택 표시기 `secondaryContainer`(규칙 6).
- 대화상자: `surfaceContainerHigh`, 모서리 16dp, 제목 `headlineSmall`. 스크림은 실제 게임 화면 위에서 읽히는지 확인한다.
- 오버레이 패널: 불투명 100%(§2 오버레이 추가 규칙).

## 7. 모션

- 누름 피드백 80~150ms(리플), 상태 전환 150~250ms, 나가는 전환은 들어오는 전환보다 짧게
- 시스템 애니메이션 배율이 0이면 즉시 최종 상태
- 오버레이는 게임 위라 반짝임·반복 애니메이션·글로우를 쓰지 않는다

## 8. 앱 아이콘

- 런처: 사용자가 준 **배경 있는** 이미지를 이 팔레트로 재채색(파랑 육각 섬 + 금색 카드, 차콜 `#181B21` 바탕. 2026-09-19 보라에서 옮김). 적응형 아이콘 전경은 원형 마스크 안전 영역에 맞추고 배경색은 `background` 계열.
- 로고: **배경 없는** 이미지를 같은 방식으로 재채색한 투명 PNG. 앱 안 로고와 README 에 쓴다.
- Android 13 테마 아이콘: 투명 로고의 실루엣.

## 9. 전달 전 체크

- [ ] 글자 4.5:1, 의미 있는 비텍스트 3:1(§3 의 면 위에서)
- [ ] `.sp` 리터럴은 `Theme.kt`·`OverlayTheme.kt` 에만, 글자는 여섯 단계
- [ ] 누름 영역 44×36dp 이상(칩은 48dp), 인접 간격 8dp, 누름 피드백
- [ ] 토큰만 사용, hex 직접 사용 없음
- [ ] 배지 한 모양, 선택 표시 한 모양, 파랑은 누를 수 있는 것·선택된 것에만
- [ ] 숫자는 §6 표기 함수로(`n=` 없음, 빼기 U+2212)
- [ ] 용어는 규칙 8, 한 줄 문구는 마침표 없이 `…`
- [ ] 이모지 아이콘 없음, 아이콘 한 계열
- [ ] 색 외 표시(기호·글자) 병기
- [ ] 시스템 바·제스처 영역 침범 없음, 목록이 고정 바에 가려지지 않음
- [ ] 애니메이션 배율 0, 가장 큰 글자 크기에서 깨지지 않음
- [ ] 작은 폰(360dp)과 가로 화면 확인

## 출처

- `ui-ux-pro-max-skill`(nextlevelbuilder, MIT) 검색: `--design-system` 2회("esports gaming stats dashboard", "dark mobile stats app"), color `Gaming`, style `Data-Dense Dashboard`, typography `Gaming Bold`, stack `jetpack-compose`(Theming, Performance), ux(Color Contrast, Touch Target Size, Touch Spacing), `references/pro-rules.md`.
- UI/UX 적대 검토(2026-09-19): `docs/ux-review/2026-09-19/final_plan.md` §5(전역 규칙 확정본), `app_visual.md`(V1~V30, 규칙 제안 R1~R10).
