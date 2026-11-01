# Aru-Tiker code31: verified all-card cleanup before the next URL

0.6.14 / code31 / jp.aruno.clicker. Based on the published code30 app source and retained URL migration, counters, popup references and configuration. Adds local per-device cleanup ON/OFF (initially OFF) and horizontal/vertical layout selection to the startup screen.

When ON, the next eligible URL is gated on opening the system overview, re-reading and swiping all visible cards, and repeating for hidden cards. Horizontal layout swipes upward; vertical layout swipes left, with a right-of-center retry. Two visible cards are discovered separately and re-read after each gesture. This closes all apps' recent cards, including Chrome/LINE. Recents removal is not Android force-stop.

A known launcher/SystemUI surface and task-card bounds are required. Empty completion requires an explicit known empty-history label in three separate complete-tree reads, followed by HOME verification. A null/incomplete tree, absence of TikTok, cancelled gesture, pinned/stuck card, or unknown OEM layout cannot authorize the next URL. OEM auto-return to HOME triggers bounded reopening of overview to verify empty. Stop/lock/unbind cancel subsequent work; pause holds further scheduled actions.

No DB/API/credential/key change. Existing package and signing certificate must be retained. Software tests and signed/public byte verification will be recorded separately; actual OEM phones, two-card gestures and playback remain unverified until device acceptance.
