package app.cadence.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** Query understanding and deterministic ranking, independent of network sources and Android UI. */
public final class SearchRanker {
  public enum Type {
    SONG("Songs"), ARTIST("Artists"), ALBUM("Albums"), PLAYLIST("Playlists"), PODCAST("Podcasts");
    public final String label;
    Type(String label) { this.label=label; }
    public static Type from(String value) {
      String key=normalize(value);
      if(key.startsWith("artist"))return ARTIST;if(key.startsWith("album"))return ALBUM;
      if(key.startsWith("playlist"))return PLAYLIST;if(key.startsWith("podcast")||key.startsWith("episode")||key.startsWith("show"))return PODCAST;
      return SONG;
    }
  }

  public record Query(String original,String normalized,List<String> tokens,Set<String> languages,Set<Type> intents) {
    public String remote(boolean relaxed) {
      List<String> words=new ArrayList<>(tokens);if(!relaxed)words.addAll(languages);
      String value=String.join(" ",words).trim();return value.isEmpty()?normalized:value;
    }
  }

  public record Document(String id,Type type,String title,String artist,String album,String composer,
      String description,String language,long popularity,boolean liked,boolean downloaded,
      boolean recent,boolean subscribed) {
    public Document {
      id=Objects.requireNonNullElse(id,"");title=Objects.requireNonNullElse(title,"");
      artist=Objects.requireNonNullElse(artist,"");album=Objects.requireNonNullElse(album,"");
      composer=Objects.requireNonNullElse(composer,"");description=Objects.requireNonNullElse(description,"");
      language=normalize(language);type=type==null?Type.SONG:type;
    }
  }

  public record Ranked(Document document,double score,boolean strict) {}
  private static final Set<String> SONG_INTENT=Set.of("song","songs","music");
  private static final Set<String> ALBUM_INTENT=Set.of("album","albums");
  private static final Set<String> ARTIST_INTENT=Set.of("artist","singer");
  private static final Set<String> PLAYLIST_INTENT=Set.of("playlist","playlists");
  private static final Set<String> PODCAST_INTENT=Set.of("podcast","podcasts","episode","episodes","show","shows");
  private static final Set<String> LANGUAGE=Set.of("tamil","telugu","malayalam","hindi","kannada","english");
  private static final Set<String> FILLER=Set.of("best","top","hits","new","latest","old","intro","bgm","mass","video");
  private static final Map<String,String> ALIASES=aliases();

  public static Query parse(String raw) {
    String normalized=normalize(raw);LinkedHashSet<String> tokens=new LinkedHashSet<>(),languages=new LinkedHashSet<>();
    EnumSet<Type> intents=EnumSet.noneOf(Type.class);
    for(String word:words(normalized)) {
      String canonical=canonical(word);
      if(SONG_INTENT.contains(canonical)){intents.add(Type.SONG);continue;}
      if(ALBUM_INTENT.contains(canonical)){intents.add(Type.ALBUM);continue;}
      if(ARTIST_INTENT.contains(canonical)){intents.add(Type.ARTIST);continue;}
      if(PLAYLIST_INTENT.contains(canonical)){intents.add(Type.PLAYLIST);continue;}
      if(PODCAST_INTENT.contains(canonical)){intents.add(Type.PODCAST);continue;}
      if(LANGUAGE.contains(canonical)){languages.add(canonical);continue;}
      if(!FILLER.contains(canonical))tokens.add(canonical);
    }
    return new Query(Objects.requireNonNullElse(raw,""),normalized,List.copyOf(tokens),Set.copyOf(languages),Set.copyOf(intents));
  }

  public static List<Ranked> rank(Query query,Collection<Document> candidates,boolean allTab) {
    if(query.normalized.isEmpty() || query.tokens.size()==1 && query.tokens.get(0).length()<2
        && query.intents.isEmpty() && query.languages.isEmpty())return List.of();
    List<Ranked> values=new ArrayList<>();boolean anyStrict=false;
    for(Document document:candidates) {
      Ranked ranked=score(query,document);if(ranked.strict)anyStrict=true;values.add(ranked);
    }
    boolean strictExists=anyStrict;
    values.removeIf(value -> {
      if(query.tokens.isEmpty())return false;
      if(strictExists&&!value.strict)return true;
      double minimum=allTab?24:10;
      return value.score<minimum;
    });
    values.sort(Comparator.comparingDouble(Ranked::score).reversed()
        .thenComparing(Comparator.comparingLong((Ranked r)->r.document.popularity).reversed())
        .thenComparing(r->normalize(r.document.title)).thenComparing(r->r.document.id));
    return values;
  }

  private static Ranked score(Query query,Document d) {
    String title=canonicalText(d.title),artist=canonicalText(d.artist),album=canonicalText(d.album),
        composer=canonicalText(d.composer),description=canonicalText(d.description);
    String main=d.type==Type.PODCAST?title:title;double score=base(d.type);
    if(!query.intents.isEmpty())score+=query.intents.contains(d.type)?90:-16;
    String phrase=String.join(" ",query.tokens);
    if(!phrase.isEmpty()) {
      if(main.equals(phrase))score+=120;else if(main.startsWith(phrase))score+=82;
      if(artist.equals(phrase))score+=105;else if(artist.startsWith(phrase))score+=72;
      if(album.equals(phrase))score+=88;else if(album.startsWith(phrase))score+=62;
      if(composer.equals(phrase))score+=82;else if(composer.startsWith(phrase))score+=58;
    }
    int matched=0;
    for(String token:query.tokens) {
      double best=Math.max(match(token,title,25),Math.max(match(token,artist,23),
          Math.max(match(token,album,19),Math.max(match(token,composer,18),match(token,description,7)))));
      if(best>0){matched++;score+=best;}
    }
    boolean strict=query.tokens.isEmpty()||matched==query.tokens.size();
    if(strict&&!query.tokens.isEmpty())score+=34;else if(matched>0)score+=matched*4;
    if(!query.languages.isEmpty()) {
      String language=canonical(d.language);score+=query.languages.contains(language)?16:language.isEmpty()?2:-8;
    }
    if(d.liked)score+=5;if(d.downloaded)score+=4;if(d.recent)score+=2;if(d.subscribed)score+=3;
    score+=Math.min(5,Math.log10(Math.max(1,d.popularity)));
    return new Ranked(d,score,strict);
  }

  private static double match(String token,String field,double weight) {
    if(field.isEmpty())return 0;List<String> fieldWords=words(field);
    if(fieldWords.contains(token))return weight;
    for(String word:fieldWords)if(word.startsWith(token)||token.startsWith(word))return weight*.78;
    for(String word:fieldWords)if(fuzzy(token,word))return weight*.52;
    return 0;
  }

  private static boolean fuzzy(String a,String b) {
    if(Math.min(a.length(),b.length())<4)return false;int allowed=Math.max(a.length(),b.length())>=8?2:1;
    if(Math.abs(a.length()-b.length())>allowed)return false;
    int[] previous=new int[b.length()+1],current=new int[b.length()+1];for(int j=0;j<=b.length();j++)previous[j]=j;
    for(int i=1;i<=a.length();i++){current[0]=i;int row=current[0];for(int j=1;j<=b.length();j++){
      current[j]=Math.min(Math.min(current[j-1]+1,previous[j]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));row=Math.min(row,current[j]);
    }if(row>allowed)return false;int[] swap=previous;previous=current;current=swap;}return previous[b.length()]<=allowed;
  }

  private static int base(Type type) {return switch(type){case SONG->14;case ARTIST->12;case ALBUM->10;case PLAYLIST->8;case PODCAST->6;};}
  private static List<String> words(String value) {return value.isBlank()?List.of():Arrays.asList(value.split(" "));}
  private static String canonicalText(String value) {
    List<String> result=new ArrayList<>();for(String word:words(normalize(value)))result.add(canonical(word));return String.join(" ",result);
  }
  private static String canonical(String word) {return ALIASES.getOrDefault(word,word);}

  public static String normalize(String value) {
    String text=Normalizer.normalize(Objects.requireNonNullElse(value,""),Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    return text.replaceAll("\\s+"," ");
  }

  private static Map<String,String> aliases() {
    Map<String,String> result=new HashMap<>();Properties properties=new Properties();
    try(InputStream stream=SearchRanker.class.getResourceAsStream("/search-aliases.properties")) {
      if(stream!=null)properties.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
    }catch(IOException ignored){}
    for(String canonical:properties.stringPropertyNames())for(String value:properties.getProperty(canonical,"").split(",")) {
      String key=normalize(value);if(!key.isEmpty())result.put(key,normalize(canonical));
    }
    return Collections.unmodifiableMap(result);
  }

  private SearchRanker() {}
}
