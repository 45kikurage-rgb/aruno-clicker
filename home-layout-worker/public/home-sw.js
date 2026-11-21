self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.mode !== "navigate" || request.method !== "GET") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || url.pathname !== "/home") return;

  // Always consult the server for the current session and editor.
  event.respondWith(fetch(request, { cache: "no-store" }).catch(() => new Response(
    `<!doctype html>
<html lang="ja"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ホーム配置 | オフライン</title>
<body style="font: 1rem system-ui; margin: 2rem; color: #193b47">
<main><h1>インターネットに接続できません</h1>
<p>接続が戻ったら、ホーム配置を開き直してください。</p>
<a href="/home">再読み込み</a></main></body></html>`,
    { status: 503, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" } },
  )));
});
