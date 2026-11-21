# PokeHabit Android v1.0.9 + PokeRoutine Web v1.7.135

## 이번 업데이트

- 홈 화면 위젯의 라이프 이벤트 버튼을 최대 6개로 확장했습니다. 3열 × 2행이며 각 버튼에 이모지 / 반복 횟수 / 이름 / 마지막 기록 시각을 표시합니다.
- 활성 라이프 이벤트가 6개 이하면 자동으로 모두 표시합니다. 기존 4개 위젯 선택 데이터는 유지하면서 6개로 확장됩니다. 활성 이벤트가 7개 이상일 때만 라이프 이벤트 설정에서 최대 6개를 선택할 수 있습니다.
- 앱 기록을 읽어오는 API `widgetRecords`를 Android WebView 브리지에 추가했습니다. 앱을 열어 로그인하면 최근 생활기록을 APK 내부에 반영하여 위젯에서 오늘 횟수와 최근 시각을 표시합니다.
- 위젯에서 버튼을 누르면 새 로그를 네이티브 저장소에 먼저 기록하고 위젯에 바로 반영합니다. 웹 저장이 확인된 뒤에만 큐 항목을 제거하며, 동일 기록 ID는 중복 집계하지 않습니다.
- 하루 1회형은 0/1회로, 반복형은 오늘의 전체 기록 횟수로 표시합니다. 취침은 자정~05:59 기록을 전날 밤으로 그룹핑하는 기존 규칙을 따릅니다.
- 날짜 변경 시 정상적인 하루 기록으로 갱신하도록 위젯 자동 갱신 주기를 30분으로 설정했습니다. 현재 시각을 계속 갱신하는 시계가 아니라 기록 시각 표시입니다.
- APK v1.0.8 대비 변경 범위는 네이티브 위젯과 위젯용 데이터 전달로 제한했습니다. 기존 퀘스트, 알림, 수면 추정, PWA, JSON 백업 데이터 구조를 보존합니다.

## 필요한 배포 작업

**웹을 먼저 배포하십시오.** `PokeRoutine_v1.7.135_Web_GitHub_Update.zip`의 `index.html`, `moves.html`, `sw.js`를 웹 GitHub Pages 저장소 최상단에 적용합니다.
그다음 `PokeHabit_Android_Wrapper_v1.0.9_Full_Source.zip`을 APK GitHub 저장소 최상단에 적용하고 기존 Actions 빌드를 실행합니다.
기존 `POKEHABIT_DEBUG_KEYSTORE_B64` 시크릿을 변경하지 마십시오. 앱 식별자 `io.github.jok86.pokehabitwrapper`도 변경하지 않았습니다.

### GitHub Codespaces Bash (웹 저장소)

```bash
unzip -o PokeRoutine_v1.7.135_Web_GitHub_Update.zip
rm -f PokeRoutine_v1.7.135_Web_GitHub_Update.zip
git add -A
git commit -m "PokeHabit Web v1.7.135 six-button widget sync"
git push origin main
```

### GitHub Codespaces Bash (Android 저장소)

```bash
unzip -o PokeHabit_Android_Wrapper_v1.0.9_Full_Source.zip
rm -f PokeHabit_Android_Wrapper_v1.0.9_Full_Source.zip
git add -A
git commit -m "PokeHabit Android v1.0.9 six-button widget"
git push origin main
```

명령을 실행하는 저장소가 올바른지 확인하세요. 기본 브랜치가 main이 아닌 경우 마지막 `git push origin main`을 해당 브랜치명으로 바꿉니다. ZIP의 내용물은 저장소 루트 경로 기준입니다.

## 기기 확인 사항

1. 기존 APK 데이터를 JSON으로 백업하고, 웹 v1.7.135를 배포한 뒤 기존 서명키로 APK 업데이트 설치.
2. APK를 실행하고 같은 트레이너에 로그인한 뒤 달력 → 라이프 이벤트 설정에서 6개 활성화.
3. 홈 화면 위젯에서 6개 타일의 이모지, 이름, 횟수, 마지막 시각을 확인. 예전 위젯이 갱신되지 않으면 삭제 후 다시 추가.
4. APK에서 반복형 이벤트를 두 번 기록한 후 위젯에서 `2회`와 가장 최근 시각을 확인.
5. 위젯에서 기록 → 앱 실행 → 동기화 → 중복 기록이 없는지 확인. 날짜가 바뀌거나 자정 후 취침을 기록할 때 이전 기록이 잘못 표시되지 않는지 확인.
6. PWA에서는 기존 수동 기록과 알림이 계속 동작하는지 확인.

## 검증 범위

HTML 스크립트 문법, JS 기록 집계 로직, Android XML 정적 파싱 등 소스 검증을 실시합니다. 실제 APK 빌드와 갤럭시 위젯 테스트는 Android SDK 및 기존 개인 서명키가 있는 빌드 환경에서 완료해야 합니다. 소스 ZIP은 설치용 APK 바이너리가 아닙니다.
