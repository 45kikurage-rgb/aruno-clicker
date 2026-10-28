# Aru-Tiker code20 — recover failed video by splash before NEXT URL

User: 「動画停止となったらスプラッシュしてから次の動画URLを開くようにして」

## Verified implementation scope
- Recoverable failure means **the URL-open Intent could not launch** or **TikTok Lite foreground confirmation failed** (previously these ended the full URL run). App playback freezes/pause detection is NOT implemented; foreground confirmation is not proof a video is playing.
- Record the numbered failed URL as SKIPPED, not as played.
- After a 650 ms delay, launch TikTok Lite from its normal app entrypoint, resetting the **Activity task only** (no app data/credentials/permission wipe). This may show a splash/logo depending on device/TikTok version, but Android does not guarantee the logo is displayed.
- Wait 1800 ms for the normal launch to settle, then advance to the next nonblank numbered URL; the next ACTION_VIEW is sent into this newly launched task rather than CLEAR_TASK immediately discarding the splash.
- Blank slots are still skipped; positions 1–5 individual seconds and 6–200 common seconds preserved. Existing next-video countdown remains.
- Last failed URL with STOP finishes without unnecessary splash. In LOOP mode, one consecutive all-failure pass ends the run (no infinite reopen loop). Failure and skipped counts appear in the final summary.
- User STOP, screen-off, locked/keyguard and service/accessibility disconnect continue to stop normally, including during the splash delay. Scheduled callbacks are invalidated on stop; no app restart/next URL occurs.
- Existing website is sole URL manager; Cloudflare Worker / D1 / authentication / APK package/signing identity are unchanged.

## Tests
- Recoverable Intent failure: splash called once before next URL and no URL opens before 650+1800ms.
- Foreground-check failure: splash called and next URL attempted.
- Manual stop and device lock during pending splash/URL cancel all future opening.
- Last failed URL and all-URL failure in loop mode do not reopen forever.
- Original code19 task-reset, countdown, sparse positions and Android 9+ lifecycle preserved.

## Release state
Source branch: fix/tiker-auto-skip-failed-links-code20-20261028
Target package: jp.aruno.clicker, 0.6.3 / code20; original certificate SHA-256: 14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f.
Independent build/sign/release proof and physical device tests are separately required. Do not claim splash logo shown or actual video playback without real-device evidence.
