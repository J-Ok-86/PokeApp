PokeHabit Android v1.0.6 — 반복되는 APK 패키지 충돌 진단 및 고정 서명 수정

확인된 사실:
 - 사용자 제공 APK 3개가 서로 다른 v2 서명 인증서로 빌드됨.
 - 기존 CI는 keystore를 ~/.android/debug.keystore로 복원하지만 app/build.gradle에서 signingConfigs를 명시하지 않아 Gradle이 새 디버그 키를 사용하는 상황이 발생함.

수정:
 - app/build.gradle에서 지정한 키스토어 경로를 debug signingConfig에 직접 연결.
 - 서명키가 없으면 Gradle 빌드 실패 (임시 새 키로 서명 금지).
 - workflow에서 비밀 키를 RUNNER_TEMP에 복원하고 경로를 Gradle에 전달.
 - 빌드된 APK 인증서 SHA-256과 키스토어 인증서 SHA-256을 비교.
 - 일치할 때에만 GitHub Actions APK Artifact 업로드.
 - versionCode 7, versionName 1.0.6 (패키지 ID 변경 없음).

적용:
 1) ZIP을 PokeApp Codespaces 저장소 루트에 업로드
 2) unzip -o PokeHabit_v1.0.6_Stable_Signing_Fix_Patch.zip
 3) rm PokeHabit_v1.0.6_Stable_Signing_Fix_Patch.zip
 4) git add app/build.gradle .github/workflows/build-apk.yml
 5) git commit -m "Enforce persistent APK signing and verify certificate v1.0.6"
 6) git push origin main
 7) GitHub Actions에서 컴파일 및 Verified 메시지 확인 후 APK 다운로드

안전:
 - POKEHABIT_DEBUG_KEYSTORE_B64 GitHub Secret은 변경/재생성하지 마세요.
 - 새로운 1.0.6 APK는 고정 키로 서명됩니다.
 - 현재 설치된 1.0.5 APK는 다른 서명으로 만들어졌기 때문에 이 패치 APK로 덮어쓸 수 없습니다.
 - JSON 데이터를 백업하고 이번 한 번 재설치한 뒤, 이후 1.0.7부터 같은 Secret으로 덮어쓰기 업데이트하세요.
 - 개인 키 자체는 본 ZIP에 포함되지 않습니다.
 - Android SDK/Gradle 부재로 이 환경에서는 실제 APK 컴파일 테스트를 수행하지 못했습니다. GitHub Actions 검증 로그를 확인하세요.
