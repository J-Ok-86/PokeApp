# PokeHabit Android Wrapper v1.0.4 / Web v1.7.132

## 변경점
- WebView에서 강제 글자 확대(115–140%)와 TEXT_AUTOSIZING 제거. 기존 Chrome/PWA CSS 크기와 정렬.
- Android 상태바/내비게이션바를 현재 HTML의 밝은/어두운 테마와 동기화.
- Android 15의 시스템바 영역은 APK가 처리하고 웹 CSS의 중복 하단 안전 여백(`--nav-safe`)은 APK에서만 0으로 지정.
- 패키지 ID 유지; versionCode 5. 기존 1.0.3 고정 서명키 사용 시 덮어쓰기 업데이트 가능.
- 웹 버전에는 인사말·명언·오늘 할 퀘스트 제목의 모바일 폰트 밸런스만 수정.
- 데이터/JSON/알림 로직 변경 없음.

## 적용 (Codespaces / PokeApp)
1. `PokeHabit_v1.0.4_Android_Display_Patch.zip`을 Codespaces 저장소 루트에 업로드.
2. `unzip -o PokeHabit_v1.0.4_Android_Display_Patch.zip`
3. `rm PokeHabit_v1.0.4_Android_Display_Patch.zip`
4. `git add -A && git commit -m "Fix APK typography and native safe areas v1.0.4" && git push origin main`
5. GitHub Actions 빌드 결과에서 APK를 다운로드하여 설치.

고정 서명키 `POKEHABIT_DEBUG_KEYSTORE_B64`가 GitHub Actions Secrets에 있어야 함. 비밀키 문자열은 공개 저장소/채팅에 업로드 금지. 새 키 생성 시 기존 고정 키로 설치한 APK는 덮어쓰지 못함.
APK에 사용되는 HTML은 계속 `https://j-ok-86.github.io/PokeHabit/`에서 불러옵니다.

## PWA 웹 버전
`PokeRoutine_v1.7.132_Web_Layout_Balance_GitHub_Update.zip`의 index.html, moves.html, sw.js를 기존 PokeHabit 웹 저장소에 반영합니다. 웹 UI만 변경했으므로 APK 빌드와 독립적으로 업데이트할 수 있습니다.

## 검증 한계
JS/zip 구문 및 모바일 CSS 렌더링은 검증했으나 이 실행 환경에는 Android SDK가 없어 실제 APK 설치/기기 수신 테스트는 수행하지 못했습니다.

추가 검증: 태블릿 768×1024 / 800×1280 / 1024×768 / 1280×800 및 960×600에서 CSS 배치 검사. PWA 테마와 APK 시스템바 색상을 동일한 색상 값으로 정렬.
