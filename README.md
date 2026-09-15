# FloaTFT

중국 TFT 공식 통계(`lol.qq.com/tft`)의 덱 데이터를 한국어로 읽는 안드로이드 앱.
metatft에 없는 덱을 가려내고, 추천 아이템으로 덱을 역검색하고, 게임 화면 위에 덱을 띄운다.

현재 대상: **2026.S18 / 패치 16.18** · 덱 26개 (중국 한정 4개)

---

## 구조

```
collector/          매일 한 번 도는 수집기 (파이썬, 의존성 없음)
  fetch_decks.py      원본 4곳 -> 한국어 decks.json
  verify.py           내보내도 되는 상태인지 검사 (CI 게이트)
data/               수집 결과. 앱이 이 두 파일만 받는다
  decks.json          덱 + 검색 인덱스 + 카탈로그 (~277 KB)
  version.json        해시와 메타데이터만 (~600 B)
android/            Kotlin + Jetpack Compose 앱
.github/workflows/  매일 05:00 KST 자동 수집
```

수집을 앱 밖에 둔 이유: 원본이 공개 API가 아니라서 시즌이 바뀌면 경로와 스키마가 흔들린다.
**수집기만 고치면 되고 앱은 재배포하지 않아도 된다.**

---

## 데이터 출처

| 용도 | 출처 |
|---|---|
| 덱 통계 | `game.gtimg.cn/.../lineupJson/{set}/6/lineup_detail_total.json` |
| 현재 시즌 | `lol.qq.com/tft/js/tft-mode-registry.js` 의 `CurrentSet` |
| 한국어 이름·아이콘 | `raw.communitydragon.org/latest/cdragon/tft/ko_kr.json` |
| 덱 코드 | `.../v1/tftchampions-teamplanner.json` 의 `team_planner_code` |
| 대조용 덱 | `api-hc.metatft.com/tft-comps-api/latest_cluster_info` |

세 소스가 모두 같은 `DA_*` ID 체계를 쓴다. 덕분에 중국어/영어 덱 이름을 대조할 필요 없이
**ID 집합 연산**으로 번역과 비교가 정확하게 끝난다.

---

## 수집기

```bash
python collector/fetch_decks.py
python collector/verify.py
```

원본 하나가 죽어도 전체가 멈추지 않는다:

- **lol.qq / 한국어 사전** — 없으면 중단하고 이전 `decks.json`을 그대로 둔다
- **metatft** — 없으면 '중국 한정' 판정을 건너뛴다 (`compared: false`). 잘못된 배지를 다는 것보다 안 다는 쪽을 택한다
- **덱 코드 / 영문명** — 없으면 그 필드만 빠지고 나머지는 정상 동작한다

상태는 `version.sources`에 기록되고 앱의 설정 화면에 그대로 보인다.

### 덱 이름은 새로 만든다

원문이 `【巨龙95】3峡谷野怪2地狱火…` 형태라 대괄호 안 별명은 번역되지 않는다.
특성은 100% 번역되므로 **캐리 + 시너지 구성**으로 한국어 이름을 만든다.

시너지 순서는 숫자가 큰 것부터, 같은 숫자끼리는 *원문 덱 이름에 등장하는 것*을 앞에 둔다.
작성자가 꼽은 핵심이 이름에 먼저 오게 하기 위해서다.

```
5 날렵이 3 기원자 2 엄호대   <-  원문 5约德尔人3神谕2护卫
4 처형자 3 소환사 2 치명적인 꽃 <-  원문 4裁决使3召唤师2绝命花妖
```

### 중국 한정 판정

덱의 정체성을 **최종 배치의 챔피언 ID 집합**으로 보고, metatft 54개 클러스터와
자카드 유사도를 계산해 최고값이 `0.5` 미만이면 중국 한정으로 본다.

실측 분포가 미등재 `0.31~0.45` / 등재 `0.55~1.00`로 뚜렷하게 갈려 경계에 걸치는 덱이 없다.

---

## 덱 코드

인게임 팀 플래너에 붙여넣는 코드를 만든다. **공개 문서(gist)는 세트 13 기준이라 현재와 다르다.**

| | gist (세트 13) | 실제 세트 18 |
|---|---|---|
| 접두사 | `01` | **`02`** |
| 슬롯당 | 2자리 | **3자리** 소문자 hex |
| 값 | 정렬 인덱스 | **`team_planner_code`** |
| 전체 | — | **32자**, 뒤쪽 `0` 패딩 + `TFTSet18` |

```
02 + 3fc 3fb 415 404 40b 430 3ed 428 + 000000 + TFTSet18
```

포맷은 Riot의 `tftchampions-teamplanner.json`과 tftguide.org 팀빌더의 독립 구현을
대조해 확인했다 (`DA_18_Akali_AD` → `team_planner_code 1002` → `0x3EA` → `3ea`, 12/12 일치).

상점에 없는 유닛(소환수 등)은 코드로 표현할 수 없어 제외하고, `teamCode.omitted`에 남겨
앱에서 안내한다.

---

## 앱

Kotlin + Jetpack Compose, minSdk 26, targetSdk 35.

```
data/     Models · DeckRepository · DeckSearch
sync/     DailySyncWorker (WorkManager, 24시간)
overlay/  OverlayService · OverlayContent
ui/       화면 4개 + 공용 컴포넌트
```

**저장을 DB로 하지 않는 이유**: 덱 26개 / 277 KB짜리 읽기 전용 데이터다.
파일로 캐시하고 메모리에 인덱스를 얹는 편이 코드가 훨씬 적고 검색도 즉시 끝난다.

### 동기화

먼저 `version.json`(수백 바이트)만 받아 `contentHash`를 비교하고, 바뀐 경우에만 본체를
내려받는다. 평상시 통신량은 사실상 0이다. 첫 실행은 APK에 동봉한 스냅샷으로 즉시 동작한다.

받은 내용은 임시 파일에 쓰고 교체하므로, 중간에 끊겨도 기존 캐시가 깨지지 않는다.

### 검색

수집 단계에서 만든 역인덱스를 조회만 한다. 전부 메모리라 오프라인에서도 즉시 나온다.

| 축 | 예 |
|---|---|
| 아이템 | `무한의 대검` → 그 아이템을 쓰는 덱 11개, **핵심/대체 구분** |
| 조합 재료 | `곡궁` → 지금 먹은 기본 아이템으로 갈 수 있는 덱 |
| 챔피언 · 시너지 · 증강체 | 이름으로 |

입력은 부분 일치 외에 **초성**(`ㅁㅎㅇ`), **줄임말**(`무대`), **영문명**(`Infinity`)을 받는다.

### 오버레이

게임 위에 덱을 띄운다. `TYPE_APPLICATION_OVERLAY` 창을 WindowManager에 직접 붙이고,
그 창이 사는 동안 프로세스가 죽지 않도록 포그라운드 서비스로 유지한다.

- 기본은 접힌 칩(캐리 아이콘 + 티어). 누르면 펼쳐진다
- 어느 상태든 드래그로 옮기고, 위치를 기억한다. 기본 위치는 좌상단
- `FLAG_NOT_FOCUSABLE` — 포커스를 가져가지 않아 띄운 채로 게임을 그대로 조작할 수 있다
- '다른 앱 위에 표시' 권한은 사용자가 직접 허용해야 한다 (설정 화면에서 안내)

---

## 빌드

```bash
cd android
gradle :app:assembleDebug
```

`local.properties`에 `sdk.dir`이 필요하다. JDK 17.

```bash
gradle :app:testDebugUnitTest
```

단위 테스트는 **실제 `decks.json`을 읽는다**. 모형 데이터가 아니라 배포되는 파일이라
수집기 스키마가 바뀌면 여기서 먼저 깨진다.

---

## 배포 설정

`android/app/build.gradle.kts`의 `FEED_BASE_URL`을 실제 배포 위치로 바꾼다.

```kotlin
buildConfigField("String", "FEED_BASE_URL",
    "\"https://raw.githubusercontent.com/<계정>/<저장소>/main/data/\"")
```

GitHub Actions가 매일 05:00 KST에 수집해 `data/`를 커밋한다.
실패하면 이슈를 하나 열고, 앱은 직전 데이터를 계속 쓴다.

---

## 시즌이 바뀌면

1. `collector/fetch_decks.py`는 `CurrentSet`을 매번 읽으므로 보통 그대로 동작한다
2. `verify.py`가 덱 수·한글화율·인덱스를 검사해 깨진 결과가 배포되는 것을 막는다
3. `version.untranslatedIds`에 새 챔피언/아이템이 쌓이면 CommunityDragon 반영을 기다리면 된다
   (그동안 앱은 원본 ID를 그대로 보여 주고 화면은 비지 않는다)

---

Riot Games가 보증하거나 후원하는 앱이 아니다.
덱 통계는 lol.qq.com/tft, 대조는 metatft.com, 이름과 아이콘은 Community Dragon에서 가져온다.

---

## 문서

- [TFT 통계 사이트 벤치마킹 (2026-09-15)](docs/benchmark/2026-09-15/README.md) — lol.qq.com/tft · lolchess.gg · metatft.com 을 카테고리 5개 × 관점 3개로 채점하고 Fable 이 최종 판정한 기록. 에이전트별 원본 결과(`raw/`)와 응답 샘플(`samples/`)을 함께 보관해 다음 설계에 재활용한다.
