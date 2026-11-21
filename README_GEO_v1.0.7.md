# PokeHabit Android Wrapper v1.0.7 — WebView geolocation fix

## 수정 범위
- APK Wrapper **전용** 수정. 기존 GitHub Pages의 index.html, moves.html, sw.js 및 JSON 백업 형식은 변경하지 않음
- WebView 위치 권한은 Android 런타임 권한 + HTTPS 신뢰 출처를 모두 확인
- WebChromeClient 원점(origin)의 마지막 `/`, 기본 HTTPS 443 포트 등 동등한 표현을 안전하게 인정
- 다른 도메인·HTTP·포트·잘못된 앱 경로·출처 속이기 모두 차단
- Android 권한 팝업이 열려 있는 동안 WebView hide 이벤트가 발생하더라도 대기 콜백 유지
- 이전 버전의 웹 출처 위치 차단 기록만 초기화
- PokeNative 메시지 리스너도 동일한 origin 검증 함수를 사용
- 검증용 PokeGeo 로그 추가 (좌표 출력하지 않음)
- APK versionCode 8 / versionName 1.0.7
- **기존 고정 서명키 유지**: POKEHABIT_DEBUG_KEYSTORE_B64
- GitHub Actions: 22개 보안/원점 유닛 테스트와 WebView 결선 소스 검증 + Android Gradle 컴파일 + APK 서명 인증서 SHA256 확인

## 갤럭시 Codespaces에서 적용
1. 이 패치 ZIP을 `PokeApp` Codespaces 최상위에 업로드
2. 터미널 명령어:

```bash
cd /workspaces/PokeApp
unzip -o PokeHabit_v1.0.7_WebView_Geolocation_Fix_Patch.zip
bash scripts/qa-geolocation.sh
git add app/build.gradle app/src/main/AndroidManifest.xml app/src/main/java/io/github/jok86/pokehabitwrapper/MainActivity.java app/src/main/java/io/github/jok86/pokehabitwrapper/TrustedGeoOrigin.java .github/workflows/build-apk.yml scripts/qa-geolocation.sh tests/TrustedGeoOriginTest.java README_GEO_v1.0.7.md
git commit -m "Fix WebView geolocation v1.0.7"
git push origin main
```

3. Actions가 성공하고 `VERIFIED: APK was signed with the fixed keystore.`를 확인한 후 `Artifacts`에서 APK 다운로드
4. 기존 v1.0.6(고정키 서명) 설치본은 **삭제하지 말고 덮어 설치** 우선 시도
5. 갤럭시 설정 → 애플리케이션 → PokeHabit → 권한 → 위치 → 앱 사용 중 허용 확인
6. 휴대폰 위치 켜기 → PokeHabit 재실행 → 날씨 → 현재 위치 → 위치가 GPS로 바뀌는지 확인

## 검증 방법 및 한계
- `bash scripts/qa-geolocation.sh`가 `ALL PASS: 22` 및 `PASS WebView location permission/source wiring` 출력하면 출처 검사와 코드 연결 정적 검증 통과
- GitHub Actions 성공하면 실제 Android 컴파일과 고정 서명 APK 유효성까지 검증됨
- 실제 갤럭시 WebView GPS 결과는 기기에서만 확인 가능. 이 패치 제작 환경에는 Android SDK/갤럭시가 없으므로 완전한 기기 통합 테스트를 통과했다고 주장할 수 없음
- 실패 시 Logcat `PokeGeo` 태그와 Android 위치 설정 상태를 검토. 좌표 또는 비밀번호 캡처 공유하지 말 것.
- Secret을 새로 만들거나 교체하지 말 것. 기존 Secret 유지
