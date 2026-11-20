// Editor and login pages for the Aruno-Home/T HOME layout site.
// Tile-based editor (decided 2026-11-20): a sticky 4x4 grid on top, a
// pool of unplaced tiles below, and a tile editor panel. Tiles are
// created first, given one function (kind) and its binding (app / text /
// URL), then dragged or tapped into the 16 slots. Vanilla JS.
//
// Site font: "コーポレート・ロゴ（ラウンド）Bold" (LOGOTYPE.JP,
// SIL Open Font License 1.1), converted to woff2 and served from the
// repo via CDN. Falls back to rounded system fonts while unavailable.

const FONT_URL = "https://cdn.jsdelivr.net/gh/45kikurage-rgb/aruno-clicker@main/home-layout-worker/assets/corporate-logo-rounded-bold.woff2";

const ICONS = ["honey", "qr", "pig", "airplane", "live", "tiker", "mail", "settings", "updates", "id", "copy", "link", "wifi", "storage", "tag", "auto"];
const ICON_GLYPHS = {
  honey: "🍯", qr: "▦", pig: "🐷", airplane: "✈", live: "●", tiker: "📱",
  mail: "✉", settings: "⚙", updates: "⟳", id: "ID", copy: "⧉", link: "🔗",
  wifi: "📶", storage: "💾", tag: "🏷", auto: "🌪",
};
const TILE_KINDS = [
  ["app", "アプリ起動"], ["copy", "コピー"], ["web", "リンク"],
  ["download", "ダウンロード"], ["update", "アプリ更新"], ["install", "アプリ入手"], ["settings", "設定"],
  ["menu", "せり上がり"], ["wifi", "Wi-Fi"], ["airplane", "機内モード"], ["label", "ラベル"],
];
const ITEM_KINDS = [
  ["update", "アプリ更新"], ["app", "アプリ起動"], ["storage", "スト埋め"],
  ["web", "リンク"], ["copy", "コピー"],
];
// Apps the user picks by name; the package name is filled in automatically.
// Anything not listed can still be entered as a raw package name.
const APP_CATALOG = [
  ["LINE", "jp.naver.line.android"],
  ["TikTok Lite", "com.ss.android.ugc.tiktok.lite"],
  ["Chrome", "com.android.chrome"],
  ["Google Play", "com.android.vending"],
  ["捨てメアド", "air.kukulive.mailnow"],
  ["Aru-Tiker", "jp.aruno.clicker"],
  ["Google開発者サービス", "com.google.android.gms"],
  ["Gmail", "com.google.android.gm"],
  ["YouTube", "com.google.android.youtube"],
  ["かんたんツール", "com.kantan.app"],
];

const PAGE_CSS = `
  @font-face { font-family: "CorpLogoRounded"; src: url("${FONT_URL}") format("woff2"); font-display: swap; }
  * { box-sizing: border-box; }
  body { margin: 0; font-family: "CorpLogoRounded", "Hiragino Maru Gothic ProN", "M PLUS Rounded 1c", sans-serif;
         background: #f3f6f5; color: #1c2b26; }
  header { position: sticky; top: 0; z-index: 6; background: #193b47; color: #fff;
           padding: 10px 12px; display: flex; align-items: center; gap: 10px; }
  header .title { font-size: 17px; flex: 1; }
  header .meta { font-size: 12px; opacity: .85; }
  button { font: inherit; cursor: pointer; }
  #saveBtn { background: #2e9e6b; color: #fff; border: 0; border-radius: 8px; padding: 10px 18px; }
  #saveBtn:disabled { opacity: .5; }
  main { padding: 10px 12px 40px; max-width: 560px; margin: 0 auto; }
  .secttl { font-size: 12px; color: #5a6d66; margin: 12px 2px 6px; letter-spacing: .04em; }
  .gridbox { position: sticky; top: 54px; z-index: 5; background: #f3f6f5; padding: 4px 0 8px; }
  .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
  .slot { border: 1.5px dashed #b9c6c1; border-radius: 12px; min-height: 66px;
          display: flex; align-items: center; justify-content: center; color: #7a8d86; font-size: 11px; }
  .slot.over { border-color: #2e9e6b; background: #e2f2ea; }
  .tile { border: 1.5px solid #c3d0cb; background: #fff; border-radius: 12px; min-height: 66px;
          display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
          padding: 5px 3px; cursor: grab; touch-action: none; user-select: none; -webkit-user-select: none;
          text-align: center; width: 100%; }
  .tile .ic { font-size: 22px; line-height: 1.1; }
  .tile .lb { font-size: 11px; line-height: 1.15; word-break: break-all; }
  .tile .kd { font-size: 9px; color: #7a8d86; }
  .tile.sel { border-color: #2e9e6b; box-shadow: 0 0 0 2px #2e9e6b; }
  .pool { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
  .newtile { border: 1.5px dashed #2e9e6b; color: #2e9e6b; background: transparent; border-radius: 12px;
             min-height: 66px; font-size: 13px; font-family: inherit; }
  .clone { position: fixed; z-index: 50; pointer-events: none; opacity: .92;
           box-shadow: 0 8px 24px rgba(0,0,0,.25); }
  .panel { margin-top: 12px; border: 1.5px solid #c3d0cb; border-radius: 14px; background: #fff; padding: 12px; }
  .panel h3 { margin: 0 0 8px; font-size: 15px; }
  .fnrow { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
  .fn { border: 1.5px solid #c3d0cb; background: #eef3f1; color: #1c2b26; border-radius: 999px;
        padding: 6px 10px; font-size: 12px; font-family: inherit; }
  .fn.on { border-color: #2e9e6b; color: #2e9e6b; background: #e2f2ea; }
  .fld { margin-bottom: 8px; }
  .fld label { display: block; font-size: 11px; color: #5a6d66; margin-bottom: 3px; }
  .fld input, .fld select { width: 100%; padding: 9px; border: 1.5px solid #c3d0cb; border-radius: 8px;
        background: #fff; color: #1c2b26; font-size: 14px; font-family: inherit; }
  .prow { display: flex; gap: 6px; margin-top: 10px; flex-wrap: wrap; }
  .pbtn { border: 1.5px solid #c3d0cb; background: #eef3f1; color: #1c2b26; border-radius: 10px;
          padding: 8px 12px; font-size: 13px; font-family: inherit; }
  .pbtn.danger { color: #c22; border-color: #c22; }
  .hint { font-size: 11px; color: #5a6d66; margin-top: 6px; line-height: 1.5; }
  .msg { font-size: 13px; color: #2e9e6b; min-height: 18px; margin-top: 8px; }
  .msg.err { color: #c22; }
  .rise { border: 1.5px solid #c3d0cb; border-radius: 14px 14px 0 0; background: #eef3f1; padding: 10px; margin-bottom: 10px; }
  .rise .cap { font-size: 11px; color: #5a6d66; margin-bottom: 6px; text-align: center; }
  .riseGrid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
  .ritem { border: 1.5px solid #c3d0cb; background: #fff; border-radius: 10px; padding: 8px 6px;
           font-size: 12px; text-align: center; color: #1c2b26; font-family: inherit; cursor: pointer; }
  .ritem.sel { border-color: #2e9e6b; box-shadow: 0 0 0 2px #2e9e6b; }
  .ritem small { display: block; font-size: 9px; color: #7a8d86; }
  .additem { border: 1.5px dashed #2e9e6b; color: #2e9e6b; background: transparent; border-radius: 10px;
             padding: 8px 6px; font-size: 12px; font-family: inherit; cursor: pointer; }
  .login { max-width: 400px; margin: 60px auto; padding: 0 16px; }
  .login .card { background: #fff; border: 1.5px solid #c3d0cb; border-radius: 14px; padding: 20px; }
  .login h3 { margin-top: 0; }
  .login .err { color: #c22; font-size: 13px; }
`;

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[c]));
}

export function loginHtml(message) {
  return `<!DOCTYPE html>
<html lang="ja"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ホーム画面の配置 - ログイン</title><style>${PAGE_CSS}</style></head>
<body>
<div class="login"><div class="card">
  <h3>ホーム画面の配置</h3>
  <p class="hint">編集は認証済み端末でのみ行えます。管理キーを入力してください。一度認証すると、この端末では以後ずっと認証済みとして扱われます。</p>
  ${message ? `<p class="err">${escapeHtml(message)}</p>` : ""}
  <form method="post" action="/home/login">
    <div class="fld"><label for="key">管理キー</label><input type="password" id="key" name="key" autocomplete="current-password" required></div>
    <button class="pbtn" type="submit" style="width:100%">ログイン</button>
  </form>
</div></div>
</body></html>`;
}

export function editorHtml() {
  return `<!DOCTYPE html>
<html lang="ja"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ホーム画面の配置</title><style>${PAGE_CSS}</style></head>
<body>
<header>
  <div class="title">ホーム画面の配置</div>
  <div class="meta" id="meta">読み込み中…</div>
  <button id="saveBtn" type="button">保存</button>
</header>
<main>
  <div class="gridbox">
    <div class="secttl">ホーム画面（16マス）― タイルをドラッグで移動、タップで編集</div>
    <div class="grid" id="grid"></div>
  </div>
  <div class="secttl">配置外のタイル（ここへドラッグで外せる）</div>
  <div class="pool" id="pool"></div>
  <div id="panelBox"></div>
  <div class="msg" id="msg"></div>
  <p class="hint">タイルを作り、機能と紐づけ（アプリ・文字列・URL）を設定して、16マスへ配置します。編集はその場でタイルに反映され、「保存」を押すと全端末へ配る配置データとして保存されます。</p>
  <form method="post" action="/home/logout"><button class="pbtn" type="submit">ログアウト</button></form>
</main>
<script>
var ICONS = ${JSON.stringify(ICONS)};
var ICON_GLYPHS = ${JSON.stringify(ICON_GLYPHS)};
var TILE_KINDS = ${JSON.stringify(TILE_KINDS)};
var ITEM_KINDS = ${JSON.stringify(ITEM_KINDS)};
var APP_CATALOG = ${JSON.stringify(APP_CATALOG)};
var KINDNAME = {}; TILE_KINDS.forEach(function (p) { KINDNAME[p[0]] = p[1]; });
var ITEMKINDNAME = {}; ITEM_KINDS.forEach(function (p) { ITEMKINDNAME[p[0]] = p[1]; });
var S = { tiles: {}, slots: [], pool: [], sel: null, selItem: -1, rev: 0, seq: 1 };
var grid = document.getElementById('grid'), poolEl = document.getElementById('pool'),
    panelBox = document.getElementById('panelBox'), msg = document.getElementById('msg'),
    meta = document.getElementById('meta'), saveBtn = document.getElementById('saveBtn');

function esc(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
function setMsg(text, isErr) { msg.textContent = text || ''; msg.className = 'msg' + (isErr ? ' err' : ''); }
function glyph(t) {
  if (t.icon && ICON_GLYPHS[t.icon]) return ICON_GLYPHS[t.icon];
  if (t.icon && t.icon.indexOf('pkg:') === 0) return '📱';
  if (t.kind === 'app') return '📱';
  if (t.kind === 'download') return '⬇';
  if (t.kind === 'update') return '⟳';
  if (t.kind === 'install') return '📥';
  return '▦';
}
function kindLabel(t) {
  if (t.kind === 'menu') return 'せり上がり ' + (t.items ? t.items.length : 0) + '件';
  return KINDNAME[t.kind] || '';
}
function tileEl(tid) {
  var t = S.tiles[tid]; if (!t) return null;
  var d = document.createElement('div');
  d.className = 'tile' + (S.sel === tid ? ' sel' : ''); d.dataset.tid = tid;
  d.innerHTML = '<div class="ic">' + glyph(t) + '</div><div class="lb">' + esc(t.title) + '</div><div class="kd">' + esc(kindLabel(t)) + '</div>';
  return d;
}
function locOf(tid) {
  var i = S.slots.indexOf(tid); if (i >= 0) return { where: 'slot', idx: i };
  var j = S.pool.indexOf(tid); if (j >= 0) return { where: 'pool', idx: j };
  return null;
}
function render() {
  grid.innerHTML = ''; poolEl.innerHTML = '';
  S.slots.forEach(function (tid, i) {
    if (tid && S.tiles[tid]) grid.appendChild(tileEl(tid));
    else { var s = document.createElement('div'); s.className = 'slot'; s.dataset.slot = i; s.textContent = 'あき'; grid.appendChild(s); }
  });
  S.pool.forEach(function (tid) { if (S.tiles[tid]) poolEl.appendChild(tileEl(tid)); });
  var nb = document.createElement('button'); nb.className = 'newtile'; nb.type = 'button'; nb.textContent = '＋ タイルを作る';
  nb.addEventListener('click', createTile); poolEl.appendChild(nb);
  meta.textContent = 'revision ' + S.rev;
  renderPanel();
}
function catalogFieldHtml(currentPkg) {
  var opts = '<option value="">アプリ名から選ぶ…</option>';
  APP_CATALOG.forEach(function (p) { opts += '<option value="' + esc(p[1]) + '"' + (p[1] === currentPkg ? ' selected' : '') + '>' + esc(p[0]) + '</option>'; });
  return '<div class="fld"><label>アプリ名から選ぶ（パッケージ名が自動で入ります）</label><select class="catalogSel">' + opts + '</select></div>' +
    '<div class="fld"><label>パッケージ名</label><input class="pkgInput" value="' + esc(currentPkg || '') + '" placeholder="jp.naver.line.android"></div>';
}
function iconFieldHtml(t) {
  var isPkg = t.icon && t.icon.indexOf('pkg:') === 0;
  var opts = ICONS.map(function (id) { return '<option value="' + id + '"' + (!isPkg && t.icon === id ? ' selected' : '') + '>' + (ICON_GLYPHS[id] || '') + ' ' + id + '</option>'; }).join('');
  opts += '<option value="__pkg"' + (isPkg ? ' selected' : '') + '>アプリのアイコンを使う</option>';
  var html = '<div class="fld"><label>アイコン</label><select id="f_icon">' + opts + '</select></div>';
  if (isPkg) html += '<div class="fld"><label>アイコンのアプリ（パッケージ名）</label><input id="f_iconpkg" value="' + esc(t.icon.slice(4)) + '"></div>';
  return html;
}
function renderPanel() {
  panelBox.innerHTML = '';
  if (!S.sel || !S.tiles[S.sel]) return;
  var t = S.tiles[S.sel], loc = locOf(S.sel);
  var p = document.createElement('div'); p.className = 'panel';
  var fnHtml = TILE_KINDS.map(function (pr) { return '<button class="fn' + (t.kind === pr[0] ? ' on' : '') + '" data-k="' + pr[0] + '">' + pr[1] + '</button>'; }).join('');
  var fields = '<div class="fld"><label>表示名</label><input id="f_title" value="' + esc(t.title || '') + '"></div>';
  if (t.kind === 'app' || t.kind === 'download' || t.kind === 'update' || t.kind === 'install') {
    fields += catalogFieldHtml(t.pkg);
    if (t.kind === 'install') fields += '<div class="fld"><label>APKのURL（直リンク・必須）</label><input id="f_url" value="' + esc(t.url || '') + '" placeholder="https://…/app.apk"></div><div class="hint">押すとアプリがAPKをダウンロードしてインストールし、起動します（アプリ側の対応が必要。未対応の間は空白表示）。入っていれば起動だけします。</div>' + iconFieldHtml(t);
    else if (t.kind !== 'app') fields += '<div class="fld"><label>' + (t.kind === 'download' ? '入手ページ' : '更新ページ') + 'のURL（空ならストア画面）</label><input id="f_url" value="' + esc(t.url || '') + '" placeholder="https://…"></div>';
  }
  if (t.kind === 'web') {
    fields += '<div class="fld"><label>URL</label><input id="f_url" value="' + esc(t.url || '') + '" placeholder="https://…"></div>';
    fields += '<div class="fld"><label>開くブラウザ</label><select id="f_browser"><option value="">指定なし</option><option value="com.android.chrome"' + (t.browser ? ' selected' : '') + '>Chrome</option></select></div>';
    fields += iconFieldHtml(t);
  }
  if (t.kind === 'copy') {
    fields += '<div class="fld"><label>コピーの種類</label><select id="f_ctype">' +
      '<option value="text"' + (t.copyType === 'text' ? ' selected' : '') + '>文字列</option>' +
      '<option value="url"' + (t.copyType === 'url' ? ' selected' : '') + '>URL</option>' +
      '<option value="deviceId"' + (t.copyType === 'deviceId' ? ' selected' : '') + '>端末ID</option></select></div>';
    if (t.copyType === 'text') fields += '<div class="fld"><label>コピーする文字列</label><input id="f_text" value="' + esc(t.text || '') + '"></div>';
    if (t.copyType === 'url') fields += '<div class="fld"><label>コピーするURL</label><input id="f_curl" value="' + esc(t.url || '') + '"></div>';
    if (t.copyType === 'deviceId') fields += '<div class="hint">端末ごとのID文字列をコピーします。内容の入力は不要です。</div>';
    fields += '<div class="hint">アイコンはコピー（紙2枚）に統一されます。何をコピーするかは表示名で表してください。</div>';
  }
  if (t.kind === 'settings' || t.kind === 'wifi' || t.kind === 'airplane' || t.kind === 'label') fields += iconFieldHtml(t);
  var menuHtml = '';
  if (t.kind === 'menu') {
    fields += iconFieldHtml(t);
    t.items = t.items || [];
    var itemsHtml = t.items.map(function (it, i) {
      return '<button class="ritem' + (S.selItem === i ? ' sel' : '') + '" data-i="' + i + '">' + esc(it.title) + '<small>' + esc(ITEMKINDNAME[it.kind] || '') + '</small></button>';
    }).join('');
    if (t.items.length < 8) itemsHtml += '<button class="additem" id="i_add">＋ 項目を足す</button>';
    menuHtml = '<div class="secttl">せり上がりの中身（最大8件・2列）</div>' +
      '<div class="rise"><div class="cap">▲ 端末では下からせり上がります</div><div class="riseGrid">' + itemsHtml + '</div></div>';
    if (S.selItem >= 0 && t.items[S.selItem]) {
      var it = t.items[S.selItem];
      var ifn = ITEM_KINDS.map(function (pr) { return '<button class="fn' + (it.kind === pr[0] ? ' on' : '') + '" data-ik="' + pr[0] + '">' + pr[1] + '</button>'; }).join('');
      menuHtml += '<div class="secttl">項目の編集</div><div class="fnrow">' + ifn + '</div>' +
        '<div class="fld"><label>表示名</label><input id="i_title" value="' + esc(it.title || '') + '"></div>';
      if (it.kind === 'app' || it.kind === 'update') {
        menuHtml += catalogFieldHtml(it.pkg).replace(/class="catalogSel"/g, 'class="catalogSel" id="i_catalog"').replace(/class="pkgInput"/g, 'class="pkgInput" id="i_pkg"');
        if (it.kind === 'update') menuHtml += '<div class="fld"><label>更新ページのURL（空ならストア画面）</label><input id="i_url" value="' + esc(it.url || '') + '" placeholder="https://…"></div>';
      }
      if (it.kind === 'web') menuHtml += '<div class="fld"><label>URL</label><input id="i_url" value="' + esc(it.url || '') + '"></div>';
      if (it.kind === 'copy') {
        menuHtml += '<div class="fld"><label>コピーの種類</label><select id="i_ctype">' +
          '<option value="text"' + (it.copyType === 'text' ? ' selected' : '') + '>文字列</option>' +
          '<option value="url"' + (it.copyType === 'url' ? ' selected' : '') + '>URL</option>' +
          '<option value="deviceId"' + (it.copyType === 'deviceId' ? ' selected' : '') + '>端末ID</option></select></div>';
        if (it.copyType === 'text') menuHtml += '<div class="fld"><label>コピーする文字列</label><input id="i_text" value="' + esc(it.text || '') + '"></div>';
        if (it.copyType === 'url') menuHtml += '<div class="fld"><label>コピーするURL</label><input id="i_curl" value="' + esc(it.url || '') + '"></div>';
      }
      if (it.kind === 'storage') menuHtml += '<div class="hint">端末のストレージ画面（スト埋め）を開きます。紐づけは不要です。</div>';
      menuHtml += '<div class="prow"><button class="pbtn" id="i_up">← 前へ</button><button class="pbtn" id="i_down">次へ →</button><button class="pbtn danger" id="i_del">項目を削除</button></div>';
    } else {
      menuHtml += '<div class="hint">項目をタップすると、その項目の機能と紐づけを編集できます。</div>';
    }
  }
  p.innerHTML = '<h3>タイルの編集</h3><div class="secttl" style="margin-top:0">機能を選ぶ</div><div class="fnrow">' + fnHtml + '</div>' + fields + menuHtml +
    '<div class="prow">' +
    (loc && loc.where === 'slot' ? '<button class="pbtn" id="p_unplace">配置を外す</button>' : '') +
    '<button class="pbtn danger" id="p_del">タイルを削除</button>' +
    '<button class="pbtn" id="p_close">閉じる</button></div>';
  panelBox.appendChild(p);
  p.querySelectorAll('.fn[data-k]').forEach(function (b) { b.addEventListener('click', function () {
    t.kind = b.dataset.k; S.selItem = -1;
    if (t.kind === 'menu' && !t.items) t.items = [{ kind: 'update', title: '新しい項目', pkg: '' }];
    if (t.kind === 'copy' && !t.copyType) t.copyType = 'text';
    render();
  }); });
  function bind(id, obj, key, rerender) { var el = document.getElementById(id); if (el) el.addEventListener('input', function () { obj[key] = el.value; if (rerender) render(); else if (key === 'title') { var lb = document.querySelector('.tile[data-tid="' + S.sel + '"] .lb'); if (lb) lb.textContent = el.value; } }); }
  bind('f_title', t, 'title'); bind('f_url', t, 'url'); bind('f_text', t, 'text'); bind('f_curl', t, 'url');
  var pkgIn = p.querySelector('.pkgInput'); if (pkgIn) pkgIn.addEventListener('input', function () { t.pkg = pkgIn.value; });
  var catSel = p.querySelector('.catalogSel'); if (catSel) catSel.addEventListener('change', function () { if (catSel.value) { t.pkg = catSel.value; render(); } });
  var br = document.getElementById('f_browser'); if (br) br.addEventListener('change', function () { t.browser = br.value || undefined; });
  var ct = document.getElementById('f_ctype'); if (ct) ct.addEventListener('change', function () { t.copyType = ct.value; render(); });
  var ic = document.getElementById('f_icon'); if (ic) ic.addEventListener('change', function () { t.icon = ic.value === '__pkg' ? 'pkg:' : ic.value; render(); });
  bind('f_iconpkg', t, 'iconpkg');
  var ip = document.getElementById('f_iconpkg'); if (ip) ip.addEventListener('input', function () { t.icon = 'pkg:' + ip.value; });
  p.querySelectorAll('.ritem').forEach(function (b) { b.addEventListener('click', function () { S.selItem = parseInt(b.dataset.i, 10); render(); }); });
  var add = document.getElementById('i_add'); if (add) add.addEventListener('click', function () { t.items.push({ kind: 'update', title: '新しい項目', pkg: '' }); S.selItem = t.items.length - 1; render(); });
  if (S.selItem >= 0 && t.items && t.items[S.selItem]) {
    var it2 = t.items[S.selItem];
    p.querySelectorAll('.fn[data-ik]').forEach(function (b) { b.addEventListener('click', function () { it2.kind = b.dataset.ik; if (it2.kind === 'copy' && !it2.copyType) it2.copyType = 'text'; render(); }); });
    bind('i_title', it2, 'title', true); bind('i_url', it2, 'url'); bind('i_text', it2, 'text'); bind('i_curl', it2, 'url');
    var ipkg = document.getElementById('i_pkg'); if (ipkg) ipkg.addEventListener('input', function () { it2.pkg = ipkg.value; });
    var icat = document.getElementById('i_catalog'); if (icat) icat.addEventListener('change', function () { if (icat.value) { it2.pkg = icat.value; render(); } });
    var ict = document.getElementById('i_ctype'); if (ict) ict.addEventListener('change', function () { it2.copyType = ict.value; render(); });
    var up = document.getElementById('i_up'), dn = document.getElementById('i_down'), dl = document.getElementById('i_del');
    if (up) up.addEventListener('click', function () { var i = S.selItem; if (i > 0) { var tmp = t.items[i - 1]; t.items[i - 1] = t.items[i]; t.items[i] = tmp; S.selItem = i - 1; render(); } });
    if (dn) dn.addEventListener('click', function () { var i = S.selItem; if (i < t.items.length - 1) { var tmp = t.items[i + 1]; t.items[i + 1] = t.items[i]; t.items[i] = tmp; S.selItem = i + 1; render(); } });
    if (dl) dl.addEventListener('click', function () { t.items.splice(S.selItem, 1); S.selItem = -1; render(); });
  }
  var un = document.getElementById('p_unplace'); if (un) un.addEventListener('click', function () { var L = locOf(S.sel); if (L && L.where === 'slot') { S.slots[L.idx] = null; S.pool.push(S.sel); } render(); });
  document.getElementById('p_del').addEventListener('click', function () { var L = locOf(S.sel); if (L) { if (L.where === 'slot') S.slots[L.idx] = null; else S.pool.splice(L.idx, 1); } delete S.tiles[S.sel]; S.sel = null; S.selItem = -1; render(); });
  document.getElementById('p_close').addEventListener('click', function () { S.sel = null; S.selItem = -1; render(); });
}
function createTile() {
  var id = 't' + Date.now().toString(36) + (S.seq++);
  S.tiles[id] = { id: id, kind: 'app', title: '新しいタイル', pkg: '' };
  S.pool.push(id); S.sel = id; S.selItem = -1; render();
  panelBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}
function cleanItem(it) {
  var o = { kind: it.kind, title: it.title };
  if (it.kind === 'app') o.pkg = it.pkg || '';
  if (it.kind === 'update') { o.pkg = it.pkg || ''; if (it.url) o.url = it.url; }
  if (it.kind === 'web') o.url = it.url || '';
  if (it.kind === 'copy') { o.copyType = it.copyType || 'text'; if (it.copyType === 'text') o.text = it.text || ''; if (it.copyType === 'url') o.url = it.url || ''; }
  return o;
}
function cleanTile(t) {
  var o = { id: t.id, kind: t.kind, title: t.title };
  if (t.kind === 'app') o.pkg = t.pkg || '';
  if (t.kind === 'download' || t.kind === 'update') { o.pkg = t.pkg || ''; if (t.url) o.url = t.url; }
  if (t.kind === 'install') { o.pkg = t.pkg || ''; o.url = t.url || ''; if (t.icon) o.icon = t.icon; }
  if (t.kind === 'web') { o.icon = t.icon || 'link'; o.url = t.url || ''; if (t.browser) o.browser = t.browser; }
  if (t.kind === 'copy') { o.copyType = t.copyType || 'text'; if (t.copyType === 'text') o.text = t.text || ''; if (t.copyType === 'url') o.url = t.url || ''; }
  if (t.kind === 'settings' || t.kind === 'wifi' || t.kind === 'airplane' || t.kind === 'label') o.icon = t.icon || (t.kind === 'settings' ? 'settings' : t.kind === 'wifi' ? 'wifi' : t.kind === 'airplane' ? 'airplane' : 'tag');
  if (t.kind === 'menu') { o.icon = t.icon || 'updates'; o.items = (t.items || []).map(cleanItem); }
  return o;
}
function serialize() {
  return { v: 2,
    slots: S.slots.map(function (tid) { return tid && S.tiles[tid] ? cleanTile(S.tiles[tid]) : { kind: 'blank' }; }),
    pool: S.pool.filter(function (tid) { return S.tiles[tid]; }).map(function (tid) { return cleanTile(S.tiles[tid]); }) };
}
saveBtn.addEventListener('click', function () {
  saveBtn.disabled = true; setMsg('保存中…');
  fetch('/v1/home-layout', { method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ expectedRevision: S.rev, layout: serialize() }) })
  .then(function (res) { return res.json().then(function (data) { return { status: res.status, data: data }; }); })
  .then(function (r) {
    saveBtn.disabled = false;
    if (r.status === 200) { S.rev = r.data.revision; meta.textContent = 'revision ' + S.rev; setMsg('保存しました（revision ' + S.rev + '）。各端末の「画面更新」で反映されます。'); }
    else if (r.status === 409) { setMsg('他の場所で更新されました。ページを読み直してください。', true); }
    else setMsg('保存できませんでした: ' + (r.data && r.data.error ? r.data.error.message : r.status), true);
  })
  .catch(function () { saveBtn.disabled = false; setMsg('通信に失敗しました', true); });
});
function removeFromLoc(tid) { var L = locOf(tid); if (!L) return; if (L.where === 'slot') S.slots[L.idx] = null; else S.pool.splice(L.idx, 1); }
function moveTile(tid, target) {
  var src = locOf(tid); if (!src) return;
  if (target.pool) { removeFromLoc(tid); S.pool.push(tid); return; }
  var dst = locOf(target.tid); if (!dst) return;
  if (dst.where === 'slot' && src.where === 'slot') { S.slots[dst.idx] = tid; S.slots[src.idx] = target.tid; }
  else if (dst.where === 'slot') { S.slots[dst.idx] = tid; S.pool.splice(src.idx, 1); S.pool.push(target.tid); }
  else if (src.where === 'slot') { S.pool[dst.idx] = tid; S.slots[src.idx] = target.tid; }
  else { S.pool[dst.idx] = tid; S.pool[src.idx] = target.tid; }
}
var drag = null;
document.addEventListener('pointerdown', function (e) {
  var el = e.target.closest ? e.target.closest('.tile') : null;
  if (el) { drag = { tid: el.dataset.tid, x0: e.clientX, y0: e.clientY, active: false, el: el, clone: null }; return; }
  var slotEl = e.target.closest ? e.target.closest('.slot') : null;
  if (slotEl && S.sel && S.tiles[S.sel]) { removeFromLoc(S.sel); S.slots[parseInt(slotEl.dataset.slot, 10)] = S.sel; render(); }
});
document.addEventListener('pointermove', function (e) {
  if (!drag) return;
  if (!drag.active && Math.hypot(e.clientX - drag.x0, e.clientY - drag.y0) > 10) {
    drag.active = true;
    var r = drag.el.getBoundingClientRect();
    var c = drag.el.cloneNode(true); c.className = 'tile clone';
    c.style.width = r.width + 'px'; c.style.left = r.left + 'px'; c.style.top = r.top + 'px';
    document.body.appendChild(c); drag.clone = c; drag.dx = e.clientX - r.left; drag.dy = e.clientY - r.top;
    drag.el.style.opacity = '.35';
  }
  if (drag.active && drag.clone) {
    drag.clone.style.left = (e.clientX - drag.dx) + 'px'; drag.clone.style.top = (e.clientY - drag.dy) + 'px';
    document.querySelectorAll('.slot.over').forEach(function (s) { s.classList.remove('over'); });
    var under = document.elementFromPoint(e.clientX, e.clientY);
    var slotEl = under && under.closest ? under.closest('.slot') : null;
    if (slotEl) slotEl.classList.add('over');
  }
});
document.addEventListener('pointerup', function (e) {
  if (!drag) return;
  var d = drag; drag = null;
  if (d.clone) d.clone.remove();
  if (d.el) d.el.style.opacity = '';
  document.querySelectorAll('.slot.over').forEach(function (s) { s.classList.remove('over'); });
  if (!locOf(d.tid)) { render(); return; }
  if (!d.active) {
    if (S.sel && S.sel !== d.tid) moveTile(S.sel, { tid: d.tid });
    else { S.sel = d.tid; S.selItem = -1; }
    render(); return;
  }
  var under = document.elementFromPoint(e.clientX, e.clientY);
  var slotEl = under && under.closest ? under.closest('.slot') : null;
  var tileTarget = under && under.closest ? under.closest('.tile') : null;
  var poolHit = under && under.closest ? under.closest('#pool') : null;
  if (slotEl) { removeFromLoc(d.tid); S.slots[parseInt(slotEl.dataset.slot, 10)] = d.tid; }
  else if (tileTarget && tileTarget.dataset.tid !== d.tid) moveTile(d.tid, { tid: tileTarget.dataset.tid });
  else if (poolHit) moveTile(d.tid, { pool: true });
  render();
});
fetch('/v1/home-layout').then(function (res) { return res.json(); }).then(function (data) {
  S.rev = data.revision || 0;
  S.tiles = {}; S.slots = []; S.pool = [];
  (data.slots || []).forEach(function (t) {
    if (t.kind === 'blank') { S.slots.push(null); return; }
    S.tiles[t.id] = t; S.slots.push(t.id);
  });
  while (S.slots.length < 16) S.slots.push(null);
  (data.pool || []).forEach(function (t) { S.tiles[t.id] = t; S.pool.push(t.id); });
  render();
}).catch(function () { setMsg('読み込みに失敗しました', true); });
</script>
</body></html>`;
}
