package app.cadence.core;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

public class SpotifyClientTest {
  private static final String ID = "37i9dQZF1DXcBWIGoYBM5M";

  @Test
  public void allPagesPreserveOrderNullEntriesAndDuplicates() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      String next = server.url("/v1/playlists/" + ID + "/items?offset=50").toString();
      server.enqueue(new MockResponse().setBody("{\"name\":\"My playlist\"}"));
      server.enqueue(
          new MockResponse()
              .setBody(
                  "{\"items\":[{\"item\":"
                      + song("First")
                      + "},{\"item\":null}],\"next\":\""
                      + next
                      + "\"}"));
      server.enqueue(
          new MockResponse()
              .setBody(
                  "{\"items\":[{\"track\":"
                      + song("Second")
                      + "},{\"item\":"
                      + song("First")
                      + "}],\"next\":null}"));
      var imported =
          new SpotifyClient(new OkHttpClient(), server.url("/v1/").toString())
              .playlist("test-token", ID);
      assertEquals("My playlist", imported.name);
      assertEquals(3, imported.tracks.size());
      assertEquals(1, imported.skipped);
      assertEquals("First", imported.tracks.get(0).title);
      assertEquals("Second", imported.tracks.get(1).title);
      assertEquals("First", imported.tracks.get(2).title);
      assertEquals("", imported.tracks.get(0).sourceUrl);
      assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"));
      assertEquals("/v1/playlists/" + ID + "/items?limit=50", server.takeRequest().getPath());
    }
  }

  @Test
  public void tokenNeverFollowsUnexpectedPaginationHost() throws Exception {
    try (MockWebServer server = new MockWebServer();
        MockWebServer foreign = new MockWebServer()) {
      server.start();
      foreign.start();
      server.enqueue(new MockResponse().setBody("{\"name\":\"Mine\"}"));
      server.enqueue(
          new MockResponse().setBody("{\"items\":[],\"next\":\"" + foreign.url("/steal") + "\"}"));
      assertThrows(
          IOException.class,
          () ->
              new SpotifyClient(new OkHttpClient(), server.url("/v1/").toString())
                  .playlist("test-token", ID));
      assertNull(foreign.takeRequest(50, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  public void forbiddenAndRateLimitsExplainRecovery() throws Exception {
    for (int code : new int[] {401, 403, 429})
      try (MockWebServer server = new MockWebServer()) {
        server.start();
        server.enqueue(new MockResponse().setResponseCode(code).setHeader("Retry-After", "10"));
        IOException error =
            assertThrows(
                IOException.class,
                () ->
                    new SpotifyClient(new OkHttpClient(), server.url("/v1/").toString())
                        .playlist("test-token", ID));
        assertFalse(error.getMessage().contains("test-token"));
        assertTrue(
            error
                .getMessage()
                .contains(code == 401 ? "Connect again" : code == 403 ? "CSV" : "10"));
      }
  }

  private static String song(String title) {
    return "{\"id\":\"abc\",\"type\":\"track\",\"name\":\""
        + title
        + "\",\"artists\":[{\"name\":\"Artist\"}],\"duration_ms\":180000}";
  }
}
