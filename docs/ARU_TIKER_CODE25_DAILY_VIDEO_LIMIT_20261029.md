# Aru-Tiker code25: device-local daily video attempt limit

User approved a daily maximum of 2 or 3 per video and explicitly included interrupted attempts.

- Home exposes 2/3 choices for every device; default 2. Save immediately on selection. Disabled during a run; fixed limit for the next run.
- Count once immediately after TikTok Lite foreground confirmation, before the configured hold. Manual stop, screen-off, lock, disconnection, or process death during the hold retain that count. Failures before foreground confirmation do not count. No completion event is needed.
- These are recorded opening attempts. Playback and reward credit are not verified.
- Synchronous private SharedPreferences persist daily hashed URL identity counts. No central DB/API change, no raw URLs stored, no setting/data reset. Dates use Asia/Tokyo. A new day allows new attempts; no background auto-start.
- Counts follow URL identity across slot reorder and remote configuration download. Known full TikTok video IDs and identical short-link tokens are normalized. Different short-link tokens pointing to the same video cannot be deduplicated offline and remain separate; no network resolver is added.
- Preserve configured individual/common waits (including the user's 90 seconds), order, start selection, STOP/LOOP mode, splash interval and stop/disconnection behavior. Skip capped slots. LOOP finishes when no eligible URL remains. STOP still stops at the list end; press start for another pass if desired.
- No gauge detection, force-stop, cache/data clearing, or claim of resetting TikTok reward eligibility.

Source base: main 4f14570fe6ad83ea563d8172ea268cc919b68016. Candidate package jp.aruno.clicker, versionName0.6.8/versionCode25. Existing signer pin is 14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f.

Verification: existing runner tests plus interrupted/restarted limits, 2/3 limits, sparse/duplicate/reordered URLs, capped loop termination, no extra splash, failed open/foreground, stale callbacks, persistence failure, Japan midnight, and actual SharedPreferences persistence on API28/35. CI builds an unsigned release candidate without signing secrets. Signing, public deployment, install/data retention and reward behavior are separate acceptance steps.

Signing gate: the existing code24 release record and certificate were read. The common signing-registry currently has no jp.aruno.clicker entry, and independent durable-key restoration evidence has not been found. Do not replace the existing key or call the old raw-Secret signing workflow as a substitute for required preservation. Complete the existing-key preservation/independent restoration check before signing or distribution.
