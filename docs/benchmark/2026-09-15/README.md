# TFT 통계 사이트 벤치마킹 — 2026-09-15

협곡 덱 리더 앱의 기능 통합 설계를 위해 **lol.qq.com/tft · lolchess.gg · metatft.com** 을 비교한 기록이다.
대상 시즌 TFT Set 18 / 패치 16.18. 원본 결과는 `raw/`, 벤치마킹 때 받은 응답 샘플은 `samples/` 에 있다.

## 방법

- **벤치마킹** — 사이트마다 에이전트 1명이 메뉴·데이터 엔드포인트를 직접 호출해 확인 (`raw/bench__*.json`)
- **인게임 연동 조사** 1명, **배치표·이미지 버그 진단** 1명
- **채점** — 심사위원 15명 = 카테고리 5개 × 관점 3개(데이터 품질 / 모바일 사용성 / 구현 가능성). 관점마다 세 사이트에 0~10점
- **최종 판정·통합 설계** — Fable. 점수 합계를 기계적으로 따르지 않고 근거의 질을 따져 판정

> 엔드포인트는 비공개 API 라 바뀔 수 있다. 재활용할 땐 `raw/` 의 endpointVerified 와 날짜를 보고 다시 호출해 확인하라.

## 한눈에 보기

| 카테고리 | lol.qq | lolchess | metatft | 점수 1위 | 최종 판정 (Fable) |
|---|---:|---:|---:|---|---|
| 덱 리스트와 덱 상세 | 18.0 | 16.0 | 21.0 | metatft.com | lol.qq.com/tft (점수와 다름) |
| 챔피언과 특성 | 12.0 | 19.5 | 24.0 | metatft.com | metatft.com |
| 아이템 | 12.5 | 19.5 | 24.5 | metatft.com | metatft.com |
| 증강 | 15.5 | 12.5 | 12.5 | lol.qq.com/tft | lol.qq.com/tft |
| 전적·티어·인게임 | 4.5 | 20.0 | 21.5 | metatft.com | metatft.com |

점수는 관점 3개 합계(최대 30점).

## 최종 판정 (Fable)

### 덱 리스트와 덱 상세 → lol.qq.com/tft · 점수 합계와 다르게 판정

합계는 metatft 21 : lolqq 18 : lolchess 16이지만 lolqq로 뒤집는다.
(1) 3점 차는 전부 모바일 사용성 축(metatft 6 : lolqq 2.5)에서 났고, 그 축의 1위는 metatft가 아니라 lolchess(7.5)다. lol.qq의 2.5점은 '중국어·1050px 고정 데스크톱 사이트'라는 원본 화면 점수인데 우리는 화면을 직접 만든다. 그 심사위원 스스로 'lol.qq 데이터는 모바일 UI에 쓰기 가장 좋다(티어 구간·변화량·칸별 배치율·단계별 증강)'고 적었다. 데이터+구현 두 축만 합치면 lolqq 15.5 : metatft 15로 대등하다.
(2) 앱 정체성: 2026-09-14 확정 결정이 'lol.qq 목록 기준, metatft는 대조(단방향)'다. 덱 목록의 기준 소스를 metatft로 바꾸면 중국 통계 앱이 아니게 된다.
(3) 유일 데이터: 덱별 증강 성적(세 소스 중 유일), 칸별 사용률·승률, 핵심 유닛 성급 비율·아이템 수, 5개 티어 구간, 당일 신선도, 편집 덱(레벨별 보드·운영 텍스트)이 lol.qq에만 있다.
(4) metatft의 데이터 8점은 comps_stats 한 엔드포인트에 기댄 점수다. 직접 재확인(2026-09-15 22:14 KST): comps_stats는 server=KR·rank 필터가 실제로 먹고(KR 플래+ 3일 1,108,328보드, 55클러스터 합이 정확히 100%, 가중 평균 4.5000, 미분류 747) 품질이 훌륭하다. 그러나 데이터·구현 심사위원이 독립적으로 확인했듯 comps_data·comp_details는 rank/days/server를 무시한 고정 모집단이고(KR 마스터+를 붙여도 423009 = 556,572·4.2225 그대로), 증강이 없고, 클러스터 id가 재클러스터링마다 바뀌며, 새 패치 덱이 43시간 늦게 생겼다. metatft로 덱 목록을 만들면 카드 숫자는 좋지만 상세는 라벨을 붙일 수 없는 고정 집계가 된다.
(5) 구현 8.5점은 근거가 충실하다. 이 카테고리 요소 전부가 인증 없이 endpointVerified이고 정적 경로가 s13부터 유지되며 숫자 id도 chess/race/job.js로 100% DA_* 변환된다. 내 재확인에서도 数据检索器 tft_lineup_rank(version='' 전 빌드, tier '7+', 3일)가 100행·개수 모순 0·comp_s 중앙값 94, 胜率阵容 tft_lineup_group_list 425조합 당일(dtstatdate 20260915), 상세 augment_data 5행이 result 0으로 왔다.
(6) lol.qq의 약점과 처방: 胜率阵容은 0.1단위 반올림·평균 4.0 이하만 노출·소표본 S등급·기간 정의 없음 → 등급과 수치는 数据检索器 카운트(comp_s·top1_cnt·top4_cnt)로 수집기가 재계산하고 표본 라벨을 단다. 단 lineup_rank 상위 100덱은 마스터+ 보드의 17%(16,404/97,474)만 덮는 좁은 덱 정의라, 커버리지가 넓은 '한국/글로벌 등수' 열은 metatft comps_stats에서 가져온다. lolchess는 약관(재배포 금지 조항을 3명이 확인)·마스터+ 글로벌 한 조합만 동작·상세 null이라 데이터원에서 제외한다.

**데이터 조달** — [기준 목록·상세: lol.qq]
① 편집 덱 GET game.gtimg.cn/images/lol/act/tftzlkauto/json/lineupJson/s18/6/lineup_detail_total.json(기존 26덱, hero_id DA_*). tft_recent_versions의 16.18 첫 빌드 시작(2026091007 CST)보다 update_time이 이르면 '이전 패치 작성' 배지(현재 21/26).
② 胜率阵容 POST mlol.qt.qq.com/go/exploit/proxy req_alias tft_lineup_group_list(version_id dgroup_v3, time_type d_grouping_v3, queue_id 1100), tier_part 255/0/1/2/3 각 1회 → '주특성+메인C 그룹 → 변형(more_lineup)' 2단 목록, 다섯 구간을 JSON에 미리 담아 폰에서 오프라인 전환. 숫자 chessId/traitId는 chess/race/job.js(TFTID→hero_EN_name, raceId/jobId→characterid; versionconfig.json은 16.17을 가리키지만 '16.18-2026.S18' 경로가 존재하므로 CDragon 패치 버전으로 먼저 시도 후 폴백)로 DA_* 변환. 아이템·증강(rune_id)은 이미 DA_*.
③ 수치·등급: 数据检索器 tft_match_overview + tft_lineup_rank(req_params version='' 전 빌드 합산, tier '7+'와 '4+', stime=etime-2, etime=전날(CST), minSampleSize 10, limit 100) → comp_s·top1_cnt·top4_cnt로 평균·톱4·1위율과 표본 보정 등급((n·avg+200·4.5)/(n+200))을 수집기가 계산, Δ는 overview rank_dist로 만든 가중 평균 대비. 덱 id(base64 구성 문자열 '1100#DA_…$DA_…$8')를 풀어 DA_* 집합으로 ①·②와 자카드 매칭.
④ 상세는 노출 덱(구간별 상위 N, 100 이내)에만 tft_lineup_all_detail(augment_data·key_champion_data·equip_data·level_data), tft_lineup_position(칸별 use_rate·win_rate → 히트맵), tft_lineup_key_chess(성급 비율·equip_num → '2성 67% · 아이템 2.9개' 칩) 호출(version_id v1, minor_traits_id는 sub_trait_list로 구성하고 없으면 '-1,-1'). dtstatdate(T-1)를 그대로 라벨.
[대조·한국 열: metatft]
latest_cluster_info(units_string, 기존 자카드 매칭·onlyInChina 유지) + comps_stats(글로벌 플래+, KR 플래+, KR 마스터+ 3회, permit_filter_adjustment=false) places[0..7] → 매칭 클러스터의 평균·톱4·n. comps_data 1회 → levelling·difficulty. 클러스터 id는 당일 조인만, 저장 금지.
[한글화] CDragon ko_kr apiName 정확 일치(기존).
[검사] verify.py: 소스별 최소 행수(편집 20, group_list 300, lineup_rank 50, 클러스터 30), sum(places)=count, avg 범위 검사, HTTP 200 빈 응답 실패 처리. 모든 덱 카드에 'n=… · 3일 · 티어 · 기준일' 라벨.

**다른 사이트에서 가져올 것**

- metatft.com: 매칭된 덱에 'KR 플래+ n=9,849 · 4.2등' 같은 한국/글로벌 비교 한 줄. comps_stats(queue=1100&patch=current&days=3&rank=…&server=KR&permit_filter_adjustment=false)의 places 앞 8개(9번째 원소는 count)로 평균·톱4·표본을 계산한다. 클러스터 id는 latest_cluster_info의 Cluster 필드로 당일에만 연결하고 저장하지 않는다.
- metatft.com: 카드에 운영 방식 칩(comps_data levelling: Fast 8/Fast 9 → '빠른 8레벨')과 난이도 칩(difficulty). comps_data는 필터를 무시하는 고정 집계이므로 티어·기간 라벨은 붙이지 않는다.
- metatft.com: 카드 정보 위계(큰 숫자 하나 + 작은 보조 수치 하나), 목록 고정·숨김(길게 누르기, DataStore 저장), 상세 레벨 칩에 도달 비율('8렙 47% · 9렙 47%', comp_details.final_levels)을 '기간·티어 미표기 집계' 라벨과 함께 표시.
- lolchess.gg: 모든 덱 카드 같은 자리에 고정 4수치 줄(평균 등수 / 픽률 / 승률 / TOP4)과 상세 맨 위의 큰 '팀 코드 복사' 버튼. 값은 lol.qq·metatft로 채우고 lolchess 데이터는 가져오지 않는다.
- lolchess.gg: 캐리 순위 강조(캐리 3명만 아이템 3개를 크게, 나머지는 아이콘만 — lol.qq main_c_chess/assist_chess/second_assist_chess가 그대로 대응), 한글 덱 이름 규칙 '[태그] 핵심 특성+캐리', '최종 업데이트 N시간 전' 표기.
- lolchess.gg: 배포 전 일관성 검사 패턴을 verify.py에 넣는다: sum(places)=count, 분포로 재계산한 평균·톱4가 표시값과 같은지, 목록 가중 평균이 4.5±0.05인지(lolchess 4.35 같은 편향 감지), 전부 0인 필드 제거, HTTP 200 빈 응답을 실패로.

### 챔피언과 특성 → metatft.com

세 축 모두 metatft 1위(데이터 8.5 / 사용성 7.5 / 구현 8)이고 근거의 질도 가장 높다. 표본(플래+ 7일 1,190만 보드, lolchess의 4.6배), 랭크 임의 조합, KR 서버 단독 조회(직접 재확인: server=KR이면 games.count 5,785,016→967,672, 70행), places[8] 원시 분포, lolchess와 상관 0.995(특성 0.998) 교차검증, 이미 수집기가 쓰는 호스트. 감점 요인(분모 흔들림, TFT18_* 6행, 특성 _N 순번, 사전 수치 일부 불일치)은 전부 수집기 정규화로 흡수된다.
lol.qq를 1위로 두지 않는 이유: 사이트 기본 화면 tft_hero_ranking은 사용률 가중 평균 3.53·톱4 68%로 모집단상 불가능하고 표본 수가 없다. 数据检索器 tier ''(不限)는 내 재확인에서도 75/75행 모순이었다. 심사위원 간 불일치(데이터 심사위원 '티어 필터면 위반 0' vs 구현 심사위원 '티어 필터에서도 47/69 모순')는 재확인으로 데이터 심사위원 손을 들었다: 분모를 unit_s(해당 유닛이 있는 보드 수)로 두면 tier 4+/2+/7+에서 위반 0(72/74/65행, 특성 87행도 0)이고, 분모를 total(전체 보드)로 두면 71/73/64행이 위반된다. 구현 심사위원의 '모순'은 total을 분모로 쓴 계산으로 보인다. 합산도 정상이다(sum(unit_s)/total=8.35 유닛/보드, 비 DA 행 0). 따라서 lol.qq 数据检索器는 '티어 필터 + version='' + unit_s 분모' 조건에서 중국 서버 열로 쓸 수 있지만, 기간 최대 3일·티어 필수·avg_rank_delta 정의 불명·정적 사전 한 패치 지연 때문에 기준이 아니라 보조다.
lolchess는 원시 카운트가 정확하고 해석성·한국어가 최고지만 약관, 글로벌만(KR 필터 없음), 표본 작음, 1시간 갱신, pickRate가 슬롯 점유율이라 UI와 용어만 빌린다.

**데이터 조달** — [기준: metatft] GET api-hc.metatft.com/tft-stat-api/units 와 /traits를 (글로벌 플래+, KR 플래+, KR 마스터+) × queue=1100&patch=current&days=3&permit_filter_adjustment=false로 하루 3회씩 호출해 행마다 places[8]와 games[0].count·updated를 저장. 수집기가 avg/top4/win/pick(=Σplaces/games.count, 보드당 채용률)과 등급(4.5−avg 기준 S>.3/A>.1/B>−.1/C>−.3, 표본 보정 후)을 계산해 champions.json·traits.json에 넣고 앱은 조회만. TFT18_* 저표본 행 제거, 특성 'DA_X_N'은 CDragon effects로 (DA_X, minUnits) 변환. 상위 챔피언은 unit_detail_items(unit=DA_*, artifact_count=0)로 추천 아이템 5개·3아이템 빌드 3개를 요약(유닛별 1회, 약 70회/일).
[중국 열: lol.qq] POST mlol.qt.qq.com/go/exploit/proxy 数据检索器 tft_hero_rank(unitType '')·tft_trait_rank, req_params {queueId '["1100"]', version '', tier '4+', stime=etime-2, etime=전날(CST), minSampleSize '10', limit '2000', filterOptions ''} → unit_id(DA_*)·unit_s·top1_cnt·top4_cnt·total 저장. 수집기에서 avg가 top1·top4 비율로 가능한 구간 안인지 검사해 실패 행은 버리고, 픽률은 unit_s/total. 특성 키 'DA_X,n'은 이미 인원수. tft_hero_ranking(国服大数据)은 쓰지 않음.
[사전] CDragon ko_kr(이름·설명 기본) + metatft data.metatft.com/lookups/TFTSet18_latest_ko_kr.json ability.variables(스킬 수치 텍스트 보조) + lol.qq chess.js 16.18(수치 검증용).
[조인] DA_* 정확 일치(metatft 65/71, lol.qq 69/69, 특성 35/35~36/36 확인). lolchess 미수집.

**다른 사이트에서 가져올 것**

- lolchess.gg: 한국어 용어 표준: 헤더 '평균 등수 / TOP4 / 승률 / 게임 수 / 픽률', 특성 행은 '11 나무정령'(활성 인원수+이름), 챔피언 상세 추천 아이템 탭 '3신기 / 아이템 / 유물 / 찬템 / 상징'. 이름은 CDragon ko_kr로 채운다.
- lolchess.gg: 특성 단계 키를 (DA_특성, 활성 인원수)로 통일. metatft의 'DA_X_N'(N=단계 순번)을 CDragon effects[N-1].minUnits로 바꾸면 lol.qq 'DA_X,n'·lolchess numUnits와 같은 키가 된다(88행 비교 상관 0.998로 검증됨). Eclipse·Rival처럼 한쪽에만 있는 행은 출처를 표시.
- lolchess.gg: 패치 리비전 스냅샷: 패치가 바뀌는 날 이전 패치의 마지막 챔피언·특성 통계를 동결 저장해 '지난 패치 대비 평균 등수 변화'를 보여 준다. 하루 1회 수집 구조에 그대로 붙는다.
- lolchess.gg: 반면교사 3개: 기본 정렬에 최소 표본 문턱(1,000게임 미만 흐림 — lolchess는 3~19게임짜리 럭스 변형이 1~4위), 표가 폰 폭을 넘으면 챔피언 이름 열 고정(가능하면 등급·평균 등수·TOP4·게임 수 4열만 남겨 스와이프 제거), 목록 상단에 '18.2 · 플래+ · 32만 판 · N시간 전' 표본·신선도 줄.
- lol.qq.com/tft: '중국 서버' 열: 数据检索器 tft_hero_rank·tft_trait_rank(tier '4+', version='', 3일)의 unit_s·top1_cnt·top4_cnt로 평균·톱4를 직접 계산하고, Δ는 서버가 주는 avg_rank_delta 대신 '유닛 평균 − 같은 모집단의 unit_s 가중 평균'(4+ 3일 기준 4.174; overview 평균 4.284와 다름)으로 우리 정의로 계산. 범위 검사 통과 행만 싣는다.
- lol.qq.com/tft: chess.js 16.18(CDragon 16.18과 74명×9수치 전부 일치)로 metatft·lolchess 사전 불일치(다이애나 마나 40, 렝가 공속 0.75, 사거리 5 등)를 자동 경고. 도감의 增强/削弱/最新을 '버프/너프/신규' 배지로. 특성 '조합' 카드(tft_trait_strength_trend 2특성+단계별 登顶·前四 5주기 추세, 숫자 traitId → race/job.js)를 아코디언으로.

### 아이템 → metatft.com

세 축 모두 metatft 1위(데이터 9 / 사용성 7 / 구현 8.5). 아이템 키 142개가 lol.qq와 완전히 같고(lolchess 126은 부분집합), items_matches places[8]·item_detail(착용 유닛 68개, 유닛 평균 대비 차이)·item_stage_detail·unit_items_processed(아이템→상위 5유닛 역검색, 1회 68KB)가 인증 없이 온다. 내 재확인: items_matches server=KR 142행, games 1,108,328(플래+ 3일). lolchess와 113개 아이템 평균 등수 차 0.020·Spearman 0.988로 정확성이 교차검증됐다.
lol.qq는 중국 표본과 '챔피언#아이템' 전체 행렬(7,395행)이라는 고유 가치가 있지만, 같은 날 같은 아이템의 평균 순위가 엔드포인트마다 4.1/1.69/5.19로 갈리고, 数据检索器도 실버+에서 10행이 불가능 값이며, 装备排行 착용 챔피언은 id가 잘려(전체 고유 8개) 못 쓴다. 인기도(build_s)만 글로벌 사용량과 0.988로 일관돼 그 용도로만 쓴다.
lolchess는 유일한 '동반 아이템' 통계와 가장 자연스러운 한국어·조합표가 있지만 약관, 부품 누락, 등수 분포·추세 없음, dt=1/5 조용한 빈 응답이라 UI만 빌린다.
범위 주의: 앱의 차별점 '아이템→덱 역검색'은 수집기 index.item(lol.qq 편집 덱 기반)이 이미 담당한다. 이 판정은 '아이템→착용 유닛·수치·조합표' 부분의 소스에 관한 것이며, 덱 역검색의 기준은 그대로 lol.qq다.

**데이터 조달** — [기준: metatft] GET api-hc.metatft.com/tft-stat-api/items_matches를 (글로벌 플래+, KR 플래+, KR 마스터+) × days=3&permit_filter_adjustment=false로 호출해 places[8]·games.count 저장 → 수집기가 평균·톱4·승률·픽률과 아이템 티어((4.5−avg+p)×빈도^i, 일반 p=.5 i=1 / 상징 p=.125 i=.25 / 유물·지원·찬란 p=0 i=0)를 계산. tft-comps-api/unit_items_processed 1회 → itemNames[DA].units 상위 5(착용 유닛). 완성템 55+상징은 item_detail(itemName=DA_*) units places로 착용 유닛 표(≈70회/일), TFT18_* 행 제거. 톱4는 조건부(아이템 인스턴스 기준)로 계산하고 '보드 기준보다 높게 나온다' 도움말.
[중국 열: lol.qq] 数据检索器 tft_equip_rank(ShowHero='1', tier '2+', version ''(hero/lineup에서 전 빌드 합산 확인, equip은 같은 파라미터 규약이라 적용 가능[추정]; 빈 응답이면 tft_recent_versions 최신 빌드 문자열로 재시도), stime=etime-2, etime=전날 CST, minSampleSize '10', limit '8000') → 'DA_챔피언#DA_아이템' 키의 build_s·build_rate 저장(최소 build_s가 10보다 크면 잘린 것으로 보고 로그). 아이템 단독 tft_equip_rank는 build_rate·build_s만 쓰고, avg_rank·前四는 범위 검사 통과 시에만 참고 표시. 빈 item_id 행 제거.
[조합표] CDragon ko_kr items[].composition(DA_* 완성템 55, 부품 10 확인) → 격자; lol.qq equip.js formula(DA_Component_* 이름, 55/55 동일)는 패치 날 교차검증에만.
[덱 역검색] 기존 index.item(lol.qq 편집 덱 units[].items) 유지, 胜率阵容 main_c_chess_equip·assist_chess_equip으로 확장.
[조인] DA_* 정확 일치(metatft 142 = lol.qq 142, CDragon 141/142; DA_Artifact_Hullcrusher만 세 소스 모두 없음). lolchess 미수집.

**다른 사이트에서 가져올 것**

- lol.qq.com/tft: 数据检索器 tft_equip_rank(ShowHero=1, limit 8000)의 'DA_챔피언#DA_아이템' 행렬(7,395행)로 '중국에서 이 아이템을 누가 드나' 열. 정렬은 build_s(사용 수), 최소 표본 300~500, 서버가 주는 S/A/B/C 등급은 버린다. 아이템 역검색 결과 아래에 '중국 덱 / 글로벌 착용 유닛' 두 줄로.
- lol.qq.com/tft: 装备 페이지의 '부품 선택 → 그 부품이 들어가는 완성템 목록' 흐름을 부품 10종 칩 줄로 옮긴다(칩 1~2개 탭 → 완성템을 평균 등수순으로). 데이터 기준 라벨에 핫픽스 빌드('16.18.817.4437 반영') 표기. 레거시 tft_equip_ranking의 hero_list·top_*_hero는 쓰지 않는다.
- lolchess.gg: 용어와 글자 칩: 지표 '평균 등수 / TOP4 / 승률 / 픽률 / 게임 수', 분류 칩 '전체 / 일반 / 상징 / 유물 / 찬란'(metatft식 글자 없는 20px 아이콘 대신). 덱은 '덱', 아이템 제작만 '조합'으로.
- lolchess.gg: 부품 10종 11×11 조합표를 가로 넘침 없이 한 화면 격자로(칸 30dp 이상, 부품 머리칸 탭 시 행·열 강조, 칸 탭 시 하단 시트에 효과·착용 상위 5명). 통계 티어표에서는 부품·물약 16개를 빼고(142→126) 조합표로만 보여 준다.
- lolchess.gg: 챔피언 TOP5를 740px 가로표 대신 2줄 카드로: 1줄 아이콘·이름·티어·평균 등수·TOP4·n판, 2줄 착용 상위 5명의 아바타+한글 이름(아이콘만 두지 않음). 모든 행에 'n판'을 항상 표시하고 표본 하한 아래는 흐림. 상세 라우트 키는 DA_* apiName으로 통일.

### 증강 → lol.qq.com/tft

시즌 18 증강의 실측 통계는 lol.qq에만 있다. 세 심사위원이 각각 확인했고 내 재확인(22:14 KST)에서도 胜率阵容 상세 tft_lineup_all_detail의 augment_data가 5행(DA_Ascension 634회 3.16등, DA_LateGameScaling 2,021회 3.19등, 1_/2_/3_avg_rank 포함)으로 result 0이었다. metatft는 unit_augments가 빈 문자열 1행, augment_unit_detail [], augments_full2 500, 경기 JSON에 증강 키 없음. lolchess는 meta-deck-augments {patchRevisions:[]}이고 화면에 'Riot 정책상 시즌 13부터 미제공'이라 적혀 있다. 두 곳의 티어는 표본 0인 편집 의견이고 공통 249개 중 53%만 같은 등급이다. lolchess의 사용성 7점은 데이터 없는 화면 구조이고 약관 문제도 있어 뒤집을 근거가 되지 못한다.
lol.qq의 한계는 분명히 적는다. 전체 증강 랭킹 tft_augment_rank는 화이트리스트(result 101)이고 프론트 탭 자체가 폐지돼 통계 티어표는 만들 수 없다. 덱당 평균 등수 상위 5개만 나와 265개 중 111개(42%)만 통계에 등장하고 성적 나쁜 증강은 보이지 않는다. use_rate 분모가 덱 판수와 맞지 않고(약 2~36배), 단계별 평균에 표본 수가 없어 '1'·'0' 같은 소표본 값이 섞인다(재확인 샘플 DA_SilverSpoon 1_avg_rank '1'). 상세는 T-1, 목록은 당일이다. 마스터+는 증강 행이 1/10덱뿐이라 골드~에메랄드(tier_part 2)·전체(255)를 쓴다. 따라서 앱의 증강 화면은 '덱별 통계(lol.qq) + 편집 티어(metatft) + 사전 태그(metatft ko_kr)' 조합이 되며, 증강 자체의 평균 등수 순위는 만들 수 없다고 명시한다.

**데이터 조달** — [통계·추천 덱: lol.qq] POST mlol.qt.qq.com/go/exploit/proxy tft_lineup_group_list(tier_part 2와 255, dgroup_v3) rune_id_group(DA_*, 덱당 ≤5) → 증강→덱 역인덱스(라벨 '통계 상위')를 만들고 기존 hexbuff 역인덱스(라벨 '작가 추천')와 병합. 노출 덱(≤100)만 tft_lineup_all_detail(version_id v1, minor_traits_id는 sub_trait_list로 구성) augment_data의 use_num·avg_rank·1_/2_/3_avg_rank 저장: '0'은 결측 처리, use_num<300은 흐림, use_rate는 미표시, 단계별 평균은 기본 접힘, dtstatdate와 tier_part 이름을 라벨로. 호출량은 목록 2회+상세 ≤100회(심사에서 436회 연속 호출도 차단 없었음).
[티어·사전: metatft] GET api-hc.metatft.com/tft-stat-api/augments_tiers content.content.tierList → catalog.augments[].editorTier(+author, updated_at); GET data.metatft.com/lookups/TFTSet18_latest_ko_kr.json augments → rarity·manual_tags·한글명 fallback; GET tft-comps-api/comp_augment_tiers → 추천 덱 역인덱스. 전부 인증 없음, tierList가 비면 이전 값 유지.
[풀·이름] lol.qq hex.js(265개, augments 필드 DA_* 100%; 16.17 경로 → 16.18 경로 우선 시도) + CDragon ko_kr 이름 우선 + 수동 오버라이드 사전.
[조인] DA_* 정확 일치(통계 증강 111/111 CDragon 일치, augments_tiers 253/265 hex.js 겹침). lolchess 데이터 미수집.

**다른 사이트에서 가져올 것**

- metatft.com: tft-stat-api/augments_tiers(1회 GET 60KB; 재확인 S24/A84/B129/C21, updated_at 2026-09-15T11:11Z) → 증강 카탈로그에 '에디터 티어 S~D' 배지. 통계가 아니므로 작성자(META Spencer)·갱신 시각을 함께 표기하고 lol.qq 수치와 섞지 않는다.
- metatft.com: data.metatft.com/lookups/TFTSet18_latest_ko_kr.json augments의 rarity(실버/골드/프리즘)·manual_tags(경제/아이템/전투/특성/성장)로 엄지 위치의 필터 칩. CDragon에 없는 증강 15개(DA_18_FloraFatalisAugmentPlus 등)의 한글명도 여기서 보충한다. 파일의 augmentTiers 키는 희귀도 아이콘 스타일이라 티어로 쓰지 않는다.
- metatft.com: comp_augment_tiers(클러스터 28개, 가이드 기반)를 뒤집어 '이 증강을 S로 추천하는 덱' 목록. units_string으로 lol.qq 덱에 붙이고 S만 쓰며 distance가 큰 매칭은 제외, source_title을 출처로 적는다.
- metatft.com: 증강 검색을 이름·한글 설명·영문명으로 넓히고 '/'로 여러 단어 OR 검색. CDragon 설명의 @EmblemAmount@ 같은 자리표시자는 지운 평문으로 인덱싱한다.
- lolchess.gg: 화면 구조만: 가이드/티어/확률/배제 탭, 희귀도·라운드(2-1/3-2/4-2) 칩, 격자↔목록 토글(폰에서 격자 이름 잘림 39~91칸 방지). 상단에 '증강 성적은 중국 서버 골드~에메랄드 하루치, 티어는 편집자 의견'이라는 출처 고지 한 줄. 라운드별 등장 확률표는 API가 아니라 게임 상수이므로 패치마다 수기 관리.

### 전적·티어·인게임 → metatft.com

합계 metatft 21.5 : lolchess 20 : lolqq 4.5. 1.5점 차지만 뒤집을 이유가 없다. 이 카테고리는 개인 데이터라 하루 1회 수집기가 아니라 기기가 직접 호출해야 하고, 앱 ProfileRepository가 이미 metatft lookup_by_riotid로 동작 중이다. 정확성은 두 소스가 같다(시즌 18 랭크 112/112 경기, 등수 불일치 0, LP 로그 108/108, 현재 DIAMOND IV 일치, 서버 순위 0.7% 이내). 차이는 세 가지다. (1) 갱신: metatft는 조회 후 last_refreshed가 바뀐 사례를 확인했고, lolchess는 GET으로 갱신되지 않아(syncedAt 11:57Z 고정) 서버가 Riot을 부르게 하는 sync RPC가 필요해 차단 위험이 가장 크다. (2) 깊이: metatft 경기 JSON에 8인 티어·LP·서버 순위·MMR 전후값·로비 평균이 있고, lolchess는 티어·디비전뿐이며 eog/details가 404다. (3) 약관: lolchess는 복제·제3자 제공·타 이용자 정보 저장 금지 조항이 명시돼 있고 metatft 약관(2023-06-11)에는 해당 조항이 없다. lolchess가 앞서는 백분위·b패치·자연스러운 한국어·큰 터치 영역은 같은 metatft 응답(server_rank, match_timestamp)으로 재현할 수 있는 UI 차이다. lol.qq는 KR 계정을 조회할 수 없어 이 카테고리의 핵심을 줄 수 없다.
범위 주의: 오버레이는 '덱 참고·티어 참고'만이므로 인게임 로비 스카우팅(summoner_by_puuid)은 범위 밖이고, 게임 중 응답도 세 심사위원 모두 '{}'만 봐서 미확인이다. 실질적 개선점은 오버레이 주기 호출을 211KB·최대 10초짜리 full_profile 대신 rating_changes(17KB, 0.9초)로 바꾸고, 현재 8~9.5sp인 ProfileCard 글자를 10sp 이상으로 올리는 것이다.

**데이터 조달** — [기기 직접 호출: metatft] 상세 화면은 GET api.metatft.com/public/profile/lookup_by_riotid/KR/{name}/{tag}?source=full_profile&tft_set=TFTSet18(기존) → ranked.rating_text/rating_numeric/peak_rating, server_rank{rank,total}, ranked_season_stats['1100'].placements[8], matches[].placement/avg_rating(로비 평균 티어 → '로비 평균 에메랄드 I')/match_timestamp/summary.units. 오버레이 주기 갱신은 GET public/profile/rating_changes/KR/{name}/{tag}?queue=1100(17KB) → 이웃 행 rating_numeric 차로 경기별 ±LP를 등수 칩 아래에 표시. 응답 refresh.status가 queued면 1·2·4·8초 백오프로 lookup 재조회(번들 코드로 확인, 실응답 미관찰). '지난 판 로비'가 필요하면 matches3.metatft.com/{riot_match_id}.json(25KB, immutable) 한 경기만 받아 participant_info 티어를 표시하고 기기 캐시에는 타인 riot_id·puuid·mmr을 넣지 않는다. 한글화는 summary.units character_id/itemNames(DA_*)를 decks.json catalog로 조인, 특성은 '_N' 제거 후 조인. 브라우저 CORS 제한(public/*는 www 출처만)은 네이티브 호출과 무관.
[수집기(집계만, 하루 1회)] metatft public/promotion_thresholds/latest → KR 챌린저(500LP·161명)·그마(200LP·314명) 컷과 인원(응답 timestamp 대신 수집 시각 기록) → '그마 컷까지 xLP'. lol.qq POST mlol.qt.qq.com/go/exploit/get_tier_rank_1000 {area_id} → 대区별 최저 point·인원만.
[저장·범위 금지] 타인 puuid·riot_id·MMR·login_ip 저장 금지, tft-spectate·관전·lolchess summoner-sync 계열은 쓰지 않음(lolchess 약관 + 범위 밖).

**다른 사이트에서 가져올 것**

- lolchess.gg: '상위 1.03% · 6,397위' 표기. metatft lookup 응답의 server_rank{rank,total}로 기기에서 계산한다(lolchess 6,353/617,673과 0.7% 안쪽 일치 확인). 톱4율·1등 백분위는 metatft에 없으니 티어 백분위 하나만.
- lolchess.gg: 터치 영역 48dp 이상, 글자 12sp 위주, 'Diamond IV'가 아니라 '다이아몬드 IV'로 통일, 'N분 전 업데이트' 표시와 수동 갱신 버튼. metatft의 9~10px 글자·고정 광고·'28일 8월'·'강제 플레이어' 같은 직역은 반면교사.
- lolchess.gg: b패치 구분(18.1 / 18.1d). metatft 프로필의 patch는 b패치를 합치므로 match_timestamp를 tft-stat-api/patch 시작 시각이나 경기 JSON _metatft.patch_resolution(live_at)과 비교해 기기에서 붙이고 '현재 패치 18.2: 32판 평균 x등'을 보여 준다.
- lolchess.gg: 지난 시즌 표기는 '마지막 관측 티어' 라벨로(metatft rating_history가 Set 14에서 DIAMOND I vs lolchess MASTER I로 어긋남), 최고 티어가 최종보다 낮은 경우(Set 9.5)는 숨긴다. LP 추이 그래프에 누적 톱4 비율을 겹친다(rating_changes 109행, 중복된 5번째 경기는 num_games로 제거).
- lol.qq.com/tft: 段位排行 get_tier_rank_1000(Referer 없이 200 확인)에서 대区별 王者·宗师 최저 LP와 인원만 집계해 '중국 서버 챌린저 컷' 참고 수치로. puuid·닉네임·intent는 저장하지 않는다. 우선순위 낮음.

### 공통 메모

[판정 요약] deck=lolqq(합계와 다름), champion·item·profile=metatft(합계와 같음), augment=lolqq(합계와 같음). 방침은 '덱 목록의 기준과 중국 고유 데이터(덱·증강)는 lol.qq, 챔피언·아이템·전적의 수치 기준은 metatft, lolchess는 UI·용어·검증 기준만'. 2026-09-14 확정 결정(lol.qq 기준·metatft 대조 단방향·앱 밖 수집·CDragon 한글화)과 충돌하는 판정은 없다.

[lolchess는 데이터원이 아니다] 세 심사위원이 라이브 약관 페이지에서 '사전 승낙 없는 복제·제3자 제공 금지', '타 이용자 개인정보 수집·저장 금지', '상업적 이용 금지' 조항을 직접 확인했다. 공개 저장소+Actions 재배포 구조와 정면 충돌하므로 어떤 카테고리에서도 자동 수집하지 않는다. 화면 패턴, 한국어 용어집(평균 등수/TOP4/승률/픽률/게임 수, '11 나무정령', 3신기/유물/찬템/상징), 내부 일관성 검사 기준만 빌린다. 사용성 축에서 lolchess가 3번 1위였지만 이것은 우리 화면 설계의 참고이지 소스 판정의 근거가 아니다.

[DA_* 조인 규칙] 세 소스 공통 키는 DA_* 정확 일치(접두사 제거 불필요; 아이템 142=142, 챔피언·특성도 CDragon과 거의 전부 일치). 정규화 3가지: ① metatft 특성 'DA_X_N'의 N은 인원수가 아니라 단계 순번 → CDragon effects[N-1].minUnits로 (DA_X, 인원수)로 변환하면 lol.qq 'DA_X,n'·lolchess numUnits와 같은 키가 된다. ② metatft 통계의 TFT18_*(Akali, Gromp, KogMaw, MasterYi, NidaleeCougar, SprykinSummonMelee) 저표본 행 제거. ③ lol.qq 胜率阵容·특성 추세의 숫자 chessId/traitId는 chess/race/job.js(TFTID→hero_EN_name, raceId/jobId→characterid; 65/65, 36/36 매핑 확인)로 변환. versionconfig.json은 아직 16.17을 가리키지만 '16.18-2026.S18' 경로 파일이 존재하므로 CDragon 패치 버전으로 먼저 조립하고 404면 versionconfig 경로로 폴백. 한글 이름은 CDragon ko_kr 우선, metatft ko_kr lookup은 CDragon에 없는 증강 15개·스킬 수치 보조.

[metatft 사용 규칙] 필터(rank/days/server/patch)가 먹는 엔드포인트는 tft-stat-api/*(units·traits·items_matches·item_detail·unit_detail_*)와 tft-comps-api/comps_stats뿐이다(재확인: server=KR에서 units games.count 5,785,016→967,672, comps_stats 6,799,416→1,108,328). comps_data·comp_details·unit_items_processed는 필터를 무시하는 고정 모집단이라 티어·기간 라벨을 붙이지 않는다. permit_filter_adjustment=false 고정, 과거 패치는 patch=18.1&b_patch=d처럼 b_patch까지 지정(빈값이면 18.2로 폴백). comps_stats places는 9원소(앞 8개=등수 분포, 9번째=count)라 앞 8개만 합산하고, 첫 행 {cluster:''}은 총 보드 수다. 클러스터 id는 저장하지 않고 units_string으로 매일 재매칭(없는 id는 HTTP 500). api-hc는 CDN 캐시가 없어 요청마다 오리진이 집계하므로 하루 호출을 수백 회 이내로 유지. 잘못된 키(TFT18_Aphelios, TFT_Item_*)는 200 빈 응답이라 빈 값 검증 필수.

[lol.qq 사용 규칙] 数据检索器는 반드시 tier 필터('2+'/'4+'/'7+')를 주고 분모는 unit_s/trait_s/build_s/comp_s(해당 항목이 있는 보드 수)를 쓴다. tier ''는 전 행 모순(재확인 75/75)이고, 'total'(전체 보드)을 분모로 쓰면 위반이 난다 — 구현 심사위원의 '티어 필터에서도 모순' 보고는 이 계산으로 판단한다. version=''로 전 빌드 합산(hero/trait/lineup에서 확인), version에 '16.18' 같은 짧은 문자열은 200 빈 응답. 기간 최대 3일(5·7·14일 빈 배열), 날짜는 CST, 수집기(05:00 KST=04:00 CST)에서는 etime=전날로 완결 데이터를 받는다. 国服大数据 랭킹(tft_hero_ranking·tft_equip_ranking)의 절대 수치와 hero_list, lol.qq가 주는 S/A/B/C 등급과 avg_rank_delta는 쓰지 않는다. 숫자는 전부 문자열, 편집 덱 detail은 제어문자 섞인 JSON 속 JSON(strict=False). 胜率阵容 목록은 당일·상세는 T-1이므로 각각 dtstatdate를 라벨로. 프록시 ACAO가 lol.qq.com이라 WebView 직접 호출은 불가하지만 수집기·네이티브는 무관.

[지표 정의 통일] 픽률=항목이 있는 보드/전체 보드(unit_s/total, Σplaces/games.count; lolchess pickRate 슬롯 점유율·metatft 전 랭크 분모 오차 11%는 쓰지 않음); 톱4·1위율=조건부(항목 보드 중); 아이템은 인스턴스 기준이라 보드 기준보다 높다는 도움말 한 줄; Δ는 같은 모집단의 가중 평균 대비(중국 플래+ 4.17~4.28 vs 글로벌 4.37이라 절대값 나란히 두면 착시); 등급은 표본 보정 후 수집기가 계산. 모든 통계 블록에 '패치(핫픽스 빌드) · 티어 · 서버 · n · 기준일 · 수집 시각'을 싣고, 최소 표본 아래 행은 흐리게.

[검증 게이트(verify.py 확장)] HTTP 200 빈 응답을 실패로(lol.qq 짧은 version·빈 req_params·limit 20000, metatft 잘못된 키, lolchess 파라미터 오류 모두 200 빈값); 소스별 최소 행수(편집 덱 20, group_list 300, lineup_rank 50, 클러스터 30, units 60, items 130); sum(places)=count; avg가 top1·top4 비율로 가능한 구간 안(이 검사가 lol.qq 不限 버킷을 100% 걸러냈다); 목록 가중 평균 4.5±0.05; DA 조인율(챔피언 90% 미만 실패); metatft filter_adjustment.override_applied=false; 원천 교차검증(metatft 마스터+ 챔피언 평균과 이전 수집 대비 차이 중앙값 0.1 초과 시 경고). 실패 시 기존 '수집 실패 이슈 등록' 흐름.

[운영 리스크] daily.yml은 등록만 되고 실행 이력 0회라 미국 러너 IP에서 mlol.qt.qq.com·api-hc.metatft.com 접근 가능 여부가 미확인이다(모든 심사위원 공통 미확인). 첫 cron 로그로 확인하고, 차단 시 self-hosted 러너나 국내 프록시 대안을 준비. 세 곳 모두 비공개 API라 스키마 변경·경로 폐기 위험이 있고(lol.qq tft_augment_rank 화이트리스트, metatft unit_detail2 같은 개정 흔적), 상세 호출량(lol.qq 425회/일 가능하지만 노출 덱만 ≤100회, metatft 유닛·아이템 상세 ≈140회/일)은 레이트리밋이 관찰되진 않았으나 유지 한도는 추정이다.

[개인정보] lol.qq fightdetail의 openid·login_ip·originalPuuid, 작가 QQ 번호, metatft 경기 JSON의 타인 puuid·riot_id·MMR은 저장·배포하지 않는다. 프로필은 기기 안에서만 처리하고 수집기에는 집계(컷·인원)만 넣는다.

[증거] 최종 심판 재확인 스크립트와 응답: C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_final/ (recheck.py, recheck_out.txt, qq_hero_rank_{4p,2p,7p,all}.json, qq_trait_rank_4p.json, qq_lineup_rank_7p.json, qq_group_list_tp2.json, qq_all_detail_top.json, mt_comps_stats_{glob_plat_d3,kr_plat_d3,kr_master_d3}.json, mt_units_{glob,kr}_plat_d3.json, mt_items_kr.json). 호출 시각 2026-09-15 22:14 KST, Chrome/120 UA, 쿠키·Referer 없음(metatft만 Referer 부착), 전부 HTTP 200·result 0. 저장소 코드는 읽기만 했다.

## 카테고리별 채점 상세

### 덱 리스트와 덱 상세

| 관점 | lol.qq | lolchess | metatft | 관점 1위 |
|---|---:|---:|---:|---|
| 데이터 품질 | 7.0 | 4.5 | 8.0 | metatft.com |
| 모바일 사용성 | 2.5 | 7.5 | 6.0 | lolchess.gg |
| 구현 가능성 | 8.5 | 4.0 | 7.0 | lol.qq.com/tft |

#### 데이터 품질

[채점 방식]
데이터 품질 4개 축으로 채점했다: 표본·티어 범위, 정확성, 패치 반영, 깊이·해석성. 2026-09-15 21:32~21:45 KST에 세 소스를 직접 호출했다. 원자료와 스크립트는 C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_deckdq/ 에 있다.

[1위 metatft 8.0]
결정적 차이는 세 가지다.
1) 필터가 실제로 먹는 덱 분포 API(comps_stats)가 있다. KR·티어·기간별로 1~8등 분포와 count를 준다.
2) 보드가 거의 전부 덱에 배정된다(미분류 0.05%). 덱 평균의 기준선이 4.50으로 치우치지 않는다.
3) lolchess와 교차검증해 정확성을 확인했다(마스터+ 유닛 평균 차이 중앙값 0.025).
감점 요인:
- 덱 상세(comp_details)와 comps_data가 필터를 무시하는 고정 모집단이고, 기간 표기가 없다.
- 증강 통계가 없다.
- 클러스터가 패치 43시간 뒤에 생성됐다.

[2위 lol.qq 7.0]
강점은 표본 규모(중국 실버+ 하루 약 150만), 당일 신선도, 그리고 유일한 덱별 증강 통계와 배치 칸 승률이다. 그러나 해석성 결함이 겹친다.
- 胜率阵容이 0.1 단위로 반올림되고, 평균 4.0 이하 덱만 노출되며, 생존 편향이 있다.
- 집계 기간 정의가 없고, 목록과 상세의 날짜·개수가 다르다.
- 증강은 상위 5개뿐이고 단계별 표본수가 없다.
- 고티어에서 소표본 덱이 S등급을 받는다.
- 조회 기간이 최대 3일이고, KR을 대표하지 않는다.

[3위 lolchess 4.5]
수치는 정확하고 일관된다. 그러나 이 카테고리 기능이 좁다.
- 마스터+·글로벌·2일 한 조합만 동작한다.
- 표본이 metatft의 40~60%다.
- 덱 상세 API가 null이고 증강이 없다.
- 분류되지 않은 보드가 빠져 목록 평균이 4.35로 치우친다.

[벤치마크 정정, 직접 확인]
- metatft 덱 표에서 필터가 적용되는 엔드포인트는 comps_stats다. comps_data와 comp_details는 queue 외 필터를 무시한다.
- metatft 클러스터 합계는 2,726,996이다. 벤치마크의 5,443,824는 이중 합산으로 보인다[추정].
- lol.qq 数据检索器는 version=''로 전 빌드를 합산할 수 있고, 기간 상한은 3일이다.

[앱 시사점]
- collector/fetch_decks.py는 지금 metatft를 latest_cluster_info로만 대조한다. comps_stats(rank/server 필터)를 쓰면 KR·티어별 표본수를 보여 줄 수 있다.
- comp_details 수치에는 '기간·티어 미표기 고정 집계'라는 라벨이 필요하다.

[추정으로 남긴 항목]
- 胜率阵容의 평균 4.0 상한이 의도된 필터인지
- 생존 편향이라는 해석
- 편집 덱 일괄 재게시라는 해석
- lolchess 로비 수 역산
- metatft 클러스터 생성 전 새 패치 덱의 부재

- **lol.qq.com/tft 7.0점** — 강점: - [확인] 중국 랭크 대표본에 티어 선택이 촘촘하다.
  - 数据检索器 tft_match_overview: 실버+ 하루(빌드 817) 1,497,681 플레이어-게임, 마스터+ 3일(version='' 전 빌드) 97,474. rank_dist 합이 total_games와 정확히 같다.
  - 티어 코드는 2~9 단일값과 '7+' 같은 범위를 받는다(page-datasearch.js 주석으로 확인).
  - 胜率阵容은 5개 버킷(255/0/1/2/3)이다. 골드~에메랄드(2)는 당일 45그룹·425조합, 조합 표본 중앙값 258.
- [확인] 세 사이트 중 유일하게 시즌 18 덱별 증강 통계가 있다. tft_lineup_all_detail의 augment_data가 DA_* 증강마다 use_num, avg_rank, 선택 단계 1/2/3별 평균을 준다(예: DA_LateGameScaling 2,021회, 3.185). metatft와 lolchess는 증강이 비어 있다.
- [확인] 상세에 실측 지표가 많다.
  - tft_lineup_position: 칸별 use_rate·win_rate
  - key_chess: 핵심 유닛 1/2/3성 비율과 평균 아이템 수(메인C 2성 66.8%, 아이템 2.89개, 소수 6자리)
  - level_data: 레벨별 분포
  - equip_data: 아이템별 추천 착용자와 use_num
  - more_lineup_data: 변형 덱
- [확인] 신선도가 좋다. 20:32 CST 조회에서 목록 dtstatdate가 당일(20260915)이었고 빌드 목록 end_time은 2026091518이다. 16.18 첫 빌드는 2026-09-10 07:00 CST에 시작했고 데이터가 빌드 단위로 나뉜다.
- [확인] tft_lineup_rank는 약한 덱까지 보여 준다(평균 7.46까지, 소수 3자리). top1_cnt·top4_cnt·total로 비율을 직접 계산할 수 있고, 개수 모순은 0건이다. / 약점: - [확인] 胜率阵容 목록의 정밀도가 낮고 편향이 있다.
  - avg_rank가 0.1 단위다(425개 중 367개가 소수 한 자리, 58개가 정수). avg_rank_diff는 177/425가 0이다.
  - 다섯 버킷 모두 avg_rank 최대가 4.00이라 약한 덱이 목록에 없다[의도된 필터로 추정].
  - 540회에 평균 1.5, top4 100%인 조합이 있다. 완성 보드로 덱을 정의해서 생기는 생존 편향이 크다[추정].
- [확인] 기간 정의가 없고 수치가 서로 맞지 않는다.
  - d_grouping_v3의 집계 기간이 어디에도 없다.
  - 목록은 20260915, 상세는 20260914다. 같은 상세 응답 안에서도 lineup_data use_num 17,059와 level_data 합 31,087이 다르다.
  - 증강은 상위 5개만 주고, 단계별 평균에 단계별 표본수가 없다(DA_SilverSpoon 1단계 평균 1.0).
  - tft_lineup_rank의 avg_rank_delta 기준값은 4.068인데 같은 조건의 전체 평균은 4.223이다. 정의가 없다.
- [확인] 고티어는 표본이 얇고 등급이 불안정하다.
  - 胜率阵容 마스터+(0) 당일은 10조합·1,052회뿐이고, 그중 9개는 증강이 비어 있다.
  - 数据检索器 마스터+ 3일 상위 100덱의 표본 중앙값은 94회다.
  - S등급 4개가 144~252회, 평균 1.57~1.84인 덱에 붙고, 3,139회로 가장 많이 쓰인 덱은 A다(표본 보정 없음).
- [확인] 조회 기간이 짧다. 5·7일은 빈 배열이라 최대 3일이다. version에 빌드를 지정하면 표본이 쪼개진다(마스터+ 3일: 44,323 대 version='' 97,474).
- [확인] 편집 덱 26개 중 21개는 update_time이 중국 16.18 시작(09-10 07:00) 이전이다(대부분 08-27). 그런데 전부 16.18로 표기돼 있고, 출력한 8건은 sub_time이 2026-09-14 11:24:14로 같다. 새 패치 검토 없이 일괄 재게시했을 수 있다[추정].
- 중국 서버 표본만 있고 KR 필터는 없다. 胜率阵容은 숫자 chessId라 정적 파일(한 패치 늦음)로 DA_*에 매핑해야 한다.
- **lolchess.gg 4.5점** — 강점: - [확인] 내부 일관성이 완벽하다. 22덱 모두 placements 합이 plays와 같고, 분포로 다시 계산한 평균·top4·1위율이 API 값과 일치한다.
- [확인] 정확성 교차검증: 마스터+ 챔피언 65명의 평균 등수가 metatft와 차이 중앙값 0.025로 일치한다.
- [확인] 덱 카드 정보가 잘 정리돼 있다.
  - 유닛별 coreRank(1~4 캐리 순위)와 추천 아이템 3개
  - 덱 안 유닛별 plays/wins/tops/8칸 분포(championStats)
  - 활성 특성 style/numUnits, 한국어 덱 이름
- [확인] 이전 패치 리비전을 조회할 수 있다(18.1d: 21덱, 225,842 plays).
- [확인] updatedAt 12:30:37Z로 30분 주기 갱신이 유지되고 있다. / 약점: - [확인] 조건이 한 조합만 동작한다. tierId=1(마스터+), shard=global, dt=3에서만 데이터가 온다. tierId 0/2/5, shard=kr, dt 1/7은 HTTP 200인데 186바이트 빈 응답이다. 골드·다이아 사용자용 덱 통계도, KR 단독 통계도 없다.
- [확인] 표본이 가장 작다.
  - 22덱 61,679 plays, 덱당 416~10,149.
  - pickRate로 역산한 로비 수는 9,331로, metatft 글로벌 마스터+ 2일 23,041게임의 약 40%다[역산이라 추정].
  - 챔피언 통계도 33,948경기로 metatft 56,296경기의 60.3%다.
- [확인] 이 카테고리의 상세 기능이 없다. /meta-decks/{key}는 metaDeck null이고(벤치마크 저장본), 증강은 [null,null,null]이다.
- [확인] 0으로 채운 필드가 있다. championTierStats(성급별)는 plays만 있고 wins/tops/placements가 전부 0이라 실제 값처럼 보인다.
- [확인] 목록 기준선이 치우친다. 분류되지 않은 보드가 빠져 22덱 가중 평균 등수가 4.35다(1등 칸 7,882 대 8등 칸 6,118). 평균 4.3짜리 덱도 실제로는 보통 수준이다.
- [확인] 단위가 섞였다. winRate·topRate는 퍼센트(22.889), pickRate는 로비당 개수(0.0965), avgPlacement는 문자열이다.
- 약관이 무단 재배포를 금지한다(벤치마크 확인). 데이터를 앱에 싣기 어렵다.
- **metatft.com 8.0점** — 강점: - [확인] 필터가 실제로 먹는 덱 분포 API는 tft-comps-api/comps_stats다. 벤치마크가 적은 comps_data가 아니다. 클러스터마다 places=[1~8등 수, count]를 준다.
  - 플래티넘+ 3일: 보드 6,788,792개 중 미분류(-1)는 3,111개(0.05%)뿐이다. 54개 덱의 가중 평균 등수가 4.4998이라 덱 비교 기준선이 치우치지 않는다.
  - 글로벌 마스터+ 3일: 267,920보드, 클러스터 중앙값 2,362.
  - KR 마스터+ 3일: 44,864보드, 중앙값 약 337.
  - 한국 서버·티어별 덱 표를 표본수와 함께 만들 수 있는 유일한 소스다.
- [확인] 정확성 교차검증: 마스터+ 챔피언 65명의 평균 등수를 lolchess와 대조했다. 차이 중앙값 0.025, p90 0.054이고 top4도 거의 같다. 원천 파이프라인을 신뢰할 수 있다.
- [확인] 원시 분포와 표본수를 준다(comps_stats places, stat-api places[8], explorer placement_count). 평균·톱4·1위율을 앱에서 같은 공식으로 다시 계산할 수 있다.
- [확인] comp_details가 가장 깊다. 거의 모든 수치에 count가 붙는다.
  - 유닛 성급별·아이템 개수별 평균과 count(예: 아무무 2성 274,767회 평균 3.196)
  - 3아이템 빌드 299개, 최종 레벨별 등수, 랭크 11단계별 평균·픽률
  - 카운터, 배치, 초반 보드, 리롤
- [확인] 요청마다 updated가 바뀌는 실시간 집계다. / 약점: - [확인] comps_data와 comp_details는 queue만 반영하고 rank/days/server/patch를 무시한다.
  - rank=IRON 1일, KR 마스터+ 1일, 18.1d 7일, 파라미터 없음이 모두 같은 값이었다(423009 = 556,572회, 평균 4.2225). queue=1160만 144,198로 바뀌었다.
  - comp_details는 KR 마스터+ 필터를 붙여도 응답이 322,469바이트로 똑같았다.
  - 번들도 이 두 URL에는 queue·세트(상세는 comp/cluster_id도)만 붙인다.
  - 그런데 filter_adjustment에는 요청한 랭크가 찍혀 와서, 필터가 적용된 것처럼 오해하기 쉽다.
- [확인] 같은 덱 ID에 모집단이 여러 개이고, 응답에 기간 표기가 없다.
  - 423009: comps_data 556,572회(4.2225) 대 comps_stats 플래티넘+ 3일 1,300,217회(4.2157).
  - comp_details 한 응답 안에서도 overall 556,572, trends 합 950,831, ranks 합 2,274,643이 서로 맞지 않는다.
  - trends에는 18.2 시작(2026-09-09T20:39Z) 이전 날짜인 09-08(18,239)과 09-09가 섞여 있다.
- [확인] 증강 통계가 없다. comp_details augments는 aug 빈값 한 줄뿐이고, 덱별 증강 티어는 수동 가이드다.
- [확인] 패치 반영이 늦다. 클러스터 423의 created_at이 2026-09-11T15:48Z로, 패치 시작 약 43시간 뒤다. 그 전에는 새 패치 덱이 목록에 없었을 것이다[추정]. 클러스터 ID도 재클러스터링 때마다 바뀐다.
- [확인] 좁히면 소표본이다. KR 마스터+ 3일은 54덱 중 11개가 100보드 미만(최소 14)이다.
- [확인] 같은 조건(플래티넘+ 3일)인데 합계가 엔드포인트마다 다르다: units games.count 5,701,392, sample_size 6,739,576, explorer 848,476게임.
- [추정] 벤치마크의 '클러스터 합계 5,443,824'는 현재 합계 2,726,996의 약 2배라 이중 합산으로 보인다.

가져올 아이디어:
- [lol.qq] 덱 상세에 '증강 성적' 섹션을 추가한다.
- 수집기가 tft_lineup_group_list(queue 1100, tier_part 2와 1)의 상위 조합마다 tft_lineup_all_detail을 호출한다.
- augment_data(rune_id DA_*, use_num, avg_rank, 1_/2_/3_avg_rank)를 decks.json에 넣는다.
- 단계별 표본수가 없으므로 단계별 평균은 use_num 300 이상일 때만 보이고, 나머지는 '표본 부족'으로 흐리게 표시한다.
- metatft와 lolchess에 없는 유일한 데이터다.
- [lol.qq] 편집 좌표 대신 실측 배치를 쓴다.
- tft_lineup_position의 content_pos_data에서 유닛마다 use_rate 1위 칸(x,y)과 그 칸의 win_rate를 보드에 겹쳐 표시한다.
- 편집 덱 좌표와 다르면 점으로 알린다.
- chess_id가 숫자이므로 정적 chess.js의 TFTID로 DA_*에 매핑한다.
- [lol.qq] 핵심 유닛 칩에 한 줄 요약을 붙인다.
- tft_lineup_key_chess의 1/2/3성 비율과 평균 아이템 수를 쓴다(예: '2성 67% · 아이템 2.9개').
- 리롤할지 레벨업할지 판단 근거를 한 손 화면에서 바로 준다.
- [lol.qq] 아이템 역검색에 중국 실측을 붙인다.
- all_detail의 equip_data(아이템별 추천 착용자 use_num·avg_rank)를 수집한다.
- 数据检索器 tft_equip_rank(ShowHero=1)의 'DA_챔피언#DA_아이템' 키도 수집한다.
- 역검색 결과의 덱 목록 옆에 '누가 들고 평균 몇 등인지'를 보여 준다.
- [lol.qq] 고티어 덱은 胜率阵容 tier_part=0을 쓰지 않는다(당일 10조합·1,052회뿐).
- 대신 数据检索器 tft_lineup_rank를 version=''(전 빌드), stime~etime 3일, tier '7+', limit 100으로 받는다. 표본이 빌드 지정 44,323에서 97,474로 늘어난다.
- 기준 평균은 tft_match_overview의 rank_dist로 직접 계산해 Δ를 우리 정의로 표시한다(lol.qq delta 기준값 4.068은 전체 평균 4.223과 다르다).
- [lol.qq 표본 필드] 등급은 수집기에서 다시 매긴다.
- comp_s·top1_cnt·top4_cnt를 그대로 받아 표본 보정 등급을 계산한다(예: 베이지안 평균 (n·avg + 200·4.5)/(n+200)).
- lol.qq S/A/B/C를 쓰면 144회·평균 1.57인 덱이 S가 되는 문제가 생긴다.
- 모든 덱 카드에 'n=… · 3일 · 마스터+' 라벨을 붙인다.
- [lol.qq 버전 목록] 편집 덱에 '이전 패치 작성' 배지를 단다.
- tft_recent_versions의 16.18 첫 빌드 시작 시각(2026091007 CST)과 편집 덱 update_time을 비교한다.
- 현재 26개 중 21개가 해당한다.
- 앱이 편집 덱을 기준 목록으로 쓰므로 신선도를 드러내는 것이 중요하다.
- [lol.qq 목록 구조] 덱 목록을 胜率阵容처럼 2단으로 접는다.
- '주 특성+메인C 그룹 → 변형 조합(more_lineup_data)' 구조를 쓴다.
- 그룹 합산으로 표본이 커져 신뢰도가 오른다.
- 폰에서는 그룹만 먼저 보이고 탭하면 변형이 펼쳐진다.
- [lolchess] 덱 카드 표현을 채택하되 데이터는 가져오지 않는다(약관).
- 표현: 유닛별 캐리 순위(coreRank 1~4), 캐리 유닛의 추천 아이템 3개, 8칸 등수 분포 막대, '최종 업데이트 N분 전'.
- 값은 직접 계산한다: metatft comps_stats places[1..8], comp_details builds/unit_stats(num_items), lol.qq main_c_chess_equip.
- [lolchess 일관성 기준] collector/verify.py에 배포 전 검사를 넣는다.
- sum(places)=count인지 확인한다.
- 분포로 다시 계산한 평균·top4가 표시값과 같은지 확인한다.
- 목록 전체 가중 평균 등수가 4.5±0.05인지 본다(lolchess의 4.35 같은 편향 감지).
- lolchess championTierStats처럼 전부 0인 필드는 뺀다.
- 원천 정확도는 수집 로그 안에서만 본다: lolchess 마스터+ 챔피언 평균과 metatft의 차이 중앙값(현재 0.025)이 0.1을 넘으면 경고하고, 재배포는 하지 않는다.

#### 모바일 사용성

[직접 확인한 방법]
- 세 사이트를 Android Chrome UA와 데스크톱 UA로 curl 호출해 viewport, SSR 모바일 플래그, CSS 미디어쿼리를 비교했다.
- lolchess와 metatft는 Browser pane의 375x812 모바일 에뮬레이션으로 목록, 스크롤, 상세 펼침을 스크린샷으로 봤다.
- lol.qq는 in-app 브라우저가 정책상 막혀 있어 index.html 템플릿, comm.css, 모바일 H5 상세 번들 문자열로만 판단했다. 그래서 lol.qq의 실제 폰 렌더링 모습은 추정이다.
- 실기기 테스트는 하지 않았다.
- 한국어 자연스러움은 lolchess i18n_ko.json과 metatft data_locales_ko_kr.json의 용어 빈도로 확인했다.
- 증거 파일: C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_mobile (lolchess_decks_m.html, lolqq_comm.css, lolqq_wrdetail.js, zh_labels.py)

[lolchess 1위 (7.5)]
- 서버가 모바일을 판별해 폰 레이아웃을 따로 준다.
- 모든 덱 카드 같은 자리에 평균 등수, 픽률, 승률, TOP 4 네 수치가 고정돼 한 손으로 훑으며 비교하기 가장 쉽다.
- 컨트롤이 드롭다운 3개와 검색창 하나로 단순하다.
- 한국어가 원어이고 용어가 통일돼 있다.
- 편집 덱 상세는 큰 팀 코드 복사 버튼, 화면 폭에 맞는 헥스 보드, Lv.5~10 탭으로 폰 사용성이 좋다.
- 감점: 통계 덱 상세가 307로 막혀 있다. 티어가 마스터+로 고정이다. 채팅 버튼이 수치를 가린다.

[metatft 2위 (6.0)]
- 카드 위계(큰 평균 등수, 운영·난이도 칩)와 필터·정렬·고정·숨김·밀도 설정은 셋 중 가장 풍부하다.
- 감점:
  - 상하단 고정 광고가 화면의 약 1/4~1/3을 가린다.
  - 상세 탭 10개가 3줄로 줄바꿈되고 내부 스크롤이 겹친다.
  - 영어 잔재('Avg Placement', 'Pro Augments')와 용어 혼재(선택률/선택율/픽률/플레이율)가 있다.
  - 카드 수치가 2개뿐이다.
- 기능은 많아도 한 손으로 한눈에 읽기에는 lolchess보다 불리하다.

[lol.qq 3위 (2.5)]
- 데이터는 모바일 UI에 쓰기 가장 좋다. 티어 구간, 변화량, 칸별 배치율, 단계별 증강 통계가 있다.
- 하지만 웹 목록은 1050px 고정 폭과 호버 기반의 데스크톱 전용이다.
- 모바일 상세 H5는 QR이나 掌盟 딥링크로만 들어갈 수 있다.
- 전부 중국어라 한국어 사용자의 폰 사용성은 매우 낮다.
- 이 카테고리 기능 자체는 있으므로 0점은 아니다.

[우리 앱에 주는 시사점]
- 현재 DeckCard와 상세에는 통계 숫자가 전혀 없다.
- 기준선: 1위 lolchess의 '카드 하단 고정 4수치 줄'과 '상세 맨 위 큰 덱 코드 버튼'.
- 여기에 lol.qq의 티어 구간·단계별 증강·배치 히트맵, metatft의 운영·난이도 칩·고정/숨김·레벨 도달률을 얹는 조합이 가장 효과적이라고 판단한다.
- 수집기가 하루 1회 모든 티어 구간을 미리 받아 두면, 필터 전환이 오프라인에서 즉시 되어 모바일 사용성이 크게 오른다.
- 피해야 할 metatft 패턴: 탭 줄바꿈, 내부 스크롤, 화면 고정 요소가 콘텐츠를 가리는 것.
- lolchess는 약관상 데이터 재배포 금지 조항이 있다(벤치마크 확인). 따라서 UI 패턴만 참고하고 데이터는 가져오지 않는 것이 맞다.

- **lol.qq.com/tft 2.5점** — 강점: [확인 번들 문자열] 별도의 모바일 H5 상세 페이지가 있다(act/a20200224tft/page/winLineup-detail/s18).
- viewport: width=1125 설계 폭에서 device-width로 전환
- 모듈: DarkMode·Skeleton
- 수치: 登场率 / 前四率 / 登顶率 / 平均排名
- 섹션: 关键棋子数据, 关键棋子出装推荐, 关键装备佩戴推荐, 运营节奏建议, 仙灵详情
- '应用阵容' 버튼이 팀 코드를 복사하고 '게임에서 붙여넣으라'는 토스트를 띄운다. 위챗·QQ·掌盟 공유도 된다.

[벤치마크 확인] 데이터 구조는 모바일 UI에 쓰기 좋다. 티어 구간(tier_part), 특성 동시 필터, 변화량(排名提升·use_rate_diff), 칸별 배치율, 증강 선택 단계별 평균 순위가 있다. / 약점: [확인 CSS·템플릿 분석] 주 사이트(#/index, #/wrlineup)는 데스크톱 전용으로 만들어져 있다.
- comm.css(609KB)의 미디어쿼리는 max-width 1400~2500px, min-width 1920px, max-height 848/900뿐이다.
- 고정 width:1050px 규칙이 있다.
- 템플릿에 @mouseenter 26개와 @mouseleave 24개가 있고, 툴팁과 모바일 상세 QR이 마우스 호버에 묶여 있다.

[추정] 폰에서는 축소 표시되거나 가로 스크롤이 생기고, 호버 정보는 터치로 볼 수 없다.

[확인] 모바일 목록 페이지는 찾지 못했다.
- 추측한 H5 경로 6개가 모두 404였다(존재하지 않는다는 증명은 아님).
- 헤더의 모바일 메뉴(위챗 미니프로그램·掌盟 QR)는 주석 처리돼 있다.
- 모바일 상세로 가는 길은 QR이나 딥링크뿐이다.

[확인] 중국어 전용이라 한국어 사용자에게는 자연스러움이 0이다. 一键应用은 QQ 로그인이 필요하다.

한계: in-app 브라우저가 정책상 막혀 있어 실제 렌더링은 보지 못했다.
- **lolchess.gg 7.5점** — 강점: [확인] 폰 전용 렌더링을 한다. 같은 /decks 주소도 Android UA로 받으면 __NEXT_DATA__에 "isMobile":true가 들어가고, 데스크톱 UA면 false다. CSS에는 576/768/992/1200px 반응형 구간이 있다.

[확인] 375x812 에뮬레이션 스크린샷
- 컨트롤: '최근 2일 (18.2) / 평균 등수 / 마스터+' 드롭다운 3개가 한 줄에 들어간다. 그 아래 '챔피언, 시너지 검색' 입력칸과 '최종 업데이트: 31분 전'이 있다.
- 덱 카드 구성: S 배지, 한글 덱 이름('[상징] 요정 트리스타나', '고밸류 드레이븐 이즈리얼'), 특성 아이콘 줄, 별과 아이템이 달린 챔피언 9~10명.
- 모든 카드 같은 자리에 수치 4개가 한 줄로 고정된다. 예: 평균 등수 #3.95 / 픽률 0.10 / 승률 22.9% / TOP 4 59.2%. 세로로 훑으며 비교하기 가장 쉽다.
- 한 화면에 카드가 약 3개 보인다.

[확인] 정렬·필터 (i18n 키 기준)
- 정렬: 평균 등수 / 승률 / 픽률 / TOP 4
- 기간: 최근 2/3/7일 또는 패치
- 필터: 챔피언·시너지·아이템

[확인] 한국어가 원어이고 용어가 통일돼 있다. i18n_ko 6,497개 항목에서 '평균 등수' 37회, '평균 순위' 0회, '선택률' 0회다. 시너지·증강체·고밸류·팀 코드 같은 롤체 커뮤니티 용어를 그대로 쓴다.

[확인] 편집 덱 상세(/builder/guide/{key})도 모바일에 맞다.
- 맨 위에 '팀 코드 복사하기' 큰 버튼
- 헥스 보드가 375px 폭에 딱 맞음
- Lv.5~Lv.10 세그먼트 탭
- 증강체 아이콘, 아이템별 추천 착용자 목록, 공유하기 / 약점: [확인] 통계 덱에서 상세로 들어갈 수 없다. /decks/{key}가 307로 /decks에 되돌아간다. 폰에서 메타 덱 카드를 눌러도 그 덱의 아이템·배치·증강 통계를 볼 수 없다. 상세가 있는 쪽은 통계가 없는 편집 덱뿐이다.

[벤치마크 확인] 티어는 사실상 마스터+ 하나다(tierId≠1이면 빈 응답). 골드~다이아 구간 한국 유저가 자기 구간 덱을 고를 수 없다.

[확인 스크린샷]
- 첫 화면은 헤더, 메뉴, 동영상 광고, 탭이 차지하고 첫 카드는 화면 하단 약 15% 지점에서 시작한다.
- 떠 있는 채팅 버튼이 카드 오른쪽 아래 TOP 4 숫자를 가린다.

[추정] 초상화 아래 아이템 아이콘이 매우 작아 한눈에 읽기 어렵다.

[벤치마크 확인] 시즌 18 증강 통계가 없다.
- **metatft.com 6.0점** — 강점: [확인] 375x812 스크린샷 기준
- 언어: 한국어(KO)가 자동 선택된다. 덱 이름('검은 가시 말파이트', '달빛 아펠리오스 니달리')과 초상화 아래 유닛 이름이 한글이다.
- 카드 위계가 셋 중 가장 명확하다. S 배지, 운영 방식 칩(표준 / 빠른 8레벨 / 빠른 9레벨), 난이도 칩(보통 / 어려움)이 있고, 오른쪽에 큰 '4.17 평균 등수'와 '0.04 선택률'이 놓인다.
- 필터 칩: 랭크 / 18.2 / 지난 3일 / 플래티넘+, '+' 고급 필터, '상황별' 칩, '덱 필터' 검색.
- 목록 도구: 고정·숨김 아이콘, 설정 버튼.
- 상단에 '최근 업데이트 2분 전 / 분석된 덱 6,787,200' 표본 규모를 보여 준다.

[확인 번들] 정렬 옵션 7개가 번역돼 있다: 평균 등수, 선택률, 승률, 순방 확률, 순방덱 중 점유율, 승리 점유, 평균 등수 변화. 설정에는 압축 모드(특성 없음), 유닛 이름 표시, 캐리 우선순위 정렬, 색맹 모드가 있다.

[확인] 카드를 누르면 제자리에서 상세가 펼쳐진다. 레벨 탭에 도달 비율이 붙는다(레벨 8 46.6% / 레벨 9 46.5%). 레벨별 보드와 라운드 승률도 보여 준다. 필터 정밀도는 셋 중 최고다. / 약점: [확인 스크린샷]
- 광고: 스크롤해도 상단 동영상 광고와 하단 배너 광고가 계속 붙어 있다. 헤더까지 합치면 812px 화면의 약 1/4~1/3을 가려, 실제로 보이는 카드는 약 2개다.
- 상세 탭: 탭 10개가 3줄로 줄바꿈된다. 선택지 / 빠른 시작 / 유닛 / 아이템 / 특성 / 통계 / Pro Augments / Pro Tips/Gods / 다시 보기 / 카운터이고, 일부는 영어가 남아 있다.
- 탭 안에 내부 스크롤 영역이 또 있어서 한 손 스크롤과 충돌한다.
- 카드 수치는 평균 등수와 선택률 2개뿐이다(톱4·승률 없음).
- 정렬 드롭다운 첫 렌더에 'Avg Placement'가 영어로 노출됐다.

[확인 ko_kr 로케일] 용어가 섞여 있다.
- '선택률' 4회, '선택율' 1회, '픽률' 4회, '플레이율' 7회
- '평균 등수' 13회, '평균 순위' 13회
- 더블 업을 '두 명이서 한 조'로 옮기는 등 번역투가 있다.

[관찰·추정] 카드를 펼친 뒤 스크린샷 렌더링이 두 번 시간 초과됐다. 페이지가 무거운 것으로 보인다.

[벤치마크 확인] 증강 통계는 수동 티어뿐이다.

가져올 아이디어:
- [metatft] 덱 카드 정보 위계를 가져온다.
- 카드 오른쪽에 큰 숫자 하나(평균 등수)와 작은 보조 수치 하나를 고정한다.
- 이름 아래에 운영 방식 칩(표준 / 빠른 8레벨 / 리롤)과 난이도 칩(보통 / 어려움)을 붙인다.
- 데이터: metatft comps_data의 levelling, difficulty, diff_pick, diff_place. 수집기가 이미 만드는 metatft.matchedComp 유사도 매칭으로 덱에 연결한다.
- 중국 한정 덱은 매칭이 없으므로 lol.qq 胜率阵容 avg_rank로 채운다.
- 현재 Components.kt의 DeckCard에는 티어, 이름, 중국 한정, 레벨, 특성 4개, UnitGrid만 있고 통계 숫자가 전혀 없다.
- [metatft] 목록 고정·숨김을 넣는다. 카드 오른쪽 위 핀·눈 아이콘(또는 길게 누르기)으로 덱을 맨 위에 고정하거나 목록에서 숨긴다. 덱 id를 기기 로컬(DataStore)에 저장한다. 지금의 pinDeck은 오버레이에 띄울 덱 1개만 기억한다.
- [metatft] SettingsScreen에 밀도 토글 3개를 둔다: 압축 모드(특성 줄 숨김), 유닛 이름 표시, 캐리 우선 정렬. 한 화면 카드 수를 사용자가 조절해 한 손 스크롤을 줄인다. 카드 수가 얼마나 늘지는 추정이다.
- [metatft] 상세 '배치'의 레벨 칩에 도달 비율을 붙인다(예: '8렙 47% · 9렙 47%'). metatft 화면은 '레벨 8 46.6% / 레벨 9 46.5%'로 보여 준다. 수집기가 매칭된 클러스터의 comp_details.final_levels count 비율을 계산해 decks.json에 넣는다.
- [metatft] 목록 상단 FeedBanner에 표본 규모와 갱신 시각을 함께 보여 준다(예: '분석 N게임 · N시간 전'). 값은 lol.qq tft_match_overview의 total_games나 胜率阵容 use_num 합으로 채운다. 하루 1회 수집이라 '언제 것인지'가 특히 중요하다.
- [metatft] 아이템 도우미를 만든다. 가진 재료나 완성템을 칩으로 고르면 그 아이템을 캐리가 쓰는 덱이 위로 올라간다.
- 데이터: decks.json units[].items(DA_*), metatft unit_items_processed.
- 앱의 아이템 역검색 요구와 같은 화면으로 합칠 수 있다.
- [lol.qq] 티어 구간 세그먼트를 넣는다: 전체 / 마스터+ / 다이아+ / 골드~에메랄드 / 골드 이하.
- 원천: 胜率阵容 tier_part 255/0/1/2/3.
- 수집기가 하루 1회 다섯 구간을 모두 받아 JSON에 담으면 폰에서는 네트워크 없이 바로 전환된다.
- lolchess는 사실상 마스터+ 고정이라 이 기능이 없다.
- [lol.qq] 수치 옆에 변화 화살표(▲▼)를 달고 '떠오르는 덱' 정렬을 추가한다. 데이터는 avg_rank_diff(排名提升), use_rate_diff, top_4_rate_diff다. 작은 화면에서도 추세가 한 글자로 읽힌다.
- [lol.qq] HexBoard에 배치 히트맵을 겹친다.
- tft_lineup_position의 칸별 use_rate를 각 칸의 진하기나 작은 %로 표시한다.
- 글을 읽지 않고도 어디에 둘지 알 수 있다.
- 숫자 chess_id는 정적 chess.js로 DA_*에 매핑한다.
- [lol.qq] 증강 선택 단계 탭(2-1 / 3-2 / 4-2)을 만든다.
- tft_lineup_all_detail augment_data의 1_/2_/3_avg_rank로 단계별 상위 증강 3개를 보여 준다.
- rune_id가 DA_*라서 catalog 한글명에 바로 붙는다.
- metatft와 lolchess에는 시즌 18 증강 통계가 없어 lol.qq만 줄 수 있다. 오버레이에서 증강을 고르는 순간에 가장 쓸모 있다.
- [lol.qq] 핵심 유닛 요약 칩을 단다. tft_lineup_key_chess의 1/2/3_star_percent와 equip_num으로 '○○ 3성 NN% · 아이템 N.N개' 형식의 한 줄을 만든다. 리롤 덱에서 3성이 얼마나 필요한지 바로 보인다.
- [lol.qq] 재료별 추천 착용자를 붙인다. tft_key_equip_recommend_chess로 DeckDetailScreen '조합 재료 우선순위'의 각 재료 칩 옆에 착용자 초상 1~2개를 둔다. 캐러셀이나 전리품에서 재료를 얻었을 때 바로 판단할 수 있다.
- [lol.qq] FilterBar에 주 특성 칩을 추가한다. 阵容·胜率阵容의 특질·직업 동시 필터를 가져와 가로 스크롤 칩 줄에 DA_* trait id 기반 칩으로 넣는다. 지금은 중국 한정, 티어, 레벨 칩만 있다.

#### 구현 가능성

[검증 방법]
- 2026-09-15 12:33~12:45 UTC(21:33~21:45 KST)에 python urllib와 Chrome/120 UA로 직접 호출했다.
- 원문 응답·스크립트·출력은 C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_deck/ 에 있다(jd_qq.py, jd_qq_map.py, jd_qq_stab.py, jd_lc.py, jd_lc_map.py, jd_mt.py, jd_mt_items.py, jd_mt_filters.py, *_out.txt).
- DA_* 결합성은 9/14에 받은 CommunityDragon ko_kr.json의 apiName 5,436개와 대조했다.

[lol.qq 재확인]
1) GET game.gtimg.cn/.../lineupJson/s18/6/lineup_detail_total.json
- 200, 605,630B, max-age=120, ACAO *, Last-Modified 2026-09-14 06:50 GMT.
- 26덱, detail 파싱 실패 0, DA_* 351개 중 340개 일치.
- 같은 경로의 s13(LM 2025-03-20, 28덱)과 s17(LM 2026-08-20, 32덱)도 200이고 키 구성이 같다. 단 hero_id는 s13·s17이 숫자이고 s18부터 DA_*다.

2) POST mlol.qt.qq.com/go/exploit/proxy (쿠키·서명·Referer·Origin 없음)
- tft_lineup_group_list: result 0, 754KB, 0.36초, dtstatdate 20260915, 45그룹 425조합. tier_part 0은 10조합, 255는 479조합.
- 조합 안의 챔피언·특성은 숫자 ID이고, 장비·증강은 DA_*로 164/164 일치한다.
- versionconfig.json이 가리키는 16.17-2026.S18/chess.js·race.js·job.js로 chessId 65/65, traitId 35/35가 매핑된다.
- 사용 수 1위 조합(use_num 17059)으로 호출한 결과는 모두 result 0이지만 dtstatdate는 20260914(T-1)였다.
  - all_detail: 21.9KB. 증강 5개에 1/2/3단계 평균 등수가 있다.
  - position: 281KB. x,y별 사용률·승률이 있다.
  - key_chess_equip: 26.6KB.

3) 数据检索器
- tft_recent_versions: 16.18 빌드 두 개의 기간이 겹친다.
- tft_match_overview(9/14, 티어 2+, 빌드 817.4437): total_games 1,275,815.
- tft_lineup_rank: 100행. DA_* 136/136 일치. 덱 id의 base64를 풀면 구성 문자열이다.

[lolchess 재확인]
1) GET tft.dakgg.io/api/v1/meta-decks?shard=global&queueId=1100&dt=3&tierId=1&hl=ko&from=web
- 200, 514KB, 0.11초, CloudFront, max-age=60.
- updatedAt 12:30:37 UTC, 22덱, plays 61,679, DA_* 135/135 일치, augments는 모두 null.

2) 파라미터 변형 4개(dt 누락, tierId=0, tierId=2, shard=kr) 모두 200·186B·0덱이었다. 덱 상세 /meta-decks/{key}와 {deckKey}는 metaDeck null이었다.

3) 기타
- guide-decks: 자체 키를 쓰며, /data/*로 챔피언 65/65, 아이템 48/48이 변환된다.
- 약관 페이지에서 복제·제3자 제공 금지 조항을 확인했다.

[metatft 재확인]
1) latest_cluster_id는 423이다(9/14 수집기 기록과 같음). comps_data는 200, 242KB, 1.17초, Cache-Control 없음, 54클러스터, DA_* 205/206 일치.

2) 필터 검증: days=1, 마스터+ 보정 off, IRON 단독, server=KR 네 요청 모두 클러스터 합계 2,726,996, 423009는 556,572보드·4.2225로 같았다. sample_size만 6,734,344에서 2,192,608로 바뀌었다.
- 벤치마크의 '필터 적용 확인'은 comps_data에는 성립하지 않는다.
- 클러스터 합계도 벤치마크의 5,443,824가 아니라 2,726,996으로 측정됐다.

3) comp_details(423009): 200, 322KB. positioning, unit_stats, itemNames, counters가 있고 augments는 빈 한 줄이다. 없는 클러스터 id는 HTTP 500이다. unit_items_processed는 200, 68KB, region_hint 'vn2'.

[운영 상태]
- 워크플로는 active(2026-09-14T22:30Z 등록)지만 실행 이력이 0회다. 첫 cron은 2026-09-15 20:00 UTC로 아직 오지 않았다.
- 로컬 실행(generatedAt 2026-09-14T14:30:44Z)에서는 lol.qq와 metatft 모두 ok였다.
- 따라서 세 소스 모두 GitHub 러너(미국 IP)에서 접근되는지는 확인하지 못했다.

[채점 논리]
- lolqq 8.5: 카테고리 요소를 모두 인증 없이 받는 것을 확인했다. 정적 경로가 1.5년 넘게 유지됐고, 숫자 ID도 공식 사전으로 100% DA_* 변환된다. 감점 이유는 다음과 같다.
  - ID 체계가 섞여 있고 시즌 간 hero_id 형식이 바뀌었다.
  - 빌드 문자열·CST·T-1을 처리해야 한다.
  - 비공개 게이트웨이다.
- metatft 7: GET·DA_* 단일 체계·깊은 상세로 가장 단순하다. 감점 이유는 필터가 조용히 무시되고, 증강이 비어 있고, 클러스터 id가 바뀌고, 캐시 없이 오리진이 집계한다는 점이다.
- lolchess 4: 기술 난도는 가장 낮다. 그러나 약관이 재배포를 명시적으로 금지하고, 상세 엔드포인트가 null이며, 파라미터 변형이 조용히 빈 응답을 주고, 표본이 작고 증강이 없다.

[추정으로 남긴 것]
- 대량 수집 시 레이트리밋(이번 호출량에서는 세 곳 모두 관찰되지 않음)
- 해외 IP 차단 여부
- 정적 사전의 패치 지연으로 인한 매핑 누락 가능성
- tft_augment_rank 화이트리스트(벤치마크 주장)

- **lol.qq.com/tft 8.5점** — 강점: [직접 확인] 이 카테고리의 요소(덱 목록, 등급, 보드 배치, 아이템, 증강, 평균 등수·톱4·픽률, 배치 통계)를 전부 인증 없이 받을 수 있다.
(1) 정적 편집 덱 lineup_detail_total.json: 200, 605KB, CORS *, max-age 120. 26덱 모두 detail 파싱에 성공했다(strict=False). 좌표·별·아이템·hexbuff 증강·quality 등급이 들어 있다. 같은 경로가 s13(2025-03), s17, s18에서 모두 살아 있고 lineup_list 키 구성이 s13과 s18에서 같다. 경로가 1.5년 넘게 유지됐다는 증거다. 지금 수집기가 이미 쓰고 있다.
(2) 통계 게이트웨이 POST mlol.qt.qq.com/go/exploit/proxy: 쿠키·서명·Referer·Origin 없이 result 0이다.
- group_list: 당일(20260915) 425조합. tier_part 0/2/255 모두 동작한다.
- all_detail: 증강별 1/2/3단계 평균 등수, 성급 비율.
- position: x,y별 사용률·승률.
- key_chess_equip: 200.
(3) 数据检索器 tft_lineup_rank: chess_ids·traits·main_c_chess_id가 DA_* 원문이라 CDragon과 136/136 일치한다. 덱 id가 구성을 base64로 인코딩한 값이라('1100#DA_Riftbeast18$DA_Draven18;DA_18_ElderDragon$8') 날짜가 바뀌어도 대조 키로 쓸 수 있다.
(4) 승률덱의 숫자 ID는 versionconfig.json이 가리키는 chess.js·race.js·job.js로 챔피언 65/65, 특성 35/35가 DA_*로 변환된다.
'중국 한정 덱' 판정의 원천이라 앱 목표와 바로 연결된다. / 약점: [직접 확인]
- ID 체계가 엔드포인트마다 다르다. 승률덱은 숫자 chessId/traitId이고, 아이템·증강만 DA_*다.
- 편집 덱 hero_id 형식이 시즌마다 바뀌었다. s13은 '10222', s17은 '100252'(숫자)였고 s18부터 DA_*다. 수집기가 두 형식을 모두 처리해야 한다.
- 변환 사전 경로가 16.17-2026.S18이라 라이브 16.18보다 한 패치 늦다. 패치 중 신규 유닛이 나오면 매핑이 빠질 수 있다(추정).
- 数据检索器는 전체 빌드 문자열을 요구한다. 16.18 빌드 두 개(817.4437, 816.5012)의 기간이 겹쳐 빌드 선택·합산 로직이 필요하다. 날짜 경계는 CST다.
- 목록은 당일(20260915)인데 상세는 T-1(20260914)이다.
- 숫자가 모두 문자열이고, detail은 제어문자가 섞인 JSON 속 JSON이다.
- 편집 덱 ID 351개 중 11개가 CDragon에 없다(DA_Reinfourcement, 소환물 등).
- tier_part 0(마스터+)은 10조합뿐이다.
- 비공개 게이트웨이다. tft_augment_rank가 화이트리스트로 막힌 선례가 있다(벤치마크 주장, 재확인 안 함).
- GitHub Actions 실행 이력이 0회라 미국 러너 IP에서 접근되는지는 확인하지 못했다.
- **lolchess.gg 4.0점** — 강점: [직접 확인] 기술적으로는 가장 쉽다.
- GET meta-decks: 200, 514KB, 0.11초(CloudFront, max-age 60, ACAO *).
- 22덱의 챔피언·아이템 키가 DA_*이고 CDragon과 135/135 일치한다.
- 한 번의 호출로 다음을 준다.
  - 한글 덱 이름(deckNameKo)
  - 캐리 순위 coreRank 1~4, 핵심 유닛별 추천 아이템 3개
  - 8칸 등수 분포
  - 챔피언 성급 분포(championTierStats)
- 편집 덱(guide-decks 30개)은 자체 키를 쓰지만 /data/champions·/data/items로 챔피언 65/65, 아이템 48/48이 ingameKey로 변환된다(중복 매핑 0). / 약점: [직접 확인]
- 약관과 충돌한다. lolchess.gg/about/terms_and_service에 사전 승낙 없는 복제·제3자 제공 금지, 취득 정보의 가공·판매 등 상업적 사용 금지 조항이 있다. 공개 GitHub 저장소 JSON으로 재배포하는 우리 구조와 정면으로 부딪혀 차단·삭제 요청 위험이 세 곳 중 가장 크다.
- 조용한 실패: dt 누락, tierId=0, tierId=2, shard=kr 네 변형 모두 HTTP 200에 186바이트 빈 목록이 온다. 글로벌 마스터+ 하나만 동작해 티어·지역을 나눌 수 없다.
- 덱 상세 /meta-decks/{key}와 {deckKey}는 200이지만 metaDeck이 null이다. 배치·증강 같은 상세를 만들 수 없다.
- 22덱 모두 augments가 null이다.
- 표본이 작다(전체 61,679판, 1위 덱 900판).
- 편집 덱은 변환 테이블에 의존하고, 아이템 ingameKey에 TFT11_Item_* 형식이 섞여 있다.
- 중국 데이터와 관계가 없어 앱 목표에 대한 기여가 낮다.
- **metatft.com 7.0점** — 강점: [직접 확인]
- GET만 쓰고 인증·쿠키가 필요 없다(ACAO *).
- comps_data: 200, 242KB, 1.17초. 54개 클러스터의 units_string·builds·name이 DA_*라 CDragon과 205/206 일치한다(빠진 것은 TFT18_MasterYi 하나).
- comp_details?comp=423009&cluster_id=423: 322KB, 1.08초. 들어 있는 것은 다음과 같다.
  - 배치 칸 빈도(positioning cell_N)
  - 유닛 성급별 평균 등수·비율(unit_stats.tiers, 65유닛)과 아이템 개수별 성과
  - 아이템→착용 유닛(itemNames.units)
  - counters, final_levels, 랭크별 성과
- 표본이 크다(sample_size 6,734,344, 1위 덱 556,572보드).
- unit_items_processed(68KB, 0.94초) 한 번이면 아이템→상위 유닛, 유닛→상위 아이템을 받는다.
- latest_cluster_id 423은 9/14 수집기 기록과 같다. 수집기가 이미 대조용으로 쓰고 있어 결합 경로가 검증돼 있다. / 약점: [직접 확인]
- comps_data는 rank·days·server 필터를 조용히 무시한다. 다음 네 요청의 클러스터 합계가 모두 2,726,996, 423009가 556,572보드·4.2225로 기본값과 완전히 같았고, filter_adjustment.sample_size만 달라졌다.
  - days=1 플래티넘+
  - 마스터+ (보정 off)
  - IRON 단독
  - server=KR
  따라서 티어별·기간별 덱 통계를 이 엔드포인트로는 만들 수 없고, 표본 수 필드를 그대로 표시하면 틀린다. 벤치마크의 '필터 적용 확인'은 comps_data에는 해당하지 않는다.
- 증강 통계가 없다(augments가 aug '' 한 줄뿐).
- 클러스터 id가 재클러스터링 때 바뀐다. 없는 id는 HTTP 500이라 id를 저장해 쓸 수 없고 units_string 집합으로 대조해야 한다.
- Cache-Control이 없고 매 요청 1초대로 오리진이 집계한다. 대량 수집하면 제한이 걸릴 수 있다(추정).
- 특성 키에 '_1' 같은 단계 접미사가 붙는다.
- unit_items_processed에 IP 기반 region_hint가 있다. KR에서 호출했는데 'vn2'였다. 수집 위치에 따라 응답이 달라질 수 있다(지금은 adjustment_applied false).
- 서버가 S~D 티어를 주지 않는다.
- 글로벌 데이터라 중국 기준 앱의 주 소스는 될 수 없다.

가져올 아이디어:
- [metatft] 덱 상세에 '글로벌 비교' 한 줄: 수집기가 이미 units_string으로 매칭하는 클러스터의 comps_data overall(count·avg)과 levelling('Fast 9'/'Fast 8')을 decks.json에 넣어, lol.qq 평균 등수 옆에 '글로벌 4.22등 · 9레벨 빠르게'로 보여준다. levelling은 구조화된 영어 라벨이라 lol.qq line_feature(중국어 자유문)보다 한글화하기 쉽다. comps_data가 필터를 무시하는 것을 확인했으므로 티어·기간 라벨은 붙이지 않는다.
- [metatft] 성급 필요도 칩: comp_details.unit_stats[].tiers의 성급별 평균 등수를 쓴다. 예를 들어 423009 덱의 DA_Amumu18은 1성 5.21등(42%), 2성 3.20등(58%)이므로 '2성 필수'로 표시한다. lol.qq all_detail.key_champion_data는 이번에 본 필드 범위에서 1/2/3성 비율만 있어서, 성급별 평균 등수만 metatft로 보강한다.
- [metatft] 배치 비교 토글: comp_details.positioning.positions(cell_N 빈도, 28칸으로 추정)를 lol.qq tft_lineup_position(x,y 사용률·승률)과 같은 4x7 보드 좌표로 맞춘다. 덱 상세에 보드 한 장과 '중국/글로벌' 토글 하나만 둬서 한 손으로 쓸 수 있게 한다.
- [metatft] 카운터 섹션: comp_details.counters의 place_change로 '이 덱이 불리한 상대 3개'를 보여준다. 대상은 metatft와 매칭된 덱만이고 units_string 집합 대조로 연결한다. 표시 이름은 우리 한글 덱 이름으로 바꾼다.
- [metatft] 아이템 역검색 보조 인덱스: unit_items_processed(68KB, 한 번 호출)를 수집기에서 줄여 '아이템→상위 착용 유닛 5개와 평균 등수'를 만든다. lol.qq tft_lineup_key_chess_equip(덱 내 아이템 순위)와 함께 아이템 검색 결과를 '중국 덱 / 글로벌 착용 유닛' 두 줄로 보여준다. IP 기반 region_hint가 있으니 수집 로그에 region_patch_adjustment를 함께 남긴다.
- [lolchess, 명명 규칙만] 한글 덱 이름 형식 차용: '[상징] 요정 트리스타나', '고밸류 드레이븐 이즈리얼'처럼 '[태그] 핵심 특성 + 캐리' 규칙을 korean_deck_name에 적용한다. 약관 때문에 데이터는 수집하지 않고 규칙만 가져온다.
- [lolchess, UI만] 캐리 순위 강조: coreRank 1~4 방식처럼 캐리 순서대로 유닛 3명까지만 아이템 3개를 크게 보여주고, 나머지 유닛은 아이콘만 둔다. lol.qq 승률덱에 main_c_chess, assist_chess, second_assist_chess와 각 아이템 필드가 이미 있어 그대로 매핑된다.
- [lolchess에서 확인한 함정을 수집기 방어로] HTTP 200 빈 응답을 실패로 처리한다. verify.py에 소스별 최소 건수를 넣는다(lol.qq 편집 덱 20 이상, group_list 조합 300 이상, lineup_rank 행 50 이상, metatft 클러스터 30 이상). metatft는 sample_size와 클러스터 합계를 따로 검사해, 스키마나 필터 동작이 조용히 바뀌면 기존 '수집 실패' 이슈 등록이 동작하게 한다.

### 챔피언과 특성

| 관점 | lol.qq | lolchess | metatft | 관점 1위 |
|---|---:|---:|---:|---|
| 데이터 품질 | 5.0 | 7.5 | 8.5 | metatft.com |
| 모바일 사용성 | 2.0 | 6.0 | 7.5 | metatft.com |
| 구현 가능성 | 5.0 | 6.0 | 8.0 | metatft.com |

#### 데이터 품질

[조사 방식] 벤치마크 결과를 그대로 믿지 않고, 2026-09-15 12:29~12:50 UTC에 세 사이트 API를 python urllib로 직접 호출해 네 가지를 검증했다.
(1) 표본 크기와 티어 범위
(2) 평균 등수가 1등·톱4 비율로 가능한 범위 안인지(내부 일관성)
(3) 같은 티어·패치에서 소스끼리 맞는지
(4) 챔피언·특성 사전을 CommunityDragon 16.18.8175716 빌드와 대조
스크립트와 출력은 C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_champ_quality (r1.py~r4.py, *_out.txt)에 있다.

[metatft 8.5, 1위]
- 표본이 lolchess 대비 플래티넘+ 4.6배, 마스터+ 1.7배로 가장 크다.
- 랭크를 하나씩 조합하고 KR 서버만 따로 볼 수 있다. 한국어 사용자 앱에 가장 맞는 범위다.
- 거의 실시간이고, 원시 등수 분포를 줘서 앱이 같은 공식으로 다시 계산할 수 있다.
- lolchess와의 상관계수 0.995(특성 0.998)로 정확성도 교차검증됐다.
- 감점: 표본 수(분모)가 엔드포인트·시점마다 다르다(5.71M 대 6.75M, 전 랭크 약 11% 오차). 필터가 자동 완화될 수 있고, 사전 수치 일부가 CommunityDragon 16.18과 다르다.

[lolchess 7.5]
- 원시 카운트가 정확히 맞고 특성 키가 인원수라 해석성은 가장 좋다. 정확성은 metatft와 같은 수준으로 검증됐다.
- 감점:
  - 글로벌만 있고 KR 필터가 없다.
  - 표본이 작고 갱신은 약 1시간 주기다.
  - pickRate가 슬롯 점유율이라 오해하기 쉽다.
  - Eclipse·Rival 단계 행이 누락됐고, 사전 16곳이 CommunityDragon과 다르다.
- metatft와 1점 차이는 표본 크기·지역·티어 세분화·속도 차이다.

[lol.qq 5.0]
- 중국 서버 표본은 대체할 수 없는 가치다. 数据检索器를 티어 필터와 함께 쓰면 카운트가 있고, 범위 위반 0, 전역과 상관계수 0.91~0.95로 쓸 만하다.
- 16.18 정적 수치는 CommunityDragon과 완전히 일치한다.
- 그러나 직접 확인한 결함이 심각하다.
  - 数据检索器 '不限' 버킷은 모든 행이 수학적으로 불가능한 값이다.
  - 사이트 기본 화면인 国服大数据 랭킹 API는 모집단 합계가 불가능한 값(가중 평균 3.53, 톱4 0.675)이라 절대 수치를 등수로 읽을 수 없다. 표본 수도 없다.
  - 시즌 17 행이 섞이고, versionconfig가 깨진 16.17 파일을 가리키며, 기간은 최대 3일이다.
- 결함을 모르고 수집하면 틀린 숫자가 그대로 앱에 실린다.

[벤치마크 정정]
- 'lol.qq 정적 데이터가 한 패치 늦다'는 절반만 맞다. 16.18 파일은 2026-09-09에 올라와 있고, 사이트의 versionconfig.json만 16.17을 가리킨다.
- '하루 약 240만 플레이어-게임'은 빌드 하나 기준이다. 9/14는 빌드 817과 816을 합쳐 약 419만인데, 이 전 티어 버킷 자체에 품질 문제가 있다.

[추정으로 남은 것]
- metatft games.count가 UTC 날짜 경계로 세는지는 비율이 맞아떨어진 데서 나온 추론이다.
- lol.qq 등수 분포가 고르지 않은 원인은 모른다.
- 중국 플래티넘+ 평균이 0.21 낮은 이유가 모집단 차이인지 기록 편향인지 판단하지 못했다.

- **lol.qq.com/tft 5.0점** — 강점: [직접 확인]
- 다른 두 곳에 없는 중국 서버 표본이다.
  - 数据检索器 2026-09-14 기준 빌드 817 2,249,666, 빌드 816 1,941,883 플레이어-게임이다.
  - 플래티넘+ 3일은 빌드 816만으로 3,342,905다.
- 数据检索器를 티어 필터(2+/4+/7+)와 함께 쓰면 품질이 괜찮다.
  - 카운트(unit_s, top1_cnt, top4_cnt, total)를 주고 등수 범위 위반이 0이다.
  - 전역 소스와의 상관계수는 플래티넘+ 0.94~0.95, 마스터+ 0.91이다.
  - 특성은 'DA_X,단계' 키로, 성급·아이템 조합 통계도 준다.
- 텐센트 16.18 chess.js는 CommunityDragon 16.18 빌드와 74명×9개 수치가 전부 일치한다. 세 곳 중 유일하다. race/job 단계도 일치한다.
- 数据检索器는 당일 데이터를 빌드 단위로 준다. 랭킹 API는 전일(T-1) 기준 10일 시계열을 준다. / 약점: [직접 확인]
- 数据检索器 '不限'(전체 티어) 버킷이 깨져 있다.
  - 영웅 69/69행, 특성 87/87행이 수학적으로 불가능한 값이다. 예: 아무무 평균 5.539인데 톱4가 87.9%다. 이 톱4면 평균은 2.07~3.60이어야 한다.
  - 개요의 등수 분포가 1등 351,443, 8등 215,886으로 고르지 않다. 모든 참가자가 기록되면 같아야 하므로 표본에 누락이나 편향이 있다(원인은 추정).
- 国服大数据 랭킹(tft_hero_ranking)은 절대값을 등수로 읽을 수 없다.
  - use_rate로 가중한 평균 등수가 3.50~3.56, 톱4가 0.675다. 모든 보드를 셌다면 약 4.4와 0.5여야 하는데 모든 tier_part에서 이렇다.
  - 아무무가 2.31인데, 같은 날 数据检索器 대사 이상은 4.08이다. 전역과의 상관계수도 0.75~0.86뿐이다.
  - 비율만 주고 표본 수는 없다. 표본이 거의 없는 행(use_rate 1e-6)도 하루 사이 0.9씩 흔들린 채 그대로 나온다.
  - tier_part 3(골드 이하)은 빈 응답이다.
- time_type=v 응답 140행 중 65행이 TFT17이고, TFT17_Reksai 목록이 16,398개다(3.68MB).
- 사이트 versionconfig.json(Last-Modified 2026-08-28)이 아직 16.17 파일을 가리킨다. 그 파일에는 사거리 4.944, 마나 0 같은 깨진 값이 있어, 16.18을 받으려면 경로를 추측해야 한다. 스킬 설명에는 수치가 없다.
- 数据检索器 기간은 최대 3일이다(7일은 빈 응답). version이 단일 빌드라 패치 합산을 직접 해야 한다.
- 특성 추세는 조합 50행(단일 특성 25행)이고 avg_rank가 소수 1자리다.
- 중국 플래티넘+ 평균이 전역보다 0.21 낮게 나온다. 모집단 차이인지 기록 편향인지는 모른다.
- **lolchess.gg 7.5점** — 강점: [직접 확인]
- 해석하기 가장 쉽다.
  - plays·wins·tops·placements·matchCount 원시 카운트를 준다.
  - plays = matchCount×8, avgPlacement = placements/plays 가 행마다 정확히 맞는다(불일치 0). 등수 범위 위반도 0이다.
- 특성 키가 [DA_특성, style, numUnits]라 단계를 실제 인원수로 바로 읽는다. 특성별 챔피언 통계도 들어 있다.
- metatft와 교차검증한 상관계수가 0.995(플래티넘+), 0.994(마스터+), 0.998(특성)이다.
- 티어 6버킷(전체/마스터+/다이아+/에메랄드+/플래티넘+/골드 이하)과 패치 리비전(18.2, 18.1d)을 고를 수 있다. 이전 패치 18.1 rev0 스냅샷은 2026-09-10 00:11 UTC에 동결돼 있다.
- 갱신 주기는 약 1시간이다(updatedAt 11:25:41 → 12:25:37 UTC, 두 번 관찰해 추정).
- 챔피언 상세에 성급별 통계, 아이템 117종, 3아이템 조합 1,347개가 있다.
- 한국어 스킬 수치 텍스트를 준다. / 약점: [직접 확인]
- 지역 필터가 없다(shard global만). 한국 메타만 따로 볼 수 없다.
- 표본이 metatft보다 작다. 18.2 전체 412,658판, 플래티넘+ 324,946판, 마스터+ 33,948판이다. 마스터+ 개화 11단계는 69플레이뿐이다.
- pickRate는 보드당 채용률이 아니다. 전체 유닛 칸 중 점유율이라 합이 1.0이다(70행 전부 확인). 그대로 쓰면 오해한다. avgPlacement는 문자열이다.
- 시즌 18 특성 중 Eclipse 통계 행이 없고, Rival은 1단계만 있다(metatft는 3단계).
- 사전이 CommunityDragon 16.18과 16곳 다르다. 사거리 12곳, 다이애나 마나 40, 렝가 공속 0.75, 브램블백 공격력 120, 마스터 이 공격력 60이다.
- 증강 통계는 빈 배열이다. 파라미터가 틀려도 200 빈 응답이 온다.
- **metatft.com 8.5점** — 강점: [직접 확인, 2026-09-15 12:29~12:50 UTC, 패치 18.2 랭크]
- 표본이 가장 크다. 플래티넘+ 7일 11,895,872보드(약 149만 판)로 lolchess 플래티넘+ 2,599,568보드의 4.6배다. 마스터+ 7일 449,752보드로 lolchess 271,584보드의 1.7배다.
- 티어·지역 범위가 가장 넓다.
  - 랭크는 아이언~챌린저를 하나씩 골라 조합할 수 있고, 서버 15개 중 KR만 따로 볼 수 있다.
  - KR 플래티넘+ 3일 949,768보드, KR 마스터+ 3일 38,960보드다.
  - 한국 메타만 따로 볼 수 있는 곳은 세 곳 중 여기뿐이다.
- 정확성
  - 행마다 원시 등수 분포 places[8]를 줘서 평균·톱4·승률을 직접 다시 계산할 수 있다.
  - 평균 등수가 1등·톱4 비율로 가능한 범위를 벗어난 행이 0개다.
  - lolchess와 같은 티어·패치로 챔피언 평균 등수를 비교하면 상관계수 0.995, 평균 절대차 0.029다. 마스터+는 0.994, 특성 단계 88행은 0.998이다.
- 패치 반영 속도: updated가 호출 시각과 수 초 차이다. 실시간으로 누적된다.
- 깊이: 성급×아이템 수, 3아이템 빌드, 배치 칸, 일·시간 추세, 동반 특성·유닛을 준다.
- 한국어 사전에 스킬 수치가 계산돼 있다(아리 455/685/3500). CommunityDragon ko_kr은 74명 중 2명만 스킬 변수가 있다. / 약점: [직접 확인]
- 표본 수(분모)가 흔들린다.
  - 같은 필터(플래티넘+ 3일)에서 units의 games.count는 5,708,984, traits는 6,750,280, sample_size는 6,740,744다.
  - count/sample_size 비율이 1일 0.51, 2일 0.77, 3일 0.85, 7일 1.0이다. count는 UTC 날짜 경계, sample_size는 롤링 기간으로 세는 것으로 보인다(추정).
  - 벤치마크 때 7,859,448이던 같은 쿼리가 약 30분 뒤 5,690,944로 바뀌었다.
- 전 랭크로 조회하면 유닛 합/games.count가 9.42다. 플래티넘+는 8.37, lolchess 전체는 8.35다. 픽률이 약 11% 부풀려진다.
- unit_detail_overall은 patch=current인데도 games에 18.1d 날짜가 섞인다. 아리 표본이 상세 907,955, 목록 770,321로 다르다.
- permit_filter_adjustment=true면 랭크 필터를 조용히 완화할 수 있다.
- 통계 응답에 DA_* 가 아닌 TFT18_* 행 6개가 섞인다. 사전 키는 TFT18_* 라 변환해야 하고, 특성 행 끝의 '_N'은 인원수가 아니라 단계 순번이다.
- 사전 수치가 CommunityDragon 16.18 빌드와 다르다.
  - 다이애나 마나 40(16.17 값, 16.18은 30), 렝가 공속 0.75(0.8), 브램블백 공격력 120(115), 마스터 이 공격력 60(65), 그롬프 공격력 45(30)
  - 사거리 6인 챔피언을 5로 적는다.

가져올 아이디어:
- [lolchess] 행마다 표본 수와 기준을 함께 싣는다.
- 수집기 JSON에 plays·wins·tops·placements·matchCount(metatft는 places 합)를 저장한다.
- 화면에 '18.2 · 마스터+ · 33,948판 · 1시간 전' 같은 기준 줄을 붙인다.
- 픽률은 lolchess pickRate(슬롯 점유율)나 metatft games.count(흔들림)를 쓰지 않는다. plays/(판수×8)로 다시 계산해 '보드당 채용률'로 통일한다.
- 검사: 유닛 합/보드가 약 8.4인지 본다. metatft 전 랭크의 분모 오차 11%를 이 검사로 잡을 수 있다.
- [lolchess] 특성 단계 키를 [DA_특성, style, numUnits]로 통일한다.
- metatft의 'DA_18_Blossom_3' 같은 순번을 lolchess/CommunityDragon의 단계 인원수로 매핑해 '개화 7'처럼 보여준다.
- 매핑은 88행 비교에서 상관계수 0.998로 검증됐다.
- Eclipse, Rival 3단계처럼 한쪽 소스에만 있는 행은 출처를 표시해 누락을 드러낸다.
- [lolchess] 패치 리비전 스냅샷을 둔다.
- 패치가 바뀌는 날 이전 패치의 마지막 챔피언·특성 통계를 동결 저장한다. lolchess의 18.1 rev0 스냅샷과 같은 방식이다.
- 챔피언 상세에 '지난 패치 대비 평균 등수 변화'를 보여준다.
- 하루 1회 수집 구조에 그대로 붙는다.
- [lol.qq 数据检索器] 챔피언·특성 화면에 '중국 서버' 열을 추가한다.
- tft_hero_rank / tft_trait_rank를 tier '4+'(또는 '7+'), 최근 3일로 호출하고 unit_s·top1_cnt·top4_cnt로 평균과 톱4를 직접 계산한다.
- 빌드(version)별 결과는 카운트를 더해 패치 단위로 합친다.
- tier ''(不限)와 tft_hero_ranking의 절대값은 쓰지 않는다.
- 검사: 평균 등수가 top1/top4로 가능한 범위 안인지 본다. 이번에 이 검사가 不限 버킷을 100% 걸러냈다.
- [lol.qq] avg_rank_delta처럼 모집단 대비 편차를 보여준다.
- 서버·티어마다 전체 평균이 다르다(중국 플래티넘+ 4.17~4.28, 전역 약 4.37). 절대 평균을 나란히 두면 중국이 0.2 좋아 보이는 착시가 생긴다.
- '챔피언 평균 − 그 모집단의 가중 평균'과 S/A/B 등급을 함께 보여줘 한 손 화면에서도 서버 간 비교가 공정하게 한다.
- [lol.qq] 최소 표본 문턱(10/50/100/300/500/1K/3K/10K)을 도입한다.
- 수집기에서 문턱 미만 행은 숨기거나 흐리게 표시한다.
- lol.qq 랭킹의 럭스 변형(use_rate 1e-6)이 하루 새 평균 등수가 0.9 흔들린 것 같은 착시를 막는다.
- [lol.qq 정적 데이터] 16.18 chess.js를 챔피언 기본 수치 검증용으로 쓴다.
- CommunityDragon 16.18과 74명 전부 일치했으므로, 두 곳과 대조해 lolchess·metatft 사전 불일치(다이애나 마나 40, 렝가 공속 0.75, 사거리 5 등)를 자동으로 경고한다.
- versionconfig.json은 16.17을 가리키므로, 경로는 CommunityDragon 버전(16.18)으로 조립하고 응답의 version 필드로 확인한다.

#### 모바일 사용성

[결론] metatft가 7.5점으로 1위, lolchess 6점, lol.qq 2점이다. 폰 한 손 사용 기준이다.

[측정 방법]
- lolchess와 metatft는 in-app 브라우저를 375x812 모바일 에뮬레이션으로 열고 getBoundingClientRect와 computed style을 쟀다.
- 그 탭의 스크린샷이 백그라운드에서 빈 화면으로 나와서 판단은 DOM 좌표에 기반한다.
- lol.qq는 정책상 브라우저가 차단이라 HTML·CSS를 직접 받아 분석했다.
- 산출물 경로: C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_mobile/ (lolqq_comm.css, metatft_main_real.css, lolchess_champs.html, lolqq_h5_equip.html 등)

[1위를 가른 차이: 한눈에 읽히는가]
- 첫 화면(812px)에 보이는 데이터: metatft 3행(표 y=605), lolchess 0행(챔피언 표 y=1019, 시너지 표 y=787).
- 스와이프 없이 보이는 지표: metatft는 등급·평균 등수·승률·빈도, lolchess는 평균 등수 하나(700px 표, 이름 열 고정 안 됨).
- 글자와 행: metatft 13px/49px, lolchess 12px/60px.
- 기본 정렬 맨 위: metatft는 표본 큰 행, lolchess는 3~19게임짜리 변형.
- 챔피언 상세: metatft는 스킬·추천 빌드 카드가 첫 화면에서 한 화면 이내(y=677~921), lolchess는 아리 정보가 y=955, 아이템 표가 y=1734.

[lolchess가 앞선 부분]
- 한국어 자연스러움이 확실히 앞선다. metatft에는 '아리이(가)' 같은 조사 자리표시자와 미번역 문구가 있다.
- 터치 목표가 크다(칩 36px 대 25~28px).
- 필터 깊이(티어·패치·등급·큐)는 대등하거나 조금 앞선다.
- 그래도 폰에서 정보를 찾기까지 스크롤·스와이프 비용이 커서 총점은 1.5점 뒤진다.

[lol.qq]
- html min-width 1240px!important, 중국어만, 호버 툴팁 구조라 폰에서 사실상 쓸 수 없다.
- 기능과 특성 조합 데이터는 있어 0점은 아니다.

[추정으로 남긴 것]
- metatft가 ko-KR 브라우저에서 영어로 뜬다는 점: 번들 코드 판독이며 실기기는 미확인.
- lol.qq 호버 툴팁이 터치에서 안 뜬다는 점: 추정.
- 광고 크기: 에뮬레이션 창 기준이라 실기기와 다를 수 있다. metatft 동영상 광고 345x194는 확인했고, lolchess 광고 영역 375x140도 확인했다.

[우리 앱에 주는 함의]
- 화면 뼈대는 metatft를 따른다: 폰 폭에 맞춘 4~5열, 등급 글자 배지, 상세 상단의 요약 카드.
- 용어와 표본·신선도 표기는 lolchess 방식으로 한다.
- 최소 표본 기준과 이름 열 고정은 lolchess의 약점을 피하기 위해 넣는다.
- 특성 조합과 중국·글로벌 차이는 lol.qq 데이터로 차별화한다.
- 모두 하루 1회 수집한 JSON과 DA_* 조인만으로 구현할 수 있다.

- **lol.qq.com/tft 2.0점** — 강점: [벤치마크 기준] 이 카테고리 기능 자체는 있다.
- 도감: 코스트·특질·직업 필터, 이름 검색
- 英雄排行: 登场/前四/登顶/排名 정렬, 每日/版本 전환
- 상세: 日/周/月 탭
- 羁绊排行: 특성 조합(예 3+3) 단위 50행과 5주기 추세. 행을 펼치면 그 조합의 상위 덱 2개가 나오고 첫 행은 자동으로 펼쳐진다.
- 특성 '조합' 관점과 펼침형 행은 다른 두 사이트에 없고, 모바일 아코디언으로 옮기기 좋다.
- 도감의 增强/削弱/最新 변경 배지가 있다. / 약점: [직접 확인: HTML·CSS 다운로드 분석. in-app 브라우저가 정책상 차단이라 렌더링은 못 봄]
- comm.css에 html{min-width:1240px!important}가 있다. 미디어쿼리 19개는 모두 폭 1400px 이상이거나 높이 조건이다. 375px 폰에서도 1240px 폭으로 그려져 약 3.3배 좌우 패닝이나 축소가 필요하다.
- 모바일 UA와 데스크톱 UA에 같은 316,532바이트 HTML을 준다. 모바일 분기가 없다.
- lang="zh-CN"만 있고 한국어·영어 전환이 없다.
- 챔피언·특성 요약이 @mouseenter 호버 툴팁이다(index.html 전체에 26개). 터치에서 안 뜰 가능성이 크다(추정).
- 번들이 참조하는 모바일 H5는 winLineup-detail과 datarank-equip-detail 두 개뿐이다. 둘 다 viewport width=1125 기반이고 챔피언·특성용 H5는 찾지 못했다.
- 폰 유도용 QR(微信小程序, 掌上英雄联盟)은 HTML에서 주석 처리돼 있다.
- **lolchess.gg 6.0점** — 강점: [직접 확인: 브라우저 375x812에서 DOM 좌표를 잰 결과]

1) 반응형이다. viewport는 device-width, 인라인 미디어쿼리 38개, 페이지 가로 스크롤 없음.

2) 한국어가 가장 자연스럽다.
- 헤더: 평균 등수 / 픽률('0.93 / 8') / TOP4 / 승률 / 게임 수 / 추천 아이템
- 시너지는 '11 나무정령'처럼 활성 인원수+이름으로 표기한다.
- 상세의 추천 아이템 탭: 3신기 / 아이템 / 유물 / 찬템 / 상징
- 번역투가 없다.

3) 필터가 깊고 터치 크기가 적당하다.
- 티어·패치 드롭다운 36px
- 시너지 등급 칩(전체/프리즘/골드/고유/실버/브론즈) 36px, 랭크/더블 업 36px, 시너지 통계/가이드/표 탭 50px
- 정렬: 헤더 6개(pointer), 코스트순/가나다순
- 챔피언 검색

4) 신뢰도 정보: 게임 수 열과 '최종 업데이트: 6분 전'.

5) 행을 누르면 상세로 간다(/champions/set18/luxblossom). 추천 아이템 칸에 아이콘 3개가 있다. / 약점: [직접 확인]
1) 첫 화면(812px)에 데이터가 0행이다.
- 챔피언 표는 y=1019, 시너지 표는 y=787에서 시작한다.
- 그 위를 광고(375x140, y=349), 시즌 배너, 탭, 정렬 버튼, 검색, 챔피언 아이콘 그리드가 차지한다.

2) 가로 스크롤에서 맥락을 잃는다.
- 표 폭 700px를 375px 박스에서 가로 스크롤한다.
- 이름 열이 고정되지 않아(position static) TOP4·승률·게임 수·추천 아이템(x=390~700)을 보려고 밀면 챔피언 이름이 사라진다.
- 스와이프 없이 온전히 보이는 지표는 평균 등수 하나다.

3) 글자 12px, 행 60px라 한눈에 읽기 어렵다.

4) 기본 정렬에 최소 표본이 없다.
- 챔피언 1~4위가 럭스 변형이다(14/12/19/3게임).
- 시너지 1위는 '11 나무정령'(1,344게임, 픽률 0.01%)이다.
- 폰에서 가장 먼저 보이는 행이 노이즈다.

5) 상세(아리)가 늦게 나온다.
- 목록 헤더와 아이콘 그리드를 다시 그린 뒤 y=955에서야 아리 정보가 시작한다.
- 아이템 통계 표는 y=1734(두 화면 아래), 폭 610px로 가로 스크롤이 필요하다.
- 아이템 조합은 아이콘뿐이다(이름은 alt 속성에만 있음).
- **metatft.com 7.5점** — 강점: [직접 확인: 브라우저 375x812에서 DOM 좌표를 잰 결과]

1) 반응형이고 목록을 폰 폭에 맞췄다.
- main CSS에 @media가 250개 있다(max-width 767/600/500/450/400 등). 페이지 가로 스크롤은 없다(docW=375).
- /units: 6열 표(490px)가 345px 박스에 들어간다. 유닛·티어·평균 등수·승률·빈도는 스와이프 없이 보이고, 인기 아이템(x=319)만 화면 밖이다.
- /traits: 433px 표에서 특성·티어·평균 등수·승률·레벨이 보인다.

2) 첫 화면에 데이터가 3행 보인다.
- 표 헤더 y=605, 글자 13px, 행 높이 48~49px.
- 헤더 6개가 모두 정렬되고 높이는 46px이다. 상단 내비(47px)는 고정된다.
- S~D 등급 글자와 평균 등수가 나란히 있어 서열이 바로 읽힌다.
- 기본 정렬 1위가 표본이 큰 럭스(672,197)라 맨 위 행에 노이즈가 없다.

3) 필터와 검색
- 필터 칩: 랭크 / 18.2 / 지난 3일 / 플래티넘+
- 코스트 칩 1~5, 검색창(OR/AND/NOT 문법은 벤치마크의 번들 분석 기준)
- 특성: 전체/레벨별 토글, 활성 단계(2|3|5|7)를 행 안에 표시

4) 상세(아리)가 폰에서 정보를 먼저 준다.
- 코스트·특성·역할과 한국어 스킬 설명·수치가 첫 화면 근처(능력/통계 탭 y=677)에 있다.
- 추천 빌드 카드 y=921, 추천 아이템 카드 y=1262. 카드마다 평균 등수·등수 변화·플레이율이 있다.
- 그 아래 48px 탭(빌드/아이템/배치/덱/추세)이 있고, 표는 345px라 가로 스크롤이 없다.
- 행을 누르면 /units/Ahri 같은 읽기 쉬운 주소로 간다.

5) 표본과 신선도를 함께 보여준다: '최근 업데이트: 몇 초 전', '분석된 덱: 5,701,392'.

6) 한국어 UI 문자열이 2,709개다. navigator.language가 'ko'인 창에서 lang-ko_kr로 렌더링됐다. / 약점: [직접 확인]
- 상단 동영상 광고(avp-video-ad, 345x194, y=88) 때문에 제목이 y=307, 표가 y=605로 밀린다.
- 번역투가 있다: '아리이(가)', '아리을(를)' 조사 자리표시자. 'Build', 'Magic Damage:'는 번역되지 않았다. 목록 헤더는 '유닛', 제목은 '챔피언'으로 섞여 있다.
- 터치 목표가 작다: 필터 칩 28px, 코스트 칩 25px, 검색창 32px(권장 44~48dp 미만).
- 목록에 TOP4 열이 없다(1280px 폭에서도 같은 6열). 게임 수가 빈도 칸에 붙어 있다.
- 상세의 빌드 표 행이 아이콘뿐이라 텍스트로 읽을 수 없다.

[추정: 코드 판독, 실기기 미확인]
- 언어 선택 코드가 navigator.language를 'ko_kr'과 정확히 같거나 접두사 'ko'일 때만 한국어로 잡는다. 'ko-kr'이면 en_us가 된다. 안드로이드 크롬(보통 ko-KR)은 첫 방문에 영어로 뜰 가능성이 크다.
- 번들에 목록 5행마다 인라인 광고를 넣는 로직이 있다. /units에서는 0개였다.

가져올 아이디어:
- [lolchess] 한국어 표기 기준을 가져온다. 헤더는 '평균 등수 / TOP4 / 승률 / 게임 수', 특성 행은 '11 나무정령'(활성 인원수+이름), 챔피언 상세의 추천 아이템 탭은 '3신기 / 아이템 / 유물 / 찬템 / 상징'. 이름은 CommunityDragon ko_kr로 채우고 lolchess에서는 용어와 UI 패턴만 참고한다. lolchess 약관이 데이터 재배포를 금지하기 때문이다.
- [lolchess] 특성 목록 상단에 등급 칩(전체/프리즘/골드·고유/실버/브론즈)을 48dp 높이로 둔다. 등급은 CommunityDragon 특성 단계 데이터로 계산해 수집기가 decks.json 옆 traits.json에 style 필드로 넣는다.
- [lolchess] 목록마다 표본 수 열과 '최종 업데이트 n시간 전' 라벨을 붙인다. 하루 1회 수집이므로 JSON에 collectedAt(KST)과 소스별 games를 넣고 Compose 상단 한 줄에 표시한다.
- [lolchess, 반면교사] 기본 정렬에 최소 표본 기준을 둔다. 예: 게임 수 1,000 미만이나 픽률 0.1% 미만은 흐리게 하거나 '표본 부족' 접기로 넣는다. lolchess는 3~19게임짜리 럭스 변형이 1~4위를 차지했다.
- [lolchess, 반면교사] 표가 폰 폭을 넘으면 가로로 밀 때 챔피언 이름 열을 고정한다(Compose에서는 첫 열 고정 LazyRow). 가능하면 핵심 4지표(등급·평균 등수·TOP4·게임 수)만 남겨 스와이프를 없앤다.
- [lolchess] 챔피언 상세에 성급별(1/2/3성) 성과와 3아이템 조합 탭을 둔다. 조합 행은 아이콘만 쓰지 말고 한글 아이템 이름을 한 줄로 병기한다. 데이터 키가 DA_*라 metatft의 unit_detail_items와 바로 합칠 수 있다.
- [lolchess, 반면교사] 코스트순/가나다순 챔피언 아이콘 그리드는 좋은 선택기지만 통계 위에 펼치지 말고 하단 시트나 검색 버튼 뒤에 둔다. lolchess는 이 그리드 때문에 표가 y=1019로 밀렸다.
- [lol.qq] 특성 '조합' 카드를 넣는다. 주특성 2개+단계(예 3+3)별 登顶·前四율과 5주기 추세 스파크라인을 한 카드에 담고, 누르면 그 조합의 상위 덱 2개가 아코디언으로 펼쳐진다. 수집기가 tft_trait_strength_trend와 tft_main_trait_lineup을 하루 1회 받아 숫자 traitId를 race/job.js로 DA_*에 매핑한다.
- [lol.qq] 챔피언 카드와 상세에 '중국 서버 vs 글로벌' 차이 배지를 단다. lol.qq tft_hero_ranking(hero_id=DA_*)과 metatft units(unit=DA_*)를 같은 키로 조인해 '중국 평균 등수 3.9 / 글로벌 4.3' 같은 형태로 보여준다. 앱의 '중국 한정 덱' 판정과 같은 논리를 챔피언 단위로 확장하는 것이다.
- [lol.qq] 도감의 增强/削弱/最新 상태를 '버프/너프/신규' 색 배지로 챔피언 목록 행에 붙인다. 코스트 필터 옆에는 레벨별 상점 확률표를 작은 하단 시트로 연결한다.

#### 구현 가능성

[직접 재확인: 2026-09-15 12:30~12:40 UTC, Git Bash curl, 브라우저 UA]

lol.qq
- POST https://mlol.qt.qq.com/go/exploit/proxy (Referer·Origin·쿠키 없음)
  - tft_hero_ranking(time_type d): HTTP 200, 86,747B, result 0. dtstatdate 20260914, 75행×10일.
  - tft_trait_strength_trend: 200, 25,924B, 50행.
  - tft_recent_versions: 최신 빌드 16.18.817.4437.
  - 数据检索器 tft_trait_rank 87행, tft_hero_rank 69행, tft_three_equip_rank 2000행(442KB): 모두 result 0. version='16.18'로 보내면 result 0에 0행.
- versionconfig.json → chess/race/job.js: 200. version 16.17, Last-Modified 09-09.
- 10회 연속 호출 모두 200.

lolchess
- GET https://tft.dakgg.io/api/v1/meta-deck-champions?tierId=3&patch=1802&revision=0&queueId=1100&from=web: 200, 181,968B. patch를 빼도 같은 응답.
- meta-deck-traits?tierId=1: 200, 66,103B.
- data/champions·data/traits?season=set18&hl=ko: 200.
- CloudFront, ACAO *, 응답 약 0.04초, 10회 연속 200.
- 약관 페이지 lolchess.gg/about/terms_and_service: 200. 사전 승낙 없는 복제·제3자 제공 금지 조항을 확인했다.

metatft
- GET https://api-hc.metatft.com/tft-stat-api/units와 traits (queue=1100&patch=current&days=3&rank=PLATINUM+&permit_filter_adjustment=true): 200, 6,237B / 8,189B. games 18.2, override_applied false.
- tft-comps-api/unit_items_processed: 200, 67,703B.
- data.metatft.com/lookups/TFTSet18_latest_ko_kr.json: 200, 1.37MB.
- 10회 연속 200.
- TermsOfService 청크에서 스크래핑·재배포 금지 조항은 없었다. 5.1 데이터 소유권 주장과 접근 차단 권리 조항만 있다.

DA_* 대조 (CommunityDragon ko_kr.json, Last-Modified 09-12, 로컬 사본과 크기 동일)
- lol.qq: 영웅 74/75, 특성 (DA,n) 35/35, 단계 인원수 87/87, race/job 36/36.
- lolchess: 챔피언 69/69, 아이템 40/40, 특성 35/35, numUnits 88/88.
- metatft: 챔피언 65/71(TFT18_* 6행), 특성 36/36(_N은 단계 순번), 아이템 141/142.

지표 일관성
- lolchess와 metatft는 가중 평균 등수 4.37~4.38, 톱4 52%로 서로 일치한다.
- lol.qq tft_hero_ranking은 3.53, 68%다.
- lol.qq 数据检索器는 행 단위로 모순이다(영웅 47/69, 특성 67~72/87이 톱4 75% 초과인데 평균 등수 4.5 초과). 날짜·빌드·filterOptions를 바꿔도 같았다.

미확인
- GitHub Actions(미국 러너)에서의 접근. daily.yml은 active지만 실행 이력 0건이다.
- lol.qq 약관.
- 대량 호출 한도.

[채점 논리] 구현 가능성 = 인증 없는 일일 수집 + 스키마 안정성 + DA 결합 용이성 + 차단 위험.

metatft 8점: 이미 연동된 호스트이고, 두 필드짜리 단순 스키마에 믿을 수 있는 원시 분포를 주며, 명시적 금지 조항이 없다. 감점은 _N 순번 변환, 비 DA 행 필터, 캐시 없는 오리진, 챔피언별 아이템 통계를 위한 유닛별 반복 호출이다.

lolchess 6점: 기술 요소만 보면 최고다(CDN, 인원수 키, 한 번 호출, 한국어 원문). 하지만 우리 운영 방식(공개 저장소 + 매일 재배포)을 약관이 명시적으로 금지하는 유일한 소스라 중단·차단 위험이 가장 크다. 운영사 허락을 받으면 8~9점대로 올라간다.

lol.qq 5점: 인증은 없다. 그러나 특성 통계가 조합 단위(18/36)이거나, 전체 빌드 문자열과 CST 날짜가 필요하고 지표가 모순되는 数据检索器에 기대야 한다. 정적 사전은 한 패치 늦고 숫자는 문자열이다. 중국 서버 데이터라는 고유 가치는 크지만 이 관점 점수에는 반영하지 않았다.

응답 사본과 대조 스크립트(join.py): C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_ct/

- **lol.qq.com/tft 5.0점** — 강점: (직접 확인) mlol.qt.qq.com/go/exploit/proxy POST가 서명·쿠키·Referer 없이 result 0을 준다. 확인한 alias와 응답 크기:
- tft_hero_ranking(d) 86,747B
- tft_trait_strength_trend 25,924B
- tft_recent_versions
- 数据检索器 tft_trait_rank 87행, tft_hero_rank 69행, tft_three_equip_rank 2000행
10회 연속 호출도 모두 200이었다.

키가 DA_*라 CommunityDragon과 바로 붙는다.
- hero_ranking 74/75, tft_hero_rank 69/69
- 특성 키 'DA_Juggernaut18,2'는 35/35 일치하고, 뒤의 숫자가 CDragon 단계 인원수와 87/87 같다.
- 챔피언·아이템 키 'DA_Draven18#아이템|아이템|아이템'도 DA_*다.
- 정적 chess.js의 raceIds/jobIds도 DA_*이고, race/job characterid는 36/36 일치한다.

수집기가 이미 game.gtimg.cn과 lol.qq.com을 부르므로 새로 추가되는 호스트는 mlol 하나다. 챔피언 10일 일간 추세는 한 번 호출로 받는다. / 약점: (직접 확인)
1) 특성 통계를 온전히 얻는 경로가 불안정하다.
- tft_trait_strength_trend 50행은 조합 단위(단일 25, 2개 17, 3개 8)라 36개 특성 중 18개만 나온다. 숫자 trait_id라 race/job.js로 변환해야 한다.
- 단계별 전체 통계는 数据检索器뿐이다. tft_recent_versions로 전체 빌드 문자열(16.18.817.4437)을 먼저 받고 CST 날짜와 함께 보내야 한다.
- version='16.18'로 보내면 result 0에 0행이 와서 조용히 실패한다.

2) 지표 정의가 다른 두 소스와 맞지 않는다.
- 数据检索器는 09-14·09-15, 빌드 816·817, filterOptions ''·'[]'의 모든 조합에서 행 단위로 모순이다. 영웅 69행 중 47행, 특성 87행 중 67~72행이 톱4 75% 초과이면서 평균 등수는 4.5를 넘는다.
- tft_hero_ranking을 사용률로 가중 평균하면 평균 등수 3.53, 톱4 68%다. lolchess·metatft는 4.37~4.38, 52%다.
- 정의를 역공학하지 않으면 한국·글로벌 수치와 나란히 보여줄 수 없다.

3) 정적 데이터와 형식 문제.
- 정적 chess/race/job.js는 16.17(Last-Modified 09-09)로 라이브 16.18보다 한 패치 늦고, 중국어뿐이다.
- 숫자는 모두 문자열이다.
- 단계표 2개(Eclipse, Rival)가 CDragon과 다르다.

(미확인) 미국에 있는 GitHub Actions 러너에서 mlol.qt.qq.com에 접근할 수 있는지는 모른다. 워크플로는 등록돼 있지만 실행 이력이 0건이다. 비공개 게이트웨이이고 프론트에 600만 게임 초과 조회 차단이 있어 부하에 민감해 보인다(추정). 약관은 확인하지 않았다.
- **lolchess.gg 6.0점** — 강점: (직접 확인) 기술적으로는 세 곳 중 가장 쉽다.
- tft.dakgg.io/api/v1은 CloudFront를 거치고(X-Cache, max-age=60) ACAO는 *이며 인증이 없다. 응답 약 0.04초, 10회 연속 호출 모두 200.
- meta-deck-champions는 patch 파라미터를 빼도 동일한 응답(1802, rev 0)이라 패치를 하드코딩하지 않아도 된다.
- 한 번 호출로 챔피언 69명의 통계, 성급별 통계, 추천 아이템 5개(65명), 3아이템 조합(첫 행 기준 10개)이 온다.

DA_* 결합이 가장 깔끔하다. 챔피언 69/69, 아이템 40/40, 특성 35/35가 일치하고, 특성 행의 numUnits가 CDragon 단계 인원수와 88/88 같아 단계 매핑이 필요 없다. plays·wins·tops·placements가 정수라 재계산이 되며, avgPlacement와의 오차는 최대 0.005다.

data/champions·traits?hl=ko가 한국어 이름·설명·단계(styles min/max)를 직접 주고, 이름은 CDragon ko_kr과 74/75 같다. / 약점: (직접 확인) 라이브 약관 페이지 lolchess.gg/about/terms_and_service는 서비스로 얻은 정보를 회사의 사전 승낙 없이 이용 외 목적으로 복제하거나 제3자에게 제공하는 행위를 금지 조항으로 명시한다.
- 공개 GitHub 저장소에서 매일 수집해 앱에 재배포하는 우리 구조가 이 조항에 정면으로 걸린다.
- 수집 코드가 공개돼 있어 발견되기 쉽다. 차단이나 중단 요청을 받으면 기능 전체가 멈춘다.
- 세 소스 중 명시적 금지 조항이 확인된 곳은 여기뿐이다.

그 밖에 (직접 확인):
- data/champions 160개 중 85개, data/traits 109개 중 73개가 시즌 18이 아닌 항목(TFT17_*, 소환물)이라 걸러야 한다.
- 설명문에 %i:scaleAD% 같은 치환자가 섞여 있다.
- 럭스 변형 5종은 통계 행이 없다.
- 글로벌 표본이다.

(재확인 안 함) 파라미터가 틀리면 HTTP 200 빈 응답이 온다는 벤치마크 주장.
- **metatft.com 8.0점** — 강점: (직접 확인) 수집기가 이미 같은 호스트(api-hc.metatft.com)와 같은 fetch()를 쓰고 있어 추가 연동 비용이 가장 작다. 인증·쿠키 없이 200이고 ACAO는 *이며, 10회 연속 호출도 모두 200이었다. 응답이 작다: tft-stat-api/units 6,237B, traits 8,189B, tft-comps-api/unit_items_processed 67,703B, 한국어 사전 lookups/TFTSet18_latest_ko_kr.json 1.37MB.

행 구조가 {키, places[8]} 두 필드뿐이라 스키마가 바뀔 여지가 좁다. 원시 등수 분포로 직접 계산하면 가중 평균 등수 4.37, 톱4 52%로 lolchess(4.38, 52%)와 일치해 지표를 믿을 수 있다.

DA_* 결합률(CommunityDragon ko_kr 대비): 특성 36/36, 아이템 141/142, 챔피언 65/71. updated 타임스탬프(12:28 UTC), sample_size, override_applied=false를 함께 줘서 신선도와 필터 완화 여부를 검증할 수 있다.

약관(TermsOfService 번들 청크 텍스트)에서 스크래핑·재배포를 명시적으로 금지하는 조항은 찾지 못했다. robots.txt는 전체 허용이다. / 약점: (직접 확인)
- 특성 키 끝의 _N은 인원수가 아니라 단계 순번이다(DA_18_Adaptor는 N=1,2,3, CDragon minUnits는 2,3,4). CDragon effects 순서로 변환해야 한다(36/36 모두 단계 수 범위 안).
- units와 unit_items_processed에 DA가 아닌 저표본 TFT18_* 6행(Akali, Gromp, KogMaw, MasterYi, NidaleeCougar, SprykinSummonMelee)이 섞여 있어 걸러야 한다.
- 럭스 변형 8종은 행이 없다. DA_Lux18_Base 한 키로 합쳐진 것으로 보인다(추정).
- unit_items_processed의 유닛→아이템은 이름 순위만 있고 조합별 성과가 없다. 챔피언별 아이템 통계를 내려면 unit_detail_items를 유닛마다(약 70회) 불러야 한다.
- api-hc 응답에 CDN 캐시 헤더가 없다. 요청마다 오리진이 집계하는 구조로 보여(추정) 대량 호출은 제한을 부를 수 있다.
- 약관 5.1이 데이터 소유권과 접근 차단 권리를 주장한다.
- 비공개 API라 경로 개정 흔적(unit_detail2, unit_positions2)이 있다.
- 글로벌 표본이라 중국 한정 판정에는 쓸 수 없다(이 관점의 감점 요인은 아님).

가져올 아이디어:
- [lolchess] 특성 단계 키를 (DA_특성, 활성 인원수)로 통일한다. lolchess의 numUnits와 lol.qq 数据检索器의 'DA_X,n'은 이미 인원수라 CDragon minUnits와 각각 88/88, 87/87 일치한다. metatft의 _N 순번만 수집 시 CDragon effects[N-1].minUnits로 바꿔 넣으면 세 소스가 같은 키로 합쳐지고, 앱은 '6 나무정령'처럼 인원수로 표시할 수 있다.
- [lolchess] 목록 API가 한 번에 챔피언별 추천 아이템 5개와 3아이템 조합을 주는 방식을 따른다. 수집기에서 metatft unit_detail_items를 하루 한 번 유닛별로 모아 '추천 아이템 5개 + 빌드 상위 3개'로 요약해 챔피언 목록 JSON에 미리 넣으면, 폰에서는 추가 호출 없이 한 번 탭으로 상세를 연다.
- [lolchess] 통계 블록마다 patch, updatedAt, matchCount(표본 수)를 JSON에 싣고 화면 상단에 '18.2 · 표본 32만 · 3시간 전'처럼 표시한다. metatft의 games.count, updated, filter_adjustment.override_applied도 같은 필드로 정규화한다. 수집 시에는 permit_filter_adjustment=false로 고정해 필터가 몰래 완화되지 않게 한다.
- [lolchess·lol.qq에서 얻은 교훈] 조용한 빈 응답을 verify.py에서 막는다. lol.qq는 version='16.18'이면 result 0에 0행이 오고, lolchess는 파라미터가 틀리면 200 빈 응답이 온다. 소스별 행 수와 CDragon 대비 DA 키 일치율(예: 챔피언 90% 미만)이 기준 아래면 실패로 처리해 기존 '실패 시 이슈 등록' 흐름을 타게 한다.
- [lol.qq] tft_hero_ranking(time_type d) 한 번(87KB)이면 챔피언별 10일 일간 추세가 DA_* 키로 온다. 챔피언 상세에 '중국 서버 추세' 스파크라인으로 넣는다. 다만 평균 등수·톱4는 척도가 달라(가중 3.53/68%) metatft 수치와 같은 축에 섞지 말고 '중국 서버 기준' 라벨로 따로 보여준다. 표본에 없는 DA_18_EliseSpider 행은 거른다.
- [lol.qq] 数据检索器 tft_trait_rank·tft_hero_rank에서는 일관성이 확인된 사용률만 먼저 '중국 픽률' 열로 쓴다. trait_s/total이 trait_rate와 일치한다(0.399). avg_rank와 top4_cnt는 행 단위로 모순이므로 정의를 확인할 때까지 숨긴다. 이렇게 하면 앱의 '중국 한정' 가치를 특성·챔피언 화면으로 넓힐 수 있다.
- [lol.qq] tft_recent_versions(510B)를 수집기의 패치 변경 감지기로 쓴다. 최신 빌드 문자열이 바뀐 날에만 数据检索器 요청의 version을 갱신하고 CDragon 사전을 새로 받는다. 정적 chess/race/job.js가 라이브보다 늦을 때(현재 16.17 대 16.18)는 CDragon ko_kr을 우선한다.

### 아이템

| 관점 | lol.qq | lolchess | metatft | 관점 1위 |
|---|---:|---:|---:|---|
| 데이터 품질 | 3.5 | 7.5 | 9.0 | metatft.com |
| 모바일 사용성 | 2.0 | 6.0 | 7.0 | metatft.com |
| 구현 가능성 | 7.0 | 6.0 | 8.5 | metatft.com |

#### 데이터 품질

[결론] 데이터 품질 관점의 1위는 metatft(9)다. 2위 lolchess(7.5), 3위 lol.qq(3.5)다. 모든 수치는 2026-09-15 12:25~12:45Z에 python urllib로 직접 호출해 확인했고, 추정은 따로 표시했다.

[정확성] 판단의 가장 큰 근거다.
- metatft와 lolchess는 같은 조건(글로벌 랭크, 플래+, 패치 18.2)에서 서로 독립적으로 거의 같은 값을 낸다. 아이템 113개의 평균 등수 차는 평균 0.020, Spearman 0.988이고, 두 소스 모두 내부 모순이 0건이다.
- lol.qq는 같은 날 같은 아이템(가고일 돌갑옷)에 엔드포인트마다 4.1 / 1.69 / 5.19를 준다.
- 数据检索器는 자기 overview와도 맞지 않는다. tier=''이면 143행이 전부 불가능한 값이다.
- 등수 분포 편향(1위 15.9%, 8위 약 10%)은 챌린저만 봐도 남는다.
- 装备排行의 착용 챔피언 목록은 ID가 잘려 쓸 수 없다.
- 중국 표본이 크고 빌드 단위로 빠르게 반영되지만, 평균 순위라는 핵심 수치를 믿을 수 없어 최하점을 줬다. 그래도 인기도(build_s) 순위는 글로벌과 0.988로 일관돼 쓸 여지가 있다.

[metatft와 lolchess의 차이]
- 표본: metatft가 플래+에서 2.6배, KR에서 1.8배 크다. 마스터+는 같다.
- 필터: 랭크 임의 조합, 서버 15개, 1~7일, 패치 선택으로 metatft가 더 넓다.
- 깊이: 등수 분포 8칸, 10일 추세, 유닛 대비 차이, 완성 스테이지, 역검색 파일이 metatft에만 있다.
- lolchess는 표본 수 표시가 명확하고 KR·이전 패치 조회가 되며, 동반 아이템 통계가 유일하다. 대신 기간 파라미터가 불명확하고(dt=1·5는 조용히 빈 응답) 추세·분포가 없다.
- 실시간 대 1시간 갱신 차이는 하루 1회 수집기에는 거의 무의미하다고 보고(추정) 가중치를 낮게 줬다.

[앱 적용 메모]
- 아이템 키는 세 소스가 DA_*로 정확히 겹친다. metatft 142 = lol.qq 142, lolchess 126은 그 부분집합이다.
- metatft는 비 DA 행(TFT18_*) 필터가, lol.qq는 수치 검증 가드가 필요하다.

[산출물] 호출 스크립트와 저장 응답은 C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/bench/judge_item/ 에 있다.
- 수집: lc_live.py, mt_live.py, qq_live1.py, qq_ds.py, qq_ds2.py, qq_ds3.py, lc_mt_window.py
- 교차 검증: cmp1.py, join.py
- lolchess 번들 경로 검색: lc_bundle.py
- 응답 JSON 다수

- **lol.qq.com/tft 3.5점** — 강점: [직접 확인]
- 유일한 중국 서버 표본이다. 数据检索器 실버+ 09-14 하루 기준 빌드 16.18.816.5012가 1,114,323보드, 16.18.817.4437이 1,275,815보드다. 한 빌드 3일은 6,528,092보드이고, 마스터+ 하루 19,412, 챌린저 하루 1,191이다.
- 버전 반영이 가장 빠르고 세밀하다. 09-15 20:40 CST에 이미 당일 데이터 1,497,681보드가 있고, tft_recent_versions로 핫픽스 빌드 단위까지 나눈다.
- 조합이 깊다.
  - 챔피언#아이템(ShowHero=1, 마스터+ 1,935행), 챔피언별 2·3아이템 조합(마스터+ 3아이템 1,023행)
  - 레벨·3성 수·유물/상징/찬란 개수 필터, 티어 실버~챌린저 단일/범위
  - 표본 build_s 제공
- 인기도(build_s) 순위는 metatft 글로벌 사용량과 Spearman 0.988(107개)로 일관된다.
- 아이템 키 142개가 数据检索器·레거시·装备排行 모두 metatft와 정확히 일치한다. / 약점: [직접 확인]
1. 같은 텐센트 소스의 엔드포인트끼리 값이 다르다. 같은 날(dtstatdate 20260914) DA_GargoyleStoneplate의 평균 순위가 레거시 equiprank 4.1, 装备排行 tft_equip_ranking 1.69(톱4 94.8%), 数据检索器 5.19로 모두 다르다. 装备排行 전체 가중 평균 순위 3.02는 모집단 평균(약 4.2)으로 불가능하다.
2. 数据检索器 수치가 자기 모순이다.
   - 실버+ 143행 중 10행이 top1/top4 비율로 가능한 평균 범위를 벗어난다(스톤플레이트 5.189, 가능 상한 4.39).
   - 인스턴스 합산 톱4는 68.9%인데 가중 평균은 4.38로, 보드 평균 4.21보다 나쁘다.
   - tier=''이면 143행 전부 불가능하다(top4_cnt가 build_s의 1.12배, 스톤플레이트 평균 7.74).
3. 등수 분포가 편향돼 있다. 1위 15.9%, 8위 9.8~10.1%(정상 12.5%)다. 챌린저만 봐도 15.8%/9.4%라 티어 필터 탓이 아니다. 원인은 미상이다.
4. 해석을 오도한다.
   - 페이지 코드(page-datasearch.js 489·710행)가 아이템 前四率을 top4_cnt/total(전체 보드)로 계산한다. 조건부 톱4가 아니라 사용률과 섞인 값이다.
   - minSampleSize=10이라 233건짜리 조합에도 S 등급이 붙는다.
   - 빈 item_id 행이 1개 있다.
5. 装备排行의 hero_list는 고유 ID가 8개뿐이다('18', 'Lux18' 등). 아이템별 추천 챔피언으로 쓸 수 없다.
6. 装备排行과 레거시에는 표본 수가 없다. 레거시 평균은 소수 한 자리(값 20종)다.
7. 기간이 좁다. 7·14일은 빈 응답이고, 페이지가 600만 게임 초과 조회를 막아 실버+ 3일이 한계다. 마스터+ 하루는 138개 중 99개가 1,000빌드 미만이다.
8. time_type=v는 TFT5·TFT17 등 옛 세트 행이 섞여 343개다. 정적 도감 equip.js는 16.17로 한 패치 늦다(벤치마크 확인).
9. 참고로 metatft 글로벌 평균 등수와의 순위 상관은 数据检索器 0.58, 装备排行 0.725, 레거시 0.816이다. 중국 메타 차이일 수도 있어 단독 근거로 쓰지는 않았다.
- **lolchess.gg 7.5점** — 강점: [직접 확인, 12:25~12:31Z]
- 응답마다 티어별 matchCount를 준다: 전체 412,658 / 마스터+ 33,948 / 다이아+ 105,621 / 플래+ 324,946 / 에메랄드+ 220,334 / 골드 이하 87,712경기. 아이템·챔피언 행마다 plays가 있어 표본을 표시하기 쉽다.
- 정확성: metatft와 교차 검증 결과가 일치한다(위 수치). avgPlacement는 126개 모두 placements/plays와 맞고, 불가능한 값은 0건이다.
- shard=kr로 KR 단독 아이템 통계를 받을 수 있다: 플래+ 78,095 / 다이아+ 15,550 / 마스터+ 5,241경기. 벤치마크의 'KR 단독 없음'은 메타 덱에만 해당한다. queueId=1160(더블업)은 20,253경기다.
- patchRevisions가 18.2 / 18.1d를 라벨과 함께 주고, 이전 패치(18.1d 249,099경기)도 조회된다.
- 상세 API가 인피니티 엣지 착용 챔피언 65개와 함께 가는 아이템(itemSynergyStats) 96~112개를 plays·평균 등수와 함께 준다. 이 '동반 아이템' 통계는 다른 두 소스에 없다.
- updatedAt이 11:25:41Z에서 12:25:39Z로 바뀌어, 관찰상 1시간 주기로 갱신된다.
- 키 126개가 모두 metatft DA_*와 일치한다. / 약점: [직접 확인]
- 표본이 metatft의 약 40%다(플래+ 3일 2.6배 차, KR 1.8배 차). 마스터+에서만 같다.
- 티어는 고정 버킷 6개, 서버는 global/kr뿐이다.
- 기간 파라미터가 조용히 실패한다. dt=3(기본)과 dt=7만 응답하고, dt=1·dt=5는 HTTP 200 빈 응답이다.
- metatft 대비 표본 비율이 3일 2.61배, 7일 1.33배로 달라진다. [추정] dt=3은 실제 3일이 아니라 UI 표기 '최근 2일'이고, dt=7은 이전 패치를 섞을 수 있다. 하지만 응답은 patch=1802로만 표시돼 확인할 수 없다.
- 부품 10개·포션 6개가 빠진 126개만 준다.
- 등수 분포 8칸이 없다(합계만 있음). 번들 190개 파일의 API 경로에도 아이템 일별 추세와 완성 스테이지 통계가 없다(추세는 meta-champion-daily-stats 뿐).
- 목록 API는 챔피언·동반 아이템 상위 5개만 준다. 전체를 받으려면 아이템별 상세를 126번 불러야 한다.
- 마스터+ 상세에서 인피니티 엣지 착용 챔피언 65개 중 45개가 500건 미만이다(5~10건 행 포함).
- **metatft.com 9.0점** — 강점: [직접 확인, 2026-09-15 12:31~12:45Z]
- 표본이 가장 크다. 글로벌 플래+ 3일은 6,787,000보드로 lolchess(2,599,568명)의 2.6배이고, KR 플래+ 3일은 1,106,032보드로 lolchess(624,760명)의 1.8배다. 7일은 11,887,600보드다. 마스터+는 267,920보드로 lolchess(271,584명)와 같다.
- 티어 범위가 가장 넓다. 랭크 10단계를 자유롭게 조합하고, 서버 15개(server=KR 실측), days 1~7, patch+b_patch(18.1d 7일 2,985,342보드 조회됨)를 고를 수 있다. permit_filter_adjustment로 필터 완화 여부를 응답에 표시한다.
- 정확성을 교차 검증했다. 플래+에서 양쪽 5,000건 이상인 공통 아이템 113개를 lolchess와 비교했다.
  - 평균 등수 차: 평균 0.020, 최대 0.125 / Spearman 0.988
  - 톱4 차: 평균 0.004
  - 인피니티 엣지 4.260 대 4.26, 가고일 돌갑옷 4.400 대 4.40
  - places[8]에서 평균·톱4·1위를 다시 계산해도 모순 0건이다.
- 반영 속도: 응답의 updated가 요청 시각과 같아 실시간 집계다. /patch는 18.2 시작 시각 2026-09-09T20:39Z를 준다.
- 깊이
  - 등수 분포 8칸
  - item_detail: 10일 일별 추세(18.1d/18.2 패치 경계 분리), 착용 유닛 68개와 유닛 전체 평균 대비 차이(예: 엘더드래곤이 인피니티 엣지 착용 시 3.801, -0.161)
  - item_stage_detail: 완성 스테이지별 승률
  - unit_items_processed: 아이템→상위 5유닛 역검색(142개)
- 아이템 키 142개가 lol.qq 142개와 정확히 같아 DA_* 결합이 쉽다. / 약점: [직접 확인]
- item_detail.units에 TFT18_Gromp(5건), TFT18_KogMaw(1건), TFT18_Akali(4건) 같은 비 DA 행이 섞인다. 수집기에서 걸러야 한다.
- 고티어 KR은 얇다. KR 마스터+ 3일은 44,816보드이고, 142개 중 82개가 1,000건 미만이다. 글로벌 마스터+도 38개가 1,000건 미만이다(부품·포션 포함).
- games.count(6,787,112)와 filter_adjustment.sample_size(6,731,136)가 달라 표본으로 무엇을 보여줄지 애매하다.
- unit_items_processed는 응답에 필터 범위가 없다(overall 2,691,536보드). region_hint도 호출마다 vn2/tw2로 바뀌어 모집단을 해석하기 어렵다.
- places는 아이템 개수 기준이다(보드당 약 10.0개 = 68,056,424/6,787,000). 따라서 '보드 기준 톱4'와 다르다. lolchess도 같다.
- 서버 목록에 중국이 없어 앱의 '중국 한정' 판정에는 못 쓴다.
- [추정] 실시간 집계라는 장점은 하루 1회 수집기에서는 체감이 작다.

가져올 아이디어:
- [lolchess] 아이템 상세에 '같이 가는 아이템' 목록을 넣는다. 수집기가 /api/v1/meta-deck-items/{DA}?tierId=3&patch=1802&revision=0&queueId=1100 의 itemSynergyStats(96~112개) 중 plays 1,000건 이상인 상위 10개와 평균 등수만 저장한다. metatft에는 아이템 단위 동반 아이템 필드가 없다. 단 lolchess 약관의 재배포 금지 조항 검토가 전제다.
- [lolchess] 필터를 한 손으로 누를 수 있는 칩으로 만든다. 플래+ / 다이아+ / 마스터+ 칩 3개와 글로벌/KR 토글을 두고, 수집기가 조합 6개를 미리 계산한다(metatft rank + server=KR로 생성). 각 행에 lolchess처럼 '표본 N건'을 표시하고, 1,000건 미만 행은 흐리게 처리한다. 확인한 바로는 KR 마스터+ 3일이면 142개 중 82개가 1,000건 미만이다.
- [lolchess] patchRevisions의 라벨('18.1d')을 빌려 '지난 패치 대비 Δ평균 등수' 배지를 단다. 값은 metatft patch=18.1&b_patch=d로 계산한다. b_patch를 비우면 18.2로 폴백하므로 반드시 채운다.
- [lolchess] 매일 수집할 때 교차 검증 가드를 둔다. metatft 플래+ 평균 등수와 lolchess tierId=3 값을 주요 아이템 20개로 비교하고, 차이 평균이 0.1을 넘으면 배포를 멈춘다. 평소 차이 평균은 0.020, 최대 0.125, Spearman 0.988로 확인했다.
- [lol.qq] '중국 서버' 탭은 인기도 중심으로 싣는다.
- 数据检索器의 build_rate 순위와 build_s 표본만 표시한다(글로벌 사용량 순위와 Spearman 0.988로 일관).
- 평균 순위와 前四는 레거시 equiprank 값만 '참고(소수 한 자리)'로 쓴다.
- 装备排行·数据检索器의 avg_rank는 싣지 않는다.
- 수집기에서 top1·top4 비율로 가능한 평균 범위를 검사해 모순 행을 버린다.
- [lol.qq] '중국에서 이 아이템을 누가 드나' 역검색을 만든다. 数据检索器 tft_equip_rank(ShowHero=1)와 tft_three_equip_rank의 'DA_챔피언#DA_아이템' 키를 쓴다. 정렬은 build_s(사용 수)로 하고, 최소 표본은 300~500 이상으로 올리며, 서버가 주는 S/A/B/C 등급은 버린다. 마스터+ 하루 1,935행 중 1,902행이 1,000건 미만이라 티어는 실버+ 기준을 권장한다.
- [lol.qq] 데이터 기준 라벨에 핫픽스 빌드를 표시한다(예: '16.18.817.4437 반영'). 수집기는 05:00 KST(=04:00 CST)에 돌므로 stime=etime=전날(CST)로 하루치 완결 데이터를 받는다. 이렇게 하면 0~2시 CST 당일 공백과 600만 게임 차단을 피할 수 있다.
- [lol.qq 반면교사] 앱의 톱4는 조건부 비율(톱4 인스턴스/사용 인스턴스)로 계산한다. lol.qq 페이지처럼 top4_cnt/전체 보드로 계산하면 사용률이 섞인다. 도움말에는 '아이템 개수 기준이라 보드 기준 톱4보다 높게 나온다'고 한 줄 적는다.

#### 모바일 사용성

모바일 사용성 관점 점수는 metatft 7점, lolchess 6점, lol.qq 2점이다.

[확인 방법]
- lolchess와 metatft: in-app 브라우저를 375×812로 맞춰 렌더링하고 DOM을 측정했다. 헤더 탭 정렬과 한글 검색은 실제로 눌러 봤다.
- lol.qq: 브라우저가 막혀 curl과 python으로 HTML, CSS, Vue 템플릿, 번들을 받아 분석했고 tft_equip_ranking을 직접 호출했다.
- CommunityDragon: ko_kr의 composition 필드를 확인했다.

[metatft가 1위인 이유]
- 첫 화면에 표 헤더와 3행이 들어온다.
- 옆으로 밀지 않고 보이는 폭에 티어 글자, 평균 등수, 등수 변화, 승률, 빈도가 모두 있다.
- 모든 열을 탭 정렬할 수 있고 한글 부분 검색이 된다.
- 표본 크기를 드러내고, 상세 페이지가 가로 스크롤 없는 세로 카드형이다.
- 감점 요인: 영상 광고, 글자 없는 아이콘 필터, 이름 없이 아이콘만 있는 챔피언, 잘리는 아이템명, 번역체와 용어 혼용, 조합표 없음.

[lolchess가 1점 뒤인 이유]
- 한국어 자연스러움, 글자 필터, 챔피언 이름을 행 안에 표시, 조합표까지 갖춘 완결성은 세 사이트 중 가장 좋다.
- 그러나 모바일 표 사용성이 약하다.
  - 첫 화면에 데이터 행이 없다.
  - 740px 표를 고정 열 없이 옆으로 밀어야 한다.
  - 게임 수를 숨긴 채 평균 등수로 정렬해 표본 적은 아이템이 1위로 뜬다.
  - 아이템 검색이 없다.

[lol.qq가 2점인 이유]
- 본 사이트가 min-width 1240px 데스크톱 전용이다.
- 상호작용이 호버에 의존하고, 중국어 전용이다.
- 아이템→챔피언 데이터가 깨져 있다.
- 모바일 H5가 있다는 점만 조금 반영했다.

[미확인·추정]
- lol.qq 모바일 H5의 실제 화면.
- lolchess 조합표가 부품 10종 격자라는 점(아이콘 120개와 CDragon 부품 10종에서 추정).
- 광고와 버튼 높이(스크린샷으로 어림).
- 스크롤 뒤 일부 스크린샷은 지연 렌더링으로 비어서 DOM 텍스트로 대신 확인했다.

[우리 앱에 주는 결론]
- metatft식 표 구성을 뼈대로 삼는다: 첫 폭에 핵심 지표, 티어 글자, 전 열 정렬, 한글 검색.
- 여기에 lolchess의 용어, 글자 칩, 조합표, 이름 인라인 패턴을 더한다.
- lol.qq 중국 수치를 DA_*로 결합한 두 번째 열을 붙인다.
- 조합표 원천인 CDragon ko_kr composition(DA_* 완성템 55개, 부품 10종 확인)은 인증이 필요 없어 매일 JSON으로 배포할 수 있다.

- **lol.qq.com/tft 2.0점** — 강점: - 모바일 H5 装备大数据 페이지가 따로 있다(/act/a20200224tft/page/datarank-equip-detail/s18).
  - HTML과 CSS로 확인한 것: viewport width=1125와 device-width 설정, 375px 기준 px CSS, 前四/登顶/排名/顶级弈子/筛选 문구.
  - in-app 브라우저가 lol.qq를 막아 실제 화면은 보지 못했다.
- PC 装备 페이지의 탐색 구조는 조합 찾기에 맞다(템플릿으로 확인).
  - 분류 탭과 '搜索装备...' 이름 검색이 있다.
  - 부품을 고르면 그 부품이 들어가는 완성템 목록(配方=合成, 효과, 适合英雄)이 나오고 日/周/月 전환 버튼이 있다.
- 装备排行은 前四/登顶/排名 정렬, 분류 필터, 每日/版本 전환을 갖췄다.
- 중국 서버 아이템 통계(avg_rank, top_4_rate, use_rate, equip_id는 DA_*)라서, 우리 앱이 두 번째 수치로 보여줄 수 있는 차별화 데이터다. / 약점: [직접 확인]
- 본 사이트가 폰을 지원하지 않는다.
  - comm.css의 body,html{min-width:1240px!important} 규칙이 375px 폰에도 1240px 레이아웃을 강제한다.
  - 확인한 @media 조건은 2180~2400px 대형 화면용뿐이었다.
  - 모바일 UA로 받아도 같은 HTML(200)이 오고, 번들에서도 UA 기반 이동 코드를 찾지 못했다.
- 아이템 툴팁과 装备排行의 '详情'(掌盟 QR)이 @mouseenter 호버로만 열린다. 터치에서는 동작하지 않는다.
- 중국어 전용이다(zh-cn, 登场/前四/登顶/平均排名). 한국 사용자는 번역 없이 읽을 수 없다.
- 아이템→챔피언 정보가 망가져 있다.
  - tft_equip_ranking을 직접 호출하니 top_1_hero_id, top_2_hero, top_3_hero 값이 '18', 'Lux18', 'Taric18'처럼 잘려 있었다.
  - 142개 아이템 전체에서 고유값이 13개뿐이라 顶级弈子 열은 믿을 수 없다.
- 모바일 H5에는 83px짜리 '去掌盟' 앱 유도 배너가 붙는다. PC에서는 QR로만 들어가는 공유용 단일 페이지라, 폰에서 아이템 목록을 훑어보는 흐름이 없다.
- **lolchess.gg 6.0점** — 강점: [직접 확인: 브라우저 375×812 렌더링과 DOM 측정]
- 한국어가 가장 자연스럽다.
  - 용어가 평균 등수, TOP4, 승률, 픽률, 게임 수, 챔피언 TOP5로 일관된다.
  - 유형 필터가 글자 탭(전체/일반/상징/유물/찬란)이다.
- 아이템 행마다 챔피언 TOP5가 43px 아이콘과 한글 이름 텍스트로 붙는다.
  - 각 챔피언은 /champions/set18/* 로 연결된다.
  - API도 아이템 126개 모두에 정확히 5명씩 준다.
- 통계표, 조합표, 아이템→챔피언 세 요소를 모두 갖췄다.
  - 조합표(/items/set18/table)는 26px 아이콘 120개로 이루어진 격자이고, 375px 안에 가로 넘침 없이 들어간다.
  - 부품 10종의 11×11 격자로 추정한다. CommunityDragon의 부품 10종과 개수가 맞는다.
- 탭이 URL로 나뉘어(/table, /guide, /three-cores) 딥링크가 된다.
  - 상세는 /items/set18/DA_InfinityEdge와 /items/set18/InfinityEdge 둘 다 200이다.
  - 상세 표(65행)에는 챔피언별 게임 수가 나온다.
- 수치 열 5개(평균 등수, TOP4, 승률, 픽률, 게임 수)는 탭으로 정렬된다. TOP4를 탭하니 스테락의 도전이 5위에서 3위로 오르며 다시 정렬됐다.
- 페이지 전체의 가로 넘침은 없다(docScrollW 375). / 약점: [직접 확인]
- 첫 화면(812px)에 아이템 행이 하나도 없다.
  - 표가 페이지 y=801에서 시작한다.
  - 그 위를 배너, 섹션 칩 2줄, 빈 광고 영역(스크린샷 기준 약 140px), 탭이 차지한다.
  - 상세 페이지의 챔피언 표도 y=1007에서 시작한다.
- 표가 740px라 375px 화면에서 옆으로 밀어야 한다.
  - 아이템 열이 259px를 차지해, 첫 폭에는 #, 이름, 평균 등수만 보인다.
  - TOP4, 승률, 픽률, 챔피언 TOP5는 옆으로 밀어야 보인다. 그런데 이름 열이 고정되지 않아(position static) 밀면 이름이 사라진다.
  - 상세 표도 610px다.
- 모바일에서 '게임 수' 열 폭이 0이 되어 표본 크기가 숨는다. 기본 정렬은 평균 등수 오름차순이다. 그래서 픽률 0.03%(495판)인 전략가의 방패가 1위로 뜨는데, 사용자는 표본을 확인할 방법이 없다.
- 아이템 페이지에 아이템 검색이 없다. 입력창은 전역 전적검색뿐이고 i18n의 items.* 에도 검색 문구가 없다. 126행을 스크롤해서 찾아야 한다.
- 조합표 아이콘이 26px라 손가락으로 누르기에 작다. 떠 있는 채팅 버튼(약 60px)이 격자 오른쪽 아래 칸을 가린다.
- 탭 이름이 '아이템 통/계', '아이템 조/합표'처럼 단어 중간에서 줄바꿈된다.
- [벤치마크 조사로 확인] 약관이 데이터 재배포를 금지한다. 우리 앱은 화면 패턴만 참고할 수 있고 데이터는 가져올 수 없다.
- **metatft.com 7.0점** — 강점: [직접 확인: 브라우저 375×812 렌더링과 DOM 측정]
- 첫 화면에 표 헤더(y≈637)와 1~3행이 들어온다.
- 표 전체는 514px지만 옆으로 밀지 않고 보이는 345px 폭에 핵심 지표가 다 들어간다: 아이콘+이름, 티어 배지(S), 평균 등수, 등수 변화 예상, 승률, 빈도(판수+%).
  - 옆으로 밀어야 보이는 건 '인기 챔피언' 열뿐이다. 행 높이는 49px다.
- 헤더 7개를 모두 탭해 정렬할 수 있다. '빈도'를 탭하니 ▲ 표시와 함께 가고일 돌갑옷, 워모그의 갑옷 순으로 다시 정렬됐다.
- 표 전용 검색창에서 한글 부분 검색이 된다. '무한'을 입력하니 무한의 대검, 무한한 삼위일체, 찬란한 무한의 대검 3행만 남았다.
- 표 위에 '분석된 덱 6,787,656'과 '최근 업데이트 3분 전'이 있어 표본과 갱신 시점이 바로 보인다.
- 필터 칩(랭크 / 18.2 / 지난 3일 / 플래티넘+)이 한 줄에 들어간다.
- 상세 페이지(/items/DA_InfinityEdge)는 가로 스크롤 요소가 하나도 없는 세로 카드형이다(docScrollW 375). 한 손으로 내리기만 하면 된다. 담긴 내용:
  - 조합법
  - 평균 등수 4.19(+0.02 vs 18.1d), 선택률 49.2%(+6.8%)
  - 추천 착용 챔피언 6명(평균 등수, 등수 변화, 플레이율)
  - 2~7단계 라운드 승률, 단계별 착용 챔피언
- 아이템명은 한글 공식명이다(ko_kr 사전으로 142/142 매핑). / 약점: [직접 확인]
- 자동재생 영상 광고가 첫 화면 위쪽 약 225px(약 28%)를 차지한다. 스크롤하면 헤더 아래에 약 115px 높이로 고정된다(닫기 X 있음). 높이는 스크린샷으로 어림한 값이다.
- 유형 필터가 글자 없는 20px 아이콘 버튼 5개다. alt도 영어('Normal Item' 등)라 한국 사용자가 뜻을 알기 어렵다.
- 추천 착용 챔피언이 36px 아이콘뿐이다. 한글 이름은 alt에만 있고 화면 글자는 숫자뿐이라, 챔피언 얼굴을 모르면 읽을 수 없다.
- 이름 열이 116px라 '찬란한 체…'처럼 잘린다. 이름 열이 고정되지 않아(position static) 옆으로 밀면 무슨 아이템인지 사라진다.
- 번역체와 용어 혼용이 있다(로케일 파일과 렌더링 텍스트로 확인).
  - 같은 흐름에서 선택률, 플레이율, 빈도를 섞어 쓴다.
  - '추천 아이템 보유 챔피언', '등수 변화 예상'이 어색하다.
  - 찬란한/빛나는, 상징/엠블럼이 함께 쓰인다.
  - '조합'이 덱(comp) 뜻이라 아이템 조합과 겹친다.
  - html lang이 en이다.
- 조합표(부품 격자)가 없다. 라우트는 /items, /items/:id, /items/{artifact,support,emblem,radiant}뿐이다.
- 기본 정렬 1위가 1,233판(0.0%)인 찬란한 체력 물약이다. 판수 열이 함께 보여서 잘못 읽을 위험은 lolchess보다 작다.

가져올 아이디어:
- [lolchess 조합표를 폰에 맞게] 부품 10종 11×11 조합표를 한 화면 격자로 만든다.
- 데이터: CommunityDragon ko_kr의 items[].composition. 직접 확인한 결과 DA_* 완성템 55개, 부품 10종이다(B.F. 대검, 곡궁, 쓸데없이 큰 지팡이, 여신의 눈물, 쇠사슬 조끼, 음전자 망토, 거인의 허리띠, 연습용 장갑, 뒤집개, 프라이팬).
- lolchess처럼 가로 넘침 없이 폭을 꽉 채우되, 칸을 26px보다 크게(360dp 기준 약 30dp) 잡는다.
- 칸마다 평균 등수 색 점을 얹는다. 수집기가 metatft places와 lol.qq avg_rank를 DA_*로 붙여 넣는다.
- 부품 머리칸을 탭하면 그 행과 열을 강조한다(lolchess의 '강조 효과').
- 칸을 탭하면 하단 시트에 효과와 착용 상위 5명을 띄운다.
- [lol.qq 装备의 '부품 선택 → 완성템 목록'] 아이템 목록 위에 부품 10종 칩 줄을 둔다.
- 칩을 하나나 둘 탭하면 그 부품이 들어간 완성템만 평균 등수 순으로 남긴다.
- '지금 가진 부품으로 뭘 만들까'를 한 손 두 번 탭으로 해결한다.
- 데이터는 composition 역색인이라 수집기에서 만든다.
- 같은 컴포넌트를 게임 위 오버레이에도 쓸 수 있다.
- [lol.qq 중국 서버 표본과 환비 표시] 아이템 행에 두 소스의 평균 등수를 나란히 보여준다(예: '중국 3.92 · 글로벌 4.10').
- 차이가 기준 이상이면 '중국에서 강함' 배지를 단다.
- 수집기가 lol.qq tft_equip_ranking(avg_rank, top_4_rate, use_rate, equip_id=DA_*)과 metatft items_matches(places[8])를 DA_*로 결합한다.
- 전날 배포한 items JSON과 비교해 ▲▼ 변화를 스스로 계산한다. 소스가 환비를 주지 않아도 된다.
- 착용자 표시에는 lol.qq의 top_*_hero 필드를 쓰지 않는다. 직접 확인한 결과 값이 깨져 있다.
- [lolchess 챔피언 TOP5 인라인을 가로 스크롤 없는 2줄 카드로] 740px 가로표 대신 행을 두 줄로 쌓는다.
- 1줄: 아이콘, 이름, 티어, 평균 등수, TOP4, 판수.
- 2줄: 착용 상위 5명의 28~32dp 아바타와 한글 이름 텍스트. metatft 상세처럼 아이콘만 두지 말고 이름을 반드시 붙인다.
- 착용자 데이터: metatft tft-comps-api/unit_items_processed의 itemNames[DA_*].units(상위 5명, 인증 없음, 68KB). 한글명은 CDragon ko_kr에서 가져온다.
- lolchess 데이터는 약관 문제로 쓰지 않고 화면 패턴만 가져온다.
- [lolchess 한국어 용어와 글자 탭] 앱 용어집을 lolchess i18n 기준으로 고정한다.
- 지표: 평균 등수, TOP4, 승률, 픽률, 게임 수.
- 분류: 전체, 일반, 상징, 유물, 찬란.
- 유형 필터는 metatft식 글자 없는 20px 아이콘 대신 글자 칩으로 만든다.
- 덱은 '덱', 아이템 제작은 '조합'으로 나눠 쓴다. metatft는 comp를 '조합'으로 번역해 헷갈린다.
- 선택률, 플레이율, 빈도를 섞어 쓰지 않는다.
- [lolchess 결함을 거꾸로 적용: 표본 항상 표시] 모든 아이템 행과 착용자 행에 'n판'을 작게 늘 보여준다. lolchess는 모바일에서 게임 수 열 폭이 0이다.
- 평균 등수로 정렬할 때 표본 하한(예: 픽률 0.1% 또는 N판 미만) 아래 행은 흐리게 하거나 '표본 적음' 구역으로 내린다.
- 수집기가 plays와 sum(places)를 JSON에 넣는다.
- 근거: lolchess 모바일 기본 정렬에서 495판짜리 전략가의 방패가 1위로 뜬다.
- [lolchess DA_* 딥링크] 아이템 상세 화면의 라우트 키를 DA_* apiName으로 통일한다(예: item/DA_InfinityEdge).
- lolchess에서도 /items/set18/DA_InfinityEdge가 그대로 열린다.
- 목록, 조합표, 덱 상세, 오버레이 어디서든 같은 키로 상세를 연다.
- 소스 결합 키와 화면 키가 같아져 매핑 계층이 줄어든다.
- [lol.qq 数据检索器의 '챔피언#아이템' 키] 중국 표본의 '이 아이템을 누가 드나'를 datasearch 키(형식 DA_Draven18#A|B|C)로 채운다.
- tft_equip_ranking의 착용자 필드가 깨져 있어 대신 쓰는 방법이다.
- 4번 카드의 둘째 줄에 '중국/글로벌' 전환을 둔다.
- 키 형식은 벤치마크 조사 결과이고, 이번에 직접 호출하지는 않았다.

#### 구현 가능성

[직접 재확인] 2026-09-15 12:29~12:50 UTC에 curl로 호출했다(Chrome/120 UA, 쿠키·Referer 없음).\n\n1) lol.qq: POST mlol.qt.qq.com/go/exploit/proxy (tft_equip_ranking, d)\n- 200, 59,649B, 0.38초.\n- 142아이템, dtstatdate 20260914.\n- CommunityDragon ko_kr와 대조했다(오늘 새로 받음, Last-Modified 2026-09-12). apiName이 141/142 일치하고 DA_Artifact_Hullcrusher만 없다. 이 아이템은 세 소스 모두에서 없다.\n- hero_list 버그를 재현했다. 고유 hero_id가 8개이고 \"18\"이 248회 나온다.\n- req_params를 비우면 200, result 0, 빈 details가 온다.\n\n数据检索器 tft_equip_rank (version 16.18.816.5012, 2026-09-14, ShowHero=1), limit별 결과:\n- 2000: 잘림(최소 build_s 737, 108아이템)\n- 5000: 140아이템\n- 8000과 10000: 7,395행(최소 build_s 10, 142아이템×65챔피언, 1.49MB)\n- 20000: 빈 data\n- version을 '16.18'로 주면 빈 data\n\n정적 파일:\n- equip.js의 formula는 벤치마킹 설명과 달리 부품 equipId가 아니라 DA_Component_* 이름이다. CommunityDragon composition과 55/55 같다.\n- versionconfig.json은 16.17을 가리키지만 16.18 경로도 200이다.\n\n2) lolchess: GET tft.dakgg.io/api/v1/meta-deck-items?tierId=1&from=web\n- 200, CloudFront ICN, max-age=60.\n- 126아이템, 목록의 itemChampionStats는 전부 5개.\n- 상세 /meta-deck-items/DA_InfinityEdge는 65챔피언.\n- tierId를 빼면 조용히 1로 대체된다.\n- data/items?hl=ko는 272개이고 한글 이름이 CommunityDragon과 263/263 같다. 조합 필드는 없다.\n- 약관 페이지에서 무단 복제·제3자 제공 금지 조항을 직접 확인했다.\n\n3) metatft: GET api-hc.metatft.com/tft-stat-api/items_matches\n- 200, 142행, games 6,786,184.\n- unit_items_processed: 200, 142아이템×5유닛, 유닛 키 53/53 일치.\n- item_detail(DA_InfinityEdge): 유닛 68개.\n- 잘못된 키(TFT_Item_InfinityEdge): 200, 빈 배열.\n\n차단: 소스마다 8회 연속 호출했고 전부 200, 429는 없었다.\n\n결합: 아이템 키 집합은 lol.qq 142 = metatft 142이고, lolchess 126은 그 부분집합이다(빠진 16개 = 부품 10 + 물약 6). 이 통계 API들은 접두사 정규화 없이 문자열 그대로 결합된다.\n\n조합표: 세 사이트 통계 API 어디에도 조합 정보가 없다. CommunityDragon composition이 공통 해법이다.\n\n[채점]\n\nmetatft 8.5\n- 단일 GET이다.\n- 원시 정수 분포와 표본 크기를 준다.\n- 키가 lol.qq와 완전히 같다.\n- 역검색 데이터를 호출 한 번에 받는다.\n- 감점: 조용한 빈 응답, CDN 캐시가 없는 오리진 부하, 약관 미확인.\n\nlol.qq 7.0\n- 아이템별 추천 챔피언을 중국 데이터로, DA_* 키로 가장 풍부하게 준다.\n- 감점: 빌드 문자열·CST 날짜·limit 조정 같은 비공개 파라미터에 의존한다.\n- 감점: 조용한 실패가 세 가지다.\n- 감점: 레거시 API가 깨져 있고 숫자가 문자열이다.\n\nlolchess 6.0\n- 기술적으로는 가장 쉽다.\n- 감점: 확인된 약관 조항이 공개 저장소 배포 구조와 충돌한다.\n- 감점: 목록 역검색이 상위 5명뿐이다.\n- 감점: 부품이 빠져 있다.\n\n[추정·미확인]\n- lol.qq limit의 정확한 상한(10000~20000 사이)\n- 프론트보다 큰 limit 사용이 제한을 부를지\n- ShowHero avg_rank의 의미\n- metatft 약관\n- 대량 호출 시 한도\n\n증거 파일: C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_item

- **lol.qq.com/tft 7.0점** — 강점: [직접 확인] 인증·서명·Referer 없이 동작한다.
- POST 프록시에 okhttp UA로 8회 연속 호출해 전부 200이었다.
- 이미 수집기의 주 소스라 인프라를 그대로 쓸 수 있다.

数据检索器 tft_equip_rank (ShowHero=1, limit=8000)
- 한 번 호출로 중국 서버의 '챔피언#아이템' 전체 행렬 7,395행을 받는다.
  - 142아이템×65챔피언, 1.49MB, 약 2초.
  - 최소 build_s가 minSampleSize와 같은 10이라 잘리지 않았다.
- 키는 양쪽 모두 DA_*이다. 2000행 응답을 CommunityDragon과 대조하니 챔피언 65/65, 아이템 107/108이 일치했다.
- 아이템 단독 모드는 서버가 계산한 S/A/B/C 티어를 준다.

정적 equip.js
- formula가 DA_Component_* 이름 그대로다. CommunityDragon composition과 55/55가 같다.

아이템 키 집합은 metatft와 142/142 똑같다. / 약점: [직접 확인]
조용한 실패가 세 가지다. 모두 result 0과 빈 data로 온다.
- req_params가 비었을 때
- version을 '16.18'처럼 짧게 줬을 때
- limit=20000일 때

호출 절차가 까다롭다.
- 전체 빌드 문자열을 tft_recent_versions로 먼저 받아야 한다(예: 16.18.816.5012).
- 날짜는 CST 기준이다.
- 프론트 기본값인 limit 2000이면 결과가 잘린다(최소 build_s 737, 108아이템). 3000이면 125아이템, 5000이면 140아이템이다.

레거시 tft_equip_ranking의 hero_list 버그를 재현했다.
- 142아이템 전체에서 고유 hero_id가 8개뿐이다.
- "18"이 248회 나온다.
- 따라서 추천 챔피언 용도로 쓸 수 없다.

데이터 품질
- 숫자가 전부 문자열이다.
- 키가 빈 문자열('')인 행이 섞여 있다.
- ShowHero의 avg_rank는 의미가 불명확하다. 무한의 대검-ElderDragon이 5.452인데 티어는 S다.

정적 파일 경로
- versionconfig.json은 아직 16.17 경로를 가리킨다.
- 16.18 경로의 파일이 따로 존재하고, 16.17과 효과 문구 59행이 다르다.
- 이름은 중국어뿐이라 CommunityDragon으로 한글화해야 한다.

[추정]
- 프론트보다 4배 큰 limit을 계속 쓰면 서버가 제한을 강화할 수 있다.
- 텐센트 비공개 API라 약관상 회색지대다.
- **lolchess.gg 6.0점** — 강점: [직접 확인] 기술적으로는 가장 쉽다.
- meta-deck-items는 단일 GET이다.
  - CloudFront ICN 캐시(max-age=60)를 거치고 ACAO는 *이다.
  - python UA로, from 파라미터 없이 불러도 200이다.
  - 8회 연속 호출해 전부 200이었다.
- 통계 키가 DA_*다. 아이템은 CommunityDragon과 125/126, 챔피언은 51/51이 일치한다.
- plays/wins가 정수이고, tierId 0~5마다 matchCount를 준다(tier0 412,658, tier1 33,948).
- data/items?hl=ko의 한글 이름은 CommunityDragon ko와 263/263이 같다(차이 0).
- 아이템 상세 API는 아이템별 착용 챔피언 전체를 준다(무한의 대검 65명). / 약점: [직접 확인] 차단·법적 위험이 가장 크다.
- 이용약관(/about/terms_and_service)에 '회사 사전 승낙 없이 서비스 외 목적으로 복제하거나 제3자에게 제공하는 행위' 금지 조항이 있다.
- 공개 GitHub 저장소와 Actions로 가공한 JSON을 배포하는 우리 구조와 정면으로 충돌한다.

기능 공백
- 목록의 itemChampionStats는 아이템마다 정확히 5명뿐이다. 전체 역검색을 하려면 상세 API를 126회 불러야 한다.
- 통계에서 부품 10종과 물약 6종이 빠져 126개뿐이다(lol.qq·metatft는 142개).
- 사전에 조합(composition) 필드가 없다.
- 글로벌 샤드만 있다.

조용한 실패와 형식
- tierId를 빼면 오류 없이 tierId=1로 대체된다.
- avgPlacement가 문자열이다.
- **metatft.com 8.5점** — 강점: [직접 확인] 수집이 가장 단순하고 스키마가 가장 깨끗하다.
- 단순 GET이고 인증·Referer·쿠키가 모두 필요 없다. ACAO는 *이다.
- days 값을 바꿔 캐시를 피하며 8회 연속 호출했고 전부 200이었다(0.69~1.01초).
- 수집기가 이미 metatft를 쓰고 있어 인프라를 그대로 쓸 수 있다.

items_matches
- 142행을 준다.
- 아이템 키 집합이 lol.qq와 142/142 똑같다. CommunityDragon apiName과는 141/142가 일치한다.
- places[8]가 원시 정수라 평균 등수·톱4·승률을 수집기에서 같은 공식으로 다시 계산할 수 있다.
- games.count(6,786,184)와 filter_adjustment.override_applied(false)로 표본 크기와 필터 보정 여부를 확인할 수 있다.

unit_items_processed
- 한 번 호출로 아이템→상위 5유닛, 유닛→상위 10아이템을 받는다(무압축 67,703B).
- 유닛 키 53/53이 CommunityDragon과 일치한다.

item_detail(DA_InfinityEdge)
- 착용 유닛 68개의 places[8]와 일별 추세를 준다.

압축
- curl에서 Accept-Encoding을 빼면 무압축으로 온다. 이 헤더를 붙이면 zstd로 온다. / 약점: [직접 확인]
- 잘못된 키(TFT_Item_InfinityEdge)를 넣어도 200과 빈 배열이 온다. 응답이 비었는지 반드시 검증해야 한다.
- 응답에 캐시 헤더가 없다. 요청마다 오리진에서 0.7~1.0초씩 집계한다.
- unit_items_processed의 units에는 유닛 이름 5개만 있다. 아이템-유닛 쌍별 통계를 얻으려면 item_detail을 아이템마다(142회) 따로 불러야 한다.
- region_patch_adjustment.region_hint가 'na1'로 오는데, 이 필드의 의미는 확인하지 못했다.

[벤치마킹 기록과 추정]
- permit_filter_adjustment=true면 랭크 필터가 완화될 수 있으므로 false로 고정해야 한다.
- 경로 개정 흔적(unit_detail2 등)이 있어 스키마가 바뀔 수 있다.
- 142회 크롤링은 오리진 부하 때문에 레이트리밋을 부를 수 있다(추정).
- 이용약관의 스크래핑 조항은 확인하지 못했다.
- 글로벌 표본이라 중국 데이터가 아니다.
- 한글 이름과 조합표는 CommunityDragon에서 가져와야 한다.

가져올 아이디어:
- [lol.qq] 데이터 검색기 tft_equip_rank(ShowHero=1, limit=8000)를 수집기에 넣는다.
- 호출 한 번으로 중국 서버의 챔피언#아이템 전체 행렬(7,395행, DA_*#DA_*)을 받는다.
- '아이템별 추천 챔피언' 화면에 중국 기준 열을 더하고, metatft item_detail(글로벌)과 같은 DA_* 키로 나란히 보여준다.
- 수집 절차:
  1) tft_recent_versions에서 전날(CST) 하루를 온전히 포함하는 전체 빌드 문자열을 고른다.
  2) stime=etime=전날로 호출한다.
  3) data가 비면 limit 5000으로 다시 부른다.
  4) 최소 build_s가 10보다 크면 결과가 잘린 것으로 보고 로그를 남긴다.
- [lol.qq] 아이템 단독 tft_equip_rank가 주는 서버 계산 S/A/B/C 티어를 '중국 티어' 배지로 쓴다.
- 키가 빈 문자열('')인 행은 버린다.
- 글로벌 티어는 metatft places[8]로 계산해 함께 표시한다.
- [lol.qq] 装备 페이지의 '부품을 누르면 그 부품이 들어가는 완성템과 각 통계' 흐름을 폰 한 손 UX로 옮긴다.
- 조합은 CommunityDragon ko_kr composition(DA_* 55개)으로 채운다.
- 통계는 metatft items_matches로 채운다.
- lol.qq equip.js formula(현재 55/55 동일)는 패치 날 교차검증에만 쓴다.
- versionconfig.json이 한 패치 늦으므로 다음 패치 경로를 먼저 조회하고, 404면 기존 경로로 돌아간다.
- [lol.qq] 레거시 tft_equip_ranking의 hero_list와 top_1_hero_id는 추천 챔피언에 쓰지 않는다.
- 고유 hero_id가 8개뿐이고 "18"이 248회 나와 깨져 있다.
- 이 API를 쓰더라도 아이템 단위 수치만 사용한다.
- [lolchess] 아이템 표 UI만 참고하고 데이터는 수집하지 않는다.
- 표 구성: 평균 등수 / TOP4 / 승률 / 픽률 / 챔피언 TOP5 아이콘.
- 티어 필터 옆에 표본 경기 수를 표시한다.
- 데이터는 metatft games.count와 lol.qq total로 채운다.
- lolchess API는 약관의 무단 복제·제3자 제공 금지 조항 때문에 수집하지 않는다.
- [lolchess] 아이템 티어표에서 부품 10종과 물약 6종을 뺀다(142→126).
- 부품은 조합표 격자로만 보여준다. 행과 열이 부품이고 각 칸이 완성템이다.
- 격자는 CommunityDragon composition으로 만든다.

### 증강

| 관점 | lol.qq | lolchess | metatft | 관점 1위 |
|---|---:|---:|---:|---|
| 데이터 품질 | 6.0 | 2.5 | 3.0 | lol.qq.com/tft |
| 모바일 사용성 | 2.5 | 7.0 | 5.0 | lolchess.gg |
| 구현 가능성 | 7.0 | 3.0 | 4.5 | lol.qq.com/tft |

#### 데이터 품질

데이터 품질(표본·티어 범위·정확성·반영 속도·깊이/해석)만으로 채점했다. 벤치마크 JSON 을 그대로 믿지 않고 2026-09-15 12:40~13:00 UTC 에 세 사이트를 직접 다시 호출해 교차 검증했다. 호출 결과와 분석 파일은 C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_aug 에 있다.

[결론의 핵심, 직접 확인]
시즌 18 증강의 실제 성적 통계는 lol.qq 에만 있다.
- metatft: unit_augments 의 augments 가 빈 문자열(1,038,927판), augment_unit_detail 은 [], augments_full2·augments 는 HTTP 500, Riot/앱 경기 JSON 에는 증강 키가 없다.
- lolchess: meta-deck-augments 가 비어 있고 매치 augments 도 모두 비어 있다. 화면 번역 문자열에 Riot 정책으로 시즌 13부터 증강 통계를 제공하지 않는다고 적혀 있다.
- 따라서 두 곳의 증강 티어는 표본이 0인 편집 의견이다. 두 편집 티어도 공통 249개 중 53%만 같은 등급이라 정확성의 기준으로 삼기 어렵다.

[lol.qq 6점]
- 가점: 유일한 실측 수치(행별 사용 수 113~9,705), 5개 티어 구간, 하루 단위 갱신, DA_* 결합 손실이 거의 없음.
- 감점 1: 전체 증강 순위 탭이 폐지됐다(whitelist 101 에 더해 코드가 요청을 막음). 증강 목록 전체의 통계 티어를 만들 수 없다.
- 감점 2: 덱별로 평균 등수 상위 5개만 준다.
- 감점 3: 사용률 분모가 덱 판수와 맞지 않는다(약 2~36배, 28행 중 3행은 증강 n 이 덱 n 초과).
- 감점 4: 단계별 평균에 표본 수가 없다('0' 3개, 5판 이하로 보이는 값 11개).
- 감점 5: 상위 티어가 빈약하다(다이아+ 30%, 마스터+ 10%).
- 감점 6: 목록과 상세의 날짜 표기가 하루 어긋난다.
- 모든 문제는 수집기 가공(표본 표시, 사용률 미사용, 0 제거)으로 상당 부분 줄일 수 있어 중간 이상 점수를 줬다.

[metatft 3점과 lolchess 2.5점]
둘 다 통계가 없어 낮다.
- metatft 가 반 점 높은 이유: 작성자와 당일 갱신 시각을 준다. 덱별 증강 티어가 28/54개 덱에 있어 '증강별 추천 덱'의 재료가 된다. manual_tags 로 해석을 돕는다.
- metatft 의 약점: 덱별 목록의 S 가 51%라 변별력이 낮고, 등급이 매겨진 키 5개가 시즌 18 풀에 없다.
- lolchess 의 장점: 티어가 S~D 로 더 고르게 퍼져 있다.
- lolchess 의 약점: 작성자 표기가 없고 약 31시간 늦게 갱신됐다. 지난 세트 키가 섞여 있고, 덱별 증강은 가이드 덱 30개뿐이다.

[벤치마크 수정 사항, 직접 확인]
- lolchess 티어는 S/A/B 가 아니라 S~D(D 3개)다.
- lol.qq tft_augment_rank 는 화이트리스트 문제만이 아니다. 프론트 코드에서 탭이 폐지돼 요청하지 않는다.
- metatft augments_full2(벤치마크 미호출)도 500 이다.
- lol.qq fightdetail 저장 샘플의 augmentsStr 8개는 모두 비어 있었다. 다만 수정본 파일이라 원본도 비어 있는지는 확인하지 못했다.

[추정으로 구분할 부분]
- lol.qq 증강 집계가 표시된 덱보다 넓은 묶음에서 나왔다는 해석. 어떤 묶음인지는 검증한 네 가지 그룹 합으로도 설명되지 않았다.
- 단계별 딱 떨어지는 값이 소표본이라는 해석. 값의 형태로 본 하한일 뿐이다.
- metatft distance 가 가이드-클러스터 매칭 거리라는 해석.
- 중국 서버 증강 성적이 한국 메타와 다를 수 있다는 점.

- **lol.qq.com/tft 6.0점** — 강점: [직접 호출, 2026-09-15 약 12:40~13:00 UTC] 세 곳 중 유일하게 시즌 18 증강의 실제 성적 통계가 있다(중국 서버 랭크). 인증 없이 호출된다.
- 덱 목록 tft_lineup_group_list 의 rune_id_group 에 덱별 상위 증강 ID가 있다.
- 덱 상세 tft_lineup_all_detail 의 augment_data 에는 평균 등수, 사용 수(use_num), 사용률, 선택 단계(1/2/3)별 평균 등수가 있다.
- 티어 구간이 5개다: 전체, 마스터+, 다이아+, 골드~에메랄드(사이트 기본값), 골드 이하.
- 증강 행이 있는 덱 비율: 전체 359/395(91%), 골드~에메랄드 313/356(88%), 골드 이하 157/218(72%).
- 확인한 증강 행의 사용 수는 113~9,705판이다.
- 하루 단위로 갱신되고 라이브 빌드 16.18.817(2026-09-14 16시 CST 시작)을 따라간다.
- 통계에 나온 증강 ID 142개가 모두 hex.js 풀(265개)에 있다. metatft 한국어 사전으로 142개 모두 한글 이름이 붙는다. CommunityDragon 은 tp2·255 기준 136개 중 135개다. DA_* 결합 손실이 거의 없다.
- 편집 덱 26개 모두에 hexbuff(우선/차선 증강)가 있다. / 약점: [직접 확인]
- 전체 증강 순위를 쓸 수 없다. tft_augment_rank 는 result 101 'not in white list' 이고, page-datasearch.js 주석과 코드에서 이 탭이 폐지돼 요청 자체를 막고 있다. 증강 전체 목록의 티어를 통계로 만들 수 없다.
- 덱마다 평균 등수가 좋은 순서로 최대 5개만 준다. 보여 준 행들의 사용률 합은 7.7%~50%라 대부분의 선택이 빠진다. 표본이 작아도 평균 등수가 좋으면 위로 올라온다.
- 사용률 분모가 공개되지 않고 덱 판수와도 맞지 않는다. use_num÷use_rate 가 덱 판수의 약 2~36배다(8,723 대 239). 28행 중 3행은 증강 사용 수가 덱 전체 판수보다 크다(DA_EarlyLearnings 9,705 대 덱 1,901). 3배수로도, 같은 특성·캐리 그룹 합계로도 설명되지 않아 집계 범위를 알 수 없다.
- 단계별 평균 등수에는 표본 수가 없다. 84개 값 중 3개가 데이터 없음을 뜻하는 '0' 이다. 11개는 '4', '5', '3.5' 처럼 딱 떨어져 5판 이하 표본과 맞는다(표본 크기 자체는 추정).
- 상위 티어가 빈약하다. 증강 행이 있는 덱이 다이아+ 23/76(30%), 마스터+ 1/10(10%)이고, 마스터+ 덱 판수 합은 1,052다.
- 날짜 표기가 어긋난다. 목록은 dtstatdate 20260915 인데 같은 수치의 상세는 20260914 다. 마스터+ 상세는 20260913 에 증강이 비어 있었다.
- 정적 사전 hex.js 는 16.17(2026-09-01)로 한 패치 늦다.
- 하루치 중국 서버 표본이라 한국 메타와 다를 수 있다(추정).
- **lolchess.gg 2.5점** — 강점: [직접 호출]
- 한국어 증강 사전 /data/augments?hl=ko: 986개. lol.qq 풀 265개 중 254개에 한글 이름과 설명이 있다.
- 편집 증강 티어 /data/augment-tiers: 253개(S14/A84/B105/C44/D3). 벤치마크의 'S/A/B'와 달리 D까지 쓰고, metatft 보다 등급이 고르게 퍼져 변별력이 조금 낫다.
- 편집 가이드 덱 30개(set18)에 증강이 3개씩 있다(1개 덱은 1개). 증강 키 37개가 모두 lol.qq 시즌 18 풀의 DA_* 로 대응된다.
- 2-1/3-2/4-2 증강 등급 등장 확률표가 있다(API가 아닌 번들 JSON).
- 화면에 '시즌 13 부터는 Riot Games 정책에 따라 증강체 통계가 제공되지 않습니다'라고 밝혀, 통계가 없는 이유를 투명하게 알린다. / 약점: [직접 확인] 통계 표본이 0이다.
- meta-deck-augments 는 {"patchRevisions":[]} 다. meta-decks 의 augments 는 [None,None,None], 매치·최근 1위 덱의 augments 도 모두 비어 있다.
- 티어에 작성자 표기가 없어 근거와 정확성을 검증할 수 없다.
- updatedAt 이 2026-09-14 05:40 UTC 로 조회 시점보다 약 31시간 늦어, metatft(당일 갱신)보다 반영이 느리다.
- 목록에 TFT7_Augment_BestFriends2 같은 지난 세트 키가 빈 등급으로 섞여 있다. 등급이 매겨진 키 중 DA_NestingDolls, TFT_Augment_JustSlayer 는 시즌 18 풀에 없다.
- metatft 티어와 공통 249개 중 같은 등급은 132개(53%)이고, 2단계 이상 차이도 5개다(예: 미래 집중 S 대 B, 생일 모임 B 대 S).
- 가이드 덱에는 패치나 갱신일 필드가 없고 lolchess 자체 키라 사전 매핑이 필요하다.
- 벤치마크 기준으로 약관에 재배포 금지 조항이 있다.
- **metatft.com 3.0점** — 강점: [직접 호출]
- 전체 증강 티어 augments_tiers: 258개(S24/A84/B129/C21/D0). 작성자(META Spencer, NA1)와 updated_at(2026-09-15 11:11:58Z, 조회 당일)을 준다.
- 덱별 증강 티어 comp_augment_tiers: 클러스터 54개 중 28개에 있고 12:44Z 에 갱신됐다. 가이드 13개의 source_title 과 매칭 거리 distance(0.18~0.50)를 준다.
- 덱별 목록과 전체 티어의 일치는 33%(703/2,153)라 덱마다 따로 매긴 값이다. 뒤집으면 '증강별 추천 덱'을 만들 수 있다.
- 한국어 사전 TFTSet18_latest_ko_kr.json: 증강 257개(골드 114/프리즘 73/실버 70). 254개에 manual_tags(econ 121, items 89, combat 88, trait 38, scaling 37, misc 38)가 있다. lol.qq 통계 증강 ID 142개 모두와 lol.qq 풀 252/265개에 한글 이름이 있다.
- 인증이 필요 없다. / 약점: [직접 확인] 통계 표본이 0이다.
- unit_augments(unit=DA_18_Aphelios)는 augments 가 빈 문자열인 한 행(1,038,927판)만 준다.
- augment_unit_detail(augment=DA_PandorasBench)는 [] 다.
- 번들에 상수로 남은 augments_full2 는 필터가 있든 없든 HTTP 500 이다. augments?{F}, comp_augment_tiers?cluster_id 도 500 이다.
- Riot 경기 JSON 과 앱 경기 JSON 에는 증강 키 자체가 없다.
- 티어는 한 사람의 판단이라 검증할 수 없다. lolchess 와 같은 등급은 53%뿐이다.
- 덱별 목록은 S/A/B 만 쓰고 2,153개 중 S 가 1,097개(51%)라 변별력이 낮다.
- 가이드 하나(APHELIOS > Elderwood OR Vanguard> Lvl 8 push)를 클러스터 6개가 공유하고, distance 가 0.5 에 가까운 매칭도 있다. 가이드를 클러스터에 거리로 붙인다는 해석은 필드명으로 본 추정이다.
- 등급이 매겨진 5개 키(DA_StarringUp, DA_Lineup, DA_18_RivalsAugmentPlus, DA_NestingDolls, DA_SubscriptionService)는 lol.qq 시즌 18 풀에 없다.
- 클러스터 ID가 재클러스터링 때마다 바뀐다.

가져올 아이디어:
- metatft 한국어 사전을 붙여 증강 표시와 필터를 보강한다. 수집기가 data.metatft.com/lookups/TFTSet18_latest_ko_kr.json(인증 없음, 1.37MB)에서 augments 만 추려 저장하고, DA_* 로 lol.qq 증강 행에 붙인다. 붙이는 값은 한글 이름·설명, rarity(실버/골드/프리즘), manual_tags 다. 증강 화면에는 엄지로 누르는 필터 칩(경제·아이템·전투·특성·성장, 등급 3종)을 둔다. 직접 확인 결과 lol.qq 통계 증강 ID 142개가 모두 매칭됐다. CommunityDragon 에 이름이 없는 DA_ExpectedUnexpectedness 같은 빈칸도 메워진다.
- metatft comp_augment_tiers 를 뒤집어 '이 증강을 S로 추천하는 덱'을 만든다. 클러스터 28개를 기존 수집기 방식(units_string 집합 대조)으로 lol.qq 덱에 붙인다. 증강 상세에는 lol.qq 통계와 분리된 '가이드 추천' 배지로 보여 준다. S 비중이 51%라 S만 쓰고 source_title 을 출처로 적는다. distance 가 큰(0.5 근처) 매칭은 거른다. 임계값은 실측 후 정하고, 클러스터 ID는 매일 바뀌므로 저장하지 않는다.
- 편집 티어 두 개(metatft augments_tiers, lolchess augment-tiers)를 DA_* 로 합쳐 '전문가 의견' 보조 칸을 만든다. 공통 249개 중 일치 53%, 1단계 차이 45%, 2단계 이상 2%다. 두 등급을 나란히 두고 크게 갈리는 증강(미래 집중 S/B, 생일 모임 B/S, 자본 이익 I C/A 등)에는 '의견 갈림' 표시를 붙인다. 작성자와 갱신 시각을 함께 적어 lol.qq 수치(중국 서버·하루치)와 섞이지 않게 한다. lolchess 쪽은 약관을 먼저 확인한다.
- metatft 가 sample_size 와 override_applied 로 표본을 공개하는 방식을 lol.qq 증강 행에 적용한다. 수집기 JSON 에 행마다 use_num, tier_part 이름, dtstatdate 를 싣고 화면에 'n=1,628 · 골드~에메랄드 · 9/14' 처럼 표시한다. 분모가 덱 판수와 맞지 않는 use_rate(%)는 표시하지 않는다. 단계별 평균 등수는 '0' 값을 버리고, 딱 떨어져 5판 이하로 보이는 값은 흐리게 표시한다.
- lolchess 번들의 증강 등급 등장 확률표(Augment_Tier_Distribution: 2-1/3-2/4-2 별 실버·골드·프리즘 확률)를 lol.qq 단계별 평균 등수(1_/2_/3_avg_rank) 옆에 작은 표로 붙인다. 몇 단계에서 어떤 등급을 고른 결과인지 맥락이 생긴다. API가 아닌 번들 JSON 이라 세트가 바뀔 때 수동으로 갱신하고, 게임 상수이므로 다른 출처와 교차 확인한다.
- lolchess 가 Riot 정책 때문에 증강 통계를 제공하지 않는다고 밝힌 방식을 본뜬다. 앱 증강 화면 상단에 한 줄로 '증강 성적은 중국 서버(골드~에메랄드, 하루치)만 제공, 한국·글로벌은 전문가 티어' 라고 출처 차이를 적는다. 한국 사용자가 수치를 한국 메타로 오해하지 않게 한다.
- lolchess 가이드 덱 30개(set18, 덱당 증강 3개, 키 37개 모두 시즌 18 DA_* 로 대응 확인)를 lol.qq 편집 덱 hexbuff(26개, 95종)와 함께 '편집 추천 증강' 역색인에 합친다. 증강 하나를 누르면 그 증강을 추천한 편집 덱을 출처별로 보여 준다. lolchess 키는 /data/augments 의 key→ingameKey 로 바꾸고, 약관 허락 여부를 먼저 확인한다.

#### 모바일 사용성

[평가 방식]\n- lolchess, metatft: 375x812 폰 크기로 설정한 브라우저 탭에서 실제 페이지를 열었다. JS로 레이아웃 폭, 칸 수, 잘린 이름 수, 첫 목록 위치를 쟀고, 탭하면 툴팁이 뜨는지 시험했다.\n- lol.qq: in-app 브라우저가 차단돼 index.html 템플릿, page-hex.js, comm.css를 분석하고 통계 API를 python으로 직접 호출했다.\n- 증거 파일 위치: C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_aug_mobile/\n  - lolchess 모바일 HTML: lc_*_m.html\n  - lolchess 증강 티어·사전: lc_augment_tiers_live.json, lc_augments_ko_live.json\n  - metatft 증강 티어·사전: mt_augments_tiers_live.json, mt_lookup_ko.json\n  - lol.qq 덱별 증강 통계: qq_aug_group*.json\n\n[1위 lolchess (7)]\n세 곳 중 폰 폭에서 정상적으로 그려지는 곳은 lolchess뿐이다(가로 넘침 없음). 한국어 원문 이름과 설명이 인라인으로 보이고, 탭하면 툴팁이 뜨며, 탭·칩 구조가 단순해 한눈에 읽힌다. 배제 목록과 라운드별 확률표도 폰 사용에 실용적이다. 감점 요인은 네 가지다.\n- 증강 검색 없음\n- 좁은 칸의 이름 잘림 39개\n- 첫 화면을 배너가 차지함\n- 시즌 18 통계 없음\n\n[2위 metatft (5)]\n컨트롤은 가장 좋다(설명까지 찾는 검색, 단계 칩, 다중 희귀도 칩, 표 정렬, 태그). 하지만 폰에서 쓰기 어렵게 만드는 결함이 있다.\n- 375px에서 폭이 514px로 넘친다.\n- 영상과 소개 카드가 목록을 첫 화면 밖으로 밀어낸다.\n- 검색창에 'Augments 검색'처럼 영어가 섞인다.\n- 이름 91개가 잘린다.\n- 통계는 없고 북미 프로 한 명의 수동 티어뿐이다.\n\n[3위 lol.qq (2.5)]\n- 실제 증강 통계(덱별 상위 5개의 평균 등수·사용률·선택 단계별 평균 등수)가 있는 유일한 곳이다. 그래서 기능이 없는 사이트보다는 조금 높게 줬다.\n- 그러나 min-width 1240px 데스크톱 전용 레이아웃, 호버 전용 툴팁, 중국어 전용이다.\n- 강화 페이지에는 티어도 통계도 없고, 전체 증강 랭킹은 화이트리스트로 막혀 있다.\n- 모바일 사용자는 QR코드로 다른 앱에 보낸다.\n\n[공통 한계] 세 곳 모두 '증강 페이지에서 증강별 평균 등수·픽률을 보고 추천 덱으로 이동'하는 흐름이 시즌 18에는 없다.\n\n[우리 앱 방향] 화면 구조는 lolchess를 따르고, 데이터는 metatft 태그와 lol.qq 덱별 증강 통계를 DA_*로 합치는 조합이 모바일 사용성과 데이터 가치를 함께 얻는 길이다.\n\n[참고] lolchess 약관은 재배포를 금지하므로 화면 구조는 참고만 하고 데이터 수집원으로는 쓰지 않는 편이 안전하다. 이 점은 사용성 점수에 반영하지 않았다.

- **lol.qq.com/tft 2.5점** — 강점: [직접 확인: 템플릿·JS·CSS 분석 + 통계 API 직접 호출]
- 强化 페이지는 증강 265개를 1/2/3단계 탭으로 나눈다. 이름 부분일치 검색(대소문자 무시, 이름만)과 仙灵 등장 단계·분류 드롭다운이 있다.
- 설명이 표 둘째 열에 펼쳐져 있어 호버 없이 읽을 수 있다.
- 세 사이트 중 실제 증강 통계가 있는 곳은 여기뿐이다. 다만 증강 페이지가 아니라 胜率阵容 덱 상세에 있다.
  - tft_lineup_all_detail을 덱 3개에 직접 호출해 augment_data를 각각 5/1/5행 받았다.
  - 필드: 평균 등수, 사용률, 사용 수, 1·2·3번째 선택 시 평균 등수.
  - rune_id 11개가 모두 CommunityDragon 한글 이름과 매칭됐다(예: 휴대용 대장간 3.36등·25.0%, 시계태엽 윤활유 3.22등·3.5%). / 약점: [직접 확인]
- 데스크톱 전용이다.
  - comm.css가 html·body에 min-width:1240px!important를 건다.
  - 미디어쿼리 19개가 모두 1400px 이상 폭이나 높이 기준이라 폰 폭 대응이 없다.
  - 증강 아이콘 툴팁은 마우스를 올릴 때만 뜬다(mouseenter).
- 모든 문구가 중국어다.
- 강화 페이지에는 티어도 통계도 없다. 전체 증강 랭킹(tft_augment_rank)은 화이트리스트 전용이라 result 101(访问受限)이 온다.
- 모바일 사용자는 QR코드로 微信小程序나 掌上英雄联盟 앱으로 보낸다.
- 덱 상세의 증강 통계는 덱당 최대 5개뿐이다. 단계별 표본 수가 없어 소표본 값이 섞인다(3번째 선택 평균 '2' 등).
- 모바일 H5 덱 상세 청크에서는 仙灵 단계별 사용률 렌더링만 찾았고, augment_data를 그리는 코드는 찾지 못했다.

[한계] in-app 브라우저가 lol.qq를 차단해 실제 폰 렌더링은 보지 못했다. CSS·템플릿으로만 판단했다.
- **lolchess.gg 7.0점** — 강점: [직접 확인: 375x812 폰 크기 브라우저 + JS 측정, 모바일 UA로 받은 HTML]
- 폰 폭에서 가로로 넘치지 않는다(innerWidth·scrollWidth 375).
- 탭 4개(증강체 가이드/티어/확률/배제 목록)와 필터 칩(전체/실버/골드/프리즘, 가이드는 라운드 2-1/3-2/4-2 추가)이 모두 자연스러운 한국어다.
- 가이드 탭은 서버에서 그린 256행에 이름·등장 라운드·효과 전문이 들어 있어 탭하지 않아도 읽힌다. 설명 수치도 이미 채워져 있다(예: '전투 3회 후 1개 더 획득').
- 티어 탭은 한 줄에 3칸씩 36px 아이콘과 이름을 놓은 격자다. 아이콘을 탭하면 이름과 효과가 담긴 툴팁이 뜬다(터치 동작 확인).
- 다른 두 사이트에 없는 화면이 두 개 있다.
  - 배제 목록: 이 증강을 고르면 이후에 나오지 않는 증강
  - 라운드별 실버/골드/프리즘 등장 확률표
- 편집자 티어 253개(S14/A84/B105/C44/D3), 2026-09-14 05:40 UTC 갱신. / 약점: [직접 확인]
- 증강 페이지에 증강 검색창이 없다. 입력창은 '플레이어#KR1 전적검색' 하나뿐이고, 증강 검색은 배치툴에만 있다.
- 이름 칸이 폭 64px, 글자 11px, 한 줄 말줄임이다. 249칸 중 39칸이 잘린다(예: 빠른 연승 및 연패, 판도라의 대기석).
- 헤더·배너·탭이 첫 화면 대부분을 차지해 S티어 제목이 화면 높이 812 중 y=691에 나온다.
- 전체 높이 6,828px이고 B티어만 약 2,400px이다.
- 오른쪽 아래에 떠 있는 채팅 버튼이 격자를 가린다.
- 시즌 18 증강 통계(평균 등수·픽률)와 증강별 추천 덱이 없다. meta-deck-augments API는 빈 응답이다.
- 번역 파일에는 통계 표 문구(평균 등수/픽률/TOP4/승률/게임 수/라운드별 선택률)가 있지만 시즌 18에서는 탭이 보이지 않는다.
- 티어는 편집자가 수동으로 정한 값이다.
- **metatft.com 5.0점** — 강점: [직접 확인: 375x812 폰 크기 브라우저 + 번들·로캘 분석]
- 언어를 한국어로 고를 수 있고, 티어 목록 이름도 한국어로 나온다.
- 필터 줄이 세 사이트 중 가장 풍부하다.
  - 등장 단계 칩(2-1/3-2/4-2)
  - 희귀도 아이콘 칩(실버/골드/프리즘, 여러 개 선택 가능)
  - 검색: 코드상 이름·설명·영문명을 함께 찾는다. 로캘 도움말에는 '/'로 여러 단어를 구분한다고 적혀 있다.
  - 티어 목록 ↔ 표 보기 토글. 표는 이름·티어·유형 열로 정렬된다.
- 증강마다 유형 태그가 붙어 있다. 시즌 18 증강 258개 중 257개이며, 분포는 econ 123, items 90, combat 88, misc 40, trait 38, scaling 37.
- 아이콘을 탭하면 이름과 설명이 담긴 툴팁이 뜬다.
- '최근 업데이트: 2시간 전'으로 데이터 신선도를 보여 준다.
- 한 줄에 4칸이라 목록 밀도가 lolchess보다 높다. / 약점: [직접 확인]
- 375px 화면에서 레이아웃 폭이 514px로 늘어난다(innerWidth·scrollWidth 514, 검색창 오른쪽 끝 491px). 페이지가 축소되거나 옆으로 밀린다. 같은 조건에서 lolchess는 375px이었다.
- 검색창 안내 문구가 'Augments 검색'으로 영어가 섞여 나온다.
- 영상 플레이어, 설명 문단, 에디터 소개 카드가 첫 화면을 차지해 티어 목록은 y≈793부터 시작한다. 스크롤하는 동안 떠 있는 영상이 헤더에 겹쳤다.
- 254칸 중 91칸의 이름이 잘린다.
- 같은 이름이 반복돼 헷갈린다(마트료시카 3개, 경쟁을 넘어서·내면의 야수·엄청나게 이로운 효과! 각 2개).
- 통계가 없다. 북미 프로 한 명(SpencerTFT)의 수동 티어(S24/A84/B129/C21/D0)뿐이고 편집 노트는 0건이다.

[미검증]
- 단계 칩은 사전 tags에 '2-1' 같은 값이 있어야 걸린다. 시즌 18 사전 tags는 'Augment.Category.*' 형식이라 실제로 제대로 거르는지 확인하지 못했다.
- 한글 검색 시험은 입력이 반영되지 않아 결과를 얻지 못했다.

가져올 아이디어:
- [metatft] 증강 검색을 효과로도 찾게 넓힌다.
- 현재 앱 검색(DeckSearch.kt)은 증강 축을 이름·영문명(nameEn)으로만 만들고 설명은 인덱싱하지 않는다.
- metatft처럼 이름·한글 설명·영문명을 함께 매칭하고, '/'로 여러 단어 OR 검색을 지원한다. '상징', '골드', '워윅' 같은 효과 단어로 증강을 찾게 된다.
- 주의(직접 확인): CommunityDragon ko_kr 설명에는 @EmblemAmount@, @AttackSpeed*100@ 같은 자리표시자가 남아 있다. effects에 해시 키({0810f00e})가 섞이고 값이 빠진 경우도 있다(DA_SpreadingRoots는 Delay만 있음). 자리표시자를 지운 평문으로 인덱싱해야 한다.
- [metatft] 희귀도 다중 선택 칩과 유형 태그 칩을 붙인다.
- 태그: 전투·골드 관리·아이템·특성·성장·전략.
- 태그 원천은 https://api-hc.metatft.com/tft-stat-api/augments_tiers 의 content.content.tags이다. DA_* 키에 'econ,scaling'처럼 쉼표로 구분된 값이 들어 있고, 시즌 18 258개 중 257개를 덮는다.
- 수집기에서 DA_*로 조인해 decks.json 카탈로그에 넣으면 인증 없이 매일 갱신된다.
- 한 손 조작을 위해 칩은 엄지가 닿는 화면 아래쪽에 가로 스크롤 한 줄로 둔다.
- [metatft] 격자 ↔ 목록 보기 토글과 정렬 칩을 둔다.
- 폰에서는 격자 이름이 많이 잘린다: metatft 254칸 중 91칸(4열 52px), lolchess 249칸 중 39칸(3열 64px).
- 목록 보기는 한 줄에 아이콘 + 이름 전체 + 티어 배지 + 유형 태그를 두어 잘림을 없앤다.
- 정렬 칩은 티어순/이름순으로 두고, 통계가 붙으면 평균 등수순을 더한다.
- [metatft] 증강 목록 맨 위에 신선도와 출처를 한 줄로 고정한다.
- 예: '수집 3시간 전 · 티어: 편집자(수동) / 통계: 중국 서버'.
- 앱은 매일 05:00 KST에 수집하므로 decks.json의 version/updatedAt으로 계산한다.
- 편집자 티어와 통계를 혼동하지 않게 한다.
- [lolqq] 덱 상세 '증강체' 섹션에 통계 상위 5개를 붙인다.
- 지금 앱은 작가 추천(hexbuff recomm/replace)만 보여 준다.
- 수집기 호출: POST https://mlol.qt.qq.com/go/exploit/proxy
  - body: {req_alias:'tft_lineup_all_detail', is_return_source:0, version_id:'v1', req_params:{…}}
  - champion_content_id: 덱 챔피언 id를 쉼표로 연결
  - main_traits_id: '특성id,개수;…' (개수 내림차순)
  - mc_champion_id: 메인 캐리 id
  - minor_traits_id: 없으면 '-1,-1'
  - queue_id:'1100', tier_part:'2', time_type:'d_grouping_v3'
- 직접 확인: 서명·쿠키 없이 덱 3개에서 5/1/5행을 받았다. rune_id가 DA_*라 한글 이름이 11/11 매칭됐다.
- 폰 표시는 '아이콘 이름 3.22등 · 3.5%' 한 줄로 한다. 1·2·3번째 선택 평균 등수는 단계별 표본 수가 없으니 기본은 접어 둔다. use_num이 작으면 흐리게 표시한다.
- [lolqq] 기존 증강→덱 역인덱스(feed.index.augment, 작가 추천 기반)에 통계 근거를 합친다.
- 덱마다 '추천(작가)'과 '상위(통계, 평균 등수)' 두 라벨을 붙인다.
- augment_data는 덱당 최대 5개라, 3개 덱 표본에서 덱 간 겹침이 0이었다. 통계만으로 역인덱스를 만들면 비어 보이니 반드시 작가 추천과 병합한다.

#### 구현 가능성

[직접 재확인]
- 일시: 2026-09-15 KST.
- 방법: python urllib, Chrome/120 UA, Referer·Origin·쿠키 없음.
- 증거 파일: C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_aug_impl/

1) lol.qq 통계 프록시 (POST https://mlol.qt.qq.com/go/exploit/proxy)
- tft_lineup_group_list(dgroup_v3, queue 1100, tier_part 2, d_grouping_v3)
  - 200/result 0, 754KB, 0.37s, dtstatdate 20260915.
  - 주 그룹 45개, 덱 425개(고유 343).
  - rune_id_group 길이: 5개 310, 1~4개 66, 0개 49.
- tft_lineup_all_detail(version_id v1, minor_traits_id는 sub_trait_list로 구성)
  - 425회 호출에 237초. 376개가 augment_data를 반환했고 모두 5개 이하였다.
  - 증강 순서가 목록의 rune_id_group과 376/376 같았다.
  - 필드: avg_rank, use_rate, use_num, 1_/2_/3_avg_rank, rune_id_rank. 전부 문자열이고 use_num 최소는 104다.
  - 샘플에서 rune_id_rank 1~5가 avg_rank 오름차순이었다. 덱별 성적 상위 5개만 노출된다는 뜻이다.
- 함정
  - minor '-1,-1'이면 result 0인데 빈 배열이다.
  - 상세에 dgroup_v3를 주면 result 100(version not found)이다.
  - 같은 시각 상세의 dtstatdate는 20260914로 목록보다 하루 늦었다.
- tft_augment_rank: result 101 'not in white list'가 재현됐다.
- 436회 호출 동안 429나 차단은 없었다.
- ID 결합
  - 통계에 나온 증강 111개가 CDragon ko_kr setData[18].augments와 111/111 일치했다.
  - 덱 챔피언 숫자 ID 65개는 chess.js(TFTID→hero_EN_name)를 거쳐 CDragon 챔피언과 65/65 일치했다.
  - 덱 425개 전부 한글로 표시할 수 있다. 예: DA_Ascension(초월)은 91개 덱에 나온다.
- 정적 파일
  - hex.js: 200, ACAO *, Last-Modified 2026-09-09, version 16.17. tft_recent_versions의 최신은 16.18.817.4437이다. 265개 모두 DA_*이고 CDragon과 250개 일치.
  - lineup_detail_total.json: 26/26 덱에 hexbuff 증강(고유 95개)이 있다.
- 수집기 코드를 읽어 확인한 것: hexbuff.recomm이 index.augment(55키)로 들어가고, 앱 DeckSearch에 AUGMENT 축이 이미 있다.

2) metatft
- augments_tiers
  - 200, gzip 60KB, ACAO *.
  - 258개가 모두 DA_*이고 분포는 S24/A84/B129/C21, updated_at 2026-09-15T11:11Z.
  - CDragon 248/258, hex.js와 253개 겹침.
- 통계 엔드포인트
  - augment_unit_detail: results [](표본 6,769,464).
  - unit_augments: augments가 빈 문자열인 한 행.
  - 대조군 unit_detail_overall은 정상이었다. 증강 차원만 비어 있다.
- 한국어 사전: 257개. augmentTiers 키는 희귀도 스타일이다.

3) lolchess
- data/augment-tiers: 200, 10KB, 0.10s, CloudFront. 253행이 S14/A84/B105/C44/D3/빈칸3이다. 벤치마킹의 'S/A/B'는 부정확했다. updatedAt은 2026-09-14 05:40 UTC.
- data/augments hl=ko: 986행 중 isHidden이 731행.
- meta-deck-augments: 두 변형 모두 {"patchRevisions":[]}.
- 라이브 약관에 무단 복제와 제3자 제공을 금지하는 조항이 있다.
- metatft와 lolchess 티어를 공통 249개로 비교하면 정확 일치 53%, 한 단계 이내 98%다.

[채점]
- lolqq 7
  - 증강 3요소 중 '통계'와 '증강별 추천 덱'을 인증 없이 매일 받을 수 있는 유일한 소스다.
  - 증강 ID 결합이 완벽하고, 편집 추천 덱 역인덱스는 이미 구현돼 있다.
  - 감점: 전체 랭킹이 화이트리스트로 막혀 티어가 없다. 덱별 상위 5개만 나와 커버리지 42%에 편향이 있다. 조용히 실패하는 파라미터, 목록과 상세의 하루 차이, 정적 파일 지연이 있다.
- metatft 4.5
  - 티어와 한글 사전은 가장 쉽고 최신이다.
  - 하지만 서버 데이터에 증강이 비어 있어 통계와 추천 덱을 원천적으로 만들 수 없다.
- lolchess 3
  - 기술적으로는 쉽지만 통계가 없다.
  - 약관이 공개 재배포 구조와 충돌한다.

[추정·미확인]
- 상세를 매일 425회 호출해도 계속 차단이 없을지는 모른다. 대안: 목록 1회로 매핑하고, 상세는 노출할 덱만 호출한다.
- metatft 약관은 확인하지 않았다.
- 1_/2_/3_avg_rank가 '선택 단계'를 뜻한다는 해석과 hex.js type과 희귀도의 대응은 필드명으로 짐작한 것이다.

- **lol.qq.com/tft 7.0점** — 강점: - 세 곳 중 유일하게 증강 통계를 인증 없이 받을 수 있다(직접 확인). tft_lineup_group_list POST 1회로 덱 425개의 rune_id_group(덱마다 최대 5개)을 받는다. tft_lineup_all_detail은 증강별 avg_rank, use_rate, use_num, 1_/2_/3_avg_rank를 준다. Referer, Origin, 쿠키 없이 result 0이었고, 436회 연속 호출에도 차단은 없었다.
- 증강 ID가 DA_* 그대로라 결합이 쉽다. 통계에 나온 증강 111개가 CommunityDragon Set18 증강과 111/111 일치해 한글 이름이 바로 붙는다.
- 덱의 숫자 챔피언 ID 65개도 chess.js(TFTID→hero_EN_name)를 거쳐 CDragon과 65/65 일치한다. 덱 425개 전부 한국어로 표시할 수 있다.
- '증강별 추천 덱'의 편집 버전은 이미 구현돼 있다. lineup_detail_total.json 덱 26개 모두 hexbuff 증강(고유 95개)을 갖고 있고, 수집기가 이를 index.augment로 만들며 앱 DeckSearch에 AUGMENT 축이 있다. 새 엔드포인트가 필요 없다.
- 증강 도감 hex.js는 인증 없는 정적 CDN(ACAO *)이고 265개 모두 DA_* 키다. / 약점: - 전체 증강 랭킹 tft_augment_rank는 result 101(화이트리스트)로 막혀 있다. 전체 평균 등수 표와 티어는 만들 수 없고, 티어 데이터 자체도 없다.
- 덱마다 성적 상위 5개만 나온다. rune_id_rank 1~5가 avg_rank 오름차순이고 use_num은 최소 104다. 그래서 통계에 나오는 증강은 265개 중 111개(42%)뿐이고, 성적이 나쁜 증강은 보이지 않는 편향이 있다.
- 조용히 실패하는 파라미터가 있다.
  - minor_traits_id를 '-1,-1'로 주면 result 0인데 augment_data가 빈 배열이다.
  - 상세에 version_id dgroup_v3를 주면 result 100이다.
  - 목록은 dgroup_v3/d_grouping_v3, 상세는 v1을 쓴다. 숫자는 전부 문자열이다.
- 같은 시각에 목록 dtstatdate는 20260915, 상세는 20260914로 하루 차이가 난다.
- hex.js 경로에 패치 문자열(16.17-2026.S18)이 들어 있고, 라이브 16.18.817보다 한 패치 늦다. 15개는 CDragon에 없어 한글명이 빈다. 현재 repo index에 DA_18_FloraFatalisAugmentPlus가 원시 키로 남아 있다.
- 비공개 텐센트 프록시라 예고 없이 바뀔 수 있다. 상세를 매일 425회 부르는 부하가 장기적으로 레이트리밋을 부를지는 미확인이다.
- **lolchess.gg 3.0점** — 강점: - data/augment-tiers?season=set18을 GET으로 받는다. 10KB, 0.10s, CloudFront max-age 60, ACAO *라 기술적으로 가장 빠르고 안정적이다.
- 253행 중 249행이 DA_* 키이고, 분포는 S14/A84/B105/C44/D3이다. CDragon과 250/253 일치한다.
- hl=ko 사전이 한국어 이름과 설명을 직접 준다. CDragon에 없는 DA_18_FloraFatalisAugmentPlus, DA_CalculatedLoss의 한글명도 있다. / 약점: - 증강 통계가 없다. meta-deck-augments는 두 변형 모두 {"patchRevisions":[]}였다. 증강별 평균 등수와 추천 덱을 만들 수 없다.
- 라이브 약관(/about/terms_and_service)에 서비스로 얻은 정보를 사전 승낙 없이 복제하거나 제3자에게 제공하는 것을 금지하는 조항이 있다(직접 확인). 공개 저장소와 Actions로 재배포하는 우리 구조와 정면으로 충돌해 차단·삭제 위험이 가장 크다.
- 과거 세트 데이터를 걸러내야 한다.
  - 사전 986행 중 731행이 isHidden인 과거 세트다.
  - 티어 맵에도 티어가 빈 레거시 키(TFT7_/TFT17_/TFT_)가 4개 있다.
- 벤치마킹은 'S/A/B'라고 했지만 실제는 S~D 5단계였다. 보고된 스키마와 실제가 달랐다.
- **metatft.com 4.5점** — 강점: - tft-stat-api/augments_tiers GET 1회로 받는다. gzip 60KB, 1.06s, ACAO *, 헤더와 쿠키가 필요 없다.
- 258개 전부 DA_* 키이고 분포는 S24/A84/B129/C21이다. updated_at이 2026-09-15T11:11Z로 세 곳 중 가장 최신이다.
- DA_* 조인만으로 붙는다. hex.js와 253개가 겹치고, 우리 편집 덱 증강 95개 중 93개에 티어가 있다. CDragon과는 248/258 일치한다.
- data.metatft.com 한국어 사전(Cloudflare 정적, 257개)에 한글 이름, 희귀도(Silver70/Gold114/Prismatic73), manual_tags(econ/items/combat/trait/scaling/misc)가 있다. 필터 칩을 바로 만들 수 있다. / 약점: - 증강 통계가 원천적으로 없다(직접 확인).
  - augment_unit_detail?augment=DA_PandorasBench는 표본 6,769,464인데 results가 []다.
  - unit_augments는 augments가 빈 문자열인 한 행뿐이다.
  - 같은 필터의 unit_detail_overall은 정상이라 필터 문제가 아니다.
  - 따라서 증강별 평균 등수, 픽률, 추천 덱을 만들 수 없다.
- 티어는 에디터 1인의 수동 평가이고 갱신이 비정기적이다.
- 스키마 함정이 있다.
  - 경로가 content.content.tierList로 깊다.
  - 사전의 augmentTiers 키는 이름과 달리 희귀도 아이콘 스타일이다(티어 아님).
  - latest 사전의 _metadata.patch가 'pbe'다.
- 에디터 저작물을 재배포해도 되는지, 약관에 스크래핑 조항이 있는지는 확인하지 못했다.

가져올 아이디어:
- metatft tft-stat-api/augments_tiers(1회 GET, 60KB)를 수집기에 추가해 증강 catalog에 DA_* 키로 '에디터 티어 S~D' 배지를 붙인다.
- 커버리지: hex.js 265개 중 253개, 현재 덱 추천 증강 95개 중 93개.
- 표시: 통계가 아닌 1인 수동 평가이므로 'metatft 에디터 평가' 라벨과 updated_at을 함께 보여 준다.
- 검증: content.content.tierList가 비면 이전 값을 유지한다.
- 주의: 사전 파일의 augmentTiers 키는 희귀도 아이콘 스타일이라 티어로 쓰면 안 된다.
- metatft 한국어 사전(data.metatft.com/lookups/TFTSet18_latest_ko_kr.json)의 rarity(Silver/Gold/Prismatic)와 manual_tags(econ/items/combat/trait/scaling)를 수집 단계에서 catalog.augments에 합친다.
- 증강 목록 상단에 '실버/골드/프리즘'과 '경제/아이템/전투/특성' 필터 칩을 둔다. 엄지로 좁혀 가는 한 손 UI에 맞다.
- 앱은 계산하지 않고 조회만 한다.
- lol.qq hex.js의 type 1/2/3과 교차 검증한다.
- 증강 상세 화면을 두 소스로 나눈다.
- 위쪽: metatft 에디터 티어.
- 아래쪽: lol.qq 통계로 만든 '이 증강이 잘 맞는 덱'. tft_lineup_group_list의 rune_id_group을 역인덱스로 만들고, 상세의 avg_rank·use_num·1_/2_/3_avg_rank를 붙인다.
- lol.qq는 덱마다 상위 5개만 주므로 '덱 기준 상위 증강'이라고 명시한다.
- 표본 수(use_num)와 기준일(dtstatdate)을 metatft의 '업데이트 N분 전'처럼 함께 표시한다.
- lolchess 증강 페이지의 탭 구성은 UI 패턴으로만 빌린다. 희귀도(type=silver|gold|prismatic)와 라운드(round=2-1|3-2|4-2) 탭이다.
- 라운드별 등장 확률표는 API가 아니라 번들 JSON이라는 벤치마킹 보고가 있다(미재확인).
- 약관이 무단 복제와 제3자 제공을 금지하므로 자동 수집하지 않는다. 패치마다 수기로 정적 표를 관리한다.
- CDragon ko_kr에 없는 증강의 한글명은 collector에 수동 오버라이드 사전을 두어 메운다.
- 대상: hex.js 기준 15개. 예: DA_18_FloraFatalisAugmentPlus, DA_CalculatedLoss.
- 현재 data/decks.json의 index.augment에 원시 키 'DA_18_FloraFatalisAugmentPlus'가 그대로 노출돼 있다.
- lolchess hl=ko 사전에 두 이름이 있지만 약관 때문에 자동 수집원으로 쓰지 않는다. 사람이 대조 확인하는 참고로만 쓴다.

### 전적·티어·인게임

| 관점 | lol.qq | lolchess | metatft | 관점 1위 |
|---|---:|---:|---:|---|
| 데이터 품질 | 1.5 | 6.5 | 7.5 | metatft.com |
| 모바일 사용성 | 1.0 | 7.5 | 6.0 | lolchess.gg |
| 구현 가능성 | 2.0 | 6.0 | 8.0 | metatft.com |

#### 데이터 품질

[범위] 전적·티어·인게임 카테고리를 데이터 품질로만 채점했다. 2026-09-15 12:46~13:04 UTC에 테스트 계정 랄라붕#KR1로 metatft와 lolchess를 직접 호출해 교차 검증했다. lol.qq는 段位排行만 직접 호출했다. 레거시 경기 API는 Referer 위조가 필요해 호출하지 않고 벤치마크 결과만 참고했다.

[직접 확인]
1) 정확성
- 두 소스의 시즌 18 랭크 112판이 경기 ID 112/112로 일치했다. 내 등수 불일치 0, 등수 분포 동일, LP 로그 108/108 일치, 현재 티어 동일.
- 서버 순위: metatft 6,397/618,215, lolchess 6,353/617,673.
- metatft의 경기 전 레이팅은 직전 경기 LP 로그와 107/107 일치했다.
- 지난 시즌 11개 중 10개가 일치했고 Set 14만 달랐다.

2) 반영 속도
- lolchess syncedAt은 13:03Z까지 조회로 바뀌지 않았다. 동기화 전 값(09-14 11:34Z) 이후에 끝난 랭크 경기가 6판이므로, 동기화 전에는 6판이 없었을 것이다(추정).
- metatft는 갱신 호출 없이 12:46:59Z에 갱신됐지만, 16분 뒤까지 재조회해도 그대로였다. 조회마다 갱신되는 것은 아니다.

3) 깊이
- metatft 경기 JSON: 8명 전원 티어·LP·서버 순위·MMR 전후값, 로비 평균, 클라이언트 빌드. 단 참가자 티어는 경기 전 마지막 관측값이다(종료 최대 51.7시간 전).
- lolchess 경기 상세: 8명 티어·디비전뿐이고 라운드 데이터가 없다.
- 대신 lolchess는 7개 지표 백분위, b패치 구분, 개인 집계를 준다.

4) 공통 한계
- 증강: metatft 0/112경기, lolchess 0/160명.
- 인게임: 두 곳 모두 본인 조회에서 게임 중이 아니었고, 관전 목록은 비어 있었다. 벤치마크 폴러의 KR 상위 50명 조회도 전부 {}였다. Riot kr1 상태 JSON에는 장애 공지가 없었다.
- 게임 중 응답 스키마는 두 곳 모두 미확인이라 인게임 항목은 동점(사실상 0)이다.

5) lol.qq
- 段位排行은 Referer 없이 1,968행을 받았다. 한국 계정은 구조적으로 조회할 수 없다.

[판정]
- 핵심 수치의 정확성은 metatft와 lolchess가 같다.
- 차이는 두 곳에서 난다: 경기 상세·로비 정보의 정밀도(LP, MMR, 로비 평균, 경기 전 레이팅)와 갱신 방식(metatft는 명시 호출 없이 갱신 사례 확인, lolchess는 수동 동기화 전 약 하루 지연).
- 그래서 metatft 7.5점, lolchess 6.5점이다. lolchess는 백분위 해석성, b패치 표기, 과거 시즌 정확성에서 앞서 1점 차에 그쳤다.
- lol.qq는 한국 사용자에게 이 카테고리 데이터를 줄 수 없어 1.5점이다.

[결합 주의] 같은 계정의 PUUID가 metatft와 lolchess에서 다르다(관찰, Riot API 키별 암호화로 보임). 선수 단위 결합은 DA_*가 아니라 Riot ID와 경기 ID(KR_8382256353 = 8382256353)로 해야 한다.

[증거 파일]
- C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_profile_dq/ (check4.py, mt_lookup_2.json, lc_profile_2.json, lc_leaguelogs_200.json)
- C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_profile/ (analyze2.py, mt_lookup.json, mt_match_latest.json, lc_matches_all.json, qq_tier_rank_1000_area1.json)
- 주의: judge_profile 폴더는 다른 에이전트가 같은 시각에 써서 lc_profile.json과 lc_leaguelogs.json이 덮어써졌다. 그 두 파일은 이 심사의 결과로 보지 말 것.

- **lol.qq.com/tft 1.5점** — 강점: - 段位排行(get_tier_rank_1000)은 Referer 없이도 result 0으로 조회됐다(직접 확인). 대区 1 기준 1,968행, date 20260915(일간), 王者 261·宗师 665·大师 1,042, LP 0~1,381.
- 레거시 fightlist/fightdetail은 로그인 없이 경기 목록과 8인 상세, 증강 문자열 필드를 준다(벤치마크 확인). 시즌 18에서 증강 값이 실제로 채워지는지는 미확인이다. / 약점: - 한국 계정의 티어·LP·전적을 조회할 수 없다. 중국 서버 계정 전용이라 우리 앱 사용자에게 이 카테고리 데이터는 사실상 없다.
- 순위표는 大师 이상 상위권만 담아 티어 범위가 극히 좁다. 이름으로 puuid를 찾는 공개 API도 없다.
- 레거시 경기 API는 Referer를 검사한다. 위조가 필요해서 직접 호출하지 않았다. 같은 계열 일부 API는 이미 -20022로 막혀 있고, 응답은 구분자 문자열이라 파싱해야 한다.
- fightdetail이 타인의 openid·login_ip를 노출한다.
- 인게임 로비 기능이 없다. 一键应用은 QQ 로그인 뒤 덱을 등록하는 기능일 뿐이다.
- **lolchess.gg 6.5점** — 강점: [직접 확인]
- 정확성은 metatft와 완전히 같다. 경기 112/112, 등수 불일치 0, LP 로그 108/108, 현재 티어 모두 일치.
- 7개 지표(티어·판수·1등·톱4·승률·최근 10판 승률·톱4율)의 순위·모집단·백분위를 준다(티어 6,353/617,673, 1.029%). 해석이 가장 쉽다.
- 패치를 b패치까지 구분한다(18.1 41판 / 18.1d 39판 / 18.2 32판).
- 개인 챔피언·특성·아이템 집계(전체·최근 20판, DA_* 키)를 준다. LP 로그 행에 누적 판수와 톱4 수가 들어 있다.
- 시즌 이력이 Set 1부터 24개다. Set 14를 MASTER I로 기록해 metatft와 달랐는데, 시즌 종료 기준으로 보인다(추정).
- 경기 상세에 8명 전원의 티어·디비전이 있다(20경기 모두 8/8).
- LP 로그 시각이 탈락 시점에 가깝다. 112번째 판에서 탈락 11:13:29Z, 로그 11:15:21Z였다(1건 확인).
- 챌린저·그마 컷(500/200)이 응답에 포함된다. / 약점: - 조회로는 갱신되지 않는다. syncedAt이 11:57:08Z(벤치마크의 수동 동기화 시각)에서 13:03Z까지 그대로였다.
  - 동기화 전 값은 09-14 11:34Z였고, 그 뒤에 끝난 랭크 경기가 6판이다. 동기화 전에는 이 6판이 없었을 것이다(추정).
  - 최신화하려면 retryAfter 폴링을 하는 비동기 RPC가 필요하다.
- 로비 참가자 정보는 티어·디비전뿐이다. LP·서버 순위·스냅샷 시각이 없고, 로비 평균과 MMR도 없다.
- 경기 eog/details가 404라 라운드 단위 데이터가 없다(벤치마크 확인).
- 경기 목록이 20개씩 페이지로 나뉜다. 112판을 받는 데 6번 호출했다.
- 백분위 모집단은 주기적으로 다시 계산된다. 12:00경 608,073(벤치마크)에서 12:47 617,673으로 바뀐 뒤 13:03까지 고정됐다.
- 증강은 첫 20경기 참가자 160명 모두 비어 있다.
- 인게임:
  - rpc/spectator 본인 조회는 '게임 중 아님'이었다.
  - spectate-matches는 kr 0건이다(벤치마크에서는 15개 샤드 모두 0건).
  - 게임 중 응답 스키마는 확인하지 못했다.
- (품질 외) 약관에 재배포 금지 조항이 있다.
- **metatft.com 7.5점** — 강점: [직접 확인, 2026-09-15 12:46~13:04 UTC, 랄라붕#KR1]
- 정확성: lolchess와 시즌 18 랭크 112판이 경기 ID 112/112로 일치한다. 내 등수 불일치 0, 등수 분포 [14,13,20,17,21,7,13,7] 동일, LP 로그(5~112번째 경기) 108/108 일치, 현재 DIAMOND IV 0 LP 동일.
- 경기마다 경기 전 내 레이팅(summary.player_rating)을 준다. 직전 경기 LP 로그와 107/107 일치했다. 결측 5건은 첫 1~5판뿐이다(LP 로그도 5번째 경기부터 시작).
- 한 번 호출로 받는 것: 시즌 전체 112판, LP 변화 109행, Set 8.5~17 시즌별 최종·최고 티어, 서버 순위(6,397/618,215, 상위 1.04%). lolchess 순위 6,353/617,673과 0.7% 안쪽으로 맞는다.
- 경기 상세 JSON(영구 캐시)의 로비 정보가 깊다.
  - 8명 전원의 티어·LP·서버 순위·MMR 경기 전후값과 불확실도
  - 로비 평균 티어(LP 단위, 107/112경기)
  - 롤아웃 기준 클라이언트 빌드와 적용 시각
  - 참가자별 last_refreshed가 있어 스냅샷이 얼마나 오래됐는지 알 수 있다.
- 갱신 호출을 따로 하지 않았는데 last_refreshed가 내 첫 조회 시각(12:46:59Z)으로 바뀌었다.
- 17개 지역의 챌린저·그마 컷과 인원(KR 500LP·161명, 200LP·314명, 마스터 1,373명)을 준다.
- 데스크톱 앱 사용자는 라운드 단위 기록이 있다. 이 계정은 시즌 18 기간에 75건이다. / 약점: - 조회할 때마다 갱신되지는 않는다. 12:46:59Z 갱신 뒤 12:54Z와 13:03Z에 다시 조회해도 그대로였다(갱신 조건은 미확인). 번들에 refresh_by_riotid(queued→completed 폴링)가 있는데 호출하지 않았다.
- 프로필의 patch 값이 b패치를 합친다. 18.1d 39판도 '18.1'로 나온다.
- 로비 참가자 티어는 경기 시점 값이 아니라 경기 전 마지막 관측값이다. 게임 종료 0.03~51.7시간 전 스냅샷이었다. 내 항목도 경기 전 값(DIAMOND IV 1 LP)이었다.
- 지난 시즌 이력이 관측 스냅샷 기반이다.
  - Set 14: metatft는 최종·최고 모두 DIAMOND I, lolchess는 MASTER I
  - Set 9.5: 최고(EMERALD III)가 최종(DIAMOND III)보다 낮다
- 자잘한 이상값:
  - rating_changes에 5번째 경기가 두 번 들어 있다.
  - promotion_thresholds의 timestamp에 2025-01-29 값과 현재보다 미래 시각이 섞여 있다.
  - MMR은 Riot 값이 아니라 metatft 자체 추정치로 보인다.
- 백분위는 티어 순위 하나뿐이다.
- 증강은 0/112경기다.
- 인게임:
  - summoner_by_puuid가 본인 조회에서 {}였다.
  - 벤치마크 폴러 로그에서도 KR 상위 50명 조회 응답이 전부 2바이트({})였다.
  - top_players는 빈 배열이었다.
  - 게임 중 응답 스키마는 확인하지 못했다.

가져올 아이디어:
- [lolchess → 상위 백분위] ProfileRepository가 이미 받는 metatft lookup 응답에서 server_rank{rank,total}를 파싱해 'KR 6,397위 · 상위 1.04%'처럼 표시한다. lolchess 티어 백분위(6,353/617,673)와 0.7% 안쪽으로 맞아 믿을 만하다. 톱4율·1등 백분위는 metatft에 없으니 티어 백분위 하나만 쓴다.
- [lolchess → 갱신 시각과 수동 갱신] lolchess의 'n분 전 업데이트'처럼 프로필 카드에 metatft summoner.last_refreshed와 최신 경기 시각을 함께 보여 준다. 직접 확인해 보니 metatft는 12:46:59Z에 갱신된 뒤 13:03:30Z까지 조회해도 그대로라, 방금 끝난 판이 안 보일 수 있다. 새로고침 버튼은 metatft 사이트처럼 refresh_by_riotid를 부르고 status가 completed가 될 때까지 폴링한 뒤 lookup을 다시 부르는 흐름을 검토한다(번들 코드로만 확인, 미호출).
- [lolchess → b패치 구분] lolchess는 18.1과 18.1d를 나누지만(이 계정 18.1d 39판) metatft 프로필은 둘 다 '18.1'로 합친다. 앱에서 match_timestamp를 metatft tft-stat-api/patch의 시작 시각이나 경기 JSON의 _metatft.patch_resolution(live_at, client_version)과 비교해 b패치를 붙이고, '현재 패치 18.2: 32판 평균 x등'처럼 패치별 내 평균 등수를 보여 준다.
- [lolchess → 개인 챔피언·특성·아이템 집계] lolchess overviews(전체·최근 20판)처럼 metatft matches[].summary.units(character_id, itemNames)와 traits를 앱에서 집계한다. 이름은 수집기의 DA_*→한국어 사전으로 붙이고, 같은 DA_* 키로 덱 통계와 연결해 '내가 자주 쓴 캐리·조합의 내 평균 등수 대 전체 평균 등수'를 보여 준다.
- [lolchess → 챌린저·그마 컷] 컷은 사람별이 아니라 지역별 값이라 하루 1회 수집기에 넣기 좋다. metatft promotion_thresholds의 KR 값(챌린저 500LP·161명, 그마 200LP·314명, 마스터 1,373명)을 decks.json에 싣고, 마스터 이상 사용자에게 '그마 컷까지 xLP'를 보여 준다. 응답의 timestamp에는 2025-01-29 값과 미래 시각이 섞여 있으니 수집 시각을 대신 쓴다.
- [lolchess → 지난 시즌 표기] lolchess summonerSeasons(Set 1부터 24개)는 시즌 종료 기준으로 보인다. metatft rating_history는 관측 스냅샷이라 Set 14에서 어긋났다(DIAMOND I 83LP 대 MASTER I). 지난 시즌을 metatft로 보여 준다면 '마지막 관측 티어'라고 적고, 최고 티어가 최종보다 낮은 경우(Set 9.5)는 최고 티어를 숨긴다.
- [lolchess → LP 추이에 누적 톱4] lolchess LP 로그에는 누적 판수와 톱4가 같이 있다. 앱은 metatft ranked_rating_changes(109행, 경기 번호 포함, 중복된 5번째 경기는 번호로 제거)로 LP 추이를 그리고, matches의 등수로 계산한 누적 톱4 비율을 같은 그래프에 겹친다.
- [lol.qq → 중국 상위권 규모] get_tier_rank_1000은 Referer 없이 조회됐다(대区 1: 1,968행, date 20260915, 王者 261·宗师 665·大师 1,042, 최고 1,381LP). 수집기가 매일 대区별 인원과 컷 LP 같은 집계만 decks.json에 넣고, 덱 화면의 '중국 한정' 배지 옆에 중국 표본의 티어 범위 설명으로 쓴다. puuid·닉네임·intent는 저장하지 않는다.

#### 모바일 사용성

모바일 사용성 1위는 lolchess(7.5)이고 metatft(6.0), lol.qq(1.0) 순이다. 세 사이트 모두 375x812 에뮬레이션에서 DOM을 직접 측정했고, CSS와 번역 파일을 비교했으며, 오늘 API를 호출해 근거를 모았다. 산출물은 C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_profile_mobile/ 에 있다.

**lolchess와 metatft의 차이**
- metatft가 앞서는 점: 첫 화면에서 티어와 큰 숫자 3칸이 보이고(tier y=735), 매치 행에 경기별 LP 증감이 있다.
- lolchess가 앞서는 점이 더 많다:
  - 서버가 UA로 모바일 화면을 따로 만든다(isMobile SSR).
  - 탭이 58px, 매치 펼침 버튼이 36px로 누르기 좋다.
  - 글자가 12px 위주다. metatft는 텍스트 노드의 28%가 9~11px다.
  - 한국어가 원어민 수준이다. metatft에는 영문 탭 Stats/Tags, 날짜 순서 오류 '28일 8월', 오역 '제외됨'·'강제 플레이어'가 남아 있다.
  - 필터·캘린더·즐겨찾기 구성이 더 풍부하다.
- metatft는 116px 고정 동영상 광고가 스크롤 내내 위를 가려서 한 손으로 흘깃 보기가 크게 손해를 본다.
- lolchess 감점 요인: 핀치 줌 차단, 핵심 수치가 첫 화면 아래(y≈862~1031), 중간 광고(도박 광고 관찰), 수동 '전적 갱신' 필요(방문해도 syncedAt 그대로).

**lol.qq**
body/html에 min-width 1240px가 강제돼 폰 레이아웃이 없다. 중국어 전용이고 중국 서버 상위 랭커만 볼 수 있어 KR 계정 전적·매치 상세·로비 기능이 없다. 그래서 사실상 이 카테고리 기능이 없는 것으로 채점했고, 우리 앱에 가져올 아이디어도 없다.

**미검증 항목**
- 두 사이트의 게임 중 로비 참가자 패널은 코드와 i18n으로만 확인했다. 조사 시점에 게임 중인 대상이 없어 실제 응답은 보지 못했다.
- metatft last_refreshed(12:46:59Z)를 누가 갱신했는지는 확인하지 못했다.
- 채팅 버튼 겹침과 광고 소재는 1회 관찰이라 노출마다 다를 수 있다.

**우리 앱 관점**
전적 데이터는 이미 앱이 인증 없이 부르는 metatft 프로필 API에서 온다. 그래서 가져올 아이디어는 같은 응답으로 바로 만들 수 있는 metatft의 장점(경기별 LP 증감, 서버 백분위, 로비 강도, 참가자 카드)에 lolchess식 한국어 표기와 큰 터치 영역을 입히는 방향이 맞다.

반면교사로 metatft의 9~10px 글자와 고정 광고는 피해야 한다. 현재 ProfileCard도 라벨·보조 문구가 8~9.5sp이니 10sp 이상으로 올리는 것이 좋다.

- **lol.qq.com/tft 1.0점** — 강점: [직접 확인]
- 段位排行 템플릿에 대区 드롭다운, 닉네임 부분일치 검색(请输入您想搜索的ID), 초기화, 10행 페이지네이션이 있다.
- 오늘 get_tier_rank_1000(대区1)을 호출해 1,967행을 받았다. 개인 전적은 intent 필드의 掌上英雄联盟 앱 딥링크(qtpage://tft/battle?...)로 넘긴다.
- 모바일·데스크톱 UA에 같은 HTML이 오고 viewport 메타는 있다. / 약점: - 폰 화면을 지원하지 않는다. comm.css에 'body,html{min-width:1240px!important}'가 있고 1400px 미만 @media가 하나도 없다. 375px 폰에서도 1240px 데스크톱 레이아웃이 그대로 뜬다.
- 중국어 전용이다. 표 헤더는 排名/玩家姓名/段位/胜点뿐이고 티어도 最强王者/傲世宗师/超凡大师 중국어 표기다.
- 조회 대상이 중국 대区별 大师 이상 상위 랭커뿐이라 KR 계정(랄라붕#KR1)은 찾을 수 없다.
- 내 최근 전적, 등수, 매치 상세 화면이 없다. 레거시 fightlist/fightdetail은 라우트가 주석 처리돼 있고 CN puuid와 Referer가 필요하다.
- 인게임 로비 참가자 기능이 없다.
- 한국어 사용자가 폰 한 손으로 쓸 수 있는 부분이 사실상 없어서 이 카테고리 기능이 없는 것으로 보고 최저점에 가깝게 줬다.
- **lolchess.gg 7.5점** — 강점: [직접 확인: 375x812 에뮬레이션, DOM 측정, 오늘 호출]
- 서버가 UA로 모바일 화면을 따로 만든다. __NEXT_DATA__의 isMobile이 모바일 UA에서 true, 데스크톱 UA에서 false다. 375px에서 페이지 가로 스크롤은 없다(scrollWidth 375).
- 손가락으로 누르기 좋은 크기다. 탭(시즌 18 종합/매치 히스토리/LP 변화 추이/통계)이 93x58px, '더 보기'가 화면 폭 x 38px, '전적 갱신'이 90x40px다. 매치 필터 칩(전체 매치/랭크/더블 업/일반)과 시즌 드롭다운이 있다. 상단 검색창에는 지역 선택과 '플레이어#KR1' 안내 문구가 있다.
- 한국어가 원어민 수준이고 커뮤니티 용어 그대로다.
  - 랭크 카드: 'Diamond IV 0 LP · 상위 1.029% · 6,353위'. 승리·승률·Top4·Top4 비율·게임 수·평균 등수마다 '상위 x%'가 붙는다.
  - '최근 20 게임 등수 (랭크)' 띠에 평균 #5.3 · 1등 횟수 · TOP 횟수가 모여 있다.
  - LP 변화 추이는 경기마다 상승/하락, LP, 누적 게임 수, TOP4%를 보여 준다.
- 매치 상세가 모바일 전용 펼침 버튼(open-btn-mobile, 36x36)으로 같은 화면에 펼쳐진다(1,002px).
  - 탭: 순위 / 라운드 상세 / 라운드 그래프 / 리롤 분석(높이 32~36px)
  - 8명 표: 등수, 레벨, 티어 약칭(D3/E1), 이름, 탈락 라운드(6-6), 생존 시간
  - 글자 크기 11~12px
- 12px 이상 글자가 대부분이다. 매치 목록은 12px, 페이지 전체에서 확인한 글자는 11~24px다.
- [코드 확인, 실제 화면 미확인] 전적 캘린더에 모바일 날짜 선택 코드(isMobileSelected, mobileSelectedDate)가 있다. 즐겨찾기 표로 여러 계정의 티어·LP·TOP4%를 한 번에 볼 수 있다.
- [코드 확인, 실제 게임 중 응답 미확인] 프로필 주인이 게임 중이면 프로필에 LIVE 패널이 뜬다. 참가자마다 '최근 10 매치 · 평균 등수'를 보여 주고 더 보기/접기가 된다(spectator.participants, i18n ingameStatus). / 약점: - viewport가 user-scalable=no, maximum-scale=1이라 핀치 줌이 막혀 있다.
- 핵심 수치가 첫 화면(812px) 아래에 있다. 상위 1.029%는 y=862px, 평균 등수는 y=1,031px이고, 첫 화면에서 티어는 시즌 드롭다운 글자로만 보인다.
- 탭 내용이 한 페이지에 모두 쌓여 문서 높이가 8,708px다.
- 광고와 겹침 요소가 있다.
  - 프로필 중간에 280px 광고 슬롯(div-gpt-ad)이 있고, 도박·암호화폐 광고(dafabet/gdpay)가 뜨는 것을 봤다.
  - 첫 스크린샷에서 채팅 버튼이 '통계' 탭을 가렸다(스크린샷 1회 관찰).
- 최신 전적을 보려면 '전적 갱신'을 눌러야 한다. 페이지를 두 번 열어도 syncedAt이 11:57Z에서 바뀌지 않았다(12:57Z 확인).
- 매치 행에 경기별 LP 증감이 없다. LP 증감은 따로 떨어진 LP 변화 추이 영역에만 있다.
- 표기가 섞여 있다. 같은 화면에서 'Diamond IV'(영문)와 '다이아몬드 IV'가 함께 쓰이고, 'Top4'·'TOP4'·'TOP 횟수'와 '#4.12' 표기가 섞인다. 번역 파일에 '승급헀어요' 오타가 있다.
- 상세 표의 소환사 이름 링크가 높이 16px로 작다. 매치 행 10개가 1,924px라 한 행이 약 190px를 차지한다.
- 관전은 PC 전용이다('PC 화면에서 관전하기를 이용해보세요!').
- 참고: API 재사용은 이용약관 위험이 있다(벤치마크 결과). 사용성 점수에는 반영하지 않았다.
- **metatft.com 6.0점** — 강점: [직접 확인: 375x812 에뮬레이션, DOM 측정, 오늘 호출]
- 한눈에 읽기는 셋 중 가장 좋다.
  - 첫 화면 안에 티어 엠블럼과 '다이아몬드 IV 0 LP'(18px, y=735)가 들어온다.
  - 바로 아래에 큰 숫자 카드 3개(평균 등수 4.13 / 4위권 57.1% / 승리 12.5%)와 서버 순위 '#6,397 상위 1.0%'가 있다.
  - 플레이어 태그 칩('연패 중', '감시자 장인', '니달리 장인')이 이어진다.
- 매치 행에 경기 후 LP와 증감(+36 LP / -35 LP), 게임 시간·탈락 라운드(29:27 • 5-2), 레벨이 한 줄에 있다.
- 행을 누르면 그 자리에 상세가 펼쳐진다.
  - 탭: 플레이어 / 개인 요약 / 타임라인 / 라운드 상세 / 상점 분석, 링크 복사
  - 로비 요약: '강한 로비 · AP 중심 · 상대 평균 랭크 다이아몬드 IV 28 LP'
  - 8명의 라이엇ID#태그, 티어 단계, LP
- 반응형 CSS다. @media가 250개이고, 플레이어 화면 전용 규칙이 450/500/767/991px 구간에 있다(.PlayerGameMatch, .PlayerProfileTop, .PlayerScoutingContainer). 페이지 가로 넘침은 없고 줌도 막지 않는다.
- 게임 모드 탭은 48px 높이이며 가로로 스크롤된다.
- ko_kr 번역 키 2,709개로, en_us 기준 빠진 키가 0개다.
- 수동 갱신 없이도 last_refreshed가 12:46:59Z였다. 연속 조회로는 바뀌지 않아 누가 갱신했는지는 확인하지 못했다.
- [코드 확인, 실제 응답 미확인] 프로필 주인이 게임 중이면 참가자 전원의 프로필을 불러와 보여 준다(summoner_by_puuid, 같은 판 5분 재조회 금지, 참가자 데이터 30분 캐시). / 약점: - 광고가 읽기를 방해한다. 116px 높이의 떠 있는 동영상 광고(avp-fixed avp-top)가 스크롤 중에도 화면 위에 붙어 있고, 본문 위에 225px 동영상 광고 영역이 또 있다. iframe은 8개다.
- 글자가 작다. 텍스트 노드 1,219개 중 338개가 9~11px다(10px 172개, 11px 153개).
  - 매치 행의 시간은 10px, 평균 등수 라벨은 11px로, 한 손 사용 중 흘깃 보기에 작다.
  - 접힌 매치 행 하나가 229px인데 유닛 성급·코스트 숫자가 라벨 없이 나열된다.
  - 펼친 상세는 2,194px로 길고, 참가자 링크는 높이 18px다.
- 한국어가 부자연스럽다.
  - 영어가 남아 있다: 탭 'Stats'/'Tags'(번들에 영문 고정), 'Stream Overlay', 등수 차트의 '1st~8th'·'Placement', LP 그래프 축 'G I / P IV / E IV / D IV'. html lang은 'en'이다.
  - 날짜 순서가 틀렸다: '28일 8월'.
  - 오역: Eliminated→'제외됨', Forcer→'강제 플레이어', 'Expect Gold to Hit 1'→'1골드에 도달할 것으로 예상'.
  - 같은 개념에 용어가 섞인다: '4위권'과 '순방 확률', '평균 등수'와 '평균 순위'.
- 필터 구성이 lolchess보다 적다. 캘린더와 여러 계정 즐겨찾기 표가 없다.
- 관전 실행과 로비 스카우팅 태그는 데스크톱 앱 전용이다.

가져올 아이디어:
- [metatft] 경기별 LP 증감 표시. 앱이 이미 호출하는 lookup_by_riotid 응답의 ranked_rating_changes를 쓴다. 오늘 확인한 값은 109건이고, rating_numeric이 티어를 넘어 이어지는 연속값이다(다이아 IV 0 LP=2400, 다음 경기 2436). created_timestamp 순으로 이웃 값의 차를 구해 경기마다 +36/-36을 얻고, 오버레이 등수 칩 아래에 작은 숫자로 붙인다. 경기와는 match_timestamp가 가장 가까운 것끼리 짝짓는다(예: 11:22:51 경기 ↔ 11:22:19 기록). 경기 112판 대비 기록이 109건이라 짝이 없는 판은 비워 둔다. 새 엔드포인트가 필요 없다.
- [metatft] 요약 숫자를 크게 3칸으로 두고 서버 백분위를 붙인다. 같은 응답의 server_rank{rank:6397,total:618215}로 'KR 6,397위 · 상위 1.0%'를 계산해 티어 줄 바로 아래에 둔다. 표기는 한국 사용자에게 익숙한 '상위 x%'로 쓴다. 평균 등수·톱4·1등 비율은 metatft처럼 큰 숫자 세 칸으로 설정 화면 맨 위에 둔다. 금방 끝나는 작업이다.
- [metatft] 경기별 로비 강도. matches[].avg_rating(오늘 값 예: 'DIAMOND IV 28 LP', 'EMERALD I 84 LP')을 기존 TIER_KO 맵으로 한글화해 최근 경기 목록에 '로비 평균 에메랄드 I'로 보여 준다. 같은 등수인데 LP가 크게 다른 이유를 한눈에 읽을 수 있다.
- [metatft] 인게임 로비 참가자 카드. metatft 프로필 코드와 같은 흐름이다: summoner.puuid → https://api.metatft.com/tft-spectate/summoner_by_puuid/kr/{puuid} 호출 → gameMode=='TFT'이면 participants[].puuid마다 공개 프로필 조회. 같은 판은 5분 안에 다시 부르지 않고 참가자 데이터는 30분 캐시한다. 오버레이에 8명의 한글 티어와 최근 10판 평균 등수를 한 줄씩, 접을 수 있게 둔다(lolchess식 '최근 10 매치 · 평균 등수' 문구). 게임 중 응답은 아직 받아 본 적이 없으므로(게임 밖에서는 '{}') 실제 판에서 필드를 먼저 확인하고, 다른 플레이어의 데이터는 기기에 저장하지 않는다.
- [metatft] 짧은 한글 플레이어 태그를 기기 안에서 계산한다. 최근 20판 기준 예: '연패 중'(최근 3판 모두 5등 이하), '○○ 장인'(summary.units의 DA_* 최빈 유닛을 CommunityDragon ko_kr로 한글화). metatft의 '강제 플레이어' 같은 직역은 피하고 문구는 우리가 직접 쓴다.
- [metatft] 최근 경기 칩을 길게 누르면 metatft 경기 페이지(https://www.metatft.com/player/kr/{이름-태그}?match={riot_match_id})를 여는 딥링크를 둔다. 8명 보드와 로비 요약을 직접 만들지 않고도 매치 상세를 제공할 수 있다. ?match= 링크 형식은 번들에서 확인했지만, 이 주소로 열었을 때 해당 경기가 펼쳐지는지는 미검증이다.

#### 구현 가능성

재확인은 2026-09-15 12:48~12:55Z(21:48~21:55 KST)에 브라우저 UA로, 인증·쿠키 없이 했다. 데이터가 두 종류로 나뉜다. 개인 전적은 사람마다 달라 하루 1회 수집기에 넣을 수 없고, 기기가 직접 호출해야 한다(현재 앱 구조도 그렇다). 집계형 데이터(컷, 랭커)만 수집기에 들어갈 수 있다.

[직접 확인: metatft]
- lookup_by_riotid/KR/랄라붕/KR1: 200, 211,127B. 첫 호출 10.3초, 재호출 1.76초. UA 헤더를 지워도 200.
  - 내용: DIAMOND IV 0 LP, 최고 DIAMOND III 29 LP, server_rank 6397/618215, 112경기 전부 큐 1100 TFTSet18, 증강은 0/112경기에만 값이 있음.
  - summary.units의 character_id와 itemNames는 DA_*, 특성은 'DA_18_Hunter_1' 형태.
- rating_changes: 200, 17,421B, 0.9초, 109행(num_games, rating_text, rating_numeric, created_timestamp).
- matches3.metatft.com/KR_8382256353.json: 200, 24,953B, 'max-age=365000000, immutable'. participants 8명, participant_info 8명 전원에 ranked.rating_text와 mmr이 있음.
- tft-spectate/summoner_by_puuid/kr/{내 puuid}: 200 '{}'. top_players?region=kr: {length:0}.

[직접 확인: lolchess]
- tft.dakgg.io profile: 200, 2,123B, 0.10초, no-store, ACAO *.
  - 내용: DIAMOND IV 0LP, plays 112, avgPlacement '4.12'(문자열), rating.tier 6353/617673/1.029%.
- matches: 200, 394,933B, 20경기×8인, totalCount 112, 로비 summonerLeagues 동봉.
- league-logs: 200, 1,039B, 21행, summonerTierCutoffs CHALLENGER 500 / GM 200.
- spectate-matches/kr: 12:48Z와 12:55Z 모두 count 0.
- GET만으로는 갱신되지 않는다. syncedAt이 11:57:08Z 그대로였다.
- 이용약관(/about/terms_and_service)에서 사전 승낙 없는 정보 복제·제3자 제공 금지, 타 이용자 개인정보 수집·저장 금지, 상업적 사용 금지 조항을 직접 읽었다.

[직접 확인: lol.qq]
- get_tier_rank_1000: Referer가 있든 없든 200, result 0, 1967행. 이번에 새로 확인한 사실이다. 같은 date 20260915인데 벤치마킹 때(1043/663/261)와 분포가 달라 하루 중에도 갱신되는 것으로 보인다(추정).
- fightlist:
  - Referer 있음: 200, text/html 'var LWDFramework_Swoole=', code 0, 10경기 TFTSet18, 행마다 login_ip 필드 있음.
  - Referer 없음: {ret:'-10000', msg:'请求不合法'}.

[직접 확인: DA_* 결합] CommunityDragon ko_kr.json(Last-Modified 2026-09-12) apiName과 정확 일치로 대조했다.

| 소스 | 유닛 | 아이템 | 특성 |
|---|---|---|---|
| metatft | 64/64 | 92/92 | 원문 0/72, '_N' 제거 후 35/35 (경기 JSON 특성은 31/31) |
| lolchess | 65/66 (TFT18_Akali 불일치) | 105/106 (DA_Artifact_Hullcrusher 불일치) | 35/35 |
| lol.qq | 41/41 | 48/48 | 해당 없음 |

경기 8382256353 하나를 metatft와 lolchess에서 받아 등수, 유닛·성급·아이템, 특성 단계를 비교했다. 8/8명이 완전히 같았다. 세 소스를 DA_*로 합칠 수 있다는 것이 실측으로 증명됐다.

[번들 확인, 실응답 미관찰]
- metatft는 lookup 응답에 refresh.status=='queued'가 오면 refresh_by_riotid를 [1,2,4,8,16,32,64]초 간격으로 최대 7회 폴링한다. 오버레이 위젯은 '&refresh=true'에 60초 스로틀을 둔다.
- metatft 약관(2023-06-11)의 금지 행위는 불법 콘텐츠와 공정 플레이 두 항목뿐이다. scrap, crawl, automat, API, commercial, redistribut 키워드는 없다.
- Riot 상태 API kr1은 200이고 장애·점검이 0건이었다. 두 사이트의 관전 목록이 KR 황금시간대에 비어 있는 원인은 확인할 수 없다(추정: 원천 데이터 문제).

[판정]
- metatft 8점(1위): 인증 없이, DA_* 거의 무변환으로 티어·LP, 게임별 LP 이력, 경기 상세, 로비 참가자 티어까지 기기에서 직접 받는다. 약관 위험이 낮고, 이미 앱에 통합되어 동작한다. 감점 이유는 셋이다. 10초·211KB 응답, 증강 부재, 게임 중 로비 기능이 현재 빈 응답이라 구조를 확인할 수 없다는 점이다.
- lolchess 6점: 기술적으로는 가장 빠르고 가볍다. 하지만 두 가지가 metatft보다 확실히 불리하다. 약관이 재사용을 명시적으로 금지하고, 최신 데이터를 받으려면 사용자마다 서버에 Riot 호출을 시키는 sync RPC가 필요해 차단 위험이 크다.
- lol.qq 2점: 한국 계정 전적을 원천적으로 제공할 수 없어 이 카테고리의 핵심을 구현할 수 없다. 남는 가치는 중국 랭커 보드와 LP 컷을 모으는 수집기용 보조 데이터뿐이고, 그마저 Referer 위조, Set 6 시절 레거시 경로, login_ip 노출이라는 부담이 있다.

작업 파일은 C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/judge_profile/ 에 있다. 저장소 코드는 수정하지 않았다.

- **lol.qq.com/tft 2.0점** — 강점: [직접 확인] 段位排行 get_tier_rank_1000은 Referer 없이도 200이다(965KB, 대区1 1967행: 王者 261 / 宗师 665 / 大师 1041). fightlist는 Referer를 붙이면 10경기(TFTSet18, 큐 1100)를 준다. 유닛 41/41, 아이템 48/48이 CDragon DA_*와 정확히 일치한다. 중국 상위 랭커의 보드와 LP 컷을 하루 1회 모으는 보조 데이터로는 쓸 수 있다. / 약점: 이 카테고리의 핵심을 제공할 수 없다. 한국 사용자의 내 티어·LP, 최근 전적, 매치 상세, 인게임 정보가 모두 해당된다. 중국 서버 계정만 다루고, 랭커 목록 말고는 이름으로 puuid를 찾는 공개 API가 없다. [직접 확인] fightlist는 Referer가 없으면 ret -10000 请求不合法을 준다. 즉 Referer 위조에 기대야 한다. 응답은 text/html에 'var LWDFramework_Swoole=' 래핑이고, 경로는 Set 6 시절(a20211021tftSet6) 레거시라 폐기될 수 있다. 벤치마킹에 따르면 같은 계열 master/info는 이미 -20022다. 경기 목록 행마다 login_ip 필드가 있어 개인정보 위험이 있다. 실시간 인게임 기능은 없다(一键应用은 QQ 로그인이 필요한 쓰기 기능).
- **lolchess.gg 6.0점** — 강점: [직접 확인] 인증이 필요 없고 ACAO가 *이며 CloudFront ICN을 거친다. 셋 중 가장 빠르고 가볍다(profile 2KB 0.10초, league-logs 1KB). 한 번의 호출로 받는 것: 티어·LP, 백분위(rank 6353/617673, 1.029%), LP 로그 21행([시각, 티어, 단계, LP, 누적 게임, top4]), 티어 컷(summonerTierCutoffs: CHALLENGER 500, GRANDMASTER 200), 매치 20경기×8인, 로비 참가자 티어(summonerLeagues). DA_* 결합은 유닛 65/66, 아이템 105/106, 특성 35/35이고 특성에 접미사가 없다. 같은 경기 8/8이 metatft와 일치했다. meta.totalCount로 페이지 수를 알 수 있다. / 약점: [직접 확인] 이용약관이 세 가지를 금지한다: 회사 사전 승낙 없이 얻은 정보를 복제하거나 제3자에게 제공하는 것, 승인 없이 다른 이용자의 개인정보를 수집·저장하는 것, 취득한 정보를 가공·판매하는 등 상업적으로 쓰는 것. 공개 저장소 앱에서 쓰면 약관 위반 소지가 크다. GET은 갱신을 일으키지 않는다(12:48Z에 조회해도 syncedAt이 11:57:08Z 그대로였다). 최신 전적을 보려면 앱이 사용자마다 /rpc/summoner-sync를 불러야 한다. 이 호출은 서버가 Riot API를 부르고 retryAfter로 폴링하는 방식이라 차단 위험이 가장 크다(추정). matches는 20경기에 395KB로 무겁다. CDragon에 없는 ID(TFT18_Akali, DA_Artifact_Hullcrusher)가 섞여 있고, avgPlacement는 문자열 '4.12'다. spectate-matches/kr은 12:48Z와 12:55Z 모두 0건이었다. Riot 상태 API kr1에는 장애 공지가 없어 원인은 모른다. 그래서 인게임 응답 구조는 확인하지 못했다.
- **metatft.com 8.0점** — 강점: [직접 확인] 인증, 쿠키, Referer가 필요 없고 UA 헤더를 지워도 200이다. lookup_by_riotid 한 번(211KB)에 다음이 모두 온다: 티어·LP('DIAMOND IV 0 LP', rating_numeric 2400), 최고 티어, 서버 순위 6397/618215, 등수 분포, 세트18 랭크 112경기(등수, 유닛, 성급, 아이템). rating_changes(17KB, 0.9초)는 게임별 LP 기록 109행이다. 경기 상세 matches3/KR_8382256353.json(25KB, immutable 캐시)은 8인 보드에 더해 participant_info 8/8에 티어와 MMR이 있어, 지난 판 로비 정보로 쓸 수 있다. DA_* 결합: 유닛 64/64, 아이템 92/92가 CommunityDragon apiName과 정확히 일치하고, 특성은 '_N' 접미사만 떼면 35/35다. 같은 경기를 lolchess와 대조하니 8명 전원의 보드가 같았다. 2023-06-11 약관에는 스크래핑이나 재배포를 금지하는 조항이 없다. 앱의 ProfileRepository가 이미 이 API로 동작하고 있어 추가 구현 비용이 가장 낮다. / 약점: [직접 확인] 첫 호출은 10.3초, 두 번째는 1.8초가 걸렸고 응답이 211KB로 무겁다. 오버레이에서 주기적으로 부르기엔 부담이다. 112경기 모두 증강이 빈 배열이다. 인게임: summoner_by_puuid는 '{}'였고, top_players?region=kr은 KR 저녁 21:55에도 0건이었다. 그래서 게임 중 로비 응답 구조는 확인하지 못했다. 프로필 요약의 특성 이름에 '_1' 같은 단계 접미사가 붙는다. [번들 확인] lookup 응답에 refresh.status 'queued'가 오면 사이트는 refresh_by_riotid를 1~64초 백오프로 폴링한다. 앱은 이 필드를 무시하므로 갱신 전 데이터를 한 번 보여 줄 수 있다(실제 queued 응답은 못 봄). [추정] 비공개 API라 예고 없이 바뀔 수 있다. 첫 호출이 10초 걸린 것으로 보아 요청마다 서버 작업이 무거운 것 같고, 과도한 폴링은 차단을 부를 수 있다. 경기 JSON에 다른 플레이어의 puuid, riot_id, MMR이 들어 있어 저장하면 안 된다.

가져올 아이디어:
- [lolchess 백분위 표시] 프로필 카드에 '상위 1.03%'를 보여 준다. 추가 호출 없이 기기에서 metatft server_rank {rank 6397, total 618215}로 계산한다. lolchess rating.tier(6353/617673/1.029%)로 같은 규모의 모집단을 교차 확인했다.
- [lolchess league-logs 발상] 게임마다 LP가 얼마나 올랐거나 내렸는지 오버레이의 최근 등수 옆에 표시한다. metatft rating_changes(17KB, 0.9초, 109행)에서 연속 행의 rating_numeric 차이와 num_games로 계산한다. 오버레이의 주기 호출은 211KB·최대 10초인 전체 프로필 대신 이 가벼운 엔드포인트로 바꾸고, 전체 프로필은 상세 화면에서만 부른다.
- [lolchess summonerTierCutoffs 발상] '그랜드마스터·챌린저까지 남은 LP'를 보여 준다. lolchess 값(CHALLENGER 500, GM 200)은 고정 최소치로 보인다(추정). 실제 컷은 수집기가 하루 1회 metatft public/promotion_thresholds/latest를 받아 JSON으로 배포한다. 이 엔드포인트는 벤치마킹 때만 확인했고 이번에 다시 호출하지는 않았다.
- [lolchess 갱신 폴링과 Riot 상태 공지] metatft lookup 응답에 refresh.status 'queued'가 있으면 1, 2, 4, 8초 백오프로 lookup을 다시 부르고 '갱신 중'을 표시한다. 번들 코드로 확인했고 실제 queued 응답은 못 봤다. 인게임이나 관전 데이터가 비면 lol.secure.dyn.riotcdn.net/channels/public/x/status/kr1.json을 확인해 장애 공지를 띄운다(이 URL은 200 확인, 지금은 장애 0건). 장애 공지도 없으면 '진행 중인 게임 정보 없음'으로만 안내한다.
- [lol.qq 段位排行 + fightlist를 수집기에] '중국 王者 최근 보드'를 만든다. get_tier_rank_1000(Referer 불필요)에서 대区1 상위 20~50명을 고르고, fightlist(Referer 필수)로 각 10경기를 받는다. piece_list의 DA_* 유닛·아이템·성급을 집계해 덱 상세와 아이템 역검색에 '중국 최상위 랭커가 최근 사용' 근거로 붙인다. puuid, nick, login_ip는 버리고 집계만 배포한다. 호출은 하루 수십 회로 제한하고, ret -10000이면 status 'missing'으로 건너뛴다.
- [lol.qq 중국 서버 컷] 수집기가 대区별 get_tier_rank_1000에서 最强王者와 傲世宗师의 최저 point를 하루 1회 뽑는다. 이를 KR 컷과 나란히 '중국 서버 챌린저 컷'으로 보여 준다. 개인 식별자 없이 숫자 두 개만 배포한다.
- [lolchess 매치 응답의 로비 티어 동봉 발상] '지난 판 로비' 화면을 만든다. metatft matches3 경기 JSON(25KB, immutable)을 최근 한 경기만 받아 8인의 등수, 보드, participant_info 티어를 보여 주고 기기에 영구 캐시한다. 다른 플레이어의 riot_id, puuid, mmr은 화면 표시에만 쓰고 캐시에는 넣지 않는다.

## 사이트별 벤치마킹

### lol.qq.com/tft

- **접근 가능**: 예
- **식별자 체계**: 세 가지 ID가 섞여 있다.

1) DA_* apiName: 편집 덱(hero_id/equipment_id/hexbuff/contact), 数据检索器 전체(unit_id·trait_id·item_id·filterOptions key), tft_hero_ranking.hero_id, tft_equip_ranking.equip_id, 레거시 equiprank, 胜率阵容의 아이템·증강(rune_id), fightlist의 character_id/itemNames. 정적 파일에서는 chess.hero_EN_name, race/job.characterid, equip.englishName(콤마로 여러 개 가능), hex.augments가 이 값이다.

2) 숫자 ID: chessId=TFTID("100340"), traitId=raceId/jobId("10379"), equipId("92988"), hexId("94859").
- 사용처: 胜率阵容(lineup, main_c_chess, main_trait_list), tft_trait_strength_trend, tft_main_trait_lineup, key_chess의 chess_id, 레거시 herorank/mbrank/sbc, datasearch의 champion_content_id_num/main_traits_num.
- DA_*로 바꾸려면 정적 chess/race/job/equip/hex.js로 매핑한다. 레거시 herorank 58/58, mbrank 특성 78/78이 매핑된다(직접 확인).
- 트레잇 단계 표기: 신규 API는 "id,개수;id,개수", 레거시는 "_" "$" "#" 구분자를 쓴다.

3) 중국어 이름(displayName/name): 표시용일 뿐이다.

[DA_* 이름 규칙이 섞여 있음]
- 챔피언: DA_18_Ahri형 63개, DA_Vi18형 13개, DA_Lux18_Base 등 6개.
- 특성: DA_18_Adaptor와 DA_Juggernaut18이 공존.
- datasearch 확장 키: "DA_Amumu18,2"(성급), "DA_Draven18#A|B|C"(착용자#아이템).

[CommunityDragon ko_kr.json과 정확 일치 (직접 확인)]
- hero_EN_name 74/82 (불일치는 AzirSoldier 등 소환물 8개)
- race/job characterid 36/36
- equip.englishName 498/502
- hex.augments 250/265
- DA_InfinityEdge도 CDragon에 apiName으로 존재한다. 따라서 메모리의 '아이템만 접두사가 다름'은 정확 일치로도 해결된다(TFT_Item_InfinityEdge도 별도로 존재).

[주의]
- tft_hero_ranking의 time_type=v 응답에 TFT17_* ID가 섞여 있다.
- 레거시 mbrank의 season_id는 내부 번호 "25"다.
- **샘플 폴더(당시)**: `C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/bench/lolqq/`

**기술 메모**

[구조] Vue2+Vuex+VueRouter 해시 SPA. 화면 템플릿은 index.html 안의 <template id>(316KB), 메뉴별 로직은 js/component/page-*.js, 설정은 js/tft-mode-registry.js(CurrentSet='s18', 모드별 queueId: s18=1100 랭크, s17 RGM=6110, s16m17=1210, channelId 6).
공통 SDK는 두 개다.
- tftlib: https://game.gtimg.cn/images/lol/tfth5lib/v1/tftlib.es5.umd.js (정적 게임 데이터·덱 로더)
- tftdatalib: https://game.gtimg.cn/images/lol/zmtftzone/winlineuplibpbe/tftdatalib.es5.umd.js (통계 프록시 래퍼)

[메뉴 14개, 해시 라우트]
阵容 #/index, 胜率阵容 #/wrlineup, 国服大数据(数据排行) #/rank/list, 数据检索器 #/datasearch, 运营节奏 #/strategy, 模拟器(외부 https://lol.qq.com/act/a20220802tftsimulator/), 资料纵观 #/overview, 版本 #/news, 羁绊 #/synergy, 英雄 #/champion, 强化 #/hex, 装备 #/equipment, 小小英雄 #/littlehero, 段位排行 #/rank/tier.
숨은 라우트: #/championDetail/{season}/{chessId}, #/lineupDetail/{season}/{lineupId}/{detail|detail_2}, #/authorDetail/{authorId}. #/masterDetail/{puuid}/{area}는 주석 처리됨.

[데이터 로딩 3계층]
1) 정적 CDN JSON (game.gtimg.cn): CORS *, max-age 120, 인증 없음. 시즌 파일 경로는 versionconfig.json에서 읽는다.
2) 통계 게이트웨이: POST https://mlol.qt.qq.com/go/exploit/proxy, body {req_alias, version_id, req_params}.
   - 서명·쿠키 불필요. Referer/Origin 없이, okhttp UA로도 result 0을 직접 확인했다.
   - 응답 ACAO가 https://lol.qq.com 이라 브라우저/WebView 직접 호출만 CORS에 막힌다.
   - 확인한 alias: tft_hero_ranking, tft_equip_ranking, tft_trait_strength_trend, tft_main_trait_lineup, tft_lineup_group_list, tft_lineup_all_detail, tft_lineup_position, tft_lineup_key_chess, tft_lineup_key_chess_equip, tft_key_equip_recommend_chess, tft_recent_versions, tft_match_overview, tft_lineup_rank, tft_hero_rank, tft_trait_rank, tft_equip_rank, tft_two_equip_rank, tft_three_equip_rank.
   - tft_augment_rank는 화이트리스트 전용, tft_lineup_change_list는 빈 응답. 번들에는 hetu_tft_6130_* alias도 있다(미호출).
3) 레거시 swoole 서버: lol.sw.game.qq.com/lol/lwdcommact/...
   - JSONP(callback 파라미터) 또는 callback이 없으면 'var LWDFramework_Swoole={...}' 형식으로 준다.
   - Referer https://lol.qq.com/ 가 필수다(없으면 ret -10000 请求不合法). UA는 okhttp여도 된다.

[api.js 주석과 현재 동작의 차이]
api.js의 herorank/equiprank/mbrank JSONP는 지금 영웅 상세·장비 페이지에서만 쓰인다. 데이터排行은 tftdatalib 프록시로 옮겨졌다.

[차단·서명]
- 수십 회 연속 호출하는 동안 차단·레이트리밋은 보지 못했다. 한국 IP에서 전부 접근 가능.
- 서명은 두 곳뿐이다(코드로만 확인, 둘 다 미호출): 옛 get_total_tier_rank_list(md5, 키가 JS에 노출), CMC complexDetail(md5+timestamp). 현재 통계 API에는 서명이 없다.
- 로그인은 Milo QQ Connect(appId 101491592), 대区 선택은 localStorage에 저장한다.

[프로필 URL]
- 사용자 프로필 페이지는 없다. 段位排行 응답의 intent는 掌盟 앱 딥링크(qtpage://tft/battle?uuid=..&region=..)다.
- 모바일 상세 페이지 형식:
  - https://lol.qq.com/act/a20200224tft/page/winLineup-detail/{season}/index.html?time_type=&tier_part=&queue_id=&champion_content_id=&main_traits_id=&mc_champion_id=&minor_traits_id=
  - .../datarank-equip-detail/{season}/?id={DA_item}&iqueue_id=&time_type=&tier_part=255

[조사 방식]
in-app 브라우저는 정책상 차단이라, JS 번들을 전부 내려받아 코드를 분석하고 python urllib로 직접 호출했다.

**이 사이트만의 강점**

- 중국 서버(国服) 랭크 표본이다. 하루 약 240만 플레이어-게임 규모의 대량 데이터이며, 앱의 '중국 한정 덱' 판정의 원천이다. (metatft에 중국 서버 표본이 있는지는 이번에 확인하지 않음)
- 数据检索器의 조건 조합 검색: 티어 세분화(白银~王者), 1~3일 기간, 빌드 단위 버전, 레벨·3성·神器/纹章/光明 개수, 챔피언(성급·아이템수)·아이템(착용자)·특성 조건 카드를 조합한다. 1/2/3件装 아이템 조합 랭크와 '챔피언#아이템' 키가 있어 앱의 아이템 역검색에 그대로 쓸 수 있고, 키가 DA_* apiName이라 소스 통합도 쉽다.
- 당일 데이터(수시간 지연)와 게임 수(total_games, 순위·레벨 분포)를 공개한다. 표본 크기를 화면에 보여줄 수 있다.
- 胜率阵容 상세: 유닛 배치 칸(x,y)별 사용률·승률, 덱별 증강의 선택 단계(1/2/3)별 평균순위, 핵심 유닛의 성급 분포와 아이템 수, 부품별 추천 착용자.
- 공식 작가 편집 덱: 등급 SS~C, 레벨 6/8/9 보드, 초·중반 운영 보드, 대체 아이템·증강, 운영 텍스트, 작가·플랫폼 정보.
- 챔피언·아이템의 10일 일간 추세와 버전 간 비교, 레거시 API의 환비(전기 대비 변화).
- 로그인 없이 경기 목록·8인 경기 상세(증강 포함)를 주는 공개 API가 있다. 단 중국 서버 puuid가 필요하다.
- 게임 클라이언트 阵容助手 연동(一键应用). QQ 로그인이 필요하고 현재 버튼은 숨겨져 있다.
- 공식 중국어 명칭·설명, 仙灵(wisps) 등장 단계 분류, 상점 확률표.

**수집 위험** — [스키마 변동]
- 시즌마다 바뀌는 것: SDK 함수명(requestS18LineupList 등), mode-registry의 queueId와 경로, version_id(dgroup_v3/v1), time_type(d_grouping_v3).
- 정적 파일 경로에 패치 문자열(16.17-2026.S18)이 들어가므로 versionconfig.json을 먼저 읽어야 한다.
- 정적 데이터가 라이브보다 한 패치 늦다(16.17 대 16.18).
- 이미 폐기·숨김된 기능: 레거시 a20210906api master/info(-20022), masterDetail 라우트, 一键应用 버튼, 符文相关性 탭. 비공개 API라 예고 없이 사라질 수 있다.

[데이터 품질]
- 숫자가 모두 문자열이다.
- tft_hero_ranking의 time_type=v 응답에 S17 행과 비정상 거대 행(1.6만 원소)이 섞여 3.6MB가 된다.
- tft_equip_ranking의 hero_list는 이름 파싱이 깨져 있다("18", "Lux18").
- DA_* 이름 규칙이 세 가지 섞여 있다(DA_18_X / DA_X18 / DA_X18_Suffix).
- 数据检索器 파라미터 제약:
  - version은 전체 빌드 문자열만 받는다.
  - filterOptions key는 DA_*만 받는다.
  - 7일·14일은 빈 응답이다.
  - 0~2시 CST에는 당일 데이터가 없다.
- tft_lineup_change_list는 빈 응답이다.
- 레거시 JSONP는 '_ $ # ; |' 구분자 문자열을 파싱해야 한다.
- 편집 덱의 detail은 제어문자가 섞인 JSON 문자열 안의 JSON이다.

[차단·인증]
- 레거시 swoole 서버는 Referer를 검사한다(현재는 위조로 통과). 검사가 강화될 수 있다.
- 프록시는 ACAO가 lol.qq.com이라 WebView/브라우저에서 직접 호출하면 CORS에 막힌다. 서버 수집기나 네이티브 호출은 무관하다.
- 프론트가 600만 게임 초과 조회를 막아 둔 것으로 보아 백엔드 부하에 민감하다. 대량 수집은 레이트리밋을 부를 수 있다(이번에는 관찰되지 않음).
- tft_augment_rank는 QQ 로그인과 화이트리스트가, 一键应用·내 정보는 QQ 로그인이 필요하다.
- 한국 사용자 전적은 불가능하다(중국 서버 계정만 해당).

[법·개인정보]
- 텐센트 비공개 API라 약관상 회색지대다.
- fightdetail은 타인의 openid·login_ip·originalPuuid를 로그인 없이 노출한다. 앱에서 수집·저장·표시하지 말고 집계 통계만 쓰는 것을 권장한다.
- author.json·lineupauthor_data에 작가 QQ 번호가 들어 있다. 앱에는 이름만 싣는 것이 좋다.

[시간대]
- 날짜 파라미터와 dtstatdate는 Asia/Shanghai 기준이다.

#### 기능 12개

| 카테고리 | 메뉴 | 보여주는 것 | 엔드포인트 | 확인 | 인증 | 표본·갱신 |
|---|---|---|---|:---:|---|---|
| deck | 阵容 (공식 작가 편집 덱) + 덱 상세/작가 상세 | - 작가가 편집한 추천 덱 26개<br>- 등급 quality SS/S/A (필터에는 B/C도 있음)<br>- 중국어 덱 이름<br>- 최종 보드: 좌표 r,c / 별 / 캐리 / 아이템 3개 / 대체 아이템<br>- 6·8·9레벨 보드, 초·중반 운영 보드<br>- 추천·대체 증강(hexbuff), 아이템 우선… | `GET https://game.gtimg.cn/images/lol/act/tftzlkauto/json/lineupJson/s18/6/lineup_detail_total.json \| 단건 GET https://game.gtimg.cn/images/lol/act/tftzlkauto/jso… | ✓ | 없음. 정적 CDN이며 CORS *. Referer 불… | 통계가 아니라 편집 덱이다.<br>- 26개 전부 status 5, 패치 16.18<br>- update_time 범위 2026-08-27 ~ 2026-09-14, 파일 L… |
| deck | 胜率阵容 (실전 데이터 기반 승률 덱) + 상세(증강·아이템·배치 통계) | 목록 (그룹 45개 / 조합 425개):<br>- 주·부특성, 유닛 8개<br>- 메인C / 보조C / 2보조C와 각자의 아이템 3개<br>- 추천 증강 5개<br>- 登场数(use_num), 前四率, 登顶率, 平均排名, 排名提升(avg_rank_diff), 사용률 변화<br><br>상세:<br>- 증강별 평균순위(선택 단… | `POST https://mlol.qt.qq.com/go/exploit/proxy body {"req_alias":"tft_lineup_group_list","is_return_source":0,"version_id":"dgroup_v3","req_params":{"queue_id":"… | ✓ | 없음. Referer/Origin 없이, okhttp… | - 국서버 랭크 큐 1100, 티어 버킷 선택형<br>- tier_part=2 목록: dtstatdate/period 20260915(당일)<br>- 상세: dtstatda… |
| trait | 羁绊排行(数据排行 안) + 羁绊(정적 도감) | 排行:<br>- 주요 특성 조합 50행(예: 10389,3 + 10390,3)의 登顶率·前四率 최신값과 5주기 추세<br>- 행을 펼치면 그 조합의 승률 덱 상위 2개(유닛·아이템·登场数·前四·登顶·排名提升)<br><br>羁绊 도감 (통계 없음):<br>- 특질(race) 24개, 직업(job) 12개<br>- 단계별… | `POST https://mlol.qt.qq.com/go/exploit/proxy {"req_alias":"tft_trait_strength_trend","version_id":"v1","req_params":{"tier_part":"255","battletype":"1100"}} \|… | ✓ | 없음. 레거시 JSONP만 Referer 필수. | - 전체 티어(255), 큐 1100<br>- dtstatdate 20260914 = 2026-09-15 20시 CST 기준 T-1 일간<br>- 5주기가 일 단위인지 버전… |
| champion | 英雄 (도감) + 英雄排行 + 英雄详情 | 도감 (82기물):<br>- 가격, 특질·직업, 스킬명·설명<br>- 체력·마나·방어·마저·공격·공속·사거리·치명, 성급별 공격력·체력("40/60/90")<br>- 변경 상태(增强/削弱/最新)<br>- 레벨별 상점 확률표(하드코딩)<br><br>상세:<br>- 같은 특성 챔피언<br>- 登场率·前四率·登顶率·平均排名과 환비(전… | `POST https://mlol.qt.qq.com/go/exploit/proxy {"req_alias":"tft_hero_ranking","version_id":"v1","req_params":{"tier_part":"255","base_price":"255","iqueue_id":"… | ✓ | 없음. 레거시 JSONP만 Referer 필수. | - 전체 티어 255, 큐 1100<br>- d = 10일 일간 시계열, dtstatdate 20260914(T-1)<br>- v 응답(3.6MB)에는 S17 잔여 행(TF… |
| item | 装备 (도감+조합 통계) + 装备排行 | 도감 (502개):<br>- 효과, 조합식<br>- 분류: 基础/合成/光明/特殊/转职纹章/奥恩神器/金鳞龙/辅助<br>- 기본 아이템을 고르면 그 부품이 들어가는 완성템 목록과 각 템의 登场·前四·登顶·平均排名·환비(日/周/月)<br><br>装备排行 (142개):<br>- 前四·登顶·排名<br>- 사용 상위 3챔피언<br>- 详情… | `POST https://mlol.qt.qq.com/go/exploit/proxy {"req_alias":"tft_equip_ranking","version_id":"v1","req_params":{"tier_part":"255","itemtype":"-1","iqueue_id":"11… | ✓ | 없음. 레거시 JSONP만 Referer 필수. | - 전체 티어 255, 큐 1100, dtstatdate 20260914 일간<br>- equip_id는 equip.js englishName과 142/142 일치<br>-… |
| augment | 强化 (증강 도감; 强化果实/星神赐福/仙灵 탭) | - 증강 265개를 1/2/3단계 탭으로 이름·설명·아이콘 표시<br>- 시즌별 탭: 强化果实(s15), 星神赐福(s17), 仙灵(s18 wisps 354개: 분류, 등장 단계, 비용, 등급)<br>- 이 페이지에는 통계가 없다. 증강 통계가 나오는 곳은 세 군데뿐:<br>  - 胜率阵容 상세 augm… | `GET https://game.gtimg.cn/images/lol/act/img/tft/js/16.17-2026.S18/hex.js \| GET https://game.gtimg.cn/images/lol/act/img/tft/js/16.17-2026.S18/tft_set18_wisps_… | ✓ | 정적 파일은 없음. tft_augment_rank는 Q… | - 정적 파일: version 16.17, time 2026-09-01, Last-Modified 2026-09-09 (라이브 16.18보다 늦음)<br>- augme… |
| tierlist | 数据检索器 (조건 조합 통계 검색기, 행마다 S/A/B/C 티어) | 상단 개요:<br>- 총 게임 수, 평균순위, 前四率, 登顶率, 평균 레벨<br>- 순위 분포·레벨 분포 히스토그램<br><br>탭:<br>- 推荐阵容: 덱 100개(태그 예 稳健运营, 유닛·특성·엠블럼, 메인/보조C 아이템, 최종 레벨, avg_rank·delta, comp_s 게임 수)<br>- 棋子相关性: 유닛… | `POST https://mlol.qt.qq.com/go/exploit/proxy {"req_alias":"tft_match_overview","version_id":"v1","req_params":{"queueId":"[\"1100\"]","version":"16.18.817.4437… | ✓ | 없음. tft_augment_rank만 로그인+화이트리… | 국서버 랭크 1100, 2026-09-15 20:04 CST 기준 당일 1일치:<br>- 전체 2,409,658 / 白银+ 1,348,678 / 大师+ 24,968 /… |
| profile | 段位排行 (대区별 상위 랭커) | 대区(서버)별 TFT 랭크 상위 플레이어:<br>- 순위(1~3위 메달), 아이콘, 닉네임#태그<br>- 段位: 最强王者 / 傲世宗师 / 超凡大师<br>- 胜点(LP) | `POST https://mlol.qt.qq.com/go/exploit/get_tier_rank_1000 body {"area_id":1} \| 대区 목록 GET https://lol.qq.com/comm-htdocs/js/game_area/lol_server_select.js` | ✓ | 없음 (Referer를 붙여 확인. Referer 없는… | - 대区1: 1967행(이름은 1000이지만 초과), 大师 1043 / 宗师 663 / 王者 261, LP 1381→43<br>- date 20260915 → 일간 갱… |
| profile | 大神战绩 (레거시 경기 기록 API, 현재 UI 숨김) | 과거 대신 상세 페이지의 데이터:<br>- 최근 경기 목록: 순위, 종료 시각, 큐, 최종 보드 유닛·성급·아이템, LP<br>- 경기 상세: 8인 전원의 보드·특성·증강 문자열·레벨·남은 골드·준 피해·탈락 라운드·꼬마전설 | `GET https://lol.sw.game.qq.com/lol/lwdcommact/a20211021tftSet6/a20211021api/fightlist?puuid={puuid}&areaid={area}&filter=all&start=0&limit=10 \| GET https://lol… | ✓ | 로그인 불필요. Referer https://lol.q… | - 개인 경기 단위 기록, 최신 경기 end_time은 조회 당일<br>- 표본은 공개 랭커 1명의 10경기로만 스키마 확인<br>- 저장한 샘플은 식별자를 가림 |
| livegame | 一键应用 (게임 내 阵容助手로 덱 등록) — 실시간 인게임 기능 없음 | - 로그인한 QQ 계정의 게임 클라이언트 '阵容助手-我的阵容'에 덱을 등록한다(가장 오래된 슬롯 자동 교체)<br>- 적용 여부 표시<br>- 라이브 매치 조회·관전·오버레이는 없다 | `GET https://lol.sw.game.qq.com/lol/lwdcommact/a20240329tftRecommend/a20240329tftRecommend/getLineUp?type=0 (조회) \| setLineUp?slineUpId={id}&type=0 (계정에 쓰는 동작이라… | ✗ | QQ Connect 로그인(Milo, appId 101… | 해당 없음 (사용자별 쓰기 기능) |
| other | 运营节奏(공략) + 版本(뉴스/운영 배너) | 공략 영상·글:<br>- 通用: 强化符文攻略 / 当前版本解读 / 新手教学<br>- 进阶: 赏金猎人 / 阵容选择 / 武器搭配 / 运营节奏 / 经济 / 摆位<br>- 제목, 작성자, 조회수, 썸네일<br><br>版本:<br>- 운영 배너(operate.json), 최신 공략 6개, 앨범별 컬렉션, 패치노트 링크 | `GET https://apps.game.qq.com/cmc/cross?serviceId=245&source=ztzgw&tagids=122120,122122&typeids=1,2&logic=and&start=0&limit=4 \| GET https://apps.game.qq.com/cmc… | ✓ | 없음. 문서 상세 complexDetail은 md5 서… | - 최신 글 2026-09-15 15:36, 전체 9596건<br>- 패치 목록 최신 16.18 (2026-09-10) |
| other | 资料纵观 / 小小英雄 / 模拟器 | - 资料纵观: 시즌 테마 소개, 특질·직업별 챔피언 매트릭스, 증강 단계 설명 팝업 (정적 데이터만)<br>- 小小英雄: 꼬마전설 2014개(등급·이미지·영상)<br>- 模拟器: 외부 배치 시뮬레이터 (이번에 조사하지 않음) | `GET https://game.gtimg.cn/images/lol/tfth5lib/v1/versionconfig.json \| GET https://game.gtimg.cn/images/lol/act/img/tft/js/hero.js` | ✓ | 없음 | - versionconfig Last-Modified 2026-08-28, s18 경로 '16.17-2026.S18'<br>- hero.js version 16.18,… |

<details><summary>기능별 UX·응답 필드 전문</summary>

**[deck] 阵容 (공식 작가 편집 덱) + 덱 상세/작가 상세** — https://lol.qq.com/tft/#/index (상세 #/lineupDetail/s18/{id}/detail, 작가 #/authorDetail/{authorId})

- 보여주는 것: - 작가가 편집한 추천 덱 26개
- 등급 quality SS/S/A (필터에는 B/C도 있음)
- 중국어 덱 이름
- 최종 보드: 좌표 r,c / 별 / 캐리 / 아이템 3개 / 대체 아이템
- 6·8·9레벨 보드, 초·중반 운영 보드
- 추천·대체 증강(hexbuff), 아이템 우선순위, 仙灵(elf_id_list)
- 운영 텍스트: early/equipment/hex/location/enemy_info
- 태그(新手推荐/高手进阶), 특징(예: 7级慢D三费)
- 작가 프로필·플랫폼(虎牙/斗鱼/B站 등), 게시·수정 시각
- UX: - 모드 탭: 自然之力 s18 / 星神 s17 / 恭喜发财 s16m17
- 등급 필터: 全部/SS/S/A/B/C
- 상태 탭: 默认 / 最新(sub_time 역순)
- 특질·직업·특수羁绊 드롭다운
- 챔피언 이름 검색(일치 챔피언 하이라이트), 작가 검색, 赛季之星 선택
- 10개씩 무한 스크롤, 동시에 펼치는 카드는 최대 2개
- 상세: 레벨별 보드, 장비·증강·영상, 관련 덱(detail_2)
- 一键应用 버튼은 템플릿에서 주석 처리됨
- 엔드포인트: `GET https://game.gtimg.cn/images/lol/act/tftzlkauto/json/lineupJson/s18/6/lineup_detail_total.json | 단건 GET https://game.gtimg.cn/images/lol/act/tftzlkauto/json/lineupJson/s18/6/14266.json | 작가별 GET https://game.gtimg.cn/images/lol/act/tftzlkauto/json/lineupJson/s18/author/886.json | 메타 GET https://game.gtimg.cn/images/lol/act/tftzlkauto/json/authorJson/author.json , tagJson/tag.json , specialityJson/speciality.json , lineupTypeJson/lineupType.json , platJson/plat.json` (확인됨)
- 핵심 필드: lineup_list[] 필드:
- id, author, lineupauthor_data{name, platId, imgUrl…}
- channel "53,5,6", status "5", quality, sortID
- rel_time / sub_time / update_time
- simulator_edition "16.18", simulator_season "2026.S18", modeId, like, lineup_type
- detail (JSON 문자열 안의 JSON, 제어문자 포함):
  - line_name, needLevel
  - hero_location[{hero_id DA_18_*, equipment_id DA_* 콤마, location "r,c", is_carry_hero, numStar, equipment_replace}]
  - hero_location_l6/l8/l9, y21_early_heros, y21_metaphase_heros
  - contact[{id DA_*, num, color}], hexbuff{recomm, replace}
  - equipment_order, elf_id_list
- detail_2 (더블/연관 덱)

작가 JSON: id → {name, desc, imgUrl, platId, uuid…}
- 인증: 없음. 정적 CDN이며 CORS *. Referer 불필요. / 표본·갱신: 통계가 아니라 편집 덱이다.
- 26개 전부 status 5, 패치 16.18
- update_time 범위 2026-08-27 ~ 2026-09-14, 파일 Last-Modified 2026-09-14
- 페이지는 3분 단위 캐시버스터(v=Date.now()/180000), CDN max-age 120초

**[deck] 胜率阵容 (실전 데이터 기반 승률 덱) + 상세(증강·아이템·배치 통계)** — https://lol.qq.com/tft/#/wrlineup (상세는 掌盟 QR 페이지 winLineup-detail)

- 보여주는 것: 목록 (그룹 45개 / 조합 425개):
- 주·부특성, 유닛 8개
- 메인C / 보조C / 2보조C와 각자의 아이템 3개
- 추천 증강 5개
- 登场数(use_num), 前四率, 登顶率, 平均排名, 排名提升(avg_rank_diff), 사용률 변화

상세:
- 증강별 평균순위(선택 단계 1/2/3별), 사용수
- 부품별 추천 착용자
- 핵심 유닛의 1/2/3성 비율과 평균 아이템 수
- 핵심 유닛 아이템 조합·단일 아이템 순위
- 유닛 위치별(x,y) 사용률·승률, 레벨 분포, 대체 덱
- UX: - 모드 선택
- 티어 필터: 全部段位 255 / 大师以上 0 / 钻石以上 1 / 黄金+铂金+翡翠 2(기본) / 黄金以下 3
- 특질·직업 동시 필터
- SDK가 붙인 tag.num 오름차순, 10개씩 무한 스크롤
- 항목 hover 시 모바일 상세 QR
- 엔드포인트: `POST https://mlol.qt.qq.com/go/exploit/proxy body {"req_alias":"tft_lineup_group_list","is_return_source":0,"version_id":"dgroup_v3","req_params":{"queue_id":"1100","tier_part":"2","time_type":"d_grouping_v3"}} | 상세: 같은 URL, req_alias=tft_lineup_all_detail / tft_lineup_position / tft_lineup_key_chess / tft_lineup_key_chess_equip / tft_key_equip_recommend_chess, version_id v1, req_params {champion_content_id:"100310,100323,...", main_traits_id:"10379,4;10383,3", mc_champion_id:"100331", minor_traits_id:"10384,2;10412,2;10413,2;10385,1"(없으면 -1,-1), queue_id:"1100", tier_part:"2", time_type:"d_grouping_v3"}` (확인됨)
- 핵심 필드: 목록 data{dtstatdate, period, main_traits_data[]}:
- id = "winlineup_"+base64("1100#10379;10383$100331;100337$8")
- main_trait_list, num
- info{main_c_chess_id, main_assist_chess, list[]}
  - lineup_rank, main_trait_list[{trait_id 숫자, chess_num}], sub_trait_list
  - lineup[chessId], core_chess, free_chess
  - main_c_chess, main_c_chess_equip[DA_*], assist_chess(_equip), second_assist_chess(_equip)
  - rune_id_group[DA_*], unpopular_lineup
  - avg_rank(_diff), use_num, use_rate(_diff), top_1_rate(_diff), top_4_rate(_diff)

상세:
- all_detail: augment_data[{rune_id, info{avg_rank, use_rate, use_num, 1_/2_/3_avg_rank, rune_id_rank}}], equip_data, key_champion_data, level_data, lineup_data, more_lineup_data
- position: content_pos_data[{chess_id, position_data[{position{x,y}, use_rate, win_rate, score}]}]
- key_chess: chess_id, info{1/2/3_star_percent, equip_num, top_1_rate…}
- key_chess_equip: key_champion_items_data[{chess_id, data{equip_group, equip_list[{equip_id, avg_rank, use_num}]}}]
- 인증: 없음. Referer/Origin 없이, okhttp UA로도 result 0 확인. / 표본·갱신: - 국서버 랭크 큐 1100, 티어 버킷 선택형
- tier_part=2 목록: dtstatdate/period 20260915(당일)
- 상세: dtstatdate 20260914(T-1)
- 조합당 use_num 예시 4232
- time_type d_grouping_v3의 집계 기간은 코드·응답 어디에도 없음(미확인)
- tft_lineup_change_list는 같은 파라미터로 빈 응답

**[trait] 羁绊排行(数据排行 안) + 羁绊(정적 도감)** — https://lol.qq.com/tft/#/rank/list , https://lol.qq.com/tft/#/synergy

- 보여주는 것: 排行:
- 주요 특성 조합 50행(예: 10389,3 + 10390,3)의 登顶率·前四率 최신값과 5주기 추세
- 행을 펼치면 그 조합의 승률 덱 상위 2개(유닛·아이템·登场数·前四·登顶·排名提升)

羁绊 도감 (통계 없음):
- 특질(race) 24개, 직업(job) 12개
- 단계별 효과, 단계 색상, 해당 챔피언
- UX: - 每日/版本 버튼 (코드상 trait 요청 파라미터에는 반영되지 않음)
- 특질·직업 드롭다운 필터
- 登顶/前四 정렬, 첫 행 자동 펼침
- 도감: 특질/직업 탭, 모드 전환
- 엔드포인트: `POST https://mlol.qt.qq.com/go/exploit/proxy {"req_alias":"tft_trait_strength_trend","version_id":"v1","req_params":{"tier_part":"255","battletype":"1100"}} | 펼침 POST 같은 URL {"req_alias":"tft_main_trait_lineup","is_return_source":0,"version_id":"v1","req_params":{"queue_id":"1100","tier_part":"255","time_type":"d_grouping_v3","main_traits_id":"10389,3;10390,3"}} | 정적 GET https://game.gtimg.cn/images/lol/act/img/tft/js/16.17-2026.S18/race.js , job.js | 레거시 JSONP GET https://lol.sw.game.qq.com/lol/lwdcommact/a20200629api/A20200629api/mbrank?time_type=1&tier_part=255&raceid=255&jobid=255&callback=X (sbc?main_traits_id=10383,9, mbrl도 200 확인)` (확인됨)
- 핵심 필드: tft_trait_strength_trend: main_buff_data[]{trait_list[{trait_id 숫자, cycle=활성 개수}], 1_~5_{use_rate, top_1_rate, top_4_rate, avg_rank}}. SDK가 순서를 뒤집어 1_를 최신값으로 표시한다.

tft_main_trait_lineup: lineup_data[]{main_c_chess, main_c_chess_equip_group[DA_*], assist_c_chess, hero_id_lineup_group, key_chess_group, free_chess_group, child_trait_group[{id,num}], top_1_rate, top_4_rate, use_num, avg_rank(_diff)}

race/job.js: raceId|jobId = traitId("10380"), characterid("DA_18_ApexPredator"), name, introduce, level{개수: 효과}, race_color_list "개수:색", imagePath

레거시 문자열:
- mbrank main_buff_datas "id,lv;id,lv_登场_前四_登顶_平均_환비4#…"
- sbc "부특성조합_유닛목록_수치…"
- mbrl "상대조합_게임수_승률"
- 인증: 없음. 레거시 JSONP만 Referer 필수. / 표본·갱신: - 전체 티어(255), 큐 1100
- dtstatdate 20260914 = 2026-09-15 20시 CST 기준 T-1 일간
- 5주기가 일 단위인지 버전 단위인지는 미확인
- 정적 race/job.js: version 16.17, time 2026-09-01, Last-Modified 2026-09-09 → 라이브 16.18보다 한 패치 늦음

**[champion] 英雄 (도감) + 英雄排行 + 英雄详情** — https://lol.qq.com/tft/#/champion , https://lol.qq.com/tft/#/championDetail/s18/{chessId} , https://lol.qq.com/tft/#/rank/list

- 보여주는 것: 도감 (82기물):
- 가격, 특질·직업, 스킬명·설명
- 체력·마나·방어·마저·공격·공속·사거리·치명, 성급별 공격력·체력("40/60/90")
- 변경 상태(增强/削弱/最新)
- 레벨별 상점 확률표(하드코딩)

상세:
- 같은 특성 챔피언
- 登场率·前四率·登顶率·平均排名과 환비(전기 대비)

英雄排行 (75명):
- 登场(use_rate)·前四·登顶·排名
- 每日: 10일 일간 시계열 / 版本: 16.18 대 16.17
- UX: - 도감: 비용·특질·직업 필터, 이름 검색, 큰 그림/아이콘 전환, 모드 전환
- 상세: 日/周/月 탭
- 排行: 登场/前四/登顶/排名 정렬(排名은 오름차순), 특질·직업·비용 필터, 每日/版本, 행 클릭 시 상세
- 엔드포인트: `POST https://mlol.qt.qq.com/go/exploit/proxy {"req_alias":"tft_hero_ranking","version_id":"v1","req_params":{"tier_part":"255","base_price":"255","iqueue_id":"1100","time_type":"d"}} (time_type "v"=버전별) | 정적 GET https://game.gtimg.cn/images/lol/act/img/tft/js/16.17-2026.S18/chess.js (경로는 https://game.gtimg.cn/images/lol/tfth5lib/v1/versionconfig.json 의 urlChessData) | 상세 레거시 JSONP GET https://lol.sw.game.qq.com/lol/lwdcommact/a20200629api/A20200629api/herorank?time_type=1&tier_part=255&raceid=255&jobid=255&callback=X` (확인됨)
- 핵심 필드: tft_hero_ranking: data{dtstatdate, period, details[{hero_id "DA_18_Ahri", list[{cycle "20260914"|"16.18", use_rate, top_1_rate, top_4_rate, avg_rank}]}]}. 숫자는 전부 문자열.

chess.js: data[]{chessId=TFTID "100340", hero_EN_name "DA_18_Ahri", displayName, title, price, raceIds/jobIds(DA_* apiName), races/jobs(중국어), skillName, skillIntroduce, life, magic, startMagic, armor, spellBlock, attack, attackSpeed, attackRange, crit, attackData, lifeData, proStatus, chessRole, originalImage}

레거시 herorank: championdatas "chessId_登场_前四_登顶_平均_c1_c2_c3_c4#…" (58행)
- 인증: 없음. 레거시 JSONP만 Referer 필수. / 표본·갱신: - 전체 티어 255, 큐 1100
- d = 10일 일간 시계열, dtstatdate 20260914(T-1)
- v 응답(3.6MB)에는 S17 잔여 행(TFT17_* 65개, 16.9~16.16)과 비정상 행(TFT17_RekSai list 16394개)이 섞여 있어 chess.js로 걸러야 함
- 레거시 herorank: tier_part 0=大师以上 / 1=黄金至钻石 / 255=全部, time_type 1/7/30일, dtstatdate 20260914

**[item] 装备 (도감+조합 통계) + 装备排行** — https://lol.qq.com/tft/#/equipment , https://lol.qq.com/tft/#/rank/list

- 보여주는 것: 도감 (502개):
- 효과, 조합식
- 분류: 基础/合成/光明/特殊/转职纹章/奥恩神器/金鳞龙/辅助
- 기본 아이템을 고르면 그 부품이 들어가는 완성템 목록과 각 템의 登场·前四·登顶·平均排名·환비(日/周/月)

装备排行 (142개):
- 前四·登顶·排名
- 사용 상위 3챔피언
- 详情은 掌盟 QR
- UX: - 분류 탭, 이름·키워드 검색, 조합 표
- 排行: 前四/登顶/排名 정렬, 장비 분류 필터, 每日/版本
- 엔드포인트: `POST https://mlol.qt.qq.com/go/exploit/proxy {"req_alias":"tft_equip_ranking","version_id":"v1","req_params":{"tier_part":"255","itemtype":"-1","iqueue_id":"1100","time_type":"d"}} | 정적 GET https://game.gtimg.cn/images/lol/act/img/tft/js/16.17-2026.S18/equip.js | 레거시 JSONP GET https://lol.sw.game.qq.com/lol/lwdcommact/a20210420api/a20210420api/equiprank?callback=X&time_type=1&tier_part=255` (확인됨)
- 핵심 필드: tft_equip_ranking: details[{equip_id "DA_18_EmblemBlackthorn", list[{data{use_rate, top_1_rate, top_4_rate, avg_rank, cycle, top_1_hero_id, top_1_hero_version}, hero_list[{version "DA", hero_id}], top_2_hero, top_3_hero}]}]

equip.js: data[]{equipId "92988", englishName "DA_Artifact_InfinityForce"(콤마로 여러 개 가능), type, name, effect, keywords, formula(부품 equipId 콤마), imagePath, isShow}

레거시 equiprank: itemdetails "DA_KrakensFury_Radiant$登场$前四$登顶$平均$c1$c2$c3$c4#…" ($ 구분)
- 인증: 없음. 레거시 JSONP만 Referer 필수. / 표본·갱신: - 전체 티어 255, 큐 1100, dtstatdate 20260914 일간
- equip_id는 equip.js englishName과 142/142 일치
- hero_list의 hero_id는 "Lux18", "18"처럼 잘려 있음. 응답 전체에서 고유 챔피언 키가 8개뿐(DA_18_X형 이름 파싱 버그 추정)이라 상위 챔피언 정보는 믿기 어려움
- 레거시 equiprank: 1/7/30일 선택, 티어 0/1/255

**[augment] 强化 (증강 도감; 强化果实/星神赐福/仙灵 탭)** — https://lol.qq.com/tft/#/hex

- 보여주는 것: - 증강 265개를 1/2/3단계 탭으로 이름·설명·아이콘 표시
- 시즌별 탭: 强化果实(s15), 星神赐福(s17), 仙灵(s18 wisps 354개: 분류, 등장 단계, 비용, 등급)
- 이 페이지에는 통계가 없다. 증강 통계가 나오는 곳은 세 군데뿐:
  - 胜率阵容 상세 augment_data: 덱별 증강 평균순위·사용수
  - 胜率阵容 목록의 rune_id_group
  - 数据检索器의 tft_augment_rank (화이트리스트 전용, 탭 비노출)
- UX: - 증강 단계 탭, 이름 부분일치 검색
- 仙灵: 등장 단계·분류 필터, 검색
- 모드 전환
- 엔드포인트: `GET https://game.gtimg.cn/images/lol/act/img/tft/js/16.17-2026.S18/hex.js | GET https://game.gtimg.cn/images/lol/act/img/tft/js/16.17-2026.S18/tft_set18_wisps_categorized.js | (통계, 실패) POST https://mlol.qt.qq.com/go/exploit/proxy req_alias tft_augment_rank → result 101 访问受限 'not in white list'` (확인됨)
- 핵심 필드: hex.js: data{"145586":{id, hexId "94859", type "1|2|3", name, description, imgUrl, augments "DA_18_CaretakersAlly", hero_EN_name, fetterId, isShow, createTime}}

wisps: _meta{set 18, total_rows 354}, wisps[]{adventureId, apiName, name_cn, name_en, desc_cn, category_cn, stages_cn, price, tier, round_detail, round_detail_metatft}. metatft 데이터를 병합한 흔적이 필드명에 남아 있다.

증강 통계(胜率阵容 상세): rune_id DA_*, info{avg_rank, use_rate, use_num, 1_/2_/3_avg_rank}
- 인증: 정적 파일은 없음. tft_augment_rank는 QQ 로그인 파라미터(acctype/appid/openid/access_token)와 화이트리스트가 필요. / 표본·갱신: - 정적 파일: version 16.17, time 2026-09-01, Last-Modified 2026-09-09 (라이브 16.18보다 늦음)
- augments 265개 중 250개가 CommunityDragon apiName과 정확히 일치
- 증강 통계는 胜率阵容 상세 기준 dtstatdate 20260914

**[tierlist] 数据检索器 (조건 조합 통계 검색기, 행마다 S/A/B/C 티어)** — https://lol.qq.com/tft/#/datasearch

- 보여주는 것: 상단 개요:
- 총 게임 수, 평균순위, 前四率, 登顶率, 평균 레벨
- 순위 분포·레벨 분포 히스토그램

탭:
- 推荐阵容: 덱 100개(태그 예 稳健运营, 유닛·특성·엠블럼, 메인/보조C 아이템, 최종 레벨, avg_rank·delta, comp_s 게임 수)
- 棋子相关性: 유닛 / 유닛+성급 / 유닛+아이템 개수
- 羁绊相关性: 특성+단계
- 装备分析: 장비 개수 / 1件装 / 1件装+착용자 / 2件装 / 3件装

모든 행에 tier(S/A/B/C)와 avg_rank_delta가 붙는다.
- UX: - 버전(최근 빌드 5개), 기간 近1/2/3/5/7/14天
- 티어: 白银+ ~ 王者, 단일 티어 또는 '이상'
- 최소 표본 10~10K, 레벨 / 3성 수 / 神器·纹章·光明 개수
- 조건 카드: 챔피언(성급·장비수 범위, 최대 13), 장비(착용 챔피언 지정, 최대 15), 특성(단계, 최대 12), 증강(화이트리스트만)
- 표 정렬, 키워드 검색, 비용 다중선택, 장비 분류 필터, 검색 기록
- 600만 게임 초과 시 랭킹 요청을 프론트에서 차단
- 엔드포인트: `POST https://mlol.qt.qq.com/go/exploit/proxy {"req_alias":"tft_match_overview","version_id":"v1","req_params":{"queueId":"[\"1100\"]","version":"16.18.817.4437","stime":"2026-09-15","etime":"2026-09-15","tier":"2+","level":"","threeStarCount":"","artifactCount":"","emblemCount":"","radiantCount":"","filterOptions":""}} | 같은 req_params+minSampleSize "10"+limit으로 tft_lineup_rank(limit 100) / tft_hero_rank(limit 2000, unitType ""|with_level|with_itemcnt) / tft_trait_rank / tft_equip_rank(ShowHero "1" 선택) / tft_two_equip_rank / tft_three_equip_rank | 버전 목록 {"req_alias":"tft_recent_versions","version_id":"v1","req_params":{"env":"0"}}` (확인됨)
- 핵심 필드: overview: data[0]{total_games, top1_cnt, top4_cnt, avg_rank, avg_level, rank_dist "[\"1,380435\",…]", level_dist}

hero_rank[]{unit_id, unit_s(게임 수), unit_rate, top1_cnt, top4_cnt, total, avg_rank, avg_rank_delta, tier}
- unit_id 예: "DA_Amumu18" / "DA_Amumu18,2" / "DA_18_Yorick,0"

trait_rank[]{trait_id "DA_Juggernaut18,2", trait_s, trait_rate, …}

equip_rank[]{item_id, build_s, build_rate, …}
- item_id 예: "DA_GargoyleStoneplate" / "DA_Draven18#DA_Guinsoos<플레이어>blade" / "DA_Draven18#A|B|C"

lineup_rank[]{id, lineup_tag, lineup{traits[{trait_id DA_*, chess_num}], chess_ids[DA_*], main_c_chess_id, emblems}, champion_content_id_num[숫자], main_traits(_num), minor_traits(_num), comp_s, comp_rate, main_c_chess_equip, assist_chess_equip, final_level, tier}

recent_versions: list[{version_id "16.18.817.4437", start_time, end_time "2026091518"}]

파라미터 주의:
- version은 전체 빌드 문자열만 동작("16.18"은 빈 응답)
- filterOptions의 key는 DA_*만 동작(숫자 chessId/equipId/traitId는 total 0)
- 인증: 없음. tft_augment_rank만 로그인+화이트리스트. / 표본·갱신: 국서버 랭크 1100, 2026-09-15 20:04 CST 기준 당일 1일치:
- 전체 2,409,658 / 白银+ 1,348,678 / 大师+ 24,968 / 王者 1,615
- total_games = 순위 분포 합계 → 플레이어-게임 단위
- 2일 6,601,207, 3일 11,573,450, 어제 하루 4,191,549
- 7일·14일은 빈 배열(백엔드 제한으로 추정)
- 당일 데이터가 조회됨. 버전 end_time 18시 → 수시간 단위 갱신으로 추정
- 날짜는 Asia/Shanghai 기준. 0~2시에는 페이지가 전날로 보정

**[profile] 段位排行 (대区별 상위 랭커)** — https://lol.qq.com/tft/#/rank/tier

- 보여주는 것: 대区(서버)별 TFT 랭크 상위 플레이어:
- 순위(1~3위 메달), 아이콘, 닉네임#태그
- 段位: 最强王者 / 傲世宗师 / 超凡大师
- 胜点(LP)
- UX: - 대区 드롭다운(艾欧尼亚 电信=1 등 약 30개, 테스트 서버 제외)
- 닉네임 부분일치 검색, 초기화
- 10개씩 페이지네이션
- 행 클릭 상세(masterDetail)는 비활성
- 엔드포인트: `POST https://mlol.qt.qq.com/go/exploit/get_tier_rank_1000 body {"area_id":1} | 대区 목록 GET https://lol.qq.com/comm-htdocs/js/game_area/lol_server_select.js` (확인됨)
- 핵심 필드: get_tier_rank_1000: {result, data{date "20260915", list[{puuid, index, nick "이름#태그", icon, intent "qtpage://tft/battle?uuid=..&region=1", point, tier_text}]}}

lol_server_select.js: LOLServerSelect.STD_DATA=[{t:"艾欧尼亚 电信", v:"1", status:"1"}, …]
- 인증: 없음 (Referer를 붙여 확인. Referer 없는 경우는 미시험). / 표본·갱신: - 대区1: 1967행(이름은 1000이지만 초과), 大师 1043 / 宗师 663 / 王者 261, LP 1381→43
- date 20260915 → 일간 갱신으로 추정
- 샘플은 5행으로 줄이고 식별자를 가림

**[profile] 大神战绩 (레거시 경기 기록 API, 현재 UI 숨김)** — (라우트 주석 처리) https://lol.qq.com/tft/#/masterDetail/{puuid}/{area}

- 보여주는 것: 과거 대신 상세 페이지의 데이터:
- 최근 경기 목록: 순위, 종료 시각, 큐, 최종 보드 유닛·성급·아이템, LP
- 경기 상세: 8인 전원의 보드·특성·증강 문자열·레벨·남은 골드·준 피해·탈락 라운드·꼬마전설
- UX: filter=all, start/limit 페이지네이션(next_start). 현재 사이트에서 진입점 없음.
- 엔드포인트: `GET https://lol.sw.game.qq.com/lol/lwdcommact/a20211021tftSet6/a20211021api/fightlist?puuid={puuid}&areaid={area}&filter=all&start=0&limit=10 | GET https://lol.sw.game.qq.com/lol/lwdcommact/a20211021tftSet6/a20211021api/fightdetail?areaid={area}&gameid={exploit_id}` (확인됨)
- 핵심 필드: 응답 형식: var LWDFramework_Swoole={code, msg, data{result}}

fightlist: result{next_start, exploit_list[]}
- game_area, exploit_id, queue_id 1100, ranking, end_time(epoch), tft_set_core_name "TFTSet18"
- piece_list[{character_id DA_*, star_num, itemNames[DA_*], rarity, piece_price}]
- game_rank_list[{tier, rank, league_points, season_id 18}], achievement_list

fightdetail: result{<gameid>{duration, chess_hex, tft_set_number, queue_id, member_exploit_list[8]}}
- member: ranking, level, last_round, gold_left, total_damage_to_players, players_eliminated, traits, piece_list, augmentsStr, companion
- nickname, openid, originalPuuid, login_ip 등 식별자도 포함

같은 계열 master(a20210906api)·info(a20211021tftSet6)는 ret -20022 请求失败. mastercom/racejob/hero/equip은 미호출.
- 인증: 로그인 불필요. Referer https://lol.qq.com/ 필수(없으면 ret -10000).

조회 조건과 한계:
- 중국 서버 puuid + areaid가 필요. 이름으로 puuid를 찾는 공개 API는 상위 랭커 목록 외에 없음.
- KR 계정(랄라붕#KR1)은 중국 서버에 없어 조회 불가.
- 본인 정보(MobilePlayerInfo, lol.ams.game.qq.com)는 QQ 로그인이 필요해 status -606/-704. / 표본·갱신: - 개인 경기 단위 기록, 최신 경기 end_time은 조회 당일
- 표본은 공개 랭커 1명의 10경기로만 스키마 확인
- 저장한 샘플은 식별자를 가림

**[livegame] 一键应用 (게임 내 阵容助手로 덱 등록) — 실시간 인게임 기능 없음** — https://lol.qq.com/tft/#/index (덱 카드·상세의 应用阵容 버튼, 현재 템플릿에서 주석 처리)

- 보여주는 것: - 로그인한 QQ 계정의 게임 클라이언트 '阵容助手-我的阵容'에 덱을 등록한다(가장 오래된 슬롯 자동 교체)
- 적용 여부 표시
- 라이브 매치 조회·관전·오버레이는 없다
- UX: - 미로그인 시 로그인 팝업
- 성공 시 '游戏内阵容助手-我的阵容 새로고침' 안내 팝업
- 상세 페이지 이동 버튼
- 엔드포인트: `GET https://lol.sw.game.qq.com/lol/lwdcommact/a20240329tftRecommend/a20240329tftRecommend/getLineUp?type=0 (조회) | setLineUp?slineUpId={id}&type=0 (계정에 쓰는 동작이라 호출하지 않음) | GET https://lol.sw.game.qq.com/lol/lwdcommact/a20201106tft/a20201106tftLineup/verify` (미확인)
- 핵심 필드: 코드 기준 getLineUp data[{id}] (var LWDFramework_Swoole 형식).
미로그인 응답:
- getLineUp: code -1 '您还没有登录或登录超时'
- verify: status -1 '未登录'
- 인증: QQ Connect 로그인(Milo, appId 101491592) + AME 동기화 쿠키 + 중국 서버 게임 계정 / 표본·갱신: 해당 없음 (사용자별 쓰기 기능)

**[other] 运营节奏(공략) + 版本(뉴스/운영 배너)** — https://lol.qq.com/tft/#/strategy , https://lol.qq.com/tft/#/news

- 보여주는 것: 공략 영상·글:
- 通用: 强化符文攻略 / 当前版本解读 / 新手教学
- 进阶: 赏金猎人 / 阵容选择 / 武器搭配 / 运营节奏 / 经济 / 摆位
- 제목, 작성자, 조회수, 썸네일

版本:
- 운영 배너(operate.json), 최신 공략 6개, 앨범별 컬렉션, 패치노트 링크
- UX: - 1차·2차 분류 탭, 섹션 스크롤 이동
- 영상 팝업 재생, 조회수 보고
- 엔드포인트: `GET https://apps.game.qq.com/cmc/cross?serviceId=245&source=ztzgw&tagids=122120,122122&typeids=1,2&logic=and&start=0&limit=4 | GET https://apps.game.qq.com/cmc/cross?serviceId=245&limit=6&source=zm&tagids=118531&typeids=1,2 | GET https://game.gtimg.cn/images/lol/act/tftzlkauto/json/operateJson/operate.json | GET https://mlol.qt.qq.com/go/database/versionlist?zone=lol&from=h5` (확인됨)
- 핵심 필드: cmc/cross: {status, data{total, items[{iDocID, iNewsId, sTitle, sAuthor, sIMG, sVID, iTotalPlay, sIdxTime, sCoverMap(JSON 문자열), sTypeName}]}}

versionlist: data[{id, name "16.18", public_date "2026-09-10", title}]
- 인증: 없음. 문서 상세 complexDetail은 md5 서명+timestamp가 필요(코드 확인, 미호출). / 표본·갱신: - 최신 글 2026-09-15 15:36, 전체 9596건
- 패치 목록 최신 16.18 (2026-09-10)

**[other] 资料纵观 / 小小英雄 / 模拟器** — https://lol.qq.com/tft/#/overview , https://lol.qq.com/tft/#/littlehero , https://lol.qq.com/act/a20220802tftsimulator/

- 보여주는 것: - 资料纵观: 시즌 테마 소개, 특질·직업별 챔피언 매트릭스, 증강 단계 설명 팝업 (정적 데이터만)
- 小小英雄: 꼬마전설 2014개(등급·이미지·영상)
- 模拟器: 외부 배치 시뮬레이터 (이번에 조사하지 않음)
- UX: - 모드 전환, 소개 탭
- 꼬마전설 목록·필터
- 模拟器는 새 창 링크
- 엔드포인트: `GET https://game.gtimg.cn/images/lol/tfth5lib/v1/versionconfig.json | GET https://game.gtimg.cn/images/lol/act/img/tft/js/hero.js` (확인됨)
- 핵심 필드: versionconfig.json: []{booleanPreVersion, arrVersionLimit ["16.17"], stringName "自然之力", idSeason "s18", urlChessData, urlRaceData, urlJobData, urlEquipData, urlBuffData(hex.js), urlElfData}. 시즌 파일 경로를 하드코딩하지 말고 여기서 읽어야 한다.

hero.js: {version "16.18", season, time, data[{heroId, name, quality, imagePath, video, contentid, miniId, star}]}
- 인증: 없음 / 표본·갱신: - versionconfig Last-Modified 2026-08-28, s18 경로 '16.17-2026.S18'
- hero.js version 16.18, time 2026-09-09

</details>

### lolchess.gg

- **접근 가능**: 예
- **식별자 체계**: [확인] lolchess는 식별자를 두 층으로 씁니다.

(A) 통계, 매치, 랭킹, 필터 API는 DA_* apiName(API 필드명 ingameKey)을 씁니다.
- CommunityDragon ko_kr.json 의 apiName과 대조 결과:
  - 시즌 18 챔피언: lolchess 160개 중 75개 일치. CommunityDragon의 플레이 가능한 시즌 18 챔피언은 모두 포함됩니다. 나머지 85개는 TFT17_* 잔여와 소환물 등입니다.
  - 특성: 109개 중 36개 일치(시즌 18 특성 36개 전부)
  - 아이템: 272개 중 263개 일치. 불일치는 DA_Artifact_* 3개, *_Legacy 6개입니다.
  - 증강: 986개 중 927개 일치
- 명명 패턴이 균일하지 않습니다.
  - 챔피언: DA_18_Ahri / DA_Amumu18 / DA_Vi18 / DA_18_Akali_AD
  - 특성: DA_18_Fae / DA_Juggernaut18 / DA_Primal18 / DA_Emerald18
  - 아이템: DA_GargoyleStoneplate / DA_18_EmblemFae / 레거시 TFT11_Item_ThiefsGlovesSupport
  - 증강: DA_18_BranchingOut / TFT14_Augment_Controller_Vertical
- 따라서 문자열 규칙으로 추정하지 말고 apiName 집합으로 대조해야 합니다.
- 필드별 사용처
  - 매치 participants: units[].character_id, units[].items[], traits[].name 이 모두 DA_*
  - meta-deck-*: key, keys
  - 장인 랭킹 경로: /ranks/master/champions/DA_18_Ahri. lolchess key "Ahri"로는 null
  - recent-win-matches: q=DA_18_Ahri. "Ahri"로는 0건

(B) 웹 URL과 편집 콘텐츠는 lolchess 자체 key를 씁니다. 접두사를 뗀 PascalCase 영문입니다.
- 챔피언 "Ahri", 아이템 "AccomplicesGloves", 특성 "Adaptor", 증강 "10000IQ"
- 사용처: /champions/set18/Ahri 경로, guide-decks·streamer-decks의 slots[].champion과 items[], team-builder
- /data/champions|items|traits|augments?season=set18&hl=ko 가 key와 ingameKey를 1:1로 대응시키고 한글 name을 줍니다. 한국어 이름을 API가 직접 제공합니다.

(C) 그 밖의 식별자
- 메타 덱: key는 64자리 hex(sha256), deckKey는 "DA_18_Fae-DA_18_Tristana-<32hex>"
- 추천·배치툴 덱: teamBuilderKey는 40자리 hex(sha1)
- 소환사: puuid와 shard(kr), 표시명 gameName·tagLine
- 매치: 숫자 matchId(8382256353)와 shard
- 최근 1위 덱 shard는 플랫폼 ID(br1, eun1, la1, sg2, tw2, vn2 ...)이고, 프로필 URL은 짧은 지역코드(br, lan, vn)를 씁니다.
- 숫자형: queueId(1100 등), patch 정수 1802 + revision, tierId 0~5
- 특성 style: 1~4 숫자. UI 필터가 브론즈/실버/골드·고유/프리즘이라 이 순서로 대응한다고 추정합니다.
- 꼬마전설이: UUID
- **샘플 폴더(당시)**: `C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/bench/lolchess/ (api/ 에 API 응답 65개와 파일별 URL·비고를 담은 api/_index.json, pages/ 에 SSR HTML과 __NEXT_DATA__ JSON, js/ 에 _app.js와 청크 179개, i18n_ko.json, buildManifest.js, probe.py·nd.py 재사용 스크립트)`

**기술 메모**

[직접 확인한 사실] 조사 시각은 2026-09-15 11:49~12:20 UTC(20:49~21:20 KST)입니다.

1) 프레임워크
- Next.js Pages Router입니다. buildId는 NCv2C6skfVd4bcmeejFMO이고, 정적 자산은 https://cdn.dak.gg/tft-web/1789451654/_next/ 에서 받습니다.
- 페이지는 getServerSideProps(gssp:true)로 렌더링되고, HTML 안 __NEXT_DATA__ 의 pageProps.dehydratedState(react-query)에 SSR 데이터가 들어 있습니다.
  - 예: /decks 는 queryKey ["meta-decks",{apiEnvironment:"live",dt:3,tierId:1,locale:"ko"}]
  - 예: 전적 페이지는 ["profile","kr","랄라붕-KR1","set18"]
- /_next/data/NCv2C6skfVd4bcmeejFMO/decks.json 과 /_next/data/NCv2C6skfVd4bcmeejFMO/profile/kr/%EB%9E%84%EB%9D%BC%EB%B6%95-KR1/set18.json 도 HTTP 200 JSON을 반환했습니다. 다만 buildId가 배포마다 바뀝니다.

2) 데이터 로딩
- 클라이언트는 axios.create({baseURL:"https://tft.dakgg.io/api/v1", withCredentials:true})로 직접 호출합니다. stage 환경은 stage-tft.dakgg.io이고, 운영 환경에는 추가 params가 없습니다.
- 챔피언 스탯표, 3신기, 리더보드 목록, 최근 1위 덱, 배치툴은 SSR 데이터 없이 클라이언트에서만 불러옵니다.
- 번들의 32자리 hex(a1cee0afdfa2fc78f39207c024960c3b)는 Kakao.init() 공유 SDK 키입니다. API 인증 키가 아닙니다.
- UI 문자열(ko/en/ja/vi/de/zh)은 _app.js 모듈 99485에 내장돼 있습니다. i18n_ko.json 으로 추출해 두었습니다.

3) 차단 여부
- nginx + CloudFront(ICN) 구성이고 Cloudflare는 아닙니다.
- 브라우저 UA 없이 python-urllib UA로 호출해도 200이었습니다. 인증 헤더, 서명, 쿠키 모두 필요 없습니다.
- 개인 데이터 엔드포인트(no-store)에 연속 15회 요청해도 전부 200이었습니다(0.7초).
- 응답 헤더
  - Access-Control-Allow-Origin: *
  - 통계: Cache-Control public, max-age=60
  - 관전 목록: max-age=30
  - 소환사/매치: no-store
- robots.txt는 /search 만 Disallow이고, tft.dakgg.io/robots.txt 는 404입니다.

4) 전적 URL 형식
- https://lolchess.gg/profile/kr/랄라붕-KR1 (인코딩 형태: /profile/kr/%EB%9E%84%EB%9D%BC%EB%B6%95-KR1)은 307로 /profile/kr/랄라붕-KR1/set18 에 연결되고, 200을 반환합니다(title "랄라붕#KR1 - 시즌 18 전적 종합").
- 형식은 /profile/{shard}/{gameName}-{tagLine}/{season}/[[...tab]] 입니다. 태그 구분자는 '#'이 아니라 '-'입니다.
- 전적 탭은 시즌 종합 / 매치 히스토리 / LP 변화 추이 / 통계(i18n에는 전적 캘린더도 있음)입니다.
- 다른 지역은 /profile/br/<플레이어>-BR4, /profile/lan/..., /profile/vn/... 처럼 짧은 지역코드를 씁니다(홈 링크에서 관찰). 이 경우 API shard 형식은 확인하지 않았습니다.
- 검색은 /search?name= 이고, API /summoners-by-riotid/{이름} 또는 {이름-태그} 로 동작합니다. '#' 형식은 0건이었습니다.

5) 메뉴 전체(GNB 설정과 i18n에서 추출)
- 추천 메타: /meta 추천 메타, /decks 메타 통계, /recent-win-decks 최근 1위 덱, /meta/youtuber
- 순위표
  - /leaderboards 랭크 순위, /leaderboards?mode=doubleup 더블 업
  - /favorites 즐겨찾기
  - /spectate 천상계 관전
  - /leaderboards/champions/set18 챔피언 장인, /leaderboards/traits/set18 시너지 장인
- 시즌 18: 신비의 숲
  - /tft/18 시즌 개요
  - /champions/set18 챔피언, /synergies/set18 시너지, /items/set18 아이템, /augments/set18 증강체
  - /rewards/set18 수호령/보상표
- 오버레이: https://desktop.dak.gg/ 가 dak.gg/desktop 으로 연결되는 외부 데스크톱 앱입니다(Windows/macOS).
- 배치툴/도구: /builder, /my-builder, /set-report 시즌 성적표, /simulator
- 게임 가이드: /guide/patch-notes, /guide/pbe-patch-note, /guide/role, rounds, exp, reroll, damage, hotkeys, /tools/little-legends
- 게시판/듀오찾기: /board/tft/list, /board/tip/list, /board/promotion/list
- 없는 경로: /tierlist, /traits, /live, /ingame, /leaderboard 는 404입니다. /meta/items 등은 307로 /items/set18 등에 연결됩니다.

6) 번들에 내장된 로직
- tierId 매핑(9714 청크): all=0, master_over=1, diamond_over=2, platinum_over=3, gold_less=4, emerald_over=5
- 큐 ID: 1100 랭크, 1130 초고속, 1160 더블 업, 1220 톡톡이의 시험, 6000·6110 부활 시즌
- 팀 코드: 클라이언트에서 생성합니다(1469 청크).
  - 형식: "02" + 챔피언 10칸(칸당 3자리 hex, 빈칸 "000") + "TFTSet18"
  - DA_*와 hex 대응표 66개가 번들에 내장돼 있습니다(예 3e9=DA_18_Ahri). 럭스는 DA_Lux18_Base로 정규화합니다.
  - 추출본: _bundle_teamcode_map_set18.json
- 증강 등장 확률표(2-1/3-2/4-2)도 API가 아니라 번들 JSON입니다.

7) 비동기 RPC 패턴
- /rpc/summoner-sync, /rpc/spectator, /rpc/set-report 는 첫 응답이 {"retry_after":1000} 또는 {"retryAfter":1000}입니다.
- 약 1~1.5초 뒤 같은 URL을 다시 부르면 결과가 옵니다.
- 전적 갱신을 실행하자 syncedAt이 2026-09-14T11:34:10Z에서 2026-09-15T11:57:08Z로 바뀐 것을 확인했습니다.

8) 브라우저로 본 렌더링
- in-app 브라우저로 lolchess.gg가 열렸습니다. 스크린샷은 렌더링 대기 시간 초과가 잦아 document.body.innerText로 UI를 확인했습니다.

**이 사이트만의 강점**

- 한국어가 기본인 API입니다. hl=ko 한 번이면 챔피언·특성·아이템·증강 이름과 효과 설명, 덱 이름(deckNameKo), 패치노트가 모두 한국어로 옵니다. 통계 API는 lol.qq·metatft와 같은 DA_* 키를 쓰기 때문에 기존 수집기와 ID 집합으로 바로 합칠 수 있습니다.
- 티어 필터 6단계(tierId 0~5)마다 표본 경기 수(matchCount)를 명시하고, 패치 리비전(18.2 / 18.1d)을 골라 조회할 수 있습니다. 챔피언·아이템·특성 통계를 모두 글로벌 랭크 기준으로 제공합니다.
- 아이템→챔피언 역방향 통계(itemChampionStats, 아이템 상세 API)와 챔피언별 3아이템 조합 통계(championItemPairStats 1,347개), 성급별 통계를 API가 직접 줍니다. 앱의 아이템 역검색 요구와 그대로 맞습니다.
- 메타 덱마다 캐리 순위(coreRank 1~4)와 유닛별 추천 아이템 3개, 8칸 등수 분포를 줍니다. 편집자 추천 덱은 레벨별 보드(초반/중반/최종, Lv5~10)까지 있고 유튜버 덱 555개가 별도로 있습니다.
- KR 전적: 지표별 상위 백분위와 순위(모집단 608,073명), LP 변화 로그, 최근 20게임·시즌 챔피언/특성/아이템 통계, 지난 시즌 성적표를 줍니다. 로그인 없이 GET 한 번으로 전적 갱신 동기화를 실행할 수 있습니다.
- 배치툴의 헥스 칸별 배치 빈도 통계(team-builder-cell-stats, 누적 3,510만 유닛)로 추천 배치 위치를 만들 수 있습니다.
- 챔피언·시너지 장인 랭킹(숙련도 점수, 3성작 수, 사용 아이템)과 챌린저·GM의 최근 1위 보드 피드를 제공합니다. 1위 보드 피드는 DA_* 챔피언 필터와 팀 코드 복사를 지원하고 약 5~10분마다 갱신됩니다.
- 챔피언 일별 시계열(meta-champion-daily-stats)과 캐리 점수 캔들 데이터(carry-scores)가 있어 추세 차트를 그릴 수 있습니다.
- 팀 플래너 코드 생성 로직과 시즌 18 DA_*↔hex 대응표(66개)가 번들에 공개돼 있어 우리 덱 코드 구현을 교차 검증할 수 있습니다.
- KR 리더보드 상위 2,000명, 더블업·톡톡이의 시험 큐, 티어 분포도를 제공합니다. 운영사 dak.gg는 데스크톱 인게임 오버레이 앱(DAK.GG Desktop)도 따로 운영합니다. 다만 lol.qq·metatft와의 비교는 이번 조사 범위 밖이라, 두 사이트에 같은 기능이 없는지는 각 사이트 조사 결과와 대조가 필요합니다.

**수집 위험** — [확인된 위험]
1) 약관: 이용약관에 다음 조항이 있습니다.
- "본 서비스를 통해 얻은 정보를 회사의 사전 승낙 없이 서비스 이용 외의 목적으로 복제하거나, 이를 출판 및 방송 등에 사용하거나, 제 3자에게 제공하는 행위"를 금지합니다.
- 공개 GitHub 저장소와 Actions로 수집해 앱에 재배포하면 약관 위반 소지가 큽니다. 운영사(PlayXP/dak.gg)의 사전 허락이나 개인용 한정 사용 검토가 필요합니다.
- robots.txt는 /search 만 막고, API 호스트는 robots가 없습니다(404).

2) 비공개 API: 현재 인증·서명·레이트리밋 징후가 없습니다.
- 비브라우저 UA로도 200, 연속 15회도 200이었고 CORS는 *입니다.
- 다만 문서화되지 않은 내부 API라 언제든 토큰·서명·차단이 추가될 수 있습니다. 대량 수집 시 한도는 확인하지 않았습니다.
- 전적 갱신 RPC는 서버가 Riot API를 호출하게 만들므로 남용하면 IP 차단 가능성이 높습니다(추정).

3) 조용한 실패: 필수 파라미터가 빠져도 에러 대신 HTTP 200 빈 응답이 옵니다. 응답이 비었는지 반드시 검증해야 합니다.
- meta-decks: dt=3 누락, tierId≠1, shard=kr, queueId=1160 이면 metaDeckList가 비어 있습니다.
- /ranks/master/champions/Ahri(lolchess key)와 /meta-decks/{key} 상세는 null입니다.
- meta-deck-augments는 patchRevisions:[] 입니다.

4) 스키마·경로 변동
- buildId와 청크 해시가 배포마다 바뀌어 /_next/data/{buildId} 경로와 번들 파싱이 깨집니다. API 경로(/api/v1)가 더 안정적입니다.
- 키 명명이 섞여 있습니다: DA_18_X / DA_X18 / TFT11_Item_*. 엔드포인트마다 lolchess key와 DA_*를 섞어 씁니다(guide-decks slots는 lolchess key). /data/* 대응표를 매 패치 다시 받아야 합니다.
- avgPlacement가 문자열("4.29")이고 필드명 불일치(championItemPairStats가 실제로는 3아이템)가 있어 파서가 취약해질 수 있습니다.
- meta-deck-exalted는 set11 데이터가 방치돼 있고 i18n에 시즌별 흔적이 남아 있어 레거시 엔드포인트가 섞여 있습니다.

5) 데이터 한계
- 메타 덱은 글로벌 마스터+만 있고 KR 단독이나 다른 티어는 없습니다.
- 시즌 18 증강 통계는 없습니다(매치 데이터에 증강 없음).
- 관전 목록은 조사 시점 15개 샤드 모두 0건이었고 화면에 Riot API 이슈 문구가 있었습니다. 인게임 응답 구조는 확인하지 못했습니다.
- 매치 eog와 details는 404입니다(데스크톱 앱 수집분만 있는 것으로 추정).
- 덱 목록의 기간이 UI상 '최근 2일'(dt=3)이고 통계 페이지는 '패치'로 표기돼, 두 데이터의 기간이 같은지 불명확합니다(추정).

6) 크기·비용
- 응답이 큽니다: team-builder-refs 729KB, ranks 약 560KB, meta-decks 514KB, data/augments 383KB.
- 모바일에서 직접 호출하기보다 기존 collector처럼 서버에서 모아 필요한 필드만 가공해야 합니다.
- 통계는 CloudFront max-age=60 캐시, 개인 데이터는 no-store입니다.

7) 폴링 패턴: RPC 계열은 retryAfter/retry_after(키 이름이 서로 다름)를 처리하는 재시도 로직이 필요합니다.

#### 기능 18개

| 카테고리 | 메뉴 | 보여주는 것 | 엔드포인트 | 확인 | 인증 | 표본·갱신 |
|---|---|---|---|:---:|---|---|
| deck | 메타 통계 (추천 메타 > 메타 통계) | 덱 카드 22개: S/A/B/C 등급, 한글 덱 이름(예 '[상징] 요정 트리스타나', '고밸류 드레이븐 이즈리얼'), 챔피언 9~10명 아이콘, 평균 등수 #3.95, 픽률 0.10, 승률 22.9%, TOP4 59.2%. API에는 8칸 등수 분포(placements), 챔피언별 c… | `https://tft.dakgg.io/api/v1/meta-decks?shard=global&queueId=1100&dt=3&tierId=1&hl=ko&from=web` | ✓ | 없음 (헤더·쿠키·서명 불필요, 비브라우저 UA도 20… | 글로벌(전 서버 합산) 마스터+ 랭크(1100), 패치 18.2. UI 라벨은 '최근 2일'이고 덱 plays 합계는 61,679입니다. updatedAt이 11… |
| deck | 추천 메타 (편집자 추천 덱) | 편집자 큐레이션 덱 30개: 덱 이름(예 '유물별 챔피언 요약', '고밸류 드레이븐'), HOT 태그, 챔피언 목록, 총 코스트(75, 120 등), '팀 코드 복사' 버튼, '공략 더 보기'(/builder/guide/{teamBuilderKey}, 레벨별 배치 보드). | `https://tft.dakgg.io/api/v1/guide-decks?q=live&hl=ko&newSet=1` | ✓ | 없음 | 통계가 아닌 편집 콘텐츠이고 패치 버전 단위로 관리됩니다. 갱신 시각 필드가 없어 주기는 확인하지 못했습니다. SSR(queryKey getGuideDecks)에… |
| deck | 유튜버 가이드 덱 | 스트리머(구루루, 정동글 등) 프로필과 유튜브 영상 썸네일·링크, 영상에 나온 덱 보드(슬롯·성급·아이템). | `https://tft.dakgg.io/api/v1/streamer-decks?hl=ko&page=1` | ✓ | 없음 | 편집 콘텐츠이며 누적 555개입니다. 갱신 주기는 확인하지 못했습니다. |
| deck | 최근 1위 덱 | 챌린저·그랜드마스터가 최근 1위를 한 보드: 지역(EUW/KR/VN..), n분 전, 레벨, 티어 뱃지(C/GM), 소환사명#태그, 유닛·아이템 보드, '팀 코드 복사'. | `https://tft.dakgg.io/api/v1/recent-win-matches?q=DA_18_Ahri` | ✓ | 없음 | 챌린저/GM 1위 매치 75건(15개 샤드). updatedAt이 11:54:43에서 12:02:46 UTC로 바뀌어 약 5~10분 주기로 추정합니다. max-a… |
| champion | 챔피언 통계 (시즌 18 > 챔피언) | 표: # / 챔피언 / 평균 등수(S 등급 뱃지 + 수치) / 픽률('0.93 / 8' 게임당 등장 수) / TOP4 / 승률 / 게임 수 / 추천 아이템. 좌측에 코스트별 챔피언 아이콘 목록. 탭: 챔피언 통계 / 챔피언 스탯표 / 3신기 한눈에. | `https://tft.dakgg.io/api/v1/meta-deck-champions?tierId=3&patch=1802&revision=0&queueId=1100&from=web` | ✓ | 없음 | 글로벌 랭크, 패치 18.2 경기 수: 전체 399,629 / 플래티넘+ 315,088 / 에메랄드+ 214,264 / 다이아+ 102,566 / 골드 이하 84… |
| champion | 챔피언 상세 (예: 아리) | 비용 4, 특성(개화·주문술사), 역할군, 체력·공격력·DPS(1/2/3성), 사거리, 공속, 방어력, 마저, 스킬(마나 20/100, 설명, 성급별 피해량). 추천 아이템 탭: 3신기 / 아이템 / 유물 / 찬템 / 상징. 표: 아이템 조합 / 평균 등수 / 픽률 / TOP4 / 승률… | `https://tft.dakgg.io/api/v1/meta-deck-champions/DA_18_Ahri?tierId=3&patch=1802&revision=0&queueId=1100&from=web` | ✓ | 없음 | 플래티넘+ 315,088경기(18.2 글로벌 랭크). updatedAt 11:25:43 UTC로 목록 통계와 같은 배치입니다. 증강 통계는 빈 배열입니다. |
| champion | 챔피언 스탯표 / 챔피언 트렌드(API) | 스탯표: 비용 / 역할군 / 체력 / 공격력 / DPS / 사거리 / 공속 / 방어력 / 스킬 / 마나. 트렌드 API는 챔피언별 일자별 플레이·승·TOP4·픽률·평균 등수와 캐리 점수 캔들 데이터를 줍니다. 단 i18n의 '챔피언 트렌드' 탭은 시즌 18 화면에 노출되지 않았습니다. | `https://tft.dakgg.io/api/v1/meta-champion-daily-stats?tierId=1&from=web` | ✓ | 없음 | 시즌 시작 2026-08-26부터 일별 시계열(마스터+ 글로벌). updatedAt 11:30에서 12:00 UTC로 바뀌어 30분 주기입니다. 사전 데이터(/d… |
| trait | 시너지 통계 (시즌 18 > 시너지) | 표: # / 시너지(활성 인원수+이름, 예 '11 나무정령', '5 사냥꾼') / 평균 등수 / TOP4 / 승률 / 픽률 / 게임 수 / 챔피언 TOP3. 탭: 시너지 통계 / 시너지 가이드 / 시너지 표. 장인 랭킹은 별도 항목에 정리했습니다. | `https://tft.dakgg.io/api/v1/meta-deck-traits?tierId=1&from=web` | ✓ | 없음 | 챔피언 통계와 같은 배치입니다(글로벌 랭크, 18.2, tierId별 matchCount 동일, updatedAt 11:25:41 UTC). |
| item | 아이템 통계 / 조합표 / 상세 (시즌 18 > 아이템) | 표: # / 아이템 / 평균 등수 / TOP4 / 승률 / 픽률 / 챔피언 TOP5(예 전략가의 방패 #3.52 67.19% 23.96% 0.03%, 아이번·아무무…). 탭: 아이템 통계 / 아이템 조합표(재료 격자, 그림자 아이템, 설명 함께 보기) / 아이템 가이드 / 3신기 한눈에… | `https://tft.dakgg.io/api/v1/meta-deck-items?tierId=1&from=web` | ✓ | 없음 | 글로벌 랭크 18.2, tierId별 matchCount는 챔피언 통계와 같습니다. updatedAt 11:25:41 UTC(12:03에도 불변). |
| augment | 증강체 가이드 / 티어 / 확률 / 배제 목록 | 증강 가이드: 실버/골드/프리즘별 이름과 효과 전문. 증강 티어: S/A/B(편집). 증강 확률: 2-1/3-2/4-2 라운드별 실버·골드·프리즘 등장 확률. 배제 목록. 시즌 18에는 '증강체 통계' 탭이 노출되지 않습니다. | `https://tft.dakgg.io/api/v1/data/augment-tiers?season=set18` | ✓ | 없음 | 통계 표본은 없습니다. 매치 participants.augments가 [] 이고 메타 덱 augments도 null이라 시즌 18은 매치 데이터에 증강이 없습니다… |
| tierlist | 티어표 (별도 메뉴 없음, 페이지 내 등급) | 독립 티어표 메뉴는 없습니다(/tierlist 404). 등급은 네 곳에 흩어져 있습니다. (1) 메타 통계 덱 S/A/B/C (2) 챔피언 통계 평균 등수 옆 S 뱃지 (3) 증강체 티어 S/A/B (4) 시너지·아이템 표의 평균 등수 순위. | `https://tft.dakgg.io/api/v1/data/augment-tiers?season=set18` | ✓ | 없음 | 증강 티어는 편집 데이터(2026-09-14 05:40 UTC)입니다. 덱과 챔피언 등급은 해당 통계 표본을 따릅니다. |
| profile | 전적 검색 (프로필: 시즌 종합 / 매치 히스토리 / LP 변화 추이 /… | 레벨, 아이콘, 랄라붕#KR1, '전적 갱신' 버튼, '시즌 17 성적표', 닥지지 카드, 최근 업데이트 n분 전. 랭크: Diamond IV 0LP와 상위 백분위·순위(티어 상위 1.029% 6,353위, 승리 14 상위 2.994%, 승률 12.5%, Top4 64, Top4 비율… | `https://tft.dakgg.io/api/v1/summoners/kr/%EB%9E%84%EB%9D%BC%EB%B6%95-KR1/profile?season=set18` | ✓ | 없음. 전적 갱신 RPC도 비로그인 GET으로 동작했고… | 개인 데이터라 Cache-Control no-store입니다. 데이터는 사용자가 갱신을 눌렀을 때 Riot API에서 동기화되며, 이전 syncedAt은 약 하루… |
| profile | 시즌 성적표 (Set Report) | 지난 시즌(시즌 17) 개인 성적 리포트. 프로필 헤더의 '시즌 17 성적표' 버튼과 /set-report/{shard}/{name} 에서 봅니다. | `https://tft.dakgg.io/api/v1/rpc/set-report/kr/%EB%9E%84%EB%9D%BC%EB%B6%95-KR1?season=set17&hl=ko&phase=prod` | ✓ | 없음 | 개인 시즌 전체 기록이며 no-store입니다. 생성 시점과 주기는 확인하지 못했습니다. |
| other | 순위표 (랭크 / 더블 업 순위, 티어 분포도) | 표: 순위 / 소환사#태그 / 티어(C) / LP / 승 / TOP4(예 #1 <플레이어>#KR1 1730 LP 88 230). 챌린저 컷 500 LP, GM 컷 200 LP, 티어 분포도 차트, '최근 업데이트: 8분 전'. | `https://tft.dakgg.io/api/v1/leaderboards/summoners/kr?hl=ko&season=set18&tier=CHALLENGER&queueId=1100&page=1` | ✓ | 없음 | KR 랭크: 챌린저 161명, tier=ALL 총 2,000명(상위권만 수록). 글로벌 챌린저 905명, KR 더블업 챌린저 2명. updatedAt이 11:47… |
| other | 장인 랭킹 (챔피언 장인 / 시너지 장인) | 표: 순위 / 지역 / 소환사 / 티어·LP / 숙련도(점수) / 승률 / TOP4 / 게임 수 / 3성작 수 / 사용 아이템(예 tantienTFT 97 LP 1,721점 19.6% 59.8% 214판 3성 112회). | `https://tft.dakgg.io/api/v1/ranks/master/champions/DA_18_Ahri?hl=ko&shard=global&queueId=1100` | ✓ | 없음 | 글로벌 전 서버 상위 유저. updatedAt 11:52 UTC(화면 표시 18분 전). 정확한 주기는 확인하지 못했습니다. |
| livegame | 천상계 관전 + 프로필 라이브게임 조회 (+ 외부 오버레이 앱) | 관전 가능한 상위 티어 게임 목록: 게임모드 / 소환사 / 티어 / LP / 관전하기(Windows 배치 실행 방식 안내). 조사 시점 화면 문구는 '현재 관전 가능한 게임이 없습니다. 라이엇 API 이슈로 관전 데이터를 받을 수 없는 상태일 수 있습니다.' 였습니다. 마스터 이하는 게… | `https://tft.dakgg.io/api/v1/rpc/spectator/kr/<puuid>` | ✓ | 없음 | spectate-matches는 max-age=30이고 updatedAt이 1~2분마다 바뀝니다. 조사 시각(21시 KST)에 15개 샤드 모두 0건이었고, 화면… |
| other | 배치툴 (팀 빌더) / 내 배치툴 / 팀 코드 | 헥스 보드, 챔피언(이름순/가격순/계열별/직업별), 아이템, 증강체 선택, 아이템 조합표, 레벨별 빌드 탭(초반/중반/최종 덱, Lv.5~10, 상징), 팀 코드 붙여넣기/복사, 내 배치툴 저장, 공략 작성, 공유하기. | `https://tft.dakgg.io/api/v1/team-builder-cell-stats?hl=ko&season=set18` | ✓ | refs와 cell-stats는 없음. 내 배치툴 저장… | 누적 3,510만 유닛 배치(시즌 전체로 추정). updatedAt 09:39 UTC가 12:03에도 그대로여서 수 시간 이상 주기로 추정합니다. |
| other | 패치노트 / 게임 가이드 / 보상표 / 기타 도구 | 한국어 패치노트(18.2b 등, '임시 번역' 표기 포함), PBE 패치노트, 역할군, 라운드, 골드/경험치, 리롤 확률, 피해량 공식, 단축키, 꼬마 전설이, 수호령/보상표(/rewards/set18/wisps), 시즌 개요(/tft/18), 시뮬레이터, 즐겨찾기, 게시판/듀오찾기. | `https://tft.dakgg.io/api/v1/patch-notes?hl=ko` | ✓ | 없음 | 패치노트는 패치 공개일 기준 등록(18.2b registeredAt 2026-09-15). 게시판 max-age=5, 나머지 max-age=60. |

<details><summary>기능별 UX·응답 필드 전문</summary>

**[deck] 메타 통계 (추천 메타 > 메타 통계)** — https://lolchess.gg/decks

- 보여주는 것: 덱 카드 22개: S/A/B/C 등급, 한글 덱 이름(예 '[상징] 요정 트리스타나', '고밸류 드레이븐 이즈리얼'), 챔피언 9~10명 아이콘, 평균 등수 #3.95, 픽률 0.10, 승률 22.9%, TOP4 59.2%. API에는 8칸 등수 분포(placements), 챔피언별 coreRank(1~4 캐리 순위, 99 기타)와 추천 아이템 3개, 활성 특성(style/numUnits), championStats도 들어 있습니다.
- UX: 기간 드롭다운 '최근 2일 (18.2)'(patchRevisions로 18.2 / 18.1d 선택), 정렬 드롭다운(평균 등수/승률/픽률/TOP 4), 티어 드롭다운은 코드상 '마스터+' 하나만 노출, 챔피언/시너지/아이템 필터, 카드 리스트, '최종 업데이트: N분 전'. S/A/B/C 등급은 API에 필드가 없어 클라이언트에서 계산하는 것으로 추정합니다. 덱 상세 라우트(/decks/[...tab], 매치 기록·챔피언·아이템 탭)는 번들에 있으나 /decks/{key} 가 307로 /decks 에 연결돼 현재 동작하지 않습니다. 덱 이름 클릭 시 변화도 없었습니다.
- 엔드포인트: `https://tft.dakgg.io/api/v1/meta-decks?shard=global&queueId=1100&dt=3&tierId=1&hl=ko&from=web` (확인됨)
- 핵심 필드: patchRevisions[{patch:1802,revision:0,patchVersion:'18.2'},{1801,3,'18.1d'}], season, metaDeckList{updatedAt, shard:'global', queueId, dt, patch, revision, tierId, plays:61679, metaDecks[{key(64hex), deckKey('DA_18_Fae-DA_18_Tristana-<32hex>'), plays, pickRate, winRate, topRate, avgPlacement, placements[8], deck{champions[{key:'DA_18_Tristana', coreRank, items['DA_GiantSlayer',..]}], traits[{key, style, numUnits}], augments[null,null,null]}, championStats, deckNameKo, deckNameEn, deckChampionKey}]}, refs{champions 160, traits 109, items 272(한글명·이미지·스킬), metaDeckNames/TeamBuilders/HideKeys 빈 배열}. 주의: dt=3 이 없거나 tierId≠1, shard=kr, queueId=1160 이면 HTTP 200인데 metaDeckList가 비어 있습니다. patch=1801&revision=3 을 붙이면 이전 패치 덱 21개가 옵니다. 덱 상세 /meta-decks/{key 또는 deckKey}?... 는 200이지만 metaDeck:null 이었습니다.
- 인증: 없음 (헤더·쿠키·서명 불필요, 비브라우저 UA도 200) / 표본·갱신: 글로벌(전 서버 합산) 마스터+ 랭크(1100), 패치 18.2. UI 라벨은 '최근 2일'이고 덱 plays 합계는 61,679입니다. updatedAt이 11:30:35에서 12:00:36 UTC로 바뀌어 30분 주기를 확인했습니다. CloudFront max-age=60. 같은 데이터가 SSR __NEXT_DATA__ 와 /_next/data/{buildId}/decks.json 에도 있습니다.

**[deck] 추천 메타 (편집자 추천 덱)** — https://lolchess.gg/meta

- 보여주는 것: 편집자 큐레이션 덱 30개: 덱 이름(예 '유물별 챔피언 요약', '고밸류 드레이븐'), HOT 태그, 챔피언 목록, 총 코스트(75, 120 등), '팀 코드 복사' 버튼, '공략 더 보기'(/builder/guide/{teamBuilderKey}, 레벨별 배치 보드).
- UX: 버전 탭(v18.2 (시즌 18) / v17.9 (시즌 17)), 카드 목록, 인게임 팀 플래너 코드 복사(클라이언트 생성), 공략 페이지는 초반/중반/최종·Lv5~10·상징 탭이 있는 배치툴 형식.
- 엔드포인트: `https://tft.dakgg.io/api/v1/guide-decks?q=live&hl=ko&newSet=1` (확인됨)
- 핵심 필드: meta{q}, guides[{key:'live'|'meta-app'|'new-set', name:'v18.2 (시즌 18)'}], guideDecks[{teamBuilderKey(40hex), name, tag('hot'), cost, data{slots[{index(헥스 칸 번호), champion(lolchess key 'Morgana'), star, items(lolchess key ['BlightingJewel',..])}]}}]. 주의: 여기 slots는 DA_* 가 아니라 lolchess 자체 key라서 /data/* 로 ingameKey 변환이 필요합니다.
- 인증: 없음 / 표본·갱신: 통계가 아닌 편집 콘텐츠이고 패치 버전 단위로 관리됩니다. 갱신 시각 필드가 없어 주기는 확인하지 못했습니다. SSR(queryKey getGuideDecks)에도 들어 있습니다.

**[deck] 유튜버 가이드 덱** — https://lolchess.gg/meta/youtuber

- 보여주는 것: 스트리머(구루루, 정동글 등) 프로필과 유튜브 영상 썸네일·링크, 영상에 나온 덱 보드(슬롯·성급·아이템).
- UX: 페이지네이션(page), 가이드 버전 탭, 유튜브 외부 링크.
- 엔드포인트: `https://tft.dakgg.io/api/v1/streamer-decks?hl=ko&page=1` (확인됨)
- 핵심 필드: meta{page, totalCount:555}, guides[...], streamers[{id, key, name, linkUrl, thumbnailUrl}], streamerDecks[{streamerId, teamBuilderKey, name, linkUrl(youtube), thumbnailUrl, data{slots[{index, champion(lolchess key), star, items}]}}]
- 인증: 없음 / 표본·갱신: 편집 콘텐츠이며 누적 555개입니다. 갱신 주기는 확인하지 못했습니다.

**[deck] 최근 1위 덱** — https://lolchess.gg/recent-win-decks

- 보여주는 것: 챌린저·그랜드마스터가 최근 1위를 한 보드: 지역(EUW/KR/VN..), n분 전, 레벨, 티어 뱃지(C/GM), 소환사명#태그, 유닛·아이템 보드, '팀 코드 복사'.
- UX: 챔피언·시너지 필터와 초기화, 지역 탭(글로벌+15개 서버), 페이지 1~5, '최종 업데이트: 1분 전', 안내문 '시즌 초반에는 서버별 상위 500명의 기록'.
- 엔드포인트: `https://tft.dakgg.io/api/v1/recent-win-matches?q=DA_18_Ahri` (확인됨)
- 핵심 필드: meta{updatedAt}, matches[{season, shard('kr','euw1','sg2'..), matchId, gameCreatedAt, gameLength, gameVersion, patchVersion, queueId, participants[8명 {placement, level, lastRound, goldLeft, puuid, companionId, timeEliminated, totalDamageToPlayers, traits[{name:'DA_18_Fae', num_units, style, tier_current, tier_total}], units[{character_id:'DA_18_Sivir', rarity, tier(성급), items['DA_InfinityEdge',..]}], augments[]}]}], summoners[{puuid, gameName, tagLine,..}], summonerLeagues[{puuid, queueId, tier, rank}]. 파라미터: q는 DA_* 키를 쉼표로 이은 값(q=DA_18_Ahri는 69건이며 전부 1위 보드에 아리가 있음, q=Ahri는 0건), shard=kr 면 KR만 조회.
- 인증: 없음 / 표본·갱신: 챌린저/GM 1위 매치 75건(15개 샤드). updatedAt이 11:54:43에서 12:02:46 UTC로 바뀌어 약 5~10분 주기로 추정합니다. max-age=60, 프론트 staleTime은 3초입니다.

**[champion] 챔피언 통계 (시즌 18 > 챔피언)** — https://lolchess.gg/champions/set18

- 보여주는 것: 표: # / 챔피언 / 평균 등수(S 등급 뱃지 + 수치) / 픽률('0.93 / 8' 게임당 등장 수) / TOP4 / 승률 / 게임 수 / 추천 아이템. 좌측에 코스트별 챔피언 아이콘 목록. 탭: 챔피언 통계 / 챔피언 스탯표 / 3신기 한눈에.
- UX: 티어 드롭다운(마스터+/다이아+/에메랄드+/플래티넘+/골드 이하/전체, 화면 기본값 플래티넘+), 패치 드롭다운('18.2 패치', revision 선택), 코스트순/가나다순, 챔피언 검색, 표 열 정렬, '최종 업데이트: 43분 전'.
- 엔드포인트: `https://tft.dakgg.io/api/v1/meta-deck-champions?tierId=3&patch=1802&revision=0&queueId=1100&from=web` (확인됨)
- 핵심 필드: patchRevisions, season, metaDeckChampion{updatedAt, shard:'global', queueId, dt:3, patch, revision, tierId, plays, matchCount, metaDeckChampionStats[{key:'DA_Amumu18', plays, wins, tops, placements(등수 합), pickRate, avgPlacement(문자열 '4.29'), championTierStats[{key:1~3 성급, plays, wins, tops, placements, pickRate, avgPlacement}], championItemStats[{key:'DA_GargoyleStoneplate', ...}]}]}. tierId 매핑(번들 코드): all=0, master_over=1, diamond_over=2, platinum_over=3, gold_less=4, emerald_over=5. tierId 6·7은 null입니다.
- 인증: 없음 / 표본·갱신: 글로벌 랭크, 패치 18.2 경기 수: 전체 399,629 / 플래티넘+ 315,088 / 에메랄드+ 214,264 / 다이아+ 102,566 / 골드 이하 84,541 / 마스터+ 32,994(각 matchCount, plays는 8배). 마스터+ 65명, 플래티넘+ 69명. updatedAt 11:25:41 UTC가 12:03에도 그대로여서 30분보다 긴 주기이며 정확한 주기는 확인하지 못했습니다. max-age=60.

**[champion] 챔피언 상세 (예: 아리)** — https://lolchess.gg/champions/set18/Ahri

- 보여주는 것: 비용 4, 특성(개화·주문술사), 역할군, 체력·공격력·DPS(1/2/3성), 사거리, 공속, 방어력, 마저, 스킬(마나 20/100, 설명, 성급별 피해량). 추천 아이템 탭: 3신기 / 아이템 / 유물 / 찬템 / 상징. 표: 아이템 조합 / 평균 등수 / 픽률 / TOP4 / 승률 / 게임 수(예 #4.18, 14.39%, 56.7%, 11.8%, 51,262).
- UX: 좌측 챔피언 목록으로 전환, 티어·패치 드롭다운(플래티넘+, 18.2 패치), 아이템 종류 탭. URL 경로에는 lolchess key(Ahri), API에는 DA_18_Ahri를 씁니다.
- 엔드포인트: `https://tft.dakgg.io/api/v1/meta-deck-champions/DA_18_Ahri?tierId=3&patch=1802&revision=0&queueId=1100&from=web` (확인됨)
- 핵심 필드: metaDeckChampionDetail{updatedAt, shard, queueId, dt, patch, revision, tierId, plays, matchCount, metaDeckChampionStat{key:'DA_18_Ahri', plays, wins, tops, placements, pickRate, avgPlacement, championTierStats[3], championItemStats[117 {key:DA_*, plays, wins, tops, placements, pickRate, avgPlacement}], championItemPairStats[1347 {keys:[아이템 3개 DA_*], ...}], championAugmentStats[]}}. 기본 정보는 /api/v1/data/champions?season=set18&hl=ko (key 'Ahri', ingameKey 'DA_18_Ahri', name '아리', traits(lolchess key), cost[3], health[3], attackDamage[3], damagePerSecond[3], attackRange, attackSpeed, armor, magicalResistance, skill{name, desc, imageUrl}, imageUrl) 에서 옵니다. 이것도 200 확인했습니다.
- 인증: 없음 / 표본·갱신: 플래티넘+ 315,088경기(18.2 글로벌 랭크). updatedAt 11:25:43 UTC로 목록 통계와 같은 배치입니다. 증강 통계는 빈 배열입니다.

**[champion] 챔피언 스탯표 / 챔피언 트렌드(API)** — https://lolchess.gg/champions/set18/stats

- 보여주는 것: 스탯표: 비용 / 역할군 / 체력 / 공격력 / DPS / 사거리 / 공속 / 방어력 / 스킬 / 마나. 트렌드 API는 챔피언별 일자별 플레이·승·TOP4·픽률·평균 등수와 캐리 점수 캔들 데이터를 줍니다. 단 i18n의 '챔피언 트렌드' 탭은 시즌 18 화면에 노출되지 않았습니다.
- UX: 시너지 필터, 등급(코스트) 선택, 열 정렬, 더 보기.
- 엔드포인트: `https://tft.dakgg.io/api/v1/meta-champion-daily-stats?tierId=1&from=web` (확인됨)
- 핵심 필드: daily-stats: metaChampionDailyStats{updatedAt, shard, queueId, patch, revision, tierId, dailyStatsObj{'DA_Sentinel18':[['20260826', plays, wins, tops, placements, pickRate, avgPlacement], ...]}}. carry: /api/v1/meta-champion-carry-scores?tierId=1&from=web(200 확인) → metaChampionCarryScore{patchInfos[[20260910,1802,0],[20260831,1801,3],..], avgValueObj{'DA_18_Ivern':{'20260826':14.8,..}}, candleDataObj}. 스탯표는 /api/v1/data/champions?season=set18&hl=ko 입니다.
- 인증: 없음 / 표본·갱신: 시즌 시작 2026-08-26부터 일별 시계열(마스터+ 글로벌). updatedAt 11:30에서 12:00 UTC로 바뀌어 30분 주기입니다. 사전 데이터(/data/*)는 max-age=60이고 패치 때 바뀝니다.

**[trait] 시너지 통계 (시즌 18 > 시너지)** — https://lolchess.gg/synergies/set18

- 보여주는 것: 표: # / 시너지(활성 인원수+이름, 예 '11 나무정령', '5 사냥꾼') / 평균 등수 / TOP4 / 승률 / 픽률 / 게임 수 / 챔피언 TOP3. 탭: 시너지 통계 / 시너지 가이드 / 시너지 표. 장인 랭킹은 별도 항목에 정리했습니다.
- UX: 랭크 / 더블 업 탭, 티어 드롭다운(플래티넘+ 기본), 패치 드롭다운, 등급 필터(전체/프리즘/골드·고유/실버/브론즈), 열 정렬, 최종 업데이트 표시.
- 엔드포인트: `https://tft.dakgg.io/api/v1/meta-deck-traits?tierId=1&from=web` (확인됨)
- 핵심 필드: metaDeckTrait{updatedAt, shard, queueId, dt, patch, revision, tierId, plays, matchCount, metaDeckTraitStats[88 {keys:['DA_Juggernaut18', style(1~4), numUnits], plays, wins, tops, placements, pickRate, avgPlacement, traitChampionStats[{key:'DA_Amumu18', ...}]}]}. 사전은 /api/v1/data/traits?season=set18&hl=ko (200) → {key:'Adaptor', ingameKey:'DA_18_Adaptor', name:'적응가', desc(HTML), type:'CLASS'|'ORIGIN', imageUrl, blackImageUrl, whiteImageUrl, 단계별 효과}.
- 인증: 없음 / 표본·갱신: 챔피언 통계와 같은 배치입니다(글로벌 랭크, 18.2, tierId별 matchCount 동일, updatedAt 11:25:41 UTC).

**[item] 아이템 통계 / 조합표 / 상세 (시즌 18 > 아이템)** — https://lolchess.gg/items/set18

- 보여주는 것: 표: # / 아이템 / 평균 등수 / TOP4 / 승률 / 픽률 / 챔피언 TOP5(예 전략가의 방패 #3.52 67.19% 23.96% 0.03%, 아이번·아무무…). 탭: 아이템 통계 / 아이템 조합표(재료 격자, 그림자 아이템, 설명 함께 보기) / 아이템 가이드 / 3신기 한눈에. 아이템 상세는 챔피언별 사용 통계입니다.
- UX: 랭크 / 더블 업, 티어(플래티넘+ 기본), 패치, 종류 필터(전체/일반/상징/유물/찬란), 열 정렬.
- 엔드포인트: `https://tft.dakgg.io/api/v1/meta-deck-items?tierId=1&from=web` (확인됨)
- 핵심 필드: metaDeckItem{updatedAt, shard, queueId, dt, patch, revision, tierId, plays, matchCount, metaDeckItemStats[126 {key:'DA_GargoyleStoneplate', plays, wins, tops, placements, pickRate, avgPlacement, itemChampionStats[{key:'DA_18_Malphite', plays, wins, tops, placements, pickRate, avgPlacement}]}]}. 상세 /api/v1/meta-deck-items/DA_GargoyleStoneplate?tierId=3&patch=1802&revision=0&queueId=1100&from=web (200) → metaDeckItemDetail{metaDeckItemStat{key, ..., itemChampionStats[...]}}. 사전 /api/v1/data/items?season=set18&hl=ko (200) → {key:'AccomplicesGloves', ingameKey:'TFT11_Item_ThiefsGlovesSupport' 또는 'DA_AdaptiveHelm', ingameIcon, name, desc, shortDesc, fromDesc, imageUrl, isHidden, isSupport}. 아이템에서 챔피언을 찾는 역검색 데이터(itemChampionStats)를 API가 직접 줍니다.
- 인증: 없음 / 표본·갱신: 글로벌 랭크 18.2, tierId별 matchCount는 챔피언 통계와 같습니다. updatedAt 11:25:41 UTC(12:03에도 불변).

**[augment] 증강체 가이드 / 티어 / 확률 / 배제 목록** — https://lolchess.gg/augments/set18

- 보여주는 것: 증강 가이드: 실버/골드/프리즘별 이름과 효과 전문. 증강 티어: S/A/B(편집). 증강 확률: 2-1/3-2/4-2 라운드별 실버·골드·프리즘 등장 확률. 배제 목록. 시즌 18에는 '증강체 통계' 탭이 노출되지 않습니다.
- UX: 탭 링크 /augments/set18/tier, /chance, /exclusive, 쿼리 필터 ?type=all|silver|gold|prismatic, ?round=all|2-1|3-2|4-2, 증강 검색(배치툴).
- 엔드포인트: `https://tft.dakgg.io/api/v1/data/augment-tiers?season=set18` (확인됨)
- 핵심 필드: augment-tiers: data{updatedAt, augmentTiers[{key(ingameKey 'DA_18_BranchingOut'|'DA_BaronsLair'|'TFT7_Augment_BestFriends2'), tier:'S'|'A'|'B'|''}]}. 사전 /api/v1/data/augments?season=set18&hl=ko (200, 986개) → {key:'10000IQ', ingameKey:'TFT14_Augment_Controller_Vertical', name:'IQ 10,000', desc, imageUrl, tier(1~3), isHidden, tags}. 확률표는 API가 아니라 번들 JSON({set:'TFTSet18', name:'Augment_Tier_Distribution', rounds:['2-1','3-2','4-2'], data[18]})이며 _bundle_augment_tier_distribution_set18.json 으로 저장했습니다. 증강 통계 API /meta-deck-augments?tierId=1&from=web 은 {"patchRevisions":[]} 빈 응답이었습니다.
- 인증: 없음 / 표본·갱신: 통계 표본은 없습니다. 매치 participants.augments가 [] 이고 메타 덱 augments도 null이라 시즌 18은 매치 데이터에 증강이 없습니다. augment-tiers updatedAt은 2026-09-14 05:40 UTC로 편집자가 갱신하는 것으로 보입니다(추정).

**[tierlist] 티어표 (별도 메뉴 없음, 페이지 내 등급)** — https://lolchess.gg/augments/set18/tier

- 보여주는 것: 독립 티어표 메뉴는 없습니다(/tierlist 404). 등급은 네 곳에 흩어져 있습니다. (1) 메타 통계 덱 S/A/B/C (2) 챔피언 통계 평균 등수 옆 S 뱃지 (3) 증강체 티어 S/A/B (4) 시너지·아이템 표의 평균 등수 순위.
- UX: 덱과 챔피언 등급은 평균 등수 정렬 목록 안의 뱃지로만 보입니다. 증강 티어는 전용 탭에서 등급별로 묶어 보여 줍니다.
- 엔드포인트: `https://tft.dakgg.io/api/v1/data/augment-tiers?season=set18` (확인됨)
- 핵심 필드: 명시적인 티어 데이터는 augment-tiers{augmentTiers[{key, tier}]} 뿐입니다. meta-decks 항목과 meta-deck-champions에는 tier 필드가 없어(avgPlacement만 있음) S/A/B/C는 클라이언트 계산으로 추정합니다.
- 인증: 없음 / 표본·갱신: 증강 티어는 편집 데이터(2026-09-14 05:40 UTC)입니다. 덱과 챔피언 등급은 해당 통계 표본을 따릅니다.

**[profile] 전적 검색 (프로필: 시즌 종합 / 매치 히스토리 / LP 변화 추이 / 통계)** — https://lolchess.gg/profile/kr/%EB%9E%84%EB%9D%BC%EB%B6%95-KR1/set18

- 보여주는 것: 레벨, 아이콘, 랄라붕#KR1, '전적 갱신' 버튼, '시즌 17 성적표', 닥지지 카드, 최근 업데이트 n분 전. 랭크: Diamond IV 0LP와 상위 백분위·순위(티어 상위 1.029% 6,353위, 승리 14 상위 2.994%, 승률 12.5%, Top4 64, Top4 비율 57.1%, 게임 수 112, 평균 등수 #4.12). 티어 그래프(최근 50 매치), 초고속·더블업 등급, 최근 20매치 챔피언·시너지·증강 통계, 매치 히스토리(등수, Lv, 모드, 게임 시간, n시간 전, 8명 보드·아이템), 최근 20게임 등수 막대, LP 변화 추이(상승/하락, LP 변동, 누적 게임 수, TOP4, TOP4%).
- UX: 탭(시즌 종합/매치 히스토리/LP 변화 추이/통계), 매치 필터(전체/랭크/더블 업/일반/초고속), 더 보기 페이지네이션, 갱신 버튼(비동기 폴링), 즐겨찾기, 게임 중이면 상단 관전 메뉴 표시.
- 엔드포인트: `https://tft.dakgg.io/api/v1/summoners/kr/%EB%9E%84%EB%9D%BC%EB%B6%95-KR1/profile?season=set18` (확인됨)
- 핵심 필드: profile: summoner{puuid, shard, gameName, tagLine, summonerLevel, profileIconUrl, syncedAt}, summonerSeasons[{season, tier, rank}] (set1부터), summonerLeagues[{queueId:1100, tier, rank, leaguePoints, plays, wins, tops, avgPlacement, rating{tier|plays|wins|tops|winrate|winrate_10|toprate:{rank, total:608073, percent}}}], summonerSeasonPlacements[{queueId, values[8]}]. 함께 검증한 호출(모두 200):
- /summoners/kr/{이름-태그}/matches?season=set18&page=1&size=20 → meta{page, perPage, totalCount:112}, matches[8명 보드 DA_*], summoners, summonerLeagues
- /summoners/kr/{이름-태그}/matches/kr/8382256353?season=set18 → match. 같은 경로의 /eog 와 /details 는 404
- /summoners/kr/{이름-태그}/overviews?season=set18 → summonerSeasonOverviews[{queueId, plays, wins, tops, placements[8], matchStats[all|last20 {championStats, traitStats, traitStyleStats, itemStats, companionStats}]}]
- /summoners/kr/{이름-태그}/league-logs?queueId=1100&season=set18&page=1&size=20&sort=desc → summonerLeagueLogs[[ts, tier, rank, LP, gameNo, tops]]
- /summoners-by-riotid/{이름-태그}
- /rpc/summoner-sync/by-name/kr/{이름-태그}?web=1 → 1차 {retry_after:1000}, 재호출 {done:true, redirect_url}
- 인증: 없음. 전적 갱신 RPC도 비로그인 GET으로 동작했고 syncedAt 변경을 확인했습니다. / 표본·갱신: 개인 데이터라 Cache-Control no-store입니다. 데이터는 사용자가 갱신을 눌렀을 때 Riot API에서 동기화되며, 이전 syncedAt은 약 하루 전이었습니다. 백분위 모집단은 KR 랭크 608,073명입니다. 갱신 후 화면 순위가 5,803위(0.9543%)에서 6,353위(1.029%)로 바뀌었습니다.

**[profile] 시즌 성적표 (Set Report)** — https://lolchess.gg/set-report

- 보여주는 것: 지난 시즌(시즌 17) 개인 성적 리포트. 프로필 헤더의 '시즌 17 성적표' 버튼과 /set-report/{shard}/{name} 에서 봅니다.
- UX: 프로필에서 진입, 카카오·트위터 공유, 비동기 로딩(retryAfter 폴링).
- 엔드포인트: `https://tft.dakgg.io/api/v1/rpc/set-report/kr/%EB%9E%84%EB%9D%BC%EB%B6%95-KR1?season=set17&hl=ko&phase=prod` (확인됨)
- 핵심 필드: 1차 {retryAfter:1000}, 2차 {shard:'kr', name:'랄라붕-KR1', season:'set17', setReport{summoner{puuid, shard, gameName, tagLine, summonerLevel, profileIconUrl}, ...}} (134KB). 세부 필드는 rpc_set_report_set17.json 에 저장했습니다.
- 인증: 없음 / 표본·갱신: 개인 시즌 전체 기록이며 no-store입니다. 생성 시점과 주기는 확인하지 못했습니다.

**[other] 순위표 (랭크 / 더블 업 순위, 티어 분포도)** — https://lolchess.gg/leaderboards?region=kr&mode=ranked

- 보여주는 것: 표: 순위 / 소환사#태그 / 티어(C) / LP / 승 / TOP4(예 #1 <플레이어>#KR1 1730 LP 88 230). 챌린저 컷 500 LP, GM 컷 200 LP, 티어 분포도 차트, '최근 업데이트: 8분 전'.
- UX: 지역 탭(글로벌, BR, EUNE, EUW, JP, KR, LAN, LAS, ME, NA, OCE, RU, SEA, TR, TW, VN), 큐 탭(랭크 게임 / 더블 업 / 톡톡이의 시험), 티어 필터(전체~아이언), 페이지당 100명, 더블 업은 TOP4 정렬 옵션.
- 엔드포인트: `https://tft.dakgg.io/api/v1/leaderboards/summoners/kr?hl=ko&season=set18&tier=CHALLENGER&queueId=1100&page=1` (확인됨)
- 핵심 필드: meta{shard, season, queueId, tier, page, totalCount, updatedAt}, summonerTierCutoffs[{tier, leaguePoints}], summonerRankings[100 {puuid, shard, gameName, tagLine, summonerLevel, profileIconUrl, tier, rank, leaguePoints, plays, wins, tops}]. 함께 200 확인:
- /leaderboards/tier-distributions/kr?hl=ko&queueId=1100 → tierAvgs[{tier, play, win, top, loss, winRate, topRate, avgPlacement}], tierDistributions[['CHALLENGER',0.0003],..]
- /data/summoner-tier-cutoffs?shard=kr&season=set18 → 날짜별 컷 LP. shard가 없으면 400
- 인증: 없음 / 표본·갱신: KR 랭크: 챌린저 161명, tier=ALL 총 2,000명(상위권만 수록). 글로벌 챌린저 905명, KR 더블업 챌린저 2명. updatedAt이 11:47:21에서 12:01:58 UTC로 바뀌어 약 15분 주기입니다. max-age=60.

**[other] 장인 랭킹 (챔피언 장인 / 시너지 장인)** — https://lolchess.gg/leaderboards/champions/set18

- 보여주는 것: 표: 순위 / 지역 / 소환사 / 티어·LP / 숙련도(점수) / 승률 / TOP4 / 게임 수 / 3성작 수 / 사용 아이템(예 tantienTFT 97 LP 1,721점 19.6% 59.8% 214판 3성 112회).
- UX: 챔피언 또는 시너지 선택 목록(더 보기), 랭크 / 더블 업, 지역 탭(글로벌+15), '마지막 업데이트: 18분 전'. URL은 /leaderboards/champions/set18/Sejuani 처럼 lolchess key입니다.
- 엔드포인트: `https://tft.dakgg.io/api/v1/ranks/master/champions/DA_18_Ahri?hl=ko&shard=global&queueId=1100` (확인됨)
- 핵심 필드: summonerChampionRankingSnapshot{updatedAt, season, shard, queueId, championKey:'DA_18_Ahri', tierId, sortKey:'scores', summonerChampionRankings[{summoner{puuid, shard, gameName, tagLine, profileUrl, profileIconUrl}, summonerSeasonStat{tier, ...}, ...}]} (약 568KB). 특성은 /api/v1/ranks/master/traits/DA_18_Fae?hl=ko&shard=global&queueId=1100 (200) → summonerTraitRankingSnapshot{traitKey, summonerTraitRankings[...]}. /ranks/master/champions/Ahri?shard=kr 는 null이었습니다.
- 인증: 없음 / 표본·갱신: 글로벌 전 서버 상위 유저. updatedAt 11:52 UTC(화면 표시 18분 전). 정확한 주기는 확인하지 못했습니다.

**[livegame] 천상계 관전 + 프로필 라이브게임 조회 (+ 외부 오버레이 앱)** — https://lolchess.gg/spectate?region=kr

- 보여주는 것: 관전 가능한 상위 티어 게임 목록: 게임모드 / 소환사 / 티어 / LP / 관전하기(Windows 배치 실행 방식 안내). 조사 시점 화면 문구는 '현재 관전 가능한 게임이 없습니다. 라이엇 API 이슈로 관전 데이터를 받을 수 없는 상태일 수 있습니다.' 였습니다. 마스터 이하는 게임 중일 때 프로필 상단에 관전 메뉴가 뜹니다. 게임 위 오버레이는 웹이 아니라 외부 앱 DAK.GG Desktop(https://desktop.dak.gg → dak.gg/desktop, Windows/macOS)으로 제공합니다. 소개된 기능은 인게임 추천 메타, 최근 등수, 멀티서치, 배치한 챔피언 표시, 상점 알림, 라운드 상세입니다.
- UX: 지역 탭 15개(글로벌 없음, global은 400), 카드로 보기 / 목록으로 보기, LP 획득순 정렬. 프로필 라이브 조회는 react-query staleTime 30초, refetchInterval 60초, 응답의 retryAfter만큼 기다렸다 재조회합니다. Riot 상태 API(lol.secure.dyn.riotcdn.net/channels/public/x/status/{region}.json)에서 Spectator 장애 공지를 확인해 표시합니다.
- 엔드포인트: `https://tft.dakgg.io/api/v1/rpc/spectator/kr/<puuid>` (확인됨)
- 핵심 필드: rpc/spectator/{shard}/{puuid}: 1차 {retryAfter:1000}, 재호출 시 게임 중이 아니면 {shard:'kr', name:<puuid>}. 게임 중이면 spectator{gameId, ...} 가 오는데, 이는 코드에서 f.spectator.gameId 참조만 확인했고 실제 응답은 받지 못했습니다(KR 챌린저 상위 14명 모두 게임 중 아님). 이후 /spectate-queue/{region}/{gameId} 로 기록 상태를 조회합니다(미검증). 목록 /api/v1/spectate-matches/kr (200) → meta{updatedAt, count:0}, data[]. kr, na1, euw1, jp1, br1, eun1, la1, la2, oc1, tr1, ru, sg2, tw2, vn2, me1 모두 0건이었습니다.
- 인증: 없음 / 표본·갱신: spectate-matches는 max-age=30이고 updatedAt이 1~2분마다 바뀝니다. 조사 시각(21시 KST)에 15개 샤드 모두 0건이었고, 화면 문구상 Riot API 문제로 현재 사실상 비활성 상태입니다. 라이브 조회는 no-store 실시간입니다.

**[other] 배치툴 (팀 빌더) / 내 배치툴 / 팀 코드** — https://lolchess.gg/builder

- 보여주는 것: 헥스 보드, 챔피언(이름순/가격순/계열별/직업별), 아이템, 증강체 선택, 아이템 조합표, 레벨별 빌드 탭(초반/중반/최종 덱, Lv.5~10, 상징), 팀 코드 붙여넣기/복사, 내 배치툴 저장, 공략 작성, 공유하기.
- UX: 드래그 앤 드롭 배치, 챔피언·특성·증강 검색, 게임 팀 플래너 코드 붙여넣기와 복사(클라이언트 인코딩), 로그인 시 저장.
- 엔드포인트: `https://tft.dakgg.io/api/v1/team-builder-cell-stats?hl=ko&season=set18` (확인됨)
- 핵심 필드: cell-stats: teamBuilderCellStat{updatedAt, count:35099652, championCellStats[{key:'DA_18_Ornn', count, cellStats[['cell_25',558864,256443],..]}]}. 헥스 칸별 배치 빈도이며 셋째 값의 의미는 확인하지 못했습니다. refs: /api/v1/team-builder-refs?hl=ko&season=set18 (200, 729KB) → refs{champions 160, traits 109, items 272, augments 986}. 팀 코드는 번들이 생성합니다: '02' + 10×3hex(빈칸 000) + 'TFTSet18', 대응표 66개(3e9=DA_18_Ahri ...), _bundle_teamcode_map_set18.json 에 저장했습니다. 저장 API는 POST /my-team-builders, POST /builders 입니다(Bearer 토큰, 미호출).
- 인증: refs와 cell-stats는 없음. 내 배치툴 저장은 로그인(Authorization: Bearer) 필요. / 표본·갱신: 누적 3,510만 유닛 배치(시즌 전체로 추정). updatedAt 09:39 UTC가 12:03에도 그대로여서 수 시간 이상 주기로 추정합니다.

**[other] 패치노트 / 게임 가이드 / 보상표 / 기타 도구** — https://lolchess.gg/guide/patch-notes

- 보여주는 것: 한국어 패치노트(18.2b 등, '임시 번역' 표기 포함), PBE 패치노트, 역할군, 라운드, 골드/경험치, 리롤 확률, 피해량 공식, 단축키, 꼬마 전설이, 수호령/보상표(/rewards/set18/wisps), 시즌 개요(/tft/18), 시뮬레이터, 즐겨찾기, 게시판/듀오찾기.
- UX: 패치 버전 목록에서 선택, 가이드는 정적 표. 게시판은 로그인 후 글쓰기.
- 엔드포인트: `https://tft.dakgg.io/api/v1/patch-notes?hl=ko` (확인됨)
- 핵심 필드: patch-notes: meta{locale:'ko_KR'}, patchNotes[{id:562, patchVersion:'18.2b', registeredAt, season:'set18'}]. 본문은 SSR queryKey ['patchNoteDataRefs',562] → patchNote{id, registeredAt, content{섹션명:{icon, descs[]}}}. 함께 200 확인: /api/v1/data/companions?hl=ko → companions[{key(UUID), name, imageUrl, level, speciesId, speciesName}], /api/v1/recent-bbs-articles?recommend=true → boards[{boardId, articles[{id, no, title, hit}]}], /api/v1/seasons → {br1:[set18..set1], ...}, /api/v1/data/landing → {season:'set18', isReleased:true}. 보상표와 가이드는 대부분 번들·SSR 정적 데이터이며 별도 API는 확인하지 못했습니다.
- 인증: 없음 / 표본·갱신: 패치노트는 패치 공개일 기준 등록(18.2b registeredAt 2026-09-15). 게시판 max-age=5, 나머지 max-age=60.

</details>

### metatft.com

- **접근 가능**: 예
- **식별자 체계**: 식별자는 숫자가 아니라 문자열 키다. 사전(lookups)의 id는 None이다.

- **챔피언**: 통계·덱 API의 키와 파라미터는 DA_* 에셋명이다(DA_18_Aphelios, DA_Amumu18, DA_Lux18_Base, DA_Nidalee18_AP처럼 이름 규칙이 섞여 있다). 사전 TFTSet18_latest_{lang}.json에서는 units[].apiName이 라이엇 표기 TFT18_Aphelios이고 assetNames가 ['DA_18_Aphelios']다. unitAssetNames 맵(DA_* → TFT18_*)으로 변환해야 한다. unit_detail_overall?unit=TFT18_Aphelios는 200이지만 levels와 dates가 빈 배열이다. 오류 없이 빈 값만 오므로 반드시 DA_ 키를 써야 한다. units 응답에는 TFT18_NidaleeCougar, TFT18_MasterYi처럼 표본이 수백 개뿐인 비 DA 행이 섞인다.
- **특성**: 통계 행은 DA_18_Adaptor_1처럼 '_활성단계' 접미사가 붙는다. trait_detail 파라미터는 접미사 없는 DA_18_Blossom이다. URL 슬러그는 trait_mapping.json(adaptor → DA_18_Adaptor, TFT16·TFT17 항목 혼재)으로 바꾼다.
- **아이템**: DA_InfinityEdge, 상징은 DA_18_EmblemBlackthorn. unit_detail_items 행은 'DA_Guinsoos<플레이어>blade-1'처럼 -N 접미사가 붙는데 의미는 미확인이다. 빌드는 'A|B|C', 덱 옵션은 units_list 'A&B&...'로 구분자가 다르다.
- **증강**: DA_PandorasBench 형태(augments_tiers, 사전 augments[].apiName).
- **덱**: 숫자 클러스터 ID(423009 = cluster_id 423 × 1000 + 순번). 재클러스터링하면 바뀐다.
- **플레이어**: riot_id '이름#태그'와 puuid. 서버 코드는 API 경로에서 KR, 응답에서 summoner_region 'kr'. 경기 id는 KR_8382256353, 앱 경기는 uuid.

같은 id인데 필드명이 다르다. items_matches.itemName, comp_details.itemNames[].itemNames, trait_detail.units[].units가 그 예다. CommunityDragon·lol.qq와의 공통 키가 DA_* 라는 점은 프로젝트 메모리 기준이고, 이번에 CommunityDragon은 다시 호출하지 않았다.
- **샘플 폴더(당시)**: `C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/bench/metatft/`

**기술 메모**

[구조] Vite로 빌드한 React SPA다. index.html은 4,190바이트짜리 셸이고, /assets/main-B0QEiPU-.js(1.12MB)와 동적 청크 94개(합계 7.39MB)를 불러온다. 청크 해시는 배포마다 바뀐다. Redux, axios, react-router, i18next를 쓰며 번역 파일은 data.metatft.com/locales/{lng}.json에서 받는다. SSR은 없고 모든 데이터를 클라이언트 XHR로 받는다. 그래서 /player/... 경로도 같은 셸이 HTTP 200으로 온다.

[호스트]
- www: Cloudflare. HTML에 window.__CF_COUNTRY가 주입된다.
- api-hc.metatft.com: 통계·덱. CDN 헤더와 server 헤더가 없고 ACAO는 *. 404 응답이 {message:'Route GET:/... not found', error, statusCode} 형태라 Fastify로 추정한다.
- api.metatft.com: 프로필·리더보드·관전·사용자 콘텐츠. 404 페이지로 보아 nginx다. public/* 와 tft-usercontent의 ACAO는 https://www.metatft.com으로 제한되고, tft-leaderboard와 tft-spectate는 *.
- api2.metatft.com: Patreon 인증에만 쓰인다.
- data.metatft.com: 정적 JSON. Cloudflare 뒤의 S3 호환 버킷이다(없는 파일에 AccessDenied XML, HostId fra1). Cache-Control max-age=120.
- cdn.metatft.com: 이미지(/file/metatft/{champions,items,traits,augments,...}, cdn-cgi/image 리사이즈).
- matches3.metatft.com: 경기 JSON. max-age=365000000, immutable.

[통계 쿼리 규칙] RequestService 청크의 QueryMaker를 따른다.
- URL = https://api-hc.metatft.com/tft-stat-api/ + path + '?' + 필터(key=value). 값이 여럿이면 정렬해 쉼표로 잇는다.
- patch가 'current'가 아니면 'X_Y'를 patch=X&b_patch=Y로 나눈다.
- rank는 queue=1100일 때만 붙는다.
- unit·itemName·trait 같은 추가 파라미터는 필터 뒤에 붙는다.
- path가 https://로 시작하면(예: tft-comps-api/comps_data) 그 URL을 기준으로 삼는다.

[기본 필터 {F}] queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustment=true. localStorage의 StatsFilters에 저장된다. 선택지는 다음과 같다.
- queue: 1100 랭크, 1160 더블업. PBE·부활·이벤트는 활성 시에만 추가.
- patch: current, 또는 games 목록에서 온 'X_Y' 쌍.
- days: 1~7.
- rank(다중): RANKUNKNOWN, IRON ~ CHALLENGER.
- server(다중): BR1, EUN1, EUW1, JP1, KR, LA1, LA2, ME1, NA1, OC1, RU, SG2, TR1, TW2, VN2.
- permit_filter_adjustment: true/false.

실제 호출로 적용 여부를 확인했다.
- days=1: 1,051,712
- MASTER+ 보정 off: 225,368
- queue=1160: 863,592
- patch=18.1&b_patch=d&days=7: 2,946,854
- 단 patch=18.1&b_patch= (빈값)은 응답 patch가 18.2로 나왔다.

[차단 여부] UA, Referer, Origin, 쿠키, 서명 모두 필요 없다. curl 기본 UA로도, UA 헤더를 지워도 api-hc, api, data 모두 200이었다. 약 80회 호출하는 동안 429나 챌린지는 없었다.

[선수 페이지 URL]
- 라우트는 /player/:server/:playerName. 검색하면 /player/${server.toLowerCase()}/${이름}-${태그}로 이동하고, 링크 복사는 https://www.metatft.com/player/{서버 소문자}/{이름-태그}(+?match=&tab=&round=) 형태다.
- 랄라붕#KR1이면 https://www.metatft.com/player/kr/랄라붕-KR1 (인코딩: /player/kr/%EB%9E%84%EB%9D%BC%EB%B6%95-KR1). HTTP 200을 확인했지만 SPA라 어떤 경로든 200이 온다.
- 이 페이지가 부르는 https://api.metatft.com/public/profile/lookup_by_riotid/KR/%EB%9E%84%EB%9D%BC%EB%B6%95/KR1?source=full_profile&tft_set=TFTSet18 가 200, 211KB로 실제 데이터를 준다.
- 리더보드의 프로 링크는 ?queue=pro가 붙는다.

[세트·버전 확인] tft-stat-api/tft_set_config (live TFTSet18), tft-stat-api/patch (18.2, 시작 2026-09-09T20:39Z), tft-comps-api/latest_cluster_id (423).

[메뉴 구조] 번들의 lge 매핑에 상단 그룹 8개가 있다: Comps, Stats, Tools, Team Builder, Info, Players, Guides, Tables. ko_kr 번역은 조합, 통계, 도구, 팀 빌더, 정보, 플레이어, 가이드, 인게임 보상 및 확률. 라우트 90여 개는 번들 path 목록에서 뽑았고 sitemap은 566개 URL이다. 각 그룹에 어떤 라우트가 속하는지는 추정이다.

[작업 산출물] 번들은 scratchpad/metatft_js/, 호출·저장 도구는 scratchpad/mt_fetch.py, 번들 문맥 검색은 scratchpad/ctx.py, 호출 목록 전체는 bench/metatft/_endpoints.tsv에 있다.

**이 사이트만의 강점**

- 필터로 직접 질의하는 실시간 통계 API다. queue, patch(b_patch), days(1~7), rank(다중), server(15개)를 조합하면 서버가 바로 집계해 표본 크기(games.count, filter_adjustment.sample_size)와 함께 준다. 100초 간격으로 다시 부르니 표본이 3,088 늘어 실시간 누적을 확인했다. lol.qq는 정적 JSON이고 CommunityDragon에는 통계가 없다.
- 챔피언·아이템·특성마다 원시 등수 분포 places[8]를 준다. 평균 등수, 톱4, 승률, 빈도를 앱에서 같은 공식으로 다시 계산할 수 있고, S~D 티어 공식(유닛 4.5−avg 기준 ±.1/.3, 특성 ±.25/.5, 아이템 빈도 가중)도 번들에서 확인했다.
- 상세 통계가 깊다. 유닛별 아이템 단품·3아이템 빌드 성과(unit_detail_items), 스테이지별 배치 칸 빈도·상대 승률(unit_positions2), 아이템 완성 스테이지별 승률(item_stage_detail), 동반 특성·유닛(trait_detail), 일별(최대 4개월)·시간별(최대 2주) 추세를 제공한다.
- 아이템 역검색 데이터가 이미 가공돼 있다. tft-comps-api/unit_items_processed(68KB) 한 번이면 아이템→상위 5 유닛과 유닛→상위 10 아이템을 평균 등수·픽률과 함께 받는다. item_detail.units는 필터별 착용 유닛 순위다. 앱의 아이템 역검색 요구에 바로 맞는다.
- 덱 클러스터 심층 데이터가 있다. 레벨 도달 타이밍, 레벨별 리롤 수, 랭크별 성과, 덱 간 카운터(place_change), 초반 보드 옵션, 배치 히트맵, 레벨별 최종 보드 옵션, 성급·아이템 수별 유닛 성과, 연결된 프로 덱까지 제공한다.
- 한국어 게임 사전과 UI 번역을 인증 없이 받을 수 있다. TFTSet18_latest_ko_kr.json은 DA_* 키, 한글 이름·설명·수치, DA→TFT18 매핑을 담고 있고, locales/ko_kr.json에는 평균 등수, 순방 확률, 선택률 같은 한국어 용어가 있다. CommunityDragon 대신 쓸 수 있다.
- 플레이어 데이터가 풍부하다. 라이엇 ID 한 번 조회로 세트별 티어 이력, LP 변화, 서버 순위, 경기 요약(유닛·아이템·로비 평균 티어)이 오고, 경기 JSON에는 참가자 8명의 티어·MMR이 있다. 서버별 상위 1,000명 리더보드, 급상승 플레이어, 프로 선수 목록도 모두 인증 없이 받는다.
- 데스크톱 앱이 수집한 데이터가 있다. 라운드별 보드·상점·리롤·데미지 기록, 라운드별 보드 강도 백분위(percentiles), TF.js 승률 예측 모델 입력 사전을 제공한다. 다른 두 소스에는 없다.
- 게임 상수 표(상점 확률, 경험치, 연승·연패 수입, 드롭 테이블)를 JSON으로 준다.
- 탐색기로 임의 조건 표본의 평균 등수·톱4·승률·등수 분포를 질의할 수 있다.

**수집 위험** — [비공개 API·스키마 변동]
문서도 안정성 보장도 없다. 경로에 개정 흔적(unit_detail2, unit_positions2, augments_full2, tft-leaderboard/v2)이 있고 번들 해시가 배포마다 바뀐다. 세트 번호 18 이상에서 로직이 갈리고, 세트가 바뀌며 폐기된 경로를 실제로 봤다.
- tft-stat-api/charms: 404
- 추정 경로 tft-stat-api/augments: 500
- api.metatft.com/augments, api.metatft.com/updated: 404
- data.metatft.com/lookups/latest_TFTSet18_tables.json: 403 (사이트는 pbe_ 로 폴백)
- comp_augment_tiers?cluster_id=423009: 500

[차단]
지금은 서명, 토큰, UA 검사가 없다(curl 기본 UA 200, 약 80회 호출에 429 없음). 다만 api-hc는 CDN 캐시 없이 요청마다 오리진에서 집계하므로, 대량 수집은 요율 제한이나 차단 도입을 부를 수 있다(추정). api.metatft.com의 public/* 와 tft-usercontent는 CORS를 www로 제한하고 세션 쿠키를 발급하므로 인증이 강화될 여지가 있다. robots.txt는 전체 허용이다. 이용약관의 스크래핑 조항은 번들 키워드 검색으로 찾지 못했지만 확인에 한계가 있다.

[식별자·스키마 함정]
- 챔피언은 통계 키 DA_* 와 사전 apiName TFT18_* 가 달라 unitAssetNames로 변환해야 한다. unit=TFT18_Aphelios는 200이지만 빈 데이터라 오류 없이 실패한다.
- units 응답에 TFT18_NidaleeCougar 같은 비 DA 행이 섞인다.
- 특성 행에는 '_단계', 유닛 아이템 행에는 '-1' 접미사가 붙는다.
- 구분자가 제각각이다: 빌드 'A|B|C', 덱 옵션 '&', units_string ', '.
- 필드명이 일관되지 않다: itemName과 itemNames, unit과 units.
- board_strength는 JSON 문자열 안에 배열이 들어 있다.

[표본 해석]
- games[0].count와 filter_adjustment.sample_size가 다르게 나오는데 이유는 확인하지 못했다.
- permit_filter_adjustment=true면 표본이 부족할 때 서버가 랭크 필터를 완화할 수 있다(override_applied). 앱에 표시하거나 false로 고정해야 한다.
- 과거 패치는 b_patch까지 맞춰야 적용된다. patch=18.1&b_patch=(빈값)은 18.2로 폴백했다.

[증강 통계 없음]
Set 18은 라이엇 경기 데이터와 앱 경기 데이터 모두에서 증강이 비어 있다. 그래서 증강 페이지는 에디터 한 명(META Spencer)의 수동 티어이고, 덱별 증강 티어도 가이드에서 옮긴 수동 데이터다. 증강을 평균 등수로 비교하는 기능은 이 소스로 만들 수 없다.

[덱 식별자]
클러스터 ID(423xxx)는 재클러스터링마다 바뀐다. 저장해 두고 쓰지 말고 units_string 집합으로 대조해야 한다(현 수집기 방식 유지).

[응답 크기]
comp_options와 comp_builds가 각 4.5MB, 사전이 1.3MB, 앱 경기 JSON이 767KB다. 모바일에서 직접 부르기보다 수집기에서 줄여 배포하는 편이 낫다.

[개인정보·약관]
프로필과 경기 JSON에 다른 플레이어의 puuid, riot_id, 티어, MMR이 들어 있다. 앱에 저장·표시할 범위와 Riot 서드파티 정책, metatft 이용약관을 확인해야 한다.

[지난 세트 데이터 혼입]
sitemap과 trait_mapping.json에 TFT16·TFT17 항목이 섞여 있고 unit_descriptions.json은 Set 7 데이터라, 세트로 걸러 써야 한다.

#### 기능 17개

| 카테고리 | 메뉴 | 보여주는 것 | 엔드포인트 | 확인 | 인증 | 표본·갱신 |
|---|---|---|---|:---:|---|---|
| deck | 조합 (Comps) 목록 | 덱 클러스터 54개를 카드로 보여 준다.<br>- 대표 이름: name_string 특성·유닛 조합<br>- 평균 등수: overall.avg<br>- 게임 수<br>- 레벨링 방식: levelling, 예 'Fast 9'<br>- 난이도: difficulty, diff_pick, diff_place<br>- 핵심 유… | `https://api-hc.metatft.com/tft-comps-api/comps_data?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_a… | ✓ | 불필요. 브라우저 UA 없이 curl 기본 UA로도 2… | - 기본 필터: 랭크 1100, 패치 18.2, 3일, 플래티넘+. 클러스터 합계 5,443,824 보드, 1위 덱 423009는 554,806 보드.<br>- 클러스… |
| deck | 조합 상세 (덱 카드 탭) | 덱 하나의 심층 통계.<br>- 유닛 성급·아이템 수별 평균 등수와 비율<br>- 추천 3아이템 빌드<br>- 레벨 도달 타이밍, 레벨별 리롤 수, 최종 레벨별 등수<br>- 랭크별 평균 등수와 픽률<br>- 상대 덱 카운터(place_change), 로비 내 같은 덱 인원<br>- 초반(레벨 4) 보드 옵션<br>- 배치… | `https://api-hc.metatft.com/tft-comps-api/comp_details?comp=423009&cluster_id=423 \| https://api-hc.metatft.com/tft-comps-api/comp_augment_tiers` | ✓ | 불필요 | - 기본 필터와 같다. 423009 덱은 554,806 보드.<br>- 추세는 2026-09-08부터 일별.<br>- 증강 티어는 수동 콘텐츠를 가져온 것(source_ti… |
| champion | 챔피언 통계 (Units) | 챔피언별 항목.<br>- 티어(S~D)<br>- 평균 등수, 승률, 톱4 비율<br>- 빈도(%)와 보드 수<br>- 코스트, 특성, 인기 아이템 | `https://api-hc.metatft.com/tft-stat-api/units?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustm… | ✓ | 불필요 | - 기본(3일, 플래티넘+, 18.2): games.count 7,859,448. 100초 뒤 다시 부르니 7,862,536이라 실시간 누적을 확인했다. samp… |
| champion | 챔피언 상세 | - 성급·아이템 수별 성과<br>- 아이템 단품 성과와 3아이템 빌드 성과<br>- 스테이지별 배치 칸 빈도와 상대 승률<br>- 일별·시간별 평균 등수와 빈도 추세<br>- 이 챔피언이 들어가는 덱<br>- 증강 탭: 데이터 없음 | `https://api-hc.metatft.com/tft-stat-api/unit_detail_overall?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_… | ✓ | 불필요 | - 기본 필터를 따른다(아펠리오스 아이템 표본 962,225 보드).<br>- unit_trends는 2026-05-15(패치 16.10)부터 일별 153행이고 sam… |
| trait | 특성 (Traits) | - 특성·활성 단계별 티어, 평균 등수, 승률, 톱4, 빈도<br>- 상세: 함께 활성화된 특성, 함께 쓰인 유닛, 추가 특성별 성과<br>- 해당 특성 덱 목록 | `https://api-hc.metatft.com/tft-stat-api/traits?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjust… | ✓ | 불필요 | - 기본 필터 games.count 6,766,472, trait_detail games 7,868,920<br>- 5분 갱신 문구<br>- trait_mapping은 Ca… |
| item | 아이템 (Items) | - 아이템별 티어, 평균 등수, 승률, 톱4, 빈도<br>- 상세: 일별 추세, 착용 유닛 순위(유닛 평균 대비), 완성 스테이지별 승률, 유닛·스테이지 조합<br>- 역검색용: 아이템 → 상위 5 유닛, 유닛 → 상위 10 아이템 | `https://api-hc.metatft.com/tft-stat-api/items_matches?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter… | ✓ | 불필요 | - 기본 필터 games.count 6,778,848<br>- unit_items_processed는 필터 없이 2,686,224 보드 기준이고 클라이언트가 1시간마다… |
| augment | 증강 티어 리스트 (Augments) | 에디터 한 명(META Spencer)이 수동으로 만든 S~D 증강 티어다. 통계가 아니다.<br>- 태그: 전투, 경제, 아이템, 성장, 특성<br>- 희귀도: 실버, 골드, 프리즘<br>- '업데이트 N분 전' 표시 | `https://api-hc.metatft.com/tft-stat-api/augments_tiers` | ✓ | 불필요 | - 표본 없음(수동 콘텐츠)<br>- updated_at 2026-09-15T11:11:58Z, created_at 2026-08-07<br>- 갱신은 비정기 |
| tierlist | 티어 리스트 / 프로 덱 티어 | - 사용자·에디터가 만든 증강·아이템·챔피언 티어표(S~D와 메모)<br>- 과거 세트에는 Anomalies, Power Ups 타입도 있었다<br>- 프로 선수(예: SOLOGESANG)의 덱 티어표<br>- 통계 페이지의 S~D는 서버 값이 아니라 클라이언트 공식이다 | `https://api.metatft.com/tft-usercontent/id/1b860280 \| https://api-hc.metatft.com/tft-stat-api/pro-comps` | ✓ | 읽기는 필요 없다. 다만 usercontent 응답이… | - 수동 콘텐츠<br>- pro-comps updated_at 2026-09-15T08:23Z<br>- 공개 목록을 한꺼번에 조회하는 엔드포인트는 찾지 못했다 |
| profile | 전적 검색 / 플레이어 프로필 | - 현재·최고 티어, 세트별 티어 이력, LP 변화 그래프, 서버 순위(rank/total)<br>- 등수 분포<br>- 최근 경기 목록: 등수, 유닛·성급·아이템, 특성, 로비 평균 티어, 패치, 게임 시간<br>- 경기 상세: 참가자 8명의 보드와 티어·서버 순위<br>- MetaTFT 앱으로 기록한 경… | `https://api.metatft.com/public/profile/lookup_by_riotid/KR/%EB%9E%84%EB%9D%BC%EB%B6%95/KR1?source=full_profile&tft_set=TFTSet18 \| https://api.metatft.com/publi… | ✓ | 불필요. api.metatft.com public/*… | - 개인 단위<br>- 조회 시점의 최신 데이터다. 마지막 경기(2026-09-15T11:22Z)가 바로 반영돼 있었다.<br>- 경기 JSON은 immutable 캐시<br>-… |
| other | 리더보드 / 급상승 플레이어 / 프로 | - 서버별 상위 1000명: 티어, LP, 게임 수, 평균 등수(place_sum/num_played), 승률, 최근 20경기 LP 변화, 이번 패치 성적, 플레이 스타일<br>- 프로 선수 목록(pro_name, pro_points)<br>- 급상승 플레이어(기간 A→B 레이팅 변화, 등수 분포… | `https://api.metatft.com/tft-leaderboard/v2/kr?offset=0&limit=100 \| https://api.metatft.com/tft-leaderboard/v2/pro?offset=0&limit=200 \| https://api-hc.metatft.c… | ✓ | 불필요 | - KR 1,000명<br>- rising 내부 updated 2026-09-15T11:07Z로, 조회 약 50분 전이다. 갱신 주기는 확인하지 못했다.<br>- 리더보드는… |
| livegame | 라이브 게임 조회 / 랭커 관전 (Spectate) | - 프로필 주인이 게임 중일 때 게임 중 표시와 로비 참가자<br>- 상위 랭커의 진행 중 게임 목록과 관전 실행(데스크톱 앱 연동) | `https://api.metatft.com/tft-spectate/summoner_by_puuid/kr/{puuid} \| https://api.metatft.com/tft-spectate/top_players?region=kr` | ✗ | 조회는 필요 없다. 관전 실행에는 MetaTFT 데스크… | 실시간 조회다. 테스트 시점에는 표시할 데이터가 없었다. |
| livegame | 인게임 앱 데이터 (승률 예측·보드 강도·라운드 기록) | - 게임 중: 상점 알림, 증강·아이템 추천, 로비 스카우팅, 라운드별 승률 예측(TF.js 모델)<br>- 경기 후: 라운드별 보드, 벤치, 상점, 리롤, 데미지, 보드 강도 기록<br>- 웹은 다운로드 안내와 결과 열람만 한다 | `https://api-hc.metatft.com/tft-stat-api/percentiles \| https://data.metatft.com/model_lookupsEA_TFTSet18.json \| https://matches3.metatft.com/027f9376-cff3-48f9-… | ✓ | 조회는 필요 없다. 앱 경기 데이터는 Overwolf… | - percentiles는 클라이언트가 24시간마다 다시 받는다<br>- 모델 lookup last-modified 2026-09-15T08:57Z<br>- 앱 경기 JSO… |
| other | 탐색기 (Data Explorer) | 유닛·특성·아이템 조건을 조합한 임의 표본의 통계.<br>- 총 게임 수, 평균 등수, 톱4, 승률, 등수 분포<br>- 유닛별 분해 | `https://api-hc.metatft.com/tft-explorer-api/total?formatnoarray=true&compact=true&queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,M… | ✓ | 불필요 | 기본 필터에서 total_games 6,781,264. |
| other | 추세 (Trends) | 패치 전후 일자별 유닛 평균 등수와 빈도 변화. | `https://api-hc.metatft.com/tft-stat-api/trends?queue=1100&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&target=units` | ✓ | 불필요 | 최근 8일 일별이다. 하루 표본은 약 2.26M 보드. |
| other | 게임 데이터 사전 / 인게임 보상 및 확률 (Tables) / 한국어 U… | - 상점 확률, 경험치, 연승·연패 수입, 스테이지, 증강 등장 라운드, 매력(charm) 상점, 드롭 테이블 같은 게임 상수 표<br>- 모든 페이지가 쓰는 이름·설명·아이콘 사전(한국어 포함) | `https://data.metatft.com/lookups/TFTSet18_latest_ko_kr.json \| https://data.metatft.com/lookups/TFTSet18_latest_en_us.json \| https://data.metatft.com/lookups/pb… | ✓ | 불필요. Cloudflare 캐시 HIT, ACAO *… | - 정적 파일, Cache-Control max-age=120<br>- last-modified: 사전 2026-09-11, tables 2026-09-14, 번역 2… |
| other | 공통 통계 필터 / 표본·패치 메타데이터 | - 필터 패널의 패치 목록<br>- 서버·랭크·큐별 게임 수<br>- 현재 세트와 PBE·부활·이벤트 큐 설정 | `https://api-hc.metatft.com/tft-stat-api/patch \| https://api-hc.metatft.com/tft-stat-api/games?days=7 \| https://api-hc.metatft.com/tft-stat-api/tft_set_config \|… | ✓ | 불필요 | - 전 세계(전 큐·랭크 합계) 하루 5.07M~6.33M 보드, KR 하루 0.83M~1.17M 보드<br>- 패치 18.2 시작 후 30.9M<br>- 호출할 때마다 값… |
| other | 그 밖의 메뉴 (확인 안 함): 팀 빌더, 나의 덱 리스트, 초반 빌드,… | - 팀 빌더(덱 코드, protobuf spec), 사용자 덱 목록, 초반 보드 추천, PBE·모드별 덱<br>- VOD·리플레이 라이브러리, 대회 기록, 미니게임(보들), 장인 랭킹<br>- 과거 세트 메커닉 티어, 스트리머 오버레이 | — | ✗ | 팀 빌더 저장, 덱 리스트, 계정은 RSO·Patreo… | 확인하지 않았다. |

<details><summary>기능별 UX·응답 필드 전문</summary>

**[deck] 조합 (Comps) 목록** — https://www.metatft.com/comps

- 보여주는 것: 덱 클러스터 54개를 카드로 보여 준다.
- 대표 이름: name_string 특성·유닛 조합
- 평균 등수: overall.avg
- 게임 수
- 레벨링 방식: levelling, 예 'Fast 9'
- 난이도: difficulty, diff_pick, diff_place
- 핵심 유닛 아이템 빌드: builds, top_itemNames
- 일별 추세: trends의 day, count, avg, pick
- 헤드라이너·성급 정보
- UX: - 공통 통계 필터 패널: 게임모드, 패치, 기간, 랭크, 서버, 필터 보정
- 고급 필터, 가진 아이템으로 덱을 찾는 아이템 도우미
- 덱 숨김·고정, 정렬 드롭다운(CompBrowserSortByDropdown)
- 카드를 펼치면 상세 탭(빠른 시작·레벨링, 옵션, 배치, 빌드, 증강, 카운터, 추세, 프로 덱, VOD)
- 엔드포인트: `https://api-hc.metatft.com/tft-comps-api/comps_data?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustment=true | https://api-hc.metatft.com/tft-comps-api/latest_cluster_info | https://api-hc.metatft.com/tft-comps-api/comp_options | https://api-hc.metatft.com/tft-comps-api/comp_builds | https://api-hc.metatft.com/tft-comps-api/unit_items_processed` (확인됨)
- 핵심 필드: **comps_data** (241KB)
- results.data.cluster_details['423009']: Cluster, centroid, units_string(쉼표 구분 DA_*), traits_string(DA_18_X_단계), name[{name,type,score}], overall{count,avg}, builds[{unit,buildName[3],avg,count,place_change,score}], build_items, top_itemNames, trends, diff_pick, diff_place, difficulty, levelling
- results.games['423009'][{count,avg}]. 'Overall', '-1' 키도 있다.
- updated, tft_set, cluster_id, filter_adjustment{override_applied, rank_filter, sample_size}

**latest_cluster_info** (54KB)
- cluster_details.clusters[54]
- unit_lookup: DA_* → apiName TFT18_*, name, unit_cost, traits
- trait_lookup, emblem_lookup, counters[2916]{against, place_change}, players(로비당 같은 덱 인원 분포), column_mappings

**comp_options** (4.55MB)
- results.options[cluster][레벨 7~11][{units_list('&' 구분), traits_list, score, avg, count}]

**comp_builds** (4.53MB)
- results[cluster].builds[300]

파라미터 없이 comps_data를 불러도 기본 필터와 같은 값이 왔다.
- 인증: 불필요. 브라우저 UA 없이 curl 기본 UA로도 200이고 ACAO는 *. / 표본·갱신: - 기본 필터: 랭크 1100, 패치 18.2, 3일, 플래티넘+. 클러스터 합계 5,443,824 보드, 1위 덱 423009는 554,806 보드.
- 클러스터 세트 423은 2026-09-11T15:48Z 생성. 며칠 간격으로 다시 만드는 것으로 추정.
- updated는 요청 시각이다.
- unit_items_processed는 클라이언트가 1시간마다 다시 받는다.

**[deck] 조합 상세 (덱 카드 탭)** — https://www.metatft.com/comps

- 보여주는 것: 덱 하나의 심층 통계.
- 유닛 성급·아이템 수별 평균 등수와 비율
- 추천 3아이템 빌드
- 레벨 도달 타이밍, 레벨별 리롤 수, 최종 레벨별 등수
- 랭크별 평균 등수와 픽률
- 상대 덱 카운터(place_change), 로비 내 같은 덱 인원
- 초반(레벨 4) 보드 옵션
- 배치 칸 히트맵
- 레벨별 최종 보드 옵션
- 연결된 프로 덱
- 덱별 수동 증강 티어
- UX: - 탭 전환, 레벨별 옵션 선택
- 유닛·증강을 잠그면 통계가 다시 계산된다(updateSelection)
- 팀 빌더로 가져오기, 라이브 보드와 비교
- 엔드포인트: `https://api-hc.metatft.com/tft-comps-api/comp_details?comp=423009&cluster_id=423 | https://api-hc.metatft.com/tft-comps-api/comp_augment_tiers` (확인됨)
- 핵심 필드: **comp_details** (322KB)
- results.cluster, placements[{count,avg}], counters[54]{against, place_change, similarity}
- players{'1.0':인원}, final_levels[{level,count,avg}]
- unit_stats[{unit, tiers[{tier,avg,count,pcnt}], num_items[...], count, avg, pcnt}]
- builds[299], itemNames[{itemNames,count,avg,pcnt,units}], options{'9':[{units_list,traits_list,score,avg,count}]}
- trends[{day,count,avg,pick}], traits[{trait,score,count,levels[{level,count,avg}]}]
- augments[{aug:'',count,avg,orders}]: 빈 값
- positioning.positions{cell_N:count}, relative_positioning, early_options{'4':[{unit_list,level,avg,win}]}
- levels[{stage,round,level,count}], rerolls{레벨:{rerolls,matches,count}}, ranks[{rank,count,avg,pick,rank_sort}]
- proComps[{distance,content}], headliner_units, first_carousel, suggested_legends

**comp_augment_tiers** (85KB)
- results[클러스터 28개]{augments[{id:'DA_BandOfThieves', tier:'S'}], gods[], source_title(프로 가이드 제목), distance}
- 번들 코드는 ?cluster_id=를 붙이지만 comp_augment_tiers?cluster_id=423009는 HTTP 500이다.
- 인증: 불필요 / 표본·갱신: - 기본 필터와 같다. 423009 덱은 554,806 보드.
- 추세는 2026-09-08부터 일별.
- 증강 티어는 수동 콘텐츠를 가져온 것(source_title)이라 통계 표본이 아니다.

**[champion] 챔피언 통계 (Units)** — https://www.metatft.com/units

- 보여주는 것: 챔피언별 항목.
- 티어(S~D)
- 평균 등수, 승률, 톱4 비율
- 빈도(%)와 보드 수
- 코스트, 특성, 인기 아이템
- UX: - 표/차트 전환, 컬럼 클릭 정렬(react-table)
- 검색 문법: '/' 또는 ',' 는 OR, '&'·'+' 는 AND, '!' 는 NOT, 괄호 중첩. 특성명이나 '4코스트' 같은 검색도 된다.
- 코스트 필터
- 공통 필터 패널
- 엔드포인트: `https://api-hc.metatft.com/tft-stat-api/units?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustment=true` (확인됨)
- 핵심 필드: **응답** (6KB)
- results[71]{unit:'DA_18_Ahri', places[8]: 1~8등 보드 수}
- games[{patch:'18.2', b_patch_version, count}], updated(ms), tft_set, queue_id
- filter_adjustment{override_applied, rank_filter, sample_size}

**클라이언트 계산** (번들에서 확인)
- n = Σplaces
- avg = Σ(i·places[i-1]) / n
- top4 = Σplaces[0..3] / n
- win = places[0] / n
- 빈도 = n / games[0].count
- 티어: v = 4.5 − avg. S > .3, A > .1, B > −.1, C > −.3, 나머지 D.

예) DA_Lux18_Base: avg 3.45, top4 68.6%, win 26.2%, 빈도 11.7%.

변형 확인: queue=1160(더블업), days=1, patch=18.1&b_patch=d, permit_filter_adjustment=false 모두 200.
- 인증: 불필요 / 표본·갱신: - 기본(3일, 플래티넘+, 18.2): games.count 7,859,448. 100초 뒤 다시 부르니 7,862,536이라 실시간 누적을 확인했다. sample_size는 약 6.77M이고 두 값의 차이는 설명되지 않았다.
- days=1: 1,051,712
- MASTER+·보정 off: 225,368
- 더블업 3일: 863,592
- 사이트 한국어 문구에 5분마다 업데이트한다고 적혀 있다.

**[champion] 챔피언 상세** — https://www.metatft.com/units/:id (링크는 '/units/'+unit_lookup[x].unit)

- 보여주는 것: - 성급·아이템 수별 성과
- 아이템 단품 성과와 3아이템 빌드 성과
- 스테이지별 배치 칸 빈도와 상대 승률
- 일별·시간별 평균 등수와 빈도 추세
- 이 챔피언이 들어가는 덱
- 증강 탭: 데이터 없음
- UX: - 탭: 아이템, 빌드, 배치, 추세, 덱
- 빌드 필터: 성급(tier), 아이템 수(num_items), 유물 포함 여부(artifact_count=0)
- 추세: 시간별(최대 2주) / 일별(최대 4개월) 전환
- 엔드포인트: `https://api-hc.metatft.com/tft-stat-api/unit_detail_overall?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustment=true&unit=DA_18_Aphelios | https://api-hc.metatft.com/tft-stat-api/unit_detail_items?{F}&unit=DA_18_Aphelios&artifact_count=0 | https://api-hc.metatft.com/tft-stat-api/unit_positions2?{F}&unit=DA_18_Aphelios | https://api-hc.metatft.com/tft-stat-api/unit_trends?queue=1100&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustment=true&unit=DA_18_Aphelios&time=daily | https://api-hc.metatft.com/tft-stat-api/unit_augments?{F}&unit=DA_18_Aphelios` (확인됨)
- 핵심 필드: **unit_detail_overall**
- levels[12]{places[8], lvl_items:[성급, 아이템수]}, dates[{day,patch,places}], games[{day,count}]
- position[28]{position:'cell_N', count, win}, round_win[]
- unit=TFT18_Aphelios로 부르면 빈 배열이 온다.

**unit_detail_items** (36KB)
- items[39]{itemName:'DA_Guinsoos<플레이어>blade-1', places}
- builds[258]{buildNames:'A|B|C', places, total}
- item_games, build_games, artifact_count

**unit_positions2**
- stages{'1'..'8':{cell_N:{count, frequency, relative_winrate}}}

**unit_trends**
- data[153]{patch, time, count, ...}
- days·patch 파라미터는 빼고 보낸다. time은 daily 또는 hourly.

**unit_augments**
- results[{augments:'', avg_place, wins, count}]
- 증강별 행이 없다.
- 인증: 불필요 / 표본·갱신: - 기본 필터를 따른다(아펠리오스 아이템 표본 962,225 보드).
- unit_trends는 2026-05-15(패치 16.10)부터 일별 153행이고 sample_size는 14.86M.
- 5분 갱신 문구가 있다.

**[trait] 특성 (Traits)** — https://www.metatft.com/traits , https://www.metatft.com/traits/:slug , https://www.metatft.com/traits/:slug/comps

- 보여주는 것: - 특성·활성 단계별 티어, 평균 등수, 승률, 톱4, 빈도
- 상세: 함께 활성화된 특성, 함께 쓰인 유닛, 추가 특성별 성과
- 해당 특성 덱 목록
- UX: - 특성별 묶음 보기 / 단계별 보기 토글(groupTraits), 정렬
- 상세에서 특성·유닛 조건을 추가 필터로 지정(trait, units 파라미터)
- /comps 하위 탭
- 엔드포인트: `https://api-hc.metatft.com/tft-stat-api/traits?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustment=true | https://api-hc.metatft.com/tft-stat-api/trait_detail?{F}&trait=DA_18_Blossom | https://data.metatft.com/lookups/trait_mapping.json` (확인됨)
- 핵심 필드: **traits**
- results[91]{trait:'DA_18_Adaptor_1', places[8]}, games, filter_adjustment
- 티어: v = 4.5 − avg. S > .5, A > .25, B > 0, C > −.25.

**trait_detail** (23KB)
- overall[{places}]
- traits[91]{traits:'DA_18_Blossom_1', count, places}
- units[71]{units:'DA_18_Sett', count, places}
- extra_traits[89]{extra_traits, count, places}, games
- 선택 파라미터: trait(쉼표로 여러 개), units(쉼표)

**trait_mapping.json**
- {슬러그: apiName} 121개. 예: adaptor → DA_18_Adaptor. TFT16·TFT17 항목이 섞여 있다.
- 인증: 불필요 / 표본·갱신: - 기본 필터 games.count 6,766,472, trait_detail games 7,868,920
- 5분 갱신 문구
- trait_mapping은 Cache-Control max-age=120

**[item] 아이템 (Items)** — https://www.metatft.com/items , https://www.metatft.com/items/:apiName , /items/artifact|support|emblem|radiant

- 보여주는 것: - 아이템별 티어, 평균 등수, 승률, 톱4, 빈도
- 상세: 일별 추세, 착용 유닛 순위(유닛 평균 대비), 완성 스테이지별 승률, 유닛·스테이지 조합
- 역검색용: 아이템 → 상위 5 유닛, 유닛 → 상위 10 아이템
- UX: - 아이템 종류 필터: 일반, 유물, 지원, 상징, 찬란
- 정렬, 검색, 산점도(ItemsScatter 청크)
- 공통 필터
- 엔드포인트: `https://api-hc.metatft.com/tft-stat-api/items_matches?queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustment=true | https://api-hc.metatft.com/tft-stat-api/item_detail?{F}&itemName=DA_InfinityEdge | https://api-hc.metatft.com/tft-stat-api/item_stage_detail?{F}&item=DA_InfinityEdge | https://api-hc.metatft.com/tft-comps-api/unit_items_processed` (확인됨)
- 핵심 필드: **items_matches**
- results[142]{itemName:'DA_*', places[8]}, games
- 티어: v = (4.5 − avg + p) × 빈도^i. S > .3, A > .2, B > .1, C > 0.
- p와 i: 일반 p=.5, i=1 / 상징 p=.125, i=.25 / 유물·지원·찬란 p=0, i=0

**item_detail**
- dates[{itemName, day, patch, places}], games
- units[68]{unit, places}, unit_games, units_overall[71]

**item_stage_detail** (37KB)
- stage[{stage, count, win}], stage_games, units_stage[422]{unit, items, stage, count, win}

**unit_items_processed** (68KB, 필터 없음)
- units{DA_*:{count, place, avg, pick, items[10]}}
- itemNames{DA_*:{count, avg, pick, units[5]}}
- overall.count, region_patch_adjustment

파라미터 없는 tft-stat-api/items도 200이다(results 142, games 2행).
- 인증: 불필요 / 표본·갱신: - 기본 필터 games.count 6,778,848
- unit_items_processed는 필터 없이 2,686,224 보드 기준이고 클라이언트가 1시간마다 다시 받는다
- 5분 갱신 문구

**[augment] 증강 티어 리스트 (Augments)** — https://www.metatft.com/augments

- 보여주는 것: 에디터 한 명(META Spencer)이 수동으로 만든 S~D 증강 티어다. 통계가 아니다.
- 태그: 전투, 경제, 아이템, 성장, 특성
- 희귀도: 실버, 골드, 프리즘
- '업데이트 N분 전' 표시
- UX: - 태그 필터
- 라이브/PBE 세트 전환(?set=pbe)
- 증강 이름·설명은 사전(TFTSet18_latest_{lang}.json)과 합쳐 보여 준다
- 엔드포인트: `https://api-hc.metatft.com/tft-stat-api/augments_tiers` (확인됨)
- 핵심 필드: **augments_tiers** (60KB)
- content.author{gameName, platform, puuid}
- content.content.tierList[{label:'S'..'D', color, content[{id:'DA_PandorasBench', type:'augment'}]}]
- content.content.tags: 이전 세트 키 1,196개
- content.updated_at, content.content_id:'1b860280', tier_list_set:'TFTSet18'
- 등급별 개수: S 24 / A 84 / B 129 / C 21 / D 0
- 선택 파라미터: tft_set

**증강 이름·희귀도**
- data.metatft.com/lookups/TFTSet18_latest_ko_kr.json의 augments[257]{apiName, name:'대출 선지급+', rarity, manual_tags}

**증강 통계는 어디에도 없다** (모두 직접 호출해 확인)
- tft-stat-api/unit_augments: augments='' 1행
- tft-stat-api/augment_unit_detail?{F}&augment=DA_PandorasBench: results []
- comp_details.augments: aug='' 1행
- Riot 경기 JSON(matches3/KR_*): augments 필드 없음
- Overwolf 앱 경기 JSON: 32라운드 모두 augments와 overwolf_augments가 비어 있음
- 추정 경로 tft-stat-api/augments?{F}: HTTP 500
- 번들의 https://api.metatft.com/augments: 404
- 인증: 불필요 / 표본·갱신: - 표본 없음(수동 콘텐츠)
- updated_at 2026-09-15T11:11:58Z, created_at 2026-08-07
- 갱신은 비정기

**[tierlist] 티어 리스트 / 프로 덱 티어** — https://www.metatft.com/tier-lists , https://www.metatft.com/tier-lists/{augments-tier-list|items-tier-list|champions-tier-list}/{content_id} , https://www.metatft.com/pro-comps

- 보여주는 것: - 사용자·에디터가 만든 증강·아이템·챔피언 티어표(S~D와 메모)
- 과거 세트에는 Anomalies, Power Ups 타입도 있었다
- 프로 선수(예: SOLOGESANG)의 덱 티어표
- 통계 페이지의 S~D는 서버 값이 아니라 클라이언트 공식이다
- UX: - 키보드 편집기: S/1, A/2 …로 등급 지정, n으로 메모
- 저장하면 공유 URL 생성
- 세트별로 타입 추가
- 엔드포인트: `https://api.metatft.com/tft-usercontent/id/1b860280 | https://api-hc.metatft.com/tft-stat-api/pro-comps` (확인됨)
- 핵심 필드: **tft-usercontent/id/{content_id}**
- content_id, author{platform, puuid, gameName, tagLine}
- metadata{tierListType:'Augments', title, setKey:'TFTSet18'}, tags, visibility:'public', created_at, updated_at
- content{tierList[{label, color, content[{id, type}]}], notes}

**pro-comps** (166KB)
- content_id, author{riotid, region}, metadata{title}
- content{comps[{data{$ref:'#/comp/uuid'}, tier:'S'}], tier_list:true}
- refs[21]{content_id, metadata, content(덱 본문)}, ranked{rating, num_played}

번들에만 있고 호출하지 않은 경로: tft-usercontent/list?self=true, /new, POST /id/{id}(withCredentials, anonymous_write_key)
- 인증: 읽기는 필요 없다. 다만 usercontent 응답이 mt_session_id 세션 쿠키를 설정한다. 작성과 '내 목록'은 세션·로그인이 필요한 것으로 보이며 확인하지 않았다. / 표본·갱신: - 수동 콘텐츠
- pro-comps updated_at 2026-09-15T08:23Z
- 공개 목록을 한꺼번에 조회하는 엔드포인트는 찾지 못했다

**[profile] 전적 검색 / 플레이어 프로필** — https://www.metatft.com/match-history , https://www.metatft.com/player/kr/랄라붕-KR1

- 보여주는 것: - 현재·최고 티어, 세트별 티어 이력, LP 변화 그래프, 서버 순위(rank/total)
- 등수 분포
- 최근 경기 목록: 등수, 유닛·성급·아이템, 특성, 로비 평균 티어, 패치, 게임 시간
- 경기 상세: 참가자 8명의 보드와 티어·서버 순위
- MetaTFT 앱으로 기록한 경기
- UX: - 라이엇 ID 검색. 태그가 없으면 후보 목록을 보여 준다.
- 최근 검색 기록(localStorage)
- 링크 복사(?match=&tab=&round=)
- 갱신 버튼(refresh_by_riotid): 서버 작업을 일으켜서 호출하지 않았다
- 로비 스카우팅, 게임 중 배지
- 엔드포인트: `https://api.metatft.com/public/profile/lookup_by_riotid/KR/%EB%9E%84%EB%9D%BC%EB%B6%95/KR1?source=full_profile&tft_set=TFTSet18 | https://api.metatft.com/public/profile/rating_changes/KR/%EB%9E%84%EB%9D%BC%EB%B6%95/KR1?queue=1100 | https://matches3.metatft.com/KR_8382256353.json | https://api.metatft.com/public/search/%EB%9E%84%EB%9D%BC%EB%B6%95 | https://api.metatft.com/public/promotion_thresholds/latest` (확인됨)
- 핵심 필드: **lookup_by_riotid** (211KB)
- summoner{id, puuid, summoner_region:'kr', riot_id, profile_icon_id, summoner_level, last_refreshed, is_profile_hidden}
- ranked{num_games, rating_text:'DIAMOND IV 0 LP', rating_numeric, peak_rating, peak_rating_numeric, timestamp}
- rating_history{TFTSetN:{'1100':{...}}}, ranked_rating_changes[]
- matches[]{placement, riot_match_id, match_timestamp, queue_id, tft_set, avg_rating, patch, game_duration, match_data_url, summary{level, last_round, units[{character_id, tier, itemNames}], traits, augments[](빈 값), player_rating, player_ids}}
- app_matches[]{uuid, match_data_url}, replays, ranked_season_stats{'1100':{total, placements[8]}}, server_rank{rank, total}

**rating_changes**
- rating_changes[109]{num_games, rating_text, rating_numeric, created_timestamp, tft_set_name, queue_id}

**matches3 경기 JSON**
- Riot 형식 metadata, info.participants[8]{placement, level, units, traits, companion, riotIdGameName, riotIdTagline, win}
- metatft 확장 _metatft{patch, patch_resolution{server, client_version, live_at}, participant_info[8]{ranked, server_rank, mmr}}

**public/search**
- data[{region, riot_id, rating, rating_num_played, profile_icon_id}]
- 사이트는 encodeURIComponent(이름+'#')를 보낸다. 내 호출에서는 '#'이 URL 조각으로 처리돼 빠진 채로 갔다.

**promotion_thresholds/latest**
- 17개 지역 [{region, challenger_size, grandmaster_size, master_size, challenger_threshold, grandmaster_threshold, timestamp}]
- 인증: 불필요. api.metatft.com public/* 의 CORS 허용 출처는 https://www.metatft.com으로 제한되지만 브라우저에만 해당하고 네이티브 앱과는 무관하다. curl 기본 UA로도 200. / 표본·갱신: - 개인 단위
- 조회 시점의 최신 데이터다. 마지막 경기(2026-09-15T11:22Z)가 바로 반영돼 있었다.
- 경기 JSON은 immutable 캐시
- promotion_thresholds timestamp는 2026-09-16T02:45Z

**[other] 리더보드 / 급상승 플레이어 / 프로** — https://www.metatft.com/leaderboard/:server , https://www.metatft.com/rising , https://www.metatft.com/pros

- 보여주는 것: - 서버별 상위 1000명: 티어, LP, 게임 수, 평균 등수(place_sum/num_played), 승률, 최근 20경기 LP 변화, 이번 패치 성적, 플레이 스타일
- 프로 선수 목록(pro_name, pro_points)
- 급상승 플레이어(기간 A→B 레이팅 변화, 등수 분포)
- UX: - 서버 선택(global 포함), 큐 선택(/leaderboard/:queue/:server)
- 100명 단위 페이지네이션
- 프로 링크(?queue=pro)
- 엔드포인트: `https://api.metatft.com/tft-leaderboard/v2/kr?offset=0&limit=100 | https://api.metatft.com/tft-leaderboard/v2/pro?offset=0&limit=200 | https://api-hc.metatft.com/tft-comps-api/rising` (확인됨)
- 핵심 필드: **leaderboard**
- meta{total:1000, offset, limit}
- data[{player_id, summoner_region, riot_id, puuid, rating, rating_numeric, num_played, rank, stats{num_played, place_sum, wins, RecentResult{num_played, wins, place_sum, lpChange, avg_similarity, topCarries, ItemData}, currentPatchResult{...}, appMatches}}]
- 번들 URL 끝에 붙는 추가 쿼리(${ie})는 확인하지 못했다

**pro**
- total 96, 행에 pro_name, pro_points가 더해진다

**rising**
- results{players[100]{riot_id, summoner_region, num_games_a, rating_a, num_games_b, rating_b, rating_change, placements[8]}, updated}, tft_set, cluster_id
- 인증: 불필요 / 표본·갱신: - KR 1,000명
- rising 내부 updated 2026-09-15T11:07Z로, 조회 약 50분 전이다. 갱신 주기는 확인하지 못했다.
- 리더보드는 조회 시점 기준

**[livegame] 라이브 게임 조회 / 랭커 관전 (Spectate)** — https://www.metatft.com/spectate/:server (및 프로필의 게임 중 배지)

- 보여주는 것: - 프로필 주인이 게임 중일 때 게임 중 표시와 로비 참가자
- 상위 랭커의 진행 중 게임 목록과 관전 실행(데스크톱 앱 연동)
- UX: - 서버 선택
- 같은 플레이어는 5분 안에 다시 조회하지 않는다(queried_time)
- 엔드포인트: `https://api.metatft.com/tft-spectate/summoner_by_puuid/kr/{puuid} | https://api.metatft.com/tft-spectate/top_players?region=kr` (미확인)
- 핵심 필드: **summoner_by_puuid**
- 게임 중이 아니면 HTTP 200에 '{}'(2바이트)가 온다.
- 테스트 계정과 KR 리더보드 1~15위 puuid를 모두 넣었지만 전부 '{}'였다. 그래서 게임 중일 때의 응답 필드는 확인하지 못했다.
- 번들 코드는 data.gameMode=='TFT'와 participants[].puuid를 읽는다. Riot spectator active-game 형식으로 추정한다.

**top_players**
- region=kr, region=global 모두 {length:0, data:[]}

**데스크톱 앱 전용** (호출하지 않음)
- 관전 실행 tft-spectate/launch/{platformId}_{gameId}/...
- 리플레이 launch_replay/{match_id}
- tft-spectate/record/latest_matches는 200이지만 matches []
- 인증: 조회는 필요 없다. 관전 실행에는 MetaTFT 데스크톱 앱이 필요하다. / 표본·갱신: 실시간 조회다. 테스트 시점에는 표시할 데이터가 없었다.

**[livegame] 인게임 앱 데이터 (승률 예측·보드 강도·라운드 기록)** — https://www.metatft.com/download , https://www.metatft.com/win-chance

- 보여주는 것: - 게임 중: 상점 알림, 증강·아이템 추천, 로비 스카우팅, 라운드별 승률 예측(TF.js 모델)
- 경기 후: 라운드별 보드, 벤치, 상점, 리롤, 데미지, 보드 강도 기록
- 웹은 다운로드 안내와 결과 열람만 한다
- UX: Overwolf 데스크톱 오버레이다. 웹 프로필의 app_matches로 기록을 볼 수 있다.
- 엔드포인트: `https://api-hc.metatft.com/tft-stat-api/percentiles | https://data.metatft.com/model_lookupsEA_TFTSet18.json | https://matches3.metatft.com/027f9376-cff3-48f9-a114-93c6a919e682.json` (확인됨)
- 핵심 필드: **percentiles** (93KB)
- percentiles[79]{queue, last_round, board_strength(JSON 문자열 속 배열), damage}
- overall_percentiles[2], updated

**model_lookupsEA_TFTSet18.json** (72KB)
- trait_encodings, trait_mapping, trait_offset, augment_tag_encodings, augment_lookup[257]{rarity, manual_tags}, rarity_values, emblems
- 번들이 'model_lookupsEA_'+세트명으로 조합하는 파일명이다. 세트명 인자를 TFTSet18로 넣어 200을 확인했다.
- 모델 가중치 WinRateModelImproved_BoardStrengthEA_TFTSet18/model.json은 호출하지 않았다.

**앱 경기 JSON** (767KB)
- stage_data(32라운드){board, bench, shops, item_bench, damage_tracker, actions, metrics, augments, charms, winrate_info}
- match_metrics{rerolls, scouting_time, first_unit_star, first_3_item_carry, first_antiheal_item}
- comp_cluster{comp_id:423007, comp_name, unit_importance{DA_*:{play_rate, item_score}}}
- board_strengths{'3-2':{strengths[8], matchups}}
- 인증: 조회는 필요 없다. 앱 경기 데이터는 Overwolf 앱 사용자 경기에만 생긴다. / 표본·갱신: - percentiles는 클라이언트가 24시간마다 다시 받는다
- 모델 lookup last-modified 2026-09-15T08:57Z
- 앱 경기 JSON은 immutable

**[other] 탐색기 (Data Explorer)** — https://www.metatft.com/explorer

- 보여주는 것: 유닛·특성·아이템 조건을 조합한 임의 표본의 통계.
- 총 게임 수, 평균 등수, 톱4, 승률, 등수 분포
- 유닛별 분해
- UX: - 조건 빌더
- 같은 조건은 5분 캐시
- 오래된 요청 취소(AbortController)
- 공통 필터
- 엔드포인트: `https://api-hc.metatft.com/tft-explorer-api/total?formatnoarray=true&compact=true&queue=1100&patch=current&days=3&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&permit_filter_adjustment=true&compact=true | https://api-hc.metatft.com/tft-explorer-api/units_unique?formatnoarray=true&compact=true&{F}` (확인됨)
- 핵심 필드: **total**
- data[{total_games, win_percentage, top4_percentage, avg_placement, placement_count[8]}], filter_adjustment

**units_unique** (48KB)
- data[613]{units_unique:'DA_18_Ahri-1', placement_count[8]}
- -N 접미사의 의미는 확인하지 못했다

기타
- 번들에는 level?, rank?, server?, tft-explorer-predictions 경로도 있다(호출하지 않음).
- 쿼리 규칙은 QueryMaker와 같고 formatnoarray=true, compact=true가 항상 붙는다.
- 인증: 불필요 / 표본·갱신: 기본 필터에서 total_games 6,781,264.

**[other] 추세 (Trends)** — https://www.metatft.com/trends

- 보여주는 것: 패치 전후 일자별 유닛 평균 등수와 빈도 변화.
- UX: - 대상 전환(target)
- 유닛일 때 tier, num_items 선택
- 차트
- 엔드포인트: `https://api-hc.metatft.com/tft-stat-api/trends?queue=1100&rank=CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM&target=units` (확인됨)
- 핵심 필드: - results[568]{day, day_unix, patch, b_patch_version, unit, places[8]}
- games[8]{day, day_unix, patch, count}, target
- patch, days, permit_filter_adjustment는 번들 코드가 빼고 보낸다
- target=units만 호출해 봤다. items·traits 대상은 확인하지 않았다.
- 인증: 불필요 / 표본·갱신: 최근 8일 일별이다. 하루 표본은 약 2.26M 보드.

**[other] 게임 데이터 사전 / 인게임 보상 및 확률 (Tables) / 한국어 UI** — https://www.metatft.com/tables , https://www.metatft.com/tables/:tableName

- 보여주는 것: - 상점 확률, 경험치, 연승·연패 수입, 스테이지, 증강 등장 라운드, 매력(charm) 상점, 드롭 테이블 같은 게임 상수 표
- 모든 페이지가 쓰는 이름·설명·아이콘 사전(한국어 포함)
- UX: 표 선택, 세트 선택. 사이트 언어는 locales로 바뀐다.
- 엔드포인트: `https://data.metatft.com/lookups/TFTSet18_latest_ko_kr.json | https://data.metatft.com/lookups/TFTSet18_latest_en_us.json | https://data.metatft.com/lookups/pbe_TFTSet18_tables.json | https://data.metatft.com/locales/ko_kr.json` (확인됨)
- 핵심 필드: **TFTSet18_latest_{lang}.json** (1.37MB)
- 언어 코드는 en_us, ko_kr 등. g4 매핑으로 en_gb→en_us, es_ar→es_mx 변환
- units[81]{apiName:'TFT18_Ahri', characterName, assetNames['DA_18_Ahri'], name:'아리', en_name, cost, traits, traitApiNames, role, stats, ability{name, desc, variables}, recommendedItems}
- traits[36]{apiName:'DA_DravenUniqueTrait18', name:'현상금 추적자', desc, effects, units}
- items[148]{apiName, name:'대천사의 지팡이', desc, composition, tags}
- augments[257]{apiName, name, rarity, manual_tags}
- armory_items[12], charms[370], encounters[20], unitAssetNames{DA_*:TFT18_*}, roles, roleData, augmentTiers, extras

**pbe_TFTSet18_tables.json** (613KB)
- shopOdds, expPerLevel, winStreakIncome, stages, augmentRounds, charmShop, lootTables, armory 등
- latest_TFTSet18_tables.json은 403(AccessDenied)이라 사이트도 pbe_로 폴백한다

**locales/ko_kr.json** (179KB)
- navigation(조합, 탐색기, 전적 검색, 인게임 …)
- stats(평균 등수, 승률, 순방 확률, 선택률 …), filters, comps

기타: unit_descriptions.json은 2022년 Set 7 데이터라 쓸 수 없다.
- 인증: 불필요. Cloudflare 캐시 HIT, ACAO *. / 표본·갱신: - 정적 파일, Cache-Control max-age=120
- last-modified: 사전 2026-09-11, tables 2026-09-14, 번역 2026-06-28

**[other] 공통 통계 필터 / 표본·패치 메타데이터** — https://www.metatft.com/units (모든 통계 페이지의 필터 패널)

- 보여주는 것: - 필터 패널의 패치 목록
- 서버·랭크·큐별 게임 수
- 현재 세트와 PBE·부활·이벤트 큐 설정
- UX: - 필터 변경이 Redux와 localStorage StatsFilters에 저장되고 모든 통계 페이지에 공유된다
- 표본이 부족하면 서버가 랭크 필터를 완화할 수 있다(permit_filter_adjustment)
- 엔드포인트: `https://api-hc.metatft.com/tft-stat-api/patch | https://api-hc.metatft.com/tft-stat-api/games?days=7 | https://api-hc.metatft.com/tft-stat-api/tft_set_config | https://api-hc.metatft.com/tft-comps-api/latest_cluster_id` (확인됨)
- 핵심 필드: **patch**
- {patch:'18.2', b_patch_version:'', full_padded_patch:'0018.0002', count:30946417, start:'2026-09-09T20:39:41Z'}

**games?days=7**
- games[{day, srq:[서버, 랭크, 큐], patch:[패치, b_patch], count}]
- 서버 17종: BR1 … VN2, PBE1, LOLTMNT01
- 랭크 12종: RANKUNKNOWN, UNDEFINED 포함
- 큐: 1090, 1100, 1160, 1220, 3000, 6110, PBE
- 패치 쌍: (18.1,''), (18.1,'d'), (18.2,''), (18.3,'')

**tft_set_config**
- {live{id:23, name:'TFTSet18'}, pbe{name:'TFTSet18', queue_id:'1090'}, revival{TFTSet15, 6100}, event{TFTSet16, 1210}}

**latest_cluster_id**
- {updated, tft_set:'TFTSet18', cluster_id:423}
- 인증: 불필요 / 표본·갱신: - 전 세계(전 큐·랭크 합계) 하루 5.07M~6.33M 보드, KR 하루 0.83M~1.17M 보드
- 패치 18.2 시작 후 30.9M
- 호출할 때마다 값이 늘어난다

**[other] 그 밖의 메뉴 (확인 안 함): 팀 빌더, 나의 덱 리스트, 초반 빌드, PBE·더블업·초고속 덱, VOD·리플레이, 대회 데이터, 보들, 장인 랭킹, 과거 세트 메커닉, 스트림 오버레이** — https://www.metatft.com/team-builder , /comp-lists , /early-comps , /pbe-comps , /double-up-comps , /hyper-roll-comps , /soul-brawl-comps , /tft-vods , /tft-replays , /tournament-data , /boardle , /onetricks , /hyper-roll-onetricks , /god-tiers , /anomalies , /powerups , /legends , /headliners , /exalted , /anima-squad-weapons , /piltover-modules , /loaded-dice , /tacticians , /tome-of-traits , /charms , /stream-overlay , /twitch-extension , /guides , /new-set , /revival-set , /accounts , /patreon

- 보여주는 것: - 팀 빌더(덱 코드, protobuf spec), 사용자 덱 목록, 초반 보드 추천, PBE·모드별 덱
- VOD·리플레이 라이브러리, 대회 기록, 미니게임(보들), 장인 랭킹
- 과거 세트 메커닉 티어, 스트리머 오버레이
- UX: 확인하지 않았다(번들 라우트와 sitemap 기준).
- 엔드포인트: 없음 (미확인)
- 핵심 필드: **번들에서 뽑았고 호출하지 않은 경로**
- api.metatft.com/tft-usercontent/comp, comp_list, comps?self=true(로그인)
- data.metatft.com/protobuf/teambuilder-spec.json
- api.metatft.com/tft-early-comps/comps_overview, comps_full
- api.metatft.com/tft-pbe-comps/comps, unit_items
- api.metatft.com/tft-vods/latest, search_prompts, id/{id}
- api.metatft.com/public/esports/tournaments, public/pro_players
- api.metatft.com/tft-boardle/daily, random-matchup
- api-hc.metatft.com/tft-comps-api/onetricks
- api-hc.metatft.com/tft-stat-api/god_tiers, anomaly_tiers, powerup_tiers, legend_tiers, headliner_units, exalted, animasquad_tiers, piltover-modules, positions
- data.metatft.com/lookups/companions_lookup.json

**호출해 본 것**
- tft-stat-api/units?queue=1160(더블업): 200
- tft-spectate/record/latest_matches: 200, 빈 목록
- tft-stat-api/charms?{F}: 404

스트림 오버레이(OverlayWidget 청크)는 public/profile과 tft_set_config를 쓴다.
- 인증: 팀 빌더 저장, 덱 리스트, 계정은 RSO·Patreon·Overwolf 로그인(세션 쿠키)이 필요한 것으로 보인다. 나머지 읽기는 필요 없을 것으로 추정한다. / 표본·갱신: 확인하지 않았다.

</details>

## 인게임 연동 조사

- **판정**: `partial`
- **TFT 패키지**: `com.riotgames.league.teamfighttactics (PBE: com.riotgames.league.teamfighttactics.pbe). Google Play 스토어 목록 URL의 id 파라미터로 확인했다(웹 검색). 이 PC에는 연결된 안드로이드 기기가 없어(adb devices 결과 비어 있음, AVD에도 TFT 미설치) pm list packages 로는 확인하지 못했다.`

**앱 실행·게임 감지** — [결론] 같은 폰에서 'TFT 앱이 전면에 떠 있는가'는 UsageStatsManager 로 감지할 수 있다. '로비인가, 매치 중인가'는 기기가 없어 확인하지 못했다.

[방법 - AOSP 소스와 문서로 확인, 실기기 테스트는 안 함]
- 이미 있는 OverlayService(foregroundServiceType=specialUse)에서 2~5초마다 UsageStatsManager.queryEvents(now-10s, now) 를 부른다.
- packageName 이 TFT 패키지이고 eventType 이 ACTIVITY_RESUMED 이면 진입으로 본다. ACTIVITY_RESUMED 는 값이 1이고 API 29 이상이며, 옛 이름은 MOVE_TO_FOREGROUND 다.
- ACTIVITY_PAUSED 나 ACTIVITY_STOPPED(값 23) 이면 이탈로 본다.
- Event.getClassName() 으로 액티비티 클래스명을 얻는다. AOSP UsageEvents.java 에서 확인했다.

[권한]
- android.permission.PACKAGE_USAGE_STATS 를 매니페스트에 선언한다(tools:ignore=ProtectedPermissions).
- 사용자가 설정의 사용 기록 접근(Settings.ACTION_USAGE_ACCESS_SETTINGS)에서 직접 켜야 한다. 허용 여부는 AppOpsManager OPSTR_GET_USAGE_STATS 로 확인한다.
- AOSP main 의 UsageStatsService.queryEventsHelper 가 적용하는 처리는 네 가지뿐이다: 인스턴트앱 난독화, 바로가기·Locus 숨김, 알림 난독화. UserUsageStatsService 를 grep 해도 패키지 가시성 필터는 없었다. 따라서 이벤트 조회에는 <queries> 가 필요 없다(소스 기준).
- 반면 설치 확인이나 실행(getPackageInfo, getLaunchIntentForPackage)은 가시성 필터 대상이다. <queries><package android:name="com.riotgames.league.teamfighttactics"/></queries> 한 줄이면 된다.
- QUERY_ALL_PACKAGES 는 Play 제한 권한이므로 쓰지 않는다.
- Play 의 민감 권한 정책 페이지에는 PACKAGE_USAGE_STATS 항목이 없었다(페이지 확인).

[한계]
- Android 11 이상에서 기기가 잠겨 있으면 queryEvents 가 null 을 준다. 이벤트는 며칠만 보관된다(AOSP javadoc).
- 로비와 매치 구분은 TFT 가 둘에 서로 다른 액티비티를 쓸 때만 가능하다. 이것은 미검증이다.
  - 확인 방법: 실제 폰의 로비와 매치에서 각각 adb shell dumpsys activity activities | grep -i teamfight 와 adb shell dumpsys usagestats | grep -i teamfighttactics 를 비교한다.
  - 단일 액티비티라면 UsageStats 로는 앱 전면 여부까지만 알 수 있다(추정).
- 배터리: 시스템이 이미 기록한 이벤트의 짧은 구간만 읽으므로 부담이 작을 것으로 본다(추정). 대신 포그라운드 서비스를 계속 유지해야 하고, 제조사 배터리 최적화의 영향은 측정하지 못했다.

[대안 평가]
- ActivityManager.getRunningTasks 는 deprecated 이고 자기 태스크 위주만 준다. getRunningAppProcesses 는 문서상 디버깅이나 프로세스 관리 UI 용이다(AOSP javadoc). 다른 앱 감지에 쓸 수 없다.
- NetworkStatsManager 의 UID별 실시간 통계(getMobileUidStats, getWifiUidStats)는 @hide @SystemApi 라 일반 앱이 쓸 수 없다(AOSP 확인). 트래픽으로 매치 중인지 추정하는 방법도 막혀 있다.
- 접근성 서비스:
  - 기술적으로는 TYPE_WINDOW_STATE_CHANGED 로 폴링 없이 패키지와 클래스 변화를 받을 수 있다.
  - Play 는 장애인 보조 목적이 아니면 Play Console 선언과 앱 안의 명시적 고지·동의를 요구한다. 더 좁은 API 가 있으면 그것을 쓰라고도 한다. UsageStats 로 대체되므로 심사 거절 위험이 높다.
  - 게임은 GL 표면에 렌더링하므로 노드 트리에 플레이어 이름이 없을 가능성이 크다(추정).
- MediaProjection 화면 캡처 + OCR: 세션마다 사용자 동의가 필요하다. FGS 타입 mediaProjection 을 선언해야 하고, Android 15 QPR1 이상은 상태바 칩이 뜨고, 화면이 잠기면 중지된다(Android 문서). 무겁고 정책상 민감해 권하지 않는다.

| 소스 | 엔드포인트 | 인증 | 확인 | 반환 |
|---|---|:---:|:---:|---|
| Riot 공식 spectator-tft-v5 | `GET https://kr.api.riotgames.com/lol/spectator/tft/v5/active-games/by-puuid/{puuid}` | 필요 | ✓ | (Riot 문서 기준, 미검증) 게임 중이면 CurrentGameInfo 를 준다: gameId, gameStartTime, gameLength, gameQueueConfigId, platformId, observers.encryptionKey, participants[puuid, ri… |
| metatft tft-spectate (www.metatft.com/as… | `GET https://api.metatft.com/tft-spectate/summoner_by_puuid/{summoner_region}/{puuid}  (region 은 metatft 표기 kr, na1, euw1 등. puuid 는 metatft… | 불필요 | ✓ | (코드 기준, 미관측) Riot CurrentGameInfo 를 그대로 넘기는 형태로 보인다. 코드가 쓰는 필드: gameMode, gameId, platformId, gameQueueConfigId, gameStartTime, observers.encryptionKey, partici… |
| metatft 리더보드의 live 필드 | `GET https://api.metatft.com/tft-leaderboard/v2/{kr\|na1\|euw1...}?offset=0&limit=50 , GET https://api.metatft.com/tft-leaderboard/v2/pro?offs… | 불필요 | ✓ | 행마다 riot_id, puuid(metatft 키), rating(예 'CHALLENGER I 1730 LP'), rating_numeric, num_played, stats 가 있다. live 는 코드에만 있고 현재 응답에는 없다. 리더보드에 오른 사람만 해당하므로 일반 사용자의 게… |
| lolchess.gg (dak.gg, tft.dakgg.io) 프로필 인… | `GET https://tft.dakgg.io/api/v1/rpc/spectator/{shard}/{gameName}-{tagLine}  (shard 는 kr, na1, euw1, jp1 등. 첫 응답이 {"retryAfter":1000} 이면 그 시… | 불필요 | ✓ | (코드 기준, 미관측) spectator 에 gameId, gameQueueConfigId, gameStartTime, encryptionKey, platformId, shard, participants[] 가 있다. 참가자마다 세 가지가 붙는다: summoner{puuid, gameN… |
| lolchess.gg 천상계 관전 목록 (전체 라이브 게임 존재 여부 지… | `GET https://tft.dakgg.io/api/v1/spectate-matches/{platform}  (kr, euw1, na1, jp1, sg2 등 플랫폼 코드. euw, na, jp 로 쓰면 400)` | 불필요 | ✓ | (코드 기준) data[] 항목마다 spectateActiveGame{gameId, gameStartTime, gameQueueConfigId, encryptionKey, platformId}, summoner, summonerLeague 가 있다. 시작 40분이 지난 게임은 클라이언트… |
| metatft 공개 프로필 LP 변동 (게임 종료 감지용, 진행 중 게임… | `GET https://api.metatft.com/public/profile/rating_changes/KR/{gameName}/{tagLine}?queue=1100` | 불필요 | ✓ | rating_changes[]{num_games, rating_text, rating_numeric, created_timestamp, tft_set_name, queue_id}. num_games 가 늘면 새 랭크 게임이 끝났다는 뜻이다(탈락 후 LP 반영, 최대 약 15분 지연). |
| metatft 매치 상세 (지난 게임 로비 8명) | `GET {matches[0].match_data_url}  예 https://matches3.metatft.com/KR_8382256353.json . match_data_url 은 GET https://api.metatft.com/public/pr… | 불필요 | ✓ | 게임이 끝난 뒤 한 번 호출로 로비 8명의 라이엇 ID, 최종 등수, 티어 스냅샷(metatft 수집 시각 기준)을 받는다. 진행 중 게임에는 해당하지 않는다. |

<details><summary>검증 증거 전문</summary>

**Riot 공식 spectator-tft-v5** — 키 없이 호출하면 HTTP 401 {"status":{"message":"Cannot process request apikey or authorization header is empty","status_code":401}} 이다. 실제 puuid 를 넣어도, featured-games 나 asia account-v1 을 불러도 같다. 가짜 키를 X-Riot-Token 헤더나 api_key 쿼리로 넣으면 401 "Unknown apikey" 다. 키가 없어 게임 중이거나 아닐 때의 실제 응답은 확인하지 못했다. Riot 개발자 정책상 배포하는 바이너리에 키를 넣을 수 없으므로, 앱에서 직접 부르려면 백엔드가 필요하다. 샘플: C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad/bench/ingame/riot_nokey_responses.txt

**metatft tft-spectate (www.metatft.com/assets/main-B0QEiPU-.js 번들의 PlayerScouting 코드)** — [게임 아닐 때]
- 랄라붕#KR1 은 HTTP 200 본문 {} 이다(약 0.7초). kr 과 KR 이 같고, Referer 가 없어도 된다.
- 없는 puuid('AAAA')도 200 {} 라서 잘못된 ID 와 게임 아님을 구분할 수 없다.

[게임 중 검증 시도]
- KR 리더보드 상위 20명: 11:53Z 1회.
- EUW 상위 15명: 11:56Z 1회.
- KR 상위 50명: 12:01~12:32Z 에 5분 간격 7라운드, 70회.
- 합계 65명, 105회 모두 200 {} 였다. 게임 중 응답은 실제로 보지 못했다.

[번들 코드에서 읽은 흐름]
- 응답의 gameMode 가 'TFT' 이면 라이브 패널을 띄운다.
- participants 각자에 대해 public/profile/lookup_by_puuid 를 부르고, units_distribution 통계를 붙인다.

샘플: .../bench/ingame/mt_spectate_notingame_kr.json, .../bench/ingame/live_poller_log.txt
코드 발췌: .../bench/ingame/_bundle_spectate_ctx.txt, .../bench/ingame/_bundle_ctx4.txt
(... 는 C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad)

**metatft 리더보드의 live 필드** — 엔드포인트는 동작한다. kr, euw1, na1 top50 과 pro 96명 모두 200 이며, 약 1~1.4초에 39~77KB 다. 번들 코드는 행에 live.match_id, live.encryption_key, live.game_start_time 이 있으면 '게임 중' 아이콘과 관전 링크를 그린다. 그러나 받은 246행 모두 live 필드가 없었다. 샘플: .../bench/ingame/lb_kr_offset_0_limit_50.json, .../bench/ingame/lb_pro_offset_0_limit_200.json

**lolchess.gg (dak.gg, tft.dakgg.io) 프로필 인게임 정보** — [게임 아닐 때]
- 랄라붕-KR1 은 1차 200 {"retryAfter":1000}, 1.3초 뒤 2차 200 {"shard":"kr","name":"랄라붕-KR1"} 이다. spectator 키가 없다.
- KR 1위 선수 이름, 12:33Z 재확인 모두 같았다.
- 이름 대신 puuid 를 넣으면 error "tft:spectator-worker:summoner" 가 오고, 오류 응답에 호출자 IP 가 되돌아온다.

[게임 중] 응답은 관측하지 못했다. 필드는 프로필 페이지 청크 코드([[...tab]]-c452cc366cb7fc51.js)에서 확인했다.

[참고]
- /summoners/kr/랄라붕-KR1/spectate 는 451 {"message":"not working"} 이다. 이것은 녹화 요청 mutation 이라 라이브 조회와 무관하다.
- 같은 계정의 puuid 가 lolchess(11dLI...)와 metatft(JVSsZ...)에서 다르다. puuid 는 API 키별 암호화 값이라는 뜻이다(확인).

샘플: .../bench/ingame/lolchess_rpc_spectator_poll.txt, 코드 .../bench/ingame/lolchess_js/

**lolchess.gg 천상계 관전 목록 (전체 라이브 게임 존재 여부 지표)** — [호출 시점]
- 11:55Z 12개 플랫폼.
- 12:01~12:32Z KR 7회.
- 12:32Z 15개 플랫폼 재확인.
모두 HTTP 200 {"meta":{"updatedAt":..., "count":0},"data":[]} 였다. ?main=1 도 같다.

[해석] updatedAt 이 호출할 때마다 1~2분 전으로 갱신되므로 수집기는 돌고 있다. 그런데 전 플랫폼에서 라이브 게임이 0건이다.

[같은 시각 교차 확인] Riot 상태 API(lol.secure.dyn.riotcdn.net/channels/public/x/status/kr1.json, euw1, na1)는 incidents 와 maintenances 가 비어 있다.

샘플: .../bench/ingame/lolchess_spectate_matches_kr.json 외 플랫폼별 파일, .../bench/ingame/riot_status_kr1.json

**metatft 공개 프로필 LP 변동 (게임 종료 감지용, 진행 중 게임 아님)** — [호출 결과]
- 랄라붕#KR1: 200, 17KB, 0.85초, 109건.
- queue 파라미터가 없으면 500, puuid 경로는 404 다.

[갱신 주기] 109건의 created_timestamp 가 15분 격자에 몰린다. 15분 주기 안에서 2분 지점 50건, 7분 지점 37건이고, 연속 두 기록의 최소 간격이 정확히 15.0분이다. metatft 가 LP 스냅샷을 약 15분마다 찍는다고 본다.

[직전 판 KR_8382256353, 매치 JSON 기준]
- 생성 10:44:02Z.
- 본인 6등 탈락 약 11:13:29Z (time_eliminated 1767초).
- 로비 종료 11:22:51Z.
- metatft LP 기록 11:22:19Z, 탈락 약 9분 뒤.

[지연 분포] 최근 25건의 'LP 기록 시각 - 매치 종료 시각'은 -10.3~+13.6분이다. 음수는 LP 가 로비 종료 전, 탈락 시점에 반영되기 때문이다.

[강제 갱신] refresh_by_riotid 는 {"tier":"Tier2","status":"not_queued"} 만 반환해 동작을 확인하지 못했다.

샘플: .../bench/ingame/mt_rating_changes_self_q1100.json, .../bench/ingame/mt_refresh_by_riotid_self_try0.json

**metatft 매치 상세 (지난 게임 로비 8명)** — 200, 25KB, 1.1초.
- info: gameCreation, game_datetime(=생성+game_length, 즉 종료 시각), game_length, queueId, tft_set_core_name, endOfGameResult, participants 8명.
- 참가자 키: riotIdGameName, riotIdTagline, puuid, placement, time_eliminated, last_round, level, traits, units.
- _metatft.participant_info 8명: riot_id, puuid, ranked{rating_text, rating_numeric, created_timestamp, num_games, queue_id}, server_rank.
샘플: .../bench/ingame/mt_match_data_sample.json

</details>

**로비 참가자 정보** — [게임 중 - 원래 목표] 참가자 목록을 주는 곳은 관전(active-game) 데이터뿐인데, 지금은 어디서도 0건이라 불가능하다. 관전 데이터가 다시 열리면 두 경로가 있다.

(a) lolchess
- rpc/spectator/{shard}/{gameName}-{tagLine} 한 번(+retryAfter 재호출 1회)으로 참가자마다 summonerLeagues(tier, rank, LP)와 recentMatchStats(최근 10판 등수)가 붙어 온다. 코드 기준이고 미관측이다.
- 호출 부담이 가장 작다. 대신 비공개 API 이고, 본인 라이엇 ID 가 필요하다.

(b) metatft
- summoner_by_puuid 를 1회 부른 뒤, 웹과 같은 방식으로 참가자마다 public/profile/lookup_by_puuid/{region}/{puuid} 를 부른다(코드 확인).
- 본인 계정 실측: 기본 응답 63KB/1.2초(최근 40판), source=app_profile 도 63KB, source=full_profile 은 211KB/1.6초(112판).
- 서버가 gzip 을 하지 않는다(Accept-Encoding gzip,br 요청에도 Content-Length 62997).
- 7명 × 63KB ≈ 440KB, 8요청. 순차로 부르면 약 9초, 병렬이면 2초 안팎이다(추정).
- 필요한 riot_id, ranked.rating_text, matches[].placement 는 기본 63KB 응답에 모두 있다.

[게임 후 - 지금 가능, 실측]
1. lookup_by_riotid/KR/{name}/{tag} 를 source 없이 부른다(63KB). 새 matches[0].riot_match_id 와 match_data_url 을 얻는다.
2. 매치 JSON(25KB)을 1회 받는다. 8명의 riotIdGameName#riotIdTagline, 등수, _metatft.participant_info[].ranked.rating_text 가 한꺼번에 온다.
3. 합계 2요청, 약 90KB 다. 개인별 최근 등수까지 원하면 참가자당 lookup_by_puuid 63KB 가 추가된다.

[식별자] puuid 는 서비스(API 키)마다 다르다. 같은 계정이 metatft 에서는 JVSsZ..., lolchess 에서는 11dLI... 였다. 소스끼리 연결할 때는 라이엇 ID(gameName#tagLine)를 기준 키로 써야 한다.

[Riot TFT 정책 요약 - developer.riotgames.com/docs/tft, 원문 인용 아님]
- 게임 중(로딩 화면 포함) 오버레이는 상대의 챔피언이나 플레이를 추적·예측할 수 없다.
- 이 금지에는 개인과 로비의 집계 통계도 포함된다.
- 상대의 모스트 챔피언, 시너지, 증강은 게임 중 표시할 수 없다.
- 비승인 사례: 스카우팅, 동적 실시간 정보를 주는 앱.
- 승인 사례: 게임 전에 알 수 있는 정적 데이터를 보여주는 오버레이(RSO 포함 프로덕션 키).

따라서 게임 중에 상대 티어를 보여주는 것은 회색지대다. 상대의 최근 등수나 평균 등수를 게임 중에 보여주는 것은 위반 소지가 크다. 게임 후 카드로 보여주는 것이 안전하다.

**권장 구조** — 1. 기기 안에서 TFT 감지 (지금 구현 가능)
- 설정 화면에 사용 기록 접근(PACKAGE_USAGE_STATS) 온보딩을 추가한다.
- 기존 OverlayService(specialUse FGS)가 2~5초마다 queryEvents 로 com.riotgames.league.teamfighttactics 의 ACTIVITY_RESUMED 와 PAUSED/STOPPED 를 본다.
- TFT 가 전면에 오면 오버레이 칩을 자동으로 띄운다. 전면을 떠나면 '게임 후 처리'를 예약한다.
- 매니페스트에 <queries><package android:name="com.riotgames.league.teamfighttactics"/></queries> 를 추가한다. QUERY_ALL_PACKAGES 는 쓰지 않는다.
- 디버그 빌드에서 이벤트 className 로그를 남긴다. 실기기에서 로비와 매치 액티비티가 다른지 먼저 확인한 뒤, 매치 시작 감지에 쓸지 결정한다.

2. 진행 중 게임과 로비 (기본 비활성, 원격 플래그)
- ActiveGameSource 인터페이스를 둔다.
- collector(GitHub Actions)가 lolchess spectate-matches/kr 의 count, 또는 metatft 상위 N명의 spectate 를 확인한다. 결과를 data/decks.json 이나 별도 flags 파일에 liveSpectateAvailable 로 기록한다.
- 앱은 이 값이 true 이고 TFT 가 전면일 때만 60초 간격으로 조회한다.
  - 1순위 lolchess rpc/spectator: 1~2요청으로 8명 티어.
  - 2순위 metatft summoner_by_puuid + 참가자별 lookup_by_puuid.
- 정책 때문에 게임 중에는 티어만 보여주고, 최근 등수는 게임 후에 보여준다.
- 지금 값은 false 가 맞다.

3. 게임 종료 감지와 지난 게임 로비 (지금 구현 가능)
- TFT 가 전면을 떠난 뒤, 또는 전면이 25분 넘게 이어진 뒤부터 metatft rating_changes/KR/{name}/{tag}?queue=1100 (17KB) 을 3~5분 간격으로 최대 30분 폴링한다.
- num_games 가 늘면 '내 게임 종료'로 판정한다. 지연은 탈락 후 LP 반영 기준 최대 약 15분이다.
- 이어서 lookup_by_riotid(source 생략, 63KB)를 3~5분 간격으로 불러 새 riot_match_id 가 보이는지 본다.
- 보이면 match_data_url(25KB)을 1회 받아 '지난 게임 로비' 카드를 만든다(8명 라이엇 ID, 등수, 티어).
- 한 판에 대략 6~12요청, 150~300KB 다.
- 현재 앱의 ?source=full_profile(211KB) 대신 source 를 생략(63KB)해도 ranked 와 최근 40판이 온다(실측). 오버레이용 조회는 가벼운 쪽을 쓴다.

4. 식별자
- 라이엇 ID 를 기준 키로 둔다.
- puuid 는 조회한 서비스별로 따로 캐시한다(metatft 와 lolchess 값이 다르다).

5. Riot 공식 키 경로는 보류한다
- 키를 앱에 넣을 수 없으므로 프로덕션 키와 백엔드가 필요하다. TFT 는 오버레이 용도 승인 조건(RSO, 정적 데이터)도 까다롭다.
- TFT 관전 데이터가 꺼져 있다면 키가 있어도 진행 중 게임은 받지 못할 가능성이 높다(추정).
- 공식화가 필요해지면 match-v1 과 league-v1 기반의 종료 감지·로비 티어부터 옮긴다.

6. 채택하지 않음
- 접근성 서비스: Play 선언·고지 부담이 있고 UsageStats 로 대체된다.
- MediaProjection OCR: 세션마다 동의, 상태바 칩, 무겁고 정책상 민감하다.

**한계** — 1. 핵심 막힘: 지금 진행 중인 TFT 게임을 볼 수 있는 경로가 없다
- 조사 시간: 2026-09-15 11:49~12:33Z (KST 20:49~21:33).
- 키 없는 세 경로 모두 진행 중 TFT 게임이 0건이었다.
  - lolchess 관전 목록: 최대 15개 플랫폼, 여러 차례.
  - metatft 리더보드 live 필드: 246행 모두 없음.
  - metatft spectate: KR/EUW 상위 65명에 105회.
- Riot 상태 API 에 장애나 점검 공지는 없었다.
- 공식 TFT 18.1 패치노트(2026-08-25)는 Set 18 출시 시점에 라이브 친구 관전이 없고 장기적으로 추가할 계획이라고 적고 있다.
- Riot developer-relations 이슈에는 Set 18 TFT 관전 보고가 없다(GitHub 검색).
- 가장 그럴듯한 설명은 Set 18 라이브에서 TFT 관전(active-game) 데이터가 꺼져 있다는 것이다. 이는 추정이며, Riot 키가 없어 공식 API 로 직접 확인하지 못했다.
- 그래서 게임 중 응답의 형태(metatft, lolchess)는 번들 코드에서 읽은 것이고 실제로 관측하지 못했다.
- 테스트 계정은 조사 직전에 게임을 마쳤다(로비 종료 11:22:51Z). 조사 중에는 게임 중이 아니었다.

2. 비공개 API 의존
- metatft 와 lolchess 엔드포인트는 문서가 없고, 언제든 바뀌거나 막힐 수 있다. 두 서비스 약관은 확인하지 않았다.
- metatft spectate 는 잘못된 puuid 에도 {} 를 줘서 오류와 게임 아님을 구분할 수 없다.
- lolchess rpc 는 retryAfter 를 거치는 2단계 호출이고, 오류 응답에 호출자 IP 를 되돌려준다.
- metatft 응답은 압축되지 않는다(프로필 63KB, full_profile 211KB).

3. 종료 감지 지연
- metatft LP 스냅샷은 약 15분 주기다(109건 분석).
- 매치 상세는 로비 전체가 끝난 뒤에만 생긴다. 이번 판은 탈락 9.4분 뒤에 로비가 끝났다.
- 매치가 metatft 프로필에 들어오기까지의 지연은 측정하지 못했다.
- refresh_by_riotid 는 status not_queued 만 반환해 강제 갱신을 확인하지 못했다.

4. 기기 쪽 미검증
- 연결된 안드로이드 기기가 없어 네 가지를 실측하지 못했다: UsageStats 이벤트 수신, TFT 액티비티 구성(로비와 매치 구분 가능 여부), 배터리 영향, 제조사 백그라운드 제한. 모두 AOSP 소스와 문서에 근거한 판단이다.
- 잠금 상태에서는 queryEvents 가 null 이다.
- 모바일 TFT 게임이 PC 와 같은 관전 대상에 들어가는지는 확인하지 못했다.

5. 정책
- Riot TFT 게임 무결성 규칙상 게임 중에는 상대의 보드 추적과 개인·로비 집계 통계, 모스트 표시가 금지된다. 동적 실시간 정보 앱은 비승인 사례다.
- 로비 참가자 정보는 게임 후 표시가 안전하다.
- 접근성 서비스는 Play 선언과 명시적 고지·동의 대상이다.

6. 식별자
- puuid 는 서비스별 암호화 값이라 소스 사이에 호환되지 않는다.

## 버그 진단

### 6·8·9렙 배치표

**원인** — 결론: 좌표·ID 문제가 아니다. 레벨 탭에 연결한 원본 필드와 탭 이름이 틀렸고, 레벨 보드에는 최종 보드에만 있는 캐리·3성 정보가 없다. 여기에 탭 전환 시 초상화가 늦게 오는 문제가 겹친다.

1) [직접 확인] lol.qq 의 hero_location_l6/l8/l9 는 6·8·9레벨 보드가 아니다.
- 26/26 덱에서 (hero_id, chess_id, location, numStar, equipment_id) 가 완전히 같다. l6 == y21_early_heros, l8 == y21_metaphase_heros, l9 == hero_location(최종).
- 실제 레벨은 초반 needLevel_early 4(10덱)·5(14)·6(1)·7(1), 중반 needLevel_middle 5(2)·6(7)·7(9)·8(8), 최종 needLevel 7(2)·8(11)·9(12)·11(1)이다. 라운드는 모든 덱이 2-3 / 4-3.
- lol.qq 사이트는 hero_location_lN 을 어떤 페이지 컴포넌트·템플릿에서도 그리지 않는다. 前期过渡(y21_early_heros), 中期过渡(y21_metaphase_heros), 阵容站位(hero_location) 세 보드만 보여 준다.
- 그런데 fetch_decks.py build_deck(L360-364)이 이 필드에 '6'/'8'/'9' 키를 붙이고, DeckDetailScreen(L168-173)이 '${level}렙' 으로 띄운다.
- 그 결과 '6렙' 탭에는 레벨 4~5 초반 보드(4~5명), '8렙' 탭에는 레벨 5~8 중반 보드, '9렙' 탭에는 최종 보드 복사본이 나온다. 최종 레벨이 7·8·11인 덱도 똑같다.

2) [직접 확인] 레벨 보드와 y21_* 원소에는 is_carry_hero 키가 아예 없다.
- 키 집계: hero_location 236/236, l6·l8·l9 0.
- build_unit(L261)이 is_carry_hero 로만 캐리를 정하므로 레벨 보드는 carry=true 가 0건이다(최종 25건).
- '9렙' 과 '최종' 탭의 화면 차이는 이 캐리 테두리 하나뿐이다.
- 사이트 파서 ct() 도 is_carry_hero 로만 캐리를 표시해 초·중반 보드에는 캐리가 없다.

3) [직접 확인] 3성 표시
- l6·l8 의 numStar 는 전부 1이다(136/136, 196/196). 그중 24개·53개는 level_3_heros 에 들어 있다.
- 사이트 파서 ct() 는 원본 numStar 를 버리고, 모든 보드에서 level_3_heros 포함 여부로 별을 다시 계산한다(numStar = arrStar3Chess.indexOf(chess_id) !== -1 ? 3 : 1).
- 앱은 numStar>=3 일 때만 ★★★ 를 그리므로 6/8렙 탭에는 3성 표시가 한 번도 나오지 않는다.
- 최종 보드도 numStar 와 level_3_heros 가 6건 어긋난다.
- 사이트 템플릿이 별을 실제로 어떻게 그리는지는 확인하지 못했다.

4) [직접 확인, 모든 탭 공통] pet 누락
- chess_type 'pet' 90건(DA_18_IronbarkTree 24, LifeRoot 21, Willump 13, ElderwoodGuardian 2)은 hero_id 가 빈 문자열이고 id 는 chess_id 에 있다.
- build_board(L269-270)의 if e.get('hero_id') 가 이들을 모든 보드에서 버린다.
- 사이트는 chess_id = hero_id || chess_id 로 칸을 채우므로 앱에서는 그 칸이 빈다(최종 15, l6 13, l8 17).
- 최종 탭에도 생기는 문제라 레벨 탭만의 원인은 아니다.

5) [라이브러리 소스·데이터로 확인, 기기 미확인] 탭 전환 시 빈 칸
- Coil 2.7.0 AsyncImagePainter.updateRequest 는 model 이 바뀌면 onStart 에서 State.Loading(placeholder=null) 로 상태를 바꾼다. 그래서 탭을 바꾸면 새 아이콘이 올 때까지 HexCell 에 코스트색 육각형만 보인다.
- 초반·중반에만 나오는 챔피언(덱당 평균 3.6, 최대 9명)은 목록 카드와 최종 탭에 없어 메모리 캐시에 없다.
- 6/8렙 탭을 처음 열 때 이 초상화들을 네트워크로 받아야 해서, 그동안 칸이 빈 채로 보인다(속도는 B 참고).

원인이 아님 [직접 확인]:
- 좌표: 모든 보드가 1기반(행 1~4, 열 1~7), 범위 밖 0건.
- 행열 뒤바뀜 없음(l9 가 최종과 동일).
- 같은 칸 중복 0건.
- catalog 에 없는 id 0건. catalog 의 icon/cost/name 과 units 불일치 0건.
- 아이콘 파일 정상: 140개 전부 챔피언·아이템 128x128, 특성 32x32이고 컨택트시트로 육안 확인했다. DA_CrimsonRaptor18 만 tileIcon 이 teamplanner_splash 경로지만 128x128 정상 초상화이고 최종 탭에도 나온다.

미확인: 실제 폰 화면은 보지 못했다. HexCell 의 return@Box 조기 반환이 탭 전환 때 Compose 에서 어떻게 동작하는지는 이 PC 에 Android SDK·JDK 가 없어 실행해 보지 못했다. 다만 9렙 탭과 최종 탭은 데이터가 같고 캐리 테두리 말고는 같은 코드 경로를 타므로, 이것이 레벨 탭만의 원인일 가능성은 낮다고 본다.

**근거** — 재료 (scratchpad C:/Users/ksj17/AppData/Local/Temp/claude/C--claude-project-tft-deck-app/bb917a36-0519-4bf4-a42c-9a2e185792cb/scratchpad):
- 원본 lineup_detail_total.json(26덱, 오늘 수신)
- 스크립트 raw_schema.py, board_audit.py, equality.py, board_table.py
- lol.qq 스크립트 qqjs/(index.html, tftlib.es5.umd.js, tfth5lib_v1_umd.js, page-*.js 18개)
- data/decks.json 과 android/app/src/main/assets/decks.json 은 같은 파일이다(sha256 f73ccb496fc058d2).

[원본 필드 비교: 최종 vs 레벨 보드]
- location: 모든 보드가 'row,col' 1기반, row 1~4, col 1~7. 행·열 분포도 early=l6, mid=l8, final=l9 로 같다.
- hero_id 형식: DA_18_X 716건, DA_X18 268건, DA_X18_Y 42건, DA_18_X_Y 20건, 빈 문자열 90건(=pet, chess_id 에 id). 보드 종류와 무관하다.
- chess_type: 'hero' 와 'pet' 만 있다. 레벨 보드에만 있는 타입은 없다.
- 키: is_carry_hero 는 hero_location 에만 있다(236건). l6/l8/l9 와 y21_* 에는 0건.
- 동일성: 26/26 덱에서 l6==y21_early_heros, l8==y21_metaphase_heros, l9==hero_location.
- 실제 레벨: needLevel_early 4~7, needLevel_middle 5~8, needLevel 7~11.

[기대(사이트 렌더) vs 앱이 그릴 배치]
챔피언과 칸 위치는 모든 탭에서 사이트와 일치한다. 아래 '차이' 열만 다르다.

덱 14266 장로 드래곤 (초반 Lv5 / 중반 Lv8 / 최종 Lv8)
| 탭 | 실제 원본 | 사이트 표시 | 칸 수 사이트/앱 | 차이 |
| 6렙 | y21_early_heros | 前期过渡 Lv5·2-3 | 5/5 | 없음. 요릭1,5 바위게2,4 케이틀린4,5 조약돌4,6 불타는묘목4,7 |
| 8렙 | y21_metaphase_heros | 中期过渡 Lv8·4-3 | 8/8 | 없음 |
| 9렙 | l9(=최종 복사) | 표시 안 함 | 8/8 | 최종과 같은데 장로 드래곤(3,5) 캐리 테두리만 빠짐 |
| 최종 | hero_location | 阵容站位 | 8/8 | 없음(드레이븐 4,7 3성 일치) |

덱 14325 아펠리오스 (Lv4 / Lv6 / Lv9, pet 있음)
| 탭 | 사이트 표시 | 칸 수 사이트/앱 | 차이 |
| 6렙 | 前期过渡 Lv4 | 6/4 | pet 2칸 비어 있음(1,4 IronbarkTree, 4,3 LifeRoot) |
| 8렙 | 中期过渡 Lv6 | 9/6 | pet 3칸 비어 있음(1,4·1,6 IronbarkTree, 4,3 LifeRoot) |
| 9렙 | 표시 안 함 | 13/9 | pet 4칸 비어 있음, 아펠리오스(4,6) 캐리 테두리 없음 |
| 최종 | 阵容站位 | 13/9 | pet 4칸 비어 있음(1,1·1,7 IronbarkTree, 1,6 ElderwoodGuardian, 4,3 LifeRoot) |

덱 14346 아칼리 (Lv4 / Lv5 / Lv7)
| 탭 | 칸 수 사이트/앱 | 차이 |
| 6렙(실제 Lv4, 4명) | 4/4 | 오른1,4·아칼리3,1·카밀3,2 가 사이트에선 3성, 앱은 별 없음 |
| 8렙(실제 Lv5, 5명) | 5/5 | 같은 3명 3성 누락 |
| 9렙(실제 최종 Lv7, 7명) | 7/7 | 아칼리 캐리 테두리 없음 |
| 최종 | 7/7 | 없음 |

덱 14319 조약돌 (Lv4 / Lv6 / Lv11)
| 탭 | 칸 수 사이트/앱 | 차이 |
| 6렙(Lv4) | 4/4 | 조약돌4,7 3성 누락 |
| 8렙(Lv6) | 6/6 | 돌거북1,3·조약돌4,7 3성 누락 |
| 9렙(최종 Lv11, 11명) | 11/11 | 조약돌 캐리 테두리 없음 |
| 최종 | 11/11 | 없음 |

[전체 집계]
- 레벨 보드 carry=true 0건(최종 25건)
- numStar=1: l6 136/136(level_3_heros 포함 24), l8 196/196(포함 53)
- pet 누락: 최종 15, l6 13, l8 17
- 6/8렙 탭에서 새로 받아야 하는 초상화: 덱당 평균 3.6, 최대 9

[사이트 렌더 규칙 근거]
- index.html 템플릿: earlyMapChessList → '前期过渡', metaphaseMapChessList → '中期过渡', finalMapChess → '阵容站位'. CSS class 는 'position'+location.replace(',', '-').
- tftlib ct(): chess_id=hero_id||chess_id, numStar 는 arrStar3Chess(level_3_heros) 기준, is_carry_hero → booleanCarry.
- hero_location_l1..10 은 $hero_location_lN 으로 파싱만 한다. page-*.js 18개, global-component.js, main.js, api.js, index.html 어디에서도 쓰지 않는다(grep 0건).

[앱 코드 근거]
- fetch_decks.py L360-364: l6/l8/l9 → '6'/'8'/'9'
- fetch_decks.py L269-270: hero_id 없는 원소 제거
- fetch_decks.py L249-252: numStar 사용
- fetch_decks.py L261: is_carry_hero 만 캐리로 인정
- Models.kt L77: boardLevels
- DeckDetailScreen.kt L168-173: '${level}렙' 라벨
- DeckDetailScreen.kt L353-377: slotsFor/toSlot
- Components.kt L122-160: HexCell
- Deck.early/mid 는 앱 어디에서도 읽지 않는다(grep).

**해결** — [수집기 collector/fetch_decks.py]
1. 단계 원본 교체
- build_deck 의 ('6','hero_location_l6')/('8',...)/('9',...) 루프를 없앤다.
- 사이트와 같이 y21_early_heros 와 y21_metaphase_heros 로 단계 보드를 만든다.
- 실제 레벨과 라운드를 함께 싣는다. 예: stages=[{key:'early', level:needLevel_early, round:early_round, units:[...]}, {key:'mid', level:needLevel_middle, round:metaphase_round, units:[...]}]
- 최종은 지금처럼 units 를 쓴다.
- l9 는 최종 복사본이므로 싣지 않는다.
- hero_location_lN 이 early/mid/final 과 다를 때만(현재 0/26) 'Lv N' 단계로 추가하도록 동일성 비교를 넣는다.

2. 별
- level_3_heros 를 split 해 집합으로 만든다.
- 모든 보드에 star = 3 if id in 집합 else numStar 를 적용한다(사이트 ct() 와 같은 규칙).

3. 캐리
- 사이트도 초·중반 보드에는 캐리를 표시하지 않는다.
- 앱 탭 사이의 일관성을 원하면 최종 carryId 와 같은 id 에만 carry=true 를 물려준다(선택).

4. pet
- build_board 필터를 e.get('hero_id') or e.get('chess_id') 로 바꾸고, id=hero_id or chess_id 와 kind=chess_type 을 싣는다.
- 이름·아이콘은 CDragon ko_kr set 18 champions 에 없다(4개 id 모두 없음 확인).
- lol.qq https://game.gtimg.cn/images/lol/act/img/tft/js/chess.js(version 16.18, season 2026.S18)에는 4개 모두 있다. 여기서 catalog 에 넣거나, 앱에서 일반 소환물 칸으로 표시한다.
- deck_code(팀플래너 코드 없음), metatft 유사도, build_index 에서는 pet 을 뺀다.

5. (선택) verify.py 검사 추가
- 단계 보드가 비었거나 레벨 정보가 없는 경우
- 레벨 원본이 early/mid/final 과 같은데 탭으로 추가된 경우

[앱]
6. Models.kt
- Deck.boards/boardLevels 대신 stages: List<Stage(key, level, round, units: List<Placement>)> 를 둔다.
- Placement 에 kind 를 더한다.
- 기본값을 두어 옛 캐시와 호환한다.

7. DeckDetailScreen.kt
- 탭 라벨을 '초반 Lv5·2-3' / '중반 Lv8·4-3' / '최종 Lv8'(deck.finalLevel) 로 만들고 '9렙' 탭을 없앤다.
- slotsFor/toSlot 은 수집기가 채운 star/carry/kind 를 그대로 쓴다.

8. Components.kt HexCell
- AsyncImage 에 코스트색 placeholder(ColorPainter)를 줘서 탭 전환 순간 칸이 비는 것을 없앤다.
- kind=pet 칸은 소환물 표시로 그린다.

9. AppViewModel.kt
- catalogChampion 의 매 호출 List 선형 탐색을 피드별 id→entry Map 으로 바꾼다.
- 피드 로드 직후 stages 에 나오는 챔피언 아이콘까지 선로딩한다(B 해법 7).

10. 데이터 재생성: data/decks.json 을 다시 만들고 android/app/src/main/assets/decks.json 스냅샷도 갱신한다(지금 둘은 같은 파일이다).

바꿀 파일: `C:/claude_project/tft_deck_app/collector/fetch_decks.py`, `C:/claude_project/tft_deck_app/collector/verify.py`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/data/Models.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/ui/screens/DeckDetailScreen.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/ui/components/Components.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/ui/AppViewModel.kt`, `C:/claude_project/tft_deck_app/data/decks.json`, `C:/claude_project/tft_deck_app/android/app/src/main/assets/decks.json`

### 이미지 로딩 속도

**측정** — 측정 환경:
- 이 PC(Windows 11)에서 curl 8.19(Schannel, HTTP/1.1 만 지원)와 Python http.client 로 쟀다.
- Python 은 연결 5개를 유지하는 방식으로 OkHttp 의 호스트당 5 제한을 흉내 냈다.
- 후보 호스트는 모두 ALPN 에서 h2 를 지원한다(확인).
- 폰·모바일망 측정은 하지 않았다.
- 스크립트와 결과물: scratchpad 의 bulk.py, img/raw/(아이콘 140개), img/view/sheet.png

[1] 한 화면의 동시 요청 수 (코드 규칙을 decks.json 에 적용해 계산)
- 덱 목록 DeckCard = 특성 take(4) + 챔피언 전원 + 유닛당 아이템 take(3). 코드상 상한은 4+4N 이다(N=유닛 수, 데이터 최대 11명).
- 실제 카드당 최소 20 / 평균 23.1 / 최대 29 (특성 4.0, 챔피언 8.5, 아이템 10.6)
- 26장 전체: 600요청, 고유 URL 131개
- 첫 화면 3장: 65요청, 고유 45개. Coil 2.7 소스에는 진행 중 요청 중복 제거가 없어 중복 URL 도 동시에 요청된다.
- 덱 상세(verticalScroll Column 이라 화면 밖까지 한 번에 compose): 최소 28 / 평균 33.3 / 최대 43
- 6/8렙 탭 첫 전환 때 추가 초상화: 평균 3.6 / 최대 9
- 앱 전체 고유 아이콘 140개: 챔피언 66(128x128), 아이템 50(128x128, 1개만 64x64), 특성 24(32x32)

[2] raw.communitydragon.org (Cloudflare, CF-RAY POP=NRT 도쿄)
헤더: Cache-Control max-age=3600, ETag, Last-Modified, cf-polished(원본 2617 kB → 서빙 1885 kB, 개당 평균 13.5 kB). Accept: image/webp 를 보내도 image/png 이다.
- TCP 연결 37~44 ms(DNS 포함), TLS 완료 81~99 ms
- 새 연결 요청(아이콘 10개, edge HIT): 123~163 ms
- edge 가 원본에 재검증(cf-cache-status REVALIDATED): 605 ms (1회차 10개 중 2개)
- 연결 재사용: 첫 요청 146 ms, 이후 46~68 ms
- 아이콘 140개, 5병렬(처음 상태): 4.53 s, 요청 중앙값 50 / p90 536 / 최대 626 ms. REVALIDATED 33개, HIT 107개
- 같은 조건 반복(전부 HIT): 1.47 s (중앙값 43 ms)
- 1연결 순차: 6.30 s
- 16병렬: 1.00 s
- 첫 화면 65요청, 5병렬: 카드 1장(22개) 0.40 s, 3장 0.77 s
- If-None-Match 조건부 요청 140개(Coil 이 캐시가 만료되면 보내는 요청): 304 로 1.46 s. 전체 다운로드(1.47 s)와 차이가 없다.
- 관측한 Age 헤더 최대 3531 s(max-age 3600)

[3] cdn.communitydragon.org/latest/game/... : 404(그런 경로 없음, 373~617 ms) → 대안이 될 수 없다.

[4] jsDelivr (x-served-by cache-icn=서울 + FRA shield)
- TCP 6~12 ms
- 새 연결 warm: 27~78 ms. 콜드 첫 요청은 @main version.json 1748 ms, @commit 파일 852 ms, fastly.jsdelivr.net 667 ms
- 연결 재사용: 7.5~13 ms
- 태그 고정 PNG 140개(twemoji@15.1.0, 0.8~1.1 kB. WebP 로 바꾼 아이콘 평균 2.5 kB 와 비슷한 크기), 5병렬: 첫 회 4.93 s(MISS 70개, 개당 약 500 ms), 반복 0.44 s(중앙값 9 ms)
- 캐시 헤더: 태그 ref 는 max-age=31536000, immutable. 브랜치·커밋 ref 는 max-age=604800, s-maxage=43200 (x-jsd-version-type branch)
- 283 kB 단일 파일(identity): 첫 950 ms, 이후 37~41 ms

[5] raw.githubusercontent.com (Fastly ICN 서울, Cache-Control max-age=300)
- PNG 140개, 5병렬: 첫 회 6.89 s(140개 전부 MISS, 중앙값 232 ms), 반복 0.30 s(중앙값 4 ms)
- version.json: MISS 208 ms, 이후 27~31 ms
- 283 kB 단일 파일: 첫 276 ms, 이후 46~56 ms

[6] WebP 변환 (Pillow 12.2, method 6, 140개 합계)
- 서빙 PNG 1884.7 kB
- q80 원래 크기: 447.3 kB
- q80 목표 크기(챔피언 128, 아이템 64, 특성 32 그대로): 344.4 kB. 챔피언 265.6, 아이템 71.4, 특성 7.3 kB. PNG 의 18%, 개당 2.5 kB
- 무손실: 1492.7 kB

[7] 라이브러리 소스 확인 (Maven Central sources jar)
- OkHttp 4.12.0 Dispatcher: maxRequests=64, maxRequestsPerHost=5. promoteAndExecute 는 호스트별 callsPerHost 만 세고, h2 예외가 없다.
- Coil 2.7.0 HttpUriFetcher 는 Call.await()→enqueue 를 써서 이 제한을 그대로 받는다.
- respectCacheHeaders 기본값은 true 다. CacheStrategy 는 max-age 와 Age 헤더를 합쳐 신선도를 계산한다.
- 메모리 캐시 기본값은 memoryClass 의 20%(저사양 기기 15%)다. 계산상 128x128 ARGB 140장 = 9.2 MB.
- 디스크 캐시 기본값은 디스크의 2%다.
- BitmapFactoryDecoder 는 기본 4개까지 병렬로 디코드한다.
- isSizeValid: 샘플링된(inSampleSize>1 또는 inScaled) 캐시 비트맵이 요청 크기보다 작으면 무효로 보고 다시 디코드한다.
- AsyncImagePainter 는 model 이 바뀌면 placeholder=null 로 Loading 상태가 된다.
- 앱에는 커스텀 ImageLoader 가 없다(TftApp.kt).

**원인** — 1) 호스트·경로 [측정]
- 앱이 쓰는 raw.communitydragon.org 는 이 PC 에서 Cloudflare 도쿄(NRT) edge 로 연결된다. 왕복 지연만 약 38 ms 다.
- 서울 edge 인 jsDelivr·GitHub raw 보다 요청 하나가 새 연결 기준 3~4배(123~163 ms 대 27~50 ms), 연결 재사용 기준 5배 안팎(46~68 ms 대 8~13 ms) 느리다.

2) edge 캐시 수명 1시간 [측정]
- CDragon 은 max-age=3600 이라 한 시간 동안 아무도 요청하지 않은 아이콘은 edge 가 원본에 재검증한다. 이 경우 약 600 ms 가 걸린다.
- 첫 대량 측정에서 140개 중 33개가 이랬고, 그래서 4.53 s 가 걸렸다(전부 HIT 이면 1.47 s).

3) 디스크 캐시가 속도에 기여하지 못함 [소스 + 측정]
- Coil 기본값 respectCacheHeaders=true 에서 CacheStrategy 는 서버의 max-age=3600 과 Age 헤더(관측 최대 3531 s)를 합쳐 신선도를 판단한다. 그래서 디스크에 저장된 아이콘도 받은 뒤 0~60분 안에 만료된다.
- 앱을 다시 켜 메모리 캐시가 비면 아이콘마다 조건부 요청이 다시 나간다.
- 그런데 304 재검증(1.46 s)이 전체 다운로드(1.47 s)와 같은 시간이 걸린다. 아이콘이 작아 왕복 지연이 시간을 대부분 차지하기 때문이다.

4) 동시성 제한 [소스 + 측정]
- OkHttp 는 호스트당 5요청만 동시에 보낸다(h2 여도 같다).
- 한 호스트에 목록 카드당 20~29개, 첫 화면 65개, 상세 28~43개 요청이 몰리고, 진행 중 중복 제거도 없어 대기열이 생긴다(5병렬 1.47 s 대 16병렬 1.00 s).

5) 선로딩 없음 [코드 + 소스]
- 초반·중반 보드 전용 초상화(평균 3.6, 최대 9개)는 탭을 열어야 요청된다.
- 그동안 placeholder 가 null 이라 칸이 비어 보인다.

6) 크기별 재디코드 [코드·소스 추론, 기기 미측정]
- 같은 URL 을 아이템 11/19/24 dp, 챔피언 26~46 dp 등 여러 크기로 요청한다.
- 작게 샘플링되어 캐시에 들어간 비트맵은 isSizeValid 에서 더 큰 요청에 대해 무효가 되어 다시 디코드되거나 다시 받는다.

**해결** — 권장안: 아이콘을 자체 호스팅한 WebP 팩 + 커스텀 ImageLoader + 선로딩. 핵심은 아이콘 한 장마다 나가는 네트워크 요청을 없애는 것이다.

1. 수집기에서 미러링 (collector/fetch_decks.py)
- 피드가 참조하는 아이콘(현재 140개)을 CDragon 에서 받아 WebP q80 으로 변환한다(챔피언 128 px, 아이템 64 px, 특성 32 px 원본). 합계 344 kB 로 PNG 1885 kB 의 18%.
- 내용 해시 파일명(data/icons/<sha1 12자>.webp)으로 저장해 URL 이 절대 바뀌지 않게 한다.
- 같은 파일을 한 요청으로 받을 수 있게 data/icons.zip(약 0.35 MB)으로도 묶는다.
- version.json/decks.json 에 iconPack {hash, bytes, count} 를 싣는다.
- decks.json 의 icon 에는 webp 파일명만 적는다.
- 새 아이콘만 받고 변환하도록 해시 캐시를 둔다.

2. 첫 실행
- 현재 팩을 android/app/src/main/assets 에 넣는다. APK 가 약 0.35 MB 늘고 첫 실행부터 아이콘에 네트워크가 필요 없다.

3. 갱신 (DeckRepository.sync)
- version.json 의 iconPack.hash 가 바뀌었을 때만 zip 을 한 번 받아 filesDir/icons/ 에 푼다.
- 서빙은 지금 피드와 같은 raw.githubusercontent.com 을 쓰거나, jsDelivr 를 커밋 SHA·태그로 고정해 쓴다.
  - 측정: 283 kB 단일 파일이 warm 37~56 ms, 콜드 276~950 ms 이고 요청은 한 번뿐이다.
  - 반면 아이콘을 CDN 에 파일별로 두면 저트래픽 앱에서는 edge 가 대부분 콜드다(GitHub 140개 첫 회 6.89 s 전부 MISS, jsDelivr 4.93 s). 파일별 CDN 미러링만으로는 빠르지 않다.
  - jsDelivr @main 은 브랜치 ref 로 s-maxage=43200(12 h) 캐시된다(헤더 확인). 새로 올린 파일이 곧바로 반영되지 않을 수 있으니 @main 대신 커밋·태그로 고정한다(12 h 지연 자체는 미검증).

4. iconUrl (UiUtils.kt)
- 로컬 파일을 먼저 쓴다(File). 팩에 없는 새 아이콘만 네트워크로 받고, 최후에는 CDragon 경로로 떨어진다.

5. ImageLoader (TftApp.kt, API 는 coil 2.7.0 소스에서 확인)
- TftApp 이 coil.ImageLoaderFactory 를 구현한다:
  override fun newImageLoader() = ImageLoader.Builder(this)
    .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
    .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(64L * 1024 * 1024).build() }
    .respectCacheHeaders(false)
    .okHttpClient { OkHttpClient.Builder().dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 }).build() }
    .crossfade(false)
    .build()
- respectCacheHeaders(false): 파일명이 불변이니 1시간마다 304 왕복이 필요 없다. 측정상 304 도 전체 다운로드만큼 느렸다.
- maxRequestsPerHost=16: 측정 16병렬 1.00 s 대 5병렬 1.47 s.
- coil-base POM 이 okhttp 4.12.0 을 compile scope 로 노출하므로 의존성은 추가하지 않아도 된다.

6. 크기 힌트
- 모든 AsyncImage 를 model = ImageRequest.Builder(ctx).data(url).size(종류별 고정값: 챔피언 128, 아이템 64, 특성 32).build() 로 바꾼다.
- 모든 호출 지점이 같은 메모리 캐시 항목을 공유해 isSizeValid 로 인한 재디코드가 없어진다.
- HexCell/UnitCell 에는 코스트색 placeholder 를 준다.

7. 선로딩 (AppViewModel)
- 피드가 Ready 가 되면 catalog.champions/items/traits 와 stages 에 쓰인 아이콘 전체(현재 140개)를 같은 size 로 imageLoader.enqueue 해 메모리 캐시에 올린다.
- 로컬 팩이면 디코드만 일어난다. 네트워크라도 측정상 16병렬 1.00 s, 5병렬 1.47~4.53 s 로 스크롤 전에 끝난다.
- 오버레이 서비스도 같은 싱글턴 ImageLoader 를 쓰므로 함께 이득을 본다.

대안(미러링을 당장 못 할 때 최소 변경)
- 5·6·7만 적용한다(CDragon 유지 + respectCacheHeaders(false) + 디스크 캐시 + 호스트당 16 + 선로딩).
- 두 번째 실행부터는 디스크 디코드만으로 끝난다.
- 하지만 첫 로드는 edge 상태에 따라 1.0~4.5 s(측정)가 걸리고, 도쿄 경로와 CDragon 가용성에 계속 묶인다.

바꿀 파일: `C:/claude_project/tft_deck_app/collector/fetch_decks.py`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/TftApp.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/data/DeckRepository.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/data/Models.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/ui/UiUtils.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/ui/AppViewModel.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/ui/components/Components.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/ui/screens/DeckDetailScreen.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/ui/screens/SearchScreen.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/java/com/tftdeck/reader/overlay/OverlayContent.kt`, `C:/claude_project/tft_deck_app/android/app/src/main/assets/icons.zip`, `C:/claude_project/tft_deck_app/data/icons/`, `C:/claude_project/tft_deck_app/data/version.json`

## 통합 설계 (Fable)

전문은 [design.md](design.md).

- **deck** → lolqq: 합계(metatft 21 : lolqq 18 : lolchess 16)를 뒤집어 lol.qq를 기준으로 한다. 3점 차는 전부 원본 사이트의 모바일 사용성 축에서 났고 우리는 화면을 직접 만들며, 데이터+구현 두 축은 15.5:15로 대등하다. 덱별 증강 성적·칸별 배치 승률·핵심 유닛 성급 비율·티어 5구간·당일 신선도·편집 덱 레벨별 보드는 lol.qq에만 있고, metatft의 8점은 comps_stats 한 엔드포인트의 점수다(comps_data/comp_details는 rank/days/server를 무시하는 고정 모집단, 증강 없음). 통합 목록 = 胜率阵容 그룹(주특성+메인C) → 변형(유닛 집합) 2단 구조에 편집 덱을 자카드≥0.5로 첨부(미첨부는 독립 덱), 数据检索器 lineup_rank(4+/7+, version '')를 자카드≥0.7로 붙여 정밀 수치, metatft 클러스터를 자카드≥0.5로 붙여 글로벌/KR 비교 열과 중국 한정 판정. 등급은 수집기가 베이지안 수축 평균(K=200, 4.5로)에 절대 컷(S≤3.90/A≤4.15/B≤4.40/C≤4.70, n<300은 null)으로 다시 매기고 모든 카드에 표본 라벨. lolchess는 데이터원에서 제외하고 카드 고정 4수치 줄·큰 덱 코드 버튼·캐리 순위·검증 기준만 빌린다.
- **champion** → metatft: 합계와 같음(metatft 24). 표본 최대(플래+ 7일 1,190만 보드), 랭크 임의 조합, KR 서버 단독 조회(server=KR 실적용 확인), places[8] 원시 분포, lolchess와 상관 0.995. 수집기가 places로 평균·TOP4·승률·픽률(n/games)과 등급(4.5−avg: S>.3/A>.1/B>−.1/C>−.3, n<1000 null)을 계산해 champions.json·traits.json에 넣고 앱은 조회만. lol.qq 数据检索器는 '티어 필터(4+/7+) + version '' + unit_s 분모' 조건에서만 모순 0이므로 '중국 서버' 스코프로 보조 표시(Δ는 unit_s 가중 평균 대비 자체 계산, 서버 avg_rank_delta·国服大数据 랭킹 미사용). 특성 키는 (DA_특성, 활성 인원수)로 통일(metatft _N 순번→CDragon effects[N-1].minUnits). lolchess 용어(평균 등수/TOP4/승률/게임 수, '11 나무정령')와 최소 표본 문턱·이름 열 고정·패치 스냅샷만 빌린다.
- **item** → metatft: 합계와 같음(metatft 24.5). 아이템 키 142개가 lol.qq와 완전히 같고 items_matches/item_detail/item_stage_detail/unit_items_processed가 인증 없이 온다. 수집기가 places로 평균·TOP4·승률·픽률과 아이템 등급((4.5−avg+p)×pick^i, 일반 .5/1, 상징 .125/.25, 유물·지원·찬란 0/0)을 계산. 착용자는 metatft item_detail.units 상위 5(글로벌)와 lol.qq tft_equip_rank(ShowHero=1)의 'DA_챔피언#DA_아이템' build_s 상위 5(중국, 인기도만 신뢰) 두 줄. 중국 평균은 범위 검사 통과 시에만. 조합표는 CDragon composition으로 11×11 격자. 기존 아이템→덱 역검색(index.item)은 lol.qq 편집 덱+胜率阵容 캐리 아이템으로 확장. lolchess는 용어·글자 칩·조합표·2줄 카드 패턴만.
- **augment** → lolqq: 합계와 같음(lolqq 15.5). 시즌 18 증강 실측은 lol.qq 胜率阵容 상세 augment_data(덱별 상위 5, use_num, avg_rank, 1_/2_/3_avg_rank)뿐이다. metatft(unit_augments 빈 문자열, augment_unit_detail [], augments_full2 500)와 lolchess(meta-deck-augments 빈 응답, Riot 정책 고지)는 통계가 없다. 전체 랭킹(tft_augment_rank)은 화이트리스트로 폐쇄돼 증강 자체의 순위표는 만들 수 없다고 명시한다. 앱 증강 화면 = lol.qq 덱별 통계(n·평균·단계별 평균, '0'은 결측, 소수부 없는 값은 소표본 흐림, n<300 흐림) + metatft 에디터 티어 S~D(작성자·갱신 시각 표기, 통계와 분리) + metatft ko 사전의 희귀도·태그·한글명 보충 + 작가 추천(hexbuff)/가이드 추천(comp_augment_tiers S) 역색인. 상단에 '중국 골드~에메랄드 하루치, 덱별 상위 5개만' 고지.
- **profile** → metatft: 합계와 같음(metatft 21.5). 정확성은 lolchess와 동일(112/112 경기, LP 로그 108/108), 갱신 사례 확인, 경기 JSON에 8인 티어·LP·MMR, 약관 문제 없음, 이미 앱 ProfileRepository가 사용 중. lolchess는 약관+sync RPC 차단 위험, lol.qq는 KR 계정 조회 불가. 개선: full_profile(211KB) 대신 source 생략(63KB), 오버레이 주기 갱신은 rating_changes(17KB)로, server_rank로 '상위 1.0%', rating_changes 이웃 차이로 판별 ±LP, b패치 구분, 지난 시즌은 '마지막 관측 티어' 라벨, 글자 10sp 이상. 게임 중 상대 정보는 표시하지 않고(Riot 정책) 지난 게임 로비 카드만 게임 후 표시.

### 앱 이름 후보

- **덱마루** — '마루'(꼭대기·으뜸)+덱. 두 글자 조합이라 짧고 발음이 쉬우며 스토어 검색 충돌이 적다. 특정 사이트·상표(롤토체스, 롤체, TFT)를 쓰지 않아 안전하다.
- **중원덱** — 中原(중국 본토)에서 온 덱. '중국 통계를 한국어로'라는 앱의 정체성을 이름에 담는다. 무협 어감이 호불호가 갈릴 수 있다.
- **한눈덱** — 게임 위에 띄워 한눈에 본다는 오버레이 기능과, 아이템 하나로 덱을 역검색하는 사용 흐름을 함께 가리킨다.
- **옥덱(玉덱)** — 앱 테마색(옥색)과 중국의 이미지를 같이 담는다. 두 글자라 아이콘·위젯에 맞다. 발음이 낯설 수 있다.
- **덱나침반** — 어느 덱으로 갈지 방향을 잡아 준다는 뜻. 검색·역검색·티어 참고 기능을 설명한다. 다섯 글자로 조금 길다.
- **협곡 덱 리더(현행)** — 이미 쓰는 이름이라 바꾸는 비용이 없다. 다만 '협곡'은 소환사의 협곡(LoL) 연상이라 TFT와 거리가 있고 세 어절이라 긴 편이다. '롤토체스'(Riot 공식 명칭)와 '롤체'(롤체지지 혼동)는 피한다.

## 공개 전 정리 (2026-09-15)

공개 저장소에 올리기 전에 샘플과 원본 결과를 정리했다.

- 제3자 사이트 페이지 사본(HTML, 페이지 앞부분)은 지웠다. 주소는 이 문서와 README 에 남아 있다.
- lolchess.gg(약관상 복제·제3자 제공 금지)와 tftactics.gg(편집 콘텐츠) 샘플은 값을 지우고 키와 타입만 남겼다. 게임 데이터 id(DA_*, TFT*)만 형식 참고용으로 둔다.
- 다른 플레이어의 라이엇 ID, puuid, 소환사명은 `<플레이어>#<태그>`, `<puuid>`, `<가림>` 으로 바꿨다. 사용자 본인 테스트 계정 이름만 남겼다.
- HTTP 헤더의 쿠키 값은 가렸다.

그래서 목록에 있어도 파일이 없거나 값이 가려진 샘플이 있다. 응답 모양을 다시 봐야 하면 README 의 엔드포인트를 직접 호출하라.
