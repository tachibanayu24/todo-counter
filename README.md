# ToDo Counter

Google Tasksの「期限切れ + 今日」のタスク数を常に表示するAndroidアプリ。

## 機能

- 画面上にフローティングオーバーレイで残りタスク数を常時表示
- 5分ごとに自動同期
- タップで即更新（バイブでフィードバック）
- 長押しでGoogle Tasksアプリを起動
- タスク0件でオーバーレイ自動非表示

## セットアップ

### Google Cloud Console

1. プロジェクトを作成
2. 「Google Tasks API」を有効化
3. OAuth同意画面を設定（外部 / テストモード）
4. 自分のGoogleアカウントをテストユーザーに追加
5. 認証情報 → OAuthクライアントID作成（**Android**タイプ）
   - パッケージ名: `com.tachibanayu24.todocounter`
   - SHA-1: デバッグ用は `./gradlew signingReport` で取得

### ビルド

Android Studioでビルド・実機にインストール。

## ライセンス

MIT
