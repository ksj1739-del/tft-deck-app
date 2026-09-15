# FloaTFT 디자인 시스템 (MASTER)

작성 2026-09-16. `ui-ux-pro-max` 스킬 2.13.0 의 검색 결과를 FloaTFT(한국어 TFT 덱 도우미, Android · Jetpack Compose · Material 3)에 맞게 **검증하고 조정한 것**이다. 화면별 예외는 `pages/<화면>.md` 에 두고, 없으면 이 문서를 따른다.

## 1. 방향

| 항목 | 결정 | 근거 |
|---|---|---|
| 제품 유형 | 게임 도우미 | color 검색 1위 `Gaming` 팔레트(제품 유형 일치) |
| 스타일 | 다크 **Data-Dense Dashboard** + Minimalism/Swiss | style 검색 `data-dense-dashboard`(다크 지원, 성능 부담 낮음, 접근성 위험 낮음), 2회차 `--design-system` 의 Minimalism & Swiss |
| 제외한 추천 | 1회차 `3D & Hyperrealism`, 랜딩 페이지 패턴(Hero/Feature/CTA) | 오버레이·밀집 목록에 무겁고 접근성 위험이 높다. 랜딩 패턴은 앱 화면에 해당하지 않아 설계 문서의 앱 구조를 따른다 |
| 다이얼 | variance 3~5 · motion 2~3(은은) · density 8(대시보드) | 게임 위에서 빠르게 읽는 정보 앱 |

## 2. 색 토큰 (다크 전용)

대비는 WCAG 상대 휘도 공식으로 직접 계산했다. 본문 4.5:1, 의미 있는 비텍스트(경계·아이콘·포커스) 3:1.

| 토큰 | 값 | 용도 | 확인한 대비 |
|---|---|---|---|
| background | `#0F0F23` | 화면 바탕 | — |
| surface | `#1E1C35` | 카드 | — |
| surfaceVariant | `#27273B` | 흐린 영역, 입력칸 | — |
| surfaceElevated | `#2A2745` | 시트·메뉴·오버레이 카드 | — |
| onSurface | `#E2E8F0` | 본문 | 바탕 15.31 · 카드 13.38 · 올린면 11.55 |
| onSurfaceVariant | `#94A3B8` | 보조 글자 | 바탕 7.36 · 카드 6.44 · 흐린면 5.69 · 올린면 5.55 |
| primary | `#7C3AED` | 채움, 선택 상태, 비텍스트 | 바탕 3.31(비텍스트만) |
| onPrimary | `#FFFFFF` | primary 위 글자 | 5.70 |
| primaryContainer / onPrimaryContainer | `#3B2A6B` / `#C4B5FD` | 선택된 칩·탭 | 6.58 |
| secondary | `#A78BFA` | 강조 글자·링크·아이콘, 포커스 링 | 바탕 6.93 · 카드 6.06 · 올린면 5.23 |
| tertiary(accent) | `#F43F5E` | 강조 채움(행동 버튼, 핵심 배지) | 채움 전용 |
| onTertiary | `#0F0F23` | accent 위 글자 | 5.14 |
| accentText | `#FB7185` | 강조 글자(rose) | 카드 6.13 · 바탕 7.01 |
| outline | `#7C6BC0` | 의미 있는 경계(버튼 테두리, 입력칸) | 카드 3.71 |
| outlineVariant | `#3A3656` | 장식 구분선 | 장식 전용 |
| error | `#F87171` | 오류 글자 | 카드 5.97 |
| gold | `#FBBF24` | 보상, 3성, 금색 강조 글자 | 카드 9.88 |
| positive / negative | `#4ADE80` / `#F87171` | 순방·상승 / 하위·하락 | 카드 9.47 / 5.97 |

등급 배지는 채움 + 글자 `#0F0F23`:

| 등급 | 채움 | 글자 대비 |
|---|---|---|
| S | `#F43F5E` | 5.14 |
| A | `#FB923C` | 8.34 |
| B | `#FACC15` | 12.32 |
| C | `#4ADE80` | 10.83 |
| D | `#94A3B8` | 7.36 |

**금지**
- `#7C3AED`(primary)를 글자색으로 쓰지 않는다(3.31).
- rose(`#F43F5E`) 위에 흰 글자를 쓰지 않는다(3.67). 어두운 글자를 쓴다.
- `#EF4444` 를 카드 위 글자로 쓰지 않는다(4.38). `#F87171` 을 쓴다.
- `#4C1D95` 를 의미 있는 경계로 쓰지 않는다(1.72).
- 화면 코드에 hex 를 직접 쓰지 않는다. 위 토큰(`MaterialTheme.colorScheme` + 확장 토큰)만 쓴다.
- 색만으로 뜻을 전하지 않는다. 상승·하락은 ▲▼, 등급은 글자, 순방은 숫자를 함께 쓴다.

## 3. 글꼴

| 역할 | 글꼴 | 비고 |
|---|---|---|
| 한글 본문·제목 | 시스템 기본(Noto Sans CJK) | 스킬의 게이밍 조합(Russo One / Chakra Petch)은 한글 글리프가 없다 |
| 숫자·등급 글자·영문 라벨 | Chakra Petch(`Gaming Bold` 조합의 본문체) | 적용 전까지는 시스템 글꼴 + 고정폭 숫자(`fontFeatureSettings = "tnum"`) |
| 워드마크 `FloaTFT` | Russo One | 로고 이미지에 포함하거나 제목에서만 |

크기(sp): label 12 · body 14 · title 16 · headline 20 · display 28, 수치 강조 18~24. 본문은 12sp 미만 금지. 오버레이만 설계 §7 에 따라 최소 10sp 이고, 그때는 대비 7:1 이상 색(`onSurface`, `onSurfaceVariant` 는 바탕 위)만 쓴다. 크기는 `MaterialTheme.typography` 로만 지정한다.

## 4. 간격 · 모양 · 크기

- 간격 리듬 4/8dp: `4 · 8 · 12 · 16 · 24 · 32`
- 카드 안쪽 12dp, 카드 사이 8dp, 섹션 사이 24dp
- 모서리: 칩·배지 8dp, 카드 12dp, 시트·다이얼로그 16dp
- 터치 영역 최소 48dp(시각 아이콘 24dp + 여백), 인접한 터치 대상 사이 8dp 이상
- 아이콘 크기 토큰 16 / 20 / 24dp, Material Symbols 한 계열, 같은 위계에서 채움·윤곽을 섞지 않는다
- 이모지를 아이콘으로 쓰지 않는다

## 5. 모션

- 누름 피드백 80~150ms(리플), 상태 전환 150~250ms, 나가는 전환은 들어오는 전환보다 짧게
- 시스템 애니메이션 배율이 0이면 즉시 최종 상태
- 오버레이는 게임 위라 반짝임·반복 애니메이션·글로우를 쓰지 않는다

## 6. 컴포넌트 규칙

- 목록: `LazyColumn` + 안정 키(`key = id`), 불변 컬렉션
- 덱 카드: 등급 배지 → 이름 → 4수치(평균 등수·픽률·승률·TOP4, 숫자 글꼴) → 캐리 3명 순. 카드 전체가 하나의 누름 영역
- 칩: 선택 = `primaryContainer` 채움 + `onPrimaryContainer` 글자, 비선택 = `outlineVariant` 테두리 + `onSurfaceVariant` 글자
- 탭·하단 내비게이션: 선택 표시기 `primaryContainer`, 아이콘·라벨 `secondary`
- 버튼: 주 행동 `primary` 채움, 보조 `outline` 테두리
- 오버레이 카드: `surfaceElevated` 92% 불투명, 모서리 12dp
- 스낵바·다이얼로그: `surfaceElevated`, 스크림은 실제 게임 화면 위에서 읽히는지 확인

## 7. 아이콘

- 런처: 사용자가 준 **배경 있는** 이미지를 이 팔레트로 재채색(보라 섬 + 금색 카드). 적응형 아이콘 전경은 원형 마스크 안전 영역에 맞추고 배경색은 `background` 계열.
- 로고: **배경 없는** 이미지를 같은 방식으로 재채색한 투명 PNG. 앱 안 로고와 README 에 쓴다.
- Android 13 테마 아이콘: 투명 로고의 실루엣.

## 8. 전달 전 체크 (스킬 pro-rules 요약)

- [ ] 본문·보조 글자 4.5:1, 의미 있는 비텍스트 3:1
- [ ] 터치 48dp, 인접 간격 8dp, 누름 피드백
- [ ] 토큰만 사용, hex 직접 사용 없음
- [ ] 이모지 아이콘 없음, 아이콘 한 계열
- [ ] 색 외 표시(기호·글자) 병기
- [ ] 시스템 바·제스처 영역 침범 없음, 목록이 고정 바에 가려지지 않음
- [ ] 애니메이션 배율 0, 가장 큰 글자 크기에서 깨지지 않음
- [ ] 작은 폰(360dp)과 가로 화면 확인

## 출처

`ui-ux-pro-max-skill`(nextlevelbuilder, MIT) 검색: `--design-system` 2회("esports gaming stats dashboard", "dark mobile stats app"), color `Gaming`, style `Data-Dense Dashboard`, typography `Gaming Bold`, stack `jetpack-compose`(Theming, Performance), ux(Color Contrast, Touch Target Size, Touch Spacing), `references/pro-rules.md`.
