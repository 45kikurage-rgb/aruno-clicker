import test from "node:test";
import assert from "node:assert/strict";
import worker, { testables } from "../src/index.js";

// In-memory D1 stub covering both the legacy remote_config flow and the
// new url_runner tables, so the tests prove the new endpoint works and
// the legacy endpoints are unaffected.
class Statement {
  constructor(db, sql) { this.db = db; this.sql = sql; this.params = []; }
  bind(...params) { this.params = params; return this; }

  async first() {
    if (this.sql.includes("FROM url_runner_config")) {
      return this.db.urlRunner ? { ...this.db.urlRunner } : null;
    }
    if (this.sql.includes("FROM remote_config")) {
      if (this.sql.includes("SELECT config_version")) {
        return { config_version: this.db.config.config_version };
      }
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
    this.urlRunner = null;
    this.urlRunnerHistory = [];
  }

  prepare(sql) { return new Statement(this, sql); }
  async exec() { return { count: 1, duration: 0 }; }

  async batch(statements) {
    const first = statements[0];
    if (first.sql.includes("url_runner_config")) {
      const [urlsJson, secondsJson, commonSeconds, nextVersion, updatedAt, updateId, optionsJson, expectedVersion] = first.params;
      let changes = 0;
      if (!this.urlRunner) {
        this.urlRunner = {
          urls_json: urlsJson, individual_seconds_json: secondsJson,
          common_seconds: commonSeconds, config_version: nextVersion,
          updated_at: updatedAt, update_id: updateId, options_json: optionsJson,
        };
        changes = 1;
      } else if (this.urlRunner.config_version === expectedVersion) {
        this.urlRunner = {
          urls_json: urlsJson, individual_seconds_json: secondsJson,
          common_seconds: commonSeconds, config_version: nextVersion,
          updated_at: updatedAt, update_id: updateId, options_json: optionsJson,
        };
        changes = 1;
      }
      if (changes === 1 && statements[1].params[0] === this.urlRunner.update_id) {
        this.urlRunnerHistory.push({
          config_version: this.urlRunner.config_version,
          urls_json: this.urlRunner.urls_json,
          individual_seconds_json: this.urlRunner.individual_seconds_json,
          common_seconds: this.urlRunner.common_seconds,
          updated_at: this.urlRunner.updated_at,
        });
      }
      return [{ success: true, meta: { changes } }, { success: true }, { success: true }];
    }
    // Legacy remote_config batch (same behaviour as the existing test stub).
    const update = first;
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

function makeEnv(overrides = {}) {
  return {
    DB: new MemoryD1(), ADMIN_TOKEN: "test-admin-token",
    ALLOWED_ADMIN_ORIGIN: "https://admin.example.com", ...overrides,
  };
}

async function readJson(response) { return JSON.parse(await response.text()); }

const AUTH = { Authorization: "Bearer test-admin-token", "Content-Type": "application/json" };

function validPayload(overrides = {}) {
  return {
    urls: [
      "https://lite.tiktok.com/t/AAA-bbb/?q=1",
      "https://vm.tiktok.com/CCC/",
      "https://lite.tiktok.com/t/AAA-bbb/?q=1",
    ],
    individualSeconds: [5, 10, 15, 20, 25],
    commonSeconds: 30,
    schemaVersion: 3,
    ...overrides,
  };
}

function put(env, body, headers = AUTH) {
  return worker.fetch(new Request("https://worker.example/v1/url-runner", {
    method: "PUT", headers, body: JSON.stringify(body),
  }), env);
}

test("GET before any PUT returns the empty default (version 0)", async () => {
  const response = await worker.fetch(new Request("https://worker.example/v1/url-runner"), makeEnv());
  assert.equal(response.status, 200);
  const body = await readJson(response);
  assert.deepEqual(body.urls, []);
  assert.equal(body.configVersion, 0);
  assert.equal(body.schemaVersion, 3);
});

test("PUT then GET round-trips order, duplicates and seconds", async () => {
  const env = makeEnv();
  const putResponse = await put(env, validPayload({ expectedConfigVersion: 0 }));
  assert.equal(putResponse.status, 200);
  const putBody = await readJson(putResponse);
  assert.equal(putBody.configVersion, 1);
  assert.equal(putBody.schemaVersion, 3);

  const getResponse = await worker.fetch(new Request("https://worker.example/v1/url-runner"), env);
  const getBody = await readJson(getResponse);
  assert.deepEqual(getBody.urls, validPayload().urls);
  assert.deepEqual(getBody.individualSeconds, [5, 10, 15, 20, 25]);
  assert.equal(getBody.commonSeconds, 30);
  assert.equal(getBody.configVersion, 1);
  assert.equal(env.DB.urlRunnerHistory.length, 1);
});

test("PUT without authorization is rejected", async () => {
  const response = await put(makeEnv(), validPayload(), { "Content-Type": "application/json" });
  assert.equal(response.status, 401);
});

test("PUT with a stale expectedConfigVersion conflicts (409)", async () => {
  const env = makeEnv();
  assert.equal((await put(env, validPayload({ expectedConfigVersion: 0 }))).status, 200);
  const conflict = await put(env, validPayload({ expectedConfigVersion: 0 }));
  assert.equal(conflict.status, 409);
  const body = await readJson(conflict);
  assert.equal(body.error.code, "version_conflict");
  assert.equal(body.error.currentConfigVersion, 1);
});

test("PUT with a disallowed host is rejected with its position (400)", async () => {
  const env = makeEnv();
  const response = await put(env, validPayload({
    urls: ["https://lite.tiktok.com/t/ok/", "https://example.com/not-allowed"],
    expectedConfigVersion: 0,
  }));
  assert.equal(response.status, 400);
  const body = await readJson(response);
  assert.equal(body.error.code, "validation_error");
  assert.match(body.error.message, /urls\[1\]/);
  assert.equal(env.DB.urlRunner, null);
});

test("PUT with 121 URLs is rejected, 120 are accepted", async () => {
  const env = makeEnv();
  const tooMany = Array.from({ length: 121 }, (_, i) => `https://lite.tiktok.com/t/${i}/`);
  const response = await put(env, validPayload({ urls: tooMany, expectedConfigVersion: 0 }));
  assert.equal(response.status, 400);
  assert.match((await readJson(response)).error.message, /121/);

  const exactlyMax = tooMany.slice(0, 120);
  const ok = await put(env, validPayload({ urls: exactlyMax, expectedConfigVersion: 0 }));
  assert.equal(ok.status, 200);
});

test("PUT rejects invalid seconds without storing anything", async () => {
  const env = makeEnv();
  const cases = [
    validPayload({ individualSeconds: [0, 10, 10, 10, 10] }),
    validPayload({ individualSeconds: [10, 10, 10, 10] }),
    validPayload({ commonSeconds: 3601 }),
    validPayload({ commonSeconds: 1.5 }),
    validPayload({ urls: ["http://lite.tiktok.com/t/http-only/"] }),
  ];
  for (const body of cases) {
    const response = await put(env, body);
    assert.equal(response.status, 400, JSON.stringify(body));
  }
  assert.equal(env.DB.urlRunner, null);
});

test("payload validator keeps duplicates and names every bad entry", () => {
  const result = testables.validateUrlRunnerPayload(validPayload({
    urls: ["https://example.com/a", "not-a-url", "https://lite.tiktok.com/t/ok/"],
  }));
  assert.equal(result.ok, false);
  assert.match(result.errors.join(" "), /urls\[0\]/);
  assert.match(result.errors.join(" "), /urls\[1\]/);
});

test("legacy /v1/config is unaffected by the new endpoint", async () => {
  const env = makeEnv();
  assert.equal((await put(env, validPayload({ expectedConfigVersion: 0 }))).status, 200);

  const getResponse = await worker.fetch(new Request("https://worker.example/v1/config"), env);
  assert.equal(getResponse.status, 200);
  const legacy = await readJson(getResponse);
  assert.equal(legacy.schemaVersion, 2);
  assert.equal(legacy.url1, "https://lite.tiktok.com/t/first/");
  assert.equal(legacy.configVersion, 1);

  const legacyPut = await worker.fetch(new Request("https://worker.example/v1/config", {
    method: "PUT",
    headers: AUTH,
    body: JSON.stringify({
      name1: "A", url1: "https://lite.tiktok.com/t/a",
      name2: "B", url2: "https://lite.tiktok.com/t/b",
      shareName: "C", shareUrl: "https://lite.tiktok.com/t/c",
      expectedConfigVersion: 1,
    }),
  }), env);
  assert.equal(legacyPut.status, 200);
  assert.equal((await readJson(legacyPut)).configVersion, 2);

  // The URL-runner version counter is independent from the legacy one.
  const runnerGet = await worker.fetch(new Request("https://worker.example/v1/url-runner"), env);
  assert.equal((await readJson(runnerGet)).configVersion, 1);
});

test("GET performs only reads: no migrations, updates or schema creation", async () => {
  const env = makeEnv();
  env.DB.exec = () => { throw Error("GET must not execute DDL"); };
  const original = env.DB.prepare.bind(env.DB);
  env.DB.prepare = (sql) => {
    assert.match(sql.trim(), /^SELECT/i);
    return original(sql);
  };
  for (const path of ["/healthz", "/v1/config", "/v1/url-runner"]) {
    assert.equal((await worker.fetch(new Request("https://worker.example" + path), env)).status, 200);
  }
});

test("runner choices round trip and reject malformed choices", async () => {
  const env = makeEnv();
  const options = {loopMode: "LOOP", waitFromForeground: false, startIndex: 1};
  assert.equal((await put(env, validPayload(options))).status, 200);
  const saved = await readJson(await worker.fetch(new Request("https://worker.example/v1/url-runner"), env));
  for (const [key, value] of Object.entries(options)) assert.equal(saved[key], value);
  for (const invalid of [{loopMode: "bad"}, {waitFromForeground: 1}, {startIndex: 120}, {commonSeconds: 0}]) {
    assert.equal((await put(env, validPayload(invalid))).status, 400);
  }
});
