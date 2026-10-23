package app.cadence.music;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class JsonDisk {
  static JSONObject read(Context c, String name) throws Exception {
    AtomicFile f = new AtomicFile(new File(c.getFilesDir(), name));
    return f.getBaseFile().exists() ? new JSONObject(new String(f.readFully(), StandardCharsets.UTF_8)) : new JSONObject();
  }
  static void write(Context c, String name, JSONObject json) throws Exception {
    AtomicFile f = new AtomicFile(new File(c.getFilesDir(), name)); FileOutputStream out = null;
    try { out = f.startWrite(); out.write(json.toString().getBytes(StandardCharsets.UTF_8)); f.finishWrite(out); }
    catch (Exception e) { if (out != null) f.failWrite(out); throw e; }
  }
  static String hash(String s) {
    try {
      StringBuilder out = new StringBuilder();
      for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)))
        out.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
      return out.toString();
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
}
