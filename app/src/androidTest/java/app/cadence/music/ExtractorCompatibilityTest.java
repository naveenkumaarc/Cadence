package app.cadence.music;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExtractorCompatibilityTest {
  @Test public void extractorUrlCodecsWorkBeforeAndroidThirteen() throws Exception {
    String title = "Tamil தமிழ் & music";
    // The extractor is a runtime dependency of core, rather than part of its public Java API.
    Class<?> utils = Class.forName("org.schabi.newpipe.extractor.utils.Utils");
    String encoded = (String)utils.getMethod("encodeUrlUtf8",String.class).invoke(null,title);
    assertEquals(title,utils.getMethod("decodeUrlUtf8",String.class).invoke(null,encoded));
    Class<?> factory = Class.forName("org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory");
    Object handler = factory.getMethod("getInstance").invoke(null);
    assertEquals("abcdefghijk",factory.getMethod("getId",String.class).invoke(handler,"https://www.youtube.com/watch?v=abcdefghijk"));
  }
}
