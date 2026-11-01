# Aru-Tiker code32 履歴画面での停止対策・公開記録

2026-11-02 JST。状態：実装・ソフトウェア試験・既存署名での署名・配布ページ反映・公開APK照合が完了。code32の実機受入は未確認。

## 実機報告と調査の範囲

利用者から「全ての端末で履歴カードを開いたまま止まった」と写真2枚を受領。フロートはURL NO.2 準備、次の動画を開いていますの表示。code31の272試験成功を、OEM端末の実機成功として扱わない。端末数・個々の要素ツリー・操作イベントは未取得で、各端末の直接の停止原因を確定したとは扱わない。写真はソースrepoへ公開していない。

code31の実装には、active rootだけを見る、カードの要素IDを限定する、子要素が1つ欠けるだけでカード取得全体を拒否する、フロートの入力領域とスワイプが重なる、gesture callbackが来ないと進まない、準備中の詳しい状態が表示されない、という弱点があった。code32はこれらへの対策であり、写真の全端末で改善したとの実機証拠はまだない。

## 変更

- 画面上のinteractive windowsから履歴画面を選び、own overlay/rootに依存しない。OEMのtask class、header/thumbnailとcard-sized ancestor、dismiss actionにも対応。一部取得でも見えているカードは削除対象にし、欠落ツリーを空履歴とは判断しない。
- 履歴画面を正に識別できた時だけカード位置によるスワイプへ切替。識別できた履歴の「すべて閉じる」ボタンも使用する。横は上へ、縦は左へ。1枚ごとに再取得し、2枚表示・奥から出るカードも対象。
- スワイプの間だけフロートを透明・非表示・タッチ不可にし、完了/中断/期限切れで元のflags・表示・alphaへ戻す。サービスのhandlerが停止で消されても、別handlerで復元を実行する。永続設定や位置は書き換えない。
- 不明画面は連続12回（約6秒）、操作応答は期限付きで停止。次URLへ進めずHOMEへ戻り、具体的な理由を表示。準備中は「履歴カードを削除・確認しています」と状態を表示する。
- 失敗時のwindow owner/ID/class/構造件数を端末内に記録。「動作診断を共有」から利用者が手動共有できる。URLやtask本文、画像を診断に保存せず、中央へ自動送信しない。

ギフトページへの自動移動は追加していない。全履歴の空表示を複数回確認する条件、既存の履歴削除ON/OFF・縦/横、code30 URL移行、日次回数、ポップアップ処理を維持。DB/API/資格情報・package・署名鍵の変更なし。

## 固定値と証跡

- package jp.aruno.clicker / 0.6.15 / code32
- source 2dacd7b0f29500e95f0cfae187309c8b6fa1f033
- unsigned SHA-256 2ac28086194092e346dbd5b7a48fcc89ad369a0515eaa21ce91228e4ec8fad6c
- signed/public SHA-256 b4b7064608846cc821379d34f88c3a2d40e06c93d5cf9f8350170c4765a58e71 / 14849839bytes
- 既存証明書SHA-256 14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f
- Android試験・生成run 41326168210。JUnit XML26本を独立集計し294 tests、failures/errors/skipped各0。新しいwindow/OEM fixtures、欠落要素、未知画面、操作無応答、停止、表示の試験を含む。
- フロートの入力退避・正確な復元・取り外し後の復元をRobolectric API28/35で4件成功。実端末でのタッチ注入・OEMのwindow取得の証明とは区別する。
- 独立2repo復元・署名run 41326698950 / workflow 9313ca0f2ad56ff86d10fb3454f3c0f4a5b038ce。primary job124908062791 / backup124908062758。既存証明書・秘密鍵challenge・API26/28/35署名検証成功。新鍵なし。
- 署名前後のアプリ内容79 entry一致。独立取得したAPKの全bytes SHA、サイズ、埋込証明書fingerprintも一致。
- 公開run 41326879210 / source 8773d72f516f756b93c8b1a99cc0b17744306ed7。公開日時 2026-11-01T16:33:48.642Z。
- publisher/rollback/transport26試験、signing gate8試験成功。
- 既存20 modulesを保持し新wrapperを追加。code31を含む旧APKとARUNOMATICのroutes、settings/bindings/schedules/subdomainを保持。DB変更なし。旧Workerの暗号化backupを別GitHub branchへ保存・読戻し確認してから公開。
- 固定APKと互換APKの公開全bytes/SHA、最新metadataのversionCode32/SHA一致をpublisherで確認。

配布ページ：https://download.aruno-id.com/tiker
固定APK：https://download.aruno-id.com/apk/aru-tiker-code32.apk
互換APK：https://download.aruno-id.com/apk/aruno-clicker.apk

更新後はまず代表1台で、既存アプリへの上書き、保存データ、履歴削除ON、縦/横、スワイプ→空確認→次URLを確認する。止まる場合はフロートの具体的な理由と動作診断を使って調査する。未確認の改善を正式運用完了とは報告しない。
