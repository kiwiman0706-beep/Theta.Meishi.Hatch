package jp.yaegaki.meishinote;

import static org.junit.Assert.*;

import org.junit.Test;

// Gemini's refusals as Google sends them, sorted into what the user can do (same as gemini.test.js).
public class GeminiFailureTest {
  static final String MINUTE =
      "{\"error\":{\"code\":429,\"message\":\"You exceeded your current quota, please check your plan and billing details. Please retry in 37.2s.\",\"status\":\"RESOURCE_EXHAUSTED\",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.QuotaFailure\",\"violations\":[{\"quotaMetric\":\"generativelanguage.googleapis.com/generate_content_free_tier_requests\",\"quotaId\":\"GenerateRequestsPerMinutePerProjectPerModel-FreeTier\"}]},{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"37s\"}]}}";
  static final String DAILY = MINUTE.replace("PerMinutePerProject", "PerDayPerProject");
  static final String BAD_KEY =
      "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\",\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}";
  static final String DISABLED =
      "{\"error\":{\"code\":403,\"message\":\"Generative Language API has not been used in project 1 before or it is disabled.\",\"status\":\"PERMISSION_DENIED\",\"details\":[{\"reason\":\"SERVICE_DISABLED\"}]}}";

  @Test
  public void sorted() {
    assertEquals("minute", Gemini.kind(429, MINUTE));
    assertEquals(37, Gemini.retrySeconds(MINUTE));
    assertEquals("daily", Gemini.kind(429, DAILY));
    assertEquals("key", Gemini.kind(400, BAD_KEY));
    assertEquals("billing", Gemini.kind(402, "{}"));
    assertTrue(new Gemini.Failure(402, "billing", 0, "x").stopsAll());
    assertEquals("disabled", Gemini.kind(403, DISABLED));
    assertEquals("denied", Gemini.kind(403, "{}"));
    assertEquals("model", Gemini.kind(404, "{}"));
    assertEquals("other", Gemini.kind(500, "{}"));
    assertEquals(13, Gemini.retrySeconds("Please retry in 12.5s"));
    assertEquals(0, Gemini.retrySeconds("{}"));
    assertTrue(new Gemini.Failure(429, "daily", 0, "x").stopsAll());
    assertFalse(new Gemini.Failure(500, "other", 0, "x").stopsAll());
    assertTrue(Gemini.advice("daily").contains("1日の無料枠"));
  }
}
