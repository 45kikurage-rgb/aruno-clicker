# ARUNO CLICKER Remote Config Worker

管理端末で変更した2件のTikTok起動URLを、全Android端末へ配信するCloudflare Workerです。D1の単一設定行を正本とし、更新履歴を直近20件だけ保持します。

## API契約

AndroidアプリにはWorkerルート（標準は `https://aruno-clicker-config.45kikurage.workers.dev/`）または `/v1/config` を同期サーバーURLとして設定できます。どちらも同じ動作です。

### `GET /` または `GET /v1/config`

認証不要。アプリのスタート押下時に取得します。

```json
{
  "url1": "https://lite.tiktok.com/t/ZS9AJY6f4n6Sw-GQnDP/",
  "url2": "https://lite.tiktok.com/t/ZS9rdoB6rsLHp-UHtJt/",
  "configVersion": 1,
  "updatedAt": "2026-09-22T00:00:00.000Z",
  "schemaVersion": 1
}
```

`Cache-Control: no-store`を返すため、HTTPキャッシュではなくD1の最新設定を確認します。

### `PUT /` または `PUT /v1/config`

管理端末専用です。

```http
Authorization: Bearer <ADMIN_TOKEN>
Content-Type: application/json
```

```json
{
  "url1": "https://lite.tiktok.com/t/NEW_URL_1/",
  "url2": "https://lite.tiktok.com/t/NEW_URL_2/",
  "expectedConfigVersion": 1
}
```

Android側の必須契約は`url1`と`url2`です。`expectedConfigVersion`は任意ですが、管理端末は更新前のGETで得た値を送ることを推奨します。競合時は`409 version_conflict`となります。成功時は更新後のGETと同じ形式を返します。

URLはHTTPSかつ次のTikTokホストだけを許可します。

- `lite.tiktok.com`
- `www.tiktok.com`
- `tiktok.com`
- `m.tiktok.com`
- `vm.tiktok.com`
- `vt.tiktok.com`

本文上限は8 KiB、URL上限は各2,048文字です。URL内のユーザー名・パスワードは拒否します。

### `GET /v1/history`

Bearer認証が必要です。直近20件を新しい順で返します。通常端末は使用しません。

### エラー

| HTTP | `error.code` | 意味 |
|---:|---|---|
| 400 | `validation_error` | URLまたはJSON項目が不正 |
| 401 | `unauthorized` | 管理トークンが不正 |
| 409 | `version_conflict` | 読み込み後に別の更新あり |
| 413 | `payload_too_large` | 本文が8 KiB超 |
| 415 | `unsupported_media_type` | JSON以外 |
| 503 | `server_not_configured` | `ADMIN_TOKEN`未設定 |

## 初回デプロイ

Node.js 20以上を使用します。

```bash
cd cloudflare-worker
npm install
npx wrangler login
npx wrangler d1 create aruno-clicker-config
```

`wrangler.toml.example`を`wrangler.toml`へコピーし、D1作成結果の`database_id`を設定します。ブラウザ管理画面を使わない場合は`ALLOWED_ADMIN_ORIGIN`を削除します。

ローカルD1へmigrationを適用してテストできます。

```bash
npx wrangler d1 migrations apply aruno-clicker-config --local
```

本番D1へmigrationを適用し、管理トークンをWorker Secretとして登録してからデプロイします。トークン値をソースや`wrangler.toml`へ記載しないでください。

```bash
npx wrangler d1 migrations apply aruno-clicker-config --remote
npx wrangler secret put ADMIN_TOKEN
npm run deploy
```

強いランダムトークンの生成例:

```bash
openssl rand -base64 48
```

## 動作確認

```bash
curl https://YOUR_WORKER.workers.dev/
```

シェル履歴に管理トークンを直接残さない更新例:

```bash
read -s ADMIN_TOKEN_VALUE
curl -X PUT https://YOUR_WORKER.workers.dev/ \
  -H "Authorization: Bearer ${ADMIN_TOKEN_VALUE}" \
  -H "Content-Type: application/json" \
  --data '{"url1":"https://lite.tiktok.com/t/NEW_URL_1/","url2":"https://lite.tiktok.com/t/NEW_URL_2/","expectedConfigVersion":1}'
unset ADMIN_TOKEN_VALUE
```

ユニットテスト:

```bash
npm test
```

## Android同期方針

1. 全端末はスタート押下時にGETします。
2. 成功時は`url1`、`url2`、`configVersion`を端末へ保存して使用します。
3. 通信失敗時は端末に最後に正常保存したURLへフォールバックします。
4. 管理端末はGET後、`expectedConfigVersion`を付けてPUTします。
5. PUT成功後は返却値を管理端末にも保存します。

日付の最初のスタート判定はAndroid端末側が日本時間（0:00〜24:00）で行います。サーバーは全端末共通URLの配信だけを担当します。

## CORSと管理情報

- AndroidのHTTP通信にブラウザCORSは適用されません。
- ブラウザ管理画面が必要な場合だけ、`ALLOWED_ADMIN_ORIGIN`へ完全一致する1オリジンを設定します。`*`は返しません。
- 公開GETに秘密情報や履歴は含めません。
- PUTと履歴取得だけBearer認証を要求します。
- トークン漏えい時は`npx wrangler secret put ADMIN_TOKEN`で交換します。

参照: [Cloudflare D1](https://developers.cloudflare.com/d1/)、[Worker Secrets](https://developers.cloudflare.com/workers/configuration/secrets/)
