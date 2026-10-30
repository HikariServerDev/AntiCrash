# Changelog

## Unreleased - 2026-10-30

- 全版へFakePlayerのcheckpoint UUID復元・名前/UUID衝突の事前拒否・UUID不一致診断を反映。各版の実際のプロフィールキャッシュAPIに適合し、通常のログイン/認証/キャッシュ内容は変更しない。
- 新Carpetの非同期プロフィール取得は復旧scope内だけ保存済みプロフィールで完了し、その完了済みプロフィールの生成だけServer thread上で即時登録。通常のCarpetの非同期取得/Executorは維持。Vector形式の生成引数にも対応。未知の生成APIやUUID不一致は引き続き復旧を拒否する。
- Scope分離・後片付け・非同期完了・版別キャッシュのbytecode契約を実Server不要のテストで検証。1.16.1は引き続きPORT_IN_PROGRESSで配布不可。
