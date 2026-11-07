package app.thetahatch.meishi;

import static org.junit.Assert.*;

import android.content.Context;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

// New cards are read before old cards are brought up to date, and the upgrades stop for the day
// after their allowance of Gemini calls (so they cannot use up the free quota every day).
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PipelineQueueTest {
  @Test
  public void readingFirstUpgradesWithinTheDailyAllowance() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    Store s = Store.get(c);
    JSONObject old = Store.empty().put("status", "confirmed").put("queue", "kana,logo");
    JSONObject fresh = Store.empty().put("queue", "recognize");
    s.put(old);
    s.put(fresh);
    try {
      assertTrue(Pipeline.rank(Pipeline.step(fresh)) < Pipeline.rank(Pipeline.step(old)));
      assertEquals(2, Pipeline.pending(c));
      assertEquals(1, Pipeline.upgradeQueued(s));
      for (int i = 0; i < Pipeline.UPGRADE_PER_DAY; i++) Gemini.record(c, i % 2 == 0 ? "kana" : "logo", 200);
      assertEquals(Pipeline.UPGRADE_PER_DAY, Gemini.upgradeCallsToday(c));
      assertEquals("only the new card can run today", 1, Pipeline.pending(c));
      Gemini.record(c, "read", 429);
      JSONObject log = Gemini.log(c);
      assertEquals(1, log.getJSONObject("calls").getInt("read"));
      assertEquals(1, log.getInt("refused"));
    } finally {
      s.removeQuietly(old.getString("id"));
      s.removeQuietly(fresh.getString("id"));
      c.getSharedPreferences("prefs", 0).edit().remove("geminiLog").apply();
    }
  }
}
