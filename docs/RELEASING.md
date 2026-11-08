# リリースの手順

**版を上げて既定のブランチに push するだけ**です（シリーズ共通仕様 §6.1 の 2）。

1. `android/app/build.gradle` の `versionName`（と `versionCode`）、`desktop/package.json`・`web/package.json` の `version` を同じ版に揃える
2. `docs/releases/v<版>.md`（リリースノート）を書く
3. コミットして既定のブランチに push

GitHub Actions（`.github/workflows/release.yml`）が、その版のタグ `v<版>` がまだ無いことを確かめ、テスト → Android APK・Windowsインストーラー・Mac（dmg/zip、Appleシリコン/Intel）を作り、タグ `v<版>` の GitHub Release として公開します（draft・prerelease にはしない）。
版を上げていない push では何もしません（最初の確認だけで終わる）。版がずれている・リリースノートが無いと、その確認で止まります。

手で動かす（Actions →「Release」→「Run workflow」、「Commit to build and release」に前のコミットも指定できる）・タグを送る（`git push origin v<版>`）こともできます。

> **ワークフローを変えたコミット**で版を上げると、GitHub が Actions にタグを作らせないため、最後の公開だけ 403 で止まります（下の「403 で止まるとき」）。ワークフローの変更は版上げとは別のコミットにし、その後に版を上げる。Secret `RELEASE_TOKEN`（個人用アクセストークン：このリポジトリの Contents と Workflows を Read and write）を登録しておけば、この制限もなくなります。

## 自動更新のしくみ

- **Windows**：`electron-updater` がReleasesの `latest.yml` を見て、新しい版をバックグラウンドでダウンロードし、次の起動（またはトレイの「再起動して更新」）で入れ替える。
- **Mac**：Appleの開発者署名が無いと自動入れ替えができないため、`latest-mac.yml` で新しい版を知らせ、ダウンロードページを開く。
- **Android**：1.0.0 から、更新は Theta Home Hatch・KidsHome Hatch（更新ハブ）が行う（シリーズ共通仕様 §6.1）。このアプリは起動時（12時間に1回まで）に最新のReleaseを確認して帯を出すだけで、「更新する」でハブに頼む（ハブが無ければ Releases ページを開く）。Release には APK を 1 つ（`ThetaMeishiHatch-<版>.apk`）、共通の鍵で署名して付ける。
- **Web版**：開いたときにService Workerが新しい版に入れ替える（次に開いたときから新しい版）。
- どれも **リポジトリが公開** であることが前提（非公開だとReleasesを読めない）。

## AndroidのAPK署名（初回だけ設定）

署名鍵はリポジトリに入れず、GitHubの Secrets に置きます。
リポジトリの Settings → Secrets and variables → Actions → New repository secret で次の4つを登録：

| 名前 | 中身 |
| --- | --- |
| `ANDROID_KEYSTORE_B64` | シリーズ共通の署名鍵（Theta.Hatch.Series の `secrets/README.md`）をBase64にした文字列 |
| `ANDROID_KEYSTORE_PASSWORD` | 鍵ストアのパスワード |
| `ANDROID_KEY_ALIAS` | 鍵の別名（`thetahatch`） |
| `ANDROID_KEY_PASSWORD` | 鍵のパスワード（鍵ストアと同じ） |

（Theta.Hatch.Series の `tools/sync-secrets` でまとめて配れる）

未設定でもリリースは作られますが、APKは付きません。設定後にそのタグのワークフローを「Re-run all jobs」すると、APKが追加されます。
**同じ鍵で署名し続けること**（鍵が変わると、上書きインストールできなくなります）。

## 「HTTP 403: Resource not accessible by integration」で公開だけ止まるとき

ワークフロー（`.github/workflows/`）を変えたコミットには、GitHub Actions の権限では新しいタグを作れない（2026-11-07、0.19.0 で確認）。ビルドは通り、最後の公開で 403 になる。

1. GitHub の Releases →「Draft a new release」で、タグ `v<版>`（Create new tag）と、そのコミットを Target にして、ファイル無しで「Publish release」
2. タグができると Release ワークフローがタグから動き、出来ているリリースにファイルを添付する（`gh release upload`）
3. 本文は `docs/releases/v<版>.md` を貼る

手で実行するときは「Commit to build and release」（`ref`）に、ブランチの先より前のコミットも指定できる。
