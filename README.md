# Theta Meishi Hatch（旧 Meishi-Claude）

個人向けの名刺管理アプリ。名刺を撮影 → Geminiで読み取り → Google連絡先に登録。名刺画像・やり取りのメモ・録音を人ごとに保存します。ChatGPTで作った「名刺ノート 0.2.0」を引き継いだものです。名刺交換日のカレンダー表示・Googleカレンダー登録、地図表示、Google Drive同期にも対応しています。

## ダウンロード

[Releases](https://github.com/kiwiman0706-beep/Theta.Meishi.Hatch/releases/latest) から、Android（APK）・Windows（インストーラー）・Mac（dmg）を入手できます。iPhoneは [Web版](https://kiwiman0706-beep.github.io/Theta.Meishi.Hatch/)（Safariでホーム画面に追加）。新しい版はアプリが知らせます（Windowsは自動更新）。リリースの作り方は [`docs/RELEASING.md`](docs/RELEASING.md)。

| フォルダ | 中身 |
| --- | --- |
| `android/` | Androidアプリ（Java + WebView UI）。ビルド方法は `android/README.md` |
| `desktop/` | Windows / Mac版（Electron）。Vaultフォルダを直接読み書きし、DeskHatchのドロワーにも表示できる。使い方は `desktop/README.md` |
| `web/` | Web版（PWA。iPhoneのSafariでホーム画面に追加）。共通UIとデスクトップの処理をブラウザで動かす。`https://kiwiman0706-beep.github.io/Theta.Meishi.Hatch/`、設定は `docs/WEB_SETUP.md` |
| `tools/` | 補助スクリプト（`make_meishi_import.py`：取り込み用のZIPを作る） |
| `docs/` | 引き継ぎメモ・検証記録・ロードマップ |

Theta Hatch シリーズの 1 つです。シリーズ共通の約束（仕様・デザイン・署名鍵・移行）は [kiwiman0706-beep/Theta.Hatch.Series](https://github.com/kiwiman0706-beep/Theta.Hatch.Series)、今後の予定は [`docs/ROADMAP.md`](docs/ROADMAP.md) を参照してください。

アプリ ID は 1.0.0 から `app.thetahatch.meishi`（それまでは `jp.yaegaki.meishiclaude`）。前の版からの移行は Theta Home Hatch が行います（0.19.0 が橋渡し版）。
