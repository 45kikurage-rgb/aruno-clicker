// aruno-home-layout: site-driven HOME layout for Aruno-Home/T.
//
// Serves the layout JSON the app fetches on "画面更新"
// (GET /v1/home-layout, public read) and the smartphone editor page
// (GET /home). Editing requires the shared ARUNO admin key (same
// admin_settings table / key as the aruno-clicker-config Worker, in the
// same D1 database). After one login the editor stays authenticated on
// that device via a long-lived cookie (decided 2026-11-19).
//
// This Worker is separate from aruno-clicker-config on purpose: the
// deployed config Worker carries /tiker/* pages that are not in the
// GitHub repo, so it must not be redeployed from the repo. This Worker
// only adds its own tables (home_layout_config / home_layout_history).

import { validateLayout, LAYOUT_VERSION } from "./layout.js";
import { editorHtml, loginHtml } from "./editor.js";

const MAX_BODY_BYTES = 64 * 1024;
const COOKIE_NAME = "home_admin";
const COOKIE_MAX_AGE = 400 * 24 * 60 * 60; // ~400 days: authenticate once, stays authenticated

export default {
  async fetch(request, env) {
    try { return await route(request, env); }
    catch (error) {
      console.error("Unhandled request error", error);
      return jsonResponse({ error: { code: "internal_error", message: "Internal server error" } }, 500);
    }
  },
};

async function route(request, env) {
  const url = new URL(request.url);
  if (url.pathname === "/healthz" && request.method === "GET") {
    return jsonResponse({ ok: true, layoutVersion: LAYOUT_VERSION }, 200);
  }
  if (url.pathname === "/v1/home-layout") {
    if (request.method === "GET") return getLayout(request, env);
    if (request.method === "PUT") return updateLayout(request, env);
    return methodNotAllowed("GET, PUT");
  }
  if (url.pathname === "/home" && request.method === "GET") {
    const authed = await isAuthenticated(request, env);
    return htmlResponse(authed ? editorHtml() : loginHtml(""));
  }
  if (url.pathname === "/home/login" && request.method === "POST") return login(request, env);
  if (url.pathname === "/home/logout" && request.method === "POST") {
    return htmlResponse(loginHtml("ログアウトしました"), {
      "Set-Cookie": `${COOKIE_NAME}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax`,
    });
  }
  return jsonResponse({ error: { code: "not_found", message: "Not found" } }, 404);
}

async function getLayout(request, env) {
  requireDb(env);
  const row = await readLayoutRow(env);
  if (!row) {
    return jsonResponse({ error: { code: "not_initialized", message: "Layout is not initialized yet" } }, 404);
  }
  return jsonResponse(row.layout, 200, { "Cache-Control": "no-store, max-age=0" });
}

async function updateLayout(request, env) {
  requireDb(env);
  if (!(await isAuthenticated(request, env))) {
    return jsonResponse({ error: { code: "unauthorized", message: "Admin authentication is required" } }, 401);
  }
  const contentType = request.headers.get("Content-Type") || "";
  if (!contentType.toLowerCase().startsWith("application/json")) {
    return jsonResponse({ error: { code: "unsupported_media_type", message: "Content-Type must be application/json" } }, 415);
  }
  const raw = await request.text();
  if (raw.length > MAX_BODY_BYTES) {
    return jsonResponse({ error: { code: "body_too_large", message: "Request body is too large" } }, 413);
  }
  let body;
  try { body = JSON.parse(raw); }
  catch { return jsonResponse({ error: { code: "invalid_json", message: "Request body is not valid JSON" } }, 400); }

  const expectedRevision = body ? body.expectedRevision : undefined;
  if (!Number.isInteger(expectedRevision) || expectedRevision < 0) {
    return jsonResponse({ error: { code: "validation_error", message: "expectedRevision must be a non-negative integer" } }, 400);
  }
  const validated = validateLayout(body.layout ?? body);
  if (!validated.ok) {
    return jsonResponse({ error: { code: "validation_error", message: validated.error } }, 400);
  }

  const current = await readLayoutRow(env);
  const currentRevision = current ? current.layout.revision : 0;
  if (expectedRevision !== currentRevision) {
    return jsonResponse({
      error: { code: "version_conflict", message: "Layout was updated elsewhere", currentRevision },
    }, 409);
  }

  const nextRevision = currentRevision + 1;
  const updatedAt = new Date().toISOString();
  const stored = { v: LAYOUT_VERSION, revision: nextRevision, updatedAt, slots: validated.value.slots };
  const layoutJson = JSON.stringify(stored);
  const updateId = `rev-${nextRevision}`;

  const statements = current
    ? [
        env.DB.prepare(
          `UPDATE home_layout_config SET layout_json = ?, revision = ?, updated_at = ?, update_id = ?
           WHERE id = 1 AND revision = ?`,
        ).bind(layoutJson, nextRevision, updatedAt, updateId, currentRevision),
        env.DB.prepare(
          `INSERT INTO home_layout_history (revision, layout_json, updated_at) VALUES (?, ?, ?)`,
        ).bind(nextRevision, layoutJson, updatedAt),
      ]
    : [
        env.DB.prepare(
          `INSERT INTO home_layout_config (id, layout_json, revision, updated_at, update_id) VALUES (1, ?, ?, ?, ?)`,
        ).bind(layoutJson, nextRevision, updatedAt, updateId),
        env.DB.prepare(
          `INSERT INTO home_layout_history (revision, layout_json, updated_at) VALUES (?, ?, ?)`,
        ).bind(nextRevision, layoutJson, updatedAt),
      ];
  const results = await env.DB.batch(statements);
  const changes = results[0] && results[0].meta ? results[0].meta.changes : 0;
  if (current && changes !== 1) {
    const now = await readLayoutRow(env);
    return jsonResponse({
      error: { code: "version_conflict", message: "Layout was updated elsewhere", currentRevision: now ? now.layout.revision : currentRevision },
    }, 409);
  }
  return jsonResponse({ ok: true, revision: nextRevision, updatedAt }, 200);
}

async function readLayoutRow(env) {
  const row = await env.DB.prepare(
    "SELECT layout_json, revision, updated_at FROM home_layout_config WHERE id = 1",
  ).first();
  if (!row) return null;
  return { layout: JSON.parse(row.layout_json), revision: row.revision, updatedAt: row.updated_at };
}

// ---- Admin authentication -------------------------------------------------
// The admin key is the one already registered for the ARUNO config system
// (admin_settings.key_hash holds its SHA-256). A Worker secret ADMIN_TOKEN
// is also accepted when set. The editor cookie carries a token derived
// from the stored key hash, so it stays valid across restarts but is
// invalidated when the admin key changes.

async function readStoredAdminKey(env) {
  if (!env.DB) return null;
  try {
    return await env.DB.prepare("SELECT key_hash FROM admin_settings WHERE id = 1").first();
  } catch {
    return null;
  }
}

async function keyMatches(rawKey, env) {
  if (typeof rawKey !== "string" || rawKey.length === 0) return false;
  const stored = await readStoredAdminKey(env);
  if (stored && stored.key_hash) {
    return secureEqual(await sha256Hex(rawKey), stored.key_hash);
  }
  if (env.ADMIN_TOKEN) return secureEqual(rawKey, env.ADMIN_TOKEN);
  return false;
}

async function cookieToken(env) {
  const stored = await readStoredAdminKey(env);
  const base = stored && stored.key_hash ? stored.key_hash : (env.ADMIN_TOKEN || "");
  if (!base) return null;
  return sha256Hex(`${base}:aruno-home-layout:v1`);
}

async function isAuthenticated(request, env) {
  const authorization = request.headers.get("Authorization") || "";
  if (authorization.startsWith("Bearer ")) {
    if (await keyMatches(authorization.slice(7).trim(), env)) return true;
  }
  const token = await cookieToken(env);
  if (!token) return false;
  const cookie = request.headers.get("Cookie") || "";
  for (const part of cookie.split(";")) {
    const [name, ...rest] = part.trim().split("=");
    if (name === COOKIE_NAME) return secureEqual(rest.join("="), token);
  }
  return false;
}

async function login(request, env) {
  const contentType = (request.headers.get("Content-Type") || "").toLowerCase();
  let key = "";
  if (contentType.startsWith("application/json")) {
    try { key = (await request.json()).key || ""; } catch { key = ""; }
  } else {
    const form = await request.formData();
    key = String(form.get("key") || "");
  }
  if (!(await keyMatches(key, env))) {
    return htmlResponse(loginHtml("キーが違います"), { status: 401 });
  }
  const token = await cookieToken(env);
  return new Response("", {
    status: 303,
    headers: {
      Location: "/home",
      "Set-Cookie": `${COOKIE_NAME}=${token}; Path=/; Max-Age=${COOKIE_MAX_AGE}; HttpOnly; Secure; SameSite=Lax`,
    },
  });
}

// ---- Helpers ---------------------------------------------------------------

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function secureEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string" || left.length !== right.length) return false;
  let diff = 0;
  for (let i = 0; i < left.length; i++) diff |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return diff === 0;
}

function requireDb(env) {
  if (!env.DB) throw new Error("D1 binding DB is not configured");
}

function jsonResponse(payload, status, additionalHeaders = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...additionalHeaders },
  });
}

function htmlResponse(html, options = {}) {
  const { status = 200, ...headers } = options;
  return new Response(html, {
    status,
    headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", ...headers },
  });
}

function methodNotAllowed(allow) {
  return jsonResponse({ error: { code: "method_not_allowed", message: "Method not allowed" } }, 405, { Allow: allow });
}

export const testables = { validateLayout };
