# PokeHabit Android Wrapper v1.0.0 / HTML v1.7.130

## 목적

- **웹 화면과 기능**: 기존 `https://j-ok-86.github.io/PokeHabit/`의 최신 `index.html`, `moves.html`, `sw.js`를 계속 사용합니다.
- **APK 껍데기**: 화면은 WebView로 표시하고 Android AlarmManager 알림 및 JSON 파일 입출력만 처리합니다.
- **기존 PWA는 그대로 유지**되며 APK와 저장소는 별개입니다. Android에 PokeHabit 앱을 설치해도 Chrome PWA 기록이 자동으로 공유되지 않습니다.

## 반드시 순서대로

1. 기존 Chrome/PWA에서 **설정 → JSON Save**로 전체 백업을 파일에 저장합니다.
2. 별도로 제공된 `PokeRoutine_v1.7.130_Web_GitHub_Update.zip`의 `index.html`, `moves.html`, `sw.js`를 **기존 GitHub Pages 저장소**에 반영합니다. 사이트가 정상 공개되는지 브라우저에서 확인합니다. 배포 주소를 변경하지 마세요.
3. 이 Android Studio 프로젝트로 APK를 만듭니다(아래 안내). **최초 APK 실행은 인터넷 연결이 필요합니다.**
4. 설치한 APK를 실행하고, **APK 내 설정 → 데이터 백업 및 복원 → JSON Load**로 1단계 백업을 복원합니다.
5. 해당 트레이너로 로그인하고 **설정 → 개인 알림 → 권한 요청**에서 Android 13+ 알림 권한, Android 12+ 정확한 알람 권한을 필요에 따라 허용합니다.
6. **테스트 알림**을 눌러 시스템 알림창에 도착하는지 확인하고, 2~3분 뒤 알림을 등록하여 **화면을 끄고 실제 수신 여부를 테스트**합니다.

## APK 만들기 A: GitHub Actions (Android Studio 없이)

1. **이 Android 프로젝트만** 별도의 GitHub 저장소(예: `pokehabit-android-wrapper`)에 올립니다. `app/`, `.github/workflows/`, `build.gradle`, `settings.gradle`, `gradle.properties`가 저장소 루트에 있어야 합니다.
2. 저장소의 **Actions → Build PokeHabit Android APK → Run workflow** 실행.
3. 완료된 실행의 **Artifacts → `PokeHabit-wrapper-debug-apk`** 다운로드 후 압축을 풀어 `app-debug.apk`를 갤럭시에 설치합니다.
4. 주의: GitHub Actions의 **Debug APK는 실행마다 임시 서명키가 달라질 수 있어** 이후 APK를 덮어 설치하지 못할 수 있습니다. 데이터가 있는 실제 사용 기기는 아래 B 방법으로 **고정 서명키**를 설정하고 안정적으로 업데이트하는 걸 권장합니다.

## APK 만들기 B: Android Studio (장기 사용 추천)

1. Android Studio에서 `android-wrapper` 폴더를 **Open**합니다. JDK 17 이상, Android SDK 35, Android Gradle Plugin 8.9.2 / Gradle 8.11.1 환경입니다.
2. Gradle wrapper JAR은 이 전달본에 포함되지 않았습니다. **Android Studio에서 Gradle 8.11.1을 사용하도록 설정**하거나 `gradle 8.11.1`이 설치된 PC에서 프로젝트 루트에서 `gradle wrapper --gradle-version 8.11.1`을 한 번 실행해 공식 wrapper 파일을 생성하세요.
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`로 디버그 APK를 빌드해 설치·테스트할 수 있습니다.
4. 장기 업데이트용으로는 `Build → Generate Signed Bundle / APK → APK`를 사용해 **본인이 관리하는 고정 키스토어로 서명**하세요. 키스토어와 비밀번호 분실 시 기존 설치본의 동일 패키지 업데이트가 어렵습니다. 키스토어는 GitHub에 절대 올리지 마세요.

## 기존 HTML 업데이트 방식

- **일반 화면·기능 변경:** 기존 GitHub Pages에 HTML을 업로드합니다. 앱을 다시 켜거나 갱신하면 WebView가 최신 웹페이지를 가져옵니다. PWA 서비스워커 캐시에 따라 갱신이 지연될 수 있습니다.
- **네이티브 알림 엔진·Android 권한·WebView 기능 변경:** Android 프로젝트 수정 및 APK 재빌드가 필요합니다.
- 웹사이트의 **주소·도메인**을 바꾸면 APK 안의 `MainActivity.APP_URL`과 신뢰할 호스트 규칙도 바꾸고 재빌드해야 합니다.

## 알림과 정확도

- 갤럭시 화면이 꺼지거나 WebView 앱 화면이 열려 있지 않을 때도 `AlarmManager`가 기기 내부에서 예약 알림을 실행합니다.
- **알림 권한 거부** 시 알림이 표시되지 않습니다. Android 12+에서 **정확한 알람 권한 거부** 시는 `setAndAllowWhileIdle`로 예약되어 시각이 지연될 수 있습니다.
- 기기 전원이 꺼진 상태, 앱을 Android 설정에서 **강제 중지**한 뒤, 제조사 배터리 제한/방해 금지 모드 등의 환경에서는 알림이 누락될 수 있습니다. 반드시 실제 갤럭시에서 테스트하세요.
- 네이티브 예약은 **이 APK에서 마지막으로 로그인·동기화한 트레이너 1명**의 알림 목록을 활성화합니다. 다른 계정으로 로그인하면 이전 계정 알림을 대체합니다.
- 백업 JSON에는 웹 알림 목록이 포함되지만, **Android의 권한 설정·발송 완료 이력·예약 엔진 내부 상태는 포함되지 않습니다**. JSON을 복원해 계정에 로그인하면 일정이 다시 동기화됩니다.
- **Google Play 배포용 앱은 아닙니다.** 정확한 알람 권한은 스토어 배포 정책 대상입니다. 개인 설치 사용을 전제로 했습니다.

## 데이터 및 보안

- APK WebView 로컬 저장 공간과 Chrome PWA 저장 공간은 **서로 다름**. 수동 JSON 백업 → APK 내 JSON Load 필요.
- WebView의 기본 JavaScriptInterface를 사용하지 않고 AndroidX WebKit `addWebMessageListener`를 사용하며, 오직 `https://j-ok-86.github.io` 최상위 문서에만 제한합니다. 다른 사이트 탐색은 기본 브라우저에서 엽니다.
- 원격 HTML은 업데이트 때마다 실행되는 **신뢰된 코드**이므로 GitHub 계정 보안이 중요합니다. GitHub 계정에 2FA 사용 권장.
- JSON 백업 파일은 APK 시스템 파일 선택기로 저장하며, JSON 복원은 시스템 파일 선택기를 사용합니다.
- 최초 온라인 방문 후 오프라인에서 기존 캐시로 실행될 수도 있지만, **모든 리소스와 신규 배포 버전의 완전한 오프라인 동작은 보장하지 않습니다**.

## 검증 상태

- 웹 코드 JavaScript 구문 검사 및 네이티브 브리지 요청/JSON 분할 전송 테스트 진행.
- 이 작성 환경에는 Android SDK/Gradle이 없어 **실제 APK 컴파일·설치·갤럭시 슬립 상태 알림 검증은 진행하지 못했습니다**. GitHub Actions 또는 Android Studio에서 빌드 후 실제 장치 테스트가 필요합니다.
