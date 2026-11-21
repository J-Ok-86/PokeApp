# PokeHabit APK v1.0.5 — 위치 권한 연결

- 기존 GitHub Pages HTML과 JSON 저장 형식 유지
- AndroidManifest: ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION 등록
- WebView Geolocation API 활성화 + WebChromeClient 위치 요청 권한 연결
- `https://j-ok-86.github.io/PokeHabit/`에서만 위치 요청 허용 (타 출처 차단)
- 날씨 등 위치 기능 최초 실행 시 Android 시스템 위치 권한 팝업 표시
- 대략적인 위치만 허용해도 사용 가능. 앱 시작 시 위치 권한 강제 요청 없음.
- 기존 알림·JSON 파일 가져오기/내보내기·키스토어 Secret은 그대로 사용

## APK 업데이트
Codespaces 저장소 루트에 패치 ZIP 업로드 후 압축 해제·커밋·푸시하세요.
GitHub Actions 빌드 후 app-debug.apk 덮어 설치 (서명키 동일해야 함).
기기 설정 → 애플리케이션 → PokeHabit → 권한 → 위치에서 확인 가능.
주의: 권한이 표시돼도 갤럭시 시스템 위치 설정이 꺼져 있으면 사용할 수 없습니다.
