# Aru-Tiker URL list slots 1–200 (candidate / not published)

Date: 2026-10-28. Scope: existing Android app and its dedicated /v1/url-runner endpoint only.

## User-requested behavior

- Slots 001–005 are individually entered URLs. They are never overwritten by bulk paste.
- Bulk paste APPEND/REPLACE affects only slots 006–200 (195 bulk slots).
- APPEND continues after the last populated position >= 006.
- REPLACE clears slots 006–200 only; first five are unchanged.
- Input order and duplicates are preserved. Over-capacity entries are reported instead of silently dropped.
- Empty positions are retained as empty strings in the numbered list and are skipped without opening a URL or waiting.
- Up to 200 total numbered positions; at least one valid HTTPS TikTok URL is required.
- Slots 001–005 retain their individual wait settings; slot 006 onward uses the existing common wait setting (user can set 30s).
- The default end behavior STOP and existing foreground-check setting are unchanged.

## Compatibility and sequencing

- Keep existing package, app signing certificate, all stored app settings and existing DB rows.
- Preserve schemaVersion 3, endpoint, authorization and optimistic locking; no DB migration needed.
- The server must accept both old <=120 and new <=200 numbered lists.
- Old code17 Android still rejects >120 and blank interior positions: update server, then update and verify Android clients, and *only then* publish a 121–200 or sparse dataset. Do not push new datasets to old clients.
- No ARUNOMATIC, Ledger or Align contract is changed by this patch. Incoming sharing/aggregation from ARUNOMATIC is separate scope.
- Do not deploy or sign before CI and safety checks: JS tests, Android tests/compile, key restoration/certificate match, backup/rollback readiness, public APK hash and physical device testing.

## Explicitly not claimed

Current URL runner only confirms TikTok Lite foreground; it does not reliably dismiss shared-link overlays, verify video playback, check reward-gauge changes or wait after a verified popup dismissal. Those are separate changes requiring device evidence.

## Test expectations

1. Bulk paste 195 URLs into slots 006–200, keeping slots 001–005; 196th is reported as excess.
2. Server accepts 200, rejects 201; accepts middle blanks but rejects a list containing only blanks.
3. URL runner skips blanks without a timer, preserves original slot numbering and stops safely at the end.
4. Existing 120 URLs remain valid, server config history and legacy /v1/config unchanged.
5. Starting on any blank slot skips forward; no fabricated video-playback success is reported.

Status: source patch and tests committed on feature branch; full compilation, automated tests, deployment and real-device acceptance are NOT yet evidenced.
