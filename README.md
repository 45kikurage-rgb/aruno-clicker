# ARUNO CLICKER

## Aru-Tiker 0.6.14 / code31 (2026-11-01)

Published: https://download.aruno-id.com/tiker / APK: https://download.aruno-id.com/apk/aru-tiker-code31.apk . Adds per-device recent-card cleanup ON/OFF (default OFF) and horizontal/vertical selection. Vertical two-front-card histories are re-read after each leftward swipe; horizontal cards swipe upward. The next URL opens only after explicit empty-history confirmation. Unknown or stuck cards stop the run. Code30 URL migration, counters, popup references and settings are retained.

Final Android regressions: 272 passed. Existing signer restored independently from both private repositories; API26/28/35 signatures and public APK bytes verified. Actual phone update, two-card gestures and playback remain unverified. See [code31 release record](docs/ARU_TIKER_CODE31_RECENTS_20261101.md). Shared distribution/signing sources and receipts are in [aruno-platform-ops code31](https://github.com/45kikurage-rgb/aruno-platform-ops/tree/feature/tiker-code31-20261101).


## Aru-Tiker 0.6.11 / code28 (2026-10-29)

Published: https://download.aruno-id.com/tiker . Removes automatic URL1 insertion and the three-second preparation delay before URL6+. Sixth-position starts open URL6 directly; popup handling, settings, daily counts, waits, pause/stop and optional splash are retained. Final 230 tests passed; existing signer and public APK bytes verified. Physical operation remains unverified. See [code28 record](docs/ARU_TIKER_CODE28_DIRECT_URL_20261029.md).

## Aru-Tiker 0.6.10 / code27 (2026-10-29)

Published: https://download.aruno-id.com/tiker . Adds verified ×/閉じる/Close popup closing beyond coin stock, bounded retries, overlay avoidance and local diagnostics. URL6+ uses URL1 → 3 seconds → target; URL1–5 are outside the daily limit. Final software tests: 230 passed. Existing certificate preserved; public signed APK SHA-256 cacd51c9efd688054d8b26843d147beea72e12e1cc1a99d03b00a864100cf5ae. Physical popup closing, gauge restart and reward credit remain untested. See [design and limits](docs/ARU_TIKER_CODE27_POPUP_REVIEW_20261029.md). Publication run: https://github.com/45kikurage-rgb/aruno-platform-ops/actions/runs/40859737981 .

Earlier release history follows.


## Aru-Tiker 0.6.4 / code21 (2026-10-28)

Published update: https://download.aruno-id.com/tiker . Adds initial accessibility settings with ON/OFF and one-run start from URL6 in HOME and both floating controllers. Existing code20 recovery remains. All 111 Android tests, stable certificate, signed package metadata and public APK byte checks passed; physical-device playback is not verified. See [release record](docs/ARU_TIKER_CODE21_START_BUTTONS_20261028.md).

Android app and Cloudflare remote configuration service.

## PWA icon assets

The repository root contains the PWA icon set using the same four-file structure as THE FOOL QUEST:

- `icon-any.png` / `icon-any-192.png`: transparent normal icons
- `icon-maskable.png` / `icon-maskable-192.png`: white-background maskable icons
- `manifest.webmanifest`: white launch background (`#ffffff`)

Asset cache version: `20260923-white-splash-v1`.
