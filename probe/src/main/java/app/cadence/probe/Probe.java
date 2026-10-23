package app.cadence.probe;

import app.cadence.core.HttpDownloader;
import app.cadence.core.YouTube;
import okhttp3.Request;

public final class Probe {
  public static void main(String[] args) throws Exception {
    System.out.println(
        "Live probe: search -> extract audio -> request media bytes (no stream URLs logged)");
    var results = YouTube.search("Beethoven Moonlight Sonata audio");
    System.out.println("SEARCH: " + results.size() + " results");
    if (results.isEmpty()) throw new IllegalStateException("No search results");
    String link = args.length > 0 ? args[0] : results.get(0).sourceUrl;
    var resolved = YouTube.resolve(link);
    System.out.println(
        "EXTRACTION: "
            + resolved.track.title
            + " / "
            + resolved.mimeType
            + " / "
            + resolved.bitrate
            + " kbps");
    try (var response =
        HttpDownloader.MEDIA_CLIENT
            .newCall(
                new Request.Builder()
                    .url(resolved.streamUrl)
                    .header("User-Agent", HttpDownloader.USER_AGENT)
                    .header("Range", "bytes=0-65535")
                    .build())
            .execute()) {
      if (!response.isSuccessful() || response.body() == null)
        throw new IllegalStateException("MEDIA: HTTP " + response.code());
      byte[] bytes = response.body().byteStream().readNBytes(65536);
      System.out.println(
          "MEDIA: HTTP "
              + response.code()
              + ", "
              + bytes.length
              + " bytes received; type="
              + response.header("Content-Type")
              + "; range="
              + response.header("Content-Range"));
      StringBuilder signature = new StringBuilder();
      for (int i = 0; i < Math.min(16, bytes.length); i++)
        signature.append(String.format("%02x", bytes[i] & 255));
      System.out.println("MEDIA signature: " + signature);
      boolean webm =
          bytes.length >= 4
              && bytes[0] == 0x1a
              && bytes[1] == 0x45
              && (bytes[2] & 255) == 0xdf
              && (bytes[3] & 255) == 0xa3;
      boolean mp4 =
          bytes.length >= 8
              && new String(bytes, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp");
      if (bytes.length < 4096 || (!webm && !mp4)) {
        if (response.header("Content-Type", "").startsWith("text/"))
          System.out.println(
              "Non-media response: "
                  + new String(
                          bytes,
                          0,
                          Math.min(500, bytes.length),
                          java.nio.charset.StandardCharsets.UTF_8)
                      .replaceAll("https?://[^\\s<]+", "[address]"));
        throw new IllegalStateException(
            "Response did not contain enough recognizable audio-container data. Playback is not"
                + " verified.");
      }
    }
    System.out.println(
        "Host checks passed. Actual Android decoding, screen-off and Bluetooth still require device"
            + " testing.");
    System.exit(0);
  }
}
