package app.cadence.music;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;

/** Equivalent URL codec calls available on every supported Android version. */
public final class UrlCodecCompat {
  private UrlCodecCompat() {}
  public static String decode(String value, Charset charset) {
    try { return URLDecoder.decode(value, charset.name()); }
    catch (UnsupportedEncodingException impossible) { throw new IllegalArgumentException(impossible); }
  }
  public static String encode(String value, Charset charset) {
    try { return URLEncoder.encode(value, charset.name()); }
    catch (UnsupportedEncodingException impossible) { throw new IllegalArgumentException(impossible); }
  }
}
