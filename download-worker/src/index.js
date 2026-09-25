const APK_CONTENT_TYPE = "application/vnd.android.package-archive";

const DOWNLOADS = new Map([
  [
    "/apk/aruno-clicker.apk",
    {
      key: "apk/aruno-clicker.apk",
      filename: "ARUNO_CLICKER.apk",
    },
  ],
  [
    "/apk/aruno-clicker-ver-s.apk",
    {
      key: "apk/aruno-clicker-ver-s.apk",
      filename: "ARUNO_CLICKER_ver.S.apk",
    },
  ],
]);

const PAGE = `<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <meta name="color-scheme" content="dark">
  <meta name="theme-color" content="#111827">
  <title>ARUNO CLICKER</title>
  <style>
    :root { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #f8fafc; background: #070b14; }
    * { box-sizing: border-box; }
    body { min-height: 100dvh; margin: 0; display: grid; place-items: center; padding: 24px; background: radial-gradient(circle at top, #172554 0, #0f172a 42%, #070b14 100%); }
    main { width: min(100%, 560px); }
    h1 { margin: 0 0 8px; text-align: center; font-size: clamp(28px, 8vw, 42px); letter-spacing: .04em; }
    .lead { margin: 0 0 28px; text-align: center; color: #cbd5e1; }
    .downloads { display: grid; gap: 16px; }
    .card { padding: 20px; border: 1px solid #334155; border-radius: 18px; background: rgb(15 23 42 / 88%); box-shadow: 0 18px 50px rgb(0 0 0 / 25%); }
    .name { margin: 0 0 14px; font-size: 20px; font-weight: 750; }
    .download { display: block; width: 100%; padding: 15px 18px; border-radius: 12px; color: #082f49; background: #7dd3fc; font-weight: 800; text-align: center; text-decoration: none; }
    .download:focus-visible { outline: 3px solid #f8fafc; outline-offset: 3px; }
    .note { margin: 22px 0 0; text-align: center; color: #94a3b8; font-size: 13px; }
  </style>
</head>
<body>
  <main>
    <h1>ARUNO CLICKER</h1>
    <p class="lead">ダウンロードするアプリを選択してください</p>
    <section class="downloads" aria-label="APKダウンロード">
      <article class="card">
        <p class="name">ARUNO CLICKER</p>
        <a class="download" href="/apk/aruno-clicker.apk">ダウンロード</a>
      </article>
      <article class="card">
        <p class="name">ARUNO CLICKER ver.S</p>
        <a class="download" href="/apk/aruno-clicker-ver-s.apk">ダウンロード</a>
      </article>
    </section>
    <p class="note">Android用APK</p>
  </main>
</body>
</html>`;

function pageResponse() {
  return new Response(PAGE, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "public, max-age=300",
      "X-Content-Type-Options": "nosniff",
      "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
      "Referrer-Policy": "no-referrer",
    },
  });
}

async function apkResponse(request, env, download) {
  const object = await env.APK_BUCKET.get(download.key);
  if (object === null) {
    return new Response("APK is not available.", { status: 404 });
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("Content-Type", APK_CONTENT_TYPE);
  headers.set("Content-Disposition", `attachment; filename="${download.filename}"`);
  headers.set("Content-Length", String(object.size));
  headers.set("Cache-Control", "public, max-age=60, must-revalidate");
  headers.set("ETag", object.httpEtag);
  headers.set("X-Content-Type-Options", "nosniff");
  const sha256 = object.customMetadata?.sha256;
  if (sha256) headers.set("X-Checksum-SHA256", sha256);

  if (request.method === "HEAD") {
    return new Response(null, { headers });
  }
  return new Response(object.body, { headers });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method Not Allowed", {
        status: 405,
        headers: { Allow: "GET, HEAD" },
      });
    }

    if (url.pathname === "/" || url.pathname === "/index.html") {
      return request.method === "HEAD"
        ? new Response(null, { headers: pageResponse().headers })
        : pageResponse();
    }

    const download = DOWNLOADS.get(url.pathname);
    if (download) return apkResponse(request, env, download);

    return new Response("Not Found", { status: 404 });
  },
};

