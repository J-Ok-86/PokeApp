# PokeHabit APK v1.0.3 — 갤럭시에서 업데이트

이번 변경은 APK Wrapper 전용입니다. GitHub Pages의 HTML이나 기존 JSON 파일 형식은 변경하지 않습니다.

## 갤럭시 Codespaces

1. `PokeHabit_v1.0.3_APK_Code_Patch.zip`을 다운로드 → Codespaces PokeApp 루트에 업로드
2. 터미널에 붙여넣기:

```bash
unzip -o PokeHabit_v1.0.3_APK_Code_Patch.zip
bash scripts/prepare-signing.sh
git add -A
git commit -m "Fix APK font, status bar, stable signing"
git push origin main
```

3. GitHub Actions에서 **Build PokeHabit Android APK**가 성공하면 **Artifacts** → `PokeHabit-wrapper-debug-apk` 다운로드 → 압축 해제 → `app-debug.apk` 설치

## 업데이트 가능 여부

- 기존 v1.0.0 APK는 빌드 당시 임시 서명키를 사용했습니다. 새 키로 생성한 v1.0.3 APK는 **기존 앱 위에 설치되지 않을 수 있습니다**. 그때는 백업 후 한 번 제거·재설치해야 합니다.
- 이후에는 서명키를 GitHub Secret에 고정했으므로 다음 v1.0.4부터 앱 삭제 없이 덮어 설치할 수 있습니다(패키지 ID 유지, versionCode 증가).
- 앱 제거 시 데이터가 삭제되므로 **먼저 JSON Save** 해야 합니다.

## 보안

GitHub Actions Secrets에 저장되는 값: `POKEHABIT_DEBUG_KEYSTORE_B64`. 이 비밀값은 공개 GitHub 저장소 코드에 올라가지 않습니다.
스크립트에서 GitHub Secrets 조회 권한 오류가 나오면 새 키를 반복 생성하지 말고 오류 화면을 공유해 주세요.
