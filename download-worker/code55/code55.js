import previous from './live-code53.js';
export const RELEASE={channel:'test',package:'com.aruno.arunomatic',version_code:55,version_name:'v0.3.1-safe-task-start',apk_url:'https://download.aruno-id.com/apk/arunomatic-code55.apk',sha256:'31a1186bb78b7d84276d3661d012f73529591f78b0207bd76e6a836e3bae8d15',commit:'78f7cf0b7fd84ddc41b4b37bddb7a653dc076280',notes:'共通Task選択・停止確認後の安全開始・遅延指示隔離のテスト版。外部クリッカーは手動停止してください。15台実機完走は未確認です。'};
export const KEY='releases/arunomatic/v0.3.1-safe-task-start/ARUNOMATIC_v0.3.1-safe-task-start-code55.apk';
const filename='ARUNOMATIC_v0.3.1-safe-task-start-code55.apk',headers={'cache-control':'no-store','x-content-type-options':'nosniff'};
async function ready(env){const o=await env.APK_BUCKET.head(KEY);return o?.size===182315&&o.customMetadata?.sha256===RELEASE.sha256&&o.customMetadata?.filename===filename;}
async function apk(request,env){
 if(!await ready(env))return new Response('TEST_NOT_AVAILABLE',{status:503,headers});
 const o=request.method==='HEAD'?await env.APK_BUCKET.head(KEY):await env.APK_BUCKET.get(KEY);
 if(!o)return new Response('TEST_NOT_AVAILABLE',{status:503,headers});
 return new Response(request.method==='HEAD'?null:o.body,{headers:{...headers,'content-type':'application/vnd.android.package-archive','content-length':String(o.size),'content-disposition':'attachment; filename="'+filename+'"','x-apk-sha256':RELEASE.sha256}});
}
const banner='<section id="code55-test" style="padding:18px;margin:16px 0;border:2px solid #f59e0b;border-radius:12px"><strong>テスト版 code55 / v0.3.1-safe-task-start</strong><p>共通Task選択・安全遠隔開始。運用版code52は維持しています。外部クリッカーは手動停止。15台実機完走は未確認です。</p><a class="download test" href="/arunomatic/code55">code55 テスト版をダウンロード</a></section>';
export function wrap(base){return {...base,async fetch(request,env,ctx){
 const path=new URL(request.url).pathname;
 const owns=['/arunomatic/test.json','/apk/arunomatic-code55.apk','/apk/arunomatic-test.apk','/arunomatic/code55','/arunomatic/code55/'].includes(path);
 if(owns&&!['GET','HEAD'].includes(request.method))return new Response('Method Not Allowed',{status:405,headers:{...headers,allow:'GET, HEAD'}});
 if(path==='/arunomatic/test.json'){
  if(!await ready(env))return Response.json({error:'TEST_NOT_AVAILABLE'},{status:503,headers});
  return new Response(request.method==='HEAD'?null:JSON.stringify(RELEASE),{headers:{...headers,'content-type':'application/json; charset=utf-8'}});
 }
 if(['/apk/arunomatic-code55.apk','/apk/arunomatic-test.apk'].includes(path))return apk(request,env);
 if(path==='/arunomatic/code55'||path==='/arunomatic/code55/'){
  if(!await ready(env))return new Response('TEST_NOT_AVAILABLE',{status:503,headers});
  const html='<!doctype html><html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ARUNOMATIC code55 テスト版</title><body style="font-family:sans-serif;max-width:720px;margin:auto;padding:24px"><h1>ARUNOMATIC code55 テスト版</h1>'+banner+'<p>既存固定署名・package com.aruno.arunomatic。既存アプリへ上書き更新してください。</p><p>左＝Novaドック右端から2個目、右＝右端から1個目。カテゴリー00〜07。「全て」「最終」は端末ごとの確定配置に従います。</p><p>停止を確認できた端末だけ新Taskを開始します。停止確認はARUNOMATIC内部の停止確認です。</p><p><a href="/apk/arunomatic-code55.apk">APKをダウンロード / versionCode 55</a></p><p>SHA-256: '+RELEASE.sha256+'</p><p>15台実機完走と上書き更新の実機受入は未確認です。まず1台でcode53画面復帰を含む確認を実施してください。</p><a href="/arunomatic">運用版・既存ダウンロード</a></body></html>';
  return new Response(request.method==='HEAD'?null:html,{headers:{...headers,'content-type':'text/html; charset=utf-8'}});
 }
 const response=await base.fetch(request,env,ctx);
 if((path==='/arunomatic'||path==='/arunomatic/')&&request.method==='GET'&&response.ok&&await ready(env)){
  const html=(await response.text()).replace('<main>','<main>'+banner), h=new Headers(response.headers);h.delete('content-length');h.set('cache-control','no-store');return new Response(html,{status:response.status,headers:h});
 }
 return response;
}};}
export default wrap(previous);
