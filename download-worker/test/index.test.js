import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

function environment(body = new Uint8Array([1, 2, 3])) {
  return {
    APK_BUCKET: {
      async get(key) {
        if (!key.startsWith("apk/")) return null;
        return {
          body,
          size: body.byteLength,
          httpEtag: '"test-etag"',
          customMetadata: { sha256: "abc123" },
          writeHttpMetadata() {},
        };
      },
    },
  };
}

test("serves the download page", async () => {
  const response = await worker.fetch(new Request("https://download.aruno-id.com/"), environment());
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /^text\/html/);
  const html = await response.text();
  assert.match(html, /ARUNO CLICKER ver\.S/);
  assert.match(html, /Redmi A3 シェア試験版/);
  assert.match(html, /\/apk\/aruno-clicker\.apk/);
  assert.match(html, /\/apk\/aruno-clicker-ver-s\.apk/);
  assert.match(html, /\/apk\/aruno-clicker-share-accessibility-test\.apk/);
});

test("update manifest is public, uncached and validates release identity", async () => {
  const manifest={package:"com.aruno.arunomatic",version_code:34,version_name:"v0.1.6-airledger2",download_page:"https://download.aruno-id.com/arunomatic",sha256:"a".repeat(64)};
  const env={APK_BUCKET:{get:async key=>key==="releases/arunomatic/latest.json"?{text:async()=>JSON.stringify(manifest)}:null}};
  const response=await worker.fetch(new Request("https://download.aruno-id.com/arunomatic/latest.json"),env);
  assert.equal(response.status,200);assert.equal(response.headers.get("cache-control"),"no-store");assert.equal((await response.json()).version_code,34);
  manifest.package="wrong.package";assert.equal((await worker.fetch(new Request("https://download.aruno-id.com/arunomatic/latest.json"),env)).status,503);
});

test("serves ARUNOMATIC sub-device page with fixed QR and APK link", async () => {
  const response = await worker.fetch(
    new Request("https://download.aruno-id.com/arunomatic"),
    environment(),
  );
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /^text\/html/);
  const html = await response.text();
  assert.match(html, /ARUNOMATIC/);
  assert.match(html, /端末03・05・06・99 共通/);
  assert.doesNotMatch(html, /v0\.1\.5|ページフラグ診断|code 19/);
  assert.match(html, /v0\.1\.6-airledger2/);
  assert.match(html, /code 34/);
  assert.match(html, /最新版/);
  assert.match(html, /ユーザー番号を自動取得/);
  assert.match(html, /重複はユーザー番号で判定/);
  assert.match(html, /カード情報は自端末だけに暗号化保存/);
  assert.match(html, /指紋認証/);
  assert.match(html, /ARUNOMATIC内の指紋認証は不要/);
  assert.match(html, /後から登録・変更/);
  assert.match(html, /中央管理台帳へ自動同期/);
  assert.doesNotMatch(html, /同一カード重複判定|指紋認証必須/);
  assert.doesNotMatch(html, /href="\/apk\/arunomatic\.apk"/);
  assert.match(html, /\/apk\/arunomatic-v016-test\.apk/);
        assert.match(html, /data:image\/svg\+xml;base64,/);
  assert.match(html, /download\.aruno-id\.com\/arunomatic/);
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("serves APKs with safe download headers", async () => {
  const response = await worker.fetch(
    new Request("https://download.aruno-id.com/apk/aruno-clicker.apk"),
    environment(),
  );
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "application/vnd.android.package-archive");
  assert.equal(response.headers.get("content-length"), "3");
  assert.equal(response.headers.get("x-checksum-sha256"), "abc123");
  assert.equal((await response.arrayBuffer()).byteLength, 3);
});

test("serves both ARUNOMATIC APK versions with clear filenames", async () => {
  const stable = await worker.fetch(
    new Request("https://download.aruno-id.com/apk/arunomatic.apk"),
    environment(),
  );
  assert.equal(stable.status, 200);
  assert.equal(stable.headers.get("content-type"), "application/vnd.android.package-archive");
  assert.match(stable.headers.get("content-disposition"), /ARUNOMATIC_SUB_v0\.1\.5\.apk/);

  const testBuild = await worker.fetch(
    new Request("https://download.aruno-id.com/apk/arunomatic-v016-test.apk"),
    environment(),
  );
  assert.equal(testBuild.status, 200);
  assert.equal(testBuild.headers.get("content-type"), "application/vnd.android.package-archive");
  assert.match(testBuild.headers.get("content-disposition"), /ARUNOMATIC_v0\.1\.6-airledger2-code34\.apk/);
  assert.equal(testBuild.headers.get("x-checksum-sha256"), "abc123");
  assert.equal(testBuild.headers.get("cache-control"), "no-store");
});

test("returns 404 when an APK is missing", async () => {
  const env = environment();
  env.APK_BUCKET.get = async () => null;
  const response = await worker.fetch(
    new Request("https://download.aruno-id.com/apk/aruno-clicker-ver-s.apk"),
    env,
  );
  assert.equal(response.status, 404);
});

test("serves the isolated share accessibility test APK", async () => {
  const response = await worker.fetch(
    new Request("https://download.aruno-id.com/apk/aruno-clicker-share-accessibility-test.apk"),
    environment(),
  );
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "application/vnd.android.package-archive");
  assert.match(
    response.headers.get("content-disposition"),
    /ARUNO_CLICKER_share_accessibility_test\.apk/,
  );
});


test("serves integrated ARUNOMATIC AIR balance trial APK", async () => {
  const response = await worker.fetch(
    new Request("https://download.aruno-id.com/apk/arunomatic-air-balance-test1.apk"),
    environment(),
  );
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "application/vnd.android.package-archive");
  assert.match(response.headers.get("content-disposition"), /ARUNOMATIC_AIR_BALANCE_TRIAL1_CODE21\.apk/);
  assert.equal(response.headers.get("cache-control"), "no-store");
});



test("code36 page switches only with the matching published manifest; code34 remains available",async()=>{
 const m={package:'com.aruno.arunomatic',version_code:36,version_name:'v0.1.7-devicecontrol2',download_page:'https://download.aruno-id.com/arunomatic',apk_url:'https://download.aruno-id.com/apk/arunomatic-code36.apk',sha256:'21eda0634986500afc3744f50487628a1858714fc25a6ee149bd3c9e24e3f479'};
 const seen=[];const fallback=environment();
 const env={APK_BUCKET:{get:async key=>{seen.push(key);if(key==='releases/arunomatic/latest.json')return{text:async()=>JSON.stringify(m)};if(key.startsWith('releases/arunomatic/v0.1.7-devicecontrol2/'))return fallback.APK_BUCKET.get('apk/test');return fallback.APK_BUCKET.get(key);}}};
 let r=await worker.fetch(new Request('https://download.aruno-id.com/arunomatic'),env);let html=await r.text();
 assert.match(html,/code 36/);assert.match(html,/実機未確認/);assert.match(html,/端末通信はOFF/);assert.match(html,/href="\/apk\/arunomatic-code36.apk"/);assert.match(html,/href="\/apk\/arunomatic-v016-test.apk"/);
 r=await worker.fetch(new Request('https://download.aruno-id.com/apk/arunomatic-code36.apk'),env);assert.equal(r.status,200);assert.match(r.headers.get('content-disposition'),/code36/);assert.equal(r.headers.get('cache-control'),'no-store');
 assert.ok(seen.includes('releases/arunomatic/v0.1.7-devicecontrol2/ARUNOMATIC_v0.1.7-devicecontrol2-code36.apk'));
 m.sha256='b'.repeat(64);r=await worker.fetch(new Request('https://download.aruno-id.com/arunomatic'),env);assert.doesNotMatch(await r.text(),/code 36/);
});

test("code37 page switches only with the matching published manifest; code34 remains available",async()=>{
 const m={package:'com.aruno.arunomatic',version_code:37,version_name:'v0.1.7-air-edit1',download_page:'https://download.aruno-id.com/arunomatic',apk_url:'https://download.aruno-id.com/apk/arunomatic-code37.apk',sha256:'adc6e54fa64197ea015f544f002a2d9e4e2e51e25c4dbc36a88aa7a43b3bdb83'};
 const seen=[];const fallback=environment();
 const env={APK_BUCKET:{get:async key=>{seen.push(key);if(key==='releases/arunomatic/latest.json')return{text:async()=>JSON.stringify(m)};if(key.startsWith('releases/arunomatic/v0.1.7-air-edit1/'))return fallback.APK_BUCKET.get('apk/test');return fallback.APK_BUCKET.get(key);}}};
 let r=await worker.fetch(new Request('https://download.aruno-id.com/arunomatic'),env);let html=await r.text();
 assert.match(html,/code 37/);assert.match(html,/1件削除・上書き・番号訂正/);assert.match(html,/手動で残高画面へ戻して/);assert.match(html,/href="\/apk\/arunomatic-code36.apk"/);assert.match(html,/実機未確認/);assert.match(html,/端末通信はOFF/);assert.match(html,/href="\/apk\/arunomatic-code37.apk"/);assert.match(html,/href="\/apk\/arunomatic-v016-test.apk"/);
 r=await worker.fetch(new Request('https://download.aruno-id.com/apk/arunomatic-code37.apk'),env);assert.equal(r.status,200);assert.match(r.headers.get('content-disposition'),/code37/);assert.equal(r.headers.get('cache-control'),'no-store');
 assert.ok(seen.includes('releases/arunomatic/v0.1.7-air-edit1/ARUNOMATIC_v0.1.7-air-edit1-code37.apk'));
 m.sha256='b'.repeat(64);r=await worker.fetch(new Request('https://download.aruno-id.com/arunomatic'),env);assert.doesNotMatch(await r.text(),/code 37/);
});

test("code38 page switches only with the matching published manifest; code34 remains available",async()=>{
 const m={package:'com.aruno.arunomatic',version_code:38,version_name:'v0.1.7-air-display1',download_page:'https://download.aruno-id.com/arunomatic',apk_url:'https://download.aruno-id.com/apk/arunomatic-code38.apk',sha256:'8fcb498e31f2e32e8cb9c79d3a29ea07e5852b43ffb5f6e02ecbdbd109e6df45'};
 const seen=[];const fallback=environment();
 const env={APK_BUCKET:{get:async key=>{seen.push(key);if(key==='releases/arunomatic/latest.json')return{text:async()=>JSON.stringify(m)};if(key.startsWith('releases/arunomatic/v0.1.7-air-display1/'))return fallback.APK_BUCKET.get('apk/test');return fallback.APK_BUCKET.get(key);}}};
 let r=await worker.fetch(new Request('https://download.aruno-id.com/arunomatic'),env);let html=await r.text();
 assert.match(html,/code 38/);assert.match(html,/右端ボタンを縦並び/);assert.match(html,/前回残高/);assert.match(html,/1件削除・上書き・番号訂正/);assert.match(html,/手動で残高画面へ戻して/);assert.match(html,/href="\/apk\/arunomatic-code36.apk"/);assert.match(html,/実機未確認/);assert.match(html,/端末通信はOFF/);assert.match(html,/href="\/apk\/arunomatic-code38.apk"/);assert.match(html,/href="\/apk\/arunomatic-v016-test.apk"/);
 r=await worker.fetch(new Request('https://download.aruno-id.com/apk/arunomatic-code38.apk'),env);assert.equal(r.status,200);assert.match(r.headers.get('content-disposition'),/code38/);assert.equal(r.headers.get('cache-control'),'no-store');
 assert.ok(seen.includes('releases/arunomatic/v0.1.7-air-display1/ARUNOMATIC_v0.1.7-air-display1-code38.apk'));
 m.sha256='b'.repeat(64);r=await worker.fetch(new Request('https://download.aruno-id.com/arunomatic'),env);assert.doesNotMatch(await r.text(),/code 38/);
});

test("code39 page switches only with the matching published manifest; code34 remains available",async()=>{
 const m={package:'com.aruno.arunomatic',version_code:39,version_name:'v0.1.8-device-test1',download_page:'https://download.aruno-id.com/arunomatic',apk_url:'https://download.aruno-id.com/apk/arunomatic-code39.apk',sha256:'1eccb60e74ac3adfb48c5e97106a83cfc3aab802df293cac76e1f385ab4db0b5'};
 const seen=[];const fallback=environment();
 const env={APK_BUCKET:{get:async key=>{seen.push(key);if(key==='releases/arunomatic/latest.json')return{text:async()=>JSON.stringify(m)};if(key.startsWith('releases/arunomatic/v0.1.8-device-test1/'))return fallback.APK_BUCKET.get('apk/test');return fallback.APK_BUCKET.get(key);}}};
 let r=await worker.fetch(new Request('https://download.aruno-id.com/arunomatic'),env);let html=await r.text();
 assert.match(html,/code 39/);assert.match(html,/COMMAND操作は実機未確認/);assert.match(html,/10分間/);assert.match(html,/ホーム移動/);assert.match(html,/href="\/apk\/arunomatic-code38.apk"/);assert.match(html,/href="\/apk\/arunomatic-code39.apk"/);assert.match(html,/href="\/apk\/arunomatic-v016-test.apk"/);assert.doesNotMatch(html,/端末通信はOFF/);
 r=await worker.fetch(new Request('https://download.aruno-id.com/apk/arunomatic-code39.apk'),env);assert.equal(r.status,200);assert.match(r.headers.get('content-disposition'),/code39/);assert.equal(r.headers.get('cache-control'),'no-store');
 assert.ok(seen.includes('releases/arunomatic/v0.1.8-device-test1/ARUNOMATIC_v0.1.8-device-test1-code39.apk'));
 m.sha256='b'.repeat(64);r=await worker.fetch(new Request('https://download.aruno-id.com/arunomatic'),env);assert.doesNotMatch(await r.text(),/code 39/);
});


test("code41 trial appears only for verified candidate without changing latest", async () => {
  const manifest = {package:"com.aruno.arunomatic",version_code:39,version_name:"v0.1.8-device-test1",download_page:"https://download.aruno-id.com/arunomatic",apk_url:"https://download.aruno-id.com/apk/arunomatic-code39.apk",sha256:"1eccb60e74ac3adfb48c5e97106a83cfc3aab802df293cac76e1f385ab4db0b5"};
  const key="releases/arunomatic/v0.1.9-winning-share1/ARUNOMATIC_v0.1.9-winning-share1-code41.apk";
  let sha="942cdf43ae4d4e23d8385b955b0f0631c741ec43d04adb874ca459b4c3f42dba";
  const fallback=environment();
  const env={APK_BUCKET:{head:async k=>k===key?{customMetadata:{sha256:sha}}:null,get:async k=>k==="releases/arunomatic/latest.json"?{text:async()=>JSON.stringify(manifest)}:k===key?fallback.APK_BUCKET.get("apk/test"):null}};
  const page=async()=>await (await worker.fetch(new Request("https://download.aruno-id.com/arunomatic"),env)).text();
  assert.match(await page(),/href="\/apk\/arunomatic-code41.apk"/);
  assert.match(await page(),/最新版 v0.1.8-device-test1 \/ code 39/);
  const latest=await worker.fetch(new Request("https://download.aruno-id.com/arunomatic/latest.json"),env);
  assert.deepEqual(await latest.json(),manifest);
  const apk=await worker.fetch(new Request("https://download.aruno-id.com/apk/arunomatic-code41.apk"),env);
  assert.equal(apk.status,200);assert.match(apk.headers.get("content-disposition"),/code41.apk/);
  sha="wrong";assert.doesNotMatch(await page(),/href="\/apk\/arunomatic-code41.apk"/);
});
test("announced code41 uses latest download card without trial or stale guidance", async () => {
  const manifest={package:"com.aruno.arunomatic",version_code:41,version_name:"v0.1.9-winning-share1",download_page:"https://download.aruno-id.com/arunomatic",apk_url:"https://download.aruno-id.com/apk/arunomatic-code41.apk",sha256:"942cdf43ae4d4e23d8385b955b0f0631c741ec43d04adb874ca459b4c3f42dba"};
  const env={APK_BUCKET:{get:async()=>({text:async()=>JSON.stringify(manifest)}),head:async()=>({customMetadata:{sha256:manifest.sha256}})}};
  const page=async()=>await (await worker.fetch(new Request(manifest.download_page),env)).text();
  const html=await page();
  assert.match(html,/最新版 v0.1.9-winning-share1 \/ code 41/);
  assert.match(html,/href="\/apk\/arunomatic-code41.apk">最新版 code 41/);
  assert.doesNotMatch(html,/実機確認用|更新通知への切り替えは実機確認後|まず端末03|端末通信はOFF/);
  assert.match(html,/href="\/apk\/arunomatic-v016-test.apk"/);
  const latest=await worker.fetch(new Request(manifest.download_page+"/latest.json"),env);
  assert.deepEqual(await latest.json(),manifest);
  assert.equal(latest.headers.get("cache-control"),"no-store");
  manifest.sha256="b".repeat(64);
  assert.doesNotMatch(await page(),/最新版.*code 41/);
});
