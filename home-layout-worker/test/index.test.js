import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { validateLayout } from "../src/layout.js";

// ---- In-memory D1 stub covering the statements this Worker issues ----
class Statement {
  constructor(db, sql) { this.db = db; this.sql = sql; this.params = []; }
  bind(...params) { this.params = params; return this; }
  async first() {
    if (this.sql.includes("FROM home_layout_config")) {
      return this.db.layout ? { layout_json: this.db.layoutJson, revision: this.db.layout.revision, updated_at: this.db.layout.updatedAt } : null;
    }
    if (this.sql.includes("FROM admin_settings")) {
      return this.db.adminKeyHash ? { key_hash: this.db.adminKeyHash } : null;
    }
    return null;
  }
  async run() {
    if (this.sql.includes("INSERT OR IGNORE INTO home_layout_config") && !this.db.layout) {
      this.db.layout = JSON.parse(this.params[0]);
      this.db.layoutJson = this.params[0];
    }
    return { success: true, meta: { changes: 1 } };
  }
}

class MemoryD1 {
  constructor({ layout = null, adminKeyHash = null } = {}) {
    this.layout = layout;
    this.layoutJson = layout ? JSON.stringify(layout) : null;
    this.history = layout ? [layout] : [];
    this.adminKeyHash = adminKeyHash;
  }
  prepare(sql) { return new Statement(this, sql); }
  async exec() { return { count: 1, duration: 0 }; }
  async batch(statements) {
    const results = [];
    for (const stmt of statements) {
      if (stmt.sql.includes("UPDATE home_layout_config")) {
        const [layoutJson, revision, updatedAt, , expected] = stmt.params;
        if (this.layout && this.layout.revision === expected) {
          this.layout = JSON.parse(layoutJson);
          this.layoutJson = layoutJson;
          results.push({ success: true, meta: { changes: 1 } });
        } else {
          results.push({ success: true, meta: { changes: 0 } });
        }
      } else if (stmt.sql.includes("INSERT INTO home_layout_config")) {
        const [layoutJson] = stmt.params;
        this.layout = JSON.parse(layoutJson);
        this.layoutJson = layoutJson;
        results.push({ success: true, meta: { changes: 1 } });
      } else if (stmt.sql.includes("INSERT INTO home_layout_history")) {
        this.history.push(JSON.parse(stmt.params[1]));
        results.push({ success: true, meta: { changes: 1 } });
      } else {
        results.push({ success: true, meta: { changes: 1 } });
      }
    }
    return results;
  }
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

const ADMIN_KEY = "testkey1";
const seedLayout = {
  v: 1, revision: 1, updatedAt: "2026-11-19T00:00:00.000Z",
  slots: [
    { kind: "web", title: "争奪戦", icon: "honey", url: "https://example.com/1" },
    ...Array.from({ length: 14 }, () => ({ kind: "blank" })),
    { kind: "settings", title: "設定", icon: "settings" },
  ],
};
// Move settings into range: rebuild explicitly (16 slots, settings at index 10)
seedLayout.slots = Array.from({ length: 16 }, () => ({ kind: "blank" }));
seedLayout.slots[0] = { kind: "web", title: "争奪戦", icon: "honey", url: "https://example.com/1" };
seedLayout.slots[10] = { kind: "settings", title: "設定", icon: "settings" };

async function makeEnv() {
  return { DB: new MemoryD1({ layout: structuredClone(seedLayout), adminKeyHash: await sha256Hex(ADMIN_KEY) }) };
}

function req(path, options = {}) {
  return new Request(`https://example.workers.dev${path}`, options);
}

test("GET /v1/home-layout returns the stored layout", async () => {
  const env = await makeEnv();
  const res = await worker.fetch(req("/v1/home-layout"), env);
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.revision, 1);
  assert.equal(body.slots.length, 16);
  assert.equal(body.slots[0].title, "争奪戦");
});

test("GET on an empty database seeds the initial layout at runtime", async () => {
  const env = { DB: new MemoryD1({ adminKeyHash: await sha256Hex(ADMIN_KEY) }) };
  const res = await worker.fetch(req("/v1/home-layout"), env);
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.revision, 1);
  assert.equal(body.slots.length, 16);
  assert.equal(body.slots[11].kind, "menu");
  assert.equal(body.slots[11].items.length, 4);
});

test("PUT without authentication is rejected", async () => {
  const env = await makeEnv();
  const res = await worker.fetch(req("/v1/home-layout", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: 1, layout: { v: 1, slots: seedLayout.slots } }),
  }), env);
  assert.equal(res.status, 401);
});

test("PUT with the admin key updates the layout and bumps the revision", async () => {
  const env = await makeEnv();
  const slots = structuredClone(seedLayout.slots);
  slots[1] = { kind: "copy", title: "合言葉", copyType: "text", text: "abc123" };
  const res = await worker.fetch(req("/v1/home-layout", {
    method: "PUT",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${ADMIN_KEY}` },
    body: JSON.stringify({ expectedRevision: 1, layout: { v: 1, slots } }),
  }), env);
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.revision, 2);

  const get = await worker.fetch(req("/v1/home-layout"), env);
  const stored = await get.json();
  assert.equal(stored.revision, 2);
  // Copy slots are normalized to the copy icon.
  assert.equal(stored.slots[1].icon, "copy");
  assert.equal(stored.slots[1].text, "abc123");
  assert.equal(env.DB.history.length, 2);
});

test("PUT with a stale expectedRevision conflicts", async () => {
  const env = await makeEnv();
  const res = await worker.fetch(req("/v1/home-layout", {
    method: "PUT",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${ADMIN_KEY}` },
    body: JSON.stringify({ expectedRevision: 7, layout: { v: 1, slots: seedLayout.slots } }),
  }), env);
  assert.equal(res.status, 409);
  const body = await res.json();
  assert.equal(body.error.currentRevision, 1);
});

test("PUT rejects a layout without any settings slot", async () => {
  const env = await makeEnv();
  const slots = Array.from({ length: 16 }, () => ({ kind: "blank" }));
  const res = await worker.fetch(req("/v1/home-layout", {
    method: "PUT",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${ADMIN_KEY}` },
    body: JSON.stringify({ expectedRevision: 1, layout: { v: 1, slots } }),
  }), env);
  assert.equal(res.status, 400);
});

test("login with the admin key sets a persistent cookie that authenticates the editor", async () => {
  const env = await makeEnv();
  const form = new FormData();
  form.set("key", ADMIN_KEY);
  const res = await worker.fetch(req("/home/login", { method: "POST", body: form }), env);
  assert.equal(res.status, 303);
  const cookie = res.headers.get("Set-Cookie");
  assert.ok(cookie.includes("home_admin="));
  assert.ok(cookie.includes("Max-Age=34560000"));
  assert.ok(cookie.includes("HttpOnly"));

  const page = await worker.fetch(req("/home", { headers: { Cookie: cookie.split(";")[0] } }), env);
  assert.equal(page.status, 200);
  const html = await page.text();
  assert.ok(html.includes("ホーム画面変更"));
  assert.ok(html.includes("保存"));
});

test("login with a wrong key is rejected and the editor stays locked", async () => {
  const env = await makeEnv();
  const form = new FormData();
  form.set("key", "wrongkey");
  const res = await worker.fetch(req("/home/login", { method: "POST", body: form }), env);
  assert.equal(res.status, 401);

  const page = await worker.fetch(req("/home"), env);
  const html = await page.text();
  assert.ok(html.includes("管理キー"));
  assert.ok(!html.includes('id="saveBtn"'));
});

test("validateLayout enforces the format rules", () => {
  const base = () => {
    const slots = Array.from({ length: 16 }, () => ({ kind: "blank" }));
    slots[10] = { kind: "settings", title: "設定", icon: "settings" };
    return slots;
  };
  assert.equal(validateLayout({ v: 1, slots: base() }).ok, true);

  let slots = base();
  slots[0] = { kind: "menu", title: "各更新", icon: "updates", items: Array.from({ length: 9 }, () => ({ kind: "storage", title: "x" })) };
  assert.equal(validateLayout({ v: 1, slots }).ok, false); // menu over 8 items

  slots = base();
  slots[0] = { kind: "web", title: "x", icon: "link", url: "http://example.com/" };
  assert.equal(validateLayout({ v: 1, slots }).ok, false); // http is not allowed

  slots = base();
  slots[0] = { kind: "web", title: "Chrome", icon: "pkg:com.android.chrome", url: "https://example.com/", browser: "com.android.chrome" };
  assert.equal(validateLayout({ v: 1, slots }).ok, true); // browser parameter (decided form)

  slots = base();
  slots[0] = { kind: "app", title: "LINE", pkg: "not a package" };
  assert.equal(validateLayout({ v: 1, slots }).ok, false);

  slots = base();
  slots[0] = { kind: "copy", title: "ID", copyType: "deviceId" };
  const checked = validateLayout({ v: 1, slots });
  assert.equal(checked.ok, true);
  assert.equal(checked.value.slots[0].icon, "copy"); // icon forced

  assert.equal(validateLayout({ v: 2, slots: base() }).ok, false); // bad version
  assert.equal(validateLayout({ v: 1, slots: base().slice(0, 15) }).ok, false); // not 16 slots
});
