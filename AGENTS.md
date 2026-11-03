# ARUNO Clicker / Aru-Tiker agent operating rules

Before editing Android CI or release workflows, read `docs/ARUNO_ANDROID_THREE_STAGE_CI_20261103.md`.

- Every code change / PR starts with lightweight host or unit tests. Never run full Android emulator matrices on every branch push.
- For UI or OS-specific defects, run exactly the impacted emulator API first (typically API28 for Android9; API35 for Android15). Use an existing candidate APK where possible; do not rebuild and re-sign merely to repeat a test.
- Use `ci/ci_tier_plan.py` or `.github/workflows/aruno-ci-plan.yml` to select lightweight / targeted / full. Full requires an explicit release approval reference and a pinned source commit. The planner itself DOES NOT execute tests or authorize publishing.
- For the final approved release candidate, run every supported Android test and retain existing key/certificate verification, installation/update compatibility, public APK SHA256 readback and rollback safeguards. A green CI test is not proof of physical-device success.
- Never auto-publish during a test-only workflow, expose signing credentials, overwrite existing public APKs, or change production data just to save CI time.
- When creating the next version-specific workflow, wire the tier planner to the real unit tests, selected emulator jobs and full release gating; record runner-job minutes and avoid blind retries.
