# Aru-Tiker code24 — user accessibility stays ON while playback stops

User evidence: Android settings switch remained ON when automation stopped.
Prior implementation: ArunoAccessibilityService.onInterrupt() immediately called stopAutomation() even when service remained connected and the screen was active.

## Changes (Android only)
1. Android onInterrupt() is a feedback interruption notification, not proof of settings OFF/disconnection: record diagnostic event but keep active URL runner and countdown going.
2. Genuine onUnbind/onDestroy disconnections: mark service disconnected, stop automation, cancel pending URL/open callbacks, release WakeLock and do not automatically start when service reconnects. Avoid processing duplicate unbind/destroy as two separate stops and avoid overwriting the first stop reason.
3. Android ON is determined by exact ComponentName within Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES (not a substring matching another app). Actual service binding is tracked separately from the AccessibilityService instance lifecycle.
4. HOME panel: user-accessibility Settings ON/OFF; connected/disconnected; automation requested/running; last actual stop cause; expandable 15 latest local events with JST timestamps.
5. For stop causes, use AutomationRuntime's actual state transition and persist a bounded event log in this application's private SharedPreferences. Redact URLs before persistence. Record brief onInterrupt events no more than once per 5s. No central DB/API/log shipping, login, tokens, or secrets. UI is updated on package-scoped broadcast and Activity.onResume.
6. Explicit user stop/screen OFF/lock/real accessibility disconnect retain their safety behavior. No automatic accessibility enablement (Android owns that setting), no auto-restart of video after service reconnect.
7. All URL and splash-frequency functionality of code23 remains, including local 1–5 interval, 6+ start, countdown, failure recovery. No modifications to URL dataset or wait times, other apps, DPC, or Cloudflare DB.

## Verification and release
- package: jp.aruno.clicker; versionName 0.6.7, versionCode 24; same certificate SHA-256 14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f.
- Test Robolectric API28/35 exact enabled-component matching, stop vs interruption, no early URL open, genuine disconnect cancellation, history bounding/redaction, and previous Android/Worker suite.
- Original signed APK key and private immutable stage independent readback required. Current public download channel is code22; code23 source changes are part of code24 candidate but code23 was NOT publicly promoted (its inspect/test run failed). Therefore direct code24 promotion must preserve the actual production code22 and older routes exactly. Do not falsely require a code23 module to exist on live Worker.
- Publishing a verified code24 APK and testing an actual install on user devices are separate stages.
