# aruno-home-layout

Aruno-Home/T の HOME 画面（4×4＝16マス）をサイトで変更できるようにする
専用 Worker です。仕様は「Aruno-Home/T HOME可変式（サイト駆動）仕様書」
（2026-11-19、利用者決定済み）に基づきます。

## 構成

- `GET /v1/home-layout` — 配置データ（JSON）を返す。アプリが「画面更新」で取得する。認証なし。
- `PUT /v1/home-layout` — 配置データを保存する。管理認証が必要。`expectedRevision` による競合検出あり。
- `GET /home` — スマホ向け編集画面（タイル方式）。未認証の場合は管理キーの入力画面。
- `POST /home/login` — 管理キーで認証し、長期 Cookie（約400日）を発行する。一度認証した端末では以後ずっと認証済み。
- `GET /healthz` — 死活確認。

## 認証

管理キーは既存の aruno-clicker-config と共通です（同じ D1 の
`admin_settings` に保存されたキーのハッシュと照合）。Worker の
シークレット `ADMIN_TOKEN` が設定されている場合はそちらも受け付けます。

## データ

- 配置データは形式 v2（タイル方式、2026-11-20）です。各タイルが一意の
  id を持ち、16マス（slots）と配置外タイル（pool）で構成されます。
  v1 形式で保存されたデータは、読み込み時に自動で v2 へ変換されます。
- 部品の種類：アプリ起動／コピー／リンク（Web）／アプリダウンロード／
  アプリ更新／設定／せり上がりメニュー／Wi-Fi／機内モード／ラベル／空白。
- 編集画面はタイル方式です：16マスを上部に固定表示し、タイルを作って
  機能と紐づけ（アプリ・文字列・URL）を設定し、ドラッグまたはタップで
  配置します。アプリの紐づけはアプリ名の一覧から選べます。
- サイトのフォントは「コーポレート・ロゴ（ラウンド）Bold」
  （LOGOTYPE.JP、SIL Open Font License 1.1）を woff2 化し、
  `assets/corporate-logo-rounded-bold.woff2` を CDN 経由で読み込みます。
- D1 は aruno-clicker-config と同じデータベースを使い、この Worker は
  自分のテーブル `home_layout_config` / `home_layout_history` だけを使います。
- 初期データ（`migrations/0001_home_layout.sql` の seed）は、code36 の
  現在の HOME 配置をそのままデータ化したものです
  （`initial-layout.json` が同じ内容の参照用コピー）。
  - 争奪戦・QR・豚は、URL が頻繁に差し替わるため、最終URLではなく
    従来どおり Worker の `/tiker/link/1〜3`（転送先）を指します。
  - コピー関係（ID・コピー・リンク）は紙2枚アイコンに統一済みです。
  - Chrome 枠は「Webショートカット＋ブラウザ指定（com.android.chrome）」で表します。
  - 各更新は、項目をデータで持つ「せり上がりメニュー」部品（2列表示・最大8項目）です。

## なぜ別 Worker なのか

本番の aruno-clicker-config Worker には、GitHub リポジトリに無い
`/tiker/*` のページがデプロイされています。リポジトリから再デプロイすると
それらが消えるため、既存 Worker には触らず、別 Worker として追加しています。

## デプロイ

GitHub Actions の `Deploy ARUNO home layout Worker`（手動実行）を使います。

1. `npm test`
2. `wrangler deploy`
3. `/healthz` と `/v1/home-layout` の確認

テーブル作成と初期データ投入は、Worker が初回アクセス時に D1 バインディング
経由で自動で行います（aruno-clicker-config Worker の実行時スキーマ確保と
同じ方式）。そのため、デプロイ側に D1 API へのアクセス権は不要です。
`migrations/0001_home_layout.sql` は同じ内容を SQL として残した参照用です。

## テスト

```sh
npm test
```
