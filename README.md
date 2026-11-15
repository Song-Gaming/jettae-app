# 제때

> Team Song · 일정과 자주 가는 곳을 학습해 **알아서 목적지와 출발 시각을 제안하고, 탭 한 번·말 한 마디로 안내를 시작**하는 Pleos Connect 차량용 내비 보조 앱

## APK

- 위치: [`apk/Team Song_제때_release.apk`](apk/) (release 빌드, 서명됨)
- 설치: `adb install -r "apk/Team Song_제때_release.apk"`

## 주요 기능

| 기능 | 화면 / 방법 | 사용한 Pleos SDK |
|---|---|---|
| 일정 기반 출발 제안 | 앱 시작·기어 P→D 때 3시간 안의 다음 일정을 골라 장소를 검색하고, 내비에 소요 시간을 물어 "지금 출발하면 N분 일찍 도착 예정이에요" 알림을 띄움. [안내 시작]을 누르면 차량 내비가 바로 경로 안내를 시작 | NaviHelper `requestTrip`·`requestRoute`, Vehicle 기어 |
| 구글 캘린더 연동 | [내 일정] 탭 → [구글 캘린더 연결하기] → 폰으로 QR을 찍어 구글 로그인. 일정은 15분마다 갱신 | — (서버 OAuth) |
| 자주 가는 곳 | 기어 P(주차)마다 방문 기록을 차 안 DB에 저장하고, 장소를 묶어 집·회사·유치원 등으로 분류·점수화 | Vehicle 기어 |
| Gleo 음성 안내 | "글레오, 유치원 가자" → 자주 가는 곳·즐겨찾기 후보 → 확인 → 안내 시작 | Gleo App Action, NaviHelper `requestRoute`·`getBookmarkInfo` |
| 차량 상태 | [센서] 탭: 배터리·주행 가능 거리·기어·문·좌석·안전벨트, Virtual Console 조작 | Vehicle |

## 실행 방법

1. Pleos Connect 에뮬레이터를 켜고 APK를 설치합니다.
2. 에뮬레이터 시간대를 서울로 바꿉니다(기본값은 LA라 일정 시각이 어긋남).
   ```
   adb shell cmd time_zone_detector set_auto_detection_enabled false
   adb shell service call alarm 3 s16 Asia/Seoul
   ```
3. **부팅 후 내비 앱(하단 ▲ 아이콘)을 한 번 열었다가** 앱으로 돌아옵니다. 내비를 한 번도 열지 않으면 내비가 도착 시간 요청에 답하지 않습니다.
4. 앱을 실행하고 알림 권한을 허용합니다.

### 서버 없이 시험하기 (가짜 일정)

[내 일정] 탭의 **일정 출처**에서 시나리오를 고르고 **[출발 제안 받기]**를 누르면 됩니다. 기어를 P→D로 바꾸거나(센서 탭의 DRIVE 버튼 또는 Virtual Console) 앱을 다시 시작해도 같은 제안이 뜹니다. 일정 시각은 지금 시각 기준으로 만들어져 언제 시험해도 같은 결과가 나옵니다.

| 선택 | 일정 | 기대 결과 |
|---|---|---|
| 가짜 일정 1 | 50분 뒤 강남역 미팅 | "강남역 2호선까지 N분 · 지금 출발하면 M분 일찍 도착 예정이에요" 알림 → [안내 시작]을 누르면 내비 안내 시작 |
| 가짜 일정 2 | 25분 뒤 인천공항 | "인천국제공항 제1여객터미널까지 N분 · 지금 출발해도 M분 늦을 예정이에요" 알림 |
| 가짜 일정 3 | 1시간 뒤 "엄마네 집" | 장소를 찾지 못해 알림 없음, 버튼 옆에 이유 표시 |
| 가짜 일정 4 | 20분 전 시작한 유치원 일정(차가 지금 있는 곳) + 40분 뒤 회사 | 유치원에 이미 와 있으니 **회사** 출발 제안 |
| 가짜 일정 5 | 20분 전 시작한 유치원 일정(먼 곳) + 40분 뒤 회사 | 유치원에 아직 못 갔으니 **유치원** "N분 늦을 예정이에요" |

**일정 고르는 기준**: 장소가 있고 3시간 안에 시작하는 다음 일정을 제안합니다. 이미 시작했지만 끝나지 않은 일정이 있으면, 차가 지금 그 장소(300m 안)에 있는지 봅니다. 있으면 들렀다 떠나는 중이니 다음 일정을, 없으면 늦었어도 그 일정을 제안합니다. 시나리오 4는 에뮬레이터 기본 위치(성남 수정구 시흥동)를 기준으로 만들었습니다.
| 실제 서버 | 연동한 구글 캘린더 | 실제 일정·장소로 같은 흐름 |

**QR 연동 흐름**: [연결 해제] → [구글 캘린더 연결하기]를 누르면 QR 화면이 뜹니다.
- 실제 서버: 폰으로 QR을 찍어 구글 로그인하면 연동됩니다. 서버가 응답하지 않으면 QR 화면 아래에 "서버 없이 시험하려면 아래에서 가짜 일정을 고르세요"와 가짜 일정 1~3 버튼이 나옵니다.
- 가짜 일정: 시연용 QR이 뜨고, 아래 가짜 일정 버튼을 누르면 연동된 것으로 처리되어 일정 화면으로 돌아갑니다.

알림의 [안내 시작]을 누르면 일정 장소를 다시 검색해 차량 내비로 경로 안내를 시작합니다. 인터넷이 없으면 내비가 소요 시간을 계산하지 못해 알림이 뜨지 않습니다.

### 그 밖의 시험

- **자주 가는 곳**: [자주 가는 곳] 탭 → [가상 방문 기록 넣기] → 장소 분류·점수·지금 시간대 추천 확인
- **Gleo**: [자주 가는 곳] 탭에서 [가상 방문 기록 넣기] 후 "글레오, 유치원 가자"
- **방문 기록**: 센서 탭에서 DRIVE를 누르면 5초 뒤 자동으로 P가 되며 도착이 기록됩니다

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect v2.1.0 (Android 14, API 34, x86_64, `android-automotive`) |
| 화면 규격 | 2560 × 1440, 160 dpi |
| 최소 / 대상 SDK | minSdk 28 / targetSdk 36 |
| Pleos SDK | NaviHelper 3.0.5, Vehicle 2.3.2, Gleo App Action |
| 빌드 | Gradle 9.4.1, AGP 9.2.1, JDK 21 (Android Studio 내장 JBR) |
| 실차 | 사용하지 않음. 에뮬레이터와 Virtual Console로만 검증 |

## 직접 빌드하기

`local.properties`(저장소에 없음)에 아래를 넣습니다. 모두 선택 항목입니다.

```properties
sdk.dir=<Android SDK 경로>
# 비우면 실제 서버를 고를 수 없고 가짜 일정만 씁니다
SERVER_URL=https://<서버 도메인>
# release 서명. 없으면 서명되지 않은 release APK가 나옵니다
RELEASE_STORE_FILE=<keystore 경로>
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

```
gradlew :app:testDebugUnitTest      # 단위 테스트
gradlew :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

## 개인정보·데이터 처리

- 방문 기록과 자주 가는 곳 점수는 **차 안(Room DB)에만** 저장하고 서버로 보내지 않습니다.
- 구글 토큰은 서버에 암호화해 보관하고 앱으로 보내지 않습니다. 권한은 `calendar.events.readonly` 하나입니다.
- 카카오 장소 검색 결과(좌표·주소)는 정책에 따라 저장하지 않고 필요할 때마다 다시 조회합니다.
- 장소 이름·일정 내용·토큰은 로그에 남기지 않습니다.

## 구조

```
app/src/main/java/com/example/hyundai/
├─ MainActivity.kt          탭 화면, NaviHelper·Vehicle 연결, 출발 제안, Gleo 액션
├─ nav/Departure.kt         출발 시각 계산
├─ data/calendar/           일정 캐시·동기화, 다음 일정 고르기
├─ data/places/             방문 기록 → 장소 묶기·분류·점수
├─ data/remote/             서버 API (실제 / 가짜 일정 시나리오)
└─ ui/                      일정·QR·자주 가는 곳 화면, 출발 제안 알림
docs/api-contract.md        앱 ↔ 서버 API 계약서
```
