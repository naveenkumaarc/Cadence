package app.cadence.core;

import java.net.URI;
import java.net.URLDecoder;
import java.util.regex.Pattern;

public final class Links {
  private static final Pattern VIDEO = Pattern.compile("[A-Za-z0-9_-]{11}");
  private static final Pattern PLAYLIST = Pattern.compile("[A-Za-z0-9]{22}");

  private Links() {}

  public static String youtubeVideo(String input) {
    String value = input.trim();
    if (VIDEO.matcher(value).matches()) return "https://www.youtube.com/watch?v=" + value;
    URI uri = URI.create(value);
    String host = uri.getHost();
    if (host == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
      throw new IllegalArgumentException("Paste a YouTube video link.");
    String id = null;
    if (host.equalsIgnoreCase("youtu.be")) id = uri.getPath().substring(1);
    else if (host.equalsIgnoreCase("youtube.com")
        || host.equalsIgnoreCase("www.youtube.com")
        || host.equalsIgnoreCase("m.youtube.com")
        || host.equalsIgnoreCase("music.youtube.com")) {
      if (uri.getPath().equals("/watch")) id = parameter(uri.getRawQuery(), "v");
      else if (uri.getPath().startsWith("/shorts/") || uri.getPath().startsWith("/embed/"))
        id = uri.getPath().split("/").length > 2 ? uri.getPath().split("/")[2] : null;
    }
    if (id == null || !VIDEO.matcher(id).matches())
      throw new IllegalArgumentException("Not a YouTube video link.");
    return "https://www.youtube.com/watch?v=" + id;
  }

  public static String spotifyPlaylist(String input) {
    String value = input.trim();
    if (PLAYLIST.matcher(value).matches()) return value;
    if (value.startsWith("spotify:playlist:")) value = value.substring(17);
    else {
      URI uri = URI.create(value);
      if (!"https".equals(uri.getScheme()) || !"open.spotify.com".equalsIgnoreCase(uri.getHost()))
        throw new IllegalArgumentException("Paste an open.spotify.com playlist link.");
      String[] parts = uri.getPath().split("/");
      value = "";
      for (int i = 0; i + 1 < parts.length; i++)
        if (parts[i].equals("playlist")) value = parts[i + 1];
    }
    if (!PLAYLIST.matcher(value).matches())
      throw new IllegalArgumentException("Not a Spotify playlist link.");
    return value;
  }

  public static String parameter(String rawQuery, String key) {
    if (rawQuery == null) return null;
    for (String pair : rawQuery.split("&")) {
      String[] fields = pair.split("=", 2);
      if (decode(fields[0]).equals(key)) return fields.length == 2 ? decode(fields[1]) : "";
    }
    return null;
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (java.io.UnsupportedEncodingException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
