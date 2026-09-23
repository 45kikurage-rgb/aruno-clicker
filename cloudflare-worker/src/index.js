const MAX_BODY_BYTES = 8 * 1024;
const MAX_URL_LENGTH = 2048;
const SCHEMA_VERSION = 1;
const ADMIN_KEY_PATTERN = /^[A-Za-z0-9]{8}$/;
const ALLOWED_TIKTOK_HOSTS = new Set([
  "tiktok.com", "www.tiktok.com", "lite.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com",
]);

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
  if (url.pathname === "/healthz" && request.method === "GET") {
    return jsonResponse({ ok: true, schemaVersion: SCHEMA_VERSION }, 200, request, env);
  }
  // The Android setting may point at either the Worker root or this explicit path.
  if (url.pathname === "/" || url.pathname === "/v1/config") {
    if (request.method === "GET") return getConfig(request, env);
    if (request.method === "PUT") return updateConfig(request, env);
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

async function getConfig(request, env) {
  requireDb(env);
  const row = await env.DB.prepare(
    "SELECT url1, url2, config_version, updated_at FROM remote_config WHERE id = 1",
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

  const allowedKeys = new Set(["url1", "url2", "expectedConfigVersion"]);
  const unexpectedKey = Object.keys(body).find((key) => !allowedKeys.has(key));
  if (unexpectedKey) return validationError(`Unexpected field: ${unexpectedKey}`, request, env);
  const url1 = validateStartupUrl(body.url1, "url1");
  if (!url1.ok) return validationError(url1.message, request, env);
  const url2 = validateStartupUrl(body.url2, "url2");
  if (!url2.ok) return validationError(url2.message, request, env);
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
       SET url1 = ?, url2 = ?, config_version = ?, updated_at = ?, update_id = ?
       WHERE id = 1 AND config_version = ?`,
    ).bind(url1.value, url2.value, nextVersion, updatedAt, updateId, currentVersion),
    env.DB.prepare(
      `INSERT INTO config_history (config_version, url1, url2, updated_at)
       SELECT config_version, url1, url2, updated_at
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
    url1: url1.value, url2: url2.value, configVersion: nextVersion, updatedAt, schemaVersion: SCHEMA_VERSION,
  }, 200, request, env, { "Cache-Control": "no-store, max-age=0" });
}

async function getHistory(request, env) {
  const authFailure = await authenticate(request, env);
  if (authFailure) return authFailure;
  requireDb(env);
  const result = await env.DB.prepare(
    "SELECT config_version, url1, url2, updated_at FROM config_history ORDER BY config_version DESC LIMIT 20",
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
  return { url1: row.url1, url2: row.url2, configVersion: Number(row.config_version),
    updatedAt: row.updated_at, schemaVersion: SCHEMA_VERSION };
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

export const testables = { ALLOWED_TIKTOK_HOSTS, validateStartupUrl };
