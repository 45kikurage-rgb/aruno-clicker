import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { validateLayout, upgradeLayout } from "../src/layout.js";

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

// Stored rows written by the v1 Worker have no tile ids and no pool.
const seedLayoutV1 = {
  v: 1, revision: 1, updatedAt: "2026-11-19T00:00:00.000Z",
  slots: Array.from({ length: 16 }, () => ({ kind: "blank" })),
};
seedLayoutV1.slots[0] = { kind: "web", title: "争奪戦", icon: "honey", url: "https://example.com/1" };
seedLayoutV1.slots[10] = { kind: "settings", title: "設定", icon: "settings" };

function blankSlots() {
  return Array.from({ length: 16 }, () => ({ kind: "blank" }));
}
function validV2Slots() {
  const slots = blankSlots();
  slots[0] = { id: "s0", kind: "web", title: "争奪戦", icon: "honey", url: "https://example.com/1" };
  slots[10] = { id: "s10", kind: "settings", title: "設定", icon: "settings" };
  return slots;
}

async function makeEnv() {
  return { DB: new MemoryD1({ layout: structuredClone(seedLayoutV1), adminKeyHash: await sha256Hex(ADMIN_KEY) }) };
}

function req(path, options = {}) {
  return new Request(`https://example.workers.dev${path}`, options);
}

function putLayout(env, layout, expectedRevision = 1) {
  return worker.fetch(req("/v1/home-layout", {
    method: "PUT",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${ADMIN_KEY}` },
    body: JSON.stringify({ expectedRevision, layout }),
  }), env);
}

test("GET /v1/home-layout returns the stored layout upgraded to v2", async () => {
  const env = await makeEnv();
  const res = await worker.fetch(req("/v1/home-layout"), env);
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.v, 2);
  assert.equal(body.revision, 1);
  assert.equal(body.slots.length, 16);
  assert.equal(body.slots[0].title, "争奪戦");
  assert.equal(body.slots[0].id, "s0");
  assert.deepEqual(body.pool, []);
});

test("GET on an empty database seeds the initial v2 layout at runtime", async () => {
  const env = { DB: new MemoryD1({ adminKeyHash: await sha256Hex(ADMIN_KEY) }) };
  const res = await worker.fetch(req("/v1/home-layout"), env);
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.v, 2);
  assert.equal(body.revision, 1);
  assert.equal(body.slots.length, 16);
  assert.equal(body.slots[0].id, "s0");
  assert.equal(body.slots[11].kind, "menu");
  assert.equal(body.slots[11].items.length, 4);
  assert.equal(body.slots[11].items[0].kind, "update");
  assert.deepEqual(body.pool, []);
});

test("PUT without authentication is rejected", async () => {
  const env = await makeEnv();
  const res = await worker.fetch(req("/v1/home-layout", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: 1, layout: { v: 2, slots: validV2Slots(), pool: [] } }),
  }), env);
  assert.equal(res.status, 401);
});

test("PUT with the admin key updates the layout, pool included, and bumps the revision", async () => {
  const env = await makeEnv();
  const slots = validV2Slots();
  slots[1] = { id: "c1", kind: "copy", title: "合言葉", copyType: "text", text: "abc123" };
  const pool = [{ id: "p1", kind: "update", title: "Tiker更新", pkg: "jp.aruno.clicker" }];
  const res = await putLayout(env, { v: 2, slots, pool });
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.revision, 2);

  const get = await worker.fetch(req("/v1/home-layout"), env);
  const stored = await get.json();
  assert.equal(stored.v, 2);
  assert.equal(stored.revision, 2);
  // Copy slots are normalized to the copy icon.
  assert.equal(stored.slots[1].icon, "copy");
  assert.equal(stored.slots[1].text, "abc123");
  assert.equal(stored.pool.length, 1);
  assert.equal(stored.pool[0].kind, "update");
  assert.equal(env.DB.history.length, 2);
});

test("PUT with a stale expectedRevision conflicts", async () => {
  const env = await makeEnv();
  const res = await putLayout(env, { v: 2, slots: validV2Slots(), pool: [] }, 7);
  assert.equal(res.status, 409);
  const body = await res.json();
  assert.equal(body.error.currentRevision, 1);
});

test("PUT rejects a layout without any settings tile in the slots", async () => {
  const env = await makeEnv();
  const res = await putLayout(env, { v: 2, slots: blankSlots(), pool: [] });
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
  assert.ok(html.includes("ホーム画面の配置"));
  assert.ok(html.includes("保存"));
  assert.ok(html.includes("タイルを作る"));
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

test("validateLayout enforces the v2 format rules", () => {
  const base = () => validV2Slots();
  assert.equal(validateLayout({ v: 2, slots: base(), pool: [] }).ok, true);
  assert.equal(validateLayout({ v: 2, slots: base() }).ok, true); // pool optional

  let slots = base();
  slots[0] = { id: "m1", kind: "menu", title: "各更新", icon: "updates", items: Array.from({ length: 9 }, () => ({ kind: "storage", title: "x" })) };
  assert.equal(validateLayout({ v: 2, slots }).ok, false); // menu over 8 items

  slots = base();
  slots[0] = { id: "w1", kind: "web", title: "x", icon: "link", url: "http://example.com/" };
  assert.equal(validateLayout({ v: 2, slots }).ok, false); // http is not allowed

  slots = base();
  slots[0] = { id: "w1", kind: "web", title: "Chrome", icon: "pkg:com.android.chrome", url: "https://example.com/", browser: "com.android.chrome" };
  assert.equal(validateLayout({ v: 2, slots }).ok, true); // browser parameter (decided form)

  slots = base();
  slots[0] = { id: "a1", kind: "app", title: "LINE", pkg: "not a package" };
  assert.equal(validateLayout({ v: 2, slots }).ok, false);

  slots = base();
  slots[0] = { kind: "app", title: "LINE", pkg: "jp.naver.line.android" };
  assert.equal(validateLayout({ v: 2, slots }).ok, false); // id is required in v2

  slots = base();
  slots[0] = { id: "d1", kind: "download", title: "入手", pkg: "jp.aruno.clicker", url: "https://example.com/get" };
  slots[1] = { id: "u1", kind: "update", title: "更新", pkg: "jp.naver.line.android" }; // URL optional
  slots[2] = { id: "l1", kind: "label", title: "見出し", icon: "tag" };
  assert.equal(validateLayout({ v: 2, slots }).ok, true); // new kinds

  slots = base();
  slots[0] = { id: "i1", kind: "install", title: "かんたんツール", pkg: "com.example.kantan", url: "https://example.com/app.apk" };
  assert.equal(validateLayout({ v: 2, slots }).ok, true); // install with an APK URL
  slots[0] = { id: "i1", kind: "install", title: "かんたんツール", pkg: "com.example.kantan" };
  assert.equal(validateLayout({ v: 2, slots }).ok, false); // install requires the APK URL

  slots = base();
  const dupPool = [{ id: "s0", kind: "label", title: "重複", icon: "tag" }];
  assert.equal(validateLayout({ v: 2, slots, pool: dupPool }).ok, false); // duplicate id

  const poolOnly = [{ id: "p9", kind: "settings", title: "設定", icon: "settings" }];
  const noSlotSettings = blankSlots();
  assert.equal(validateLayout({ v: 2, slots: noSlotSettings, pool: poolOnly }).ok, false); // settings must be in slots

  slots = base();
  slots[0] = { id: "c1", kind: "copy", title: "ID", copyType: "deviceId" };
  const checked = validateLayout({ v: 2, slots });
  assert.equal(checked.ok, true);
  assert.equal(checked.value.slots[0].icon, "copy"); // icon forced

  assert.equal(validateLayout({ v: 1, slots: base() }).ok, false); // v1 is not accepted as v2
  assert.equal(validateLayout({ v: 2, slots: base().slice(0, 15) }).ok, false); // not 16 slots
});

test("upgradeLayout converts a stored v1 layout to v2", () => {
  const v1 = structuredClone(seedLayoutV1);
  v1.slots[11] = {
    kind: "menu", title: "各更新", icon: "updates",
    items: [{ kind: "updateApp", title: "Chrome更新", pkg: "com.android.chrome" }],
  };
  const upgraded = upgradeLayout(v1);
  assert.equal(upgraded.ok, true);
  assert.equal(upgraded.value.v, 2);
  assert.equal(upgraded.value.slots[0].id, "s0");
  assert.equal(upgraded.value.slots[11].items[0].kind, "update"); // updateApp normalized
  assert.deepEqual(upgraded.value.pool, []);

  const again = upgradeLayout({ v: 2, slots: validV2Slots(), pool: [] });
  assert.equal(again.ok, true); // v2 passes through
});
