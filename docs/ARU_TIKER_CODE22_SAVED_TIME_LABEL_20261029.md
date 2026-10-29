# Aru-Tiker code22 — saved URL configuration's last-updated timestamp in the Android home screen

User screenshot (2026-10-29) shows `保存済み設定: URL 149件（版5）` after the website had already changed to an actual Japan-local update date.

## Change
- Android UI at `UrlRunnerHomeScreen` replaces only the numeric displayed `configVersion` with `UrlRunnerRevisionLabel.format(savedConfig.updatedAt)`.
- Format server-provided ISO 8601 timestamp in Japan's fixed `Asia/Tokyo` time zone, `M月d日H時mm分版`, e.g. 2026-10-28T22:35:00Z => `10月29日7時35分版`.
- If the cached timestamp is empty, unavailable or invalid, show `更新日時未取得` (never claim the device's current time or numeric version is the last save time). The time is *the central dataset's last save time*, not the date of APK build/install.
- No auto-download added: the existing `データダウンロード` is the only permitted way to refresh the central dataset on demand. `UrlRunnerJson` already saves and restores `updatedAt`; no storage format changes.
- Existing `configVersion` remains untouched for optimistic concurrency, so app and website retain compatible version-conflict behavior.
- Preserve the 1–5 URLs and wait seconds, 6+ slots, splash recovery, six-slot start, accessibility, swipe/overlay, signed package and local data.
- No central Cloudflare Worker, D1 schema, website, credentials or URLs modified.

## Tests and acceptance
- JVM unit tests for UTC->JST date rollover, clock timezone independence, zero-padded minute, malformed/missing timestamp and local JSON roundtrip.
- Existing Android regression suite and Worker suite must succeed, signed with original certificate SHA-256 `14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f`.
- VersionName 0.6.5 and versionCode 22, package `jp.aruno.clicker`, new APK distributed at existing `https://download.aruno-id.com/tiker`, leaving code21 and ARUNOMATIC APK routes intact.
- Software build, signing, publication and actual in-place device installation are different acceptance steps. Do not declare physical update or user screenshot verified without new device evidence.
