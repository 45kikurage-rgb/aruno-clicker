import previous from './code55.js';
export const RELEASE={channel:'test',package:'com.aruno.arunomatic',version_code:56,version_name:'v0.3.2-long-task-safe',apk_url:'https://download.aruno-id.com/apk/arunomatic-code56.apk',sha256:'4fde40f74e9f2216ea7e9bb70744f55a04130f7fa05752295f861785fff14cc8',commit:'b25b0f701b1295d7f6e529a472fb9dd46d4691f0',notes:'長時間Task安定化のテスト版code56。中央台帳の期限修正は反映済み。実機完走は未確認。普段の40〜60分Taskを1台で確認してから15台へ進めてください。運用版への変更なし。'};
export const KEY='releases/arunomatic/v0.3.2-long-task-safe/ARUNOMATIC_v0.3.2-long-task-safe-code56.apk';
const filename='ARUNOMATIC_v0.3.2-long-task-safe-code56.apk',bytes=185335;
const headers={'cache-control':'no-store','x-content-type-options':'nosniff'};
async function ready(env){const o=await env.APK_BUCKET.head(KEY);return o?.size===bytes&&o.customMetadata?.sha256===RELEASE.sha256&&o.customMetadata?.filename===filename;}
async function apk(request,env){
 if(!await ready(env))return new Response('TEST_NOT_AVAILABLE',{status:503,headers});
 const o=request.method==='HEAD'?await env.APK_BUCKET.head(KEY):await env.APK_BUCKET.get(KEY);
 if(!o||o.size!==bytes||o.customMetadata?.sha256!==RELEASE.sha256)return new Response('TEST_NOT_AVAILABLE',{status:503,headers});
 return new Response(request.method==='HEAD'?null:o.body,{headers:{...headers,'content-type':'application/vnd.android.package-archive','content-length':String(o.size),'content-disposition':'attachment; filename="'+filename+'"','x-apk-sha256':RELEASE.sha256}});
}
const banner='<section id="code56-test" style="padding:18px;margin:16px 0;border:2px solid #f59e0b;border-radius:12px"><strong>テスト版 code56 / 長時間Task安定化</strong><p>中央台帳の期限修正は反映済みです。普段の40〜60分Taskを使い、まず1台で確認してください。実機完走は未確認です。</p><a class="download test" href="/arunomatic/code56">code56 テスト版をダウンロード</a></section>';
export function wrap(base){return {...base,async fetch(request,env,ctx){
 const path=new URL(request.url).pathname;
 const manifests=['/arunomatic/test.json','/arunomatic/code56/test.json'];
 const downloads=['/apk/arunomatic-code56.apk','/apk/arunomatic-test.apk'];
 const pages=['/arunomatic/code56','/arunomatic/code56/'];
 const owns=[...manifests,...downloads,...pages].includes(path);
 if(owns&&!['GET','HEAD'].includes(request.method))return new Response('Method Not Allowed',{status:405,headers:{...headers,allow:'GET, HEAD'}});
 if(manifests.includes(path)){
  if(!await ready(env))return Response.json({error:'TEST_NOT_AVAILABLE'},{status:503,headers});
  return new Response(request.method==='HEAD'?null:JSON.stringify(RELEASE),{headers:{...headers,'content-type':'application/json; charset=utf-8'}});
 }
 if(downloads.includes(path))return apk(request,env);
 if(pages.includes(path)){
  if(!await ready(env))return new Response('TEST_NOT_AVAILABLE',{status:503,headers});
  const html='<!doctype html><html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ARUNOMATIC code56 テスト版</title><body style="font-family:sans-serif;max-width:720px;margin:auto;padding:24px"><h1>ARUNOMATIC code56 テスト版</h1>'+banner+'<p>versionCode 56 / v0.3.2-long-task-safe。既存アプリへ上書き更新する固定署名APKです。</p><p><a href="/apk/arunomatic-code56.apk">code56 APKをダウンロード</a></p><p>外部自動クリッカーを手動で止め、Taskが動いていない端末にインストールしてください。アンインストールやデータ初期化は不要です。</p><p>普段のTask（約47分など）で、まず1台の完了と次ページを確認します。その後2ページ連続、15台同時、5ページ連続の順で確認してください。START・STOPは利用者が手動で行います。</p><p>通信断の安全停止はARUNOMATICの次ページ・次Taskへの進行を止めます。外部クリッカーが停止したことを保証するものではありません。</p><p>このcodeより低い運用版へは、そのまま上書きして戻せません。</p><p>SHA-256: '+RELEASE.sha256+'</p><a href="/arunomatic">運用版・既存ダウンロード</a></body></html>';
  return new Response(request.method==='HEAD'?null:html,{headers:{...headers,'content-type':'text/html; charset=utf-8'}});
 }
 const response=await base.fetch(request,env,ctx);
 if((path==='/arunomatic'||path==='/arunomatic/')&&request.method==='GET'&&response.ok&&await ready(env)){
  const html=(await response.text()).replace('<main>','<main>'+banner),h=new Headers(response.headers);h.delete('content-length');h.set('cache-control','no-store');return new Response(html,{status:response.status,headers:h});
 }
 return response;
}};}
export default wrap(previous);
