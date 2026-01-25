# ToDo Counter

Google Tasksの残りタスク数をステータスバーに常時表示するAndroidアプリ。

## 機能

- 期限切れタスク + 今日のタスクの残り件数をステータスバーに表示
- 数字アイコンで常に見える
- 定期的にGoogle Tasksと同期

## 必要な権限

- インターネット接続
- 通知（フォアグラウンドサービス用）
- Googleアカウントへのアクセス（Google Tasks API）

## セットアップ

1. Google Cloud Consoleでプロジェクトを作成
2. Google Tasks APIを有効化
3. OAuth 2.0クライアントIDを作成
4. `google-services.json` を `app/` に配置

## ライセンス

MIT
