# 추가 벤치마킹: tftactics.gg — 2026-09-15

[README](README.md)의 세 사이트(lol.qq · metatft · lolchess) 비교에 **네 번째 사이트로 넣을 가치가 있는지**만 좁게 본 조사다. 전체 재심사가 아니다.

**결론: UI 아이디어만 가져오고, 데이터원으로는 쓰지 않는다.**
tftactics.gg 는 편집자가 고른 36덱과 영문 도감을 JS 번들에 넣어 둔 사이트다. 통계가 없고, 덱은 기존 소스와 대부분 겹친다. 세트 18 도감은 비어 있는 필드가 많고, 이용약관이 없어 이용 허락을 받을 근거도 없다.

## 조사 개요

| 항목 | 내용 |
|---|---|
| 조사 일시 | 2026-09-15 22:58:15 ~ 23:10:07 KST (13:58~14:10 UTC) |
| 대상 시즌 | 세트 18 (글로벌 패치 18.2, 중국 16.18) |
| 요청 수 | 17회: tftactics.gg 13, web.archive.org 2(CDX), www.critcap.gg 1, sunderarmor.com 1. 요청 사이 최소 0.8초, 응답은 200 16회·404 1회 |
| 방법 | Python urllib, Chrome/120 User-Agent, gzip. 쿠키·Referer·로그인 없음. 운영 주체는 웹 검색 1회로 보충 |
| 브라우저 | 쓰지 않았다. 페이지 한 번에 이미지·광고 요청이 수십~수백 개 붙어 60회 한도를 넘기기 때문이다. 화면 모습은 프리렌더 HTML과 번들 코드로 판단했고, 그런 내용은 [추정]으로 표시했다 |
| 비교 기준 데이터 | metatft `latest_cluster_info`(2026-09-14 22:33 KST 수집, 클러스터 54개), lol.qq `lineup_detail_total.json`(2026-09-15 20:49 KST, 편집 덱 26개), CommunityDragon `ko_kr.json`(2026-09-15 20:59 KST). 모두 이전 조사 때 받아 둔 로컬 파일이다 |
| 스크립트 | `C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/tftactics/` (fetch.py 요청 기록, extract.py 번들 모듈 추출, save_samples.py 샘플 저장) |
| 표기 | **[확인]** 실제 응답·데이터·코드로 확인한 것 / **[추정]** 간접 근거로 판단한 것 |

## 한눈에 보기

| 카테고리 | 데이터 | UX | 구현 | 합계(30) | 판정 |
|---|---:|---:|---:|---:|---|
| 덱 | 3 | 7 | 4 | 14 | UI 아이디어만 |
| 챔피언 | 1 | 4 | 4 | 9 | 제외 |
| 아이템 | 2 | 6 | 5 | 13 | 제외 (UI도 README에 반영된 것과 같음) |
| 증강 | 2 | 3 | 6 | 11 | 제외 |
| 빌드업 | 3 | 6 | 4 | 13 | UI 아이디어만 |

README 합계와 비교하면 덱 카테고리는 lol.qq 18, metatft 21, lolchess 16이다. 점수 근거는 [7. 판단](#7-판단)에 있다.

---

## 1. 메뉴와 기능

사이트는 Create React App 으로 만들고 react-snap 으로 프리렌더한 SPA이며 Netlify에서 서비스한다. 라우트는 번들의 React Router 정의에서 뽑았고, 내용은 프리렌더 HTML과 번들 데이터로 확인했다.

| 메뉴 | 경로 | 보여 주는 것 | 세트 18 상태 | 근거 |
|---|---|---|---|---|
| 덱 티어리스트 | `/tierlist/team-comps/` | S/A/B 그룹. 카드마다 덱 이름, 운영 칩(Standard · Slow Roll (5/6/7) · Fast 8 · Fast 9), Emblem·Augment 배지, 8유닛과 캐리 아이템 3개, 3성 표시. 필터는 운영 방식·챔피언·특성, 검색 가능 | 36덱(S 6 · A 23 · B 7), 머리말에 "Patch 18.2" | [확인] |
| 덱 상세(카드 펼침) | 같은 페이지 | 초반 조합(5유닛), 특성 단계, 캐러셀(완성템과 집을 부품 3개), 대체 유닛(Options 1~3개), 배치(4×7칸) | 레벨업·리롤 가이드 문장은 운영 방식 템플릿뿐. 증강 추천·라운드 표기는 없음 | 섹션·필드 [확인: 코드], 화면 [추정] |
| 팀 코드 복사 | 덱 카드 아이콘 | `"02"` + 유닛마다 `game_id` 3자리 + `"000"` 채움 + `"TFTSet"+세트` | 세트 18 챔피언 `game_id` 0/73이라 코드가 40자가 안 돼 버튼이 생성되지 않음 | 데이터 [확인], 화면 [추정] |
| 챔피언 목록 | `/champions/` | 코스트·특성 필터, 검색 | 73명 | [확인] |
| 챔피언 상세 | `/champions/{소문자 이름}/` (예 `ahri`) | 코스트, 추천 아이템 3개, 스킬(이름·마나·설명), 특성 단계와 같은 특성 유닛 | 스킬·특성 설명에서 **숫자가 빠져 있음**(“within hexes”, 단계 값 "X") | [확인] |
| 챔피언 티어리스트 | `/tierlist/champions/` (메뉴에 없음) | S/A/B 그룹 | 모든 세트의 모든 챔피언이 `tier: 1`. 프리렌더가 없어 index.html 로 대체됨 | 데이터 [확인], 화면 [추정] |
| 챔피언 스탯표 | `/db/champion-stats/` (메뉴에 없음) | 공격(DPS·공속·사거리) / 방어(체력·마나·MR) 표 | 세트 18 체력·방어·공격·공속·DPS **0/73** 채움(모두 `"-"` 또는 0) | 데이터 [확인], 화면 [추정] |
| 아이템 티어리스트 | `/tierlist/items/` | S 13 · A 19 · B 4 아이콘 | 등급이 매겨진 36개 **전부 세트 15~18 공용 객체라 등급이 세트마다 같다**. 세트별 재평가 흔적 없음 | [확인] |
| 아이템 빌더 | `/item-builder/` | 부품을 고르면 조합표(Recipe / Com<플레이어>es Into / Tier) | 동작 | [확인: 코드] |
| 팀 빌더 | `/team-builder/`, `/team-builder/{코드}` | 배치·아이템·성급 편집. 공유 URL은 `{team, chosen, set}` JSON을 lz-string Base64로 압축한 값(`/`→`~`) | 동작. 인게임 팀 코드는 위와 같이 없음 | [확인: 코드] |
| 특성 DB | `/db/origins/`, `/db/classes/` | 효과와 단계 | 기원 19 · 계열 16(CDragon은 36). 단계 값이 "X"로 비어 있음 | [확인] |
| 상점 확률 | `/db/rolling/` | 레벨 1~11 × 1~5코 확률. 헤더에 기물 수(22/20/17/10/9) | 데이터에 세트 구분이 없어 세트 18 값인지 확인 못 함 | 데이터 [확인], 정확성 미검증 |
| 증강 DB | `/db/augments/` | 표(증강 / 희귀도 / 효과), 검색 | 257개. 페이지 제목과 머리말은 "Set 17". 데이터의 편집 등급 `tierlist`(1~4)는 **어디에도 표시되지 않음** | [확인] |
| 세트 업데이트 | `/set-update/` | 세트 18 "Enchanted Wilds" 소개(PBE 2026-07-28), 시스템·특성·챔피언 | 세트 18 | [확인] |
| 통계 | — | 없음. 번들에 "Win Rate", "Avg. Place", "Top 4", "Games" 라벨이 한 번도 나오지 않음 | — | [확인] |
| 오버레이 앱 | Overwolf(Windows) 다운로드 | 홈 소개 문구: 개인 코치, 매치 기록, 보드 트래커, 챔피언 스탯 | PC 전용이라 이번 범위 밖 | [추정: 소개 문구만] |

### 갱신 상태

| 항목 | 값 | 근거 |
|---|---|---|
| 기본 세트 | 18 (드롭다운 15 / 16 / 17 / 18) | [확인: 앱 상태 `set:"18"`, `activeSets`] |
| 패치 표기 | "Patch 18.2" (번들 모듈 120의 `Ver`) | [확인] |
| 갱신 시각 표기 | 화면·데이터·응답 헤더 어디에도 없음. Netlify 응답에 Last-Modified 없음 | [확인] |
| 현재 번들 배포 시점 | `main.616d9066.chunk.js` 는 Wayback CDX 2026년 목록에 없다. 가장 최근 캡처 해시는 `82b1b294`(2026-08-27 03:39 UTC)이므로 그 뒤 ~ 조사 시점 사이에 배포됐다 | 캡처 부재 [확인], 배포일 [추정] |
| 배포 빈도 | 2026년 1~8월 서로 다른 main 번들 해시 38개가 캡처됨(실제 배포 수의 하한) | [확인] |
| 18.2 반영 흔적 | 하향 표시 4덱(Caustic Adaptors · Flora Executioners · Unrivaled · Apex Predator) | 데이터 [확인], 18.2 반영이라는 해석 [추정] |
| 방치된 부분 | SEO 설명 문구와 증강 페이지 머리말은 "set 17", 번들 패치노트 최신은 14.4(2024-02-22 작성), 앱 변경 이력 최신은 "Set 8.5", 세트 18 챔피언 스탯·팀 코드 공란, 챔피언 등급 전원 1, 아이템 등급 세트 공용 | [확인] |

---

## 2. 빌드업 자료

**형태** — 덱 데이터(모듈 121)에서 확인했다. 전부 편집자 작성이고 표본 수·기간·평균 등수 필드는 없다. [확인]

| 필드 | 내용 | 세트 18 실측 |
|---|---|---|
| `mid` (화면 제목 "Early Comp") | 초반 조합 5유닛 한 벌. 레벨·라운드 표기 없음 | 36/36덱에 5유닛. 서로 다른 목록은 30종(같은 목록을 2~3덱이 공유). 최종 보드와 겹치는 유닛 0~5명(0명 3덱) |
| `description` | 덱마다 쓴 글이 아니라 **운영 방식별 템플릿 6종** | Fast 8 15덱: 4단계 초반 8렙으로 4코를 찾고 연승·연패나 경제 증강이 필요 / Slow Roll (7) 8, (6) 6, (5) 4덱: 그 레벨에서 남는 골드로 매 라운드 리롤해 3성 / Fast 9 2덱: 5단계 초반 9렙으로 5코 / Standard 1덱: 3-2 6렙 · 4-1 7렙 · 5-1 8렙 |
| `playstyle` | 카드 칩 (Slow Roll 은 레벨 숫자 포함) | 위와 같음 |
| `characters[].level` · `chosen` | 3성 목표 표시, 강조 유닛 | 3성 57유닛, 2성 1유닛, 강조 9유닛(대부분 리롤 덱의 3성 캐리) |
| `carrousel` | 캐러셀에서 집을 부품과 그 부품으로 만들 완성템 | 36/36덱에 3개 |
| `replacements` | 대체·추가 유닛(`out` → `in`, `out`이 빈 배열이면 추가 투입) | 1개 8덱, 2개 23덱, 3개 5덱 |
| `characters[].position` | 배치 p1~p28(4×7칸) | 사용 칸 22곳 |
| 없음 | 레벨별(4~10) 보드, 레벨업 라운드, 판당 리롤 수, 덱별 증강 추천, 통계 | — |

**기준 소스와 비교**

| 항목 | tftactics | metatft comp_details | lol.qq |
|---|---|---|---|
| 초반(4~7렙) | 초반 조합 5유닛 한 벌, 레벨 표기 없음, 편집 | `early_options`: 4~7렙 유닛 조합별 판수·평균 등수(통계) | 편집 덱 초반(2-3) 보드 |
| 중·후반(7~10렙) | 최종 8유닛과 대체 1~3개 | `options`: 7~10렙(통계) | 편집 덱 중반(4-3)·최종 보드, 승률 조합 `level_change_lineup_data`(7~10렙 통계) |
| 레벨업 시점 | 템플릿 문장. 라운드는 Standard 템플릿에만 있음 | `levels`: 레벨별 도달 라운드(예 5렙 2-5, 8렙 4-2) | 편집 덱 `needLevel` · `needLevel_early` · `needLevel_middle`, 중국어 운영 팁 |
| 리롤 | "Slow Roll (N)" 칩으로 주 리롤 레벨만 | `rerolls`: 레벨별 판당 리롤 수 → 주 리롤 레벨 | 편집 텍스트 |
| 아이템·캐러셀 | 캐리 3템, 캐러셀 부품 3개 | 유닛별 아이템(통계) | `equipment_info`(중국어 서술), 장비 통계 |
| 대체 유닛 | `replacements` | 변형 조합 | `hero_replace` |
| 근거 | 편집, 날짜·표본 없음 | 통계(필터를 무시하는 고정 모집단) | 편집 + 통계 |

lol.qq 필드명은 로컬 `lineup_detail_total.json` 의 detail 키로 [확인]했다.

**판단** — tftactics 빌드업은 초반 조합 한 단계와 운영 방식 템플릿이 전부다. metatft(레벨별 통계)와 lol.qq(단계별 편집 보드와 레벨 필드)보다 정보가 적고 통계도 없어서 가져올 **필드는 없다**. 참고할 것은 표현 방식 두 가지다. 운영 칩에 레벨 숫자를 넣는 방식과, 캐러셀 부품 칩이다.

---

## 3. 세 사이트에 없는 고유한 가치

### 덱 목록 — 대부분 겹친다

tftactics 세트 18 36덱의 최종 보드(8유닛)를 기준 데이터와 자카드 유사도로 비교했다. 유닛은 DA_* apiName 에서 접두사·접미사를 떼어 정규화했고, 챔피언 이름 3개(Lux 개화 · Lux 검은 가시 · Mama Beak)는 매핑하지 못한 채 계산해 약간 낮게 나왔을 수 있다. [확인: 로컬 계산]

| 유사도 기준 | metatft 클러스터 54개 중 가장 비슷한 것 | lol.qq 편집 덱 26개 중 가장 비슷한 것 | 둘 다 미달 |
|---|---:|---:|---:|
| 0.6 이상 | 23 / 36 | 20 / 36 | 8 |
| 0.5 이상 | 27 / 36 | 23 / 36 | 5 |
| 0.4 이상 | 34 / 36 | 29 / 36 | 2 |

0.5 기준으로 둘 다 미달인 5덱은 Fae Rapidfire(B) · Solar Rapidfire(A) · Fae Spellweavers(A) · Riftbeast Summoners(B) · Solar Riftbeasts(B)다. 대부분 B등급 변형이라 앱 덱 목록에 새로 더할 가치가 작다.

### 도감 — 고유 가치 없음

- 챔피언·특성·아이템·증강 설명과 상점 확률은 Riot 게임 데이터다. CommunityDragon ko_kr(한글, 수치 포함)과 metatft lookup 이 더 정확하고 최신이다.
- tftactics 세트 18 도감은 스탯 0/73, 스킬·특성 수치 누락, 챔피언 등급 전원 1, 아이템 등급 세트 공용이다. 기존 소스보다 못하다. [확인]
- 증강의 숨은 편집 등급 `tierlist`(1~4)는 사이트가 공개 화면에 쓰지 않는 값이다. 가져다 쓸 근거가 없다. 증강 편집 티어는 이미 metatft `augments_tiers` 로 계획돼 있다.

### 게임 중 오버레이 참고 정보 — 구조만 참고할 만하다

- 카드 한 장에 운영 칩, 8유닛과 캐리템, 펼침 섹션(초반 조합 / 특성 / 캐러셀 / 대체 / 배치)이 들어간다. 게임 중 한 번에 확인하기 좋은 정보 구조다. [확인: 코드 구조] 폰에서의 실제 모습은 [추정]이다.
- 캐러셀 부품 3개는 기존 세 소스에 구조화된 필드로 없다. 다만 캐리 아이템을 CDragon `composition` 으로 부품 분해하면 자체 계산할 수 있어 수집할 필요가 없다. [추정]
- 팀 코드는 세트 18 에서 없어 우리 덱 코드 교차검증에도 쓸 수 없다. 세트 17까지는 `game_id` 3자리 hex가 있다(예 Aatrox `01d`). [확인]
- Overwolf PC 오버레이는 안드로이드 앱과 무관하다.

---

## 4. 데이터 전달 방식

JSON API, `__NEXT_DATA__`, 정적 JSON 파일은 **없다**. 데이터 전체가 JS 번들 안에 `JSON.parse('...')` 문자열로 들어 있다. [확인] 번들에 `fetch(`·`axios`·`XMLHttpRequest`·`.json` URL 이 한 번도 나오지 않고, 페이지는 요청 없이 번들 데이터로 그린다.

| 자원 | URL | 형식 | 크기(원본 / 전송) | 인증 | CORS | 캐시·기타 |
|---|---|---|---|---|---|---|
| 프리렌더 HTML | `https://tftactics.gg/{경로}/` | react-snap 정적 HTML | 13,981 ~ 164,695B / 3,753 ~ 16,886B | 없음 | `Access-Control-Allow-Origin` 없음 | Netlify, `public,max-age=0,must-revalidate`, ETag. 없는 경로도 홈 index.html을 **200**으로 돌려준다(ETag 동일, soft 404) |
| 앱 번들(데이터 포함) | `https://tftactics.gg/static/js/main.616d9066.chunk.js` | webpack JS, JSON 모듈 10개 | 3,042,959B / 291,246B(gzip), 1.8초 | 없음 | 없음 | 파일명 해시가 배포마다 바뀌어 HTML의 `<script src>` 에서 찾아야 한다. 저장하지 않음 |
| 벤더 번들 | `https://tftactics.gg/static/js/2.96eb4f4b.chunk.js` | JS | 요청 안 함 | — | — | — |
| 이미지 | `https://sunderarmor.com/characters/Base/{이름}.png`, `/items/{이름}.png`, `/icons/{특성}.png` | PNG·SVG | 요청 안 함(루트만 404 확인) | 없음 | — | Cloudflare. 영문 이름으로 만든 경로 |
| robots.txt | `https://tftactics.gg/robots.txt` | `User-agent: *` / 빈 `Disallow:` (전체 허용) | 25B | — | — | — |
| 쿠키 | — | tftactics 응답에 Set-Cookie 없음. GA4(G-EWZ5DY8X87)·Playwire 광고 스크립트는 있음 | — | — | — | — |

### 번들 안 JSON 모듈 [확인]

| 모듈 번호 | 내용 | 건수(세트 18 / 전체) | 주요 키 |
|---|---|---|---|
| 121 | 덱 | 36 / 186 | `name`, `tier`(1=S, 2=A, 3=B), `tier_up`·`tier_down`, `playstyle`, `description`, `synergy`, `characters[name, position, items, level, chosen]`, `carrousel[item, component]`, `mid`, `replacements[out, in]`, `emblem`·`augment` |
| 2 | 챔피언 | 73 / 301 | `name`(+`name_es`…`name_kr` 12개 언어), `id`(숫자 문자열 "1205"), `game_id`(세트 15~17만), `cost`, `items`, `origin`·`type`, `health`·`armor`·`attack`·`speed`·`dps`, `skill` |
| 3 | 아이템 | 98 / 163 | `name`, `id`(숫자), `tier`(1/2/3, 6=미평가), `bonus`, `stats`, `com<플레이어>e`, `into`, `shadow`·`radiant`·`artifact` |
| 33 | 증강 | 257 (세트 구분 없음) | `id`(DA_*), `name`, `tier`(Silver 70 / Gold 118 / Prismatic 69), `bonus`, `tierlist`(1: 19, 2: 102, 3: 124, 4: 12) |
| 9 / 8 | 기원 / 계열 | 19 / 16 | `name`, `effect`, `bonus[count, value]` |
| 122 | 상점 확률 | 11레벨 | `level`, `tier_1`~`tier_5` |
| 120 | 메타 | — | `Set` "18", `Ver` "18.2", `Full`(Contentful 패치노트 5건), `Notes`(더미), `Changelog`(앱 32건) |
| 61 · 17 | 구세트 설명 107건 · DB 탭 이름 4개 | — | — |

모듈 번호는 webpack 이 붙인 값이라 배포 때 바뀔 수 있다. 수집기를 만든다면 번호 대신 키 모양으로 찾아야 한다. [추정]

### ID 체계와 매핑 난이도

tftactics 는 **증강만 DA_*** 를 쓰고, 챔피언·특성·아이템은 **영문 표시명**이 키다. 덱의 유닛도 이름 문자열로 챔피언을 참조한다. [확인]

| 대상 | tftactics 키 | CDragon DA_* 대응 결과 | 난이도 |
|---|---|---|---|
| 증강 | `id` = DA_* | 정확 일치 248/257. 불일치 9개(DA_18_FloraFatalisAugmentPlus, DA_CalculatedLoss 등, CDragon에 없음) | 쉬움 |
| 챔피언 | 영문 이름 | 이름 정규화 61/73 → 접미사(`_AD`/`_AP`/`Small`) 규칙 추가 시 67/73 → 나머지 6명은 수동 별칭(Lux 달빛=DA_18_Lux_Moonbeam, Lux 태양=DA_18_Lux_Sunbeam, Lux 개화=DA_Lux18_Blossom, Lux 검은 가시=DA_Lux18_Blackthorn, Mama Beak=DA_CrimsonRaptor18, Pebbles=DA_18_Sentry) | 보통 |
| 특성 | 영문 이름 | 27/35. 고유 특성 이름이 apiName과 다르다(Attuned=DA_AluneUniqueTrait18, Bounty Seeker=DA_DravenUniqueTrait18, Emerald Aspect=DA_Emerald18 등). Avatar·Monolith·Ravager·Old Growth·Thornmaiden 은 수동 확인 필요 | 보통 |
| 아이템 | 영문 이름(+자체 숫자 id) | 52/98. 불일치 46개(유물·기타 30, 상징 16). 명명 규칙 2개(유물 계열 접두사, "X Emblem"↔"EmblemX")를 더하면 대부분 풀릴 것 [추정] | 보통 |
| 팀 코드 | `game_id` | 세트 18 0/73 | 불가 |

위 매핑 결과는 모두 로컬 CDragon ko_kr 로 계산해 [확인]했다.

**수집할 경우의 위험**
- API가 없어서 3MB 번들 문자열에서 JS 이스케이프를 풀고 JSON을 뽑아야 한다.
- 번들 해시와 모듈 번호가 배포마다 바뀐다.
- 영문 이름 키라 세트마다 별칭표를 손봐야 한다.
- 갱신 시각이 없어 신선도 라벨을 달 수 없다.
- 요청 부담은 번들 1회(전송 291KB)라 작다.

---

## 5. 약관·법적

| 문서 | 존재 | 핵심 |
|---|---|---|
| 이용약관 | **없음** [확인] | 푸터 링크는 Privacy Policy · Contact · Set Skins 뿐이다. 라우터에 약관 경로가 없고, `/terms/` 는 홈 index.html(같은 ETag)을 200으로 돌려준다 |
| 개인정보처리방침 | 있음 [확인] | `/privacy-policy/`, 시행일 2018-12-11. privacypolicies.com 생성기 양식이다. 광고 Playwire LLC, 분석 Google Analytics·Comscore, Network N 쿠키 정책 링크, 개인정보를 스페인으로 이전 |
| 저작권 표시 | "© TFTactics 2026" [확인] | 이용 허락·라이선스 문구 없음 |
| robots.txt | 전체 허용 [확인] | 크롤링을 허용할 뿐 복제·재배포 허락은 아님 |
| Riot 고지 | 있음 [확인] | Riot 이 보증하지 않는다는 표준 문구 |

- **스크래핑·복제·재배포·상업적 이용 조항: 없다.** 약관 문서 자체가 없기 때문이다. [확인]
- 개인정보처리방침은 존재하지 않는 약관을 참조한다. 용어가 "…have the same meanings as in our Terms and Conditions, accessible from https://TFTACTICS.GG"라고 되어 있지만 그런 문서는 없다. [확인]
- 데이터 처리 장소는 "we transfer the data, including Personal Data, to Spain and process it there". [확인]

**운영 주체**

| 항목 | 내용 | 근거 |
|---|---|---|
| 사이트 표기 | "TFTACTICS.GG". 법인명·주소 없음, 연락처는 Gmail(hello.tftactics@gmail.com) | [확인] |
| 소재지 | 개인정보를 스페인에서 처리한다는 문구로 보아 스페인 | [추정] |
| 개발자 | Overwolf 사례 기사가 1인 개발자 "JJ"가 웹사이트로 시작해 2019년 Overwolf 앱으로 확장했다고 소개 | [추정: 검색 결과 요약만 봄, 원문 미요청] |
| 네트워크 | 푸터 "Our Network"의 critcap.gg(Guildrun · Batomon)가 같은 이미지 호스트(sunderarmor.com), 같은 Typekit 키트(bya0rai), Netlify 를 쓴다 → 같은 운영자 | 공유 [확인], 동일 운영자 [추정] |

**판단** — lolchess 처럼 명시적인 금지 조항은 없다. 하지만 약관이 없다는 것은 이용 허락도 없다는 뜻이다. 사이트가 저작권을 표시하고 있고, 덱 선정·등급·배치·추천 아이템은 편집자가 고르고 배열한 콘텐츠다. 이것을 공개 저장소와 앱으로 재배포하면 권리 침해 소지가 있다고 본다. [추정, 법률 자문 아님] 게임 데이터(설명·확률)는 CDragon 에서 받으면 되므로 tftactics 를 거칠 이유가 없다. 따라서 **자동 수집 대상에서 제외하고, 화면 개념만 참고한다.** 문장·이미지는 옮기지 않는다.

샘플도 이 판단에 맞췄다. 번들에서 뽑은 데이터는 64KB 이하여도 편집 콘텐츠 전체 복제를 피하려고 구조 골격만 저장했다(2KB 이하 상점 확률·탭 이름만 원본).

---

## 6. 한국어 지원

**없다.** [확인]

- UI 는 영어뿐이다. 앱 상태 기본값이 `lang:"en"` 이고 언어 선택 UI 코드가 없다. 번들에 한글이 0자다.
- 데이터에 `name_kr` · `origin_kr` · `type_kr` 필드는 있지만 세트 18 챔피언 73/73이 영문과 같은 값이다. `name_ch` · `name_cn` 도 영문이다.
- 번들 속 Overwolf 앱 변경 이력에는 스페인어·이탈리아어 추가 기록만 있다.

한글화는 어차피 CDragon ko_kr 로 해야 하므로 이 점은 판단에 영향이 없다.

---

## 7. 판단

### 결론: UI 아이디어만 (데이터원 채택 없음)

1. **통계가 없다.** 앱의 두 기준(lol.qq 중국 통계, metatft 글로벌·KR 통계)을 보완할 수치가 하나도 없다. 덱·증강·챔피언·아이템 모두 편집 의견이고 날짜·표본이 없다.
2. **덱 목록이 기존 소스와 크게 겹친다.** 36덱 중 31덱이 metatft 또는 lol.qq 와 자카드 0.5 이상이다. 겹치지 않는 5덱은 대부분 B등급 변형이다.
3. **세트 18 도감이 부실하다.** 스탯 0/73, 수치 누락, 챔피언 등급 전원 1, 아이템 등급 세트 공용, 팀 코드 0/73, 페이지 문구 "set 17". 편집 인력이 덱 목록만 관리하는 구조로 보인다. [추정]
4. **구현이 불리하다.** API 없이 3MB 번들을 파싱해야 하고, 해시·모듈 번호가 바뀌며, 영문 이름 별칭표가 필요하다(챔피언 6 · 특성 8 · 아이템 46 불일치). 갱신 시각이 없어 신선도 검증도 못 한다.
5. **법적 근거가 없다.** 금지 조항은 없지만 약관·라이선스도 없어, 편집 콘텐츠 재배포를 정당화할 근거가 없다. [추정]
6. **한국어가 없다.** 이점이 되지 않는다.

### 카테고리별 점수 (0~10)

| 카테고리 | 데이터 | UX | 구현 | 근거 |
|---|---:|---:|---:|---|
| 덱 | 3 | 7 | 4 | 데이터: 편집 36덱과 배치·캐리템·대체·캐러셀이 구조화돼 있지만 통계·날짜가 없고 31/36이 기존 소스와 겹침. UX: 카드 한 줄 위계, 운영 칩, 펼침 5섹션, 필터 3종이 오버레이 참고에 적합(호버 툴팁 위주, 세트 18 팀 코드 없음, 광고 슬롯 코드 존재 [확인: 코드], 폰 화면 [추정]). 구현: 번들 파싱·이름 매핑·약관 부재 |
| 챔피언 | 1 | 4 | 4 | 데이터: 스탯 0/73, 수치 누락, 등급 전원 1. UX: 상세가 코스트·아이템·스킬·시너지로 단순하고 스탯표·티어리스트는 메뉴에서 숨김. 구현: 챔피언 이름 매핑 67/73 규칙 + 수동 6 |
| 아이템 | 2 | 6 | 5 | 데이터: S/A/B 36개, 세트 공용 단일 등급, 수치 없음(조합식·효과는 CDragon 과 중복). UX: 부품→완성템 빌더와 등급 그룹(README 가 lol.qq·lolchess 에서 이미 가져오기로 한 흐름과 같음). 구현: 이름 매핑 52/98 |
| 증강 | 2 | 3 | 6 | 데이터: 257개 설명·희귀도뿐, 편집 등급은 비공개 필드, 통계 없음. UX: 단일 표 + 검색, 제목 "Set 17". 구현: DA_* 그대로 248/257 일치(번들 파싱은 필요) |
| 빌드업 | 3 | 6 | 4 | 데이터: 초반 조합 한 단계(레벨 표기 없음)와 운영 템플릿 6종, 3성 목표·캐러셀·대체 유닛만 구조화, 통계 없음. UX: "Slow Roll (7)"·"Fast 8" 칩이 운영 방식을 한눈에 전달하고 초반→최종 두 단계가 간결. 구현: 덱과 같음 |

### 가져올 UI 아이디어

값은 전부 기존 소스(lol.qq · metatft · CDragon)로 채우고, tftactics 의 문장·이미지·데이터는 쓰지 않는다.

1. **레벨 숫자가 든 운영 칩** — "Slow Roll (7)"처럼 칩 하나에 운영 방식과 레벨을 함께 넣는다. 우리 표기로는 `리롤 7렙` / `빠른 8렙` / `빠른 9렙` / `표준`. 숫자는 metatft `rerolls`(판당 리롤이 가장 많은 레벨)와 `levelling` 으로 계산한다. 칩을 누르면 운영 규칙 한 줄을 우리 문장으로 보여 준다. README 덱 판정의 "운영 방식 칩(빠른 8레벨)"을 확장하는 안이다.
2. **덱 상세·오버레이 카드의 펼침 순서** — 초반 조합 → 시너지 → 캐러셀 부품 → 대체 유닛 → 배치. lol.qq 편집 덱 초반·중반 보드와 `hero_replace`, 칸별 사용률 히트맵으로 채운다.
3. **캐러셀 우선 부품 칩 3개** — 캐리 3명의 추천 아이템을 CDragon `composition` 으로 부품 분해해 빈도순 상위 3개를 수집기가 계산한다.
4. **등급 변동 화살표** — 전날 스냅샷 대비 등급이 오르거나 내린 덱에 ▲▼를 붙인다(`tier_up`·`tier_down` 개념).
5. **상징 필요 배지** — 최종 보드 캐리 아이템에 상징이 있는 덱에 "상징" 배지를 달고, 누르면 해당 상징 설명(CDragon ko_kr)을 보여 준다.

**반면교사 → verify.py 검사 항목**
- tftactics 는 세트가 바뀐 뒤 필드를 방치했다(스탯 공란, 팀 코드 없음, 챔피언 등급 전원 동일, 페이지 문구 "set 17").
- 현재 세트 필수 필드 채움률(스탯·팀 코드 `team_planner_code`·한글명) 하한을 둔다.
- 등급 분산 0(전원 같은 등급)을 실패로 처리한다.
- 화면·데이터의 세트 표기가 `CurrentSet` 과 같은지 확인한다.

---

## 부록 A. 요청 기록

| # | 시각(UTC) | URL | 상태 | 크기(원본 / 전송, B) | 응답(ms) |
|---:|---|---|---:|---:|---:|
| 1 | 13:58:15 | `https://tftactics.gg/robots.txt` | 200 | 25 / 25 | 103 |
| 2 | 13:58:16 | `https://tftactics.gg/` | 200 | 13,981 / 4,109 | 252 |
| 3 | 13:58:51 | `https://tftactics.gg/static/js/main.616d9066.chunk.js` | 200 | 3,042,959 / 291,246 | 1,842 |
| 4 | 13:58:54 | `https://tftactics.gg/tierlist/team-comps/` | 200 | 164,695 / 10,829 | 59 |
| 5 | 13:58:54 | `https://tftactics.gg/privacy-policy/` | 200 | 19,715 / 6,466 | 522 |
| 6 | 14:00:06 | `https://tftactics.gg/set-update/` | 200 | 146,354 / 16,886 | 302 |
| 7 | 14:00:07 | `https://tftactics.gg/db/augments/` | 200 | 131,569 / 13,835 | 936 |
| 8 | 14:00:09 | `https://tftactics.gg/tierlist/items/` | 200 | 18,660 / 3,753 | 251 |
| 9 | 14:00:10 | `https://tftactics.gg/champions/` | 200 | 35,317 / 5,236 | 255 |
| 10 | 14:01:09 | `https://tftactics.gg/terms/` | 200 | 13,981 / 4,106 | 400 |
| 11 | 14:03:08 | `https://web.archive.org/cdx/search/cdx?url=tftactics.gg/static/js/main.&matchType=prefix&from=2…` | 200 | 6,601 / 2,251 | 6,835 |
| 12 | 14:03:16 | `https://www.critcap.gg/` | 200 | 16,765 / 4,355 | 827 |
| 13 | 14:03:18 | `https://sunderarmor.com/` | 404 | 1,506 / 1,506 | 581 |
| 14 | 14:06:19 | `https://tftactics.gg/champions/ahri/` | 200 | 16,545 / 3,952 | 847 |
| 15 | 14:06:21 | `https://tftactics.gg/tierlist/champions/` | 200 | 13,981 / 4,110 | 247 |
| 16 | 14:06:22 | `https://tftactics.gg/db/champion-stats/` | 200 | 13,981 / 4,110 | 259 |
| 17 | 14:09:58 | `https://web.archive.org/cdx/search/cdx?url=tftactics.gg/tierlist/team-comps/&from=202605&output…` | 200 | 323 / 251 | 8,162 |

호스트별: tftactics.gg 13 · web.archive.org 2 · www.critcap.gg 1 · sunderarmor.com 1 = 17회 (한도 60).

## 부록 B. 샘플 파일

위치는 `samples/tftactics/`. 규칙은 다음과 같다.
- HTTP 응답은 64KB 이하 원본, 넘는 HTML 은 앞 32KB(`.head.txt`).
- JS 번들 본문은 저장하지 않고 헤더만 남겼다.
- 번들에서 뽑은 JSON 모듈은 5장 판단에 따라 구조 골격(`.shape.json`: 배열 앞 2개 + "... 외 N개", 문자열 200자 + "... 외 N자")으로 저장했다.
- 전체 목록과 원본 URL은 `_index.json`, 요청 로그는 `_requests.jsonl` 에 있다.

| 파일 | 출처 | 저장 형태 | 원본 크기(B) | 비고 |
|---|---|---|---:|---|
| `robots.txt.headers.txt`, `robots.txt` | `https://tftactics.gg/robots.txt` | original | 25 |  |
| `home.html.headers.txt`, `home.html` | `https://tftactics.gg/` | original | 13,981 | CRA + react-snap 프리렌더 HTML |
| `tierlist_team-comps.html.headers.txt`, `tierlist_team-comps.html.head.txt` | `https://tftactics.gg/tierlist/team-comps/` | first 32KB | 164,695 | 덱 티어리스트 프리렌더(36덱) |
| `privacy-policy.html.headers.txt`, `privacy-policy.html` | `https://tftactics.gg/privacy-policy/` | original | 19,715 | 개인정보처리방침(2018-12-11) |
| `set-update.html.headers.txt`, `set-update.html.head.txt` | `https://tftactics.gg/set-update/` | first 32KB | 146,354 |  |
| `db_augments.html.headers.txt`, `db_augments.html.head.txt` | `https://tftactics.gg/db/augments/` | first 32KB | 131,569 | 제목·설명은 Set 17 문구, 내용은 Set 18 증강 |
| `tierlist_items.html.headers.txt`, `tierlist_items.html` | `https://tftactics.gg/tierlist/items/` | original | 18,660 |  |
| `champions.html.headers.txt`, `champions.html` | `https://tftactics.gg/champions/` | original | 35,317 |  |
| `champions_ahri.html.headers.txt`, `champions_ahri.html` | `https://tftactics.gg/champions/ahri/` | original | 16,545 |  |
| `terms_probe.headers.txt` | `https://tftactics.gg/terms/` | headers-only | 13,981 | 라우트 없음: 홈과 같은 index.html(etag e326a208…) 폴백 |
| `tierlist_champions.headers.txt` | `https://tftactics.gg/tierlist/champions/` | headers-only | 13,981 | 라우터에는 있으나 프리렌더 없음: index.html 폴백 |
| `db_champion-stats.headers.txt` | `https://tftactics.gg/db/champion-stats/` | headers-only | 13,981 | 라우터에는 있으나 프리렌더 없음: index.html 폴백 |
| `main.616d9066.chunk.js.headers.txt` | `https://tftactics.gg/static/js/main.616d9066.chunk.js` | headers-only | 3,042,959 | JS 번들 본문은 저장하지 않음(데이터는 아래 module 샘플로 대체) |
| `wayback_cdx_main-chunk_2026.json.headers.txt`, `wayback_cdx_main-chunk_2026.json` | `https://web.archive.org/cdx/search/cdx?url=tftactics.gg/static/js/main.&matchType=prefix&f…` | original | 6,601 | web.archive.org CDX |
| `wayback_cdx_team-comps_2026.json.headers.txt`, `wayback_cdx_team-comps_2026.json` | `https://web.archive.org/cdx/search/cdx?url=tftactics.gg/tierlist/team-comps/&from=202605&o…` | original | 323 | web.archive.org CDX |
| `critcap_home.html.headers.txt`, `critcap_home.html` | `https://www.critcap.gg/` | original | 16,765 | 같은 네트워크(Our Network) 사이트 |
| `sunderarmor_root_404.html.headers.txt`, `sunderarmor_root_404.html` | `https://sunderarmor.com/` | original | 1,506 | 이미지 호스트 루트(Cloudflare 404) |
| `bundle_comps_all.shape.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 121)` | shape (편집 콘텐츠 전체 복제를 피하려고 64KB 이하여도 골격만) | 319,084 | 덱 186개(세트 15~18) |
| `bundle_comps_set18.shape.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 121)` | shape (편집 콘텐츠 전체 복제를 피하려고 64KB 이하여도 골격만) | 61,620 | 덱 중 세트 18만(36개) |
| `bundle_champions_set18.shape.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 2)` | shape (편집 콘텐츠 전체 복제를 피하려고 64KB 이하여도 골격만) | 364,428 | 챔피언 중 세트 18만(73명) |
| `bundle_items_set18.shape.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 3)` | shape (편집 콘텐츠 전체 복제를 피하려고 64KB 이하여도 골격만) | 177,406 | 아이템 중 세트 18 포함(98개) |
| `bundle_augments.shape.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 33)` | shape (편집 콘텐츠 전체 복제를 피하려고 64KB 이하여도 골격만) | 48,777 | 증강 257개(id=DA_*, tierlist 1~4) |
| `bundle_classes_set18.shape.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 8)` | shape (편집 콘텐츠 전체 복제를 피하려고 64KB 이하여도 골격만) | 42,094 | 계열(class) 중 세트 18(16개) |
| `bundle_origins_set18.shape.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 9)` | shape (편집 콘텐츠 전체 복제를 피하려고 64KB 이하여도 골격만) | 60,202 | 기원(origin) 중 세트 18(19개) |
| `bundle_rolling_odds.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 122)` | original (bundle JSON.parse module, <=2KB) | 1,071 | 레벨별 상점 확률(세트 구분 없음) |
| `bundle_patch_changelog.shape.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 120)` | shape (편집 콘텐츠 전체 복제를 피하려고 64KB 이하여도 골격만) | 67,686 | Set/Ver 표기 + 패치노트(Contentful, 최신 2024-02) + 앱 변경 이력 |
| `bundle_mod61_descriptions.shape.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 61)` | shape (편집 콘텐츠 전체 복제를 피하려고 64KB 이하여도 골격만) | 13,977 | 구세트 파워업류 설명 107개 |
| `bundle_mod17_db_tabs.json` | `https://tftactics.gg/static/js/main.616d9066.chunk.js (webpack module 17)` | original (bundle JSON.parse module, <=2KB) | 45 | DB 탭 이름 |

## 부록 C. 재현 메모

- 현재 번들 경로는 홈 HTML 의 `<script src="/static/js/main.*.chunk.js">` 에서 읽는다.
- 모듈은 정규식 `(\d+):function\(e\)\{e\.exports=JSON\.parse\('` 로 시작점을 찾고, 이스케이프되지 않은 `')` 까지 자른다. JS 문자열 이스케이프(`\'`, `\\`, `\uXXXX`)를 풀어 `json.loads(strict=False)`로 읽는다(`extract.py`).
- 세트 필터는 각 객체의 `set` 배열(예 `[18]`, 아이템은 `[17, 15, 18, 16]`)이다.
- 운영 주체 참고 링크:
  - [Overwolf 앱 페이지](https://www.overwolf.com/app/tftactics.gg-tftactics)
  - [Overwolf 사례 기사](https://medium.com/overwolf/success-story-tftactics-path-to-glory-c57127428381)

  두 링크 모두 검색 결과로만 확인했고 원문은 요청하지 않았다.
