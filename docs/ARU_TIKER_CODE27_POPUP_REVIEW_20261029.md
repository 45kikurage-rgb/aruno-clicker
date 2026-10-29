# Aru-Tiker code27: URL1 preparation and popup close recovery

Requested behaviour: before each URL6+ open URL1, wait 3000 ms, then open the video. URL1–5 do not consume the per-video daily limit. A missing or failed preparation still stops with an explicit explanation; this existing user requirement is unchanged.

The user clarified on 2026-10-29 that all TikTok Lite popups should be closed, rather than limiting detection to coin stock. The shared detector accepts visible, enabled, small × / 閉じる / Close / Dismiss controls and known close resource IDs in the target application's windows. Text, description and view IDs are checked. Unlabelled unknown popup types receive no guessed coordinates. The existing coin-stock context allows an additional bounded card/CTA-relative fallback. CTA overlaps and large close nodes are rejected.

The URL path is isolated from legacy swipes and gift-to-home navigation. Only HOLDING may act. Stop, pause, lock, screen-off, service identity, run generation and video visit invalidate delayed actions. Both floating-controller implementations register their bounds; a covering controller moves and the next scan must confirm separation before a tap.

ACTION_CLICK or a completed gesture is not dismissal proof. A complete target-window scan must show no remaining popup/close candidate, with three checks at 250 ms. Missing/incomplete trees cannot count as success. An appearance has at most 3 presses with 900 ms cooldown and an 8-second deadline; each video has an additional 6-press cap. Animation does not reset counters. Stable absence for 1 second permits another appearance while preserving the video cap. Unknown or truncated trees suppress coordinate actions. The scan has a 4000-node / 8-window bound and records truncation.

Hold time excludes bounded popup recovery; it does not claim to measure actual playback or reward-gauge progress. Exhausted recovery displays and records the reason, then returns to the configured video wait. The next URL6+ still goes through URL1. User pause and stop remain available.

The first detected popup per video saves target-window nodes, parent relationships, type/layer, screen size, density, SDK/model, float bounds and selected candidate locally. Raw URL text is redacted. Only the latest 3 diagnostic files are retained. The HOME share button opens the Android chooser for the latest record; sharing is a user action. FileProvider is unexported and exposes only this diagnostic folder. No screenshot capture, network upload, new permission, force-stop or app-data clearing is added.

Tests cover non-coin close controls, separate windows, rejected CTA overlap, absent/foreign/incomplete trees, node budgets, amount variants, per-appearance and per-video caps, hold-time compensation, pause and stop, and existing URL1 timing/daily-limit regressions. Build/sign/publication evidence must be recorded after the corresponding runs complete. Physical popup dismissal, actual gauge restart and reward credit remain untested without the user's device.

Package jp.aruno.clicker; 0.6.10 / versionCode 27. Preserve the existing stable certificate and installed settings. Publish to the existing https://download.aruno-id.com/tiker route with a versioned APK URL; preserve old APK routes and all shared ARUNOMATIC routes. No configuration database changes are needed.
