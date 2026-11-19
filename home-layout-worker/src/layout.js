// Aruno-Home/T HOME layout (format v2): validation and normalization.
// Shared by the Worker handlers; kept free of Worker/D1 APIs for testing.
//
// Format v2 (tile model, decided 2026-11-20):
//   { v: 2, revision, updatedAt, slots: [tileOrBlank x16], pool: [tile] }
// Every non-blank tile carries a unique string id. The pool holds tiles
// that are not placed in the 16 slots. Format v1 data (no ids, no pool,
// menu item kind "updateApp") is upgraded by upgradeLayout().

export const LAYOUT_VERSION = 2;
export const SLOT_COUNT = 16;
export const MENU_MAX_ITEMS = 8;
export const MAX_POOL_TILES = 32;
export const MAX_TITLE_LENGTH = 40;
export const MAX_TEXT_LENGTH = 500;
export const MAX_URL_LENGTH = 2048;

// Built-in icon ids shipped in the Aruno-Home/T app (ic_home_<id> drawables).
// An icon may also be "pkg:<package>" to borrow an installed app's icon.
export const BUILTIN_ICONS = [
  "honey", "qr", "pig", "airplane", "live", "tiker", "mail",
  "settings", "updates", "id", "copy", "link", "wifi", "storage", "tag",
];

export const SLOT_KINDS = [
  "app", "web", "copy", "download", "update",
  "settings", "menu", "wifi", "airplane", "label", "blank",
];
export const MENU_ITEM_KINDS = ["update", "storage", "web", "app", "copy"];
export const COPY_TYPES = ["text", "url", "deviceId"];

const PACKAGE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/;
const ID_PATTERN = /^[A-Za-z0-9_-]{1,40}$/;
const CONTROL_PATTERN = /[\u0000-\u001f\u007f]/;

function fail(message) { return { ok: false, error: message }; }
function pass(value) { return { ok: true, value }; }

function isPlainObject(value) {
  return Boolean(value && typeof value === "object" && !Array.isArray(value));
}

function checkTitle(value, field) {
  if (typeof value !== "string") return fail(`${field} must be a string`);
  const title = value.trim();
  if (title.length === 0) return fail(`${field} must not be empty`);
  if (title.length > MAX_TITLE_LENGTH) return fail(`${field} is too long (max ${MAX_TITLE_LENGTH})`);
  if (CONTROL_PATTERN.test(title)) return fail(`${field} must not contain control characters`);
  return pass(title);
}

function checkPackage(value, field) {
  if (typeof value !== "string" || !PACKAGE_PATTERN.test(value)) {
    return fail(`${field} must be a valid package name`);
  }
  return pass(value);
}

function checkUrl(value, field) {
  if (typeof value !== "string" || value.length === 0 || value.length > MAX_URL_LENGTH) {
    return fail(`${field} must be a URL of at most ${MAX_URL_LENGTH} characters`);
  }
  let parsed;
  try { parsed = new URL(value); } catch { return fail(`${field} must be a valid URL`); }
  if (parsed.protocol !== "https:") return fail(`${field} must be an https URL`);
  if (CONTROL_PATTERN.test(value)) return fail(`${field} must not contain control characters`);
  return pass(value);
}

function checkOptionalUrl(value, field) {
  if (value === undefined || value === null || value === "") return pass(undefined);
  return checkUrl(value, field);
}

function checkIcon(value, field, { required }) {
  if (value === undefined || value === null || value === "") {
    return required ? fail(`${field} is required`) : pass(undefined);
  }
  if (typeof value !== "string") return fail(`${field} must be a string`);
  if (BUILTIN_ICONS.includes(value)) return pass(value);
  if (value.startsWith("pkg:")) {
    const pkg = checkPackage(value.slice(4), field);
    return pkg.ok ? pass(value) : pkg;
  }
  return fail(`${field} must be a built-in icon id or pkg:<package>`);
}

function checkId(value, field) {
  if (typeof value !== "string" || !ID_PATTERN.test(value)) {
    return fail(`${field} must be a tile id (1-40 chars: A-Z a-z 0-9 _ -)`);
  }
  return pass(value);
}

// Fields shared by a copy tile and a copy menu item.
function checkCopyFields(source, fieldPrefix) {
  if (!COPY_TYPES.includes(source.copyType)) {
    return fail(`${fieldPrefix}.copyType must be one of ${COPY_TYPES.join(", ")}`);
  }
  const out = { copyType: source.copyType };
  if (source.copyType === "text") {
    if (typeof source.text !== "string" || source.text.length === 0) {
      return fail(`${fieldPrefix}.text is required for copyType text`);
    }
    if (source.text.length > MAX_TEXT_LENGTH) return fail(`${fieldPrefix}.text is too long`);
    if (CONTROL_PATTERN.test(source.text)) return fail(`${fieldPrefix}.text must not contain control characters`);
    out.text = source.text;
  } else if (source.copyType === "url") {
    const url = checkUrl(source.url, `${fieldPrefix}.url`);
    if (!url.ok) return url;
    out.url = url.value;
  }
  return pass(out);
}

function checkWebFields(source, fieldPrefix) {
  const url = checkUrl(source.url, `${fieldPrefix}.url`);
  if (!url.ok) return url;
  const out = { url: url.value };
  if (source.browser !== undefined && source.browser !== null && source.browser !== "") {
    const browser = checkPackage(source.browser, `${fieldPrefix}.browser`);
    if (!browser.ok) return browser;
    out.browser = browser.value;
  }
  return pass(out);
}

// Fields for app / download / update: a package, plus an optional page URL
// for download and update (empty means "the store page for this app").
function checkAppFields(source, field, { withOptionalUrl }) {
  const pkg = checkPackage(source.pkg, `${field}.pkg`);
  if (!pkg.ok) return pkg;
  const out = { pkg: pkg.value };
  if (withOptionalUrl) {
    const url = checkOptionalUrl(source.url, `${field}.url`);
    if (!url.ok) return url;
    if (url.value !== undefined) out.url = url.value;
  }
  return pass(out);
}

function normalizeTile(raw, field, { inPool }) {
  if (!isPlainObject(raw)) return fail(`${field} must be an object`);
  if (!SLOT_KINDS.includes(raw.kind)) return fail(`${field}.kind is not supported`);
  if (raw.kind === "blank") {
    if (inPool) return fail(`${field} must not be blank in the pool`);
    return pass({ kind: "blank" });
  }

  const id = checkId(raw.id, `${field}.id`);
  if (!id.ok) return id;
  const title = checkTitle(raw.title, `${field}.title`);
  if (!title.ok) return title;
  const out = { id: id.value, kind: raw.kind, title: title.value };

  if (raw.kind === "app" || raw.kind === "download" || raw.kind === "update") {
    // The tile icon is the target app's own icon; nothing is stored.
    const app = checkAppFields(raw, field, { withOptionalUrl: raw.kind !== "app" });
    if (!app.ok) return app;
    Object.assign(out, app.value);
    return pass(out);
  }
  if (raw.kind === "copy") {
    // Copy tiles always use the copy (two papers) icon; the title states
    // what is copied (decided 2026-11-19).
    out.icon = "copy";
    const copy = checkCopyFields(raw, field);
    if (!copy.ok) return copy;
    Object.assign(out, copy.value);
    return pass(out);
  }

  const icon = checkIcon(raw.icon, `${field}.icon`, { required: true });
  if (!icon.ok) return icon;
  out.icon = icon.value;

  if (raw.kind === "web") {
    const web = checkWebFields(raw, field);
    if (!web.ok) return web;
    Object.assign(out, web.value);
  } else if (raw.kind === "menu") {
    if (!Array.isArray(raw.items) || raw.items.length === 0) {
      return fail(`${field}.items must contain at least one item`);
    }
    if (raw.items.length > MENU_MAX_ITEMS) {
      return fail(`${field}.items must contain at most ${MENU_MAX_ITEMS} items`);
    }
    const items = [];
    for (let i = 0; i < raw.items.length; i++) {
      const item = normalizeMenuItem(raw.items[i], `${field}.items[${i}]`);
      if (!item.ok) return item;
      items.push(item.value);
    }
    out.items = items;
  }
  // settings / wifi / airplane / label need no extra fields.
  return pass(out);
}

function normalizeMenuItem(raw, field) {
  if (!isPlainObject(raw)) return fail(`${field} must be an object`);
  // v1 used "updateApp" for the update action; accept and normalize it.
  const kind = raw.kind === "updateApp" ? "update" : raw.kind;
  if (!MENU_ITEM_KINDS.includes(kind)) return fail(`${field}.kind is not supported`);
  const title = checkTitle(raw.title, `${field}.title`);
  if (!title.ok) return title;
  const out = { kind, title: title.value };

  const icon = checkIcon(raw.icon, `${field}.icon`, { required: false });
  if (!icon.ok) return icon;
  if (icon.value !== undefined) out.icon = icon.value;

  if (kind === "app") {
    const app = checkAppFields(raw, field, { withOptionalUrl: false });
    if (!app.ok) return app;
    Object.assign(out, app.value);
  } else if (kind === "update") {
    const app = checkAppFields(raw, field, { withOptionalUrl: true });
    if (!app.ok) return app;
    Object.assign(out, app.value);
  } else if (kind === "web") {
    const web = checkWebFields(raw, field);
    if (!web.ok) return web;
    Object.assign(out, web.value);
  } else if (kind === "copy") {
    if (out.icon === undefined) out.icon = "copy";
    const copy = checkCopyFields(raw, field);
    if (!copy.ok) return copy;
    Object.assign(out, copy.value);
  }
  // storage needs no extra fields.
  return pass(out);
}

// Validates a client-supplied v2 layout body ({ slots, pool }) and returns
// the normalized layout object ({ v, slots, pool }) or an error message.
export function validateLayout(body) {
  if (!isPlainObject(body)) return fail("layout must be an object");
  if (body.v !== undefined && body.v !== LAYOUT_VERSION) {
    return fail(`unsupported layout version: ${body.v}`);
  }
  if (!Array.isArray(body.slots) || body.slots.length !== SLOT_COUNT) {
    return fail(`slots must contain exactly ${SLOT_COUNT} entries`);
  }
  const poolBody = body.pool ?? [];
  if (!Array.isArray(poolBody)) return fail("pool must be an array");
  if (poolBody.length > MAX_POOL_TILES) {
    return fail(`pool must contain at most ${MAX_POOL_TILES} tiles`);
  }
  const slots = [];
  let settingsCount = 0;
  for (let i = 0; i < body.slots.length; i++) {
    const slot = normalizeTile(body.slots[i], `slots[${i}]`, { inPool: false });
    if (!slot.ok) return slot;
    if (slot.value.kind === "settings") settingsCount++;
    slots.push(slot.value);
  }
  const pool = [];
  for (let i = 0; i < poolBody.length; i++) {
    const tile = normalizeTile(poolBody[i], `pool[${i}]`, { inPool: true });
    if (!tile.ok) return tile;
    pool.push(tile.value);
  }
  const seen = new Set();
  for (const tile of [...slots, ...pool]) {
    if (tile.kind === "blank") continue;
    if (seen.has(tile.id)) return fail(`duplicate tile id: ${tile.id}`);
    seen.add(tile.id);
  }
  if (settingsCount < 1) {
    return fail("at least one settings tile is required in the slots so the settings screen stays reachable");
  }
  return pass({ v: LAYOUT_VERSION, slots, pool });
}

// Upgrades a stored layout of any supported version to normalized v2.
// v1 layouts gain tile ids (s0..s15 by slot position) and an empty pool.
export function upgradeLayout(stored) {
  if (!isPlainObject(stored)) return fail("layout must be an object");
  if (stored.v === LAYOUT_VERSION) return validateLayout(stored);
  if (!Array.isArray(stored.slots)) return fail("layout must contain slots");
  const slots = stored.slots.map((raw, i) => {
    if (!isPlainObject(raw) || raw.kind === "blank") return { kind: "blank" };
    return { ...raw, id: `s${i}` };
  });
  return validateLayout({ v: LAYOUT_VERSION, slots, pool: [] });
}
