# PokeHabit Android Wrapper v1.0.13 / GPT 분석 자료 공유

APK 내 웹앱의 새 'ChatGPT 분석용 내보내기 > 공유'를 누르면 Android ACTION_SEND 공유 메뉴가 열립니다.
별도의 API 비용, 인터넷 서버, API 키를 추가하지 않았습니다.
표시·요약은 웹 v1.7.138의 기존 기록 데이터에서 생성하며, APK 쪽은 shareText 네이티브 메시지에만 반응합니다.
WebView 메시지는 설정된 GitHub Pages 신뢰 출처에서만 수신합니다.

[주의]
- 웹/PWA v1.7.138 배포 후 Android v1.0.13 APK를 빌드·설치해야 공유 메뉴가 연결됩니다.
- 같은 기존 서명 키(POKEHABIT_DEBUG_KEYSTORE_B64)를 유지하여 기존 앱과 서명이 충돌하지 않게 하세요.
- 소스 정적 검사 및 브라우저 인터랙션 테스트 완료. 실제 APK 빌드와 갤럭시 공유 메뉴 테스트는 별도 필요합니다.

[Codespaces / APK 저장소 루트]
unzip -o PokeHabit_Android_Wrapper_v1.0.13_GPT_Share_Full_Source.zip
rm -f PokeHabit_Android_Wrapper_v1.0.13_GPT_Share_Full_Source.zip
git add -A
git commit -m "PokeHabit Android v1.0.13 GPT share"
git push origin main
