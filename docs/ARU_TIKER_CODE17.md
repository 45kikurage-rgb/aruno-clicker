# Aru-Tiker 0.6.0 / code17

2026-10-27: User authorized ChatGPT to correct the Muse delivery and publish.

- Normal edition only: `jp.aruno.clicker`. Existing certificate SHA-256:
  `14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f`.
- Signed application source: `c57fb5199e573693fa1e467f7995e8d95053d0a1`.
- APK SHA-256: `963bfb4c4908d69b5a1227c6cdb7db857ead5b715a9b0cda13b54fae1f21a8d8`.
- Bytes: 14751479. Full Android build succeeded; 77 Android unit/Robolectric tests
  passed with no failures, errors or skips. Lifecycle scenarios run at API28 and35.
- Worker tests: 17, including real SQLite migrations, GET/PUT, optimistic lock,
  URL120/121 boundary, history retention and unchanged legacy data. Two download
  wrapper tests verify one-edition links, APK validation and delegation.
- Source build run40491498086 completed its compile and test step successfully.
  Its packaging-verification step failed only because the parser expected the old
  apksigner label. The APK had already been signed and verified by apksigner.
  The parser now accepts current labels. Run40492015488 independently checked the
  exact signed bytes, both old/new certificate digests, manifest package/code and
  every JUnit result, then uploaded and re-fetched the immutable private R2 object.
  The complete staging/verification run succeeded. No Android rebuild was repeated
  solely for the label parser. No signing key was created or changed.

## Operation

Home has 動画スタート and データダウンロード. Management mode also shows 設定.
Long-press the application title to reach existing device/admin/network settings.
Existing admin credentials remain in the Android Keystore and are not embedded in
the distributed APK. Old encrypted credentials, legacy destination fields and
other saved settings are retained.

Paste URLs in the input field to append extracted HTTP(S) candidates automatically.
The 12 range buttons show 10 numbered slots each; order and duplicates are retained.
URLs are not normalized or stripped of valid terminal punctuation. Parentheses
outside a surrounding text wrapper are treated as text delimiters only when the
surrounding wrapper actually exists. Publishing/running accepts HTTPS TikTok hosts
from the existing six-host allowlist and reports unsupported entries by position.

Links1–5 each have their own1–3600 second setting; link6 onward shares one setting.
Initial values are10 seconds, stop after the last item, measure from confirmed
foreground and start at item1. End behavior, timer origin and starting number are
editable on the management screen and are included in the shared dataset.

Data download retrieves only config, never video media. It is explicit; opening the
app or pressing start does not fetch. A running session uses an immutable snapshot.
Failed/invalid responses preserve good cached data. Cache writes use AtomicFile;
UI edits survive range changes and Activity rotation through the ViewModel holder.

All public/manual/float start routes use the runner. Legacy gesture/×/ad/recents
source is preserved but inactive. Old scheduled alarms are canceled without
deleting their settings. No reconnect or service-enable event starts a stale run.
Repeated start is ignored; stop invalidates callbacks; screen OFF, lock, interrupt
or service disconnect stops the sequence. Opening/foreground confirmation does
not assert actual video playback or popup dismissal.

## Publication

The signed object is staged in the original private R2 bucket at a SHA-addressed
key. Central deployment uses `aruno-platform-ops/projects/tiker-release`, through
the existing central Cloudflare connection. The old app-repository token cannot
read D1 and is not escalated or copied. Dedicated D1 migration0004 adds runner
tables; the old schema2 API and three saved destinations remain intact. Schema
changes are explicit, never executed by HTTP reads.

Before publication, the operation exports D1, restores it to independent SQLite,
compares legacy rows and saves an encrypted rollback envelope on a private GitHub
backup branch. Only exact live Worker modules are wrapped; settings/bindings,
secrets, schedules, routes and ARUNOMATIC payloads remain preserved. Any failed
post-deploy check restores prior Worker code. Additive empty tables can remain;
production data is not automatically rewound.

Intended page: https://download.aruno-id.com/tiker
Intended APK: https://download.aruno-id.com/apk/aru-tiker-code17.apk
Publication proof is recorded separately in the platform repository.
Physical updating, native foreground behavior and TikTok playback: NOT RUN.
