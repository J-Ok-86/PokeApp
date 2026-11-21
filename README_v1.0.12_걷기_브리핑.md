# PokeHabit Android Wrapper v1.0.12 · Smart Life 2.0 (Walking only)

웹 화면 버전: PokeRoutine v1.7.136. 기존 v1.0.10 소스와 설정을 바탕으로 변경했습니다.

## 변경 내용

- 라이프 이벤트에 **걷기**를 기본 추가. 첫 터치 시작 / 두 번째 터치 종료. 완료된 세션만 반복 횟수에 반영.
- 기존 3×2 홈 위젯과 6개 선택 로직 유지. 반복형 라이프 이벤트만 횟수를 표시하며, 걷기는 최근 시작·종료 시각과 진행 중 상태를 표시. Android 위젯의 지속 깜빡임 대신 활성 색상 유지.
- 걷기 시간은 시작/종료 시각으로 계산. APK는 `TYPE_STEP_COUNTER` 센서 + `ACTIVITY_RECOGNITION` 허용 시 진행 중 걸음 수를 수집하며, 측정 불가 시 시간만 기록. PWA는 같은 원터치 시작/종료지만 걸음 수는 `—` (미측정).
- 걸음 수는 해당 세션에서 센서값 차이를 계산. 사용자 계정별로 원본을 보관하고 앱이 열린 후 웹 저장을 확인한 뒤에만 큐에서 제거.
- 걷기 통계 7일/30일(시간·횟수·측정 걸음) 및 최근 세션. `걷기 통계 표시` 기본 OFF, 데이터는 삭제되지 않음.
- APK에서만 기상·취침 **추정** 후보 제시. 야간의 짧은 사용을 고려하는 계산, 신뢰도 표시, 개인 수동 기록 기준 보정. **확정** 버튼을 누르기 전에는 기존 수동 기록이나 보상에 반영하지 않음. PWA는 수동만 지원.
- **아침·저녁 브리핑 알림**: 계정별 ON/OFF, 시간 개별 설정, 기본 OFF. 저녁 알림 시간은 기존 저녁 브리핑 표시 시간과 같은 값을 사용. 탭 시 해당 브리핑 열기. Android 백그라운드에서는 `AlarmManager`로 예약하며 정확한 전달은 기기 설정에 따라 달라짐. PWA는 앱이 화면에 열려 있을 때만 알림 시도.
- 모든 퀘스트의 완료/보상은 계속 **수동**. 유튜브/기타 앱 사용량 통계, 사용량 보상, 걷기 보상은 추가하지 않음.

## 권한

- `ACTIVITY_RECOGNITION`: 세션 걸음 수 측정 (없으면 걷기 시간만 기록)
- `POST_NOTIFICATIONS`: Android 13+ 알림 표시
- `SCHEDULE_EXACT_ALARM`: 정확한 시각 알림 가능 여부 (거부 시 지연 가능)
- `PACKAGE_USAGE_STATS`: APK 전용 기상·취침 추정 (사용정보 접근 권한 별도)

`WalkService`는 사용자가 시작한 걷기를 추적하는 **Foreground Service**이며, Android 재시작/배터리 절약/센서 미지원 상태에서 값이 누락될 수 있습니다. 위젯 점멸 애니메이션은 Android `RemoteViews`의 제한으로 구현하지 않았습니다.

## 빌드 및 기존 설치 앱 업데이트

기존 GitHub Actions 서명 시크릿 `POKEHABIT_DEBUG_KEYSTORE_B64`를 그대로 유지해야 설치 앱을 삭제하지 않고 업데이트할 수 있습니다. APK 자체는 제공하지 않으며 빌드 소스를 전달합니다. GitHub 저장소 최상위에 ZIP을 올린 뒤 Codespaces Bash에서:

```bash
unzip -o PokeHabit_Android_Wrapper_v1.0.12_Full_Source.zip
rm -f PokeHabit_Android_Wrapper_v1.0.12_Full_Source.zip
git add -A
git commit -m "PokeHabit Android v1.0.12 walking briefing"
git push origin main
```

웹 저장소에는 별도로 `PokeRoutine_v1.7.136_Web_GitHub_Update.zip`을 배포합니다.

## 테스트 상태

- JavaScript 파싱/핵심 데이터 분기 테스트 통과
- Android XML 파싱 통과
- Java 소스 파싱 오류 없음 (`javac`로 문법 확인, Android SDK 의존성 때문에 완전 빌드는 수행하지 못함)
- **미검증**: 실제 Android Gradle 컴파일·Galaxy 위젯 클릭·포그라운드 걸음 센서 수집·Android 알림 수신/탭 동작·PWA 실기기 표시

업데이트 전 JSON 백업을 권장합니다.
