const MAX_BODY_BYTES = 8 * 1024;
const MAX_URL_LENGTH = 2048;
const MAX_NAME_LENGTH = 80;
const SCHEMA_VERSION = 2;

// ---- URL runner (/v1/url-runner, schemaVersion 3) ----
// Added without changing the legacy /v1/config contract (schemaVersion 2).
// Body limit rationale: a valid body holds at most 120 URLs of up to 2048
// characters each (120 * 2048 = 245,760 bytes) plus per-URL JSON syntax
// (quotes/comma, ~3 bytes each), five individual seconds, one common
// seconds value and expectedConfigVersion. The worst valid case is about
// 246 KB, so the limit is set to 512 KiB (about 2.1x headroom).
const URL_RUNNER_SCHEMA_VERSION = 3;
const MAX_URL_RUNNER_BODY_BYTES = 512 * 1024;
const MAX_URL_RUNNER_URLS = 120;
const URL_RUNNER_INDIVIDUAL_COUNT = 5;
const MIN_RUNNER_SECONDS = 1;
const MAX_RUNNER_SECONDS = 3600;
const ADMIN_KEY_PATTERN = /^[A-Za-z0-9]{8}$/;
const ALLOWED_TIKTOK_HOSTS = new Set([
  "tiktok.com", "www.tiktok.com", "lite.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com",
]);
const schemaReadyByDatabase = new WeakMap();

export default {
  async fetch(request, env) {
    try { return await route(request, env); }
    catch (error) {
      console.error("Unhandled request error", error);
      return jsonResponse({ error: { code: "internal_error", message: "Internal server error" } }, 500, request, env);
    }
  },
};

async function route(request, env) {
  const url = new URL(request.url);
  if (request.method === "OPTIONS") return handleOptions(request, env);
  if (url.pathname === "/healthz" || url.pathname === "/" || url.pathname.startsWith("/v1/")) {
    requireDb(env);

  }
  if (url.pathname === "/healthz" && request.method === "GET") {
    return jsonResponse({ ok: true, schemaVersion: SCHEMA_VERSION }, 200, request, env);
  }
  // The Android setting may point at either the Worker root or this explicit path.
  if (url.pathname === "/" || url.pathname === "/v1/config") {
    if (request.method === "GET") return getConfig(request, env);
    if (request.method === "PUT") return updateConfig(request, env);
    return methodNotAllowed(request, env, "GET, PUT, OPTIONS");
  }
  if (url.pathname === "/v1/url-runner") {
    if (request.method === "GET") return getUrlRunnerConfig(request, env);
    if (request.method === "PUT") return updateUrlRunnerConfig(request, env);
    return methodNotAllowed(request, env, "GET, PUT, OPTIONS");
  }
  if (url.pathname === "/v1/history") {
    if (request.method !== "GET") return methodNotAllowed(request, env, "GET, OPTIONS");
    return getHistory(request, env);
  }
  if (url.pathname === "/v1/admin-key") {
    if (request.method === "GET") return getAdminKeyStatus(request, env);
    if (request.method === "PUT") return registerAdminKey(request, env);
    return methodNotAllowed(request, env, "GET, PUT, OPTIONS");
  }
  return jsonResponse({ error: { code: "not_found", message: "Not found" } }, 404, request, env);
}

async function ensureLatestSchema(db) {
  let ready = schemaReadyByDatabase.get(db);
  if (!ready) {
    ready = migrateLegacySchema(db).catch((error) => {
      schemaReadyByDatabase.delete(db);
      throw error;
    });
    schemaReadyByDatabase.set(db, ready);
  }
  await ready;
}

async function migrateLegacySchema(db) {
  await ensureColumns(db, "remote_config", [
    ["name1", "TEXT NOT NULL DEFAULT ''"],
    ["name2", "TEXT NOT NULL DEFAULT ''"],
    ["share_name", "TEXT NOT NULL DEFAULT ''"],
    ["share_url", "TEXT NOT NULL DEFAULT ''"],
  ]);
  await db.exec("UPDATE remote_config SET share_url = url1 WHERE share_url = ''");

  await ensureColumns(db, "config_history", [
    ["name1", "TEXT NOT NULL DEFAULT ''"],
    ["name2", "TEXT NOT NULL DEFAULT ''"],
    ["share_name", "TEXT NOT NULL DEFAULT ''"],
    ["share_url", "TEXT NOT NULL DEFAULT ''"],
  ]);
  await db.exec("UPDATE config_history SET share_url = url1 WHERE share_url = ''");

  await db.exec("CREATE TABLE IF NOT EXISTS d1_migrations (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
  await db.prepare("INSERT OR IGNORE INTO d1_migrations (name) VALUES (?)")
    .bind("0003_three_destinations.sql").run();
}

async function ensureColumns(db, table, definitions) {
  let existing = await readColumnNames(db, table);
  for (const [name, definition] of definitions) {
    if (existing.has(name)) continue;
    try {
      await db.exec(`ALTER TABLE ${table} ADD COLUMN ${name} ${definition}`);
    } catch (error) {
      existing = await readColumnNames(db, table);
      if (!existing.has(name)) throw error;
      continue;
    }
    existing.add(name);
  }
}

async function readColumnNames(db, table) {
  const result = await db.prepare(`PRAGMA table_info(${table})`).all();
  return new Set((result.results || []).map((column) => column.name));
}

async function getConfig(request, env) {
  requireDb(env);
  const row = await env.DB.prepare(
    `SELECT name1, url1, name2, url2, share_name, share_url,
            config_version, updated_at FROM remote_config WHERE id = 1`,
  ).first();
  if (!row) throw new Error("D1 migration has not been applied");
  return jsonResponse(toPublicConfig(row), 200, request, env, { "Cache-Control": "no-store, max-age=0" });
}

async function updateConfig(request, env) {
  const authFailure = await authenticate(request, env);
  if (authFailure) return authFailure;
  requireDb(env);

  const contentType = request.headers.get("Content-Type") || "";
  if (!contentType.toLowerCase().startsWith("application/json")) {
    return apiError(415, "unsupported_media_type", "Content-Type must be application/json", request, env);
  }
  const declaredLength = Number(request.headers.get("Content-Length") || "0");
  if (Number.isFinite(declaredLength) && declaredLength > MAX_BODY_BYTES) {
    return apiError(413, "payload_too_large", "Request body is too large", request, env);
  }
  const rawBody = await request.text();
  if (new TextEncoder().encode(rawBody).byteLength > MAX_BODY_BYTES) {
    return apiError(413, "payload_too_large", "Request body is too large", request, env);
  }

  let body;
  try { body = JSON.parse(rawBody); }
  catch { return apiError(400, "invalid_json", "Request body is not valid JSON", request, env); }
  if (!isPlainObject(body)) return validationError("Request body must be a JSON object", request, env);

  const allowedKeys = new Set([
    "name1", "url1", "name2", "url2", "shareName", "shareUrl", "expectedConfigVersion",
  ]);
  const unexpectedKey = Object.keys(body).find((key) => !allowedKeys.has(key));
  if (unexpectedKey) return validationError(`Unexpected field: ${unexpectedKey}`, request, env);
  const url1 = validateStartupUrl(body.url1, "url1");
  if (!url1.ok) return validationError(url1.message, request, env);
  const url2 = validateStartupUrl(body.url2, "url2");
  if (!url2.ok) return validationError(url2.message, request, env);
  const shareUrl = validateStartupUrl(body.shareUrl, "shareUrl");
  if (!shareUrl.ok) return validationError(shareUrl.message, request, env);
  const name1 = validateName(body.name1, "name1");
  if (!name1.ok) return validationError(name1.message, request, env);
  const name2 = validateName(body.name2, "name2");
  if (!name2.ok) return validationError(name2.message, request, env);
  const shareName = validateName(body.shareName, "shareName");
  if (!shareName.ok) return validationError(shareName.message, request, env);
  if (body.expectedConfigVersion !== undefined &&
      (!Number.isSafeInteger(body.expectedConfigVersion) || body.expectedConfigVersion < 1)) {
    return validationError("expectedConfigVersion must be a positive integer", request, env);
  }

  const current = await env.DB.prepare("SELECT config_version FROM remote_config WHERE id = 1").first();
  if (!current) throw new Error("D1 migration has not been applied");
  const currentVersion = Number(current.config_version);
  if (body.expectedConfigVersion !== undefined && body.expectedConfigVersion !== currentVersion) {
    return versionConflict(currentVersion, request, env);
  }

  const nextVersion = currentVersion + 1;
  const updatedAt = new Date().toISOString();
  const updateId = crypto.randomUUID();
  const results = await env.DB.batch([
    env.DB.prepare(
      `UPDATE remote_config
       SET name1 = ?, url1 = ?, name2 = ?, url2 = ?, share_name = ?, share_url = ?,
           config_version = ?, updated_at = ?, update_id = ?
       WHERE id = 1 AND config_version = ?`,
    ).bind(
      name1.value, url1.value, name2.value, url2.value, shareName.value, shareUrl.value,
      nextVersion, updatedAt, updateId, currentVersion,
    ),
    env.DB.prepare(
      `INSERT INTO config_history
         (config_version, name1, url1, name2, url2, share_name, share_url, updated_at)
       SELECT config_version, name1, url1, name2, url2, share_name, share_url, updated_at
       FROM remote_config WHERE id = 1 AND update_id = ?`,
    ).bind(updateId),
    env.DB.prepare(
      `DELETE FROM config_history WHERE config_version NOT IN (
         SELECT config_version FROM config_history ORDER BY config_version DESC LIMIT 20
       )`,
    ),
  ]);

  if (!results[0]?.success) throw new Error("D1 update failed");
  if (Number(results[0]?.meta?.changes || 0) !== 1) {
    const latest = await env.DB.prepare("SELECT config_version FROM remote_config WHERE id = 1").first();
    return versionConflict(Number(latest?.config_version || currentVersion), request, env);
  }
  return jsonResponse({
    name1: name1.value, url1: url1.value,
    name2: name2.value, url2: url2.value,
    shareName: shareName.value, shareUrl: shareUrl.value,
    configVersion: nextVersion, updatedAt, schemaVersion: SCHEMA_VERSION,
  }, 200, request, env, { "Cache-Control": "no-store, max-age=0" });
}

// ---- URL runner handlers ----
// Storage is a new single-row table (url_runner_config) plus a history
// table trimmed to the latest 20 versions, mirroring the legacy config
// flow. The legacy remote_config / config_history tables are untouched.

function toPublicUrlRunnerConfig(row) {
  if (!row) {
    return {
      urls: [],
      individualSeconds: [10, 10, 10, 10, 10],
      commonSeconds: 10,
      configVersion: 0,
      updatedAt: null,
      schemaVersion: URL_RUNNER_SCHEMA_VERSION,
      loopMode: "STOP", waitFromForeground: true, startIndex: 0,
    };
  }
  return {
    ...JSON.parse(row.options_json || '{"loopMode":"STOP","waitFromForeground":true,"startIndex":0}'),
    urls: JSON.parse(row.urls_json),
    individualSeconds: JSON.parse(row.individual_seconds_json),
    commonSeconds: Number(row.common_seconds),
    configVersion: Number(row.config_version),
    updatedAt: row.updated_at,
    schemaVersion: URL_RUNNER_SCHEMA_VERSION,
  };
}

async function readUrlRunnerRow(env) {
  return env.DB.prepare(
    `SELECT urls_json, individual_seconds_json, common_seconds,
            config_version, updated_at, update_id, options_json
     FROM url_runner_config WHERE id = 1`,
  ).first();
}

async function getUrlRunnerConfig(request, env) {
  requireDb(env);
  const row = await readUrlRunnerRow(env);
  return jsonResponse(toPublicUrlRunnerConfig(row), 200, request, env, { "Cache-Control": "no-store, max-age=0" });
}

// Validates one URL-runner payload. Every problem is collected so the
// response can name the offending positions instead of silently dropping
// entries. Host rules reuse validateStartupUrl (HTTPS + TikTok allowlist).
function validateUrlRunnerPayload(body) {
  const errors = [];
  if (!isPlainObject(body)) return { ok: false, errors: ["Request body must be a JSON object"] };
  const allowedKeys = new Set(["urls", "individualSeconds", "commonSeconds", "expectedConfigVersion", "schemaVersion", "loopMode", "waitFromForeground", "startIndex"]);
  for (const key of Object.keys(body)) {
    if (!allowedKeys.has(key)) errors.push(`Unexpected field: ${key}`);
  }
  if (body.schemaVersion !== undefined && body.schemaVersion !== URL_RUNNER_SCHEMA_VERSION) {
    errors.push(`schemaVersion must be ${URL_RUNNER_SCHEMA_VERSION}`);
  }

  let urls = null;
  if (!Array.isArray(body.urls)) {
    errors.push("urls must be an array");
  } else if (body.urls.length === 0) {
    errors.push("urls must contain at least one URL");
  } else if (body.urls.length > MAX_URL_RUNNER_URLS) {
    errors.push(`urls must contain at most ${MAX_URL_RUNNER_URLS} entries (received ${body.urls.length})`);
  } else {
    urls = [];
    body.urls.forEach((value, index) => {
      const result = validateStartupUrl(value, `urls[${index}]`);
      if (!result.ok) errors.push(result.message);
      else urls.push(result.value);
    });
    if (errors.length > 0) urls = null;
  }

  let individualSeconds = null;
  if (!Array.isArray(body.individualSeconds) ||
      body.individualSeconds.length !== URL_RUNNER_INDIVIDUAL_COUNT) {
    errors.push(`individualSeconds must contain exactly ${URL_RUNNER_INDIVIDUAL_COUNT} values`);
  } else if (!body.individualSeconds.every(
    (value) => Number.isSafeInteger(value) && value >= MIN_RUNNER_SECONDS && value <= MAX_RUNNER_SECONDS,
  )) {
    errors.push(`individualSeconds values must be integers between ${MIN_RUNNER_SECONDS} and ${MAX_RUNNER_SECONDS}`);
  } else {
    individualSeconds = [...body.individualSeconds];
  }

  let commonSeconds = null;
  if (!Number.isSafeInteger(body.commonSeconds) ||
      body.commonSeconds < MIN_RUNNER_SECONDS || body.commonSeconds > MAX_RUNNER_SECONDS) {
    errors.push(`commonSeconds must be an integer between ${MIN_RUNNER_SECONDS} and ${MAX_RUNNER_SECONDS}`);
  } else {
    commonSeconds = body.commonSeconds;
  }

  if (body.expectedConfigVersion !== undefined &&
      (!Number.isSafeInteger(body.expectedConfigVersion) || body.expectedConfigVersion < 0)) {
    errors.push("expectedConfigVersion must be a non-negative integer");
  }

  const loopMode = body.loopMode ?? "STOP";
  const waitFromForeground = body.waitFromForeground ?? true;
  const startIndex = body.startIndex ?? 0;
  if (!["STOP", "LOOP"].includes(loopMode)) errors.push("loopMode must be STOP or LOOP");
  if (typeof waitFromForeground !== "boolean") errors.push("waitFromForeground must be boolean");
  if (!Number.isSafeInteger(startIndex) || startIndex < 0 || startIndex >= (body.urls?.length || 1)) errors.push("startIndex is outside urls");
  if (errors.length > 0) return { ok: false, errors };
  return {
    ok: true,
    value: {
      urls,
      individualSeconds,
      commonSeconds, loopMode, waitFromForeground, startIndex,
      expectedConfigVersion: body.expectedConfigVersion,
    },
  };
}

async function updateUrlRunnerConfig(request, env) {
  const authFailure = await authenticate(request, env);
  if (authFailure) return authFailure;
  requireDb(env);

  const contentType = request.headers.get("Content-Type") || "";
  if (!contentType.toLowerCase().startsWith("application/json")) {
    return apiError(415, "unsupported_media_type", "Content-Type must be application/json", request, env);
  }
  const declaredLength = Number(request.headers.get("Content-Length") || "0");
  if (Number.isFinite(declaredLength) && declaredLength > MAX_URL_RUNNER_BODY_BYTES) {
    return apiError(413, "payload_too_large", "Request body is too large", request, env);
  }
  const rawBody = await request.text();
  if (new TextEncoder().encode(rawBody).byteLength > MAX_URL_RUNNER_BODY_BYTES) {
    return apiError(413, "payload_too_large", "Request body is too large", request, env);
  }

  let body;
  try { body = JSON.parse(rawBody); }
  catch { return apiError(400, "invalid_json", "Request body is not valid JSON", request, env); }

  const validation = validateUrlRunnerPayload(body);
  if (!validation.ok) return validationError(validation.errors.join("; "), request, env);
  const value = validation.value;

  const current = await readUrlRunnerRow(env);
  const currentVersion = current ? Number(current.config_version) : 0;
  if (value.expectedConfigVersion !== undefined && value.expectedConfigVersion !== currentVersion) {
    return versionConflict(currentVersion, request, env);
  }

  const nextVersion = currentVersion + 1;
  const updatedAt = new Date().toISOString();
  const updateId = crypto.randomUUID();
  const urlsJson = JSON.stringify(value.urls);
  const secondsJson = JSON.stringify(value.individualSeconds);
  const results = await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO url_runner_config
         (id, urls_json, individual_seconds_json, common_seconds, config_version, updated_at, update_id, options_json)
       VALUES (1, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         urls_json = excluded.urls_json,
         individual_seconds_json = excluded.individual_seconds_json,
         common_seconds = excluded.common_seconds,
         config_version = excluded.config_version,
         updated_at = excluded.updated_at,
         update_id = excluded.update_id,
         options_json = excluded.options_json
       WHERE url_runner_config.config_version = ?`,
    ).bind(urlsJson, secondsJson, value.commonSeconds, nextVersion, updatedAt, updateId, JSON.stringify({loopMode:value.loopMode,waitFromForeground:value.waitFromForeground,startIndex:value.startIndex}), currentVersion),
    env.DB.prepare(
      `INSERT INTO url_runner_history
         (config_version, urls_json, individual_seconds_json, common_seconds, updated_at, options_json)
       SELECT config_version, urls_json, individual_seconds_json, common_seconds, updated_at, options_json
       FROM url_runner_config WHERE id = 1 AND update_id = ?`,
    ).bind(updateId),
    env.DB.prepare(
      `DELETE FROM url_runner_history WHERE config_version NOT IN (
         SELECT config_version FROM url_runner_history ORDER BY config_version DESC LIMIT 20
       )`,
    ),
  ]);

  if (!results[0]?.success) throw new Error("D1 update failed");
  if (Number(results[0]?.meta?.changes || 0) !== 1) {
    const latest = await readUrlRunnerRow(env);
    return versionConflict(latest ? Number(latest.config_version) : currentVersion, request, env);
  }
  return jsonResponse({
    urls: value.urls,
    individualSeconds: value.individualSeconds,
    commonSeconds: value.commonSeconds,
    loopMode: value.loopMode, waitFromForeground: value.waitFromForeground, startIndex: value.startIndex,
    configVersion: nextVersion,
    updatedAt,
    schemaVersion: URL_RUNNER_SCHEMA_VERSION,
  }, 200, request, env, { "Cache-Control": "no-store, max-age=0" });
}

async function getHistory(request, env) {
  const authFailure = await authenticate(request, env);
  if (authFailure) return authFailure;
  requireDb(env);
  const result = await env.DB.prepare(
    `SELECT config_version, name1, url1, name2, url2, share_name, share_url, updated_at
     FROM config_history ORDER BY config_version DESC LIMIT 20`,
  ).all();
  return jsonResponse({
    history: (result.results || []).map(toPublicConfig), schemaVersion: SCHEMA_VERSION,
  }, 200, request, env, { "Cache-Control": "no-store, max-age=0" });
}

async function getAdminKeyStatus(request, env) {
  requireDb(env);
  const current = await readStoredAdminKey(env);
  return jsonResponse({ configured: Boolean(current), schemaVersion: SCHEMA_VERSION }, 200, request, env,
    { "Cache-Control": "no-store, max-age=0" });
}

async function registerAdminKey(request, env) {
  requireDb(env);
  const contentType = request.headers.get("Content-Type") || "";
  if (!contentType.toLowerCase().startsWith("application/json")) {
    return apiError(415, "unsupported_media_type", "Content-Type must be application/json", request, env);
  }
  const rawBody = await request.text();
  if (new TextEncoder().encode(rawBody).byteLength > MAX_BODY_BYTES) {
    return apiError(413, "payload_too_large", "Request body is too large", request, env);
  }
  let body;
  try { body = JSON.parse(rawBody); }
  catch { return apiError(400, "invalid_json", "Request body is not valid JSON", request, env); }
  if (!isPlainObject(body) || Object.keys(body).some((key) => key !== "newKey")) {
    return validationError("Only newKey may be supplied", request, env);
  }
  if (typeof body.newKey !== "string" || !ADMIN_KEY_PATTERN.test(body.newKey)) {
    return validationError("newKey must be exactly 8 ASCII letters or digits", request, env);
  }
  if (await readStoredAdminKey(env)) {
    return apiError(409, "admin_key_already_configured", "Administrator key is already configured", request, env);
  }
  const keyHash = await sha256Hex(body.newKey);
  const updatedAt = new Date().toISOString();
  const result = await env.DB.prepare(
    `INSERT OR IGNORE INTO admin_settings (id, key_hash, updated_at) VALUES (1, ?, ?)`,
  ).bind(keyHash, updatedAt).run();
  if (!result?.success) throw new Error("Administrator key registration failed");
  if (Number(result?.meta?.changes || 0) !== 1) {
    return apiError(409, "admin_key_already_configured", "Administrator key is already configured", request, env);
  }
  return jsonResponse({ configured: true, updatedAt, schemaVersion: SCHEMA_VERSION }, 201, request, env,
    { "Cache-Control": "no-store, max-age=0" });
}

function toPublicConfig(row) {
  return {
    name1: row.name1 || "", url1: row.url1,
    name2: row.name2 || "", url2: row.url2,
    shareName: row.share_name || "", shareUrl: row.share_url || row.url1,
    configVersion: Number(row.config_version), updatedAt: row.updated_at, schemaVersion: SCHEMA_VERSION,
  };
}

function validateName(value, fieldName) {
  if (typeof value !== "string") return { ok: false, message: `${fieldName} must be a string` };
  const normalized = value.trim();
  if (normalized.length > MAX_NAME_LENGTH) return { ok: false, message: `${fieldName} is too long` };
  return { ok: true, value: normalized };
}

function validateStartupUrl(value, fieldName) {
  if (typeof value !== "string" || value.length === 0) return { ok: false, message: `${fieldName} is required` };
  if (value.length > MAX_URL_LENGTH) return { ok: false, message: `${fieldName} is too long` };
  let parsed;
  try { parsed = new URL(value); }
  catch { return { ok: false, message: `${fieldName} must be a valid URL` }; }
  if (parsed.protocol !== "https:") return { ok: false, message: `${fieldName} must use HTTPS` };
  if (parsed.username || parsed.password) return { ok: false, message: `${fieldName} must not contain credentials` };
  if (!ALLOWED_TIKTOK_HOSTS.has(parsed.hostname.toLowerCase())) {
    return { ok: false, message: `${fieldName} must use an allowed TikTok host` };
  }
  return { ok: true, value: parsed.toString() };
}

async function authenticate(request, env) {
  const suppliedToken = readBearerToken(request.headers.get("Authorization"));
  const stored = await readStoredAdminKey(env);
  const authenticated = stored
    ? Boolean(suppliedToken && await secureEqual(await sha256Hex(suppliedToken), stored.key_hash))
    : Boolean(env.ADMIN_TOKEN && suppliedToken && await secureEqual(suppliedToken, env.ADMIN_TOKEN));
  if (!stored && !env.ADMIN_TOKEN) {
    return apiError(503, "server_not_configured", "Administrator key is not configured", request, env);
  }
  if (!authenticated) {
    return jsonResponse({ error: { code: "unauthorized", message: "Invalid administrator token" } },
      401, request, env, { "WWW-Authenticate": "Bearer" });
  }
  return null;
}

async function readStoredAdminKey(env) {
  return env.DB.prepare("SELECT key_hash, updated_at FROM admin_settings WHERE id = 1").first();
}

async function sha256Hex(value) {
  const bytes = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function readBearerToken(authorization) {
  if (!authorization) return null;
  const match = /^Bearer[ \t]+([^\s]+)$/i.exec(authorization);
  return match ? match[1] : null;
}

async function secureEqual(left, right) {
  const encoder = new TextEncoder();
  const [leftHash, rightHash] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(left)),
    crypto.subtle.digest("SHA-256", encoder.encode(right)),
  ]);
  const leftBytes = new Uint8Array(leftHash);
  const rightBytes = new Uint8Array(rightHash);
  let difference = 0;
  for (let index = 0; index < leftBytes.length; index += 1) difference |= leftBytes[index] ^ rightBytes[index];
  return difference === 0;
}

function requireDb(env) {
  if (!env.DB || typeof env.DB.prepare !== "function") throw new Error("D1 DB binding is missing");
}

function handleOptions(request, env) {
  const origin = request.headers.get("Origin");
  if (!isAllowedOrigin(origin, env)) return new Response(null, { status: 403 });
  return new Response(null, { status: 204, headers: {
    "Access-Control-Allow-Origin": origin, "Access-Control-Allow-Methods": "GET, PUT, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type", "Access-Control-Max-Age": "600", Vary: "Origin",
  } });
}

function isAllowedOrigin(origin, env) {
  return Boolean(origin && env.ALLOWED_ADMIN_ORIGIN && origin === env.ALLOWED_ADMIN_ORIGIN);
}

function jsonResponse(payload, status, request, env, additionalHeaders = {}) {
  const headers = new Headers({ "Content-Type": "application/json; charset=utf-8",
    "X-Content-Type-Options": "nosniff", ...additionalHeaders });
  const origin = request.headers.get("Origin");
  if (isAllowedOrigin(origin, env)) { headers.set("Access-Control-Allow-Origin", origin); headers.append("Vary", "Origin"); }
  return new Response(JSON.stringify(payload), { status, headers });
}

function apiError(status, code, message, request, env) {
  return jsonResponse({ error: { code, message } }, status, request, env);
}
function validationError(message, request, env) { return apiError(400, "validation_error", message, request, env); }
function versionConflict(currentConfigVersion, request, env) {
  return jsonResponse({ error: { code: "version_conflict", message: "The configuration was changed after it was loaded",
    currentConfigVersion } }, 409, request, env);
}
function methodNotAllowed(request, env, allow) {
  return jsonResponse({ error: { code: "method_not_allowed", message: "Method not allowed" } },
    405, request, env, { Allow: allow });
}
function isPlainObject(value) { return Boolean(value && typeof value === "object" && !Array.isArray(value)); }

export const testables = {
  ALLOWED_TIKTOK_HOSTS, validateName, validateStartupUrl, ensureLatestSchema,
  validateUrlRunnerPayload,
  MAX_URL_RUNNER_BODY_BYTES, MAX_URL_RUNNER_URLS,
};
