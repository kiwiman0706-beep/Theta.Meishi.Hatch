package app.thetahatch.meishi;

import static org.junit.Assert.*;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

// The series' settings hub (Theta Home Hatch) as THETA_SHARED_SETTINGS.md §4.0–4.1 describes it:
// newer values come in for the items this app follows, changes go back, the key only on request.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HubTest {
  public static class FakeHome extends ContentProvider {
    static final Map<String, String[]> info = new LinkedHashMap<>();
    static final Map<String, String> written = new HashMap<>();
    static String key = "AIza-from-home";

    public boolean onCreate() {
      return true;
    }

    public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
      if (u.getPath().equals("/info")) {
        MatrixCursor m = new MatrixCursor(new String[] {"key", "value", "by", "at"});
        for (Map.Entry<String, String[]> e : info.entrySet()) m.addRow(new Object[] {e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]});
        return m;
      }
      MatrixCursor m = new MatrixCursor(new String[] {"key", "value"});
      if (u.getPath().equals("/secret/gemini.key") && !key.isEmpty()) m.addRow(new Object[] {"gemini.key", key});
      return m;
    }

    public int update(Uri u, ContentValues v, String s, String[] a) {
      for (String k : v.keySet()) written.put(k, v.getAsString(k));
      return v.size();
    }

    public String getType(Uri u) {
      return null;
    }

    public Uri insert(Uri u, ContentValues v) {
      return null;
    }

    public int delete(Uri u, String s, String[] a) {
      return 0;
    }
  }

  @Test
  public void settingsFromAndToTheHub() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    android.content.SharedPreferences prefs = c.getSharedPreferences("prefs", 0);
    assertEquals("no hub, nothing happens", "", Hub.installed(c));
    PackageInfo pi = new PackageInfo();
    pi.packageName = "app.thetahatch.home";
    Shadows.shadowOf(c.getPackageManager()).installPackage(pi);
    Robolectric.setupContentProvider(FakeHome.class, "app.thetahatch.home.theta");
    try {
      assertEquals("app.thetahatch.home", Hub.installed(c));
      prefs.edit().putString("model", "gemini-2.5-flash").putString("series", SeriesSettings.adopt(SeriesSettings.initial(), new JSONArray(), new HashSet<>()).toString()).apply();
      FakeHome.info.put("gemini.model", new String[] {"gemini-3-flash", "home", "2020-01-01T00:00:00.000Z"});
      FakeHome.info.put("ui.skin", new String[] {"dark", "home", "2020-01-01T00:00:00.000Z"});
      Series.fromHub(c);
      assertEquals("the hub's newer model is taken", "gemini-3-flash", prefs.getString("model", ""));

      // This app changes the model: the hub hears of it.
      prefs.edit().putString("model", "gemini-3-pro").apply();
      Series.changed(c, "gemini.model");
      assertEquals("gemini-3-pro", FakeHome.written.get("gemini.model"));
      // Older on the hub than ours: kept.
      Series.fromHub(c);
      assertEquals("gemini-3-pro", prefs.getString("model", ""));

      // "このアプリだけ": the hub's value is not taken, ours is not sent.
      Series.follow(c, "gemini.model", false);
      FakeHome.info.put("gemini.model", new String[] {"gemini-9", "home", "2099-01-01T00:00:00.000Z"});
      Series.fromHub(c);
      assertEquals("gemini-3-pro", prefs.getString("model", ""));

      assertEquals("AIza-from-home", Hub.secret(c, "app.thetahatch.home", "gemini.key"));
      FakeHome.key = "";
      assertEquals("", Hub.secret(c, "app.thetahatch.home", "gemini.key"));
    } finally {
      prefs.edit().clear().apply();
    }
  }
}
