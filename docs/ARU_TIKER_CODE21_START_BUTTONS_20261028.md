# Aru-Tiker code21: sixth URL start and always-visible accessibility settings

User requests: add a start button for URL slot6 and put an accessibility-settings button on the app's first screen. Based on main code20, retaining its splash recovery and failed-link skip changes.

- HOME always shows the accessibility ON/OFF state and a button opening Android's public accessibility settings. The user selects Aru-Tiker and explicitly enables it; the app does not grant accessibility to itself.
- HOME and both existing floating-controller implementations gain `6番目からスタート`. It uses a one-run defensive snapshot starting at zero-based index5, preserves all slot numbers and wait times, and never saves/downloads/modifies the stored URL dataset. Blank slot6 skips forward; no usable slot6+ refuses the start instead of falling back to slots1–5.
- Normal start retains the saved start position. Existing stop, screen-OFF, locked-device, service-disconnect, duplicate-start and recovery behavior remain in the same runner. Configured loop behavior remains unchanged.
- Running sessions disable the additional start button. Enabling accessibility after a refused start does not queue an automatic start.
- Version0.6.4/code21, same package and stable signer. No screen-off-timeout feature in Aru-Tiker; DPC owns that setting.

Verification/publication results will be recorded after completion. Tests do not prove TikTok playback, rewards or physical-device acceptance. The management site's requested slot6+ delete buttons are a separate frontend change; deployment must preserve existing URLs and credentials.
