# API 계약서

> 앱과 서버 사이 기준 문서. 2026-11-02 초안. 바꿀 땐 이 문서부터 고치고 A·B에게 알림.
> 서버가 나오기 전에는 이 문서의 예시로 가짜 응답을 만들어 개발함.

## 공통

**주소**: `https://<도메인>` (개발 서버 `https://dev.<도메인>`). 도메인 확정 후 기입. HTTPS만 사용.

**로컬 서버**: `server/`에서 `docker compose -f docker-compose.dev.yml up --build`. 에뮬레이터는 `adb reverse tcp:14800 tcp:14800` 후 `http://127.0.0.1:14800`. 앱의 cleartext 허용이 127.0.0.1뿐이라 `10.0.2.2`는 막힘. QR·구글 로그인은 폰이 접속해야 하므로 개발 서버 주소로 테스트함.

**형식**: JSON, UTF-8, 필드명 snake_case.

**인증**: `/devices` 외 앱이 부르는 API는 모두 헤더에 기기 토큰을 실음.
```
Authorization: Bearer <device_token>
```

**시각**: ISO 8601, 타임존 오프셋 필수(`2026-11-03T09:00:00+09:00`). 쿼리 문자열에서 `+`는 `%2B`로 인코딩함. 안 하면 공백으로 읽혀 400이 남.

**좌표**: `latitude`, `longitude`. NaviHelper `RouteInfo`와 같은 이름. 카카오의 `x`, `y`는 서버가 바꿔서 줌.

**개인정보**: 서버는 검색어, 좌표, 발화, 일정 내용을 저장하지 않고 로그에도 남기지 않음. 앱도 기기 토큰을 로그에 남기지 않음.

### 에러

모든 에러는 같은 형식. 앱은 `code`로 분기함. `message`는 개발자용이라 화면에 그대로 띄우지 않음.
```json
{ "code": "invalid_token", "message": "토큰이 없거나 유효하지 않음" }
```

| HTTP | code | 뜻 | 앱 동작 |
|---|---|---|---|
| 400 | `invalid_request` | 파라미터 오류 | 앱 버그. 로그 확인 |
| 401 | `invalid_token` | 토큰 없음·무효 | `POST /devices`로 재발급. 구글 연동은 다시 해야 함 |
| 404 | `not_found` | 없는 경로 | 앱 버그. 로그 확인 |
| 405 | `method_not_allowed` | 잘못된 HTTP 메서드 | 앱 버그. 로그 확인 |
| 409 | `not_linked` | 구글 연동 안 됨 | QR 화면 안내 |
| 409 | `reauth_required` | 구글 연동 끊김 | QR 화면 안내 |
| 409 | `already_linked` | 이미 연동됨 | 계정을 바꾸려면 `DELETE /pairing` 후 다시 |
| 429 | `rate_limited` | 요청 과다. `Retry-After` 헤더(초) 있음 | 그 시간만큼 대기 |
| 429 | `daily_limit` | LLM 일일 상한 초과 | STT 결과로 바로 `/search` |
| 500 | `internal_error` | 서버 버그 | 오류 안내, 일정은 캐시 표시 |
| 502 | `upstream_error` | 카카오·구글·LLM 실패나 시간 초과 | 일정은 캐시 표시, 검색은 오류 안내 |

### 제한

앱은 429만 처리하면 됨. 숫자는 초기값이라 바뀔 수 있음.

| API | 제한 |
|---|---|
| `POST /devices` | IP당 시간 20회 |
| `POST /pairing` | 토큰당 분 5회 |
| `GET /pairing/status` | 토큰당 분 30회 |
| `GET /search` | 토큰당 분 30회 |
| `POST /parse` | 토큰당 분 10회, IP당 하루 200회, 서버 전체 하루 2000회 |
| `GET /calendar/events` | 토큰당 분 10회 |
| 전체 | IP당 분 120회 |

IPv6는 주소 하나가 아니라 /64 대역 단위로 셈.

## 사용 가능 목표일

| 날짜 | API |
|---|---|
| 11/5 | `POST /devices`, `GET /search`, `GET /calendar/events`(가짜 일정) |
| 11/7 | `POST /pairing`, `GET /pairing/status`, `DELETE /pairing` |
| 11/10 | `GET /calendar/events`(실제 일정) |
| 11/12 | `POST /parse` |

## 앱이 부르는 API

### POST /devices
앱 첫 실행 때 한 번 호출. 받은 토큰은 Keystore로 보호해 저장함. 만료 없음.

- 인증: 없음
- 입력: 없음
- 출력 201:
```json
{ "device_token": "dvc_8fK2..." }
```
- 토큰은 내용을 해석하지 않는 불투명한 문자열로 다룸. 서버는 해시만 저장하므로 다시 조회할 수 없음.

### GET /search
카카오 키워드 검색 프록시. 결과가 없으면 주소 검색으로 한 번 더 찾으므로, 일정 `location`을 좌표로 바꿀 때도 그대로 넣으면 됨.

찾는 순서. 결과가 나오는 첫 단계에서 멈춤
1. `query` 그대로 키워드 검색
2. 주소 검색. `이름(주소)`면 괄호 안, 구글 지도식 `이름, 대한민국 주소`면 쉼표 뒤(앞의 `대한민국` 제외), 둘 다 아니면 `query` 전체
3. `이름(주소)`나 `이름, 주소` 형태면 이름만으로 키워드 검색

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `query` | O | 검색어, 1~100자 |
| `latitude`, `longitude` | | 현재 위치. 둘 다 주거나 둘 다 생략 |
| `sort` | | `accuracy`(기본) 또는 `distance`. `distance`는 좌표 필수 |
| `size` | | 1~15, 기본 10 |

출력 200:
```json
{
  "places": [
    {
      "id": "26338954",
      "name": "카카오 판교오피스",
      "category": "회사 > 기업",
      "address": "경기 성남시 분당구 백현동 532",
      "road_address": "경기 성남시 분당구 판교역로 166",
      "latitude": 37.39570,
      "longitude": 127.11054,
      "distance_m": 1204
    }
  ]
}
```
- `category`, `road_address`: 없으면 `null`
- `distance_m`: 좌표를 안 보내면 `null`
- 주소 검색(2단계)에서 나온 결과는 카카오 장소가 아니라서 `id`, `category`, `distance_m`이 `null`이고 `sort`가 적용되지 않음. `name`은 건물 이름, 없으면 주소. 앱은 `id`가 `null`일 수 있다고 보고 처리함
- 결과 없음은 에러가 아님. `places`가 빈 배열로 옴
- 카카오를 최대 3번 부름. 보통 1번
- 검색 결과는 저장하지 않음(즐겨찾기·최근 목적지는 검색어만 저장)

`RouteInfo` 변환: `poiName`=`name`, `address`=`road_address ?: address`, `latitude`·`longitude` 그대로. `poiId`는 카카오 결과에 없음(아래 "보류" 참고).

### POST /pairing
QR에 넣을 URL 발급. 다시 부르면 이전 코드는 무효가 됨.

- 입력: 없음
- 출력 201:
```json
{ "qr_url": "https://<도메인>/p/x7Kq2mVb", "expires_in": 300 }
```
- 에러: 이미 연동된 기기는 409 `already_linked`
- `expires_in`은 초 단위

### GET /pairing/status
연동 상태 조회. QR 화면에서는 3초 간격으로 폴링하고, `pending`이 아니면 중단함. 앱 시작 때 연동 여부 확인에도 씀.

- 입력: 없음
- 출력 200:
```json
{ "status": "pending" }
```

| status | 뜻 | 앱 동작 |
|---|---|---|
| `none` | 연동한 적 없음 | QR 화면 안내 |
| `pending` | QR 유효, 대기 중 | 폴링 계속 |
| `linked` | 연동 완료 | 일정 조회 |
| `expired` | QR 시간 초과 | `POST /pairing`으로 새 QR |
| `failed` | 구글 로그인 거부·실패 | 안내 후 새 QR |
| `reauth_required` | 구글 연동 끊김 | QR 화면 안내 |

`reauth_required`는 서버가 구글 토큰 갱신에 실패했을 때 기록됨. 구글 계정 정보(이메일 등)는 scope 최소화 때문에 받지 않음.

### GET /calendar/events
기본 캘린더 일정 조회. 서버는 일정을 저장하지 않으므로 캐시는 앱(Room)이 맡음.

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `from` | O | 시작 시각. 오프셋 필수 |
| `to` | O | 끝 시각. `from`보다 뒤, 최대 31일 차이 |

출력 200:
```json
{
  "events": [
    {
      "id": "a1b2c3d4e5",
      "title": "치과 예약",
      "location": "강남역 3번 출구",
      "all_day": false,
      "start": "2026-11-03T09:00:00+09:00",
      "end": "2026-11-03T10:00:00+09:00",
      "updated": "2026-11-02T08:12:00Z"
    },
    {
      "id": "f6g7h8i9j0",
      "title": "제주 여행",
      "location": null,
      "all_day": true,
      "start": "2026-11-07",
      "end": "2026-11-09",
      "updated": "2026-11-01T02:30:00Z"
    }
  ]
}
```
- 범위: `from`~`to`와 겹치는 일정. 진행 중인 일정도 포함
- 정렬: `start` 오름차순, 최대 250개
- 반복 일정은 회차별로 펼쳐서 옴. 회차마다 `id`가 다름. 취소된 일정은 안 옴
- `title`, `location`: 없으면 `null`. `location`은 자유 텍스트라 좌표가 아님
- `all_day: true`면 `start`, `end`는 날짜만 옴. `end`는 포함하지 않음(위 예시는 7일, 8일 이틀)
- `start`, `end`, `updated`: 구글 값 그대로. 오프셋이 `+09:00`이 아닐 수 있고 `updated`에는 밀리초가 붙을 수 있음. ISO 8601 파서로 읽을 것
- 에러: 409 `not_linked`, 409 `reauth_required`, 502 `upstream_error`
- access token 갱신은 서버가 알아서 함. 앱은 신경 쓰지 않음
- 구글을 두 번 호출해 느릴 수 있음. 앱 타임아웃은 12초 권장(서버 쪽 호출당 상한 5초)
- `reauth_required`를 받은 뒤에는 `/pairing/status`도 같은 값을 줌. QR을 다시 받으면 복구됨

**가짜 일정 모드**: 서버 설정으로 켜며(`FAKE_CALENDAR=1`, 로컬 서버 기본값), 연동 여부와 상관없이 현재 시각 기준의 가짜 일정을 돌려줌. 형식은 같음. 이 모드에서는 `qr_url`을 열면 구글 로그인 없이 바로 `linked`가 되어 QR 화면을 혼자 시험할 수 있음. 실제 구글 연결 전까지 켜 둠. 끄면 이 모드로 연동한 기기는 `reauth_required`가 됨.

### DELETE /pairing
구글 연동 해제. 서버의 구글 토큰을 지우고 Google에 revoke를 요청함. 기기 토큰은 그대로 유지됨.

- 입력: 없음
- 출력 204: 본문 없음. 연동이 없어도 204

### POST /parse
음성 발화를 목적지 목록으로 구조화함. 발화 텍스트는 Claude API(Anthropic, 모델 Haiku 5.5)로 전송됨.

입력:
```json
{ "text": "유치원 들렀다가 강남역 스타벅스 가자" }
```
- `text`: 1~200자. Pleos STT 결과 그대로

출력 200:
```json
{
  "intent": "navigate",
  "stops": [
    { "query": "유치원", "kind": "personal" },
    { "query": "강남역 스타벅스", "kind": "search" }
  ]
}
```
- `intent`: `navigate` 또는 `unknown`. `unknown`이면 `stops`는 빈 배열
- `stops`: 말한 순서, 최대 5개
- `kind: personal`: 유치원, 회사처럼 역할로 부르는 곳. 앱이 즐겨찾기·자주 가는 곳에서 먼저 찾고, 없으면 `/search`
- `kind: search`: 바로 `/search`
- 앱의 방문 기록은 서버로 보내지 않음. 그래서 어느 유치원인지는 앱이 판단함
- 실패 처리: 200이 아니면 STT 결과를 그대로 `/search`에 넣음. 앱 타임아웃은 6초 권장(서버 쪽 LLM 상한 5초)

## 앱이 부르지 않는 경로

| 경로 | 호출자 | 동작 |
|---|---|---|
| `GET /health` | 도커 헬스체크 | DB 연결 확인 후 `{"status": "ok"}`. 인증 없음 |
| `GET /p/{code}` | 폰(QR 스캔) | 구글 로그인으로 리다이렉트. 코드가 만료되었거나 이미 쓴 경우 안내 페이지 |
| `GET /oauth/google/callback` | 구글(폰 경유) | 구글 토큰 저장 후 완료 페이지. 앱은 폴링으로 `linked`를 확인 |

페어링 코드는 1회용, 5분 만료.

## 보류

- **`/route/optimize`(TMAP 경유지 최적화)**: ETA를 `requestTrip`으로 확정하고 경유지 최적화를 쓸지 정한 뒤 추가. 안 쓰면 TMAP 프록시 전체 생략
- **`poiId`**: A의 "poiId 없이 외부 좌표로 경로 요청" 테스트 결과에 따라 `/search` 출력이나 변환 방식이 바뀔 수 있음
- **장소 없는 일정 매칭**(3단계): API 형태 미정
