# Aru-Tiker code19: subsequent deep-link navigation and floating timer

2026-10-28 screenshot: overlay says "4番目のURLを開きました" but user reports that the actual video stayed at the third URL. The existing implementation accepted TikTok Lite in the foreground as success and used FLAG_ACTIVITY_CLEAR_TOP for every ACTION_VIEW. This can reuse a previously opened video Activity, without verifying a new video.

## Change
- Keep the first URL's original ACTION_VIEW + NEW_TASK/CLEAR_TOP.
- After the first successful URL-opening request, set NEW_TASK/CLEAR_TASK for each successive URL. This recreates the TikTok Lite Activity task so the app has to process the next deep link. CLEAR_TASK clears the *Activity task*, not app data or account sessions. No uninstall, force-stop or DATA_CLEAR is requested.
- Keep the target package pinned to the configured TikTok Lite packages, original https URL and order; preserve error on failed Intent resolve.
- Use runner position and its next non-blank position to show floating status "次の動画まであとN秒"; show "終了まであとN秒" for the last URL (stop mode). Outside URL mode keep the original overall-session timer.
- Stop clears countdown state; never infer actual video playback or reward from a successful Intent or foreground package.
- URL management remains on the existing website. No server or D1 schema change is required for this Android fix.
- Preserve original package jp.aruno.clicker and existing cert SHA-256 14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f. New v0.6.2/code19.

## Acceptance before calling it fully working
- Software tests and fresh app build/signing with existing certificate (API28/35, runner lifecycle and floating UI).
- Publish immutable APK and preserve the old code18 APK plus ARUNOMATIC code59.
- User verifies actual navigation past third entry (at least 4, 5, 6 with distinct video), app update without data loss and floating countdown.
- No claim that source changes guarantee video playback: TikTok Lite may disregard the URL, show a popup, or show recommendations. Such cases remain device-dependent.

## Control
Do not publish a new remote URL dataset when deploying the app. Do not clear old credentials or change existing management settings. The new website remains URL settings source.
