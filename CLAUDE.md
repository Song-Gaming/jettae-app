# hyundai-app

현대자동차그룹 차량용 앱 개발 공모전 출품작. Pleos Connect(AAOS) 앱, 에뮬레이터로만 개발·검증.

## 역할과 패키지 경계
- 앱 A: `nav/` `vehicle/` `gleo/` (내비·차량·시뮬레이션)
- 앱 B: `ui/` `voice/` `search/` `ai/` `data/` (화면·음성·데이터, 캘린더 연동)
- 서버: 별도 저장소. 앱과 서버 사이는 `docs/api-contract.md`가 기준
- 다른 사람 패키지는 수정하지 않는다. 필요하면 먼저 묻는다

## 규칙
- 서버 API는 `docs/api-contract.md` 그대로 구현한다. 계약서에 없는 필드·API를 만들지 않는다
- 기기 토큰, 발화, 일정 내용, 좌표는 로그에 남기지 않는다
- 키와 서버 주소는 `local.properties`에만 둔다. 코드에 하드코딩하지 않는다
- 화면은 XML 레이아웃(appcompat). 전체 화면 팝업 금지, 알림·배너 사용
- 차량 화면이므로 글씨·버튼은 크게
- Kotlin, minSdk 28. Vehicle SDK는 `ai.pleos.playground:Vehicle:2.3.2` (2.3.1은 저장소에 없음)

## local.properties 설정
```
SERVER_BASE_URL=https://dev.<도메인>/   # 비워 두면 가짜 서버 모드
```

## 빌드·테스트
- 빌드: `gradlew :app:assembleDebug`
- 단위 테스트: `gradlew :app:testDebugUnitTest`
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 브라우저에서 http://127.0.0.1:8088/
