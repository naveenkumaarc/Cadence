package app.cadence.core;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** RFC4180-style quoted fields, including embedded newlines and doubled quotes. */
public final class PlaylistCsv {
  private PlaylistCsv() {}

  public static List<Track> read(Reader input) throws IOException {
    List<List<String>> rows = rows(input);
    if (rows.isEmpty()) throw new IOException("CSV is empty.");
    List<String> header = rows.get(0);
    int name = column(header, "trackname", "songname", "title", "name");
    int artist = column(header, "artistname", "artistnames", "artists", "artist");
    int duration = column(header, "durationms", "durationmilliseconds");
    int uri = column(header, "trackuri", "spotifyuri", "uri");
    if (name < 0 || artist < 0)
      throw new IOException("CSV needs Track Name and Artist Name(s) columns.");
    List<Track> result = new ArrayList<>();
    for (int i = 1; i < rows.size(); i++) {
      List<String> row = rows.get(i);
      String title = cell(row, name).trim();
      if (title.isEmpty()) continue;
      Track track = new Track(title, cell(row, artist).trim(), "", "", 0);
      track.query = track.searchQuery();
      track.matchState = "unmatched";
      String id = cell(row, uri);
      if (id.startsWith("spotify:track:")) track.spotifyId = id.substring(14);
      try {
        track.durationSeconds = Long.parseLong(cell(row, duration)) / 1000;
      } catch (NumberFormatException ignored) {
        /* duration is optional */
      }
      result.add(track);
    }
    if (result.isEmpty()) throw new IOException("No named songs found in this CSV.");
    return result;
  }

  private static int column(List<String> header, String... names) {
    for (int i = 0; i < header.size(); i++) {
      String normalized = header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
      for (String name : names) if (name.equals(normalized)) return i;
    }
    return -1;
  }

  private static String cell(List<String> row, int i) {
    return i >= 0 && i < row.size() ? row.get(i) : "";
  }

  static List<List<String>> rows(Reader input) throws IOException {
    PushbackReader reader = new PushbackReader(input, 1);
    List<List<String>> result = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false, atStart = true;
    int size = 0, c;
    while ((c = reader.read()) != -1) {
      if (++size > 10_000_000) throw new IOException("CSV is larger than 10 MB.");
      if (atStart && c == 0xfeff) {
        atStart = false;
        continue;
      }
      atStart = false;
      if (c == '"') {
        if (quoted) {
          int next = reader.read();
          if (next == '"') field.append('"');
          else {
            quoted = false;
            if (next != -1) reader.unread(next);
          }
        } else if (field.length() == 0) quoted = true;
        else field.append('"');
      } else if (!quoted && c == ',') {
        row.add(field.toString());
        field.setLength(0);
      } else if (!quoted && (c == '\n' || c == '\r')) {
        if (c == '\r') {
          int next = reader.read();
          if (next != '\n' && next != -1) reader.unread(next);
        }
        row.add(field.toString());
        result.add(row);
        row = new ArrayList<>();
        field.setLength(0);
      } else field.append((char) c);
    }
    if (quoted) throw new IOException("CSV has an unclosed quoted field.");
    if (field.length() > 0 || !row.isEmpty()) {
      row.add(field.toString());
      result.add(row);
    }
    return result;
  }
}
