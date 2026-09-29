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

test("serves ARUNOMATIC sub-device page with fixed QR and APK link", async () => {
  const response = await worker.fetch(
    new Request("https://download.aruno-id.com/arunomatic"),
    environment(),
  );
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /^text\/html/);
  const html = await response.text();
  assert.match(html, /ARUNOMATIC/);
  assert.match(html, /サブ端末用/);
  assert.match(html, /v0\.1\.5 安定版/);
  assert.match(html, /v0\.1\.6-aircard-local8/);
  assert.match(html, /code 30/);
  assert.match(html, /AIRカード共有登録テスト版/);
  assert.match(html, /ページフラグ探索上限2000/);
  assert.match(html, /カード番号/);
  assert.match(html, /セキュリティコード/);
  assert.match(html, /指紋認証/);
  assert.match(html, /カード登録・再取得は指紋認証なし/);
  assert.match(html, /順に共有/);
  assert.match(html, /失敗AIRだけ/);
  assert.match(html, /同一カード重複判定/);
  assert.match(html, /\/apk\/arunomatic\.apk/);
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
  assert.match(testBuild.headers.get("content-disposition"), /ARUNOMATIC_v0\.1\.6-aircard-local8-code30\.apk/);
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

