# Aru-Tiker code28: remove automatic URL1 insertion

User reports the code27 URL1 detour caused extra popups and requests removal as code28. Package jp.aruno.clicker / 0.6.11 / code28, existing signer retained.

Remove the first-URL preparation option, extra opener, three-second delay and associated task-preservation flag. All video starts (normal, sixth start and splash sixth start) share the same direct runner. Starting at URL6 opens URL6 directly and no longer requires URL1. Normal starts and configured LOOP still visit URL1 in its ordinary list position; the saved list is unchanged. Do not automatically re-enable Gift/Home navigation. Preserve code27 popup detection/dismissal/diagnostics and their known physical limitations, optional splash, first-five daily-limit exemption, daily video counts, configured waits, pause and stop.

Replace obsolete URL1-detour tests with direct-start/next-transition, blank or failing URL1, capped-video skipping and pause/stop/lock checks. Retain all other regressions. Test/build/sign/publication facts will be recorded only when completed. No new claim of physical popup dismissal, gauge progress or reward credit.
