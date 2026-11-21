# PokeHabit Android v1.0.10 — 라이프 이벤트 위젯 큰 글씨 개선

- **기존 3열 × 2행(최대 6개) 위젯 디자인 유지**. 웹 앱/PWA 변경 불필요.
- 이벤트명 11sp → **15sp(굵게)**, 최근 기록 시각 11sp → **16sp(굵게)**.
- 반복 횟수 9sp → **12sp(굵게)**, 이모지 17sp → **22sp**.
- 상단 제목 13sp → **15sp**, 하단 동기화 안내 10sp → **11sp**.
- 큰 글씨와 6개 버튼이 겹치지 않도록 기본 위젯 높이 175dp → **235dp**, 기본 크기 4×3 → **4×4**로 조정. 위젯은 계속 크기 변경 가능.
- 기존 라이프 이벤트 기록, 계정, 위젯 저장소, 알림, 수면 추정, 웹 브리지, 서명키/패키지명 변경 없음.

## 필수 안내

기존 홈 화면에 등록한 위젯의 크기는 APK 업데이트만으로 항상 자동 변경되지 않습니다. 기존 위젯에서 내용이 잘리면 위젯을 길게 눌러 **세로 4칸 이상으로 늘리거나**, 삭제 후 다시 추가하세요. 3열 버튼이라 가로 폭도 가능한 4칸으로 사용하세요.

## GitHub Codespaces / Git Bash — APK 저장소 루트에서 그대로 실행

```bash
unzip -o PokeHabit_Android_Wrapper_v1.0.10_Larger_Text_Full_Source.zip
git add -A
git commit -m "PokeHabit Android v1.0.10 enlarge widget labels"
git push origin main
```

압축파일을 저장소 루트에 업로드한 상태에서 실행하십시오. `git add -A`에 ZIP 자체까지 포함하지 않도록 가급적 압축파일은 저장소 외부에 둔 뒤 풀거나, 위 명령에 `rm -f PokeHabit_Android_Wrapper_v1.0.10_Larger_Text_Full_Source.zip`을 `git add -A` 전에 추가해도 됩니다.

### 빌드/설치
GitHub Actions > Build PokeHabit Android APK > 최신 실행 > Artifacts에서 `PokeHabit-wrapper-debug-apk` 다운로드. 기존 `POKEHABIT_DEBUG_KEYSTORE_B64` 서명 시크릿과 앱 데이터는 유지하세요.

### 검증
6개 각 셀의 텍스트 크기·굵기, 리소스 XML 파싱, 버전 변경 및 ZIP 내용 검증을 실시했습니다. Android SDK/갤럭시 실제 기기에서는 빌드·표시 테스트를 수행하지 못했습니다.
