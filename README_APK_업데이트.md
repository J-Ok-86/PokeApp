# PokeHabit APK v1.0.14 — 업데이트 방법

웹 화면은 v1.7.140 이상이어야 합니다. **웹 ZIP을 먼저 GitHub Pages 저장소에 배포**하고, APK 프로젝트 ZIP을 별도의 Android 저장소에 적용하세요.

## APK 프로젝트 소스 반영 (GitHub Codespaces / Bash)

```bash
unzip -o PokeHabit_Android_Wrapper_v1.0.14_Web_Refresh_Full_Source.zip
rm -f PokeHabit_Android_Wrapper_v1.0.14_Web_Refresh_Full_Source.zip
git add -A
git commit -m "PokeHabit Android v1.0.14 safe web refresh"
git push origin main
```

GitHub Actions의 **Build PokeHabit Android APK → Artifacts**에서 APK를 내려받아 설치합니다. ZIP은 설치 파일이 아닌 빌드 소스입니다.

**기존 고정 서명키 시크릿 `POKEHABIT_DEBUG_KEYSTORE_B64`는 수정하지 마세요.** 이 시크릿이 없다면 빌드가 서명을 임의 생성하지 않고 실패하도록 되어 있습니다.

업데이트 이후 **APK → 설정 → 앱 관리 → 최신 웹 버전 확인 및 새로고침**을 사용할 수 있습니다. 서버 연결 실패 때는 기존 화면 유지, 연결 성공 때에는 WebView 화면 캐시만 갱신하며 저장 데이터는 삭제하지 않습니다.

**주의:** 자동 저장이 꺼져 있거나 저장에 실패했다면 기능이 실행되지 않습니다. 설치나 테스트 전에 JSON 백업을 권장합니다.

APK Android 실기기에서의 빌드 및 동작 검증은 아직 수행하지 않았습니다.
