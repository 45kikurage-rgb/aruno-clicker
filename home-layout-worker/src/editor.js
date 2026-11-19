// Editor and login pages for the Aruno-Home/T HOME layout site.
// Smartphone-first: a 4x4 grid preview on top, one slot editor below,
// and a sticky save bar. Vanilla JS, no external assets.

const ICONS = ["honey", "qr", "pig", "airplane", "live", "tiker", "mail", "settings", "updates", "id", "copy", "link", "wifi", "storage"];
const ICON_GLYPHS = {
  honey: "🍯", qr: "▦", pig: "🐷", airplane: "✈", live: "●", tiker: "📱",
  mail: "✉", settings: "⚙", updates: "⟳", id: "ID", copy: "⧉", link: "🔗",
  wifi: "📶", storage: "💾",
};
const SLOT_KINDS = [
  ["app", "アプリ起動"], ["web", "Webショートカット"], ["copy", "コピー"],
  ["settings", "設定"], ["menu", "せり上がりメニュー"], ["wifi", "Wi-Fi"],
  ["airplane", "機内モード"], ["blank", "空白"],
];
const ITEM_KINDS = [
  ["updateApp", "アプリ更新"], ["storage", "ストレージ埋め"], ["web", "URLを開く"],
  ["app", "アプリ起動"], ["copy", "コピー"],
];

const PAGE_CSS = `
  * { box-sizing: border-box; }
  body { margin: 0; font-family: sans-serif; background: #f3f6f5; color: #1c2b26; }
  header { position: sticky; top: 0; z-index: 5; background: #193b47; color: #fff;
           padding: 10px 12px; display: flex; align-items: center; gap: 10px; }
  header .title { font-size: 17px; font-weight: 700; flex: 1; }
  header .meta { font-size: 12px; opacity: .85; }
  button { font: inherit; cursor: pointer; }
  #saveBtn { background: #2e9e6b; color: #fff; border: 0; border-radius: 8px;
             padding: 10px 18px; font-weight: 700; }
  #saveBtn:disabled { background: #8fb8a5; }
  main { padding: 12px; max-width: 560px; margin: 0 auto; }
  .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
  .cell { background: #fff; border: 2px solid #c4d2cc; border-radius: 10px;
          min-height: 74px; padding: 6px 4px; text-align: center; cursor: pointer; }
  .cell.sel { border-color: #2e9e6b; background: #e7f4ec; }
  .cell .glyph { font-size: 22px; line-height: 1.2; }
  .cell .name { font-size: 12px; margin-top: 2px; word-break: break-all; }
  .cell .kind { font-size: 10px; color: #5a6a64; }
  .panel { background: #fff; border: 1px solid #c4d2cc; border-radius: 10px;
           padding: 12px; margin-top: 14px; }
  .panel h2 { font-size: 15px; margin: 0 0 10px; }
  label { display: block; font-size: 13px; margin: 10px 0 4px; color: #35534a; }
  input[type=text], select { width: 100%; padding: 10px; font-size: 16px;
    border: 1px solid #b9c6c1; border-radius: 8px; background: #fff; }
  .row { display: flex; gap: 8px; }
  .row > * { flex: 1; }
  .mini { background: #eef3f1; border: 1px solid #b9c6c1; border-radius: 8px; padding: 8px; }
  .itemCard { border: 1px solid #c4d2cc; border-radius: 8px; padding: 10px; margin-top: 10px; }
  .itemBtns { display: flex; gap: 6px; margin-top: 8px; }
  .itemBtns button, .addBtn { background: #eef3f1; border: 1px solid #b9c6c1;
    border-radius: 6px; padding: 7px 10px; font-size: 13px; }
  .danger { color: #b03030; }
  .menuPreview { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 8px; }
  .menuPreview .mcell { background: #eef3f1; border-radius: 8px; padding: 10px 6px;
    text-align: center; font-size: 13px; }
  .note { font-size: 12px; color: #5a6a64; line-height: 1.6; }
  #status { margin: 10px 2px; font-size: 14px; min-height: 20px; }
  .err { color: #b03030; } .ok { color: #2e7d5f; }
  form.login { max-width: 420px; margin: 40px auto; padding: 0 16px; }
  form.login .panel { margin-top: 0; }
  form.login button { width: 100%; margin-top: 14px; background: #193b47; color: #fff;
    border: 0; border-radius: 8px; padding: 12px; font-weight: 700; }
`;

export function loginHtml(message) {
  return `<!DOCTYPE html>
<html lang="ja"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ホーム画面変更 - 認証</title><style>${PAGE_CSS}</style></head>
<body>
<form class="login" method="post" action="/home/login">
  <div class="panel">
    <h2>ホーム画面変更サイト</h2>
    <p class="note">編集は認証済み端末でのみ行えます。管理キーを入力してください。
    一度認証すると、この端末では以後ずっと認証済みとして扱われます。</p>
    ${message ? `<p class="err">${escapeHtml(message)}</p>` : ""}
    <label for="key">管理キー</label>
    <input type="text" id="key" name="key" autocomplete="off" required>
    <button type="submit">認証する</button>
  </div>
</form>
</body></html>`;
}

export function editorHtml() {
  return `<!DOCTYPE html>
<html lang="ja"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ホーム画面変更</title><style>${PAGE_CSS}</style></head>
<body>
<header>
  <div>
    <div class="title">ホーム画面変更</div>
    <div class="meta" id="meta">読み込み中…</div>
  </div>
  <button id="saveBtn" type="button">保存</button>
</header>
<main>
  <div id="status"></div>
  <div class="grid" id="grid"></div>
  <div class="panel" id="editor"></div>
  <p class="note">保存すると revision が上がります。各端末では Aruno-Home/T の
  「画面更新」を押すと、この配置に変わります。コピー部品のアイコンは紙2枚に
  統一され、何をコピーするかは表示名で表します。</p>
  <form method="post" action="/home/logout">
    <button type="submit" class="mini">この端末の認証を解除（ログアウト）</button>
  </form>
</main>
<script>
"use strict";
var ICONS = ${JSON.stringify(ICONS)};
var GLYPHS = ${JSON.stringify(ICON_GLYPHS)};
var SLOT_KINDS = ${JSON.stringify(SLOT_KINDS)};
var ITEM_KINDS = ${JSON.stringify(ITEM_KINDS)};
var state = { revision: 0, updatedAt: "", slots: [], selected: 0 };
var $grid = document.getElementById("grid");
var $editor = document.getElementById("editor");
var $meta = document.getElementById("meta");
var $status = document.getElementById("status");
var $save = document.getElementById("saveBtn");

function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
    return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
  });
}
function glyph(slot) {
  if (!slot || slot.kind === "blank") return "·";
  if (slot.kind === "app") return "📱";
  if (slot.kind === "copy") return GLYPHS.copy;
  var icon = slot.icon || "";
  if (icon.indexOf("pkg:") === 0) return "📱";
  return GLYPHS[icon] || "▢";
}
function kindLabel(list, kind) {
  for (var i = 0; i < list.length; i++) if (list[i][0] === kind) return list[i][1];
  return kind;
}
function setStatus(msg, ok) {
  $status.innerHTML = msg ? '<span class="' + (ok ? "ok" : "err") + '">' + esc(msg) + "</span>" : "";
}
function renderMeta() {
  $meta.textContent = "rev " + state.revision + (state.updatedAt ? " / " + state.updatedAt.replace("T", " ").slice(0, 16) : "");
}
function renderGrid() {
  $grid.innerHTML = "";
  state.slots.forEach(function (slot, i) {
    var d = document.createElement("div");
    d.className = "cell" + (i === state.selected ? " sel" : "");
    d.innerHTML = '<div class="glyph">' + glyph(slot) + '</div>' +
      '<div class="name">' + esc(slot.kind === "blank" ? "（空白）" : slot.title || "") + "</div>" +
      '<div class="kind">' + esc(kindLabel(SLOT_KINDS, slot.kind)) + "</div>";
    d.addEventListener("click", function () { state.selected = i; renderGrid(); renderEditor(); });
    $grid.appendChild(d);
  });
}
function options(list, value) {
  return list.map(function (pair) {
    return '<option value="' + pair[0] + '"' + (pair[0] === value ? " selected" : "") + ">" + pair[1] + "</option>";
  }).join("");
}
function iconSelect(id, value, allowEmpty) {
  var html = '<select id="' + id + '">';
  if (allowEmpty) html += '<option value="">（なし）</option>';
  ICONS.forEach(function (icon) {
    html += '<option value="' + icon + '"' + (icon === value ? " selected" : "") + ">" + icon + "</option>";
  });
  html += '<option value="__pkg__"' + (value && value.indexOf("pkg:") === 0 ? " selected" : "") + ">アプリのアイコン（パッケージ指定）</option>";
  html += "</select>";
  var pkg = value && value.indexOf("pkg:") === 0 ? value.slice(4) : "";
  html += '<input type="text" id="' + id + 'Pkg" placeholder="パッケージ名（アイコン用）" value="' + esc(pkg) + '"' +
    (value && value.indexOf("pkg:") === 0 ? "" : ' style="display:none"') + ">";
  return html;
}
function readIcon(id) {
  var sel = document.getElementById(id);
  if (!sel) return undefined;
  if (sel.value === "__pkg__") {
    var pkg = document.getElementById(id + "Pkg").value.trim();
    return pkg ? "pkg:" + pkg : "";
  }
  return sel.value;
}
function wireIcon(id) {
  var sel = document.getElementById(id);
  if (!sel) return;
  sel.addEventListener("change", function () {
    var input = document.getElementById(id + "Pkg");
    if (input) input.style.display = sel.value === "__pkg__" ? "" : "none";
  });
}
function copyFields(prefix, obj) {
  var html = '<label>コピー内容の種類</label><select id="' + prefix + 'CopyType">' +
    [["text", "固定文字列"], ["url", "URL"], ["deviceId", "端末ID"]].map(function (p) {
      return '<option value="' + p[0] + '"' + (p[0] === (obj.copyType || "text") ? " selected" : "") + ">" + p[1] + "</option>";
    }).join("") + "</select>";
  html += '<div id="' + prefix + 'TextWrap"><label>コピーする文字列</label>' +
    '<input type="text" id="' + prefix + 'Text" value="' + esc(obj.text || "") + '"></div>';
  html += '<div id="' + prefix + 'UrlWrap"><label>コピーするURL</label>' +
    '<input type="text" id="' + prefix + 'Url" value="' + esc(obj.url || "") + '"></div>';
  return html;
}
function wireCopy(prefix) {
  var sel = document.getElementById(prefix + "CopyType");
  if (!sel) return;
  var update = function () {
    document.getElementById(prefix + "TextWrap").style.display = sel.value === "text" ? "" : "none";
    document.getElementById(prefix + "UrlWrap").style.display = sel.value === "url" ? "" : "none";
  };
  sel.addEventListener("change", update);
  update();
}
function readCopy(prefix, out) {
  out.copyType = document.getElementById(prefix + "CopyType").value;
  delete out.text; delete out.url;
  if (out.copyType === "text") out.text = document.getElementById(prefix + "Text").value;
  if (out.copyType === "url") out.url = document.getElementById(prefix + "Url").value;
}
function renderEditor() {
  var i = state.selected;
  var slot = state.slots[i];
  var html = "<h2>マス " + (i + 1) + " の編集</h2>";
  html += '<label>部品の種類</label><select id="fKind">' + options(SLOT_KINDS, slot.kind) + "</select>";
  if (slot.kind !== "blank") {
    html += '<label>表示名</label><input type="text" id="fTitle" value="' + esc(slot.title || "") + '">';
  }
  if (slot.kind === "app") {
    html += '<label>パッケージ名</label><input type="text" id="fPkg" value="' + esc(slot.pkg || "") + '">';
    html += '<p class="note">アイコンはそのアプリのものが自動で表示されます。</p>';
  } else if (slot.kind === "web") {
    html += '<label>アイコン</label>' + iconSelect("fIcon", slot.icon || "link", false);
    html += '<label>開くURL</label><input type="text" id="fUrl" value="' + esc(slot.url || "") + '">';
    html += '<label>開くブラウザ（任意。Chrome固定など）</label><input type="text" id="fBrowser" placeholder="例：com.android.chrome" value="' + esc(slot.browser || "") + '">';
  } else if (slot.kind === "copy") {
    html += '<p class="note">アイコンは紙2枚（コピー）に統一されます。表示名に何をコピーするか書いてください。</p>';
    html += copyFields("f", slot);
  } else if (slot.kind === "menu") {
    html += '<label>アイコン</label>' + iconSelect("fIcon", slot.icon || "updates", false);
    html += '<div id="menuItems"></div>';
    html += '<div class="menuPreview" id="menuPreview"></div>';
    html += '<button type="button" class="addBtn" id="addItem">＋ 項目を追加（最大8）</button>';
  } else if (slot.kind !== "blank") {
    html += '<label>アイコン</label>' + iconSelect("fIcon", slot.icon || "", false);
    if (slot.kind === "settings") html += '<p class="note">設定部品は削除できません（種類を変えると、そのマスは設定でなくなります。設定部品が1つもない配置は保存できません）。</p>';
  }
  $editor.innerHTML = html;
  document.getElementById("fKind").addEventListener("change", function (e) {
    collectSlot();
    var kind = e.target.value;
    var next = { kind: kind };
    if (kind === "copy") { next.title = "コピー"; next.copyType = "text"; next.text = ""; }
    if (kind === "menu") { next.title = "各更新"; next.icon = "updates"; next.items = [{ kind: "storage", title: "70%スト埋め", icon: "storage" }]; }
    if (kind === "settings") { next.title = "設定"; next.icon = "settings"; }
    if (kind === "wifi") { next.title = "Wi-Fi"; next.icon = "wifi"; }
    if (kind === "airplane") { next.title = "機内モード"; next.icon = "airplane"; }
    state.slots[state.selected] = next;
    renderGrid(); renderEditor();
  });
  wireIcon("fIcon");
  wireCopy("f");
  if (slot.kind === "menu") renderMenuItems();
}
function renderMenuItems() {
  var slot = state.slots[state.selected];
  var wrap = document.getElementById("menuItems");
  var html = "";
  slot.items.forEach(function (item, idx) {
    html += '<div class="itemCard"><b>項目 ' + (idx + 1) + "</b>" +
      '<label>動作の種類</label><select id="iKind' + idx + '">' + options(ITEM_KINDS, item.kind) + "</select>" +
      '<label>表示名</label><input type="text" id="iTitle' + idx + '" value="' + esc(item.title || "") + '">';
    if (item.kind === "updateApp" || item.kind === "app") {
      html += '<label>パッケージ名</label><input type="text" id="iPkg' + idx + '" value="' + esc(item.pkg || "") + '">';
    } else if (item.kind === "web") {
      html += '<label>開くURL</label><input type="text" id="iUrl' + idx + '" value="' + esc(item.url || "") + '">';
    } else if (item.kind === "copy") {
      html += copyFields("i" + idx, item);
    }
    if (item.kind !== "copy") {
      html += '<label>アイコン（任意）</label>' + iconSelect("iIcon" + idx, item.icon || "", true);
    }
    html += '<div class="itemBtns">' +
      '<button type="button" data-act="up" data-idx="' + idx + '">↑ 上へ</button>' +
      '<button type="button" data-act="down" data-idx="' + idx + '">↓ 下へ</button>' +
      '<button type="button" class="danger" data-act="del" data-idx="' + idx + '">削除</button></div></div>';
  });
  wrap.innerHTML = html;
  slot.items.forEach(function (item, idx) {
    document.getElementById("iKind" + idx).addEventListener("change", function (e) {
      collectSlot();
      var s = state.slots[state.selected];
      var next = { kind: e.target.value, title: s.items[idx].title || "" };
      if (next.kind === "storage") { next.icon = "storage"; }
      if (next.kind === "copy") { next.copyType = "text"; next.text = ""; }
      s.items[idx] = next;
      renderMenuItems(); renderMenuPreview();
    });
    wireIcon("iIcon" + idx);
    wireCopy("i" + idx);
  });
  wrap.querySelectorAll("button[data-act]").forEach(function (b) {
    b.addEventListener("click", function () {
      collectSlot();
      var s = state.slots[state.selected];
      var idx = Number(b.dataset.idx);
      if (b.dataset.act === "del") s.items.splice(idx, 1);
      if (b.dataset.act === "up" && idx > 0) { var t = s.items[idx - 1]; s.items[idx - 1] = s.items[idx]; s.items[idx] = t; }
      if (b.dataset.act === "down" && idx < s.items.length - 1) { var u = s.items[idx + 1]; s.items[idx + 1] = s.items[idx]; s.items[idx] = u; }
      renderMenuItems(); renderMenuPreview();
    });
  });
  var add = document.getElementById("addItem");
  add.disabled = slot.items.length >= 8;
  add.addEventListener("click", function () {
    collectSlot();
    var s = state.slots[state.selected];
    if (s.items.length >= 8) return;
    s.items.push({ kind: "updateApp", title: "", pkg: "" });
    renderMenuItems(); renderMenuPreview();
  });
  renderMenuPreview();
}
function renderMenuPreview() {
  var slot = state.slots[state.selected];
  var pv = document.getElementById("menuPreview");
  if (!pv) return;
  pv.innerHTML = slot.items.map(function (item) {
    return '<div class="mcell">' + esc(item.title || "（無題）") + "</div>";
  }).join("");
}
function collectSlot() {
  var kindSel = document.getElementById("fKind");
  if (!kindSel) return;
  var slot = state.slots[state.selected];
  var kind = kindSel.value;
  var next = { kind: kind };
  if (kind === "blank") { state.slots[state.selected] = next; return; }
  next.title = document.getElementById("fTitle").value;
  if (kind === "app") {
    next.pkg = document.getElementById("fPkg").value.trim();
  } else if (kind === "web") {
    next.icon = readIcon("fIcon");
    next.url = document.getElementById("fUrl").value.trim();
    var browser = document.getElementById("fBrowser").value.trim();
    if (browser) next.browser = browser;
  } else if (kind === "copy") {
    readCopy("f", next);
  } else if (kind === "menu") {
    next.icon = readIcon("fIcon");
    next.items = slot.items.map(function (item, idx) {
      var out = { kind: document.getElementById("iKind" + idx).value, title: document.getElementById("iTitle" + idx).value };
      if (out.kind === "updateApp" || out.kind === "app") out.pkg = document.getElementById("iPkg" + idx).value.trim();
      else if (out.kind === "web") out.url = document.getElementById("iUrl" + idx).value.trim();
      else if (out.kind === "copy") readCopy("i" + idx, out);
      if (out.kind !== "copy") {
        var icon = readIcon("iIcon" + idx);
        if (icon) out.icon = icon;
      }
      return out;
    });
  } else {
    next.icon = readIcon("fIcon");
  }
  state.slots[state.selected] = next;
}
$save.addEventListener("click", function () {
  collectSlot();
  $save.disabled = true;
  setStatus("保存中…", true);
  fetch("/v1/home-layout", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: state.revision, layout: { v: 1, slots: state.slots } }),
  }).then(function (res) {
    return res.json().then(function (data) { return { status: res.status, data: data }; });
  }).then(function (r) {
    $save.disabled = false;
    if (r.status === 200) {
      state.revision = r.data.revision;
      state.updatedAt = r.data.updatedAt;
      renderMeta();
      setStatus("保存しました（rev " + r.data.revision + "）。各端末で「画面更新」を押すと反映されます。", true);
    } else if (r.status === 409) {
      setStatus("他の場所で更新されました。ページを再読み込みしてからやり直してください。", false);
    } else if (r.status === 401) {
      setStatus("認証が切れています。再読み込みしてください。", false);
    } else {
      setStatus("保存できませんでした：" + (r.data.error ? r.data.error.message : r.status), false);
    }
  }).catch(function () {
    $save.disabled = false;
    setStatus("通信に失敗しました", false);
  });
});
fetch("/v1/home-layout").then(function (res) { return res.json(); }).then(function (data) {
  if (data && data.slots) {
    state.revision = data.revision;
    state.updatedAt = data.updatedAt;
    state.slots = data.slots;
  } else {
    state.slots = [];
    for (var i = 0; i < 16; i++) state.slots.push({ kind: "blank" });
    state.slots[10] = { kind: "settings", title: "設定", icon: "settings" };
  }
  renderMeta(); renderGrid(); renderEditor();
}).catch(function () { setStatus("読み込みに失敗しました", false); });
</script>
</body></html>`;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[c]));
}
