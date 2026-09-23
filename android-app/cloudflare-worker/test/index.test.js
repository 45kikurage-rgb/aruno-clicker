import test from "node:test";
import assert from "node:assert/strict";
import worker, { testables } from "../src/index.js";

class Statement {
  constructor(db, sql) { this.db = db; this.sql = sql; this.params = []; }
  bind(...params) { this.params = params; return this; }
  async first() {
    if (this.sql.includes("FROM remote_config")) {
      if (!this.db.config) return null;
      if (this.sql.includes("SELECT config_version")) return { config_version: this.db.config.config_version };
      return { ...this.db.config };
    }
    return null;
  }
  async all() {
    if (this.sql.includes("FROM config_history")) return { results: [...this.db.history].reverse().slice(0, 20) };
    return { results: [] };
  }
}

class MemoryD1 {
  constructor() {
    this.config = {
      url1: "https://lite.tiktok.com/t/first/",
      url2: "https://lite.tiktok.com/t/second/",
      config_version: 1,
      updated_at: "2026-09-22T00:00:00.000Z",
      update_id: "initial",
    };
    this.history = [{ ...this.config }];
  }
  prepare(sql) { return new Statement(this, sql); }
  async batch(statements) {
    const update = statements[0];
    const [url1, url2, nextVersion, updatedAt, updateId, expectedVersion] = update.params;
    let changes = 0;
    if (this.config.config_version === expectedVersion) {
      this.config = { url1, url2, config_version: nextVersion, updated_at: updatedAt, update_id: updateId };
      changes = 1;
    }
    if (changes === 1 && statements[1].params[0] === this.config.update_id) this.history.push({ ...this.config });
    this.history = this.history.sort((a, b) => b.config_version - a.config_version).slice(0, 20).reverse();
    return [{ success: true, meta: { changes } }, { success: true }, { success: true }];
  }
}

function makeEnv(overrides = {}) {
  return {
    DB: new MemoryD1(),
    ADMIN_TOKEN: "test-admin-token",
    ALLOWED_ADMIN_ORIGIN: "https://admin.example.com",
    ...overrides,
  };
}

async function readJson(response) { return JSON.parse(await response.text()); }

test("GET at root returns the current shared configuration", async () => {
  const response = await worker.fetch(new Request("https://worker.example/"), makeEnv());
  assert.equal(response.status, 200);
  assert.deepEqual(await readJson(response), {
    url1: "https://lite.tiktok.com/t/first/",
    url2: "https://lite.tiktok.com/t/second/",
    configVersion: 1,
    updatedAt: "2026-09-22T00:00:00.000Z",
    schemaVersion: 1,
  });
  assert.equal(response.headers.get("Cache-Control"), "no-store, max-age=0");
});

test("PUT requires the administrator bearer token", async () => {
  const response = await worker.fetch(new Request("https://worker.example/", {
    method: "PUT", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ url1: "https://lite.tiktok.com/t/one", url2: "https://lite.tiktok.com/t/two" }),
  }), makeEnv());
  assert.equal(response.status, 401);
  assert.equal((await readJson(response)).error.code, "unauthorized");
});

test("PUT stores valid TikTok URLs, increments version, and records history", async () => {
  const env = makeEnv();
  const response = await worker.fetch(new Request("https://worker.example/v1/config", {
    method: "PUT",
    headers: { Authorization: "Bearer test-admin-token", "Content-Type": "application/json" },
    body: JSON.stringify({
      url1: "https://lite.tiktok.com/t/new-one", url2: "https://www.tiktok.com/t/new-two",
      expectedConfigVersion: 1,
    }),
  }), env);
  assert.equal(response.status, 200);
  const updated = await readJson(response);
  assert.equal(updated.configVersion, 2);
  assert.equal(updated.url1, "https://lite.tiktok.com/t/new-one");
  assert.equal(env.DB.history.length, 2);
  assert.equal(env.DB.history.at(-1).config_version, 2);
});

test("PUT rejects stale expectedConfigVersion", async () => {
  const response = await worker.fetch(new Request("https://worker.example/", {
    method: "PUT",
    headers: { Authorization: "Bearer test-admin-token", "Content-Type": "application/json" },
    body: JSON.stringify({
      url1: "https://lite.tiktok.com/t/new-one", url2: "https://lite.tiktok.com/t/new-two",
      expectedConfigVersion: 9,
    }),
  }), makeEnv());
  assert.equal(response.status, 409);
  const body = await readJson(response);
  assert.equal(body.error.code, "version_conflict");
  assert.equal(body.error.currentConfigVersion, 1);
});

test("URL validation permits only HTTPS TikTok hosts", () => {
  assert.equal(testables.validateStartupUrl("http://lite.tiktok.com/t/a", "url").ok, false);
  assert.equal(testables.validateStartupUrl("https://example.com/t/a", "url").ok, false);
  assert.equal(testables.validateStartupUrl("https://user:pass@lite.tiktok.com/t/a", "url").ok, false);
  assert.equal(testables.validateStartupUrl("https://lite.tiktok.com/t/a", "url").ok, true);
});

test("history is admin-only", async () => {
  const denied = await worker.fetch(new Request("https://worker.example/v1/history"), makeEnv());
  assert.equal(denied.status, 401);
  const allowed = await worker.fetch(new Request("https://worker.example/v1/history", {
    headers: { Authorization: "Bearer test-admin-token" },
  }), makeEnv());
  assert.equal(allowed.status, 200);
  assert.equal((await readJson(allowed)).history.length, 1);
});

test("CORS is emitted only for the configured exact origin", async () => {
  const env = makeEnv();
  const allowed = await worker.fetch(new Request("https://worker.example/", {
    headers: { Origin: "https://admin.example.com" },
  }), env);
  assert.equal(allowed.headers.get("Access-Control-Allow-Origin"), "https://admin.example.com");
  const other = await worker.fetch(new Request("https://worker.example/", {
    headers: { Origin: "https://evil.example" },
  }), env);
  assert.equal(other.headers.get("Access-Control-Allow-Origin"), null);
});
