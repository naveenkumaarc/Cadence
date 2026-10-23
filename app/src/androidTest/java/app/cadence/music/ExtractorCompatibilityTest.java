package app.cadence.music;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExtractorCompatibilityTest {
  @Test public void extractorUrlCodecsWorkBeforeAndroidThirteen() throws Exception {
    String title = "Tamil தமிழ் & music";
    assertEquals(title, org.schabi.newpipe.extractor.utils.Utils.decodeUrlUtf8(
        org.schabi.newpipe.extractor.utils.Utils.encodeUrlUtf8(title)));
    assertEquals("abcdefghijk", org.schabi.newpipe.extractor.services.youtube.linkHandler
        .YoutubeStreamLinkHandlerFactory.getInstance().getId("https://www.youtube.com/watch?v=abcdefghijk"));
  }
}
