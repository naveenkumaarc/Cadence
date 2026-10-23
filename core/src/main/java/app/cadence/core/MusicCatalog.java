package app.cadence.core;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import okhttp3.*;

/** Unauthenticated YouTube Music catalogue. Only music metadata enters the UI. */
public final class MusicCatalog {
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  public static final String[] LANGUAGES = {"Tamil", "Telugu", "Malayalam", "English"};

  public static final class Entry {
    public String title = "", artist = "", artwork = "", browseId = "", kind = "playlist";
    public Track track;
  }

  public static final class Shelf {
    public String title = "";
    public final List<Entry> entries = new ArrayList<>();

    public Shelf(String title) {
      this.title = title;
    }
  }

  public static final class Home {
    public final List<Shelf> shelves = new ArrayList<>();
    public long fetchedAt = System.currentTimeMillis();
  }

  private static JsonObject request(String endpoint, JsonObject body) throws IOException {
    JsonObject client = new JsonObject();
    client.addProperty("clientName", "WEB_REMIX");
    client.addProperty("clientVersion", "1.20261019.01.00");
    client.addProperty("hl", "en");
    client.addProperty("gl", "IN");
    JsonObject context = new JsonObject();
    context.add("client", client);
    body.add("context", context);
    Request req =
        new Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/" + endpoint + "?prettyPrint=false")
            .header("User-Agent", HttpDownloader.USER_AGENT)
            .header("Origin", "https://music.youtube.com")
            .post(RequestBody.create(body.toString(), JSON))
            .build();
    try (Response r = HttpDownloader.CLIENT.newCall(req).execute()) {
      if (!r.isSuccessful() || r.body() == null)
        throw new IOException("Music catalogue unavailable (" + r.code() + "). Try again shortly.");
      try {
        return JsonParser.parseString(r.body().string()).getAsJsonObject();
      } catch (Exception e) {
        throw new IOException("The music catalogue returned an unexpected response.", e);
      }
    }
  }

  public static String serialize(Home h) {
    return new Gson().toJson(h);
  }

  public static Home deserialize(String s) {
    return new Gson().fromJson(s, Home.class);
  }

  public static Home home() throws IOException {
    Home home = charts("IN");
    for (Shelf shelf : home.shelves)
      if (shelf.title.startsWith("Trending now") && !shelf.entries.isEmpty()) {
        Entry chart =
            shelf.entries.stream()
                .filter(e -> e.title.toLowerCase(Locale.ROOT).contains("trending"))
                .findFirst()
                .orElse(shelf.entries.get(0));
        List<Entry> songs = new ArrayList<>();
        for (Entry entry : browse(chart.browseId)) if (entry.track != null) songs.add(entry);
        enrichTrending(songs);
        if (!songs.isEmpty()) {
          shelf.entries.clear();
          shelf.entries.addAll(songs);
        }
      }
    // The home surface shows music artwork rather than video-chart promotional covers.
    home.shelves.removeIf(shelf -> shelf.title.equals("Your languages"));
    return home;
  }

  private static void enrichTrending(List<Entry> entries) {
    var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    List<java.util.concurrent.Future<Entry>> pending = new ArrayList<>();
    int count = Math.min(16, entries.size());
    for (int i = 0; i < count; i++) {
      Entry original = entries.get(i);
      String rawTitle = original.title;
      original.title = MusicText.videoTitle(original.title, original.artist);
      original.track.title = original.title;
      pending.add(
          pool.submit(
              () -> {
                try {
                  List<Track> candidates = songs(original.title + " " + original.artist);
                  Track wanted =
                      new Track(rawTitle, original.artist, "", "", original.track.durationSeconds);
                  MatchRanker.sort(wanted, candidates);
                  String raw = normalize(rawTitle);
                  for (Track candidate : candidates) {
                    String name = normalize(candidate.title);
                    if (name.length() < 4 || !raw.contains(name)) continue;
                    if (original.track.durationSeconds > 0
                        && candidate.durationSeconds > 0
                        && Math.abs(original.track.durationSeconds - candidate.durationSeconds)
                            > 45) continue;
                    String lowered = candidate.title.toLowerCase(Locale.ROOT);
                    boolean changedVersion = false;
                    for (String modifier :
                        List.of("cover", "karaoke", "remix", "sped up", "slowed", "live"))
                      if (lowered.contains(modifier)
                          && !rawTitle.toLowerCase(Locale.ROOT).contains(modifier))
                        changedVersion = true;
                    if (changedVersion) continue;
                    Entry music = new Entry();
                    music.title = candidate.title;
                    music.artist = candidate.artist;
                    music.artwork = candidate.artwork;
                    music.kind = "song";
                    music.track = candidate;
                    return music;
                  }
                } catch (Exception ignored) {
                }
                return original;
              }));
    }
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
    for (int i = 0; i < pending.size(); i++)
      try {
        long remaining = deadline - System.nanoTime();
        if (remaining > 0)
          entries.set(i, pending.get(i).get(remaining, java.util.concurrent.TimeUnit.NANOSECONDS));
      } catch (Exception ignored) {
      }
    pool.shutdownNow();
  }

  private static String normalize(String value) {
    return java.text.Normalizer.normalize(
            value.replaceAll("(?i)\\(from\\s+[^)]*\\)", ""), java.text.Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]", "");
  }

  private static Home charts(String country) throws IOException {
    JsonObject b = new JsonObject();
    b.addProperty("browseId", "FEmusic_charts");
    JsonObject form = new JsonObject();
    JsonArray selected = new JsonArray();
    selected.add(country);
    form.add("selectedValues", selected);
    b.add("formData", form);
    Home home = parseCharts(request("browse", b), country.equals("IN") ? "India" : "United States");
    if (home.shelves.isEmpty())
      throw new IOException("Music charts could not be read. Pull to refresh later.");
    return home;
  }

  public static Home parseCharts(JsonObject root) {
    return parseCharts(root, "India");
  }

  private static Home parseCharts(JsonObject root, String country) {
    Home h = new Home();
    Shelf languages = new Shelf("Your languages");
    for (JsonObject c : objects(root, "musicCarouselShelfRenderer")) {
      String title = text(find(c.get("header"), "title"));
      Shelf s =
          new Shelf(
              title.equals("Video charts")
                  ? "Trending now · " + country
                  : title.equals("Top artists") ? "Top artists · " + country : title);
      for (JsonElement element : array(c, "contents")) {
        Entry e = parseEntry(element);
        if (e == null) continue;
        if (title.equals("Languages")) {
          for (String language : LANGUAGES)
            if (e.title.toLowerCase(Locale.ROOT).contains(language.toLowerCase(Locale.ROOT)))
              languages.entries.add(e);
        } else s.entries.add(e);
      }
      if (!s.entries.isEmpty() && !title.equals("Languages")) h.shelves.add(s);
    }
    if (!languages.entries.isEmpty()) {
      languages.entries.sort(
          Comparator.comparingInt(
              e -> {
                for (int i = 0; i < LANGUAGES.length; i++)
                  if (e.title.contains(LANGUAGES[i])) return i;
                return 9;
              }));
      h.shelves.add(Math.min(1, h.shelves.size()), languages);
    }
    return h;
  }

  public static List<Entry> search(String query, String filter) throws IOException {
    JsonObject b = new JsonObject();
    b.addProperty("query", query);

    String params =
        switch (filter) {
          case "Albums" -> "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D";
          case "Playlists" -> "EgWKAQIoAWoKEAkQChAFEAMQBA%3D%3D";
          case "Artists" -> "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D";
          default -> "EgWKAQIIAWoKEAkQChAFEAMQBA%3D%3D";
        };
    b.addProperty("params", params);
    return parseEntries(request("search", b));
  }

  /** Search across songs and albums without mixing video descriptions into music results. */
  public static List<Entry> searchAll(String query) throws IOException {
    List<Entry> out = new ArrayList<>(search(query, "Songs"));
    out.addAll(search(query, "Albums"));
    return out;
  }

  public static List<String> suggestions(String query) throws IOException {
    JsonObject body = new JsonObject();
    body.addProperty("input", query);
    JsonObject root = request("music/get_search_suggestions", body);
    LinkedHashSet<String> results = new LinkedHashSet<>();
    for (JsonObject o : objects(root, "searchSuggestionRenderer")) {
      String suggestion = text(o.get("suggestion"));
      if (!suggestion.isBlank()) results.add(suggestion);
    }
    return new ArrayList<>(results).subList(0, Math.min(5, results.size()));
  }

  /** Fetch every continuation; a failed or repeated page never becomes a silently partial import. */
  public static List<Entry> browseAll(String id) throws IOException {
    if (!id.matches("[A-Za-z0-9_-]{2,160}")) throw new IOException("Invalid music collection.");
    JsonObject b = new JsonObject(); b.addProperty("browseId", id);
    JsonObject root = request("browse", b);
    List<Entry> out = new ArrayList<>(parseEntries(root));
    Set<String> tokens = new HashSet<>();
    String continuation = collectionContinuation(root);
    while (!continuation.isEmpty()) {
      if (!tokens.add(continuation) || tokens.size() > 200 || out.size() > 20000)
        throw new IOException("This playlist is too large to import completely. Nothing was saved.");
      b = new JsonObject(); b.addProperty("continuation", continuation);
      root = request("browse", b);
      out.addAll(parseEntries(root));
      continuation = collectionContinuation(root);
    }
    return out;
  }

  static String collectionContinuation(JsonObject root) {
    // Artist recommendation carousels may also carry tokens: only advance the song shelf.
    for (String key : List.of("musicPlaylistShelfRenderer", "musicPlaylistShelfContinuation", "musicShelfContinuation"))
      for (JsonObject shelf : objects(root, key)) {
        String token = string(find(shelf.get("continuations"), "continuation"));
        if (!token.isEmpty()) return token;
        token = string(find(shelf.get("contents"), "token"));
        if (!token.isEmpty()) return token;
      }
    JsonElement actions = root.get("onResponseReceivedActions");
    String token = string(find(actions, "token"));
    return token;
  }

  /** YouTube Music radio, seeded by the exact song the user selected. */
  public static List<Track> related(String sourceUrl) throws IOException {
    String id = Links.parameter(java.net.URI.create(Links.youtubeVideo(sourceUrl)).getRawQuery(), "v");
    JsonObject b = new JsonObject();
    b.addProperty("videoId", id); b.addProperty("playlistId", "RDAMVM" + id);
    b.addProperty("isAudioOnly", true); b.addProperty("enablePersistentPlaylistPanel", true);
    b.addProperty("params", "wAEB"); b.addProperty("tunerSettingValue", "AUTOMIX_SETTING_NORMAL");
    return parseRadio(request("next", b));
  }

  public static List<Track> parseRadio(JsonObject root) {
    List<Track> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JsonObject o : objects(root, "playlistPanelVideoRenderer")) {
      String id = string(o.get("videoId"));
      if (!id.matches("[A-Za-z0-9_-]{11}") || !seen.add(id)) continue;
      String artist = text(o.get("longBylineText"));
      if (artist.isEmpty()) artist = text(o.get("shortBylineText"));
      artist = artist.split(" • ")[0];
      String title = MusicText.title(text(o.get("title")), artist);
      out.add(new Track(title, MusicText.artist(artist), "https://www.youtube.com/watch?v=" + id,
          artwork(o.get("thumbnail")), duration(text(o.get("lengthText")))));
    }
    return out;
  }

  public static Home languageHome(String language) throws IOException {
    Home h = new Home();
    List<Track> songs = picks(language);
    Shelf music = new Shelf(language + " picks");
    for (Track t : songs) {
      Entry e = new Entry(); e.title = t.title; e.artist = t.artist; e.artwork = t.artwork;
      e.track = t; e.kind = "song"; music.entries.add(e);
    }
    if (!music.entries.isEmpty()) h.shelves.add(music);
    Shelf albums = new Shelf("Albums · " + language);
    albums.entries.addAll(search(language + " latest albums", "Albums"));
    if (!albums.entries.isEmpty()) h.shelves.add(albums);
    Shelf artists = new Shelf("Artists · " + language);
    artists.entries.addAll(search(language + " music", "Artists"));
    if (!artists.entries.isEmpty()) h.shelves.add(artists);
    if (h.shelves.isEmpty()) throw new IOException("No music found for " + language);
    return h;
  }

  public static List<Track> picks(String language) throws IOException {
    if (!language.equals("English")) return songs(language + " latest songs");
    Home h = charts("US");
    for (Shelf shelf : h.shelves)
      if (shelf.title.startsWith("Top artists")) {
        List<Track> picks = new ArrayList<>();
        for (Entry artist : shelf.entries.subList(0, Math.min(2, shelf.entries.size()))) {
          List<Track> releases = songs(artist.title);
          for (Track track : releases.subList(0, Math.min(5, releases.size()))) picks.add(track);
        }
        if (!picks.isEmpty()) return picks;
      }
    return songs("English pop");
  }

  public static List<Track> songs(String query) throws IOException {
    List<Track> tracks = new ArrayList<>();
    for (Entry e : search(query, "Songs")) if (e.track != null) tracks.add(e.track);
    return tracks;
  }

  public static List<Entry> browse(String id) throws IOException {
    if (!id.matches("[A-Za-z0-9_-]{2,160}")) throw new IOException("Invalid music collection.");
    JsonObject b = new JsonObject();
    b.addProperty("browseId", id);
    return parseEntries(request("browse", b));
  }

  public static List<Entry> parseEntries(JsonObject root) {
    List<Entry> out = new ArrayList<>();
    collectEntries(root, out);
    return out;
  }

  private static void collectEntries(JsonElement value, List<Entry> out) {
    if (value == null || value.isJsonNull()) return;
    if (value.isJsonObject()) {
      JsonObject o = value.getAsJsonObject();
      if (o.has("musicResponsiveListItemRenderer") || o.has("musicTwoRowItemRenderer")) {
        Entry e = parseEntry(o);
        if (e != null) out.add(e);
        return;
      }
      // No menus, descriptions, recommendations hidden in player overlays, or ad contents.
      for (var kv : o.entrySet())
        if (!Set.of("menu", "overlay", "thumbnailOverlay", "description", "frameworkUpdates")
            .contains(kv.getKey())) collectEntries(kv.getValue(), out);
    } else if (value.isJsonArray())
      for (JsonElement e : value.getAsJsonArray()) collectEntries(e, out);
  }

  private static Entry parseEntry(JsonElement element) {
    if (element == null || !element.isJsonObject()) return null;
    JsonObject wrap = element.getAsJsonObject(), o;
    Entry e = new Entry();
    JsonArray artists = new JsonArray();
    String length = "";
    if (wrap.has("musicResponsiveListItemRenderer")) {
      o = wrap.getAsJsonObject("musicResponsiveListItemRenderer");
      JsonArray cols = array(o, "flexColumns");
      if (cols.isEmpty()) return null;
      e.title = text(find(cols.get(0), "text"));
      if (cols.size() > 1) {
        JsonElement runs = find(cols.get(1), "runs");
        if (runs != null && runs.isJsonArray()) artists = runs.getAsJsonArray();
      }
      length = text(find(o.get("fixedColumns"), "text"));
    } else if (wrap.has("musicTwoRowItemRenderer")) {
      o = wrap.getAsJsonObject("musicTwoRowItemRenderer");
      e.title = text(o.get("title"));
      JsonElement runs = find(o.get("subtitle"), "runs");
      if (runs != null && runs.isJsonArray()) artists = runs.getAsJsonArray();
    } else return null;
    if (e.title.isBlank()) return null;
    JsonElement nav = o.get("navigationEndpoint");
    if (nav == null) nav = find(o.get("title"), "navigationEndpoint");
    JsonElement browse = find(nav, "browseId");
    e.browseId = string(browse);
    String pageType = string(find(nav, "pageType"));
    e.kind =
        pageType.contains("ARTIST") ? "artist" : pageType.contains("ALBUM") ? "album" : "playlist";
    e.artwork = artwork(o.has("thumbnail") ? o.get("thumbnail") : o.get("thumbnailRenderer"));
    StringBuilder a = new StringBuilder();
    if (!artists.isEmpty()
        && Set.of("Song", "Album", "Single", "EP", "Artist", "Playlist")
            .contains(string(artists.get(0).getAsJsonObject().get("text")))) {
      JsonArray rest = new JsonArray();
      boolean separator = false;
      for (JsonElement run : artists) {
        String t = string(run.getAsJsonObject().get("text"));
        if (!separator) {
          if (t.contains("•")) separator = true;
          continue;
        }
        rest.add(run);
      }
      artists = rest;
    }
    for (JsonElement run : artists) {
      String part = string(run.getAsJsonObject().get("text"));
      if (part.contains("•") || part.matches("\\d{1,2}:\\d{2}.*")) break;
      a.append(part);
    }
    e.artist = a.toString().trim();
    if (e.artist.matches("(?i).*(subscribers|plays|views|monthly audience)$")
        || e.artist.equals("Chart")
        || e.artist.equals("Song")
        || e.artist.equals("Artist")
        || e.artist.equals("Album")) e.artist = "";
    String video = string(find(o.get("playlistItemData"), "videoId"));
    if (video.isEmpty()) video = string(find(o.get("flexColumns"), "videoId"));
    if (video.isEmpty() && e.browseId.isEmpty()) video = string(find(nav, "videoId"));
    if (video.matches("[A-Za-z0-9_-]{11}")) {
      e.kind = "song";
      long seconds = duration(length);
      if (seconds == 0)
        for (JsonElement run : artists) {
          String t = string(run.getAsJsonObject().get("text"));
          if (t.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) seconds = duration(t);
        }
      String videoType = string(find(o, "musicVideoType"));
      if (videoType.equals("MUSIC_VIDEO_TYPE_OMV") || videoType.equals("MUSIC_VIDEO_TYPE_UGC"))
        e.title = MusicText.videoTitle(e.title, e.artist);
      e.track =
          new Track(
              MusicText.title(e.title, e.artist),
              MusicText.artist(e.artist),
              "https://www.youtube.com/watch?v=" + video,
              e.artwork,
              seconds);
      e.title = e.track.title;
      e.artist = e.track.artist;
    } else if (e.browseId.isEmpty()) return null;
    return e;
  }

  public static long duration(String text) {
    try {
      long n = 0;
      for (String p : text.split(":")) n = n * 60 + Long.parseLong(p.trim());
      return n;
    } catch (Exception e) {
      return 0;
    }
  }

  private static String artwork(JsonElement root) {
    JsonElement t = find(root, "thumbnails");
    if (t == null || !t.isJsonArray() || t.getAsJsonArray().isEmpty()) return "";
    JsonArray a = t.getAsJsonArray();
    String url = string(a.get(a.size() - 1).getAsJsonObject().get("url"));
    if (url.contains("googleusercontent.com/"))
      url = url.replaceAll("=w[0-9]+-h[0-9]+", "=w544-h544");
    return url;
  }

  private static JsonArray array(JsonObject o, String key) {
    return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
  }

  private static String string(JsonElement e) {
    return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
  }

  private static String text(JsonElement e) {
    if (e == null) return "";
    if (e.isJsonPrimitive()) return string(e);
    if (!e.isJsonObject()) return "";
    JsonObject o = e.getAsJsonObject();
    if (o.has("simpleText")) return string(o.get("simpleText"));
    StringBuilder s = new StringBuilder();
    for (JsonElement r : array(o, "runs")) s.append(string(r.getAsJsonObject().get("text")));
    return s.toString();
  }

  private static JsonElement find(JsonElement e, String key) {
    if (e == null || e.isJsonNull()) return null;
    if (e.isJsonObject()) {
      JsonObject o = e.getAsJsonObject();
      if (o.has(key)) return o.get(key);
      for (var kv : o.entrySet()) {
        JsonElement v = find(kv.getValue(), key);
        if (v != null) return v;
      }
    } else if (e.isJsonArray())
      for (JsonElement x : e.getAsJsonArray()) {
        JsonElement v = find(x, key);
        if (v != null) return v;
      }
    return null;
  }

  private static List<JsonObject> objects(JsonElement e, String key) {
    List<JsonObject> out = new ArrayList<>();
    if (e == null) return out;
    if (e.isJsonObject()) {
      JsonObject o = e.getAsJsonObject();
      if (o.has(key) && o.get(key).isJsonObject()) out.add(o.getAsJsonObject(key));
      else for (var kv : o.entrySet()) out.addAll(objects(kv.getValue(), key));
    } else if (e.isJsonArray())
      for (JsonElement x : e.getAsJsonArray()) out.addAll(objects(x, key));
    return out;
  }
}
