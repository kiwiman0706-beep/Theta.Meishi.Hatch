package jp.yaegaki.meishinote;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;

// Theta Home Hatch moves Meishi to its new ID: the bridge version writes the zip, the new one reads
// it (docs/MIGRATION.md §3 in Theta.Hatch.Series). Here both ends run in one app.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MigrationTest {
  @Test
  public void cardsPhoneLinksAndSettingsMove() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    Store s = Store.get(c);
    SharedPreferences prefs = c.getSharedPreferences("prefs", 0);
    try (OutputStream o = new FileOutputStream(s.mediaFile("card-1.jpg"))) {
      o.write("front".getBytes());
    }
    try (OutputStream o = new FileOutputStream(s.mediaFile("nenga-1.jpg"))) {
      o.write("nenga".getBytes());
    }
    try (OutputStream o = new FileOutputStream(s.mediaFile("ink-1.png"))) {
      o.write("ink".getBytes());
    }
    JSONObject p =
        Store.empty().put("name", "山田 太郎").put("status", "confirmed").put("contactRawId", 4711L).put("contactAccount", "a@example.com")
            .put("calendarEventId", 99);
    p.getJSONArray("pages").put(new JSONObject().put("file", "card-1.jpg"));
    p.getJSONArray("notes").put(new JSONObject().put("id", "n1").put("time", 1L).put("text", "手書き").put("ink", "ink-1.png"));
    p.put("greetings", new JSONArray().put(new JSONObject().put("id", "g1").put("year", 2027).put("files", new JSONArray().put(new JSONObject().put("file", "nenga-1.jpg")))));
    String id = p.getString("id");
    s.put(p);
    prefs.edit().putString("model", "gemini-2.5-flash").putLong("calendarId", 3L).putInt("contactNameRule", 1).putBoolean("autoContact", false).putString("key", "SECRET").apply();

    File zip = Backup.make(c, true);
    // The new app: nothing yet.
    s.removeQuietly(id);
    prefs.edit().clear().apply();

    int added;
    try (InputStream in = new FileInputStream(zip)) {
      added = Backup.restore(c, in, true);
    }
    assertEquals(1, added);
    JSONObject q = s.person(id);
    assertEquals(4711L, q.getLong("contactRawId"));
    assertEquals(99, q.getInt("calendarEventId"));
    String nengaFile = q.getJSONArray("greetings").getJSONObject(0).getJSONArray("files").getJSONObject(0).getString("file");
    assertTrue("New Year card picture follows its new name", s.mediaFile(nengaFile).isFile());
    assertTrue(s.mediaFile(q.getJSONArray("notes").getJSONObject(0).getString("ink")).isFile());
    assertEquals("gemini-2.5-flash", prefs.getString("model", ""));
    assertEquals(3L, prefs.getLong("calendarId", -1));
    assertEquals(1, prefs.getInt("contactNameRule", 0));
    assertFalse(prefs.getBoolean("autoContact", true));
    assertFalse("the Gemini key never moves", prefs.contains("key"));

    // Already back from the Vault before the import: only the phone's links are added.
    JSONObject fromVault = s.person(id);
    fromVault.remove("contactRawId");
    s.put(fromVault);
    try (InputStream in = new FileInputStream(zip)) {
      assertEquals(0, Backup.restore(c, in, true));
    }
    assertEquals(4711L, s.person(id).getLong("contactRawId"));

    // An ordinary backup still leaves the phone's links out.
    File plain = Backup.make(c);
    s.removeQuietly(id);
    try (InputStream in = new FileInputStream(plain)) {
      Backup.restore(c, in);
    }
    assertFalse(s.person(id).has("contactRawId"));
    s.removeQuietly(id);
    prefs.edit().clear().apply();
  }

  @Test
  public void refusedWithoutAResultToGiveItTo() {
    Activity a = Robolectric.buildActivity(MigrationActivity.class, new Intent(MigrationActivity.EXPORT)).create().get();
    assertTrue(a.isFinishing());
    assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(a).getResultCode());
    assertTrue(Shadows.shadowOf(a).getResultIntent().getStringExtra(MigrationActivity.EXTRA_ERROR).contains("startActivityForResult"));
  }
}
