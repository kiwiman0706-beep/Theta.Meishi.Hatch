package app.thetahatch.meishi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.InputStream;

// Moving the app to its new ID, run by Theta Home Hatch (Theta.Hatch.Series docs/MIGRATION.md §3):
//  - app.thetahatch.action.EXPORT (the bridge version, old ID): the cards, their pictures and notes,
//    what only this phone knows (Google contact and calendar links) and the settings, as one zip.
//    Returned as the result's data (a content:// URI the caller may read).
//  - app.thetahatch.action.IMPORT (the new version): the zip as the intent's data (or EXTRA_STREAM).
//    The result says how many cards came in, or the error.
// Only an app signed with the same (series) key may ask, it must ask for a result
// (startActivityForResult), and the user confirms. The Gemini key is never in the zip.
public class MigrationActivity extends Activity {
  public static final String EXPORT = "app.thetahatch.action.EXPORT";
  public static final String IMPORT = "app.thetahatch.action.IMPORT";
  public static final String EXTRA_COUNT = "app.thetahatch.extra.COUNT";
  public static final String EXTRA_ERROR = "app.thetahatch.extra.ERROR";
  public static final String EXTRA_FORMAT = "app.thetahatch.extra.FORMAT";
  public static final String FORMAT = "meishi-migration/1";

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    String action = String.valueOf(getIntent().getAction());
    String caller = getCallingPackage();
    if (caller == null) {
      fail("結果を受け取る形（startActivityForResult）で呼んでください");
      return;
    }
    if (!caller.equals(getPackageName())
        && getPackageManager().checkSignatures(caller, getPackageName()) != PackageManager.SIGNATURE_MATCH) {
      fail("Theta Hatch シリーズの同じ鍵で署名されたアプリからしか移せません");
      return;
    }
    String who = label(caller);
    int cards;
    try {
      cards = Store.get(this).snapshot().getJSONArray("people").length();
    } catch (Exception e) {
      fail(e.getMessage());
      return;
    }
    if (action.equals(EXPORT))
      confirm(
          "データを書き出す",
          who + " が、新しい Theta Meishi Hatch へ移すために、この端末の名刺 " + cards + "件（画像・メモ・手書き・録音・連絡先とのつながり・設定）を受け取ります。Gemini の APIキーは渡しません。",
          "書き出す",
          this::export);
    else if (action.equals(IMPORT)) {
      Uri u = getIntent().getData() != null ? getIntent().getData() : (Uri) getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
      if (u == null) {
        fail("読み込むファイルが渡されていません");
        return;
      }
      confirm(
          "データを読み込む",
          who + " から、前の Theta Meishi Hatch の名刺・画像・メモ・設定を読み込みます。" + (cards > 0 ? "今ある名刺 " + cards + "件はそのまま残し、同じ名刺は重ねません。" : ""),
          "読み込む",
          () -> importFrom(u));
    } else fail("不明な操作です");
  }

  String label(String pkg) {
    try {
      return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
    } catch (Exception e) {
      return pkg;
    }
  }

  void confirm(String title, String message, String ok, Runnable go) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(ok, (d, w) -> go.run())
        .setNegativeButton("やめる", (d, w) -> fail("利用者が取りやめました"))
        .setOnCancelListener(d -> fail("利用者が取りやめました"))
        .show();
  }

  void export() {
    AlertDialog wait = progress("書き出しています…");
    new Thread(
            () -> {
              try {
                File f = Backup.make(this, true);
                Uri u = FilesProvider.uri(this, f).buildUpon().appendQueryParameter("name", "ThetaMeishiHatch-migration.zip").build();
                Intent r =
                    new Intent()
                        .setDataAndType(u, "application/zip")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .putExtra(EXTRA_FORMAT, FORMAT)
                        .putExtra(EXTRA_COUNT, Store.get(this).snapshot().getJSONArray("people").length());
                r.setClipData(ClipData.newRawUri("migration", u));
                done(wait, RESULT_OK, r);
              } catch (Exception e) {
                done(wait, RESULT_CANCELED, new Intent().putExtra(EXTRA_ERROR, String.valueOf(e.getMessage())));
              }
            },
            "migration-export")
        .start();
  }

  void importFrom(Uri u) {
    AlertDialog wait = progress("読み込んでいます…");
    new Thread(
            () -> {
              try (InputStream in = getContentResolver().openInputStream(u)) {
                int added = Backup.restore(this, in, true);
                done(wait, RESULT_OK, new Intent().putExtra(EXTRA_COUNT, added));
              } catch (Exception e) {
                done(wait, RESULT_CANCELED, new Intent().putExtra(EXTRA_ERROR, String.valueOf(e.getMessage())));
              }
            },
            "migration-import")
        .start();
  }

  AlertDialog progress(String text) {
    return new AlertDialog.Builder(this).setMessage(text).setCancelable(false).show();
  }

  void done(AlertDialog wait, int code, Intent data) {
    runOnUiThread(
        () -> {
          wait.dismiss();
          setResult(code, data);
          finish();
        });
  }

  void fail(String why) {
    setResult(RESULT_CANCELED, new Intent().putExtra(EXTRA_ERROR, why));
    finish();
  }
}
