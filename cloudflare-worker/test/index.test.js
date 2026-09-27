import test from "node:test";
import assert from "node:assert/strict";
import worker, { testables } from "../src/index.js";

class Statement {
  constructor(db, sql) { this.db = db; this.sql = sql; this.params = []; }
  bind(...params) { this.params = params; return this; }
  async first() {
    if (this.sql.includes("FROM remote_config")) {
      if (this.sql.includes("SELECT config_version")) return { config_version: this.db.config.config_version };
      return { ...this.db.config };
    }
    if (this.sql.includes("FROM admin_settings")) return null;
    return null;
  }
  async all() {
    if (this.sql.includes("PRAGMA table_info(remote_config)")) {
      return { results: ["id", "name1", "url1", "name2", "url2", "share_name", "share_url", "config_version", "updated_at", "update_id"].map((name) => ({ name })) };
    }
    if (this.sql.includes("PRAGMA table_info(config_history)")) {
      return { results: ["config_version", "name1", "url1", "name2", "url2", "share_name", "share_url", "updated_at"].map((name) => ({ name })) };
    }
    if (this.sql.includes("FROM config_history")) return { results: [...this.db.history].reverse().slice(0, 20) };
    return { results: [] };
  }
  async run() { return { success: true, meta: { changes: 1 } }; }
}

class MemoryD1 {
  constructor() {
    this.config = {
      name1: "first", url1: "https://lite.tiktok.com/t/first/",
      name2: "second", url2: "https://lite.tiktok.com/t/second/",
      share_name: "shared", share_url: "https://lite.tiktok.com/t/shared/",
      config_version: 1, updated_at: "2026-09-22T00:00:00.000Z", update_id: "initial",
    };
    this.history = [{ ...this.config }];
  }
  prepare(sql) { return new Statement(this, sql); }
  async exec() { return { count: 1, duration: 0 }; }
  async batch(statements) {
    const update = statements[0];
    const [name1, url1, name2, url2, shareName, shareUrl, nextVersion, updatedAt, updateId, expectedVersion] = update.params;
    let changes = 0;
    if (this.config.config_version === expectedVersion) {
      this.config = {
        name1, url1, name2, url2, share_name: shareName, share_url: shareUrl,
        config_version: nextVersion, updated_at: updatedAt, update_id: updateId,
      };
      changes = 1;
    }
    if (changes === 1 && statements[1].params[0] === this.config.update_id) this.history.push({ ...this.config });
    return [{ success: true, meta: { changes } }, { success: true }, { success: true }];
  }
}

class LegacyD1 {
  constructor() {
    this.columns = {
      remote_config: new Set(["id", "url1", "url2", "config_version", "updated_at", "update_id"]),
      config_history: new Set(["config_version", "url1", "url2", "updated_at"]),
    };
    this.recordedMigration = null;
  }
  prepare(sql) {
    const db = this;
    return {
      params: [],
      bind(...params) { this.params = params; return this; },
      async all() {
        const match = /PRAGMA table_info\(([^)]+)\)/.exec(sql);
        return { results: match ? [...db.columns[match[1]]].map((name) => ({ name })) : [] };
      },
      async run() {
        if (sql.includes("d1_migrations")) db.recordedMigration = this.params[0];
        return { success: true, meta: { changes: 1 } };
      },
    };
  }
  async exec(sql) {
    const match = /ALTER TABLE (\w+) ADD COLUMN (\w+)/.exec(sql);
    if (match) this.columns[match[1]].add(match[2]);
    return { count: 1, duration: 0 };
  }
}

function makeEnv(overrides = {}) {
  return {
    DB: new MemoryD1(), ADMIN_TOKEN: "test-admin-token",
    ALLOWED_ADMIN_ORIGIN: "https://admin.example.com", ...overrides,
  };
}

async function readJson(response) { return JSON.parse(await response.text()); }

test("GET returns all three destinations and private labels", async () => {
  const response = await worker.fetch(new Request("https://worker.example/v1/config"), makeEnv());
  assert.equal(response.status, 200);
  assert.deepEqual(await readJson(response), {
    name1: "first", url1: "https://lite.tiktok.com/t/first/",
    name2: "second", url2: "https://lite.tiktok.com/t/second/",
    shareName: "shared", shareUrl: "https://lite.tiktok.com/t/shared/",
    configVersion: 1, updatedAt: "2026-09-22T00:00:00.000Z", schemaVersion: 2,
  });
});

test("PUT stores all three destinations and increments the version", async () => {
  const env = makeEnv();
  const response = await worker.fetch(new Request("https://worker.example/v1/config", {
    method: "PUT",
    headers: { Authorization: "Bearer test-admin-token", "Content-Type": "application/json" },
    body: JSON.stringify({
      name1: "A", url1: "https://lite.tiktok.com/t/a",
      name2: "B", url2: "https://lite.tiktok.com/t/b",
      shareName: "C", shareUrl: "https://lite.tiktok.com/t/c",
      expectedConfigVersion: 1,
    }),
  }), env);
  assert.equal(response.status, 200);
  const body = await readJson(response);
  assert.equal(body.configVersion, 2);
  assert.equal(body.shareName, "C");
  assert.equal(body.shareUrl, "https://lite.tiktok.com/t/c");
  assert.equal(env.DB.history.length, 2);
});

test("PUT requires authorization and rejects missing third destination", async () => {
  const unauthorized = await worker.fetch(new Request("https://worker.example/v1/config", {
    method: "PUT", headers: { "Content-Type": "application/json" }, body: "{}",
  }), makeEnv());
  assert.equal(unauthorized.status, 401);

  const incomplete = await worker.fetch(new Request("https://worker.example/v1/config", {
    method: "PUT",
    headers: { Authorization: "Bearer test-admin-token", "Content-Type": "application/json" },
    body: JSON.stringify({ name1: "A", url1: "https://lite.tiktok.com/t/a", name2: "B", url2: "https://lite.tiktok.com/t/b", shareName: "C" }),
  }), makeEnv());
  assert.equal(incomplete.status, 400);
});

test("validation accepts short labels and only HTTPS TikTok URLs", () => {
  assert.equal(testables.validateName("label", "name").ok, true);
  assert.equal(testables.validateName("x".repeat(81), "name").ok, false);
  assert.equal(testables.validateStartupUrl("http://lite.tiktok.com/t/a", "url").ok, false);
  assert.equal(testables.validateStartupUrl("https://example.com/t/a", "url").ok, false);
  assert.equal(testables.validateStartupUrl("https://lite.tiktok.com/t/a", "url").ok, true);
});

test("legacy schema is upgraded once through the existing D1 binding", async () => {
  const db = new LegacyD1();
  await testables.ensureLatestSchema(db);
  assert.deepEqual(
    [...db.columns.remote_config].filter((name) => ["name1", "name2", "share_name", "share_url"].includes(name)).sort(),
    ["name1", "name2", "share_name", "share_url"],
  );
  assert.deepEqual(
    [...db.columns.config_history].filter((name) => ["name1", "name2", "share_name", "share_url"].includes(name)).sort(),
    ["name1", "name2", "share_name", "share_url"],
  );
  assert.equal(db.recordedMigration, "0003_three_destinations.sql");
});
