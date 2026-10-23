package app.cadence.core;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import okhttp3.*;
import org.w3c.dom.*;

/** Public RSS/Atom subscriptions and public podcast-directory metadata. */
public final class Podcasts {
  public static final class Show {
    public String title = "", author = "", artwork = "", feedUrl = "";
    public List<Episode> episodes = new ArrayList<>();
  }

  public static final class Episode {
    public String id = "", title = "", show = "", artwork = "", audioUrl = "", published = "";
    public long duration;

    public Track track() {
      Track t = new Track(title, show, audioUrl, artwork, duration);
      t.podcast = true;
      t.episodeId = id;
      return t;
    }
  }

  public static String https(String url) throws IOException {
    try {
      URI u = new URI(url.trim());
      if ("http".equalsIgnoreCase(u.getScheme()))
        u = new URI("https", u.getRawSchemeSpecificPart(), u.getRawFragment());
      if (!"https".equalsIgnoreCase(u.getScheme())
          || u.getHost() == null
          || u.getUserInfo() != null) throw new Exception();
      return u.toASCIIString();
    } catch (Exception e) {
      throw new IOException("Enter a valid HTTPS feed or audio address.");
    }
  }

  private static byte[] get(String url, int max) throws IOException {
    Request request =
        new Request.Builder()
            .url(https(url))
            .header("User-Agent", "Cadence/0.2 (Android podcast player)")
            .build();
    try (Response r = HttpDownloader.CLIENT.newCall(request).execute()) {
      if (!r.isSuccessful() || r.body() == null)
        throw new IOException("The source returned " + r.code() + ". Try again later.");
      return readLimited(r.body().byteStream(), max);
    }
  }

  public static byte[] readLimited(InputStream in, int max) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    while ((n = in.read(buffer)) != -1) {
      if (b.size() + n > max) throw new IOException("This file is too large to import.");
      b.write(buffer, 0, n);
    }
    return b.toByteArray();
  }

  public static Show feed(String url) throws Exception {
    return parseFeed(https(url), get(url, 10 * 1024 * 1024));
  }

  private static Document xml(byte[] bytes) throws Exception {
    // Android's XML provider does not implement every JAXP hardening feature.
    // Reject DTDs before parsing (including UTF-16/32 ASCII markup), and deny all entity
    // resolution.
    String markup =
        new String(bytes, StandardCharsets.ISO_8859_1)
            .replace("\u0000", "")
            .toUpperCase(Locale.ROOT);
    if (markup.contains("<!DOCTYPE") || markup.contains("<!ENTITY"))
      throw new IOException("Feeds with document type declarations are not supported.");
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setExpandEntityReferences(false);
    for (String feature :
        List.of(
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities"))
      try {
        f.setFeature(feature, false);
      } catch (javax.xml.parsers.ParserConfigurationException ignored) {
      }
    var b = f.newDocumentBuilder();
    b.setEntityResolver(
        (publicId, systemId) -> {
          throw new org.xml.sax.SAXException("External entities are disabled.");
        });
    b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
    return b.parse(new ByteArrayInputStream(bytes));
  }

  public static Show parseFeed(String url, byte[] bytes) throws Exception {
    Document doc = xml(bytes);
    Element root = doc.getDocumentElement();
    boolean atom = local(root).equals("feed");
    Element channel = atom ? root : child(root, "channel");
    if (channel == null) throw new IOException("This address is not a podcast RSS or Atom feed.");
    Show show = new Show();
    show.feedUrl = https(url);
    show.title = value(channel, "title");
    show.author = value(channel, "author");
    if (show.author.isEmpty()) show.author = value(channel, "managingEditor");
    show.artwork = image(channel, show.feedUrl);
    if (show.title.isBlank()) throw new IOException("The feed has no show title.");
    LinkedHashMap<String, Episode> unique = new LinkedHashMap<>();
    for (Element item : children(channel, atom ? "entry" : "item")) {
      Episode e = new Episode();
      String audio = "";
      for (Element enc : children(item, atom ? "link" : "enclosure")) {
        if (atom && !enc.getAttribute("rel").equals("enclosure")) continue;
        String type = enc.getAttribute("type"), candidate = enc.getAttribute(atom ? "href" : "url");
        if (!candidate.isEmpty()
            && (type.isEmpty()
                || type.startsWith("audio/")
                || type.equals("application/octet-stream"))) {
          audio = candidate;
          break;
        }
      }
      if (audio.isEmpty()) continue;
      try {
        e.audioUrl = https(new URI(show.feedUrl).resolve(audio).toString());
      } catch (Exception ignored) {
        continue;
      }
      e.title = plain(value(item, "title"));
      if (e.title.isBlank()) continue;
      e.id = value(item, atom ? "id" : "guid");
      if (e.id.isEmpty()) e.id = e.audioUrl;
      // Include feed identity so unrelated publishers can reuse a GUID safely.
      e.id = show.feedUrl + "#" + e.id;
      e.show = show.title;
      e.artwork = image(item, show.feedUrl);
      if (e.artwork.isEmpty()) e.artwork = show.artwork;
      e.duration = MusicCatalog.duration(value(item, "duration"));
      e.published = value(item, atom ? "published" : "pubDate");
      unique.putIfAbsent(e.id, e);
    }
    show.episodes.addAll(unique.values());
    return show;
  }

  public static List<String> opml(byte[] bytes) throws Exception {
    Document d = xml(bytes);
    if (!local(d.getDocumentElement()).equals("opml"))
      throw new IOException("Choose an OPML subscriptions file.");
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    NodeList nodes = d.getElementsByTagName("outline");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element e = (Element) nodes.item(i);
      String u = e.getAttribute("xmlUrl");
      if (u.isEmpty()) u = e.getAttribute("xmlurl");
      if (!u.isBlank()) urls.add(https(u));
    }
    if (urls.isEmpty())
      throw new IOException("No podcast subscriptions were found in this OPML file.");
    if (urls.size() > 250) throw new IOException("Import up to 250 feeds at a time.");
    return new ArrayList<>(urls);
  }

  public static List<Show> search(String term) throws Exception {
    HttpUrl u =
        HttpUrl.get("https://itunes.apple.com/search")
            .newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", "30")
            .addQueryParameter("term", term)
            .build();
    JsonObject json =
        JsonParser.parseString(
                new String(get(u.toString(), 2 * 1024 * 1024), StandardCharsets.UTF_8))
            .getAsJsonObject();
    List<Show> shows = new ArrayList<>();
    for (JsonElement x : json.getAsJsonArray("results")) {
      JsonObject j = x.getAsJsonObject();
      if (!j.has("feedUrl")) continue;
      Show s = new Show();
      s.title = str(j, "collectionName");
      s.author = str(j, "artistName");
      s.artwork = str(j, "artworkUrl600");
      s.feedUrl = https(str(j, "feedUrl"));
      shows.add(s);
    }
    return shows;
  }

  public static String spotifyShowTitle(String link) throws Exception {
    URI u = new URI(link.trim());
    if (!"open.spotify.com".equalsIgnoreCase(u.getHost()))
      throw new IOException("Paste a Spotify show link.");
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("(?:/intl-[a-zA-Z-]+)?/show/([A-Za-z0-9]{22})/?")
            .matcher(u.getPath());
    if (!m.matches())
      throw new IOException("Use a Spotify show link, rather than an episode or playlist link.");
    String html =
        new String(
            get("https://open.spotify.com/show/" + m.group(1), 2 * 1024 * 1024),
            StandardCharsets.UTF_8);
    return spotifyTitleHtml(html);
  }

  public static String spotifyTitleHtml(String html) throws IOException {
    var title = org.jsoup.Jsoup.parse(html).selectFirst("meta[property=og:title]");
    if (title == null || title.attr("content").isBlank())
      throw new IOException("Could not read this show. Search its name or paste its RSS link.");
    return title.attr("content").trim();
  }

  private static String str(JsonObject j, String key) {
    return j.has(key) && !j.get(key).isJsonNull() ? j.get(key).getAsString() : "";
  }

  private static String local(Node n) {
    return n.getLocalName() == null ? n.getNodeName() : n.getLocalName();
  }

  private static List<Element> children(Element p, String name) {
    List<Element> list = new ArrayList<>();
    for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling())
      if (n instanceof Element e && local(e).equals(name)) list.add(e);
    return list;
  }

  private static Element child(Element p, String name) {
    List<Element> list = children(p, name);
    return list.isEmpty() ? null : list.get(0);
  }

  private static String value(Element p, String name) {
    Element c = child(p, name);
    return c == null ? "" : c.getTextContent().trim();
  }

  private static String image(Element p, String base) {
    for (Element e : children(p, "image")) {
      String url = e.getAttribute("href");
      if (url.isEmpty()) url = value(e, "url");
      try {
        if (!url.isBlank()) return https(new URI(base).resolve(url).toString());
      } catch (Exception ignored) {
      }
    }
    return "";
  }

  private static String plain(String s) {
    return s.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
  }
}
