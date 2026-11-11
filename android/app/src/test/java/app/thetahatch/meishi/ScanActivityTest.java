package app.thetahatch.meishi;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

// "撮って登録" (series spec §6.2 SCAN; the intent filter is checked on the built APK, docs/TESTING.md):
// the camera (or picker) is asked for at once; leaving before a card is taken, or with back, gives RESULT_CANCELED.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScanActivityTest {
  @Test
  public void opensForTheCallerAndGoesBackWhenLeft() throws Exception {
    Intent i = new Intent(MainActivity.ACTION_HATCH_SCAN).putExtra("source", "home").putExtra("mode", "pick");
    ActivityController<ScanActivity> c = Robolectric.buildActivity(ScanActivity.class, i).create();
    ScanActivity a = c.get();
    assertTrue(a.scanCall);
    assertEquals("home", a.scanFrom);
    assertEquals("scanPick", a.launch);
    a.handle("scanDone", new JSONObject());
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
    assertTrue(a.isFinishing());
    assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(a).getResultCode());
  }

  @Test
  public void theAppItselfIsNotInThatMode() {
    MainActivity m = Robolectric.buildActivity(MainActivity.class).create().get();
    assertFalse(m.scanCall);
  }
}
