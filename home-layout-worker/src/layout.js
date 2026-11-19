// Aruno-Home/T HOME layout (format v1): validation and normalization.
// Shared by the Worker handlers; kept free of Worker/D1 APIs for testing.

export const LAYOUT_VERSION = 1;
export const SLOT_COUNT = 16;
export const MENU_MAX_ITEMS = 8;
export const MAX_TITLE_LENGTH = 40;
export const MAX_TEXT_LENGTH = 500;
export const MAX_URL_LENGTH = 2048;

// Built-in icon ids shipped in the Aruno-Home/T app (ic_home_<id> drawables).
// An icon may also be "pkg:<package>" to borrow an installed app's icon.
export const BUILTIN_ICONS = [
  "honey", "qr", "pig", "airplane", "live", "tiker", "mail",
  "settings", "updates", "id", "copy", "link", "wifi", "storage",
];

export const SLOT_KINDS = ["app", "web", "copy", "settings", "menu", "wifi", "airplane", "blank"];
export const MENU_ITEM_KINDS = ["updateApp", "storage", "web", "app", "copy"];
export const COPY_TYPES = ["text", "url", "deviceId"];

const PACKAGE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/;
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

// Fields shared by a copy slot and a copy menu item.
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

function normalizeSlot(raw, index) {
  const field = `slots[${index}]`;
  if (!isPlainObject(raw)) return fail(`${field} must be an object`);
  if (!SLOT_KINDS.includes(raw.kind)) return fail(`${field}.kind is not supported`);
  if (raw.kind === "blank") return pass({ kind: "blank" });

  const title = checkTitle(raw.title, `${field}.title`);
  if (!title.ok) return title;
  const out = { kind: raw.kind, title: title.value };

  if (raw.kind === "app") {
    const pkg = checkPackage(raw.pkg, `${field}.pkg`);
    if (!pkg.ok) return pkg;
    out.pkg = pkg.value;
    return pass(out);
  }
  if (raw.kind === "copy") {
    // Copy slots always use the copy (two papers) icon; the title states
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
  // settings / wifi / airplane need no extra fields.
  return pass(out);
}

function normalizeMenuItem(raw, field) {
  if (!isPlainObject(raw)) return fail(`${field} must be an object`);
  if (!MENU_ITEM_KINDS.includes(raw.kind)) return fail(`${field}.kind is not supported`);
  const title = checkTitle(raw.title, `${field}.title`);
  if (!title.ok) return title;
  const out = { kind: raw.kind, title: title.value };

  const icon = checkIcon(raw.icon, `${field}.icon`, { required: false });
  if (!icon.ok) return icon;
  if (icon.value !== undefined) out.icon = icon.value;

  if (raw.kind === "updateApp" || raw.kind === "app") {
    const pkg = checkPackage(raw.pkg, `${field}.pkg`);
    if (!pkg.ok) return pkg;
    out.pkg = pkg.value;
  } else if (raw.kind === "web") {
    const web = checkWebFields(raw, field);
    if (!web.ok) return web;
    Object.assign(out, web.value);
  } else if (raw.kind === "copy") {
    if (out.icon === undefined) out.icon = "copy";
    const copy = checkCopyFields(raw, field);
    if (!copy.ok) return copy;
    Object.assign(out, copy.value);
  }
  // storage needs no extra fields.
  return pass(out);
}

// Validates a client-supplied layout body ({ slots: [...] }) and returns the
// normalized layout object ({ v, slots }) or an error message.
export function validateLayout(body) {
  if (!isPlainObject(body)) return fail("layout must be an object");
  if (body.v !== undefined && body.v !== LAYOUT_VERSION) {
    return fail(`unsupported layout version: ${body.v}`);
  }
  if (!Array.isArray(body.slots) || body.slots.length !== SLOT_COUNT) {
    return fail(`slots must contain exactly ${SLOT_COUNT} entries`);
  }
  const slots = [];
  let settingsCount = 0;
  for (let i = 0; i < body.slots.length; i++) {
    const slot = normalizeSlot(body.slots[i], i);
    if (!slot.ok) return slot;
    if (slot.value.kind === "settings") settingsCount++;
    slots.push(slot.value);
  }
  if (settingsCount < 1) {
    return fail("at least one settings slot is required so the settings screen stays reachable");
  }
  return pass({ v: LAYOUT_VERSION, slots });
}
