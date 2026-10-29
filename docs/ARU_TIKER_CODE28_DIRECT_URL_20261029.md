# Aru-Tiker code28: remove automatic URL1 insertion

User reports the code27 URL1 detour caused extra popups and requests removal as code28. Package jp.aruno.clicker / 0.6.11 / code28, existing signer retained.

Remove the first-URL preparation option, extra opener, three-second delay and associated task-preservation flag. All video starts (normal, sixth start and splash sixth start) share the same direct runner. Starting at URL6 opens URL6 directly and no longer requires URL1. Normal starts and configured LOOP still visit URL1 in its ordinary list position; the saved list is unchanged. Do not automatically re-enable Gift/Home navigation. Preserve code27 popup detection/dismissal/diagnostics and their known physical limitations, optional splash, first-five daily-limit exemption, daily video counts, configured waits, pause and stop.

Replace obsolete URL1-detour tests with direct-start/next-transition, blank or failing URL1, capped-video skipping and pause/stop/lock checks. Retain all other regressions. Test/build/sign/publication facts will be recorded only when completed. No new claim of physical popup dismissal, gauge progress or reward credit.

## Verified publication

Final source a9de108e6864801d22f744ec76a8d72a68179df5 passed all 230 tests (run 40881038601), unsigned build and private byte readback. Same existing certificate restored from both pinned GitHub stores and signed in run 40881778433. Signed/public APK SHA-256 c4c3e66e46617686dd3d9b5ebd694d2c7f7c166b45a3bbe3afbeb399f7b621f1. Public page, latest metadata and APK bytes verified by publication run 40881981866 at 2026-10-29T13:27:53.237Z. Existing code27 and earlier APK routes and shared ARUNOMATIC routes/settings retained; database untouched. Download: https://download.aruno-id.com/tiker; APK: https://download.aruno-id.com/apk/aru-tiker-code28.apk. Physical install/data retention, popup closing, actual playback and gauge/reward behavior remain untested on code28. Full release record is in aruno-platform-ops/evidence/tiker-code28-20261029/release-record.json.
