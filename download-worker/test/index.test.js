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
