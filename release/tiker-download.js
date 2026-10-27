// This module is installed as a thin wrapper around the exact live download Worker.
// __LIVE_MAIN__ and __RELEASE_FACTS__ are filled only after verifying the candidate.
import legacy from './__LIVE_MAIN__';
export const RELEASE = __RELEASE_FACTS__;
export const KEY = 'releases/tiker/0.6.0-code17/' + RELEASE.sha256 + '/Aru-Tiker-v0.6.0-code17.apk';

export function page() {
  return `<!doctype html><html lang="ja"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#101713"><title>Aru-Tiker ダウンロード</title><style>
  *{box-sizing:border-box}body{margin:0;background:#101713;color:#eff8f1;font-family:system-ui,-apple-system,sans-serif;line-height:1.75}main{max-width:560px;margin:auto;padding:28px 20px 48px}.tag{display:inline-block;color:#9ee7b4;font-weight:700;font-size:13px;letter-spacing:.1em}h1{font-size:36px;margin:8px 0 0;line-height:1.2}h2{font-size:19px;margin:0 0 12px}.lead{color:#c1cec5;margin:14px 0 24px}.card{border:1px solid #3c5345;background:#19261e;border-radius:20px;padding:22px;margin:18px 0}.version{font-weight:800;font-size:23px;margin:0}.sub{font-size:14px;color:#c1cec5;margin:6px 0 18px}.download{display:block;padding:17px 12px;text-align:center;background:#a6efb9;color:#102d19;text-decoration:none;font-weight:800;border-radius:13px;min-height:56px}.download:focus-visible,a:focus-visible,summary:focus-visible{outline:3px solid white;outline-offset:4px}ol{padding-left:24px;margin-bottom:0}li{padding-bottom:10px}p{overflow-wrap:anywhere}small{color:#bdcbc1}a{color:#b6f3c5}details{margin-top:18px}summary{cursor:pointer;min-height:44px;padding:8px 0}code{font-size:12px;overflow-wrap:anywhere}.note{font-size:14px;color:#c1cec5}.badge{display:inline-block;font-size:12px;border:1px solid #648871;border-radius:6px;padding:2px 8px;margin-bottom:10px}
  </style></head><body><main><span class="tag">ARUNO / ANDROID</span><h1>Aru-Tiker</h1><p class="lead">URLを登録した順に開き、指定した秒数で次へ進みます。</p>
  <section class="card" aria-label="アプリをダウンロード"><span class="badge">通常版・1種類</span><p class="version">0.6.0 <small>／ code17</small></p><p class="sub">既存の通常版と同じ署名・パッケージ。Android 8.0以上。</p><a class="download" href="/apk/aru-tiker-code17.apk" download="Aru-Tiker-v0.6.0-code17.apk">アプリをダウンロード</a><p class="note">通常版はアンインストールせず、上書き更新してください。旧ver.S・シェア試験版は別アプリのため、このAPKでは上書きされません。</p></section>
  <section class="card"><h2>メイン端末での準備</h2><ol><li>アプリ名を長押しし、詳細設定で既存の管理端末モード・管理キーを設定します。</li><li>ホームの「設定」→「URL入力」でリンクを貼り付けます。最大120件、10件ずつ切り替えて編集できます。</li><li>1〜5番の秒数と、6番以降の共通秒数を決めて「データ送信」を押します。</li></ol></section>
  <section class="card"><h2>各端末での使い方</h2><ol><li>「データダウンロード」でURLと秒数を取得します。動画ファイルのダウンロードではありません。</li><li>「動画スタート」で保存済みの一覧を順に開きます。実行中はフロートまたは通知から停止できます。</li><li>画面OFF・ロック・ユーザー補助の中断時は停止します。再開は「動画スタート」を押してください。</li></ol></section>
  <p class="note">最初は最後のURLで停止・前面表示を確認してから計時・各10秒・先頭から開始です。メイン端末の設定で変更できます。対象はHTTPSのTikTok URLです。</p><p class="note">この版はURLを開く動作を対象としています。ポップアップの×を閉じる動作やスワイプは実行しません。動画の再生状況は判定しません。</p>
  <details><summary>バージョンと検証情報</summary><p>ソフトウェア試験・全体ビルド・既存署名との照合を実施。実機での上書き更新・TikTok Lite動作は未確認です。</p><p>APK SHA-256<br><code>${RELEASE.sha256}</code></p><p>パッケージ：jp.aruno.clicker<br>サイズ：${(RELEASE.bytes/1024/1024).toFixed(1)} MB</p></details><p class="note">ARUNOMATICは別アプリです。<a href="/arunomatic">ARUNOMATICの配布ページ</a></p></main></body></html>`;
}

export default {
  async fetch(request, env, ctx) {
    const path = new URL(request.url).pathname;
    if (['/','/index.html','/tiker','/tiker/','/tiker/latest.json','/apk/aruno-clicker.apk','/apk/aru-tiker-code17.apk'].includes(path)) {
      if (!['GET','HEAD'].includes(request.method)) return new Response('Method Not Allowed',{status:405,headers:{Allow:'GET, HEAD'}});
      if (path.endsWith('.apk')) {
        const object = await env.APK_BUCKET.get(KEY);
        if (!object || object.customMetadata?.sha256 !== RELEASE.sha256 || object.size !== RELEASE.bytes) return new Response('APK準備中です',{status:503,headers:{'Cache-Control':'no-store'}});
        return new Response(request.method === 'HEAD' ? null : object.body,{headers:{
          'Content-Type':'application/vnd.android.package-archive','Content-Disposition':'attachment; filename="Aru-Tiker-v0.6.0-code17.apk"',
          'Content-Length':String(object.size),'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','X-Checksum-SHA256':RELEASE.sha256,
        }});
      }
      const json = path.endsWith('.json');
      return new Response(request.method === 'HEAD' ? null : json ? JSON.stringify(RELEASE) : page(), {headers:{
        'Content-Type':json?'application/json; charset=utf-8':'text/html; charset=utf-8','Cache-Control':'no-store',
        'X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer',
        'Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'",
      }});
    }
    return legacy.fetch(request, env, ctx);
  },
};
