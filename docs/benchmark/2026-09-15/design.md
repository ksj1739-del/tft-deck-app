# TFT 덱 앱 통합 설계 — 벤치마킹·심사 종합

작성일 2026-09-15. 대상 저장소 `C:/claude_project/tft_deck_app` (github.com/ksj1739-del/tft-deck-app), 시즌 18(lol.qq `s18`, metatft `TFTSet18`), 중국 패치 16.18 / 글로벌 패치 18.2.

이 문서는 세 사이트(lol.qq.com/tft, lolchess.gg, metatft.com) 벤치마킹 보고서, 심사위원 3명 × 카테고리 5개 채점, 최종 판정, 인게임 연동 조사, 버그 진단을 하나의 구현 설계로 묶은 것이다. 모든 엔드포인트·수치는 2026-09-15에 직접 호출해 확인한 것만 "확인"으로 적고, 나머지는 "추정"으로 구분했다.

## 0. 한 줄 결론

| 항목 | 결정 |
|---|---|
| 덱 목록의 기준 | **lol.qq** (편집 덱 + 胜率阵容 2단 목록 + 数据检索器 수치/등급). metatft는 대조·한국/글로벌 비교 열. |
| 챔피언·특성·아이템 수치의 기준 | **metatft** (tft-stat-api, 글로벌/KR 플래+, KR 마스터+). lol.qq 数据检索器는 "중국 서버" 열. |
| 증강 | **lol.qq** 덱별 실측(唯一) + metatft 에디터 티어·태그·한글 사전. 증강 자체의 평균 등수 순위는 만들 수 없다(세 곳 모두 없음). |
| 전적·티어 | **metatft** 공개 프로필(기기 직접 호출, 기존 구조 유지). |
| lolchess.gg | 데이터원에서 제외(약관의 복제·제3자 제공 금지 조항 확인). UI 패턴·한국어 용어·검증 기준만 빌린다. |
| 소스 통합 키 | `DA_*` apiName 정확 일치 + 정규화 3가지(§4.1). |
| 오버레이 | 덱 참고 + 티어 참고만. 상대 정보·로비 스카우팅 없음(Riot 정책). |
| 인게임 연동 | UsageStats로 TFT 앱 전면 감지(구현 가능) → 게임 후 로비 카드(구현 가능). 진행 중 로비는 원천 데이터 0건이라 플래그 뒤에 인터페이스만 둔다. |
| 작업 패키지 | 6개, 파일 소유 겹침 없음(§12). |

---

## 1. 벤치마킹 요약표

### 1.1 사이트 성격

| | lol.qq.com/tft | lolchess.gg | metatft.com |
|---|---|---|---|
| 운영 | 텐센트 공식 주제 사이트(중국 서버) | dak.gg / PlayXP(한국) | MetaTFT(글로벌, Overwolf 앱) |
| 구조 | Vue2 해시 SPA, 정적 CDN JSON + 통계 프록시(POST mlol.qt.qq.com) + 레거시 JSONP | Next.js SSR, API `tft.dakgg.io/api/v1` (CloudFront) | Vite React SPA, `api-hc.metatft.com`(통계, CDN 캐시 없음) + `api.metatft.com`(프로필) + `data.metatft.com`(정적) |
| 인증·차단 | 없음. 프록시는 Referer/Origin 없이 result 0. 레거시 JSONP만 Referer 필수. 436회 연속 호출 차단 없음 | 없음. 비브라우저 UA도 200. 조용한 빈 응답(200) 다수 | 없음. UA 없이도 200. 약 80회 호출 429 없음 |
| 약관 | 비공개 API(회색지대). 스크래핑 금지 조항 미확인 | **사전 승낙 없는 복제·제3자 제공 금지, 타 이용자 정보 저장 금지, 상업적 이용 금지** (3명이 라이브 페이지에서 확인) | 2023-06-11 약관에 스크래핑·재배포 금지 조항 없음(5.1 데이터 소유권만) |
| 표본 | 중국 랭크 1100. 실버+ 하루 약 150만 플레이어-게임, 플래+ 3일 353만 | 글로벌 마스터+ 2일(덱), 티어 6버킷(챔피언·아이템) 18.2 패치 41만 경기 | 글로벌 15서버, 랭크 10단계 조합, 1~7일. 플래+ 3일 680만 보드, KR 플래+ 3일 111만 보드 |
| 갱신 | 목록 당일, 상세 T-1, 数据检索器 당일(수시간 지연) | 30분~1시간 | 실시간 누적(5분 표기) |
| 한국어 | 없음(중국어) | 원어(API `hl=ko`) | 사전(`TFTSet18_latest_ko_kr.json`)과 UI 번역(번역투 있음) |
| 모바일 | `min-width:1240px!important`, 호버 전용 | 서버 모바일 렌더, 12px 글자, 표 가로 스크롤 | 반응형, 광고 고정, 9~11px 글자 |

### 1.2 카테고리 × 사이트 핵심 사실

| 카테고리 | lol.qq | lolchess | metatft |
|---|---|---|---|
| 덱 | 편집 덱 26(레벨별 보드, 운영 텍스트, 증강). 胜率阵容 45그룹/425조합, 티어 5구간, 칸별 배치 승률, 핵심 유닛 성급 비율, **덱별 증강 성적(유일)**. 数据检索器 lineup_rank 100행(카운트, 티어 세분, 최대 3일) | 메타 덱 22개(마스터+ 글로벌 한 조합만 동작), 캐리 순위·8칸 분포, 상세 API null | 클러스터 54개. `comps_stats`만 rank/days/server 필터 적용(확인). `comps_data`·`comp_details`는 고정 모집단. 증강 없음. 클러스터 id 재생성 시 변경 |
| 챔피언·특성 | 도감(16.18 chess.js는 CDragon과 수치 완전 일치), 数据检索器는 티어 필터 + `unit_s` 분모일 때 모순 0(확인). 国服大数据 랭킹은 가중 평균 3.53로 불가능 → 사용 금지 | 원시 카운트 정확, numUnits가 인원수, 글로벌만, 표본 작음 | 표본 최대, KR 단독 조회, places[8], lolchess와 상관 0.995. 특성 `_N`은 단계 순번 |
| 아이템 | 키 142개 metatft와 동일. `챔피언#아이템` 행렬 7,395행(build_s 인기도만 일관). 装备排行 착용자 id 깨짐 | 동반 아이템 통계(유일), 조합표, 한국어. 부품 누락(126) | 표본 최대, `item_detail` 착용 유닛 68·유닛 평균 대비, `unit_items_processed` 역검색, 완성 스테이지별 승률 |
| 증강 | **시즌 18 실측 통계 유일**(덱별 상위 5, 단계별 평균, 표본 있음). 전체 랭킹은 화이트리스트로 폐쇄 | 편집 티어 S~D(작성자 없음), 확률표·배제 목록(번들), 통계 없음(Riot 정책 고지) | 에디터 1인 티어(S24/A84/B129/C21, 당일 갱신), 태그·희귀도 사전, 통계 원천 없음 |
| 전적 | 중국 계정 전용(KR 조회 불가) | 백분위 7지표, b패치 구분, GET으로 갱신 안 됨(sync RPC 필요) | 이미 앱이 사용. 8인 티어·LP·MMR·로비 평균, 갱신 사례 확인, 약관 문제 없음 |

---

## 2. 카테고리별 점수와 선택 근거

### 2.1 합계(심사위원 3명, 만점 30)

| 카테고리 | lol.qq | lolchess | metatft | 합계 1위 | **최종 판정** | 합계와 다른가 |
|---|---|---|---|---|---|---|
| 덱 리스트·상세 | 18 | 16 | 21 | metatft | **lol.qq** | 예 |
| 챔피언·특성 | 12 | 19.5 | 24 | metatft | **metatft** | 아니오 |
| 아이템 | 12.5 | 19.5 | 24.5 | metatft | **metatft** | 아니오 |
| 증강 | 15.5 | 12.5 | 12.5 | lol.qq | **lol.qq** | 아니오 |
| 전적·티어·인게임 | 4.5 | 20 | 21.5 | metatft | **metatft** | 아니오 |

관점별 1위: 덱(데이터 metatft 8 / 사용성 lolchess 7.5 / 구현 lol.qq 8.5), 챔피언(전부 metatft), 아이템(전부 metatft), 증강(데이터 lol.qq 6 / 사용성 lolchess 7 / 구현 lol.qq 7), 전적(데이터 metatft 7.5 / 사용성 lolchess 7.5 / 구현 metatft 8).

### 2.2 선택 근거 요약

**덱 = lol.qq (합계를 뒤집음).**
- 3점 차는 전부 모바일 사용성 축(metatft 6 : lol.qq 2.5)에서 났고, 그 축의 점수는 원본 사이트 화면에 대한 것이다. 우리는 화면을 직접 만든다. 데이터+구현 두 축은 lol.qq 15.5 : metatft 15로 대등하다.
- 2026-09-14 확정 결정("lol.qq 목록 기준, metatft는 대조 단방향")과 앱 정체성(중국 통계를 한국어로) 유지.
- lol.qq에만 있는 것: 덱별 증강 성적, 칸별 사용률·승률, 핵심 유닛 성급 비율·아이템 수, 티어 5구간, 당일 신선도, 편집 덱의 레벨별 보드·운영 텍스트.
- metatft의 8점은 `comps_stats` 한 엔드포인트의 점수다. `comps_data`·`comp_details`는 rank/days/server를 무시하는 고정 모집단(KR 마스터+를 붙여도 423009 = 556,572·4.2225 그대로)이고 증강이 없다.
- lol.qq의 약점(胜率阵容 0.1 반올림, 평균 4.0 이하만 노출, 소표본 S등급, 기간 정의 없음)은 数据检索器 카운트로 수집기가 등급을 다시 매기고 표본 라벨을 붙여 흡수한다.

**챔피언·특성 = metatft.** 표본(플래+ 7일 1,190만 보드, lolchess의 4.6배), 랭크 임의 조합, KR 서버 단독 조회(`server=KR`이면 games.count 5,785,016→967,672로 실제 적용 확인), places[8] 원시 분포, lolchess와 상관 0.995. lol.qq 数据检索器는 "티어 필터 + version='' + unit_s 분모" 조건에서만 모순 0(tier ''는 75/75 모순, total 분모는 71/72 모순)이라 보조 열로만 쓴다.

**아이템 = metatft.** 아이템 키 142개가 lol.qq와 완전히 같고, `items_matches`(server=KR 142행 확인)·`item_detail`·`unit_items_processed`가 인증 없이 온다. lol.qq는 같은 날 같은 아이템 평균이 엔드포인트마다 4.1/1.69/5.19로 갈려 인기도(build_s)만 쓴다.

**증강 = lol.qq.** 시즌 18 실측은 lol.qq 胜率阵容 상세 `augment_data`뿐이다(재확인: DA_Ascension 634회 3.16등, DA_LateGameScaling 2,021회 3.19등, 1_/2_/3_avg_rank 포함). metatft(`unit_augments` 빈 문자열 1행, `augment_unit_detail` [], `augments_full2` 500)와 lolchess(`meta-deck-augments` 빈 응답, 화면에 Riot 정책 고지)는 통계가 없다.

**전적 = metatft.** 정확성은 lolchess와 동일(112/112 경기, LP 로그 108/108). 갱신 사례 확인, 경기 JSON의 8인 티어·LP·MMR, 약관 문제 없음, 이미 앱에 통합되어 동작 중. lolchess는 약관 + sync RPC 차단 위험.

### 2.3 lolchess에서 빌리는 것(데이터 아님)

- 한국어 용어집: 평균 등수 / TOP4 / 승률 / 픽률 / 게임 수, `11 나무정령`(활성 인원수+이름), 3신기 / 아이템 / 유물 / 찬템 / 상징, 전체 / 일반 / 상징 / 유물 / 찬란.
- 덱 카드 고정 4수치 줄, 상세 맨 위 큰 "팀 코드 복사" 버튼, 캐리 3명만 아이템 크게, 한글 덱 이름 규칙 `[태그] 핵심 특성+캐리`, "최종 업데이트 N시간 전".
- 반면교사: 기본 정렬 최소 표본 문턱, 폰에서 이름 열 고정, 첫 화면에 데이터 행이 보이게, 게임 수 항상 표시.
- 배포 전 일관성 검사: `sum(places)==count`, 분포 재계산 평균 = 표시 평균, 목록 가중 평균 4.5±0.05, 전부 0인 필드 제거, HTTP 200 빈 응답을 실패로.

---

## 3. 소스 사용 규칙(공통)

### 3.1 metatft
- 필터(rank/days/server/patch)가 실제로 적용되는 엔드포인트는 `tft-stat-api/*`(units, traits, items_matches, item_detail, unit_detail_*)와 `tft-comps-api/comps_stats`뿐이다(확인). `comps_data`·`comp_details`·`unit_items_processed`는 필터를 무시하는 고정 모집단이라 티어·기간 라벨을 붙이지 않는다.
- 항상 `permit_filter_adjustment=false`. 과거 패치는 `patch=18.1&b_patch=d`처럼 b_patch까지(빈값이면 18.2로 폴백).
- `comps_stats` places는 9원소(앞 8개 = 등수 분포, 9번째 = count). 첫 행 `{cluster:"", places:[총 보드 수]}`.
- 클러스터 id는 저장하지 않고 `latest_cluster_info.units_string`으로 매일 재매칭(없는 id는 HTTP 500).
- 잘못된 키(`TFT18_Aphelios`, `TFT_Item_*`)는 200 빈 응답 → 빈 값 검증 필수.
- api-hc는 CDN 캐시가 없어 요청마다 오리진이 집계한다. 하루 호출 수백 회 이내(설계상 약 250회).
- 랭크 집합: 플래+ = `CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM`, 마스터+ = `CHALLENGER,GRANDMASTER,MASTER`.

### 3.2 lol.qq
- 통계 프록시: `POST https://mlol.qt.qq.com/go/exploit/proxy`, `Content-Type: application/json`, body `{"req_alias":…, "version_id":…, "req_params":{…}}`(목록 alias는 `"is_return_source":0` 추가). 쿠키·서명·Referer 불필요. 응답 `result==0`이고 `data`가 비어 있지 않아야 성공.
- 数据检索器(`tft_match_overview`, `tft_lineup_rank`, `tft_hero_rank`, `tft_trait_rank`, `tft_equip_rank` …): 반드시 `tier`를 준다(`"4+"` 플래+, `"7+"` 마스터+; `""`는 전 행 모순). 분모는 `unit_s/trait_s/build_s/comp_s`(해당 항목이 있는 보드 수). `version:""`로 전 빌드 합산(hero/trait/lineup에서 확인). `version:"16.18"` 같은 짧은 문자열은 200 빈 응답. 기간 최대 3일(5·7·14일 빈 배열), 날짜는 CST. 수집기(05:00 KST = 04:00 CST)에서는 `stime = etime-2`, `etime = 전날`로 완결 데이터를 받는다.
- 胜率阵容(`tft_lineup_group_list`, version_id `dgroup_v3`, time_type `d_grouping_v3`): 목록은 당일, 상세(`tft_lineup_all_detail` 등, version_id `v1`)는 T-1. 각각의 `dtstatdate`를 라벨로 싣는다. 상세의 `minor_traits_id`는 목록의 `sub_trait_list`로 구성하고, 없으면 `"-1,-1"`(이때 augment_data가 빈 배열일 수 있음 — 조용한 실패).
- 国服大数据 랭킹(`tft_hero_ranking`, `tft_equip_ranking`)의 절대 수치와 `hero_list`, lol.qq가 주는 S/A/B/C 등급과 `avg_rank_delta`는 쓰지 않는다.
- 숫자는 전부 문자열. 편집 덱 `detail`은 제어문자 섞인 JSON 속 JSON(`strict=False`).
- 정적 파일 경로: `versionconfig.json`은 아직 `16.17-2026.S18`을 가리키지만 `16.18-2026.S18` 경로 파일이 존재한다. CDragon 패치 버전(현재 16.18)으로 먼저 조립하고 404면 versionconfig 경로로 폴백. 응답의 `version` 필드를 기록한다.
- 胜率阵容의 챔피언·특성은 숫자 id(`100310`, `10379`) → `chess.js`(chessId→hero_EN_name), `race.js`/`job.js`(raceId/jobId→characterid)로 DA_* 변환(65/65, 36/36 매핑 확인). 아이템·증강(`rune_id`)은 이미 DA_*.

### 3.3 CommunityDragon
- `ko_kr.json`/`en_us.json` apiName 정확 일치가 한글 이름의 1순위. 없는 증강 15개는 metatft ko 사전으로 보충하고, 그래도 없으면 수동 오버라이드 사전(`collector/overrides_ko.json`).
- 조합표는 `items[].composition`(DA_* 완성템 55개, 부품 10종 확인).

### 3.4 개인정보
- lol.qq `fightdetail`의 openid·login_ip·originalPuuid, 작가 QQ 번호, metatft 경기 JSON의 타인 puuid·riot_id·MMR은 저장·배포하지 않는다. 프로필은 기기 안에서만 처리하고 수집기에는 집계(컷·인원)만 넣는다.

---

## 4. 통합 덱 리스트 알고리즘

### 4.1 ID 정규화(세 소스 공통 키)

세 소스의 공통 키는 `DA_*` apiName **정확 일치**다(접두사 제거 불필요; 아이템 142=142, 챔피언·특성도 CDragon과 거의 전부 일치). 다음 세 가지만 정규화한다.

1. metatft 특성 `DA_X_N`의 N은 인원수가 아니라 **단계 순번** → CDragon `traits[].effects[N-1].minUnits`로 `(DA_X, 인원수)`로 바꾼다. 그러면 lol.qq `DA_X,n`·lolchess `numUnits`와 같은 키가 된다(88행 비교 상관 0.998로 검증).
2. metatft 통계의 `TFT18_*` 저표본 행(Akali, Gromp, KogMaw, MasterYi, NidaleeCougar, SprykinSummonMelee) 제거.
3. lol.qq 胜率阵容·특성 추세의 숫자 chessId/traitId → `chess.js`/`race.js`/`job.js`로 변환.

덱 **매칭용** 유닛 집합을 만들 때 추가로: 럭스 변형(`DA_18_Lux_*`, `DA_Lux18_*`)은 `DA_Lux18_Base`로 접고, 소환물·pet(`hero_id`가 빈 lol.qq 항목, CDragon set 18 champions에 없는 id)은 뺀다.

### 4.2 입력 후보 4종

| 기호 | 원천 | 단위 | 키 | 수치 |
|---|---|---|---|---|
| E | lol.qq 편집 덱 `lineup_detail_total.json` (26) | 덱 | 최종 보드 유닛 DA 집합 | 없음(quality SS~C) |
| W | lol.qq 胜率阵容 `tft_lineup_group_list` × tier_part 255/0/1/2/3 | 그룹(주특성+메인C) → 조합(유닛 8) | 그룹: 주특성(id,개수)+메인C / 조합: 유닛 DA 집합 | use_num, avg_rank(0.1), top_1/4_rate, *_diff, rune_id_group, 캐리 3명 아이템 |
| R | lol.qq 数据检索器 `tft_lineup_rank` tier 4+ / 7+, 3일, version '' | 조합 | `lineup.chess_ids` DA 집합 | comp_s, top1_cnt, top4_cnt, avg_rank(0.001), final_level |
| M | metatft `latest_cluster_info` + `comps_stats`(글로벌 플래+, KR 플래+, KR 마스터+) + `comps_data` | 클러스터 | units_string DA 집합 | places[8]·count(스코프별), levelling, difficulty |

### 4.3 병합 절차

```
1) W를 다섯 구간 모두 받는다. 그룹 키 = 정렬된 [(DA_trait, count)] + 메인C DA.
   다섯 구간의 그룹 키 합집합이 마스터 그룹 목록이다. 구간별 수치는 stats[bucket]에 붙는다.
   조합(변형) 키 = 정규화 유닛 집합(4.1). 같은 키는 하나로 합친다.
2) R(4+, 7+)의 행을 변형에 붙인다: 자카드(R.chess_ids, 변형.units) ≥ 0.7 인 최고 후보. 붙으면 stats[bucket].precise 로 저장
   (4+ → goldem/all/diamond/low 구간의 참고치, 7+ → master 구간). 붙지 않은 R 행은 버린다(현재 R은 마스터+ 보드의 17%만 덮는 좁은 정의).
3) E를 붙인다: 편집 덱 유닛 집합과 모든 변형의 자카드 최대값 ≥ 0.5 → 그 변형의 그룹에 editorial 로 첨부(여럿이면 리스트, 대표는 update_time 최신).
   0.5 미만이면 kind:"editorial" 인 독립 덱으로 목록에 남긴다(현재 방식과 같음).
4) M을 붙인다: 그룹의 대표 변형 유닛 집합과 클러스터 units_string 자카드 최대값 ≥ 0.5(기존 SIMILARITY_THRESHOLD) → global{cluster, similarity, name, stats[scope], levelling, difficulty}.
   그룹의 어느 변형도 매칭되지 않으면 sources.onlyInChina = true (기존 onlyInChina 정의 유지, compared 플래그도 유지).
5) 대표 변형 = 기본 구간(goldem)에서 use_num 최대(없으면 다른 구간 순). 카드의 units/traits/carry/teamCode 는 대표 변형(편집 덱이 붙었으면 편집 덱 보드 우선)에서 만든다.
6) 상세 호출(노출 덱만, 구간별 상위 N, 전체 ≤ 100회/일): tft_lineup_all_detail(augment_data, key_champion_data, equip_data, level_data),
   tft_lineup_position(칸별 use_rate·win_rate), tft_lineup_key_chess(성급 비율·equip_num). 대표 변형의 원본 파라미터로 호출한다.
7) 인덱스: 기존 name-키 index(item/component/champion/trait/augment)는 유지하고, byId(DA id → 덱 id) 인덱스를 추가한다. 인덱스는 대표 변형 + 편집 덱 유닛·아이템으로 만든다.
```

### 4.4 통합 점수·등급 산식(수집기에서 계산, 앱은 조회만)

구간 b(all/master/diamond/goldem/low)마다:

- `n_b` = 그룹의 변형 use_num 합. `avg_b` = use_num 가중 평균 avg_rank. `top4_b`, `win_b`도 가중 평균. `precise`가 있으면 카드 보조 표시에만 쓰고 등급 계산은 W 수치로 통일한다(정의가 다른 두 모집단을 섞지 않기 위해).
- 표본 보정 평균(베이지안, 4.5로 수축): `adjAvg_b = (n_b · avg_b + K · 4.5) / (n_b + K)`, `K = 200`.
- 등급(절대 컷, 상수 표 `GRADE_CUTS`로 한 곳에서 조정): `S: adjAvg ≤ 3.90`, `A: ≤ 4.15`, `B: ≤ 4.40`, `C: ≤ 4.70`, `D: 그 외`. 胜率阵容 목록은 avg_rank ≤ 4.0만 노출되므로 실제 분포는 S/A/B가 대부분이다. `verify.py`가 S 비율 ≤ 30%, 등급 분포 비어 있지 않음을 검사한다.
- 표본 부족: `n_b < 300`이면 `grade = null`, 앱은 흐리게 + "표본 부족". 편집 덱만 있는 독립 덱은 `editorialTier`(SS/S/A/B/C)를 그대로 보여 주되 배지 색을 다르게 한다.
- 추세: `trend_b = "up"` if `avgDiff < -0.05 && pickDiff > 0`, `"down"` if `avgDiff > 0.05 && pickDiff < 0`, else `"flat"`.
- 카드 4수치: 평균 등수 `avg_b`, 픽률 `pick_b`(= use_rate 합; 정의는 "이 구성이 등장한 보드 비율"), 승률 `win_b`, TOP4 `top4_b`. 표본 라벨 `n_b · 3일(가정) · 구간 이름 · dtstatdate`. 胜率阵容의 집계 기간은 문서화되지 않았으므로 라벨에는 기준일만 적고 기간은 적지 않는다.
- 글로벌/KR 비교 줄: `global.stats[scope]`의 places[0..7]로 `avg = Σ(i·p_i)/n`, `top4`, `win` 계산. 스코프 이름과 n을 같이 표시("KR 플래+ n=9,849 · 4.2등").
- 정렬 기본값: `grade` 오름차순(S→D, null 마지막) → `adjAvg` 오름차순. 옵션: 픽률, 상승(trend up 우선 + avgDiff), 표본.

### 4.5 출처 배지

| 배지 | 조건 | 색 |
|---|---|---|
| `편집` | editorial 첨부 | 옥색 테두리 |
| `이전 패치 작성` | editorial.updatedAt < 중국 16.18 첫 빌드 시작(`tft_recent_versions`의 최소 start_time, 현재 2026-09-10 07:00 CST) | 회색 |
| `중국 통계` | stats에 구간 하나 이상 | 기본 |
| `글로벌 있음` / `중국 한정` | global 매칭 여부(기존 onlyInChina 의미) | 호박색(중국 한정) |
| `KR` | global.stats.kr_plat.n ≥ 300 | 파랑 |
| `▲`/`▼` | trend | 초록/빨강 |

### 4.6 검증 게이트(`collector/verify.py` 확장)

- 소스별 최소 행수: 편집 덱 ≥ 20, group_list 조합 ≥ 300(goldem), lineup_rank ≥ 50, 클러스터 ≥ 30, 상세 augment_data 보유 덱 ≥ 50%.
- HTTP 200 빈 응답을 실패로(lol.qq 짧은 version·빈 req_params, metatft 잘못된 키, 모두 200 빈 값).
- `sum(places) == count`(metatft), `avg`가 top1·top4 비율로 가능한 구간 안(lol.qq, 이 검사가 不限 버킷을 100% 걸러냈다), 목록 가중 평균 4.5±0.05(comps_stats 총합), DA 조인율(챔피언 90% 미만 실패), `filter_adjustment.override_applied == false`.
- 원천 교차검증: metatft 글로벌 플래+ 챔피언 평균의 이전 수집 대비 차이 중앙값이 0.1을 넘으면 경고(배포는 함).
- 히트맵 방향 검사: 편집 덱이 붙은 그룹에서 편집 좌표(row,col)와 `tft_lineup_position` 최빈 칸의 일치율이 40% 미만이면 positions 필드를 싣지 않고 경고(x/y 축 해석이 틀렸을 가능성).

---

## 5. 수집기 출력 데이터 계약

원칙:
- 앱은 계산하지 않고 조회만 한다. 등급·평균·픽률·Δ·라벨은 전부 수집기가 넣는다.
- 모든 파일은 `data/` 아래 정적 JSON이며 `raw.githubusercontent.com/ksj1739-del/tft-deck-app/main/data/` 로 배포된다(기존 `FEED_BASE_URL`).
- 파일마다 작은 `version`(해시 포함)이 있고, 앱은 해시가 바뀐 파일만 내려받는다.
- 숫자는 JSON 숫자(문자열 금지). 비율은 0~1 소수 3자리, 평균 등수는 소수 2자리, 카운트는 정수.
- `icon`은 CommunityDragon 상대 경로(`assets/...png`)를 기본으로 하고, 예외적으로 절대 URL(`https://…`)을 허용한다(pet 소환물 등). 앱의 `iconUrl()`은 `http`로 시작하면 그대로 쓴다.
- 이름은 CommunityDragon ko_kr 우선, 없으면 metatft ko 사전, 없으면 `collector/overrides_ko.json`, 그래도 없으면 id 원문(`untranslatedIds`에 기록).
- 구간(bucket)과 스코프(scope) 키는 아래 고정 문자열을 쓴다.

| 구분 | 키 | 라벨 | 원천 |
|---|---|---|---|
| 덱 구간 | `all` / `master` / `diamond` / `goldem` / `low` | 전체 / 마스터+ / 다이아+ / 골드~에메랄드 / 골드 이하 | lol.qq 胜率阵容 tier_part 255 / 0 / 1 / 2 / 3 |
| 통계 스코프 | `glob_plat` / `kr_plat` / `kr_master` | 글로벌 플래티넘+ / KR 플래티넘+ / KR 마스터+ | metatft (rank 집합, `server=KR`) 3일 |
| 통계 스코프 | `cn_plat` / `cn_master` | 중국 플래티넘+ / 중국 마스터+ | lol.qq 数据检索器 tier `4+` / `7+`, 3일, version '' |

### 5.1 `data/version.json` (덱 피드 버전, schemaVersion 2)

```json
{
  "schemaVersion": 2,
  "generatedAt": "2026-09-16T20:05:12Z",
  "set": "s18",
  "setNumber": 18,
  "patch": "16.18",
  "patchGlobal": "18.2",
  "qqBuild": "16.18.817.4437",
  "qqPatchStart": "2026-09-10T07:00:00+08:00",
  "statDate": "2026-09-15",
  "deckCount": 61,
  "editorialCount": 26,
  "onlyInChinaCount": 9,
  "teamCodeCount": 61,
  "metatftSet": "TFTSet18",
  "metatftClusterId": 423,
  "assetBase": "https://raw.communitydragon.org/latest/game/",
  "sources": {
    "modeRegistry": "ok", "lolqq": "ok", "lolqqWinrate": "ok", "lolqqDatasearch": "ok", "lolqqDetail": "ok",
    "lolqqStatic": "ok", "metatft": "ok", "metatftStats": "ok", "namesKo": "ok", "namesEn": "ok", "teamPlanner": "ok"
  },
  "untranslatedIds": [],
  "contentHash": "1f0c…"
}
```
`sources` 값은 `ok` / `stale`(이전 결과 재사용) / `missing`(생략). 앱의 기존 `FeedVersion.hasDegradedSource`·`metatftCompared` 의미는 유지된다(`metatft`가 ok가 아니면 중국 한정 배지 비표시).

### 5.2 `data/decks.json` v2

최상위:
```json
{
  "version": { …5.1과 동일… },
  "buckets": {
    "goldem": {"label": "골드~에메랄드", "qqTierPart": "2", "listDate": "20260915", "detailDate": "20260914", "groups": 45, "variants": 425, "default": true},
    "all":    {"label": "전체",          "qqTierPart": "255", "listDate": "20260915", "detailDate": "20260914", "groups": 52, "variants": 479},
    "master": {"label": "마스터+",       "qqTierPart": "0",   "listDate": "20260915", "detailDate": "20260914", "groups": 9,  "variants": 10},
    "diamond":{"label": "다이아+",       "qqTierPart": "1",   "listDate": "20260915", "detailDate": "20260914", "groups": 30, "variants": 76},
    "low":    {"label": "골드 이하",     "qqTierPart": "3",   "listDate": "20260915", "detailDate": "20260914", "groups": 40, "variants": 218}
  },
  "scopes": {
    "glob_plat": {"label": "글로벌 플래티넘+", "source": "metatft", "days": 3, "boards": 6799416, "updatedAt": "2026-09-15T13:14:40Z"},
    "kr_plat":   {"label": "KR 플래티넘+",     "source": "metatft", "days": 3, "boards": 1108328, "updatedAt": "2026-09-15T13:14:41Z"},
    "kr_master": {"label": "KR 마스터+",       "source": "metatft", "days": 3, "boards": 44976,   "updatedAt": "2026-09-15T13:14:42Z"}
  },
  "gradeCuts": {"S": 3.90, "A": 4.15, "B": 4.40, "C": 4.70, "minSample": 300, "shrinkK": 200},
  "decks": [ … 그룹/독립 덱 … ],
  "index": {
    "item": {"무한의 대검": [{"deck": "g-7f3a9c1b2d", "unit": "장로 드래곤", "role": "main"}]},
    "component": {"B.F. 대검": ["g-7f3a9c1b2d"]},
    "champion": {"장로 드래곤": ["g-7f3a9c1b2d"]},
    "trait": {"협곡야수": ["g-7f3a9c1b2d"]},
    "augment": {"초월": ["g-7f3a9c1b2d"]},
    "byId": {
      "champion": {"DA_18_ElderDragon": ["g-7f3a9c1b2d"]},
      "item": {"DA_InfinityEdge": ["g-7f3a9c1b2d"]},
      "trait": {"DA_Riftbeast18": ["g-7f3a9c1b2d"]},
      "augment": {"DA_Ascension": ["g-7f3a9c1b2d"]}
    }
  },
  "catalog": {
    "champions": [{"id": "DA_18_ElderDragon", "name": "장로 드래곤", "nameEn": "Elder Dragon", "cost": 5, "icon": "assets/characters/tft18_elderdragon/tft18_elderdragon_square.png"}],
    "traits":    [{"id": "DA_Riftbeast18", "name": "협곡야수", "icon": "assets/ux/traiticons/trait_icon_18_riftbeast.png"}],
    "items":     [{"id": "DA_InfinityEdge", "name": "무한의 대검", "icon": "assets/maps/tft/icons/items/hexcore/tft_item_infinityedge.png", "components": ["DA_Component_BFSword", "DA_Component_SparringGloves"]}],
    "augments":  [{"id": "DA_Ascension", "name": "초월", "icon": "assets/maps/tft/icons/augments/hexcore/ascension_ii.png"}],
    "pets":      [{"id": "DA_18_IronbarkTree", "name": "철갑 나무", "icon": "https://game.gtimg.cn/images/lol/act/img/tft/original-image/tft18_ironbarktree_square.tft_set18.png", "kind": "pet"}]
  }
}
```
`index`의 name-키 다섯 축과 `catalog` 네 배열은 v1과 같은 형태·의미로 유지한다(현재 앱·검색·테스트가 그대로 읽는다). `byId`와 `pets`는 추가다.

덱(그룹) 객체 — v1 필드는 이름·의미를 유지하고(`id, name, nameCn, tier, tierOrder, patch, finalLevel, carryId, traits, units, itemOrder, augments, notesCn, author, updatedAt, teamCode, metatft`), `boards/early/mid`는 `stages`로 대체한다:

```json
{
  "id": "g-7f3a9c1b2d",
  "kind": "group",
  "key": "DA_Riftbeast18.3|DA_18_ElderDragon",
  "name": "장로 드래곤 · 3 협곡야수 2 선봉대 2 전쟁기계",
  "nameCn": "【巨龙95】3峡谷野怪2地狱火2主宰2重装战士",
  "tier": "S",
  "tierOrder": 1,
  "editorialTier": "SS",
  "patch": "16.18",
  "finalLevel": 8,
  "carryId": "DA_18_ElderDragon",
  "mainTraits": [{"id": "DA_Riftbeast18", "name": "협곡야수", "count": 3, "style": 1, "icon": "assets/ux/traiticons/trait_icon_18_riftbeast.png"}],
  "traits": [{"id": "DA_Riftbeast18", "name": "협곡야수", "count": 3, "style": 1, "icon": "…"}, {"id": "DA_18_Vanguard", "name": "선봉대", "count": 2, "style": 1, "icon": "…"}],
  "units": [
    {"id": "DA_18_ElderDragon", "name": "장로 드래곤", "nameEn": "Elder Dragon", "cost": 5, "icon": "…", "star": 2, "carry": true, "carryRank": 1, "row": 3, "col": 5,
     "items": [{"id": "DA_Guinsoos<플레이어>blade", "name": "구인수의 격노검", "icon": "…"}], "itemsBackup": []},
    {"id": "DA_Draven18", "name": "드레이븐", "cost": 4, "icon": "…", "star": 3, "carry": false, "carryRank": 2, "row": 4, "col": 7, "items": [], "itemsBackup": []},
    {"id": "DA_18_IronbarkTree", "name": "철갑 나무", "cost": null, "icon": "https://game.gtimg.cn/…/tft18_ironbarktree_square.tft_set18.png", "kind": "pet", "star": 1, "carry": false, "row": 1, "col": 4, "items": [], "itemsBackup": []}
  ],
  "sources": {"editorial": true, "editorialStale": true, "cnStats": true, "global": true, "kr": true, "onlyInChina": false},
  "stats": {
    "goldem": {"n": 17059, "avg": 3.61, "adjAvg": 3.62, "top4": 0.688, "win": 0.108, "pick": 0.0021, "avgDiff": -0.1, "pickDiff": -0.0002, "grade": "S", "trend": "flat",
               "precise": {"scope": "cn_plat", "n": 3139, "avg": 4.14, "top4": 0.547, "win": 0.193, "finalLevel": 9}},
    "all":    {"n": 25133, "avg": 3.70, "adjAvg": 3.71, "top4": 0.66, "win": 0.10, "pick": 0.0019, "avgDiff": 0.0, "pickDiff": 0.0001, "grade": "S", "trend": "flat"},
    "master": {"n": 210, "avg": 3.3, "adjAvg": 3.91, "top4": 0.71, "win": 0.14, "pick": 0.0025, "avgDiff": null, "pickDiff": null, "grade": null, "trend": "flat"}
  },
  "global": {
    "cluster": 423009, "similarity": 0.78, "name": "DA_18_Elderwood, DA_18_Ezreal, DA_Draven18",
    "levelling": "빠른 9레벨", "levellingRaw": "Fast 9", "difficulty": "보통", "difficultyRaw": -0.014,
    "stats": {
      "glob_plat": {"n": 556572, "avg": 4.22, "top4": 0.55, "win": 0.13, "places": [72354, 71012, 70988, 70210, 69880, 68990, 67530, 65608]},
      "kr_plat":   {"n": 9849,   "avg": 4.20, "top4": 0.56, "win": 0.13, "places": [1281, 1290, 1266, 1245, 1231, 1200, 1180, 1156]},
      "kr_master": {"n": 620,    "avg": 4.31, "top4": 0.52, "win": 0.11, "places": [70, 78, 82, 92, 80, 79, 72, 67]}
    },
    "finalLevels": [{"level": 8, "share": 0.466}, {"level": 9, "share": 0.465}, {"level": 10, "share": 0.05}],
    "counters": [{"cluster": 423003, "deck": "g-1c2d3e4f5a", "placeChange": 0.42}]
  },
  "keyUnits": [{"id": "DA_18_ElderDragon", "star1": 0.328, "star2": 0.668, "star3": 0.004, "items": 2.89, "avg": 3.67}],
  "positions": {"DA_18_ElderDragon": [{"row": 3, "col": 5, "use": 0.157, "win": 0.091}, {"row": 3, "col": 4, "use": 0.115, "win": 0.093}]},
  "levelDist": [{"level": 8, "share": 0.025, "avg": 6.7}, {"level": 9, "share": 0.796, "avg": 4.0}, {"level": 10, "share": 0.179, "avg": 1.9}],
  "augmentStats": [
    {"id": "DA_Ascension", "name": "초월", "icon": "…", "rank": 1, "n": 634, "avg": 3.16, "stage": [3.23, 2.68, 4.33], "stageLowSample": [false, false, true]},
    {"id": "DA_SilverSpoon", "name": "은수저", "icon": "…", "rank": 5, "n": 306, "avg": 3.25, "stage": [null, 3.24, 3.23], "stageLowSample": [true, false, false]}
  ],
  "itemWearers": [{"item": {"id": "DA_18_EmblemVanguard", "name": "선봉대 상징", "icon": "…"}, "wearers": [{"id": "DA_Taric18", "name": "타릭", "n": 94, "avg": 2.5}]}],
  "variants": [
    {"id": "v-1a2b3c4d5e", "representative": true, "editorialId": "14266",
     "units": [{"id": "DA_18_ElderDragon", "star": 2, "items": ["DA_Guinsoos<플레이어>blade", "DA_InfinityEdge", "DA_KrakensFury"]}, {"id": "DA_Draven18", "star": 3}],
     "carryId": "DA_18_ElderDragon", "assistIds": ["DA_Taric18", "DA_18_Kennen"],
     "stats": {"goldem": {"n": 17059, "avg": 3.6, "top4": 0.688, "win": 0.108}}}
  ],
  "editorial": {
    "id": "14266", "author": "巨龙95", "updatedAt": "2026-08-27 15:10:02", "stale": true, "quality": "SS", "needLevel": 8,
    "stages": [
      {"key": "early", "label": "초반", "level": 5, "round": "2-3", "units": [{"id": "DA_18_Yorick", "star": 1, "row": 1, "col": 5}, {"id": "DA_18_IronbarkTree", "kind": "pet", "star": 1, "row": 1, "col": 4}]},
      {"key": "mid",   "label": "중반", "level": 8, "round": "4-3", "units": [{"id": "DA_Draven18", "star": 3, "row": 4, "col": 7, "items": ["DA_Guinsoos<플레이어>blade"]}]},
      {"key": "final", "label": "최종", "level": 8, "round": null,  "units": [{"id": "DA_18_ElderDragon", "star": 2, "row": 3, "col": 5, "carry": true, "items": ["DA_Guinsoos<플레이어>blade", "DA_InfinityEdge", "DA_KrakensFury"]}]}
    ],
    "itemOrder": [{"id": "DA_Component_BFSword", "name": "B.F. 대검", "icon": "…"}],
    "augments": {"recommended": [{"id": "DA_Ascension", "name": "초월", "icon": "…"}], "alternatives": []},
    "notesCn": {"items": "…", "augments": "…"},
    "teamCode": {"code": "023fc3fb41540440b4303ed428000000TFTSet18", "units": 8, "omitted": ["철갑 나무"], "truncated": null}
  },
  "itemOrder": [{"id": "DA_Component_BFSword", "name": "B.F. 대검", "icon": "…"}],
  "augments": {"recommended": [{"id": "DA_Ascension", "name": "초월", "icon": "…"}], "alternatives": []},
  "notesCn": {"items": "…", "augments": "…"},
  "author": "巨龙95",
  "updatedAt": "2026-09-15",
  "teamCode": {"code": "023fc3fb41540440b4303ed428000000TFTSet18", "units": 8, "omitted": ["철갑 나무"], "truncated": null},
  "metatft": {"similarity": 0.78, "matchedComp": "DA_18_Elderwood, DA_18_Ezreal, DA_Draven18", "onlyInChina": false, "compared": true}
}
```

필드 규칙:
- `kind`: `group`(胜率阵容 그룹) 또는 `editorial`(편집 덱만 있는 독립 덱). `editorial` 종류는 `stats`가 비고 `tier = editorialTier`, `sources.cnStats=false`.
- `units`(대표 보드)는 v1 Unit 구조 그대로이며 `carryRank`(1~3, 메인C/보조C/2보조C; W의 `main_c_chess`/`assist_chess`/`second_assist_chess`)와 `kind`(생략 또는 `pet`)가 추가된다. 편집 덱이 붙은 그룹은 편집 덱 최종 보드(좌표 있음), 아니면 W 대표 조합(좌표 없음, `row/col = null`; 이때 앱은 히트맵 최빈 칸을 배치로 쓴다).
- 편집 덱의 `stages`는 lol.qq `y21_early_heros`(early), `y21_metaphase_heros`(mid), `hero_location`(final)이고 레벨은 `needLevel_early / needLevel_middle / needLevel`, 라운드는 `early_round / metaphase_round`. `hero_location_l6/l8/l9`는 쓰지 않는다(26/26 덱에서 early/mid/final 복사본임이 확인됨). 별은 모든 단계에서 `level_3_heros` 포함 시 3, 아니면 원본 `numStar`. 캐리는 최종 보드의 `is_carry_hero`이고 초·중반에는 최종 캐리와 같은 id에만 `carry:true`를 물려준다. `hero_id`가 빈 pet 항목은 `chess_id`를 id로 쓰고 `kind:"pet"`, 이름·아이콘은 lol.qq `chess.js`(16.18)에서 채운다. pet은 덱 코드·유사도·인덱스에서 제외.
- `positions`는 유닛별 상위 3칸. `tft_lineup_position.position_data.position.{x,y}`에서 `row=y`, `col=x`(x∈1..7, y∈1..4 확인 후 매핑, 4.6 방향 검사 통과 시에만 실림).
- `augmentStats.stage[i]`는 `1_/2_/3_avg_rank`. 원본 `"0"`은 null. 소수부가 없는 값(`"1"`, `"4"`, `"3.5"`)은 `stageLowSample[i]=true`.
- `global.stats[scope].places`는 comps_stats의 앞 8개. `n`은 9번째 원소(count).
- `levelling` 한글화: `Fast 8`→`빠른 8레벨`, `Fast 9`→`빠른 9레벨`, `Standard`→`표준`, `Reroll`/`Slow Roll`→`리롤`, 그 외 원문. `difficulty`: `< -0.1 쉬움`, `> 0.1 어려움`, 그 사이 `보통`(metatft 화면 라벨 기준, 경계값은 추정이므로 상수로 둔다).

### 5.3 `data/stats/version.json` (도감 피드 버전)

```json
{"schemaVersion": 1, "generatedAt": "2026-09-16T20:07:40Z", "set": "s18", "setNumber": 18, "patchGlobal": "18.2", "patch": "16.18",
 "qqBuild": "16.18.817.4437", "statDate": "2026-09-15",
 "files": {"champions": "3a9f…", "traits": "b71c…", "items": "0c44…", "augments": "9de2…"},
 "sources": {"metatftStats": "ok", "metatftDetail": "ok", "lolqqDatasearch": "ok", "namesKo": "ok", "metatftLookup": "ok", "augmentsTiers": "ok", "decksJson": "ok"},
 "contentHash": "…"}
```
앱은 이 파일만 받아 `files.<name>` 해시가 바뀐 파일만 내려받는다.

### 5.4 `data/stats/champions.json`

```json
{
  "version": {"schemaVersion": 1, "generatedAt": "…", "patchGlobal": "18.2", "patch": "16.18", "statDate": "2026-09-15", "contentHash": "3a9f…"},
  "scopes": {
    "glob_plat": {"label": "글로벌 플래티넘+", "source": "metatft", "days": 3, "games": 5785016, "updatedAt": "2026-09-15T13:14:43Z", "unitsPerBoard": 8.38},
    "kr_plat":   {"label": "KR 플래티넘+",     "source": "metatft", "days": 3, "games": 967672,  "updatedAt": "…", "unitsPerBoard": 8.40},
    "kr_master": {"label": "KR 마스터+",       "source": "metatft", "days": 3, "games": 38960,   "updatedAt": "…", "unitsPerBoard": 8.41},
    "cn_plat":   {"label": "중국 플래티넘+",   "source": "lolqq",   "days": 3, "games": 3534340, "statDate": "2026-09-15", "build": "", "meanAvg": 4.174},
    "cn_master": {"label": "중국 마스터+",     "source": "lolqq",   "days": 3, "games": 97474,   "statDate": "2026-09-15", "build": "", "meanAvg": 4.087}
  },
  "gradeCuts": {"S": 0.30, "A": 0.10, "B": -0.10, "C": -0.30, "minSample": 1000},
  "prevPatch": {"patchGlobal": "18.1d", "frozenAt": "2026-09-09T20:39:41Z"},
  "champions": [
    {
      "id": "DA_18_Ahri", "name": "아리", "nameEn": "Ahri", "cost": 4,
      "icon": "assets/characters/tft18_ahri/tft18_ahri_square.png",
      "traits": [{"id": "DA_18_Blossom", "name": "개화"}, {"id": "DA_18_Invoker", "name": "기원자"}],
      "role": "주문술사",
      "ability": {"name": "구미호", "desc": "적에게 마법 피해를 입힙니다 (455/685/3500)", "mana": [20, 100]},
      "base": {"health": [700, 1260, 2268], "attackDamage": [40, 60, 90], "armor": 30, "magicResist": 30, "attackSpeed": 0.75, "range": 4},
      "changed": null,
      "stats": {
        "glob_plat": {"n": 134007, "places": [17013, 17438, 18173, 17866, 17296, 16486, 15735, 14000], "avg": 4.37, "top4": 0.525, "win": 0.127, "pick": 0.139, "delta": -0.02, "grade": "B"},
        "kr_plat":   {"n": 14200, "places": [1800, 1810, 1820, 1790, 1760, 1740, 1730, 1750], "avg": 4.48, "top4": 0.51, "win": 0.13, "pick": 0.147, "delta": 0.07, "grade": "B"},
        "kr_master": {"n": 620, "places": [80, 80, 79, 78, 77, 76, 75, 75], "avg": 4.47, "top4": 0.51, "win": 0.13, "pick": 0.159, "delta": 0.06, "grade": "B"},
        "cn_plat":   {"n": 1526455, "top1": 292487, "top4": 898053, "avg": 3.99, "top4Rate": 0.588, "win": 0.192, "pick": 0.432, "delta": -0.19, "grade": "A"},
        "cn_master": {"n": 47565, "top1": 8696, "top4": 27232, "avg": 4.09, "top4Rate": 0.573, "win": 0.183, "pick": 0.488, "delta": 0.0, "grade": "B"}
      },
      "starStats": {"glob_plat": [{"star": 1, "n": 40000, "avg": 5.2}, {"star": 2, "n": 90000, "avg": 4.1}, {"star": 3, "n": 4007, "avg": 2.2}]},
      "items": {
        "scope": "glob_plat",
        "top": [{"id": "DA_Guinsoos<플레이어>blade", "name": "구인수의 격노검", "icon": "…", "n": 51262, "avg": 4.18, "delta": -0.19}],
        "builds": [{"items": ["DA_Guinsoos<플레이어>blade", "DA_InfinityEdge", "DA_KrakensFury"], "n": 8210, "avg": 3.98}]
      },
      "cnItems": [{"id": "DA_Guinsoos<플레이어>blade", "name": "구인수의 격노검", "n": 192382, "avg": 3.92}],
      "prev": {"glob_plat": {"avg": 4.30, "pick": 0.15}},
      "decks": ["g-7f3a9c1b2d"]
    }
  ]
}
```
규칙:
- metatft 스코프의 `avg = Σ((i+1)·places[i]) / n`, `top4 = Σplaces[0..3]/n`, `win = places[0]/n`, `pick = n / games`(보드당 채용률). `delta = avg − 4.5`의 부호를 뒤집지 않고 그대로(음수가 좋음). `grade`는 `4.5 − avg`가 `gradeCuts`(S > .3, A > .1, B > −.1, C > −.3, 그 외 D; metatft 번들 공식)인 등급. `n < minSample`이면 `grade=null`.
- lol.qq 스코프는 `n = unit_s`, `top1/top4 = top1_cnt/top4_cnt`, `avg`는 원본 `avg_rank`(3자리→2자리), `pick = unit_s/total`, `delta = avg − scopes.<cn>.meanAvg`(unit_s 가중 평균; 서버 `avg_rank_delta`는 쓰지 않음). 수집기는 `avg`가 top1/top4 비율로 가능한 구간(`1·p1 + 2·(p4−p1) + 5·(1−p4) ≤ avg ≤ 1·p1 + 4·(p4−p1) + 8·(1−p4)`) 밖이면 그 스코프를 통째로 뺀다.
- `items.top`은 metatft `unit_detail_items.items`(`itemName`의 `-N` 접미사를 제거하고 같은 아이템끼리 합산; N의 뜻은 미확인이므로 합산이 안전) 상위 5개, `builds`는 `builds`(`A|B|C`) 상위 3개(n ≥ 200). `delta`는 아이템 착용 시 평균 − 챔피언 전체 평균.
- `cnItems`는 lol.qq `tft_equip_rank(ShowHero=1)`의 `DA_챔피언#DA_아이템` 행에서 `build_s` 상위 5개(최소 build_s 300). avg는 참고용.
- `base`·`ability`는 CDragon ko_kr set 18 champions에서. 스킬 수치 텍스트는 metatft ko 사전 `ability.desc`(변수 계산본)를 우선하고 CDragon desc의 `@…@` 자리표시자는 제거한다. `changed`는 lol.qq chess.js `proStatus`(增强→`buff`, 削弱→`nerf`, 最新→`new`, 없으면 null).
- `prev`는 패치가 바뀐 날 이전 패치 마지막 값을 동결한 것(`data/stats/prev_snapshot.json`에 수집기가 보관).
- `decks`는 decks.json `index.byId.champion` 복사(도감→덱 이동용).
- 챔피언 목록 = CDragon set 18 champions 중 `cost ≥ 1`이고 TFT18_* 소환물이 아닌 것(약 74). 통계 행이 없으면 `stats`는 빈 객체.

### 5.5 `data/stats/traits.json`

```json
{
  "version": {…},
  "scopes": {…champions.json과 같은 5개…},
  "traits": [
    {
      "id": "DA_18_Fae", "name": "요정", "nameEn": "Fae", "type": "origin", "icon": "assets/ux/traiticons/trait_icon_18_fae.png",
      "desc": "요정 …",
      "breakpoints": [{"units": 3, "style": 1, "desc": "…"}, {"units": 5, "style": 2, "desc": "…"}, {"units": 7, "style": 3, "desc": "…"}, {"units": 10, "style": 4, "desc": "…"}],
      "champions": [{"id": "DA_18_Tristana", "name": "트리스타나", "cost": 1}],
      "stats": {
        "glob_plat": {"byUnits": {"3": {"n": 900000, "places": [...], "avg": 4.55, "top4": 0.49, "win": 0.11, "pick": 0.16, "delta": 0.05, "grade": "C"}, "5": {…}}},
        "cn_plat":   {"byUnits": {"3": {"n": 1620557, "top1": 304617, "top4": 953423, "avg": 4.00, "top4Rate": 0.588, "win": 0.188, "pick": 0.459, "delta": -0.17, "grade": "A"}}}
      },
      "combos": [{"with": {"id": "DA_18_Adaptor", "name": "적응가", "units": 3}, "units": 3, "top4": 0.61, "win": 0.14, "trend": [0.58, 0.60, 0.61, 0.60, 0.61]}],
      "decks": ["g-7f3a9c1b2d"]
    }
  ]
}
```
규칙: metatft `traits` 행 `DA_X_N` → `(DA_X, breakpoints[N-1].units)`. 특성 등급 컷은 `S > .5, A > .25, B > 0, C > −.25`(metatft 번들 공식). `type`은 CDragon(`origin`/`class`; unique는 제외). `style`은 CDragon effects의 style 값(1 브론즈 … 4 프리즘, lolchess 등급 칩과 같은 의미). `combos`는 lol.qq `tft_trait_strength_trend`(주특성 2개 조합, 숫자 traitId→DA, `1_..5_`를 뒤집어 최신이 마지막)에서 이 특성이 들어간 행만; 없으면 빈 배열.

### 5.6 `data/stats/items.json`

```json
{
  "version": {…},
  "scopes": {…},
  "items": [
    {
      "id": "DA_InfinityEdge", "name": "무한의 대검", "nameEn": "Infinity Edge", "kind": "completed",
      "icon": "assets/maps/tft/icons/items/hexcore/tft_item_infinityedge.png",
      "desc": "치명타 확률 …", "components": ["DA_Component_BFSword", "DA_Component_SparringGloves"],
      "stats": {
        "glob_plat": {"n": 2438487, "places": [...], "avg": 4.26, "top4": 0.54, "win": 0.13, "pick": 0.36, "grade": "A"},
        "kr_plat": {…}, "kr_master": {…},
        "cn_plat": {"n": 1348678, "pick": 0.38, "avg": null, "grade": null}
      },
      "wearers": {
        "glob_plat": [{"id": "DA_18_ElderDragon", "name": "장로 드래곤", "n": 120000, "avg": 3.80, "delta": -0.16}],
        "cn_plat":   [{"id": "DA_Draven18", "name": "드레이븐", "n": 192382, "avg": 3.92}]
      },
      "stages": [{"stage": 2, "n": 12000, "win": 0.58}, {"stage": 3, "n": 400000, "win": 0.55}],
      "prev": {"glob_plat": {"avg": 4.30}},
      "decks": ["g-7f3a9c1b2d"]
    }
  ],
  "components": ["DA_Component_BFSword", "DA_Component_RecurveBow", "DA_Component_NeedlesslyLargeRod", "DA_Component_TearOfTheGoddess", "DA_Component_ChainVest", "DA_Component_NegatronCloak", "DA_Component_GiantsBelt", "DA_Component_SparringGloves", "DA_Component_Spatula", "DA_Component_FryingPan"],
  "recipes": {"DA_Component_BFSword|DA_Component_SparringGloves": "DA_InfinityEdge"}
}
```
규칙: `kind` ∈ `component | completed | emblem | artifact | radiant | support | other`(CDragon 태그/이름 규칙 + lol.qq equip.js `type` 교차). 아이템 등급 공식 `(4.5 − avg + p) × 빈도^i`, S > .3, A > .2, B > .1, C > 0, 그 외 D; `p,i`는 일반 .5/1, 상징 .125/.25, 유물·지원·찬란 0/0(metatft 번들). `cn_plat.avg`는 범위 검사 통과 행만, 통과 못 하면 null(중국은 인기도 `pick=build_s/total`만 신뢰). `wearers.glob_plat`은 `item_detail.units` places 상위 5(TFT18_* 제외), `cn_plat`은 `tft_equip_rank(ShowHero=1)`의 build_s 상위 5(최소 300). `stages`는 `item_stage_detail.stage`. `recipes` 키는 부품 두 id를 정렬해 `|`로 이은 것(11×11 조합표용, 같은 부품 두 개도 포함).

### 5.7 `data/stats/augments.json`

```json
{
  "version": {…},
  "meta": {
    "editorTier": {"source": "metatft", "author": "META Spencer", "updatedAt": "2026-09-15T11:11:58Z", "counts": {"S": 24, "A": 84, "B": 129, "C": 21, "D": 0}},
    "cnStats": {"source": "lolqq", "bucket": "goldem", "label": "중국 골드~에메랄드", "detailDate": "20260914", "note": "덱별 상위 5개만 집계된다"}
  },
  "augments": [
    {
      "id": "DA_Ascension", "name": "초월", "nameEn": "Ascension", "rarity": "gold", "tags": ["combat"],
      "icon": "assets/maps/tft/icons/augments/hexcore/ascension_ii.png",
      "desc": "15초 후 아군이 …",
      "editorTier": "A",
      "deckStats": [{"deck": "g-7f3a9c1b2d", "deckName": "장로 드래곤 · 3 협곡야수", "n": 634, "avg": 3.16, "rank": 1, "stage": [3.23, 2.68, 4.33], "stageLowSample": [false, false, true]}],
      "summary": {"n": 2280, "avg": 3.21, "decks": 4},
      "recommendedBy": {"editorial": ["g-7f3a9c1b2d"], "guide": [{"deck": "g-2b3c4d5e6f", "tier": "S", "source": "APHELIOS > Elderwood OR Vanguard"}]},
      "decks": ["g-7f3a9c1b2d", "g-2b3c4d5e6f"]
    }
  ],
  "rounds": {"2-1": {"silver": 0.5, "gold": 0.4, "prismatic": 0.1}, "3-2": {…}, "4-2": {…}}
}
```
규칙: 목록 = lol.qq hex.js 265개 ∪ metatft ko 사전 257개(DA_* 키 합집합, 시즌 18만). `rarity`는 metatft ko 사전 `rarity`(silver/gold/prismatic), 없으면 hex.js `type`(1/2/3). `tags`는 `augments_tiers.content.content.tags`의 쉼표 분리값(`econ, items, combat, trait, scaling, misc`). `editorTier`는 `augments_tiers.tierList`. `deckStats`는 decks.json `decks[].augmentStats`를 뒤집어 만든 것이며 `summary.avg`는 n 가중 평균(덱별 상위 5개 편향이 있음을 앱이 고지). `recommendedBy.editorial`은 편집 덱 hexbuff.recomm, `guide`는 metatft `comp_augment_tiers`의 S만(클러스터→그룹 매칭, distance ≤ 0.35). `rounds`는 게임 상수이며 수집기 상수 파일(`collector/stats/constants.json`)에서 온다(패치 노트로 수기 관리; 값은 미검증이라 초기값은 빈 객체로 두고 앱은 비어 있으면 표를 숨긴다).

### 5.8 아이콘 팩 `data/icons/manifest.json` + `data/icons/icons.zip`

```json
{"schemaVersion": 1, "hash": "e4b1…", "generatedAt": "…", "count": 212, "bytes": 522113, "zip": "icons.zip",
 "files": {"assets/characters/tft18_ahri/tft18_ahri_square.png": "9c1d2e3f4a5b.webp",
           "https://game.gtimg.cn/images/lol/act/img/tft/original-image/tft18_ironbarktree_square.tft_set18.png": "0a1b2c3d4e5f.webp"}}
```
- 파일명 = sha1(원본 경로 문자열)[:12] + `.webp`. 내용이 바뀌어도 이름은 같으므로 `hash`(모든 파일 sha1의 sha1)로 갱신을 판단한다.
- 변환: 챔피언 128px, 아이템·증강 64px, 특성 32px, WebP q80. zip은 고정 타임스탬프(1980-01-01)와 정렬된 순서로 만들어 내용이 같으면 바이트가 같다(git 변경 없음).
- 원천 목록 = `data/decks.json`과 `data/stats/*.json`의 모든 `icon` 값(재귀 수집). 팩에 없는 아이콘은 앱이 원격(CDragon)으로 떨어진다.

---

## 6. 앱 정보 구조(IA)

### 6.1 하단 탭 4개

| 탭 | 라우트 | 화면 | 데이터 |
|---|---|---|---|
| 덱 | `decks` | 통합 덱 목록(2단) | decks.json |
| 도감 | `codex` (+ `codex/champion/{id}`, `codex/trait/{id}`, `codex/item/{id}`, `codex/augment/{id}`) | 챔피언·특성·아이템·증강 | stats/*.json + decks.json 인덱스 |
| 검색 | `search` | 통합 검색(기존) + 도감 항목으로 이동 | decks.json index/catalog |
| 내 정보 | `settings` | 전적 카드, 지난 게임 로비, 게임 연동, 오버레이, 데이터 상태, 출처 | metatft 프로필(기기 직접 호출) |

덱 상세는 `deck/{deckId}`(기존). 도감 상세 4종은 위 라우트. 오버레이에서 "앱에서 보기"는 기존대로 `deck/{id}`.

### 6.2 덱 탭

- 상단 FeedBanner(기존): `패치 16.18(글로벌 18.2) · 덱 61 · 중국 한정 9 · 기준일 9/15 · 3시간 전`. 원본 상태 경고는 기존 로직 유지.
- 티어 구간 세그먼트(가로 칩, 단일 선택, 마지막 선택 기억): 전체 / 마스터+ / 다이아+ / 골드~에메랄드(기본) / 골드 이하. 선택은 `stats[bucket]`을 바꿀 뿐 네트워크가 없다(다섯 구간이 JSON에 미리 들어 있다).
- 정렬 칩: 등급(기본) / 픽률 / 상승 / 표본.
- 필터 칩 줄(가로 스크롤): 초기화, 중국 한정, 편집 덱만, 주 특성(DA trait 칩; 목록에 있는 주특성만, 아이콘+이름), 최종 레벨.
- 목록: 그룹 카드(lolchess 카드 구성 + metatft 위계):
  - 1줄: 등급 배지(통계 등급; null이면 "표본 부족" 회색; 편집 독립 덱은 editorialTier를 테두리 배지로) · 덱 이름(2줄 말줄임) · 출처 배지들(편집 / 이전 패치 / 중국 한정 / KR) · ▲▼.
  - 2줄: 운영 칩(`빠른 8레벨`·`보통`은 global이 있을 때만) · `Lv 8 완성` · 주 특성 칩 4개(기존 TraitChip).
  - 3줄: 캐리 3명(carryRank 1~3)은 초상화 40dp + 아이템 3개 크게, 나머지 유닛은 26dp 초상화 줄(기존 UnitGrid 축소). 3성 별·캐리 테두리 유지.
  - 4줄(고정 4수치): `평균 등수 3.61` / `픽률 0.2%` / `승률 10.8%` / `TOP4 68.8%` — 모든 카드 같은 자리. 값이 없으면 `-`.
  - 5줄(작게): `n=17,059 · 골드~에메랄드 · 9/15` (+ global이 있으면 `KR 플래+ 4.2등 n=9,849`).
  - 카드 탭 → 상세. 길게 누르기 → 고정/숨김 메뉴(기기 로컬 저장, 고정은 맨 위, 숨김은 필터 "숨긴 덱 보기"로 복구).
  - 그룹에 변형이 2개 이상이면 카드 하단 "변형 N개" 펼침 → 변형 행(유닛 얼굴 줄 + n·평균). 변형 행 탭 → 상세의 변형 섹션으로.
- 빈 상태·로딩 상태는 기존 로직 유지(`searchReady` 구분).

### 6.3 덱 상세

> 2026-09-19 UX 검토 WP-A3 로 다시 짰다(`docs/ux-review/2026-09-19/final_plan.md` WP-A3, D1~D18). 아래가 현재 순서다.

순서대로(세로 스크롤 하나, 내부 스크롤 없음):
0. 머리말: `[등급 배지(채움=metatft · 테두리+'중국'=중국 한정 · '편'=편집)][표본 적음][▲▼] 별칭` / `'{운영} · 마무리 8레벨 58.9% · 9레벨 36.5%'`(metatft `finalLevels` 10% 이상 둘까지; 중국 한정 덱은 `'최종 N레벨'`) / 특성 칩 4개 + `'+N'` / 네 수치(평균 등수 · TOP4 · 픽률 · 승률, 값 20sp — 카드에서 뺀 승률은 여기서 본다) / `'골드~에메랄드 기준 ▾ · 작가 ○○ ▾ … ⓘ'` / `[오버레이로 보기][코드 복사]`(같은 폭·44dp) / 부분 덱 코드 안내. 구간 ▾ 는 **이 화면 안에서만** 바뀌고 목록·오버레이 구간(DeckPrefs)을 바꾸지 않는다(D5·N12). 긴 원래 이름·KR 배지·표본 줄·비교 줄·`'N레벨 완성'` 은 없다(D1·D3·D4). 스크롤이 머리말을 지나면 위에 `[별칭][코드 복사]` 40dp 띠를 고정한다(D6).
1. 캐리·아이템: 캐리별 아이템 3 + 대체 아이템, 3성 비율 30% 이상이면 `'3성 목표 NN%'`(예전 핵심 유닛 섹션 흡수, D11), 캐리가 아닌 착용자는 접힘, 조합 재료 우선순위.
2. 레벨별 구성(§13.3): 레벨 칩(숫자만) + 캡션 `'8레벨 4-2 도달 · 롤다운 9레벨'` + 1순위 구성 한 줄(등수·판수 없음). 같은 출처 아래 순위는 1순위 표본 10% 이상만 `'다른 구성 N개'`(접힘). 출처 글자는 출처가 둘 이상일 때만.
3. 추천 증강: 한 목록. 편집 덱 작가가 고른 것만 `'작가'` 배지, 통계 상위와 id 로 합침, 등수는 300판 이상만(흐리기 없음), `고른 시점 [전체][2-1][3-2][4-2]`(D12·V20).
4. 배치: 작가 좌표와 실측 칸이 둘 다 있을 때만 `[작가 배치][많이 놓는 자리]` 전환, 칸 비율은 10% 이상만(D10).
5. 더 보기(접힘): 비슷한 구성 N개(보이는 보드 대비 `'−카직스 +럭스'`, 누르면 제자리에서 얼굴 줄을 펼침, D13) · 상대하기 어려운 덱(별칭 · `'▲ +0.26등'` · ›, D14).
6. 데이터 출처(ⓘ·맨 아래 줄 → 시트 `DeckSourceSheet`): 등급·네 수치의 출처·구간·판 수·기준일, 지역별 성적 막대와 중국 참고치(척도 주의), 섹션별 출처 표, 배지 뜻, 중국어 원문(D8·D15·D18). 본문 섹션에는 출처 캡션을 달지 않는다(MASTER 규칙 2).

### 6.4 도감 탭

공통: 상단 세그먼트(챔피언 / 특성 / 아이템 / 증강) + 스코프 칩(글로벌 플래+ / KR 플래+ / KR 마스터+ / 중국 플래+ / 중국 마스터+; 기본 `kr_plat`, 기억) + 표본·신선도 줄(`18.2 · 96만 판 · 3시간 전`). 표본이 `minSample` 미만인 행은 흐림 + 기본 정렬에서 뒤로. 표 폭은 폰 폭 안에 4~5열(아이콘·이름 | 등급 | 평균 등수 | TOP4 | 게임 수). 가로 스크롤 없음. 글자 12sp 이상.

- 챔피언: 코스트 칩 1~5, 특성 필터(드롭다운), 이름·초성 검색(기존 DeckSearch.keysFor 재사용은 불가 — 도감 패키지가 같은 규칙을 자체 구현), 정렬(등급/평균/픽률/이름). 행 탭 → 상세: 헤더(초상, 코스트, 특성 칩, 역할, 스킬 이름·설명·마나), 스코프별 비교 표(5행: 평균·TOP4·승률·픽률·등급), 지난 패치 Δ, 추천 아이템 5 + 3아이템 빌드 3(이름 병기), 성급별 성과, 중국 착용 아이템(cnItems), `이 챔피언 덱 N개`(decks → 덱 상세 이동).
- 특성: 등급 칩(전체/프리즘/골드/실버/브론즈), 유형(계열/직업) 토글, 단계별 보기 토글. 행: `5 사냥꾼`(style 색) · 등급 · 평균 · TOP4 · 게임 수. 상세: 단계 효과표, 챔피언 목록(코스트순), 조합 카드(combos, 있을 때만), 덱 N개.
- 아이템: 탭 `통계` / `조합표`. 통계: 종류 칩(전체/일반/상징/유물/찬란/지원), 부품 칩 10개(1~2개 선택 시 그 부품이 들어간 완성템만), 이름 검색. 행은 2줄 카드: 1줄 아이콘·이름·등급·평균·TOP4·n, 2줄 착용 상위 5명 아바타+이름(스코프별). 조합표: 부품 11×11 격자(칸 30dp 이상, 머리칸 탭 시 행·열 강조, 칸 탭 시 하단 시트: 효과·통계·착용자). 상세: 효과, 조합, 스코프별 통계, 착용자(글로벌/중국 두 줄), 완성 스테이지별 승률, `이 아이템 덱 N개`(기존 아이템 역검색으로 연결: SearchAxis.ITEM 결과).
- 증강: 탭 `티어`(에디터 S~D 격자, 출처·갱신 시각 표시) / `목록`(2줄 카드: 아이콘·이름·희귀도·태그, 에디터 티어, 통계 요약 `평균 3.21 · n=2,280 · 덱 4`). 희귀도 칩, 태그 칩(경제/아이템/전투/특성/성장), 이름·설명 검색. 상세: 설명, 에디터 티어(출처 문구), 덱별 통계(중국, 단계별 접힘), 추천 덱(작가/가이드/통계 라벨), 라운드별 확률표(rounds 비어 있으면 숨김). 상단 고지: `증강 성적은 중국 서버 골드~에메랄드 하루치(덱별 상위 5개)만, 티어는 편집자 의견`.

### 6.5 검색 탭
기존 통합 검색 유지. 후보 행 오른쪽에 `도감` 작은 버튼(축이 챔피언/특성/아이템/증강이면 도감 상세로). 증강 축은 설명문도 검색(§12 WP-3에서 DeckSearch 확장).

### 6.6 내 정보 탭(기존 설정 화면 확장)
1. 전적 카드: 티어 엠블럼, `다이아몬드 IV 0 LP`, `KR 6,397위 · 상위 1.0%`(server_rank), 최근 8판 칩(왼쪽 최신, 기존) 각 칩 아래 `±LP`(rating_changes), 평균 등수 큰 숫자, `112판 · TOP4 57% · 1등 14`, `현재 패치 18.2: 32판 4.1등`. 글자 12sp 이상. 갱신 버튼·시각.
2. 지난 게임 로비 카드(§8): 8명 라이엇 ID·등수·티어(경기 전 관측값 표기), 내 행 강조. "게임이 끝나면 자동으로 채워집니다".
3. 게임 연동: `TFT 실행 감지`(사용 기록 접근 권한 온보딩 + 스위치), `TFT가 켜지면 오버레이 자동 표시` 스위치, `게임이 끝나면 결과 알림` 스위치.
4. 오버레이(기존), 덱 데이터(기존 + 도감 데이터·아이콘 팩 상태 행), 원본 상태(기존), 출처(문구 갱신: metatft 통계, lol.qq 중국 통계, CommunityDragon).

---

## 7. 오버레이 사양(덱 참고 + 티어 참고만)

범위: 게임 중 필요한 것 두 가지만. 상대 정보·로비 스카우팅·실시간 예측은 넣지 않는다(Riot TFT 정책: 게임 중 상대 추적·집계 표시 금지).

| 상태 | 내용 |
|---|---|
| 접힘 칩 | 고른 덱 캐리 초상 + 등급(기존) 또는 `덱 N`. 게임 연동이 켜져 있으면 TFT 감지 시 초록 점, 판 종료 감지 시 60초 동안 `6등 −35 LP` 작은 배지. |
| 펼침 — 덱 목록 | 헤더에 티어 구간 이름(앱에서 마지막 선택한 구간, 탭하면 순환), 목록은 그 구간 등급순. 고정한 덱이 맨 위 — 꺼 둔 등급인데 고정 때문에 남은 덱은 등급 배지를 흐리게(0.45) 하고 줄 오른쪽에 핀. 행(2026-09-19 UX 개편 O4, 사용자 결정 4): 가로 = `[등급 배지][별칭 12sp 굵게][캐리 얼굴 2개 20dp]` / `설명 12sp 최대 2줄`(한 줄 44~59dp, 목록 상한 200dp ≈ 4줄 + 스크롤), 얼굴 전체 줄(24dp)은 넓게 보기에서만. 세로(앱 위) = 같은 두 줄 + 얼굴 전체 줄 24dp, 목록 상한 300dp. 등급 배지는 metatft 채움 / 중국 한정 테두리 + `중국` 글자 배지 / 편집 등급 테두리 + `편`(예전 중국 한정 점은 없앴다). 별칭의 ` · {운영}` 접미사는 떼고 보여 준다(설명 첫머리가 운영). 수치는 넣지 않는다. |
| 펼침 — 덱 요약 | `[등급 배지] 별칭`(12sp 굵게) / `{운영} · {주 특성1 n} · {주 특성2 n}`(12sp) · (넓게: 특성 칩 — 공용 `TraitChip`) · 레벨 칩과 바로 아래 캡션 `'{L}렙 {round} 도달 · 롤다운 {r}렙'`(11sp, §13.4) · 그 레벨 1순위 구성의 얼굴(캐리 40dp 맨 앞·나머지 32dp, 아래 레벨에 없던 유닛은 왼쪽 위 6dp 초록 점, 3성은 금색 별 8sp — 캐리를 금색·파랑 테두리로 가르지 않는다; 넓게: 얼굴·이름·아이템 줄) · (넓게: 재료 5개). 4수치는 넣지 않는다(앱 덱 상세에서 본다). 보드 그림은 그리지 않는다. |
| 티어 카드 | 소환사명 · 티어/LP(`1,234 LP`) · 최근 5판 칩(18dp, 간격 3dp, 왼쪽 최신) + 칩 아래 ±LP · 평균 등수 큰 숫자(20sp) · `N판 · TOP4 NN%` · 새로고침(기존). 바탕 불투명, 글자 최소 11sp. |
| 글자·바탕 | 오버레이는 앱 테마 밖에서 그려 글자 크기를 `OverlayTheme.kt` 의 `OverlayType` 한 곳에 둔다: `title` 12sp 굵게 · `body` 12sp(줄 높이 15) · `label` 11sp · `badge` 11sp 굵게 · `mark` 10sp(얼굴 위 첫 글자) · `star` 8sp(3성 별) · `display` 20sp(티어 카드 숫자). 오버레이 화면 코드에는 `.sp` 리터럴을 두지 않는다. 패널·접힌 칩·티어 카드 바탕은 불투명 100%(예전 95%, R11·V28). |

동작 규칙:
- 앱이 보이는 동안 숨김(기존). 게임 연동을 켜면 TFT 전면일 때만 보이고 홈 화면 등에서는 자동 숨김(사용자 설정 `TFT 밖에서도 표시`로 해제 가능).
- 전적 갱신은 `rating_changes`(17KB)로만 한다: 펼칠 때(3분 스로틀), TFT 전면 중 5분마다, 사용자 새로고침. `lookup_by_riotid`(63KB, source 생략)는 판 종료 감지 후 1회와 앱 화면에서만. `?source=full_profile`(211KB)은 쓰지 않는다.
- 폭 계산·드래그·위치 기억·알림은 기존 구현 유지.

---

## 8. 인게임 연동 설계와 한계

### 8.1 확인된 사실(2026-09-15)
- TFT 패키지명 `com.riotgames.league.teamfighttactics`(Play 스토어 id로 확인, 기기 없어 `pm list`는 못 함).
- 진행 중 게임 데이터는 키 없는 세 경로(lolchess 관전 목록 15개 플랫폼, metatft 리더보드 live 필드 246행, metatft spectate KR/EUW 상위 65명 105회) 모두 0건. Riot 상태 API에 장애 없음. Set 18 라이브에서 TFT 관전 데이터가 꺼져 있는 것으로 추정(미확인). Riot 공식 API는 키 없이 401이고 배포 앱에 키를 넣을 수 없다.
- metatft `rating_changes`는 약 15분 격자로 LP를 기록한다(109건 분석). 매치 JSON은 로비 종료 후 생성된다.
- Riot 정책: 게임 중 상대 보드 추적·개인/로비 집계·모스트 표시 금지. 로비 참가자 정보는 게임 후 표시가 안전.

### 8.2 설계

```
[기기]  UsageStatsManager.queryEvents(now-10s, now)  (PACKAGE_USAGE_STATS, 사용자가 설정에서 허용)
   ├─ TFT ACTIVITY_RESUMED → GameState.Foreground(since)   → 오버레이 자동 표시(설정 시)
   ├─ TFT ACTIVITY_PAUSED/STOPPED → GameState.Background     → 판 종료 감시 예약
   └─ (디버그) className 로그 → 로비/매치 액티비티가 다른지 실기기에서 확인 후 결정
[판 종료 감시]  전면 이탈 후, 또는 전면 25분 경과 후부터
   ├─ rating_changes?queue=1100 (17KB) 3~5분 간격, 최대 30분
   ├─ num_games 증가 → 판 종료 판정, ±LP = rating_numeric 차이 → 칩 배지 + 알림(설정 시)
   ├─ lookup_by_riotid (source 생략, 63KB) 3~5분 간격 → 새 riot_match_id
   └─ match_data_url (25KB, immutable) 1회 → 지난 게임 로비 카드(8명 riotId·등수·participant_info.ranked)
[진행 중 로비]  ActiveGameSource 인터페이스 + NoopSource. 원격 플래그 liveSpectateAvailable(수집기가 metatft tft-spectate/top_players?region=kr length>0 를 매일 확인해 data/flags.json 에 기록) 이 true 이고 TFT 전면일 때만 60초 간격 조회. 구현체는 MetatftSpectateSource 스텁(게임 중 응답 스키마 미관측 → 필드 확인 전까지 비활성). 정책상 게임 중에는 티어만 표시.
```

- 매니페스트: `PACKAGE_USAGE_STATS`(tools:ignore=ProtectedPermissions), `<queries><package android:name="com.riotgames.league.teamfighttactics"/></queries>`. `QUERY_ALL_PACKAGES`·접근성 서비스·MediaProjection은 쓰지 않는다.
- 배터리: 감시는 OverlayService(specialUse FGS)가 살아 있을 때만, 3초 간격 queryEvents(시스템이 이미 기록한 10초 구간만 읽음). 잠금 상태에서는 queryEvents가 null이므로 그때는 건너뛴다.
- 식별자: 라이엇 ID를 기준 키로. puuid는 서비스별로 따로 캐시(metatft와 lolchess 값이 다름).
- 개인정보: 지난 게임 로비의 타인 정보는 화면 표시용 메모리·최근 1경기 파일 캐시까지만, 이름·puuid를 다른 곳에 보내지 않는다.

### 8.3 한계
- 로비/매치 구분은 TFT가 두 화면에 다른 액티비티를 쓸 때만 가능(미검증). 단일 액티비티면 "앱 전면 여부"까지만.
- 판 종료 판정 지연 최대 약 15분(LP 스냅샷 주기) + 매치 JSON 생성 지연(로비 종료 후; 측정 못 함).
- 실기기 미검증 4가지: UsageStats 이벤트 수신, 액티비티 구성, 배터리 영향, 제조사 백그라운드 제한.
- 진행 중 로비는 데이터가 열릴 때까지 비활성.

---

## 9. 이미지 로드 속도 설계(요약; 진단 결과 그대로 채택)

원인(측정): CDragon은 도쿄 edge(요청당 123~163ms), max-age 3600이라 1시간마다 304 재검증(전체 다운로드와 같은 시간), OkHttp 호스트당 5요청 제한, 선로딩 없음, 크기별 재디코드.

해법:
1. 수집기가 아이콘을 WebP 팩(§5.8, 약 0.35~0.55MB)으로 미러링해 `data/icons/icons.zip` + `manifest.json`으로 배포하고, 같은 팩을 `assets/icons.zip`에 동봉한다(첫 실행부터 네트워크 없음).
2. 앱은 `manifest.hash`가 바뀔 때만 zip 1회 다운로드 → `filesDir/icons/`에 풀고 `path→file` 맵을 메모리에 둔다.
3. Coil `Interceptor`가 요청 모델(문자열 URL)을 팩의 로컬 파일로 바꾼다(호출 지점 변경 없음). 팩에 없으면 원격 그대로.
4. 커스텀 `ImageLoader`(`TftApp: ImageLoaderFactory`): `respectCacheHeaders(false)`, 디스크 캐시 64MB, `maxRequestsPerHost=16`, `crossfade(false)`, 메모리 캐시 25%.
5. Interceptor가 종류별 고정 크기(챔피언 128 / 아이템·증강 64 / 특성 32)로 `withSize`를 적용해 같은 URL의 여러 dp 요청이 한 캐시 항목을 쓰게 한다.
6. 피드 Ready 직후 팩 전체를 `enqueue`로 메모리 캐시에 올린다(로컬이면 디코드만).
7. HexCell/UnitCell에 코스트색 placeholder(§12 WP-3).

---

## 10. 6·8·9렙 배치표 버그 수정 설계(요약; 진단 결과 그대로 채택)

원인: `hero_location_l6/l8/l9`는 6·8·9레벨 보드가 아니라 초반/중반/최종 보드의 복사본(26/26 동일)이고, 사이트는 그 필드를 그리지 않는다. 레벨 보드에는 `is_carry_hero`가 없고 `numStar`가 전부 1(3성은 `level_3_heros`로 다시 계산해야 함), pet(`hero_id` 빈 문자열) 90칸이 모든 보드에서 빠지며, 탭 전환 시 placeholder가 없어 칸이 빈다.

수정:
- 수집기: `stages`(§5.2) — early/mid/final + 실제 레벨·라운드, `level_3_heros` 기반 별, 최종 캐리 상속, pet 포함(chess_id, kind, 이름·아이콘은 lol.qq chess.js 16.18), l6/l8/l9 미사용. verify.py에 단계 보드·레벨 존재 검사.
- 앱: 탭 라벨 `초반 Lv5·2-3 / 중반 Lv8·4-3 / 최종 Lv8`, `9렙` 제거, pet 칸 스타일, placeholder, `catalogChampion`을 Map 조회로, 단계 아이콘 선로딩.

---

## 11. 통합 순서와 공용 파일 소유

- 저장소 루트 기준 상대 경로로 소유를 정한다(워크트리마다 절대 경로가 다르다). 아래 표의 파일은 정확히 한 패키지만 고친다. 다른 패키지는 읽기만 하고, 연결이 필요하면 spec의 "연결 지점"에 적는다.
- 병렬 실행 후 통합 순서: **WP-1, WP-2, WP-5(데이터 쪽) → WP-3, WP-4, WP-6(앱 쪽) → 메인 루프 배선(§11.2)**. dependsOn은 전부 비어 있다(각 패키지는 계약 예시 JSON과 v1 호환 필드만으로 컴파일·테스트가 된다).

### 11.1 공용 파일 소유표

| 파일 | 소유 | 다른 패키지의 연결 지점 |
|---|---|---|
| `collector/fetch_decks.py`, `collector/verify.py`, `.github/workflows/daily.yml` | WP-1 | WP-2: 워크플로에 `fetch_stats.py`/`verify_stats.py` 단계. WP-5: `pip install pillow` + `build_icons.py` 단계 |
| `data/decks.json`, `data/version.json`, `android/app/src/main/assets/decks.json` | WP-1 | WP-2·WP-5는 읽기만 |
| `android/.../MainActivity.kt` | WP-3 | WP-4: 라우트 `codex`, `codex/champion/{id}`, `codex/trait/{id}`, `codex/item/{id}`, `codex/augment/{id}`와 composable 이름(§12 WP-4) |
| `android/.../ui/AppViewModel.kt` | WP-3 | WP-6: 기존 프로필 API(`savedRiotId, savedRegion, profileState, profileBusy, saveProfile, refreshProfile, clearProfile, feedState, syncing, refresh, pinnedDeckId, deck()`)를 그대로 유지 |
| `android/.../sync/DailySyncWorker.kt` | WP-3 | WP-4: `StatsRepository.get(ctx).sync()`, WP-5: `IconPack.get(ctx).sync()` 호출 두 줄 |
| `android/.../TftApp.kt` | WP-5 | WP-4: `StatsRepository.get(this).load()` 호출 한 줄 |
| `android/.../AndroidManifest.xml`, `res/values/strings.xml` | WP-6 | 없음 |
| `android/app/build.gradle.kts`, `proguard-rules.pro` | WP-3 | WP-4/WP-5/WP-6: 새 의존성 없음이 원칙. 필요하면 spec 결과에 적는다 |
| `android/.../overlay/OverlayContent.kt` | WP-3 | WP-6: `CollapsedChip` 옆에 `GameStatusBadge(GameSession.state)` 한 줄 |
| `android/.../ui/screens/SettingsScreen.kt` | WP-6 | WP-3·WP-4: 데이터 상태 행 추가는 통합 단계 |

### 11.2 메인 루프 배선(통합 단계 체크리스트)
1. `MainActivity`: `CodexPlaceholder` 호출을 WP-4 composable로 교체(5개 라우트).
2. `DailySyncWorker.doWork()`: 덱 동기화 뒤 `StatsRepository.sync()`, `IconPack.sync()`.
3. `TftApp.onCreate()`: `StatsRepository.get(this).load()` 추가(WP-5가 TftApp을 소유하므로 통합 때 한 줄).
4. `OverlayContent.CollapsedChip`: `GameStatusBadge` 삽입.
5. `SettingsScreen` 데이터 그룹: 도감 데이터·아이콘 팩 상태 행(`StatsRepository.state`, `IconPack.state`).
6. WP-1이 만든 실제 `decks.json`으로 WP-3 단위 테스트 재실행, WP-2 실제 파일로 WP-4 테스트 재실행.
7. `versionCode` 증가, 릴리스 빌드.

---

## 12. 작업 패키지

공통 규칙은 `C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/impl-common-rules.md`를 따른다(워크트리, 빌드 명령, 파일 수정 방법, Windows 함정, 커밋 규칙). 이 설계 문서 전체는 `.../scratchpad/design/benchmark-design.md`에 있다. 앱 빌드: `cd android && "C:/Android/gradle/gradle-8.11.1/<플레이어>/gradle.bat" :app:compileDebugKotlin --no-daemon --console=plain`, 테스트는 `:app:testDebugUnitTest`.

### WP-1 `collector-decks-v2` — 수집기 덱 파이프라인 v2(통합 덱 목록 + 배치표 데이터 수정)

**소유 파일**: `collector/fetch_decks.py`, `collector/verify.py`, `collector/qq_static.py`(신규), `collector/qq_proxy.py`(신규), `collector/metatft_comps.py`(신규), `collector/deck_merge.py`(신규), `collector/overrides_ko.json`(신규), `.github/workflows/daily.yml`, `data/decks.json`, `data/version.json`, `android/app/src/main/assets/decks.json`.

**목표**: §4 알고리즘대로 lol.qq 편집 덱 + 胜率阵容 5구간 + 数据检索器 + metatft를 합쳐 §5.1·§5.2 계약의 `decks.json` v2와 `version.json`(schemaVersion 2)을 만든다. 표준 라이브러리만 사용(기존 방침).

**원천과 호출**
1. 기존: `tft-mode-registry.js`(CurrentSet), `lineup_detail_total.json`, CDragon `ko_kr.json`/`en_us.json`, `tftchampions-teamplanner.json`, metatft `latest_cluster_info`/`latest_cluster_id`. 기존 `fetch()`(UA, gzip, 재시도)를 재사용한다.
2. `qq_static.py`: `https://game.gtimg.cn/images/lol/tfth5lib/v1/versionconfig.json`을 읽어 `urlChessData/urlRaceData/urlJobData/urlEquipData/urlBuffData`를 얻고, 경로의 `16.17-2026.S18` 부분을 CDragon 패치(ko_kr.json 상단 `version` 또는 metatft `tft-stat-api/patch`가 아니라 lol.qq `tft_recent_versions` 최신 빌드의 앞 두 자리 `16.18`)로 바꿔 먼저 시도하고 404면 원래 경로. 맵: `chess_by_id[chessId] = {da: hero_EN_name, name_cn, price, image: originalImage, chess_type}`; `trait_by_id[raceId|jobId] = characterid`; `augment_by_hexid[hexId] = augments`; `equip_by_id[equipId] = englishName(첫 값)`. 응답 `version`을 `version.sources.lolqqStatic`에 `ok`로, 버전 문자열은 로그로.
3. `qq_proxy.py`: `POST https://mlol.qt.qq.com/go/exploit/proxy`, 헤더 `Content-Type: application/json`, `User-Agent`(기존 UA). `proxy(alias, params, version_id="v1", extra={"is_return_source":0})`. 응답 `result != 0`이거나 `data`가 비면 `SourceError`. 호출 사이 0.3초 sleep.
   - 목록: `tft_lineup_group_list`, version_id `dgroup_v3`, `req_params {"queue_id":"1100","tier_part":P,"time_type":"d_grouping_v3"}`, P ∈ {"255","0","1","2","3"} → 구간 all/master/diamond/goldem/low. `data.dtstatdate`를 `buckets[b].listDate`로.
   - 상세(노출 덱만, 총 ≤ 100회): `tft_lineup_all_detail`, `tft_lineup_position`, `tft_lineup_key_chess`(각 version_id `v1`), `req_params {"champion_content_id": lineup(숫자 콤마), "main_traits_id": "id,개수;…"(main_trait_list), "mc_champion_id": main_c_chess, "minor_traits_id": sub_trait_list 같은 형식 또는 "-1,-1", "queue_id":"1100", "tier_part":P, "time_type":"d_grouping_v3"}`. 노출 덱 = 구간별 그룹의 대표 변형을 등급순으로 goldem 40 · all 25 · diamond 15 · master 10 · low 10(중복 제거). `dtstatdate`를 `buckets[b].detailDate`로.
   - 数据检索器: 공통 `base = {"queueId":"[\"1100\"]","version":"","stime":S,"etime":E,"tier":T,"level":"","threeStarCount":"","artifactCount":"","emblemCount":"","radiantCount":"","filterOptions":""}`, `E = 실행 시각의 CST 날짜 − 1일`, `S = E − 2일`, `T ∈ {"4+","7+"}`. `tft_recent_versions {"env":"0"}`로 최신 빌드(`version.qqBuild`)와 16.18 첫 빌드 시작(`start_time` 최소값 → `qqPatchStart`)을 기록. `tft_match_overview`(base) → `scopes.cn_*`용 `games`·`avg_rank`. `tft_lineup_rank`(base + `minSampleSize:"10"`, `limit:"100"`) 두 티어.
4. `metatft_comps.py`: `comps_stats?queue=1100&patch=current&days=3&rank=<집합>&permit_filter_adjustment=false[&server=KR]` 세 스코프(glob_plat, kr_plat, kr_master). 첫 행 `{cluster:"", places:[총보드]}`, 나머지 `places[0..7]` + `count`. `comps_data`(파라미터 없이 1회) → `levelling, difficulty, overall`. `comp_details?comp=<Cluster>&cluster_id=<cluster_id>`는 매칭된 그룹 상위 30개만 → `final_levels`(share=count/Σ), `counters`(place_change 상위 3, against→우리 덱 id 매핑). 헤더 `Referer: https://www.metatft.com/`(기존 fetch가 붙임).

**병합·계산**(`deck_merge.py`): §4.3 절차와 §4.4 산식(K=200, GRADE_CUTS S 3.90/A 4.15/B 4.40/C 4.70, minSample 300)을 그대로 구현한다. 유닛 집합 정규화(럭스 → `DA_Lux18_Base`, pet·소환물 제외, `TFT18_*` 제외). 그룹 id는 `"g-" + sha1(key)[:10]`, key = `"|".join(sorted("DA_trait.count")) + "|" + carryDA`; 변형 id는 `"v-" + sha1(",".join(sorted(units)))[:10]`; 편집 독립 덱 id는 기존처럼 lol.qq id(`"14266"`). 한글 덱 이름은 기존 `korean_deck_name`(캐리 · N 특성 …)을 그룹에도 적용(주특성 우선).

**편집 덱 파싱 변경(배치표 버그)**: `build_deck`에서 `hero_location_l6/l8/l9`·`early/mid/boards`를 없애고 `editorial.stages`(early = `y21_early_heros` + `needLevel_early` + `early_round`, mid = `y21_metaphase_heros` + `needLevel_middle` + `metaphase_round`, final = `hero_location` + `needLevel`)를 만든다. 별: `level_3_heros`(콤마 분리, hero_id 또는 chess_id) 포함이면 3, 아니면 `numStar`. 캐리: final의 `is_carry_hero`; early/mid는 final 캐리 id와 같을 때만 true. pet: `hero_id`가 비면 `chess_id`로 `qq_static.chess_by_id`에서 `da`(hero_EN_name이 비면 `"QQ_" + chessId`)·이름(`displayName`, CDragon에 없으면 `overrides_ko.json`→중국어 원문)·아이콘(`originalImage` 절대 URL)을 채우고 `kind:"pet"`, `catalog.pets`에 기록. pet은 `deck_code`, 유사도, 인덱스에서 제외(`omitted`에 이름). `hero_location_lN`이 세 보드와 다른 덱이 있으면(현재 0/26) 경고만 남긴다.

**출력**: §5.2 구조 그대로. v1 필드(`id,name,nameCn,tier,tierOrder,patch,finalLevel,carryId,traits,units,itemOrder,augments,notesCn,author,updatedAt,teamCode,metatft`)와 `index` 5축·`catalog` 4배열은 이름·의미를 유지한다(현재 앱이 v2 파일을 읽어도 죽지 않아야 한다). `write_output`은 `data/decks.json`, `data/version.json`을 쓰고, `assets/decks.json`은 `--snapshot` 옵션으로 복사.

**verify.py 확장**: §4.6 전부 + 기존 검사 + `stages` 검사(편집 덱마다 early/mid/final 존재, level 4~11, row 1~4·col 1~7, pet 칸이 유닛 수에 포함) + 등급 분포(S ≤ 30%, grade null 비율 로그) + `positions` 방향 검사 + 파일 크기 상한 2MB.

**daily.yml**: 단계 순서 `fetch_decks → verify → fetch_stats(continue-on-error) → verify_stats(continue-on-error, 실패 시 git checkout -- data/stats) → pip install pillow + build_icons(continue-on-error) → data/ 전체 커밋 → 어느 단계든 실패면 이슈`. 커밋 메시지에 덱 수·도감 파일 갱신 여부.

**검증 방법**: `python collector/fetch_decks.py --snapshot` 실제 네트워크 실행 → 콘솔에 구간별 그룹/변형 수, 매칭률(편집 덱 26 중 그룹 첨부 수, R 매칭 수, metatft 매칭 수), 상세 호출 수, 파일 크기 출력. `python collector/verify.py` 통과. 기존 앱(v1 코드)으로 `:app:testDebugUnitTest`가 여전히 통과해야 한다(v1 호환 확인; `레벨별 배치의 챔피언은 catalog…` 테스트는 `boards/early/mid`가 빈 배열이 되어 통과한다).

### WP-2 `collector-stats` — 도감 통계 파이프라인(챔피언·특성·아이템·증강)

**소유 파일**: `collector/fetch_stats.py`(신규), `collector/stats/__init__.py`, `collector/stats/net.py`, `collector/stats/metatft.py`, `collector/stats/lolqq.py`, `collector/stats/cdragon.py`, `collector/stats/build.py`, `collector/stats/constants.json`(신규), `collector/verify_stats.py`(신규), `data/stats/version.json`, `data/stats/champions.json`, `data/stats/traits.json`, `data/stats/items.json`, `data/stats/augments.json`, `data/stats/prev_snapshot.json`, `data/flags.json`(신규), `android/app/src/main/assets/stats/*.json`(다섯 파일 스냅샷, 신규).

**목표**: §5.3~§5.7 계약의 다섯 파일을 만든다. WP-1과 코드를 공유하지 않는다(자체 `net.py`에 UA·gzip·재시도·`proxy()`를 둔다). 표준 라이브러리만.

**원천과 호출**
- metatft(`https://api-hc.metatft.com/`, 헤더 `Referer: https://www.metatft.com/`): `F(scope)` = `queue=1100&patch=current&days=3&permit_filter_adjustment=false&rank=<집합>[&server=KR]`.
  - `tft-stat-api/units?F`, `tft-stat-api/traits?F`, `tft-stat-api/items_matches?F` × 3스코프(glob_plat, kr_plat, kr_master). 응답 `results[{unit|trait|itemName, places[8]}]`, `games[0].count`, `updated`.
  - `tft-stat-api/unit_detail_items?F(glob_plat)&unit=<DA>&artifact_count=0` 유닛마다(약 70회) → `items[{itemName:"DA_X-N", places}]`, `builds[{buildNames:"A|B|C", places, total}]`.
  - `tft-stat-api/item_detail?F(glob_plat)&itemName=<DA>` 완성템·상징마다(약 70회) → `units[{unit, places}]`, `units_overall`.
  - `tft-stat-api/item_stage_detail?F(glob_plat)&item=<DA>` 완성템 55개 → `stage[{stage,count,win}]`.
  - `tft-stat-api/augments_tiers` 1회(`content.content.tierList[{label, content[{id}]}]`, `content.author.gameName`, `content.updated_at`, `tags`).
  - `tft-comps-api/comp_augment_tiers` 1회(`results[{augments[{id,tier}], source_title, distance}]`, 클러스터 키).
  - `tft-comps-api/latest_cluster_info` 1회(클러스터→그룹 매칭용 units_string; decks.json의 `global.cluster`와 대조해 그룹 id 결정).
  - `tft-stat-api/patch` 1회(`patch`, `start`) → `patchGlobal`, 이전 패치 동결 시각.
  - `https://data.metatft.com/lookups/TFTSet18_latest_ko_kr.json` 1회(세트명은 decks.json `version.metatftSet`) → `augments[{apiName,name,rarity,manual_tags}]`, `units[].ability`(스킬 수치 텍스트), `unitAssetNames`.
  - `https://api.metatft.com/tft-spectate/top_players?region=kr` 1회 → `data/flags.json {"liveSpectateAvailable": length>0, "checkedAt": …}` (조회 실패면 false 유지).
  - 호출 간 0.4초 sleep, 총 약 220회.
- lol.qq(`POST https://mlol.qt.qq.com/go/exploit/proxy`): `base`(WP-1과 같은 형식, `version:""`, `stime=E-2`, `etime=E`(CST 전날)), `tier` `"4+"`(cn_plat)·`"7+"`(cn_master), `minSampleSize:"10"`.
  - `tft_match_overview`(base) → `games=total_games`, `rank_dist` → 가중 평균은 unit_s 가중으로 따로 계산(아래).
  - `tft_hero_rank`(base + `limit:"2000"`, `unitType:""`) → `unit_id`(DA, `,N` 접미사 없는 행만), `unit_s, top1_cnt, top4_cnt, total, avg_rank`.
  - `tft_trait_rank`(base + `limit:"2000"`) → `trait_id "DA_X,n"`.
  - `tft_equip_rank`(base + `ShowHero:"1"`, `limit:"8000"`) → `item_id "DA_champ#DA_item"`(단일 `#`만; `|`가 있는 3아이템 행은 버림), `build_s, build_rate, avg_rank`. 최소 build_s가 10보다 크면 잘림 경고. `tft_equip_rank`(base, `ShowHero:""`, `limit:"2000"`) → 아이템 단독 `build_s/build_rate`.
  - `tft_trait_strength_trend`(version_id `v1`, `{"tier_part":"255","battletype":"1100"}`) → combos(숫자 traitId는 `race.js/job.js`로 변환 — WP-1의 `qq_static.py`를 import하지 말고 같은 URL을 자체 로더로 읽는다).
  - `scopes.cn_*.meanAvg` = Σ(unit_s·avg_rank)/Σunit_s (hero_rank 행). `delta = avg − meanAvg`. 범위 검사(§5.4) 실패 행은 제외하고 실패 수를 로그.
- CommunityDragon `ko_kr.json`(세트 18 champions/traits/items, `items[].composition`), `en_us.json`(nameEn).
- `data/decks.json`(WP-1 산출물, 읽기만): `index.byId.*` → 각 항목 `decks`, `decks[].augmentStats` → `augments[].deckStats`, `decks[].editorial.augments.recommended` → `recommendedBy.editorial`, `decks[].global.cluster` → 가이드 추천 매칭. 없거나 schemaVersion < 2면 해당 필드를 비우고 `sources.decksJson="missing"`.

**계산 규칙**: §5.4~§5.7의 등급 공식(챔피언 S>.3/A>.1/B>−.1/C>−.3, 특성 S>.5/A>.25/B>0/C>−.25, 아이템 `(4.5−avg+p)×pick^i`), `minSample` 1000(챔피언·아이템), 500(특성 단계). 특성 `DA_X_N` → CDragon `effects[N-1].minUnits`. `TFT18_*` 행 제거. `unit_detail_items.items`의 `-N` 접미사 제거 후 합산. 아이템 `kind` 판정: 부품 목록(§5.6 `components` 10개), `DA_18_Emblem*`→emblem, `DA_Artifact_*`→artifact, 이름에 `Radiant`/CDragon 태그로 radiant, `*Support*`→support, `composition` 두 개→completed, 나머지 other. `prev_snapshot.json`: `{patchGlobal, frozenAt, champions:{id:{scope:{avg,pick}}}, items:{…}}`; 실행 시 `tft-stat-api/patch.patch`가 스냅샷의 `patchGlobal`과 다르면 직전 실행 결과를 동결해 스냅샷으로 저장(첫 실행은 빈 객체).

**verify_stats.py**: 파일 5개 존재·파싱, 챔피언 ≥ 60·특성 ≥ 30·아이템 ≥ 120·증강 ≥ 200, 각 metatft 스코프 `sum(places)==n`, `Σn/games`가 8.0~8.8(챔피언, 스코프별), lol.qq 스코프 범위 검사 위반 0(위반 행은 이미 제외됐어야 함), DA 조인율(CDragon 이름 있는 챔피언 ≥ 90%), 등급 분포 비어 있지 않음, 한글화율 ≥ 95%.

**검증 방법**: `python collector/fetch_stats.py --snapshot` 실제 실행 → 스코프별 행 수·표본·호출 수·소요 시간 출력, `python collector/verify_stats.py` 통과, 파일 크기(각 ≤ 1.5MB) 출력.

**연결 지점**: WP-1의 `daily.yml`에 단계 추가(§11.1). `overrides_ko.json`은 WP-1 파일을 있으면 읽는다.

### WP-3 `app-decks-v2` — 앱 덱 v2(모델·저장소·검색·목록·상세·오버레이 내용·내비게이션)

**소유 파일**: `android/app/src/main/java/com/tftdeck/reader/MainActivity.kt`, `ui/AppViewModel.kt`, `ui/UiUtils.kt`, `ui/theme/Theme.kt`, `ui/components/Components.kt`, `ui/components/DeckCardV2.kt`(신규), `ui/components/StatChips.kt`(신규), `ui/screens/DeckListScreen.kt`, `ui/screens/DeckDetailScreen.kt`, `ui/screens/SearchScreen.kt`, `ui/screens/CodexPlaceholder.kt`(신규·임시), `overlay/OverlayContent.kt`, `data/Models.kt`, `data/DeckRepository.kt`, `data/DeckSearch.kt`, `data/DeckPrefs.kt`(신규), `sync/DailySyncWorker.kt`, `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`, `android/app/src/test/java/com/tftdeck/reader/DeckSearchTest.kt`, `android/app/src/test/resources/decks_v2_sample.json`(신규).

**목표**: §5.2 계약을 읽는 모델과 §6.2·§6.3·§6.5·§7(덱 부분) 화면. v1 파일도 깨지지 않고 읽혀야 한다(모든 새 필드에 기본값).

**모델(Models.kt)**: `DeckFeed`에 `buckets: Map<String, BucketMeta>`, `scopes: Map<String, ScopeMeta>`, `gradeCuts`. `Deck`에 `kind, key, editorialTier, mainTraits, sources: DeckSources, stats: Map<String, DeckStats>, global: GlobalStats?, keyUnits, positions: Map<String, List<CellStat>>, levelDist, augmentStats, itemWearers, variants, editorial: Editorial?`; `boards/early/mid` 삭제 → `Editorial.stages: List<Stage(key,label,level,round,units: List<Placement>)>`; `Placement`에 `kind: String? = null`; `Unit`에 `carryRank: Int? = null`, `kind: String? = null`; `SearchIndex`에 `byId: IdIndex`; `Catalog`에 `pets`. `FeedVersion`에 `schemaVersion, patchGlobal, qqBuild, qqPatchStart, statDate, editorialCount`. 편의 프로퍼티: `Deck.statsFor(bucket)`, `Deck.gradeFor(bucket)`(null이면 editorialTier), `Deck.carries`(carryRank 1~3 정렬), `Deck.stages`(editorial?.stages ?: listOf(final from units)).

**DeckRepository**: 기존 동작 유지(version.json 해시 비교 → 본체). `schemaVersion`이 2 미만인 원격이면 그대로 받는다(호환). `pinnedDeckId` 유지. **DeckPrefs.kt**(신규, SharedPreferences): 선택 구간(기본 `goldem`), 정렬, 고정 덱 집합, 숨긴 덱 집합, `showHidden`.

**DeckSearch**: `filter(tiers, levels, onlyChina, editorialOnly, mainTrait: String?, bucket, hidden: Set<String>, showHidden)`, 정렬 함수 `sort(list, mode, bucket)`(등급→adjAvg / 픽률 / 상승(trend up 우선, avgDiff 오름차순) / 표본). 검색 축에 증강 설명(`catalog.augments[].desc`가 있으면 haystack에 단어 단위로 추가; 계약에 `desc`는 없으므로 stats 파일이 없을 때는 이름만)과 `byId` 조회(`decksForId(axis, id)`).

**AppViewModel**: 기존 공개 API 전부 유지(§11.1). 추가: `bucket: StateFlow<String>`, `setBucket()`, `sortMode`, `mainTraitFilter`, `editorialOnly`, `pinnedSet/hiddenSet` 토글, `availableMainTraits`(목록에 있는 mainTraits 집합, 아이콘 포함), `catalogChampion/catalogItem`을 피드별 `Map<String, CatalogEntry>`로(선형 탐색 제거), `petEntry(id)`.

**MainActivity**: 탭 4개 `decks / codex / search / settings`(라벨 덱 / 도감 / 검색 / 내 정보, 아이콘 ViewList / MenuBook / Search / Person). 라우트 추가: `codex`, `codex/champion/{id}`, `codex/trait/{id}`, `codex/item/{id}`, `codex/augment/{id}` → 모두 `CodexPlaceholder(route, id)`(신규 파일의 임시 composable; 통합 때 WP-4의 `CodexScreen(viewModel, onOpenChampion, onOpenTrait, onOpenItem, onOpenAugment)`, `ChampionDetailScreen(id, viewModel, onOpenDeck, onOpenItem)`, `TraitDetailScreen(id, viewModel, onOpenDeck, onOpenChampion)`, `ItemDetailScreen(id, viewModel, onOpenDeck, onOpenChampion)`, `AugmentDetailScreen(id, viewModel, onOpenDeck)`로 교체). 상세 라우트는 하단 바 숨김·뒤로 버튼(기존 isDetail 로직을 `deck/` 및 `codex/*/{id}` 접두사로 확장). TopAppBar 제목: 도감 상세는 `도감`.

**UiUtils**: `iconUrl()`이 `http`로 시작하면 그대로 반환. `bucketLabel(key)`, `scopeLabel(key)`, `formatAvg(Double?)`("3.61"/"-"), `formatPct(Double?, digits)`, `formatCount(Int?)`("17,059"), `gradeColor(grade: String?)`(null 회색), `trendGlyph`. `tierColor`는 유지(편집 등급 SS/S/A/B/C용).

**Components.kt**: `HexCell`에 `placeholder = ColorPainter(costColor.copy(alpha=.25f))`, pet 칸 스타일(회색 테두리, 별 없음, `kind == "pet"`), `BoardSlot`에 `kind`, `heat: Float?`(히트맵 진하기), `heatLabel: String?`. `TierBadge(grade, editorial: Boolean)`. `UnitGrid`는 유지(카드 하단 얼굴 줄에 26dp 변형 추가). `ThreeStarMark`·`OnlyInChinaBadge`·`TraitChip`·`ItemIcons`·`EmptyState` 유지.

**DeckCardV2.kt**: §6.2의 카드(5줄). `StatChips.kt`: 고정 4수치 줄 `StatsRow(stats: DeckStats?)`, 표본 라벨 `SampleLabel`, 운영/난이도 칩, 출처 배지 묶음 `SourceBadges(deck)`, 트렌드 글리프.

**DeckListScreen**: FeedBanner 문구 갱신(패치·글로벌 패치·기준일), 구간 세그먼트(가로 칩, 단일 선택), 정렬 칩, 필터 칩 줄(초기화·중국 한정·편집 덱만·주 특성 칩·최종 레벨·숨긴 덱 보기), 고정 덱 우선 정렬, 카드 길게 누르기 → `DropdownMenu`(고정/해제, 숨김/복구), 변형 펼침(카드 안 `변형 N개` 텍스트 버튼 → 변형 행 목록).

**DeckDetailScreen**: §6.3 1~12 순서. 배치 탭은 `deck.stages`로(라벨 `${label} Lv${level}` + round가 있으면 `·${round}`), `9렙` 제거, `slotsFor(stage)`는 `Placement`를 `catalogChampion`/`petEntry`로 풀고 `kind` 전달. 히트맵 스위치(`positions` 비어 있으면 숨김): 켜면 유닛별 최빈 칸을 `heat`로, 편집 좌표와 다르면 작은 점. 좌표 없는 대표 보드(W만 있는 그룹)는 히트맵 최빈 칸을 좌표로 쓴다(없으면 보드 대신 얼굴 줄). 핵심 유닛 칩, 레벨 도달 줄(도움말 아이콘 → `AlertDialog` 한 줄 설명), 증강 두 그룹(단계별 평균은 `FilterChip` 세 개로 접힘·`stageLowSample` 흐림), 변형 섹션(탭하면 보드 미리보기 상태 변경), 상대 덱, 글로벌 비교 카드(8칸 막대는 `Row`+`Box` 폭 비율), 작성자 원문(기존).

**SearchScreen**: 후보 행에 `도감` 아이콘 버튼(콜백 `onOpenCodex(axis, id)` 파라미터 추가; MainActivity에서 라우트로 연결). `ResultList`는 `DeckCardV2` 사용.

**OverlayContent.kt**(§7 덱 부분만): 헤더에 구간 이름(`DeckPrefs` 구간, 탭으로 순환), 목록 정렬은 그 구간 등급순·고정 덱 우선, 덱 요약에 4수치 한 줄과 `초반/중반/최종` 얼굴 줄 전환(stages가 있을 때), `OverlayProfileCard` 호출 시그니처는 그대로(WP-6이 카드 내부를 바꾼다). `CollapsedChip` 안에 통합 때 `GameStatusBadge`를 넣을 자리를 주석으로 표시.

**DailySyncWorker**: 기존 동작 + 통합 시 `StatsRepository.sync()`·`IconPack.sync()` 호출 자리 주석.

**테스트**: `decks_v2_sample.json`(§5.2 예시를 확장한 그룹 3개·편집 독립 1개, 구간 5개, byId 포함)을 `src/test/resources`에 두고 `DeckSearchTest`를 둘로 나눈다: (a) 기존처럼 `assets/decks.json`(v1이든 v2든)을 읽는 호환 테스트(`boards` 관련 테스트는 `stages`가 비어 있어도 통과하도록 수정), (b) v2 픽스처로 `statsFor/gradeFor`, 구간 필터·정렬, 고정/숨김, `byId` 조회, 단계 보드 파싱(pet 포함), 절대 URL `iconUrl` 테스트.

**검증 방법**: `:app:compileDebugKotlin`, `:app:testDebugUnitTest` 통과. 통합 후 WP-1 실제 파일로 재실행.

### WP-4 `app-codex` — 도감(챔피언·특성·아이템·증강) 화면과 통계 저장소

**소유 파일**: `android/app/src/main/java/com/tftdeck/reader/data/StatsModels.kt`(신규), `data/StatsRepository.kt`(신규), `ui/codex/CodexScreen.kt`, `ui/codex/CodexViewModel.kt`, `ui/codex/ChampionTab.kt`, `ui/codex/ChampionDetailScreen.kt`, `ui/codex/TraitTab.kt`, `ui/codex/TraitDetailScreen.kt`, `ui/codex/ItemTab.kt`, `ui/codex/ItemGrid.kt`, `ui/codex/ItemDetailScreen.kt`, `ui/codex/AugmentTab.kt`, `ui/codex/AugmentDetailScreen.kt`, `ui/codex/CodexComponents.kt`, `ui/codex/CodexFormat.kt`, `ui/codex/CodexSearch.kt`(모두 신규), `android/app/src/test/java/com/tftdeck/reader/StatsRepositoryTest.kt`(신규), `android/app/src/test/resources/stats/*.json`(신규 픽스처: version, champions, traits, items, augments).

**목표**: §5.3~§5.7 계약을 읽는 `StatsRepository`와 §6.4 화면. 기존 파일은 일절 수정하지 않는다. 덱 연결은 v1에도 있는 `DeckRepository.get(app).state`(`FeedState.Ready.feed.index.champion/item/trait/augment`(이름 키)와 `feed.catalog`)만 쓴다(`byId`는 통합 후 선택적으로).

**StatsRepository**: 싱글턴 `get(context)`. `load()`: `filesDir/stats/<name>.json` 캐시 → 없으면 `assets/stats/<name>.json` → 없으면 `StatsState.Missing`. `sync()`: `BuildConfig.FEED_BASE_URL + "stats/version.json"`을 받아 `files.<name>` 해시가 캐시와 다른 파일만 `stats/<name>.json` 다운로드(gzip, 임시 파일 교체, 빈 파일 거부 — DeckRepository와 같은 방식이지만 코드는 복제해서 독립). `state: StateFlow<StatsState>`(`Loading / Ready(champions, traits, items, augments, version, lastSyncedAt) / Missing`). 스코프 선택은 `CodexPrefs`(SharedPreferences, 기본 `kr_plat`).

**StatsModels.kt**: §5.4~§5.7 필드 그대로(`@Serializable`, 전부 기본값, `ignoreUnknownKeys`). 편의: `ChampionRow.stat(scope)`, `grade(scope)`, `TraitRow.stat(scope, units)`, `ItemRow.wearers(scope)`.

**CodexViewModel**(`AndroidViewModel`): `state`, `scope`, `setScope`, 탭별 필터·정렬·검색어 상태, `deckIdsFor(axis, name)`(DeckRepository index 이름 키), `deckName(id)`. 검색은 `CodexSearch.kt`에 DeckSearch와 같은 규칙(부분 일치·초성·줄임말·영문)을 자체 구현(공유 불가).

**화면(§6.4)**: `CodexScreen(viewModel: CodexViewModel, onOpenChampion: (String)->Unit, onOpenTrait: (String)->Unit, onOpenItem: (String)->Unit, onOpenAugment: (String)->Unit)` — 상단 세그먼트(챔피언/특성/아이템/증강, `SingleChoiceSegmentedButtonRow`), 스코프 칩 줄, 표본·신선도 줄, 탭 내용. 상세 4개는 §11.1 시그니처. 표는 `LazyColumn` 행(가로 스크롤 없음): 아이콘 32dp+이름(weight) | 등급 배지 | 평균 | TOP4 | n. 글자 12sp 이상, 터치 높이 48dp. `minSample` 미만 행은 alpha 0.5 + 기본 정렬 뒤. 아이템 조합표 `ItemGrid.kt`: `recipes` 맵으로 11×11(머리칸 부품 아이콘, 셀 30dp 이상 `aspectRatio(1f)`, 셀 탭 → `ModalBottomSheet`). 증강 티어 격자는 `FlowRow`. 스코프에 데이터가 없는 항목은 `-`.

**테스트**: 픽스처(계약 예시 확장, 각 파일 3~5행)로 파싱, `stat(scope)` 조회, 등급/정렬/검색(초성 포함), 조합표 `recipes` 역조회.

**검증 방법**: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`. 연결 지점: MainActivity 라우트 5개(WP-3 placeholder 교체), `TftApp.onCreate`에 `StatsRepository.get(this).load()`, `DailySyncWorker`에 `sync()`. 새 의존성 없음(material3 `SegmentedButton`·`ModalBottomSheet`는 BOM 2024.12.01에 포함).

### WP-5 `icon-pack` — 아이콘 팩 미러링과 로컬 우선 이미지 로더

**소유 파일**: `collector/build_icons.py`(신규), `data/icons/manifest.json`, `data/icons/icons.zip`(신규), `android/app/src/main/java/com/tftdeck/reader/TftApp.kt`, `data/IconPack.kt`(신규), `data/IconInterceptor.kt`(신규), `android/app/src/main/assets/icons.zip`, `android/app/src/main/assets/icons_manifest.json`(신규).

**수집기(`build_icons.py`, Pillow 필요)**: `data/decks.json`과 `data/stats/*.json`(있는 것만)을 재귀 순회해 문자열 값 중 `icon` 키(및 `iconUrl`)를 모아 원본 URL을 만든다(상대 경로면 `version.assetBase` + 경로, `http`면 그대로). 각 원본을 받아(재시도 3회, 실패는 건너뛰고 로그) 종류별 크기(경로에 `/characters/`→128, `/traiticons/`→32, 그 외 64)로 축소해 WebP q80(`method=6`)으로 `sha1(원본 경로 문자열)[:12].webp`에 저장. 이미 같은 이름의 파일이 있고 원본 `ETag`/`Last-Modified`가 캐시(`collector/.icon_cache.json`, 소유)와 같으면 다시 받지 않는다. `manifest.json`(§5.8)과 `icons.zip`(`ZIP_DEFLATED`, 항목 날짜 1980-01-01, 이름 정렬)을 쓴다. 팩이 바뀌지 않으면 파일을 다시 쓰지 않는다(git diff 없음). `--snapshot`이면 `assets/icons.zip`·`assets/icons_manifest.json`도 복사. 콘솔에 개수·바이트·실패 목록 출력.

**앱(`IconPack.kt`)**: 싱글턴. `load()`: `filesDir/icons/manifest.json` → 없으면 `assets/icons_manifest.json` + `assets/icons.zip`을 `filesDir/icons/`에 풀기. 메모리 맵 `path → File`. `sync()`: `FEED_BASE_URL + "icons/manifest.json"`(수백 바이트) 받아 `hash`가 다르면 `icons/icons.zip` 1회 다운로드 → 임시 폴더에 풀고 교체 → 맵 갱신. `state: StateFlow<IconPackState(hash, count, lastSyncedAt)>`. `resolve(url: String): File?`(상대 경로·절대 URL 모두 manifest 키로 조회; `assetBase` 접두사는 떼고 비교). `warmUp(imageLoader)`: `DeckRepository.state`를 관찰해 Ready가 되면 catalog 아이콘 전부를 `ImageRequest`로 `enqueue`(로컬이면 디코드만).

**`IconInterceptor.kt`**: `coil.intercept.Interceptor`. `chain.request.data`가 String이고 `IconPack.resolve()`가 파일을 주면 `request.newBuilder().data(file)`로 바꾼다. 종류별 고정 크기(`/characters/`→128, `/traiticons/`→32, 그 외 64)를 `chain.withSize(Size(w,h))`로 적용. 팩에 없으면 원격 그대로(크기만 적용).

**`TftApp.kt`**: `ImageLoaderFactory` 구현 — `ImageLoader.Builder(this).memoryCache{ MemoryCache.Builder(this).maxSizePercent(0.25).build() }.diskCache{ DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(64L*1024*1024).build() }.respectCacheHeaders(false).okHttpClient{ OkHttpClient.Builder().dispatcher(Dispatcher().apply{ maxRequestsPerHost = 16 }).build() }.components{ add(IconInterceptor(IconPack.get(this@TftApp))) }.crossfade(false).build()`. `onCreate`에서 `IconPack.get(this).load()`를 IO에서 실행한 뒤 `warmUp`. 기존 `DeckRepository.load()`·`DailySyncWorker.schedule()` 유지. okhttp는 coil-base가 `api`로 노출하므로 의존성 추가 없음(컴파일 실패 시 `libs.versions.toml`에 okhttp 4.12.0 추가가 필요하다고 결과에 적는다 — build.gradle은 WP-3 소유).

**검증 방법**: `pip install pillow` 후 `python collector/build_icons.py --snapshot` 실제 실행(현재 v1 decks.json 기준 140개 → 약 0.35MB), 두 번째 실행에서 파일이 다시 쓰이지 않는지 확인, `:app:compileDebugKotlin` 통과. 연결 지점: `daily.yml` 단계(WP-1), `DailySyncWorker`에 `IconPack.get(ctx).sync()`(WP-3), 설정 화면 상태 행(WP-6).

### WP-6 `ingame-link` — TFT 앱 감지, 게임 종료 감지, 지난 게임 로비, 오버레이 서비스·티어 카드, 내 정보 화면

**소유 파일**: `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/values/strings.xml`, `android/app/src/main/java/com/tftdeck/reader/ingame/GameDetector.kt`, `ingame/GameSession.kt`, `ingame/LobbyRepository.kt`, `ingame/ActiveGameSource.kt`, `ingame/IngamePrefs.kt`(모두 신규), `ui/ingame/IngameViewModel.kt`, `ui/ingame/GameLinkSection.kt`, `ui/ingame/LastLobbyCard.kt`, `ui/ingame/ProfileSummaryV2.kt`(신규), `overlay/GameBadge.kt`(신규), `data/ProfileRepository.kt`, `overlay/OverlayService.kt`, `overlay/ProfileCard.kt`, `ui/screens/SettingsScreen.kt`.

**목표**: §8 설계와 §6.6·§7(티어 카드) 화면. Riot 정책상 게임 중 상대 정보는 표시하지 않는다.

**매니페스트**: `<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>`, `<queries><package android:name="com.riotgames.league.teamfighttactics"/></queries>`. 그 외 권한 추가 없음.

**GameDetector.kt**: `UsageStatsManager.queryEvents(now-10_000, now)`를 3초마다(코루틴, 서비스 스코프) 돌려 패키지 `com.riotgames.league.teamfighttactics`의 `ACTIVITY_RESUMED`(API 29+; 26~28은 `MOVE_TO_FOREGROUND`)/`ACTIVITY_PAUSED`/`ACTIVITY_STOPPED`로 `StateFlow<GameState>`(`Unknown / Foreground(since, className) / Background(since)`)를 갱신. `queryEvents`가 null(잠금)이면 상태 유지. 권한 확인 `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS)`(API 29+; 아래는 `checkOpNoThrow`), 설정 이동 `Settings.ACTION_USAGE_ACCESS_SETTINGS`. 디버그 빌드에서 className을 `Log.d`.

**GameSession.kt**: 전면 이탈 또는 전면 25분 경과 시 감시 시작 → `ProfileRepository.fetchRatingChanges()`(아래) 3분 간격 최대 30분; `num_games` 증가 시 `SessionEvent.GameEnded(placement?, lpDelta)` 발행(칩 배지 60초, 설정이 켜져 있으면 알림 채널 `game_result`로 알림) → `LobbyRepository.fetchLatest()` 3분 간격 최대 30분(새 `riot_match_id`가 보이면 매치 JSON 1회). 전역 `StateFlow<GameStatus>`(`Idle / TftForeground / Watching / Result(text)`).

**LobbyRepository.kt**: `GET https://api.metatft.com/public/profile/lookup_by_riotid/{REGION}/{name}/{tag}`(source 생략, 63KB) → `matches[0].riot_match_id, match_data_url`; `GET {match_data_url}`(25KB) → `info.participants[8]{riotIdGameName, riotIdTagline, placement, level, last_round, units}`, `_metatft.participant_info[]{riot_id, ranked.rating_text}`. `LastLobby(matchId, endedAt, me, players[8]{riotId, placement, tierText(한글), level, isMe})`. 파일 캐시는 최근 1경기(`filesDir/last_lobby.json`), 타인 puuid는 저장하지 않는다.

**ActiveGameSource.kt**: `interface ActiveGameSource { suspend fun current(puuid: String): ActiveLobby? }`, `NoopSource`, `MetatftSpectateSource`(`GET https://api.metatft.com/tft-spectate/summoner_by_puuid/kr/{puuid}` → `{}`이면 null; 필드는 미관측이라 `gameMode=="TFT"`와 `participants[].puuid`만 파싱, 나머지는 무시). `FEED_BASE_URL + "flags.json"`의 `liveSpectateAvailable`을 하루 1회 읽어(`IngamePrefs`) true이고 TFT 전면일 때만 60초 간격 호출. 결과가 있어도 이번 버전에서는 화면에 티어 외 아무것도 그리지 않는다(정책). 기본은 Noop.

**ProfileRepository.kt**: `refresh()`의 URL에서 `?source=full_profile`을 제거(63KB로 충분: `ranked`, `matches` 40판, `server_rank`). `PlayerProfile`에 `serverRank: Int?`, `serverTotal: Int?`, `percentile: Double?`, `recentLpChanges: List<Int?>`(최근 8판, `rating_changes` 이웃 차이를 `match_timestamp`와 `created_timestamp` 최근접 짝짓기; ±15분 밖이면 null), `currentPatch: String?`, `currentPatchGames/currentPatchAvg`(`matches[].patch`가 `ranked` 첫 행 patch와 같은 판) 추가. 새 함수 `fetchRatingChanges(): RatingSnapshot?`(`GET .../rating_changes/{REGION}/{name}/{tag}?queue=1100` → `rating_changes[]`; 3분 스로틀; `numGames`, 최신 `rating_numeric`). 오버레이·세션은 이 함수를 쓰고 `refresh(force=false)`의 기본 최소 간격은 유지.

**OverlayService.kt**: `ACTION_WATCH`(게임 연동 켜짐 시 설정에서 시작) — 창은 붙이지 않고 `GameDetector`만 돌리다가 TFT 전면이면 `attachOverlay()`, 이탈이면 `View.GONE`+`FLAG_NOT_TOUCHABLE`(기존 appVisible 로직 재사용, `IngamePrefs.showOutsideTft`면 계속 표시). 기존 `start/stop/EXTRA_DECK_ID` 동작 유지. `GameSession`을 서비스 스코프에서 시작. 알림 텍스트에 `TFT 감지 중` 표시.

**GameBadge.kt**: `@Composable fun GameStatusBadge(statusFlow: StateFlow<GameStatus>)` — 초록 점 / `6등 −35 LP` 60초 배지. (통합 때 `OverlayContent.CollapsedChip`에 삽입.)

**ProfileCard.kt(오버레이 티어 카드)**: 글자 최소 10sp, 최근 6판 칩 아래 `±LP`(recentLpChanges), `KR 상위 1.0%` 한 줄, 새로고침은 `fetchRatingChanges()` 우선.

**SettingsScreen.kt(내 정보 탭)**: §6.6 순서. `ProfileSummaryV2`(전적 카드), `LastLobbyCard`(지난 게임 로비; 없으면 안내), `GameLinkSection`(권한 온보딩 버튼·스위치 3개: `TFT 실행 감지`, `TFT가 켜지면 오버레이 자동 표시`, `게임이 끝나면 결과 알림`; `IngameViewModel`이 `IngamePrefs`와 `OverlayService.startWatch/stop`을 다룬다), 기존 오버레이·덱 데이터·원본·출처 그룹 유지(출처 문구를 "덱 통계는 lol.qq.com/tft, 챔피언·아이템 통계와 전적은 metatft.com, 한국어 이름과 아이콘은 Community Dragon"으로). 데이터 그룹에 통합 때 도감·아이콘 팩 상태 행이 들어갈 자리 주석. `AppViewModel`은 기존 API만 사용.

**strings.xml**: 알림 채널 `game_result` 이름·설명, `TFT 감지 중` 문구.

**검증 방법**: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`(`LobbyRepository` 파서를 `src/test/resources/ingame/match_sample.json`(소유, 식별자 가린 샘플)으로 테스트, `rating_changes` 짝짓기 단위 테스트). 실기기 검증 항목(가능하면): 사용 기록 권한 허용 후 TFT 실행 → 로그에 RESUMED, 홈으로 → PAUSED; 로비/매치 className 차이 기록. 기기가 없으면 "미검증"으로 보고.

---

## 13. 제목 후보

| 후보 | 이유 |
|---|---|
| **덱마루** | '마루'(꼭대기·으뜸)+덱. 두 글자 조합이라 짧고 발음이 쉬우며 스토어 검색 충돌이 적다. 특정 사이트·상표(롤토체스, 롤체, TFT)를 쓰지 않아 안전하다. |
| **중원덱** | 中原(중국 본토)에서 온 덱. "중국 통계를 한국어로"라는 앱의 정체성을 이름에 담는다. 무협 어감이 호불호가 갈릴 수 있다. |
| **한눈덱** | 게임 위에 띄워 한눈에 본다는 오버레이 기능과, 아이템 하나로 덱을 역검색하는 사용 흐름을 함께 가리킨다. |
| **옥덱(玉덱)** | 앱 테마색(옥색)과 중국의 이미지를 같이 담는다. 두 글자라 아이콘·위젯에 맞다. 발음이 낯설 수 있다. |
| **덱나침반** | 어느 덱으로 갈지 방향을 잡아 준다는 뜻. 검색·역검색·티어 참고 기능을 설명한다. 다섯 글자로 조금 길다. |
| **협곡 덱 리더(현행)** | 이미 쓰는 이름. '협곡'은 소환사의 협곡(LoL) 연상이라 TFT와 거리가 있고 세 어절이라 긴 편이다. |

주의: '롤토체스'는 Riot 공식 한국어 명칭, '롤체'는 lolchess(롤체지지)와 혼동되므로 피한다.

---

## 14. 리스크와 미확인 사항

- **GitHub Actions 러너(미국 IP)에서의 접근**: `daily.yml`은 등록만 되고 실행 이력이 0회다. `mlol.qt.qq.com`·`api-hc.metatft.com` 접근 가능 여부를 첫 cron 로그로 확인한다. 차단 시 self-hosted 러너 또는 국내 프록시.
- **비공개 API 변동**: 세 소스 모두 문서가 없다(lol.qq `tft_augment_rank` 화이트리스트 폐쇄 선례, metatft `unit_detail2` 개정 흔적). verify 게이트와 이슈 등록으로 조용한 실패를 막는다.
- **호출량**: 설계상 하루 lol.qq 약 120회(목록 5 + 상세 ≤ 100 + 数据检索器 12), metatft 약 250회(상세 유닛·아이템 포함). 레이트리밋은 관찰되지 않았으나 유지 한도는 추정. 실패 시 상세를 절반으로 줄이는 옵션(`--detail-limit`)을 둔다.
- **胜率阵容 해석**: 집계 기간 미문서화, 목록(당일)·상세(T-1) 날짜 차이, use_rate 분모 불명. 앱은 기준일만 표시하고 use_rate는 "픽률"로만 쓴다.
- **등급 컷**: `GRADE_CUTS`·`shrinkK`는 초기값이며 첫 배포 후 분포를 보고 조정한다(수집기 상수 한 곳).
- **히트맵 축 해석(x=col, y=row)**: 4.6의 방향 검사를 통과할 때만 싣는다.
- **pet 소환물 이름·아이콘**: lol.qq chess.js(중국어 이름)에 의존. 한글 이름은 `overrides_ko.json` 수기 관리.
- **인게임**: §8.3(실기기 미검증 4가지, 진행 중 로비 데이터 0건, 판 종료 지연 최대 약 15분+α).
- **아이콘 팩**: WebP 변환에 Pillow가 필요해 수집기 "의존성 없음" 방침의 예외가 된다(워크플로에서 설치). jsDelivr 등 파일별 CDN은 저트래픽 앱에서 대부분 콜드라 zip 한 파일 방식이 낫다(측정).
- **lolchess**: 어떤 자동 수집도 하지 않는다. 향후 운영사 허락을 받으면 아이템 동반 통계와 KR 리더보드를 추가 후보로 검토.

---

## 13. 추가 요구(설계 이후 사용자 요청): 레벨별 빌드업

> 사용자: "metatft 의 5 6 7 8 9 10렙 좋던데 배치는 필요없고 빌드업 어떻게 하는지 자료" (2026-09-15)
>
> 이 절은 앞 절(§5 데이터 계약, §6.3 덱 상세, §7 오버레이)과 충돌하면 **이 절이 우선**한다.

### 13.1 원천 조사 결과 (2026-09-15 실측)

| 원천 | 내용 | 범위 | 성격 |
|---|---|---|---|
| metatft `comp_details?comp={cluster}&cluster_id={set}&queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustment=true` | `early_options{"4".."7": [{unit_list "&"구분, count, level(평균 레벨 소수), avg, win}]}` | 4~7렙, 레벨당 최대 10조합 | 통계 |
| 같은 응답 | `options{"7".."10": [{units_list, traits_list "DA_X_N", score, avg, count}]}` (comp_options 일괄 응답에도 같은 값: 54클러스터 중 7렙 43 · 8렙 54 · 9렙 54 · 10렙 52 · 11렙 6) | 7~10(11)렙 | 통계 |
| 같은 응답 | `levels[{stage, round, count, level}]` — 레벨별로 가장 흔한 도달 라운드. 예 423009: 5렙 2-5, 6렙 3-2, 7렙 3-5, 8렙 4-2, 9렙 4-6, 10렙 6-5 | 3~10렙 | 통계 |
| 같은 응답 | `rerolls{"레벨": {rerolls, matches, count}}` — `rerolls/matches` = 판당 리롤 수. 예 423009: 9렙 31.9회(주 리롤 레벨), 423020: 7렙 43.8회(리롤 덱) | 1~10렙, 음수 값 있음 | 통계 |
| lol.qq `tft_lineup_all_detail`(胜率阵容 그룹 상세, 이미 수집 중인 호출) | `level_change_lineup_data[{level, avg_rank, list[{rank, lineup[숫자 chessId], main_trait_list, sub_trait_list, main_c_chess, assist_chess, avg_rank, use_num, use_rate, top_1_rate, top_4_rate, …}]}]` | **최종 레벨 근처 7~10렙만**. 골드~에메랄드 45그룹 중 9그룹은 비어 있음. 4~6렙은 0건 | 통계(중국) |
| lol.qq 편집 덱(`lineup_detail_total`) | `y21_early_heros`(Lv `needLevel_early`, `early_round` 2-3), `y21_metaphase_heros`(Lv `needLevel_middle`, `metaphase_round` 4-3), `hero_location`(최종) + 원문 팁 `early_info`(초반 운영), `d_time`(레벨업·리롤 시점), `hex_info`(증강) | 3단계 | 편집(중국어) |

- `win`(early_options)의 뜻은 확인하지 못했다(0.54~0.77, 1등 비율일 수 없음; 라운드 승률로 추정). **싣지도 표시하지도 않는다.**
- lol.qq 숫자 chessId → DA id 는 `chess.js`(16.18)의 `hero_EN_name` 필드가 DA id 다(예 `DA_18_AzirSoldier`). `TFTID` 가 아니다.
- 빌드업 옵션에는 소환물·변신체 id(`DA_Elderwood18_Lifeblossom`, `DA_Lux18_Base` 등)가 섞인다. 수집기는 이런 id 도 catalog(champions 또는 pets)에 이름·아이콘을 채워야 한다(없으면 `untranslatedIds`).

### 13.2 데이터 계약 추가 — `decks.json` 덱 객체의 `buildup`

```json
"buildup": {
  "global": {
    "scope": "glob_plat",
    "cluster": 423009,
    "rollLevel": 9,
    "levels": [
      {"level": 4, "reachRound": null,  "reachShare": 0.01, "rollsPerGame": 0.8,
       "options": [{"units": ["DA_18_Rakan", "DA_18_Yorick", "DA_18_Yunara", "DA_Karma18"], "n": 3632, "avg": 4.30}]},
      {"level": 5, "reachRound": "2-5", "reachShare": 0.98, "rollsPerGame": 0.2,
       "options": [{"units": ["DA_18_Alistar", "DA_18_Ornn", "DA_18_Shen", "DA_18_Varus", "DA_18_Xayah"], "n": 5634, "avg": 4.22}]},
      {"level": 9, "reachRound": "4-6", "reachShare": 0.96, "rollsPerGame": 31.9,
       "options": [{"units": ["…9개"], "n": 69363, "avg": 4.01, "traits": [{"id": "DA_18_Inferno", "count": 1}]}]}
    ]
  },
  "cn": {
    "bucket": "goldem",
    "levels": [
      {"level": 8, "options": [{"units": ["DA_…"], "carryId": "DA_…", "n": 462, "avg": 3.3, "top4": 0.755, "win": 0.147}]},
      {"level": 9, "options": []}
    ]
  }
}
```

규칙:
- `buildup` 은 선택 필드다. 원천이 하나도 없으면 생략하고, 앱은 섹션을 숨긴다. 옛 캐시(필드 없음)와 호환되게 모델 기본값은 null.
- `global` 은 덱에 metatft 클러스터가 매칭됐을 때만(`global.cluster` 와 같은 값). comp_details 는 그룹의 `global` 을 채우려고 이미 호출하는 응답을 재사용한다(추가 호출 없음).
  - 레벨 4~6: `early_options`. 레벨 7: `early_options["7"]` 와 `options["7"]` 을 합쳐 유닛 집합 기준 중복 제거. 레벨 8~10: `options`. 11 은 버린다.
  - 정렬: `options` 는 원본 `score` 내림차순, `early_options` 는 `count` 내림차순. 레벨당 상위 3개, `n ≥ 100` 만.
  - `units` 는 원본 순서가 아니라 catalog 코스트 오름차순 → 이름순으로 정렬한 DA id. `traits` 는 `traits_list` 의 `DA_X_N` 을 `(id, count=breakpoints[N-1].units)` 로(early_options 에는 없음 → 생략).
  - `reachRound` = `levels` 에서 그 레벨 항목의 `"{stage}-{round}"`, stage 가 빈 문자열이면 null. `reachShare` = 그 레벨 count ÷ `levels` count 최댓값(소수 2자리).
  - `rollsPerGame` = `rerolls/matches`(rerolls ≤ 0 이면 null, 소수 1자리). `rollLevel` = rollsPerGame 최대 레벨(전부 null 이면 null).
  - 옵션이 하나도 없는 레벨도 `reachRound`/`rollsPerGame` 이 있으면 `options: []` 로 싣는다(타이밍 줄 표시용).
- `cn` 은 lol.qq 그룹 대표 변형의 `tft_lineup_all_detail` 응답(이미 받는 값)의 `level_change_lineup_data`. 레벨당 `rank` 오름차순 상위 3개, `use_num ≥ 30` 만. `units` 는 chessId → DA(`hero_EN_name`) 변환 후 위와 같은 정렬. `carryId` = `main_c_chess` 변환값. 비율은 0~1 소수 3자리, avg 소수 2자리.
- 편집 덱 팁: `editorial.notesCn` 에 `early`(= `early_info`), `levelUp`(= `d_time`) 키를 추가한다(기존 `items`, `augments` 유지). 빈 문자열이면 키 생략.
- `verify.py`: 매칭된 덱의 80% 이상이 `buildup.global.levels` 를 가진다. buildup 의 모든 unit id 가 catalog(champions ∪ pets)에 있다(없는 것은 `untranslatedIds` 에 기록, 5개 넘으면 실패).

### 13.3 앱 — 덱 상세 (§6.3 대체)

- **3번 "배치"를 바꾼다.** 초반/중반/최종 보드 탭을 없애고 **최종 배치 보드 하나만** 남긴다(pet 칸 스타일·코스트색 placeholder·히트맵 토글은 그대로). 편집 덱의 초반·중반 구성은 아래 빌드업 섹션에 얼굴 줄로 들어간다.
- **새 섹션 "빌드업"** 을 2번(버튼) 바로 아래에 둔다.
  1. 타이밍 한 줄: `5레벨 2-5 · 6레벨 3-2 · 7레벨 3-5 · 8레벨 4-2 · 9레벨 4-6` + `롤다운 9레벨`(아래 '표기'). 예전 강조 칩 `주 리롤 9렙 (판당 32회)` 과 출처 글자 `글로벌 플래+ 3일` 은 뺀다(2026-09-19 UX 검토 D9).
  2. 레벨 칩 줄: 데이터가 있는 레벨만 `4 5 6 7 8 9 10`(가로 스크롤 없이 한 줄). 기본 선택 = 편집 덱 최종 레벨, 없으면 `rollLevel`, 없으면 가장 큰 레벨.
  3. 선택한 레벨의 행들(내부 스크롤 없음):
     - `글로벌` 옵션 최대 3개: 얼굴 줄(초상 32dp, 3성 표시 없음, 캐리 테두리 없음) + 오른쪽 `4.22등 · 5,634판`.
     - `중국` 옵션 최대 3개(cn 에 그 레벨이 있을 때): 같은 형식 + `TOP4 75%`.
     - `작가` 행(편집 덱 단계의 레벨이 선택 레벨과 같을 때): 얼굴 줄 + `2-3 초반` / `4-3 중반` / `최종`.
     - 이전 레벨 대비 새로 들어온 유닛은 얼굴 위에 작은 `+` 점(같은 출처의 1순위끼리 비교).
  4. 접힘 `작성자 운영 메모 (중국어 원문)`: `notesCn.early`, `notesCn.levelUp`.
  - 데이터가 전혀 없으면 섹션을 숨긴다. 표본 부족 레벨은 흐리게.
- **표기 (2026-09-19 UX 검토 WP-C1 데이터 · A3 화면)**:
  - 레벨 캡션은 `'{L}레벨 {round} 도달 · 롤다운 {r}레벨'`(`round` = `reachRound`, `r` = `rollLevel`). `주 리롤`·`판당 N회`·`글로벌 플래+ 3일` 은 쓰지 않고, 상세에서는 `렙` 대신 `레벨` 이다(`렙` 은 오버레이 레벨 칩에만).
  - 운영은 수집기가 싣는 운영 어휘 다섯 꼴만 쓴다: `빠른 8레벨` · `빠른 9레벨` · `N레벨 리롤` · `표준 운영` · `최종 N레벨`. metatft `levelling` 의 `Fast 8/9` → `빠른 8/9레벨`, `lvl N`·`Reroll N`·`Slow Roll N` → `N레벨 리롤`, `Standard` → `표준 운영`(예전 `표준`), 옮길 수 없는 값(레벨 없는 `Reroll` 등)과 levelling 이 없는 덱은 `최종 N레벨`. 중국 한정 덱은 `buildup.cn.rollLevel ≤ 7` → `N레벨 리롤`, `= 8` 이고 8레벨 도달 라운드가 4-1 이전 → `빠른 8레벨`, 그 밖 `최종 N레벨` — lol.qq 에는 롤다운 레벨·도달 라운드가 없어 지금은 모두 `최종 N레벨` 이다. 예전 `'N레벨 완성'` 은 없앤다(D3·R7).
  - 마무리 레벨 `deck.finalLevel`: 조합 덱(kind meta)은 `global.finalLevels` 점유율 1위 레벨(같으면 낮은 쪽), 분포가 없으면 null — 합쳐진 lol.qq 대표 덱에서 복사되던 값(편집 덱 needLevel·그룹 인원)은 쓰지 않는다(D3: `9레벨 완성` 바로 아래 `빠른 8레벨`). 중국 한정 덱은 편집 덱 최종 레벨 → 그룹 인원 그대로다.
  - 별칭 `alias` = `'{대표 특성} {캐리}'`. 대표 특성은 조합 덱이면 metatft 이름 조각의 특성(덱 traits 에 있을 때), 중국 한정 덱이면 lol.qq 주특성 중 활성 단계가 가장 높은 것, 없으면 traits 중 활성 단계가 가장 높은 것. 캐리는 1순위 캐리이고, 조합 덱은 metatft 이름 조각의 유닛이 캐리 1·2순위면 그 유닛. 겹치면 `·{다음 캐리}` → ` · {운영}`(운영이 다를 때) → ` 2` 순으로 구별한다.
  - 목록·오버레이 한 줄 설명 `summary` = `'{운영} · {대표 특성} {인원} · {캐리 아이템}'`(44자 이하 = 오버레이 설명 두 줄, 아이템은 들어가는 만큼, 같은 아이템은 `×2`). 규칙과 검사는 `fetch_decks.assign_aliases`·`verify.py` (a)~(i).

### 13.4 앱 — 오버레이 (§7 대체 부분)

- 덱 요약의 `초반/중반/최종` 칩을 **레벨 칩**(buildup 이 있는 레벨)으로 바꾼다. 칩을 누르면 얼굴 줄이 그 레벨의 1순위 구성(글로벌 → 중국 → 작가 순으로 있는 것)으로 바뀌고, 그 아래 작은 글자로 `8렙 4-2 도달 · 롤다운 9렙`(`'{L}렙 {round} 도달 · 롤다운 {r}렙'`, 2026-09-19 UX 검토 R4·D9 — 예전 `8렙 4-2 · 주 리롤 9렙` 의 `주 리롤` 은 쓰지 않는다). 운영 어휘·마무리 레벨은 §13.3 '표기' 와 같다.
- 기본 선택은 덱 상세와 같은 규칙. 넓게 보기에서는 이름을 함께 표시(기존 넓게/좁게 규칙).
- 보드 그림은 여전히 그리지 않는다.

### 13.5 소유

- **WP-1(collector-decks-v2)**: `buildup`, `editorial.notesCn.early/levelUp`, buildup 유닛의 catalog 보강, verify.py 검사.
- **WP-3(app-decks-v2)**: `Models.kt` 의 `Buildup` 계열 @Serializable 모델(기본값 null), 덱 상세 빌드업 섹션과 단계 보드 탭 제거, `OverlayContent.kt` 레벨 칩, 테스트 샘플 JSON 의 buildup 예시.
- 필드 이름은 13.2 를 글자 그대로 따른다. 두 패키지는 서로의 코드를 보지 못하므로 이 절이 유일한 약속이다.
