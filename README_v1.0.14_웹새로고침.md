# PokeHabit Android Wrapper v1.0.14 — 웹 버전 확인/새로고침

- **웹 버전**: v1.7.140 이상과 함께 사용하세요. 웹 업데이트를 먼저 GitHub Pages에 배포합니다.
- **위치**: APK → 설정 → 앱 관리 → 최신 웹 버전 확인 및 새로고침.
- GitHub Pages의 index.html을 별도 네트워크 요청으로 확인(성공 시 HTML `<title>`의 버전 읽기). 확인 실패 시 캐시 변경 없이 종료.
- 확인 성공 시 WebView HTTP 리소스 캐시만 정리하고, `?web_refresh=...`로 캐시를 우회해 최신 페이지 로드. 완료 후 URL의 문서 제목을 확인해 성공/미확인 안내.
- **하지 않는 일**: `WebStorage.deleteAllData()`, `CookieManager.removeAllCookies()`, `clearHistory()`, 앱 데이터 삭제 및 PWA 서비스 워커 저장소 강제 초기화.
- **주의**: 자동 저장 OFF 또는 저장 실패 시 사용을 막습니다. 현재 입력 중인 미저장 폼 내용은 새로고침할 때 사라질 수 있으므로 먼저 저장하세요.
- 구 APK와 브라우저 PWA는 기존 동작 유지; APK에서 capability `webRefreshSupported`를 반환할 때만 화면 표시.
- Android 실기기/서명 빌드 검증은 별도로 필요합니다.

## GitHub Codespaces (Bash)

```bash
unzip -o PokeHabit_Android_Wrapper_v1.0.14_Web_Refresh_Full_Source.zip
rm -f PokeHabit_Android_Wrapper_v1.0.14_Web_Refresh_Full_Source.zip
git add -A
git commit -m "PokeHabit Android v1.0.14 safe web refresh"
git push origin main
```

기존 `POKEHABIT_DEBUG_KEYSTORE_B64` GitHub Secret을 변경하지 마세요. 설치본에 대한 업데이트가 실패하면 APK 삭제 전에 JSON 백업을 확인하세요.
