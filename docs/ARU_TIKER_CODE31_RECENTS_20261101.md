# Aru-Tiker code31 履歴カード全削除・公開記録

状態：実装、ソフトウェア試験、既存署名による署名、配布ページ反映、公開APK全バイト照合が完了。実機受入と正式運用開始は未確認。

- package：jp.aruno.clicker
- version：0.6.14 / code31
- APK source：f8d75f372be32d22acd18bd8327d915be61d243e
- APK SHA-256：aa2ed9e249682882bf2ab41f571aa31eb635b9dd0094110946f62f789f301509
- APK bytes：14833455
- 既存証明書SHA-256：14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f
- 未署名SHA-256：05e6441cffcfd7675e2b3e8b176c7b05da0b6aa4ad4d95221b7dd80f15b4e6a7
- Android試験・生成run：41311122908（JUnit XML23本、272 tests、failures/errors/skipped各0）
- 既存鍵の独立2repo復元・署名run：41311729508（primary job124863372824 / backup job124863372829、証明書・秘密鍵challenge成功）
- 署名検証：API26/28/35。署名前後のアプリ内容79 entry一致。独立取得APKの証明書fingerprintと全bytes SHAも一致。
- 公開run：41311936414 / publisher source 9fd380abc0e2ad30690ebe31c708218361c324e0
- 公開日時：2026-11-01T13:35:41.303Z
- 配布ページ：https://download.aruno-id.com/tiker
- 固定APK：https://download.aruno-id.com/apk/aru-tiker-code31.apk
- 互換APK：https://download.aruno-id.com/apk/aruno-clicker.apk

## 動作と設定

起動画面に履歴削除ON/OFF（初期OFF）、履歴表示「縦／横」を追加。横はカード中央から上、縦は左へスワイプし、反応しない場合はカード内の右寄りを起点に再試行する。長押し待機はしない。縦の2枚表示も毎回実際のカード境界を取得して1枚ずつ処理し、奥から出るカードも繰り返す。TikTok以外のChrome/LINEなど全アプリの履歴が対象。

次の有効URLを開く前に、既知の履歴画面または専用の空表示要素による「履歴なし」を3回確認する。通常HOMEへ自動復帰した場合も履歴を開き直して空を確認する。ARUNO HOME等の独自HOMEには標準workspace IDを要求しない。画面要素欠落、TikTokが見つからないこと、スワイプの完了通知だけでは成功にしない。停止・画面OFF・ロック・サービス切断では後続処理を止め、一時停止中は次の操作を保留。消せないカードや未対応画面では次URLへ進まない。

既存code30のURL移行・最大195件・全URL共通秒数・日次回数・停止再開・ポップアップ参考データを維持。DB/API/資格情報/署名鍵/packageの変更なし。履歴削除はAndroidの強制停止ではない。

## 公開保全・限界

既存19 modulesを全bytes保持し、新しいcode31 wrapperだけを追加。旧code30を含むAru-Tiker固定APK、ARUNOMATIC code55/59/60/61と既存表示・metadata route、Workerのsettings/bindings/schedules/subdomainを保持。公開APKの新routeと互換routeは署名済み候補と全bytes/SHA一致。公開metadataのcode31/SHA一致を確認。DB変更なし。以前のWorkerを暗号化し、独立GitHub branchへの保存と読戻し検証後に更新した。

最初の候補run41310413423（269 tests）とrun41310868104（272 tests）は成功した未署名候補だが、最終の独自HOME対応候補で置換し、公開していない。最終の272試験と署名済みAPKだけを公開した。署名gate8試験とpublisher/rollback/transport26試験も成功。

実機の上書き・データ保持、3ボタン/ジェスチャー操作、縦2枚の実際のスワイプ、OEMの要素取得・ロックカード、実TikTok動画の再生は未確認。実機受入と正式運用開始を公開成功に読み替えない。

GitHubの配布・署名詳細は aruno-platform-ops の feature/tiker-code31-20261101 branch、evidence/tiker-code31-20261101/release-record.json を参照。
