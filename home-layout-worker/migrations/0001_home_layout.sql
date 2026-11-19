-- Aruno-Home/T HOME layout (format v1). New tables only, in the shared
-- aruno-clicker-config D1 database; existing tables are untouched.
-- Seed = the current code36 HOME layout converted to layout data
-- (copy slots unified to the copy icon; Chrome expressed as a web
-- shortcut with a browser parameter; the updates popup as a menu slot).

CREATE TABLE IF NOT EXISTS home_layout_config (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  layout_json TEXT NOT NULL,
  revision INTEGER NOT NULL CHECK (revision >= 1),
  updated_at TEXT NOT NULL,
  update_id TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS home_layout_history (
  revision INTEGER PRIMARY KEY,
  layout_json TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

INSERT OR IGNORE INTO home_layout_config (id, layout_json, revision, updated_at, update_id)
VALUES (1, '{"v":1,"revision":1,"updatedAt":"2026-11-19T00:00:00.000Z","slots":[{"kind":"web","title":"争奪戦","icon":"honey","url":"https://aruno-clicker-config.45kikurage.workers.dev/tiker/link/1"},{"kind":"web","title":"QR","icon":"qr","url":"https://aruno-clicker-config.45kikurage.workers.dev/tiker/link/2"},{"kind":"web","title":"豚","icon":"pig","url":"https://aruno-clicker-config.45kikurage.workers.dev/tiker/link/3"},{"kind":"airplane","title":"機内モード","icon":"airplane"},{"kind":"app","title":"Google Play","pkg":"com.android.vending"},{"kind":"app","title":"LINE","pkg":"jp.naver.line.android"},{"kind":"app","title":"TikTok Lite","pkg":"com.ss.android.ugc.tiktok.lite"},{"kind":"app","title":"Aru-Tiker","pkg":"jp.aruno.clicker"},{"kind":"web","title":"Chrome","icon":"pkg:com.android.chrome","url":"https://docs.google.com/spreadsheets/d/1-X9r9GETsqoV7ZuBv0vmygcbmAmq62g8-sFo_iEjZ_8/edit?usp=drivesdk","browser":"com.android.chrome"},{"kind":"app","title":"捨てメアド","pkg":"air.kukulive.mailnow"},{"kind":"settings","title":"設定","icon":"settings"},{"kind":"menu","title":"各更新","icon":"updates","items":[{"kind":"updateApp","title":"開発者プログラム更新","pkg":"com.google.android.gms"},{"kind":"updateApp","title":"TikTok Lite更新","pkg":"com.ss.android.ugc.tiktok.lite"},{"kind":"storage","title":"70%スト埋め","icon":"storage"},{"kind":"updateApp","title":"Chrome更新","pkg":"com.android.chrome"}]},{"kind":"copy","title":"ID","icon":"copy","copyType":"deviceId"},{"kind":"copy","title":"コピー","icon":"copy","copyType":"text","text":"adad333#"},{"kind":"copy","title":"リンク","icon":"copy","copyType":"url","url":"https://lightweight-links.pages.dev/b6t9wh3cy5kr.html"},{"kind":"wifi","title":"Wi-Fi","icon":"wifi"}]}', 1, '2026-11-19T00:00:00.000Z', 'initial');

INSERT OR IGNORE INTO home_layout_history (revision, layout_json, updated_at)
SELECT revision, layout_json, updated_at FROM home_layout_config WHERE id = 1;
