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
