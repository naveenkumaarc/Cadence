package app.cadence.music;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import app.cadence.core.Links;
import app.cadence.core.MatchRanker;
import app.cadence.core.MusicCatalog;
import app.cadence.core.PlaylistCsv;
import app.cadence.core.Podcasts;
import app.cadence.core.Track;
import app.cadence.core.YouTube;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
  private static final int BG = Color.rgb(16, 18, 20),
      CARD = Color.rgb(29, 32, 35),
      INK = Color.rgb(245, 247, 246),
      MUTED = Color.rgb(173, 180, 177);
  private final int accent = Color.rgb(159, 218, 187);
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final ExecutorService workers = Executors.newFixedThreadPool(4);
  private final ExecutorService discoveryWorkers = Executors.newFixedThreadPool(2);
  private LinearLayout root, body, mini;
  private TextView status, miniTitle, miniArtist;
  private Artwork miniArt, headerPhoto;
  private GoogleLogin googleLogin;
  private TextView syncStatus;
  private Button playlistDownload;
  private final java.util.Map<String, TransportButton> episodePlayButtons = new java.util.HashMap<>();
  private final java.util.Map<String, TextView> episodeProgress = new java.util.HashMap<>();
  private final java.util.Map<String, ProgressBar> episodeBars = new java.util.HashMap<>();
  private final java.util.Map<String, Podcasts.Episode> displayedEpisodes = new java.util.HashMap<>();
  private TransportButton miniPlay;
  private Library library;
  private SearchHistory searchHistory;
  private PodcastStore podcasts;
  private SongDownloads songDownloads;
  private final java.util.Map<DownloadBadge, Track> songBadges = new java.util.LinkedHashMap<>();
  private final java.util.Map<TextView, Track> songTimes = new java.util.LinkedHashMap<>();
  private final java.util.Set<String> durationRequests = new java.util.HashSet<>();
  private final java.util.Map<String, Long> knownDurations = new java.util.HashMap<>();
  private final ExecutorService durationWorkers = Executors.newSingleThreadExecutor();
  private MusicCatalog.Home homeCatalog;
  private boolean homeLoading = false;
  private String homeError = "", language = "All", searchFilter = "All";
  private final java.util.Map<String, List<Track>> languagePicks = new java.util.LinkedHashMap<>();
  private final java.util.Set<String> pickingLanguages = new java.util.HashSet<>();
  private List<MusicCatalog.Entry> catalogResults = new ArrayList<>();
  private Podcasts.Show shownShow;
  private LinearLayout navigation;
  private final java.util.Map<String, AppIcon> navIcons = new java.util.HashMap<>();
  private final java.util.Map<String, TextView> navLabels = new java.util.HashMap<>();
  private Dialog queueDialog;
  private LinearLayout queueBody;
  private ScrollView queueScroll;
  private AppIcon playerShuffle, playerRepeat;
  private LinearLayout discoveryBody, musicExtras, podcastExtras;
  private TransportButton playerBack, playerForward, playerPrevious, playerNext;
  private final java.util.Map<String, MusicCatalog.Home> languageHomes = new java.util.HashMap<>();
  private final java.util.Set<String> loadingHomes = new java.util.HashSet<>();
  private final java.util.Map<String, String> languageErrors = new java.util.HashMap<>();
  private java.util.concurrent.Future<?> suggestionTask;
  private final ExecutorService suggestions = Executors.newFixedThreadPool(2);
  private int suggestionGeneration;
  private Runnable pendingSuggestion;
  private String queueStamp = "";
  private android.content.BroadcastReceiver downloadsReceiver;
  private MediaController controller;
  private ListenableFuture<MediaController> controllerFuture;
  private List<Track> searchResults = new ArrayList<>();
  private Library.Playlist shownPlaylist;
  private String page = "Home", searchText = "", lastError = "No error recorded.";
  private boolean destroyed = false, resumed = false;
  private int searchGeneration = 0;
  private TextView playerTitle, playerArtist, playerTime, playerRemaining, playerContext, playerState;
  private LinearLayout playerRelated, playerRecovery;
  private String playerRelatedKey = "";
  private int relatedGeneration;
  private final java.util.Map<String, List<Track>> relatedCache = new java.util.LinkedHashMap<>();
  private boolean searchLoading;
  private String searchError = "";
  private LinearLayout searchResultsBody;
  private Artwork playerArt;
  private SeekBar playerSeek;
  private boolean playerSeeking;
  private Dialog playerDialog;
  private TransportButton playerPlay;
  private final Player.Listener listener =
      new Player.Listener() {
        @Override
        public void onEvents(Player player, Player.Events events) {
          updatePlayer();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
          lastError = error.getErrorCodeName() + ": " + error.getMessage();
          Throwable cause = error.getCause();
          int depth = 0;
          while (cause != null && depth++ < 4) {
            lastError += "\n" + cause.getClass().getSimpleName() + ": " + cause.getMessage();
            cause = cause.getCause();
          }
          lastError = lastError.replaceAll("https?://\\S+", "[address]");
          status.setText("Playback failed. Tap the player for Retry or Details.");
          updatePlayer();
        }
      };
  private int downloadTick = 0;
  private final Runnable ticker =
      new Runnable() {
        @Override
        public void run() {
          updatePlayer();
          if (++downloadTick % 4 == 0) refreshDownloadButtons();
          if (resumed) ui.postDelayed(this, 500);
        }
      };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Artwork.configure(this);
    googleLogin = new GoogleLogin(this, message -> new AlertDialog.Builder(this).setTitle("Google connection").setMessage(message).setPositiveButton("OK",null).show(), () -> { refreshProfilePhoto(); showProfile(); });
    CloudSync.onApplied = this::cloudApplied;
    try {
      library = new Library(this);
      searchHistory = new SearchHistory(this);
      podcasts = PodcastStore.get(this);
      songDownloads = SongDownloads.get(this);
      java.io.File cached = new java.io.File(getFilesDir(), "discovery.json");
      if (cached.isFile())
        try {
          homeCatalog =
              MusicCatalog.deserialize(
                  new String(
                      java.nio.file.Files.readAllBytes(cached.toPath()), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    } catch (Exception e) {
      new AlertDialog.Builder(this)
          .setTitle("Library could not be read")
          .setMessage("Your saved data has been left untouched. " + e.getMessage())
          .setCancelable(false)
          .setPositiveButton("Close", (d, w) -> finish())
          .show();
      return;
    }
    language = Preferences.get(this).getString("homeLanguage", "All");
    buildShell();
    showHome();
    handleShare(getIntent());
    getPreferences(MODE_PRIVATE).edit().remove("spotifyClientId").remove("accent").apply();
    downloadsReceiver =
        new android.content.BroadcastReceiver() {
          @Override
          public void onReceive(android.content.Context c, Intent i) {
            if (page.equals("Show") && shownShow != null) showPodcast(shownShow);
            else if (page.equals("Podcasts")) showPodcasts();
            refreshSongDownloads();
          }
        };
    androidx.core.content.ContextCompat.registerReceiver(
        this,
        downloadsReceiver,
        new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 4);
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (library == null) return;
    resumed = true;
    controllerFuture =
        new MediaController.Builder(
                this, new SessionToken(this, new ComponentName(this, PlaybackService.class)))
            .buildAsync();
    ListenableFuture<MediaController> pendingController = controllerFuture;
    pendingController.addListener(
        () -> {
          if (!resumed || destroyed) return;
          if (controllerFuture != pendingController) return;
          try {
            controller = pendingController.get();
            controller.addListener(listener);
            updatePlayer();
          } catch (Exception e) {
            status.setText("Player connection failed. Reopen the app.");
            lastError = e.toString();
          }
        },
        this::runOnUiThread);
    CloudSync.opened(this);
    ui.post(ticker);
  }

  @Override
  protected void onStop() {
    resumed = false;
    ui.removeCallbacks(ticker);
    if (controller != null) controller.removeListener(listener);
    if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
    controllerFuture = null;
    controller = null;
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    destroyed = true;
    CloudSync.onApplied = null;
    if (googleLogin != null) googleLogin.close();
    ui.removeCallbacksAndMessages(null);
    if (playerDialog != null) playerDialog.dismiss();
    if (queueDialog != null) queueDialog.dismiss();
    workers.shutdownNow();
    discoveryWorkers.shutdownNow();
    suggestions.shutdownNow();
    durationWorkers.shutdownNow();
    if (downloadsReceiver != null)
      try {
        unregisterReceiver(downloadsReceiver);
      } catch (Exception ignored) {
      }
    super.onDestroy();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (library != null) handleShare(intent);
  }

  @Override
  public void onBackPressed() {
    if (page.equals("Show")) navigate("Podcasts");
    else if (page.equals("Collection")) navigate("Home");
    else if (page.equals("Playlist")) navigate("Library");
    else if (!page.equals("Home")) navigate("Home");
    else super.onBackPressed();
  }

  private void buildShell() {
    root = column();
    root.setBackgroundColor(BG);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars =
                insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          } else
            v.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
          return insets;
        });
    LinearLayout header = row();
    header.setPadding(dp(20), dp(8), dp(12), dp(8));
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView brand = text("Cadence", 25, INK, true);
    brand.setPadding(0, 0, 0, 0);
    brand.setGravity(Gravity.CENTER_VERTICAL);
    brand.setIncludeFontPadding(false);
    header.addView(brand, new LinearLayout.LayoutParams(-2, dp(48)));
    android.widget.FrameLayout profileTouch = new android.widget.FrameLayout(this);
    profileTouch.setContentDescription("Open your profile"); profileTouch.setFocusable(true);
    profileTouch.setBackground(ripple(Color.TRANSPARENT,24)); profileTouch.setOnClickListener(v -> showProfile());
    headerPhoto = new Artwork(this); headerPhoto.circle(); headerPhoto.setContentDescription("Profile photo");
    android.widget.FrameLayout.LayoutParams avatarSize = new android.widget.FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER);
    profileTouch.addView(headerPhoto,avatarSize); header.addView(profileTouch,new LinearLayout.LayoutParams(dp(48),dp(48)));
    header.addView(new View(this),new LinearLayout.LayoutParams(0,1,1)); refreshProfilePhoto();
    header.addView(
        icon("queue", "Open queue", this::showQueue),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    header.addView(
        icon("settings", "Settings", this::settings),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    root.addView(header);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    body = column();
    body.setPadding(dp(20), dp(12), dp(20), dp(28));
    scroll.setVerticalScrollBarEnabled(false);
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    status = text("", 12, MUTED, false);
    status.setVisibility(View.GONE);
    root.addView(status);
    mini = row();
    mini.setPadding(dp(8), dp(7), dp(4), dp(7));
    mini.setBackground(round(Color.rgb(39, 48, 43), 18));
    miniArt = new Artwork(this);
    mini.addView(miniArt, new LinearLayout.LayoutParams(dp(44), dp(44)));
    LinearLayout info = column();
    info.setPadding(dp(11), 0, dp(6), 0);
    miniTitle = text("", 14, INK, true);
    miniTitle.setMaxLines(1);
    miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
    miniArtist = text("", 12, Color.rgb(205, 211, 207), false);
    miniArtist.setMaxLines(1);
    miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
    info.addView(miniTitle);
    info.addView(miniArtist);
    mini.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
    mini.setOnClickListener(v -> openPlayer());
    mini.addView(
        icon("queue", "Open queue", this::showQueue),
        new LinearLayout.LayoutParams(dp(44), dp(44)));
    miniPlay =
        new TransportButton(
            this, TransportButton.Icon.PLAY, INK, Color.TRANSPARENT, "Play or pause");
    miniPlay.setOnClickListener(v -> togglePlay());
    mini.addView(miniPlay, new LinearLayout.LayoutParams(dp(48), dp(48)));
    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
    mp.setMargins(dp(12), dp(4), dp(12), dp(4));
    root.addView(mini, mp);
    mini.setVisibility(View.GONE);
    navigation = row();
    navigation.setBackgroundColor(BG);
    navigation.setPadding(dp(4), dp(4), dp(4), dp(4));
    navIcons.clear();
    navLabels.clear();
    String[] names = {"Home", "Search", "Library", "Podcasts"},
        symbols = {"home", "search", "library", "podcasts"};
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      LinearLayout tab = column();
      tab.setGravity(Gravity.CENTER);
      AppIcon img = new AppIcon(this, symbols[i], MUTED, name);
      img.setFocusable(false);
      navIcons.put(name, img);
      tab.addView(img, new LinearLayout.LayoutParams(dp(40), dp(34)));
      TextView label = text(name.equals("Library") ? "Your Library" : name, 10, MUTED, false);
      label.setGravity(Gravity.CENTER);
      navLabels.put(name, label);
      tab.addView(label);
      tab.setContentDescription(name);
      tab.setOnClickListener(v -> navigate(name));
      navigation.addView(tab, new LinearLayout.LayoutParams(0, dp(64), 1));
    }
    root.addView(navigation);
    setContentView(root);
    root.requestApplyInsets();
  }

  private AppIcon icon(String name, String label, Runnable action) {
    AppIcon icon = new AppIcon(this, name, INK, label);
    icon.setOnClickListener(v -> action.run());
    icon.setBackground(ripple(Color.TRANSPARENT, 24));
    return icon;
  }

  private void navigate(String target) {
    searchGeneration++;
    shownPlaylist = null;
    switch (target) {
      case "Search" -> showSearch();
      case "Library" -> showLibrary();
      case "Import" -> showImport();
      case "Podcasts" -> showPodcasts();
      default -> showHome();
    }
  }

  private void clear(String name, String subtitle) {
    page = name;
    songBadges.clear(); songTimes.clear();
    episodePlayButtons.clear(); episodeProgress.clear(); episodeBars.clear(); displayedEpisodes.clear();
    playlistDownload = null; syncStatus = null;
    body.removeAllViews();
    String selected =
        name.equals("Playlist")
            ? "Library"
            : name.equals("Show") ? "Podcasts" : name.equals("Collection") ? "Home" : name;
    for (String key : navIcons.keySet()) {
      navIcons.get(key).colour(key.equals(selected) ? accent : MUTED);
      navLabels.get(key).setTextColor(key.equals(selected) ? accent : MUTED);
    }
  }

  private void showHome() {
    clear("Home", "");
    searchBox("Search songs, albums, artists or playlists", "", false);
    gap(14);
    LinearLayout filters = row();
    filters.addView(chip("Music", true, () -> {}));
    filters.addView(chip("Podcasts", false, () -> navigate("Podcasts")));
    body.addView(filters);
    gap(20);
    if (!library.playlists.isEmpty()) {
      android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
      scroll.setHorizontalScrollBarEnabled(false);
      LinearLayout cards = row();
      for (Library.Playlist p : library.playlists) {
        LinearLayout tile = column();
        Artwork art = new Artwork(this);
        if (!p.tracks.isEmpty()) art.load(p.tracks.get(0).artwork);
        tile.addView(art, new LinearLayout.LayoutParams(dp(102), dp(102)));
        TextView label = text(p.id.equals("favourites") ? "Liked Songs" : p.name, 13, INK, true);
        label.setMaxLines(2); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(0, dp(8), 0, 0); tile.addView(label);
        tile.setOnClickListener(v -> showPlaylist(p));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(110), -2);
        size.setMargins(0, 0, dp(12), 0); cards.addView(tile, size);
      }
      scroll.addView(cards); body.addView(scroll); gap(22);
    }
    android.widget.HorizontalScrollView langs = new android.widget.HorizontalScrollView(this);
    langs.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = row();
    List<String> languages = new ArrayList<>(); languages.add("All"); languages.addAll(Preferences.languages(this));
    if (!languages.contains(language)) language = "All";
    for (String l : languages) chips.addView(chip(l, language.equals(l), () -> {
      language = l; Preferences.get(this).edit().putString("homeLanguage", l).apply(); showHome();
    }));
    langs.addView(chips); body.addView(langs); gap(22);
    discoveryBody = column(); body.addView(discoveryBody);
    renderDiscovery();
  }

  private void renderDiscovery() {
    if (!page.equals("Home") || discoveryBody == null) return;
    LinearLayout outer = body; body = discoveryBody; body.removeAllViews();
    try {
      if (language.equals("All")) {
        if (homeCatalog != null) for (MusicCatalog.Shelf shelf : homeCatalog.shelves) shelf(shelf);
        else body.addView(text(homeError.isEmpty() ? "Loading discovery…" : homeError, 15, MUTED, false));
        loadHome(false);
        for (String l : Preferences.languages(this)) {
          MusicCatalog.Home h = languageHomes.get(l);
          if (h != null && !h.shelves.isEmpty()) shelf(h.shelves.get(0));
          loadLanguageHome(l);
        }
      } else {
        MusicCatalog.Home h = languageHomes.get(language);
        if (h != null) for (MusicCatalog.Shelf shelf : h.shelves) shelf(shelf);
        else body.addView(text(languageErrors.getOrDefault(language, "Loading " + language + " music…"), 15, MUTED, false));
        loadLanguageHome(language);
      }
      gap(12);
      body.addView(button("Refresh discovery", false, () -> {
        languageErrors.clear();
        if (language.equals("All")) { homeError = ""; loadHome(true); languageHomes.clear(); for (String l : Preferences.LANGUAGES) new java.io.File(getFilesDir(),"discovery-"+l+".json").delete(); }
        else { languageHomes.remove(language); new java.io.File(getFilesDir(),"discovery-"+language+".json").delete(); }
        renderDiscovery();
      }));
    } finally { body = outer; }
  }

  private void loadLanguageHome(String l) {
    if (languageHomes.containsKey(l) || loadingHomes.contains(l) || languageErrors.containsKey(l)) return;
    loadingHomes.add(l);
    discoveryWorkers.execute(() -> {
      MusicCatalog.Home value = null; String problem = "";
      try {
        java.io.File cache = new java.io.File(getFilesDir(), "discovery-" + l + ".json");
        if (cache.isFile() && System.currentTimeMillis() - cache.lastModified() < 3600_000)
          value = MusicCatalog.deserialize(new String(java.nio.file.Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8));
        else {
          value = MusicCatalog.languageHome(l);
          java.nio.file.Files.write(cache.toPath(),MusicCatalog.serialize(value).getBytes(StandardCharsets.UTF_8));
        }
      }
      catch (Exception e) { problem = "Couldn’t load " + l + " discovery. Check your connection and refresh."; }
      MusicCatalog.Home result = value; String error = problem;
      ui.post(() -> {
        if (destroyed) return;
        loadingHomes.remove(l);
        if (result != null) languageHomes.put(l, result); else languageErrors.put(l, error);
        renderDiscovery();
      });
    });
  }

  private void searchBox(String hint, String initial, boolean searchPage) {
    LinearLayout search = row();
    search.setBackground(round(Color.rgb(44, 48, 51), 28));
    AppIcon searchMark = new AppIcon(this, "search", MUTED, "Search");
    searchMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    search.addView(searchMark, new LinearLayout.LayoutParams(dp(44), dp(52)));
    EditText query = input(hint, initial);
    query.setBackgroundColor(Color.TRANSPARENT);
    query.setPadding(0, 0, dp(4), 0);
    query.setTextSize(15);
    query.setSingleLine(true); query.setSelectAllOnFocus(true);
    query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
    query.setContentDescription("Search music");
    search.addView(query, new LinearLayout.LayoutParams(0, dp(56), 1));
    AppIcon submitIcon = icon("arrow", "Submit search", () -> {});
    search.addView(submitIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));
    body.addView(search, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout panel = column(); body.addView(panel);
    Runnable submit = () -> {
      String value = query.getText().toString().trim(); if (value.isEmpty()) return;
      suggestionGeneration++; if (pendingSuggestion != null) ui.removeCallbacks(pendingSuggestion);
      panel.removeAllViews();
      searchText = value;
      ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
      if (value.startsWith("http")) openYouTubeLink(value);
      else { if (!searchPage) searchFilter = "All"; catalogResults.clear(); showSearch(); searchMusic(); }
    };
    submitIcon.setOnClickListener(v -> submit.run());
    query.setOnEditorActionListener((v,id,event) -> {
      if (id == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { submit.run(); return true; } return false;
    });
    query.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
      public void onTextChanged(CharSequence value,int start,int before,int count) {
        if (searchPage) {
          searchText = value.toString();
          searchGeneration++; searchLoading = false;
          catalogResults.clear(); searchError = "";
          if (searchResultsBody != null) searchResultsBody.setVisibility(View.GONE);
        }
        int generation = ++suggestionGeneration;
        if (pendingSuggestion != null) ui.removeCallbacks(pendingSuggestion);
        if (suggestionTask != null) suggestionTask.cancel(true);
        panel.removeAllViews(); String term = value.toString().trim();
        if (term.length() < 2 || term.startsWith("http")) {
          if (searchPage && term.isEmpty()) renderRecents(panel);
          return;
        }
        pendingSuggestion = () -> suggestionTask = suggestions.submit(() -> {
          List<String> words = new ArrayList<>(); List<Track> tracks = new ArrayList<>();
          try { words = MusicCatalog.suggestions(term); } catch (Exception ignored) {}
          if (Thread.currentThread().isInterrupted()) return;
          try { tracks = MusicCatalog.songs(term); } catch (Exception ignored) {}
          List<String> completions = words; List<Track> matches = tracks;
          ui.post(() -> {
            if (destroyed || generation != suggestionGeneration || !query.isAttachedToWindow()) return;
            panel.removeAllViews();
            for (String word : completions) {
              Button option = button(word, false, () -> { query.setText(word); query.setSelection(word.length()); submit.run(); });
              option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
              panel.addView(option, new LinearLayout.LayoutParams(-1, dp(44)));
            }
            for (Track track : matches.subList(0, Math.min(2, matches.size()))) {
              LinearLayout r = row(); r.setPadding(0, dp(6), 0, dp(6));
              Artwork art = new Artwork(MainActivity.this); art.load(track.artwork);
              r.addView(art, new LinearLayout.LayoutParams(dp(44), dp(44)));
              TextView t = text(track.title + "\n" + track.artist, 13, INK, false);
              t.setMaxLines(2); t.setPadding(dp(10),0,0,0); r.addView(t, new LinearLayout.LayoutParams(0,-2,1));
              r.setOnClickListener(v -> { searchHistory.remember(track); playRadio(track); }); panel.addView(r);
            }
          });
        });
        ui.postDelayed(pendingSuggestion, 350);
      }
      public void afterTextChanged(android.text.Editable s) {}
    });
  }

  private Button chip(String title, boolean selected, Runnable action) {
    Button b = button(title, selected, action);
    b.setTextSize(13);
    b.setBackground(ripple(selected ? accent : CARD, 24));
    b.setMinWidth(0); b.setMinimumWidth(0);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48));
    p.setMargins(0, 0, dp(8), 0);
    b.setLayoutParams(p);
    return b;
  }

  private void loadHome(boolean force) {
    if (homeLoading
        || (!force
            && homeCatalog != null
            && System.currentTimeMillis() - homeCatalog.fetchedAt < 3600000)
        || (!force && !homeError.isEmpty())) return;
    homeLoading = true;
    discoveryWorkers.execute(
        () -> {
          try {
            MusicCatalog.Home h = MusicCatalog.home();
            java.nio.file.Files.write(
                new java.io.File(getFilesDir(), "discovery.json").toPath(),
                MusicCatalog.serialize(h).getBytes(StandardCharsets.UTF_8));
            ui.post(
                () -> {
                  if (destroyed) return;
                  homeCatalog = h;
                  homeLoading = false;
                  homeError = "";
                  if (page.equals("Home")) renderDiscovery();
                });
          } catch (Exception e) {
            ui.post(
                () -> {
                  if (destroyed) return;
                  homeLoading = false;
                  homeError = "Connect to the internet, then refresh.";
                  if (page.equals("Home")) renderDiscovery();
                });
          }
        });
  }

  private void loadLanguage(String l) {
    if (languagePicks.containsKey(l) || pickingLanguages.contains(l)) return;
    pickingLanguages.add(l);
    discoveryWorkers.execute(
        () -> {
          try {
            List<Track> tracks = MusicCatalog.picks(l);
            ui.post(
                () -> {
                  if (destroyed) return;
                  languagePicks.put(l, tracks.subList(0, Math.min(10, tracks.size())));
                  pickingLanguages.remove(l);
                  if (page.equals("Home")) renderDiscovery();
                });
          } catch (Exception e) {
            ui.post(
                () -> {
                  if (destroyed) return;
                  pickingLanguages.remove(l);
                  languagePicks.put(l, new ArrayList<>());
                });
          }
        });
  }

  private void shelf(MusicCatalog.Shelf shelf) {
    if (shelf.entries.isEmpty()) return;
    body.addView(text(shelf.title, 23, INK, true));
    gap(14);
    android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setClipToPadding(false);
    LinearLayout cards = row();
    cards.setGravity(Gravity.TOP);
    List<Track> tracks = new ArrayList<>();
    for (MusicCatalog.Entry entry : shelf.entries) if (entry.track != null) tracks.add(entry.track);
    for (MusicCatalog.Entry entry : shelf.entries.subList(0, Math.min(16, shelf.entries.size()))) {
      LinearLayout card = column();
      Artwork art = new Artwork(this);
      if (entry.kind.equals("artist")) art.circle();
      art.load(entry.artwork);
      art.setContentDescription(entry.title + " artwork");
      card.addView(art, new LinearLayout.LayoutParams(dp(146), dp(146)));
      TextView title = text(entry.title, 14, INK, true);
      title.setMaxLines(2);
      title.setEllipsize(android.text.TextUtils.TruncateAt.END);
      title.setPadding(0, dp(8), 0, 0);
      card.addView(title);
      String sub =
          entry.kind.equals("artist")
              ? "Artist"
              : entry.track != null
                  ? entry.artist
                  : entry.kind.equals("album") ? entry.artist : "Playlist";
      TextView by = text(sub, 12, MUTED, false);
      by.setMaxLines(1);
      by.setEllipsize(android.text.TextUtils.TruncateAt.END);
      card.addView(by);
      card.setOnClickListener(
          v -> {
            if (entry.track != null) play(entry.track, tracks);
            else openCollection(entry);
          });
      card.setOnLongClickListener(
          v -> {
            if (entry.track != null) trackOptions(entry.track, null);
            return true;
          });
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(146), -2);
      p.setMargins(0, 0, dp(16), 0);
      cards.addView(card, p);
    }
    scroll.addView(cards);
    body.addView(scroll);
    gap(28);
  }

  private void openCollection(MusicCatalog.Entry entry) {
    searchHistory.remember(entry);
    clear("Collection", "");
    body.addView(text(entry.title, 30, INK, true));
    gap(12);
    body.addView(text("Loading music…", 14, MUTED, false));
    int generation = ++searchGeneration;
    work(
        "Loading collection…",
        () -> entry.kind.equals("artist") ? MusicCatalog.browse(entry.browseId) : MusicCatalog.browseAll(entry.browseId),
        items -> {
          if (generation != searchGeneration || !page.equals("Collection")) return;
          clear("Collection", "");
          Artwork art = new Artwork(this);
          art.load(entry.artwork);
          if (entry.kind.equals("artist")) art.circle();
          LinearLayout center = row();
          center.setGravity(Gravity.CENTER);
          center.addView(art, new LinearLayout.LayoutParams(dp(210), dp(210)));
          body.addView(center);
          gap(22);
          body.addView(text(entry.title, 30, INK, true));
          gap(6);
          body.addView(
              text(entry.kind.equals("artist") ? "Artist" : entry.artist, 14, MUTED, false));
          gap(18);
          List<Track> songs = new ArrayList<>();
          MusicCatalog.Shelf albums = new MusicCatalog.Shelf("Albums");
          for (MusicCatalog.Entry e : items)
            if (e.track != null) {
              if (e.track.artwork.isEmpty()) e.track.artwork = entry.artwork;
              if (e.track.artist.isEmpty()) e.track.artist = entry.artist;
              songs.add(e.track);
            } else if (e.kind.equals("album")) albums.entries.add(e);
          if (!songs.isEmpty()) {
            body.addView(
                button("Play", true, () -> play(songs.get(0), songs)),
                new LinearLayout.LayoutParams(-1, dp(50)));
            gap(8);
            body.addView(button("Shuffle play", false, () -> playPlaylist(songs, true)));
            if (!entry.kind.equals("artist")) body.addView(button("Add " + (entry.kind.equals("album") ? "album" : "playlist") + " to library", false, () -> {
              Library.Playlist saved = new Library.Playlist(entry.title);
              saved.origin = entry.browseId.startsWith("VL") ? "https://music.youtube.com/playlist?list=" + entry.browseId.substring(2) : "";
              saved.tracks.addAll(songs); library.playlists.add(saved); save(); toast("Saved " + songs.size() + " songs to your library");
            }));
            gap(18);
            appendCollectionTracks(songs, 0);
          }
          if (!albums.entries.isEmpty()) shelf(albums);
          if (songs.isEmpty() && albums.entries.isEmpty())
            body.addView(button("Find songs", true, () -> runMusicSearch(entry.title)));
        });
  }

  private void runMusicSearch(String query) {
    searchText = query;
    searchFilter = "All";
    catalogResults.clear();
    showSearch();
    searchMusic();
  }

  private void searchMusic() {
    if (searchText.isBlank()) return;
    int generation = ++searchGeneration;
    final String term = searchText, filter = searchFilter;
    searchLoading = true; searchError = ""; showSearch();
    workers.execute(() -> {
      List<MusicCatalog.Entry> found = new ArrayList<>(); String problem = "";
      try { found = filter.equals("All") ? MusicCatalog.searchAll(term) : MusicCatalog.search(term, filter); }
      catch (Exception e) { problem = "Couldn’t search right now. Check your connection and try again."; }
      List<MusicCatalog.Entry> result = found; String error = problem;
      ui.post(() -> {
        if (destroyed || generation != searchGeneration || !page.equals("Search")) return;
        searchLoading = false; searchError = error; catalogResults = result; showSearch();
      });
    });
  }

  private void showSearch() {
    clear("Search", "");
    searchBox("What do you want to listen to?", searchText, true);
    gap(16);
    android.widget.HorizontalScrollView sc = new android.widget.HorizontalScrollView(this);
    sc.setHorizontalScrollBarEnabled(false);
    LinearLayout filters = row();
    for (String filter : List.of("All", "Songs", "Albums", "Artists", "Playlists"))
      filters.addView(chip(filter, searchFilter.equals(filter), () -> {
        searchFilter = filter; catalogResults.clear(); searchError = "";
        showSearch(); searchMusic();
      }));
    sc.addView(filters); body.addView(sc); gap(24);
    searchResultsBody = column(); body.addView(searchResultsBody);
    LinearLayout outer = body; body = searchResultsBody;
    try {
      if (searchText.isBlank()) { renderRecents(body); return; }
      if (searchLoading) { body.addView(text("Searching…", 15, MUTED, false)); return; }
      if (!searchError.isEmpty()) {
        body.addView(text(searchError, 15, MUTED, false));
        body.addView(button("Try again", false, this::searchMusic)); return;
      }
      if (catalogResults.isEmpty()) {
        body.addView(text("Find your next favourite", 22, INK, true)); gap(8);
        body.addView(text("Search for a song, album or artist. Try a different spelling if nothing appears.", 15, MUTED, false));
        return;
      }
      if (searchFilter.equals("All")) {
        body.addView(text("Songs", 23, INK, true)); gap(10);
        int count = 0;
        for (MusicCatalog.Entry entry : catalogResults)
          if (entry.track != null && count++ < 5) searchEntryRow(body, entry, false);
        body.addView(button("See all songs", false, () -> { searchFilter = "Songs"; searchMusic(); })); gap(20);
        MusicCatalog.Shelf albums = new MusicCatalog.Shelf("Albums");
        for (MusicCatalog.Entry entry : catalogResults) if (entry.kind.equals("album")) albums.entries.add(entry);
        shelf(albums);
      } else {
        body.addView(text(searchFilter, 23, INK, true)); gap(10);
        for (MusicCatalog.Entry entry : catalogResults) searchEntryRow(body, entry, false);
      }
    } finally { body = outer; }
  }

  private void renderRecents(LinearLayout target) {
    target.removeAllViews();
    LinearLayout heading = row();
    heading.setPadding(0, dp(12), 0, dp(14));
    heading.addView(text("Recents", 25, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    List<MusicCatalog.Entry> recent = searchHistory.entries();
    if (!recent.isEmpty()) heading.addView(button("Clear", false, () ->
        new AlertDialog.Builder(this).setTitle("Clear recent searches?")
            .setMessage("Your saved songs and playlists will stay in your library.")
            .setNegativeButton("Cancel", null).setPositiveButton("Clear", (d,n) -> {
              searchHistory.clear(); renderRecents(target);
            }).show()), new LinearLayout.LayoutParams(-2, dp(48)));
    target.addView(heading);
    for (MusicCatalog.Entry entry : recent) searchEntryRow(target, entry, true);
    if (recent.isEmpty()) {
      TextView empty = text("Your recent songs, albums and artists will appear here.", 15, MUTED, false);
      empty.setPadding(0, dp(4), 0, dp(20)); target.addView(empty);
    }
  }

  private void searchEntryRow(LinearLayout target, MusicCatalog.Entry entry, boolean recent) {
    LinearLayout item = row(); item.setPadding(0, dp(9), 0, dp(9));
    item.setBackground(ripple(Color.TRANSPARENT, 12));
    Artwork art = new Artwork(this); if (entry.kind.equals("artist")) art.circle();
    art.load(entry.artwork); item.addView(art, new LinearLayout.LayoutParams(dp(54), dp(54)));
    LinearLayout labels = column(); labels.setPadding(dp(12), 0, dp(4), 0);
    TextView title = text(entry.title, 16, INK, false); title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(title);
    String kind = entry.kind.equals("artist") ? "Artist" : entry.kind.equals("album") ? "Album" : entry.track != null ? "Song" : "Playlist";
    TextView detail = text(kind + (entry.artist.isEmpty() ? "" : " · " + entry.artist), 13, MUTED, false);
    detail.setSingleLine(true); detail.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(detail);
    item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (entry.track != null) item.addView(icon("save", "Save " + entry.title, () -> choosePlaylist(entry.track)), new LinearLayout.LayoutParams(dp(48), dp(48)));
    if (recent) item.addView(icon("close", "Remove " + entry.title + " from recents", () -> {
      searchHistory.remove(entry); target.removeView(item);
      if (searchHistory.entries().isEmpty()) renderRecents(target);
    }), new LinearLayout.LayoutParams(dp(48), dp(48)));
    else item.addView(icon("more", "Options for " + entry.title, () -> {
      if (entry.track != null) trackOptions(entry.track, null); else openCollection(entry);
    }), new LinearLayout.LayoutParams(dp(48), dp(48)));
    item.setOnClickListener(v -> { searchHistory.remember(entry);
      if (entry.track != null) playRadio(entry.track); else openCollection(entry);
    });
    target.addView(item);
  }

  private void showLibrary() {
    shownPlaylist = null;
    clear("Library", "Keep the good ones");
    body.addView(text("Your library", 32, INK, true));
    gap(14);
    body.addView(
        button(
            "Create playlist",
            true,
            () ->
                ask(
                    "New playlist",
                    "Playlist name",
                    "",
                    name -> {
                      library.playlists.add(new Library.Playlist(name));
                      save();
                      showLibrary();
                    })),
        new LinearLayout.LayoutParams(-1, dp(50)));
    gap(12);
    body.addView(button("Import from file", false, this::showImport));
    gap(18);
    playlistCard(songDownloads.playlist());
    try { Library.Playlist saved = DownloadList.playlist(this); if(!saved.tracks.isEmpty()) playlistCard(saved); } catch(Exception ignored) {}
    for (Library.Playlist playlist : library.playlists) playlistCard(playlist);
  }

  private void playlistCard(Library.Playlist playlist) {
    LinearLayout card = row();
    card.setPadding(dp(14), dp(12), dp(12), dp(12));
    card.setBackground(round(CARD, 14));
    Artwork art = new Artwork(this);
    if (!playlist.tracks.isEmpty()) art.load(playlist.tracks.get(0).artwork);
    card.addView(art, new LinearLayout.LayoutParams(dp(58), dp(58)));
    LinearLayout labels = column();
    labels.setPadding(dp(14), 0, 0, 0);
    labels.addView(
        text(playlist.id.equals("favourites") ? "Liked Songs" : playlist.name, 17, INK, true));
    long unmatched = playlist.tracks.stream().filter(t -> t.sourceUrl.isEmpty()).count();
    labels.addView(
        text(
            playlist.tracks.size()
                + " songs"
                + (unmatched > 0 ? " · " + unmatched + " to match" : ""),
            12,
            MUTED,
            false));
    card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    card.setOnClickListener(v -> showPlaylist(playlist));
    card.setOnLongClickListener(
        v -> {
          if (!playlist.id.equals("favourites") && !playlist.id.equals(SongDownloads.PLAYLIST_ID) && !playlist.id.equals(DownloadList.PLAYLIST_ID))
            new AlertDialog.Builder(this)
                .setTitle(playlist.name)
                .setItems(
                    new String[] {"Rename", "Delete playlist"},
                    (d, n) -> {
                      if (n == 0)
                        ask(
                            "Rename playlist",
                            "Name",
                            playlist.name,
                            name -> {
                              playlist.name = name;
                              save();
                              showLibrary();
                            });
                      else
                        new AlertDialog.Builder(this)
                            .setMessage("Delete this playlist from Cadence?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton(
                                "Delete",
                                (dd, nn) -> {
                                  library.playlists.remove(playlist);
                                  save();
                                  showLibrary();
                                })
                            .show();
                    })
                .show();
          return true;
        });
    body.addView(card, new LinearLayout.LayoutParams(-1, -2));
    gap(10);
  }

  private void showPlaylist(Library.Playlist requested) {
    Library.Playlist playlist = resolvePlaylist(requested);
    shownPlaylist = playlist;
    clear("Playlist", playlist.origin.isEmpty() ? "Your collection" : "Imported playlist");
    body.addView(text(playlist.name, 32, INK, true));
    gap(8);
    body.addView(text(playlist.tracks.size() + " songs", 13, MUTED, false));
    gap(16);
    if (!playlist.origin.isEmpty()) {
      body.addView(
          button(
              "View source playlist",
              false,
              () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(playlist.origin)))));
      body.addView(
          text(
              playlist.tracks.stream().anyMatch(t -> t.sourceUrl.isEmpty()) ? "Tap an unmatched song to choose its version." : "",
              13,
              MUTED,
              false));
      gap(12);
    }
    LinearLayout playActions = row();
    Library.Playlist displayed = playlist;
    playActions.addView(button("Play all", true, () -> playPlaylist(displayed.tracks, false)), new LinearLayout.LayoutParams(0, dp(50), 1));
    playActions.addView(button("Shuffle play", false, () -> playPlaylist(displayed.tracks, true)), new LinearLayout.LayoutParams(0, dp(50), 1));
    body.addView(playActions);
    gap(10);
    playlistDownload=button("Download all",false,()->playlistDownloadAction(displayed));
    playlistDownload.setContentDescription("Download all songs in " + displayed.name);
    body.addView(playlistDownload,new LinearLayout.LayoutParams(-1,dp(48))); refreshPlaylistDownload();
    gap(18);
    appendPlaylistTracks(playlist, 0);
    if (playlist.tracks.isEmpty())
      body.addView(text(playlist.id.equals(SongDownloads.PLAYLIST_ID)
          ? "Choose Download song from a song’s options. Completed songs appear here for offline listening."
          : "Save songs here from search, or import a playlist.", 14, MUTED, false));
  }

  private Library.Playlist resolvePlaylist(Library.Playlist requested) {
    if(requested.id.equals(SongDownloads.PLAYLIST_ID)) return songDownloads.playlist();
    if(requested.id.equals(DownloadList.PLAYLIST_ID)) try { return DownloadList.playlist(this); } catch(Exception ignored) {}
    return requested;
  }

  private void appendCollectionTracks(List<Track> tracks,int offset) {
    int end=Math.min(offset+50,tracks.size());
    for(int i=offset;i<end;i++) trackRow(tracks.get(i),tracks,null);
    if(end<tracks.size()) {
      Button more=button("More songs",false,()->{});
      more.setOnClickListener(v->{body.removeView(more);appendCollectionTracks(tracks,end);}); body.addView(more);
    }
  }

  private void appendPlaylistTracks(Library.Playlist playlist, int offset) {
    int end=Math.min(offset+50,playlist.tracks.size());
    for(int i=offset;i<end;i++) trackRow(playlist.tracks.get(i),playlist.tracks,playlist);
    if(end<playlist.tracks.size()) {
      Button more=button("More songs",false,()->{});
      more.setOnClickListener(v->{body.removeView(more);appendPlaylistTracks(playlist,end);}); body.addView(more);
    }
  }

  private void trackRow(Track track, List<Track> list, Library.Playlist playlist) {
    LinearLayout row = row();
    row.setPadding(0, dp(9), 0, dp(9));
    Artwork art = new Artwork(this);
    art.load(track.artwork);
    row.addView(art, new LinearLayout.LayoutParams(dp(56), dp(56)));
    LinearLayout labels = column();
    labels.setPadding(dp(12), 0, dp(6), 0);
    TextView title = text(track.title, 15, INK, true);
    title.setMaxLines(1);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    labels.addView(title);
    TextView artist =
        text(
            track.artist + (track.sourceUrl.isEmpty() ? " · Match song" : ""),
            12,
            track.sourceUrl.isEmpty() ? accent : MUTED,
            false);
    artist.setMaxLines(1);
    artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
    labels.addView(artist);
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (!track.podcast && !track.sourceUrl.isEmpty()) {
      DownloadBadge badge = new DownloadBadge(this);
      badge.setContentDescription("Download song " + track.title);
      badge.setOnClickListener(v -> songDownloadAction(track, playlist));
      row.addView(badge, new LinearLayout.LayoutParams(dp(32), dp(48)));
      songBadges.put(badge, track); updateSongBadge(badge, track);
    }
    TextView time = text(duration(track.durationSeconds), 11, MUTED, false);
    time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    row.addView(time, new LinearLayout.LayoutParams(dp(52), dp(48)));
    songTimes.put(time, track); requestSongDuration(track);
    Button more = button("•••", false, () -> trackOptions(track, playlist));
    more.setContentDescription("Options for " + track.title);
    more.setMinWidth(0);
    more.setMinimumWidth(0);
    row.addView(more, new LinearLayout.LayoutParams(dp(46), dp(48)));
    row.setOnClickListener(
        v -> {
          if (track.sourceUrl.isEmpty()) findMatch(track, playlist);
          else if (page.equals("Search")) playRadio(track);
          else play(track, list);
        });
    row.setOnLongClickListener(
        v -> {
          trackOptions(track, playlist);
          return true;
        });
    body.addView(row);
  }

  private void trackOptions(Track track, Library.Playlist playlist) {
    List<String> options =
        new ArrayList<>(
            List.of(
                "Play next",
                "Add to queue",
                "Save to playlist",
                "Add to favourites",
                "Choose another version"));
    if (!track.podcast) {
      SongDownloads.State state = songDownloads.state(track);
      options.add(state.complete ? "Remove download" : state.active ? "Cancel download" : "Download song");
    }
    if (playlist != null && !playlist.id.equals(SongDownloads.PLAYLIST_ID) && !playlist.id.equals(DownloadList.PLAYLIST_ID)) options.add("Remove from this playlist");
    if (playlist != null && playlist.id.equals(DownloadList.PLAYLIST_ID)) options.add("Remove from saved downloads");
    new AlertDialog.Builder(this)
        .setTitle(track.title)
        .setItems(
            options.toArray(new String[0]),
            (d, n) -> {
              switch (options.get(n)) {
                case "Save to playlist" -> choosePlaylist(track);
                case "Remove from saved downloads" -> { try { DownloadList.forget(this,track); showLibrary(); } catch(Exception e) { error("Saved downloads",e); } }
                case "Add to favourites" -> {
                  add(
                      library.playlists.stream()
                          .filter(p -> p.id.equals("favourites"))
                          .findFirst()
                          .orElseThrow(),
                      track);
                  toast("Saved to favourites");
                }
                case "Play next" -> enqueue(track, true, playlist);
                case "Add to queue" -> enqueue(track, false, playlist);
                case "Choose another version" -> findMatch(track, playlist);
                case "Download song", "Cancel download", "Remove download" -> songDownloadAction(track, playlist);

                case "Remove from this playlist" -> {
                  playlist.tracks.remove(track);
                  save();
                  showPlaylist(playlist);
                }
              }
            })
        .show();
  }

  private void choosePlaylist(Track track) {
    String[] names = library.playlists.stream().map(p -> p.name).toArray(String[]::new);
    new AlertDialog.Builder(this)
        .setTitle("Save to playlist")
        .setItems(
            names,
            (d, n) -> {
              add(library.playlists.get(n), track);
              toast("Saved");
            })
        .setNeutralButton(
            "New playlist",
            (d, n) ->
                ask(
                    "New playlist",
                    "Name",
                    "",
                    name -> {
                      Library.Playlist p = new Library.Playlist(name);
                      library.playlists.add(p);
                      add(p, track);
                    }))
        .show();
  }

  private void add(Library.Playlist playlist, Track track) {
    // Explicit playlist import preserves duplicates; manual save avoids accidental repeats.
    boolean exists =
        playlist.tracks.stream()
            .anyMatch(
                t ->
                    !track.sourceUrl.isEmpty()
                        ? t.sourceUrl.equals(track.sourceUrl)
                        : t.title.equals(track.title) && t.artist.equals(track.artist));
    if (!exists) playlist.tracks.add(track);
    save();
  }

  private void findMatch(Track wanted, Library.Playlist playlist) {
    findMatch(
        wanted, playlist, () -> play(wanted, playlist == null ? List.of(wanted) : playlist.tracks));
  }

  private void findMatch(Track wanted, Library.Playlist playlist, Runnable matched) {
    work(
        "Finding song versions…",
        () -> {
          List<Track> candidates = MusicCatalog.songs(wanted.searchQuery());
          MatchRanker.sort(wanted, candidates);
          return candidates;
        },
        candidates -> {
          if (candidates.isEmpty()) {
            toast("No matching versions found. Try searching manually.");
            return;
          }
          String[] titles =
              candidates.stream()
                  .map(t -> t.title + "\n" + t.artist + " · " + duration(t.durationSeconds))
                  .toArray(String[]::new);
          new AlertDialog.Builder(this)
              .setTitle("Choose a version: " + wanted.title)
              .setItems(
                  titles,
                  (d, n) -> {
                    Track selected = candidates.get(n);
                    wanted.confirmVersion(selected);
                    // Imported title, artist and order stay intact.
                    save();
                    if (playlist != null) showPlaylist(playlist);
                    matched.run();
                  })
              .setNegativeButton("Cancel", null)
              .setNeutralButton(
                  "Edit search",
                  (d, n) ->
                      ask(
                          "Find another version",
                          "Search words",
                          wanted.searchQuery(),
                          query -> {
                            searchText = query;
                            searchResults.clear();
                            navigate("Search");
                            toast(
                                "Search, then use the song options to save your preferred"
                                    + " version.");
                          }))
              .show();
        });
  }

  private void showImport() {
    clear("Import", "");
    body.addView(text("Import from file", 30, INK, true));
    gap(14);
    body.addView(text("Bring your collection", 20, INK, true));
    gap(10);
    body.addView(
        text(
            "Import a playlist CSV, podcast subscriptions, or audio stored on your phone.",
            14,
            MUTED,
            false));
    gap(22);
    body.addView(
        button("Playlist CSV", true, () -> pickFile(12, "*/*", null)),
        new LinearLayout.LayoutParams(-1, dp(52)));
    gap(10);
    body.addView(
        text(
            "Track Name and Artist Name(s) columns. Song order and duplicates are preserved.",
            12,
            MUTED,
            false));
    gap(24);
    body.addView(
        button("Podcast subscriptions · OPML", false, () -> pickFile(13, "*/*", null)),
        new LinearLayout.LayoutParams(-1, dp(52)));
    gap(10);
    body.addView(
        button("Local MP3 / FLAC files", false, this::pickAudio),
        new LinearLayout.LayoutParams(-1, dp(52)));
  }

  private void openYouTubeLink(String link) {
    Uri uri = Uri.parse(link);
    String id = uri.getQueryParameter("list");
    String host = uri.getHost();
    if (id != null && host != null && (host.equals("music.youtube.com") || host.equals("www.youtube.com") || host.equals("youtube.com")) && id.matches("[A-Za-z0-9_-]{2,158}")) {
      MusicCatalog.Entry playlist = new MusicCatalog.Entry(); playlist.title = "YouTube Music playlist";
      playlist.browseId = "VL" + id; openCollection(playlist); return;
    }
    work(
        "Loading song…",
        () -> YouTube.resolve(Links.youtubeVideo(link)),
        resolved -> {
          playRadio(resolved.track);
        });
  }

  private void handleShare(Intent intent) {
    if (!Intent.ACTION_SEND.equals(intent.getAction())) return;
    String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
    if (shared == null) return;
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("https?://[^\\s]+").matcher(shared);
    if (!matcher.find()) return;
    String url = matcher.group();
    if (url.contains("open.spotify.com/show/")) {
      showPodcasts();
      addPodcastLink(url);
    } else if (url.contains("open.spotify.com")) {
      showImport();
      toast("Choose a playlist CSV file to import your songs.");
    } else openYouTubeLink(url);
    intent.setAction(Intent.ACTION_MAIN);
  }

  private void pickAudio() {
    pickFile(
        11, "audio/*", new String[] {"audio/*", "audio/flac", "audio/x-flac", "application/ogg"});
  }

  private void pickFile(int code, String type, String[] types) {
    Intent intent =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    if (types != null) intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
    if (code == 11) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    startActivityForResult(intent, code);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if(request==60) { googleLogin.authorizationResult(result,data); return; }
    if (result != RESULT_OK || data == null) return;
    if(request==61 && data.getData()!=null) { saveProfilePhoto(data.getData()); return; }
    if ((request == 14 || request == 15) && data.getData() != null) {
      Uri uri = data.getData();
      if (request == 14) {
        final org.json.JSONObject queue;
        try { queue = controller == null ? null : Backup.queue(controller); }
        catch (Exception e) { error("Backing up queue",e); return; }
        work("Saving backup…", () -> {
          org.json.JSONObject backup = Backup.create(this, library, podcasts);
          if (queue != null) backup.put("queue",queue);
          try (var out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new java.io.IOException("Cannot write this file.");
            out.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
          }
          return true;
        }, ignored -> toast("Backup saved"));
      } else work("Reading backup…", () -> {
        try (var in = getContentResolver().openInputStream(uri)) {
          if (in == null) throw new java.io.IOException("Cannot read this file.");
          return new org.json.JSONObject(new String(Podcasts.readLimited(in,50*1024*1024),StandardCharsets.UTF_8));
        }
      }, backup -> new AlertDialog.Builder(this).setTitle("Restore backup?")
        .setMessage("Merge playlists, podcast subscriptions and listening progress. Existing playlists will be kept.")
        .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,n)-> restoreBackup(backup)).show());
      return;
    }
    if (request == 12 && data.getData() != null) {
      Uri uri = data.getData();
      work(
          "Reading playlist CSV…",
          () -> {
            try (var stream = getContentResolver().openInputStream(uri)) {
              if (stream == null) throw new java.io.IOException("Cannot open this file.");
              return PlaylistCsv.read(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
          },
          tracks ->
              ask(
                  "Name imported playlist",
                  "Playlist name",
                  fileName(uri).replaceFirst("(?i)\\.csv$", ""),
                  name -> {
                    Library.Playlist playlist = new Library.Playlist(name);
                    playlist.tracks.addAll(tracks);
                    library.playlists.add(playlist);
                    save();
                    showPlaylist(playlist);
                    status.setText(tracks.size() + " songs imported from CSV");
                  }));
    } else if (request == 13 && data.getData() != null) {
      Uri uri = data.getData();
      work(
          "Reading subscriptions…",
          () -> {
            try (var in = getContentResolver().openInputStream(uri)) {
              if (in == null) throw new java.io.IOException("Cannot open this file.");
              return Podcasts.opml(Podcasts.readLimited(in, 2 * 1024 * 1024));
            }
          },
          this::importFeeds);
    } else if (request == 11) {
      List<Uri> uris = new ArrayList<>();
      if (data.getClipData() != null)
        for (int i = 0; i < data.getClipData().getItemCount(); i++)
          uris.add(data.getClipData().getItemAt(i).getUri());
      else if (data.getData() != null) uris.add(data.getData());
      Library.Playlist local =
          library.playlists.stream().filter(p -> p.id.equals("local")).findFirst().orElse(null);
      if (local == null) {
        local = new Library.Playlist("On this phone");
        local.id = "local";
        library.playlists.add(local);
      }
      for (Uri uri : uris) {
        try {
          getContentResolver()
              .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
          toast("This provider cannot keep file access. Choose a file stored on the phone.");
          continue;
        }
        add(local, new Track(fileName(uri), "Local audio", uri.toString(), "", 0));
      }
      save();
      showPlaylist(local);
    }
  }

  private void restoreBackup(org.json.JSONObject backup) {
    android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
    progress.setMessage("Restoring your library…"); progress.setCancelable(false); progress.show();
    workers.execute(() -> {
      try {
        int count=Backup.restore(this,backup,library,podcasts);
        if(backup.has("queue")) {
          android.util.AtomicFile file=new android.util.AtomicFile(new java.io.File(getFilesDir(),"restore-queue.json"));
          java.io.FileOutputStream stream=null;
          try { stream=file.startWrite();stream.write(backup.getJSONObject("queue").toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(stream); }
          catch(Exception e) { if(stream!=null)file.failWrite(stream);throw e; }
        }
        ui.post(() -> {
          if(destroyed)return; progress.dismiss();
          if(backup.has("queue") && controller!=null && controller.getMediaItemCount()==0)
            controller.sendCustomCommand(new androidx.media3.session.SessionCommand(PlaybackService.RESTORE,Bundle.EMPTY),Bundle.EMPTY);
          showLibrary(); toast("Backup restored · "+count+" playlists added");
        });
      } catch(Exception e) { ui.post(() -> { if(!destroyed){progress.dismiss();error("Restoring backup",e);} }); }
    });
  }

  private String fileName(Uri uri) {
    try (Cursor cursor =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
    } catch (Exception ignored) {
    }
    return "Imported playlist";
  }

  private MediaItem mediaItem(Track track) {
    Bundle extras = new Bundle();
    try {
      extras.putString("track", Library.encode(track).put("radio", radioStarting).toString());
    } catch (Exception ignored) {
    }
    MediaMetadata.Builder meta =
        new MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setExtras(extras);
    if (!track.artwork.isEmpty()) meta.setArtworkUri(Uri.parse(track.artwork));
    return new MediaItem.Builder()
        .setMediaId(track.sourceUrl)
        .setUri(track.sourceUrl)
        .setMediaMetadata(meta.build())
        .build();
  }

  private boolean radioStarting;
  private void playRadio(Track track) {
    radioStarting = Preferences.get(this).getBoolean("autoplay", true);
    try { play(track, List.of(track)); } finally { radioStarting = false; }
  }

  private void playPlaylist(List<Track> tracks, boolean shuffle) {
    List<Track> playable = new ArrayList<>();
    for (Track t : tracks) if (!t.sourceUrl.isEmpty()) playable.add(t);
    if (playable.isEmpty()) { toast("Match a song first by tapping it."); return; }
    Track first = shuffle ? playable.get(new java.util.Random().nextInt(playable.size())) : playable.get(0);
    play(first, tracks);
    if (controller != null) controller.setShuffleModeEnabled(shuffle);
  }

  private void play(Track track, List<Track> source) {
    if (controller == null || !resumed) {
      toast("Return to Cadence, then tap the song to play.");
      return;
    }
    List<MediaItem> queue = new ArrayList<>();
    int start = 0;
    for (Track t : source)
      if (!t.sourceUrl.isEmpty()) {
        if (t == track) start = queue.size();
        queue.add(mediaItem(t));
      }
    if (queue.isEmpty()) return;
    if (page.equals("Search")) searchHistory.remember(track);
    controller.setShuffleModeEnabled(false);
    controller.setRepeatMode(Player.REPEAT_MODE_OFF);
    controller.setMediaItems(queue, start, track.podcast ? podcasts.progress(track.episodeId) : 0);
    controller.prepare();
    controller.play();
    status.setText("Loading " + track.title + "…");
    openPlayer();
  }

  private void togglePlay() {
    if (controller == null) return;
    if (controller.getMediaItemCount() == 0) {
      toast("Choose a song first.");
      return;
    }
    if (controller.getPlayWhenReady() && controller.getPlaybackState()!=Player.STATE_ENDED && controller.getPlayerError()==null) controller.pause();
    else {
      if(controller.getPlaybackState()==Player.STATE_ENDED) controller.seekToDefaultPosition();
      if (controller.getPlayerError() != null || controller.getPlaybackState() == Player.STATE_IDLE)
        controller.prepare();
      controller.play();
    }
  }

  private void updatePlayer() {
    refreshEpisodeControls(); refreshProfileStatus();
    if (controller == null || miniTitle == null) return;
    MediaItem item = controller.getCurrentMediaItem();
    Track playingTrack = currentTrack();
    if (playingTrack != null && !playingTrack.podcast && controller.getDuration() > 0)
      rememberSongDuration(playingTrack.sourceUrl, controller.getDuration() / 1000);
    boolean playing = controller.getPlayWhenReady() && controller.getPlaybackState()!=Player.STATE_ENDED && controller.getPlayerError()==null;
    miniPlay.icon(playing ? TransportButton.Icon.PAUSE : TransportButton.Icon.PLAY);
    mini.setVisibility(item == null ? View.GONE : View.VISIBLE);
    if (item == null) { if (playerDialog != null) playerDialog.dismiss(); return; }
    if (queueDialog != null) refreshQueue(false);
    MediaMetadata metadata = controller.getMediaMetadata();
    miniTitle.setText(metadata.title);
    miniArtist.setText(metadata.artist);
    miniArt.load(metadata.artworkUri == null ? "" : metadata.artworkUri.toString());
    if (playerTitle != null) {
      playerTitle.setText(metadata.title);
      playerArtist.setText(metadata.artist);
      playerArt.load(metadata.artworkUri == null ? "" : metadata.artworkUri.toString());
      playerPlay.icon(playing ? TransportButton.Icon.PAUSE : TransportButton.Icon.PLAY);
      Track active = currentTrack(); boolean podcast = active != null && active.podcast;
      musicExtras.setVisibility(podcast ? View.GONE : View.VISIBLE);
      podcastExtras.setVisibility(podcast ? View.VISIBLE : View.GONE);
      playerPrevious.setVisibility(podcast ? View.GONE : View.VISIBLE);
      playerNext.setVisibility(podcast ? View.GONE : View.VISIBLE);
      playerBack.seconds(podcast ? Preferences.skip(this, false) : 15);
      playerForward.seconds(podcast ? Preferences.skip(this, true) : 15);
      playerBack.setContentDescription("Skip back " + (podcast ? Preferences.skip(this, false) : 15) + " seconds. Hold to change.");
      playerForward.setContentDescription("Skip forward " + (podcast ? Preferences.skip(this, true) : 15) + " seconds. Hold to change.");
      long total = controller.getDuration(), position = controller.getCurrentPosition();
      if(total <= 0 && active != null && active.durationSeconds > 0) total = active.durationSeconds*1000L;
      if (playerSeeking && total>0) position=total*playerSeek.getProgress()/1000;
      position = Math.max(0, total > 0 ? Math.min(position, total) : position);
      if (!playerSeeking && total > 0)
        playerSeek.setProgress((int) (1000 * position / total));
      else if (!playerSeeking) playerSeek.setProgress(0);
      playerSeek.setEnabled(total > 0 && controller.isCurrentMediaItemSeekable());
      if (playerShuffle != null)
        playerShuffle.colour(controller.getShuffleModeEnabled() ? accent : INK);
      if (playerRepeat != null) {
        playerRepeat.symbol(controller.getRepeatMode() == Player.REPEAT_MODE_ONE ? "repeatOne" : "repeat");
        playerRepeat.colour(
            controller.getRepeatMode() == Player.REPEAT_MODE_OFF ? INK : accent);
      }
      updateTimeLabels(position, total);
      playerContext.setText(podcast ? "PLAYING FROM PODCAST" : "NOW PLAYING");
      playerRecovery.setVisibility(controller.getPlayerError() != null ? View.VISIBLE : View.GONE);
      refreshRelated(active);
      playerState.setText(
          controller.getPlayerError() != null
              ? "Could not play this version. Retry or choose another."
              : controller.getPlaybackState() == Player.STATE_BUFFERING ? "Loading…" : "");
    }
    if (controller.isPlaying()) status.setText("Playing · " + metadata.artist);
  }

  private void openPlayer() {
    if (controller == null || controller.getCurrentMediaItem() == null) {
      toast("Choose a song first.");
      return;
    }
    if(playerDialog!=null && playerDialog.isShowing()) {updatePlayer();return;}
    Dialog dialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
    playerDialog=dialog;playerSeeking=false;
    LinearLayout surface = column();
    surface.setBackgroundColor(BG);
    surface.setPadding(dp(24), dp(12), dp(24), dp(24));
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.addView(surface);
    LinearLayout top = row();
    top.addView(
        icon("down", "Close player", dialog::dismiss),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    TextView now = text("NOW PLAYING", 11, MUTED, true);
    playerContext = now;
    now.setLetterSpacing(.12f);
    now.setGravity(Gravity.CENTER);
    top.addView(now, new LinearLayout.LayoutParams(0, dp(48), 1));
    top.addView(
        icon("queue", "Open queue", this::showQueue),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    surface.addView(top);
    playerArt = new Artwork(this);
    int artDp = Math.max(120, Math.min(320, Math.min(getResources().getConfiguration().screenWidthDp - 48,
        (int)(getResources().getConfiguration().screenHeightDp * .30f))));
    LinearLayout hero = row(); hero.setGravity(Gravity.CENTER);
    hero.addView(playerArt,new LinearLayout.LayoutParams(dp(artDp),dp(artDp)));
    LinearLayout.LayoutParams artSize = new LinearLayout.LayoutParams(-1,-2);
    artSize.setMargins(0,dp(20),0,dp(28)); surface.addView(hero,artSize);
    playerTitle = text("", 25, INK, true);
    playerTitle.setMaxLines(2); playerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
    playerTitle.setOnClickListener(v -> { Track t = currentTrack(); if (t != null) new AlertDialog.Builder(this).setTitle(t.title).setMessage(t.artist).setPositiveButton("Close",null).show(); });
    LinearLayout identity = row();
    identity.addView(playerTitle, new LinearLayout.LayoutParams(0, -2, 1));
    identity.addView(icon("save", "Save current item", () -> {
      Track t = currentTrack(); if (t != null) choosePlaylist(t);
    }), new LinearLayout.LayoutParams(dp(48), dp(48)));
    surface.addView(identity);
    playerArtist = text("", 15, MUTED, false);
    playerArtist.setSingleLine(true); playerArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
    playerArtist.setPadding(0,dp(5),0,dp(8)); surface.addView(playerArtist);
    playerSeek = new SeekBar(this);
    playerSeek.setMax(1000);
    playerSeek.setContentDescription("Playback position");
    playerSeek.setSplitTrack(false);
    playerSeek.setPadding(dp(8), 0, dp(8), 0);
    playerSeek.setProgressTintList(ColorStateList.valueOf(INK));
    playerSeek.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(93, 101, 97)));
    playerSeek.setThumbTintList(ColorStateList.valueOf(INK));
    GradientDrawable trackBackground = round(Color.rgb(93, 101, 97), 3);
    GradientDrawable trackProgress = round(INK, 3);
    android.graphics.drawable.LayerDrawable seekTrack = new android.graphics.drawable.LayerDrawable(
        new android.graphics.drawable.Drawable[] {trackBackground,
            new android.graphics.drawable.ClipDrawable(trackProgress, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)});
    seekTrack.setId(0, android.R.id.background); seekTrack.setId(1, android.R.id.progress);
    for (int i = 0; i < 2; i++) {
      seekTrack.setLayerHeight(i, dp(4));
      seekTrack.setLayerGravity(i, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
    }
    playerSeek.setProgressDrawable(seekTrack);
    GradientDrawable thumb = round(INK, 8); thumb.setSize(dp(14), dp(14));
    playerSeek.setThumb(thumb);
    surface.addView(playerSeek, new LinearLayout.LayoutParams(-1, dp(48)));
    playerSeek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar bar) {playerSeeking=true;}

          public void onStopTrackingTouch(SeekBar bar) {
            if(controller!=null && controller.getDuration()>0)
              controller.seekTo(controller.getDuration()*bar.getProgress()/1000);
            playerSeeking=false;updatePlayer();
          }

          public void onProgressChanged(SeekBar bar, int progress, boolean user) {
            if(user && controller!=null && controller.getDuration()>0 && playerTime!=null) {
              long total=controller.getDuration();
              updateTimeLabels(total*progress/1000, total);
            }
          }
        });
    LinearLayout times = row();
    playerTime = text("0:00", 13, MUTED, false);
    playerRemaining = text("--:--", 13, MUTED, false); playerRemaining.setGravity(Gravity.END);
    times.addView(playerTime, new LinearLayout.LayoutParams(0, -2, 1));
    times.addView(playerRemaining, new LinearLayout.LayoutParams(0, -2, 1));
    surface.addView(times);
    LinearLayout controls = row();
    controls.setGravity(Gravity.CENTER);
    controls.setPadding(0, dp(18), 0, dp(18));
    playerBack = skipButton(false);
    playerForward = skipButton(true);
    controls.addView(playerBack, new LinearLayout.LayoutParams(0, dp(56), 1));
    playerPrevious = new TransportButton(this, TransportButton.Icon.PREVIOUS, INK, BG, "Previous track");
    playerPrevious.setOnClickListener(v -> { if (controller != null) controller.seekToPrevious(); });
    controls.addView(playerPrevious, new LinearLayout.LayoutParams(0, dp(56), 1));
    playerPlay = new TransportButton(this, TransportButton.Icon.PLAY, BG, INK, "Play");
    playerPlay.setOnClickListener(v -> togglePlay());
    LinearLayout.LayoutParams playSize = new LinearLayout.LayoutParams(dp(80), dp(80));
    playSize.setMargins(dp(8), 0, dp(8), 0); controls.addView(playerPlay, playSize);
    playerNext = new TransportButton(this, TransportButton.Icon.NEXT, INK, BG, "Next track");
    playerNext.setOnClickListener(v -> { if (controller != null) controller.seekToNextMediaItem(); });
    controls.addView(playerNext, new LinearLayout.LayoutParams(0, dp(56), 1));
    controls.addView(playerForward, new LinearLayout.LayoutParams(0, dp(56), 1));
    surface.addView(controls);
    playerState = text("", 12, MUTED, false); playerState.setGravity(Gravity.CENTER);
    surface.addView(playerState);
    musicExtras = row(); musicExtras.setPadding(0,dp(12),0,dp(8));
    musicExtras.addView(icon("save", "Save to playlist", () -> { Track t = currentTrack(); if (t != null) choosePlaylist(t); }), new LinearLayout.LayoutParams(0,dp(48),1));
    musicExtras.addView(icon("download", "Download current song", () -> { Track t = currentTrack(); if (t != null) songDownloadAction(t, null); }), new LinearLayout.LayoutParams(0,dp(48),1));
    playerShuffle = icon("shuffle", "Shuffle", () -> {
      if (controller != null) { controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled()); updatePlayer(); }
    });
    musicExtras.addView(playerShuffle, new LinearLayout.LayoutParams(0,dp(48),1));
    playerRepeat = icon("repeat", "Repeat", () -> {
      if (controller != null) {
        int mode = (controller.getRepeatMode()+1)%3; controller.setRepeatMode(mode); updatePlayer();
        toast(mode == Player.REPEAT_MODE_OFF ? "Repeat off" : mode == Player.REPEAT_MODE_ONE ? "Repeat song" : "Repeat queue");
      }
    });
    musicExtras.addView(playerRepeat, new LinearLayout.LayoutParams(0,dp(48),1));
    musicExtras.addView(icon("queue", "Open queue", this::showQueue), new LinearLayout.LayoutParams(0, dp(48), 1));
    surface.addView(musicExtras);
    podcastExtras = row(); podcastExtras.setGravity(Gravity.CENTER);
    podcastExtras.addView(button("Playback speed", false, this::chooseSpeed), new LinearLayout.LayoutParams(-2,dp(48)));
    podcastExtras.addView(icon("queue", "Open queue", this::showQueue), new LinearLayout.LayoutParams(dp(48), dp(48)));
    surface.addView(podcastExtras);
    LinearLayout recovery = row();
    recovery.addView(
        button(
            "Retry",
            false,
            () -> {
              if (controller != null) {
                controller.prepare();
                controller.play();
              }
            }),
        new LinearLayout.LayoutParams(0, dp(48), 1));
    recovery.addView(
        button(
            "Choose version",
            false,
            () -> {
              Track t = currentTrack();
              if (t != null && !t.podcast) findMatch(t, null);
            }),
        new LinearLayout.LayoutParams(0, dp(48), 1));
    playerRecovery = recovery; surface.addView(recovery);
    playerRelated = column(); playerRelated.setPadding(dp(16), dp(18), dp(16), dp(12));
    playerRelated.setBackground(round(CARD, 24));
    LinearLayout.LayoutParams relatedSize = new LinearLayout.LayoutParams(-1, -2);
    relatedSize.setMargins(0, dp(24), 0, dp(12)); surface.addView(playerRelated, relatedSize);
    playerRelatedKey = "";
    dialog.setContentView(scroll);
    surface.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              dp(24) + i.getSystemWindowInsetLeft(),
              dp(16) + i.getSystemWindowInsetTop(),
              dp(24) + i.getSystemWindowInsetRight(),
              dp(16) + i.getSystemWindowInsetBottom());
          return i;
        });
    dialog.setOnDismissListener(
        d -> {
          if(playerDialog!=dialog) return;
          playerDialog=null;playerSeeking=false;
          playerShuffle = null;
          playerRepeat = null;
          playerTitle = null;
          playerArtist = null;
          playerArt = null;
          playerPlay = null;
          playerSeek = null;
          playerTime = null; playerRemaining = null; playerRelated = null; playerRelatedKey = "";
          playerContext = null; playerRecovery = null;
          playerState = null;
        });
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setLayout(-1, -1);
      dialog.getWindow().setStatusBarColor(BG); dialog.getWindow().setNavigationBarColor(BG);
      dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }
    surface.requestApplyInsets();
    updatePlayer();
  }

  private void updateTimeLabels(long position, long total) {
    playerTime.setText(elapsed(position / 1000));
    playerTime.setContentDescription("Elapsed " + elapsed(position / 1000));
    playerRemaining.setText(total > 0 ? "−" + elapsed(Math.max(0, total - position) / 1000) : "--:--");
    playerRemaining.setContentDescription(total > 0 ? "Remaining " + elapsed(Math.max(0, total - position) / 1000) : "Duration unavailable");
  }

  private void refreshRelated(Track active) {
    if (active == null || playerRelated == null) return;
    String key = (active.podcast ? "podcast:" : "music:") + active.sourceUrl;
    if (key.equals(playerRelatedKey)) return;
    playerRelatedKey = key;
    int generation = ++relatedGeneration;
    playerRelated.removeAllViews();
    if (active.podcast) {
      playerRelated.addView(text("More episodes & podcasts", 20, INK, true));
      List<Track> episodes = new ArrayList<>();
      // Episodes from the current show come first; other known shows follow.
      for (int pass = 0; pass < 2; pass++)
        for (Podcasts.Show show : podcasts.knownShows())
          if ((show.title.equals(active.artist)) == (pass == 0))
            for (Podcasts.Episode episode : show.episodes)
              if (!episode.id.equals(active.episodeId) && episodes.size() < 6) episodes.add(episode.track());
      renderRelated(episodes, active, false);
      return;
    }
    playerRelated.addView(text("Related songs", 20, INK, true));
    if (relatedCache.containsKey(key)) { renderRelated(relatedCache.get(key), active, false); return; }
    if (!active.sourceUrl.startsWith("https://")) {
      List<Track> saved = new ArrayList<>(); java.util.Set<String> seen = new java.util.HashSet<>();
      for (Library.Playlist playlist : library.playlists) for (Track track : playlist.tracks)
        if (!track.podcast && track.artist.equals(active.artist) && !track.sourceUrl.equals(active.sourceUrl)
            && !track.sourceUrl.isEmpty() && seen.add(track.sourceUrl) && saved.size() < 6) saved.add(track);
      renderRelated(saved, active, false); return;
    }
    TextView loading = text("Finding more to listen to…", 14, MUTED, false);
    loading.setPadding(0, dp(12), 0, dp(8)); playerRelated.addView(loading);
    workers.execute(() -> {
      List<Track> found = new ArrayList<>(); boolean failed = false;
      try { found = MusicCatalog.related(active.sourceUrl); } catch (Exception e) { failed = true; }
      List<Track> result = found; boolean error = failed;
      ui.post(() -> {
        if (destroyed || playerRelated == null || !key.equals(playerRelatedKey) || generation != relatedGeneration) return;
        if (!error) {
          if (relatedCache.size() >= 12) relatedCache.remove(relatedCache.keySet().iterator().next());
          relatedCache.put(key, result);
        }
        playerRelated.removeView(loading); renderRelated(result, active, error);
      });
    });
  }

  private void renderRelated(List<Track> tracks, Track active, boolean failed) {
    int count = 0; java.util.Set<String> seen = new java.util.HashSet<>();
    for (Track track : tracks) {
      if (count >= 6) break;
      if (track.sourceUrl.equals(active.sourceUrl) || !seen.add(track.sourceUrl)) continue;
      count++;
      LinearLayout item = row(); item.setPadding(0, dp(12), 0, dp(4));
      Artwork art = new Artwork(this); art.load(track.artwork);
      item.addView(art, new LinearLayout.LayoutParams(dp(46), dp(46)));
      LinearLayout labels = column(); labels.setPadding(dp(12), 0, dp(4), 0);
      TextView title = text(track.title, 14, INK, true); title.setMaxLines(2);
      title.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(title);
      TextView by = text(track.artist, 12, MUTED, false); by.setSingleLine(true);
      by.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(by);
      item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      item.addView(icon("plus", "Add " + track.title + " to queue", () -> enqueue(track, false, null)), new LinearLayout.LayoutParams(dp(48), dp(48)));
      item.setOnClickListener(v -> {
        if (track.podcast) {
          for (Podcasts.Show show : podcasts.knownShows()) for (Podcasts.Episode episode : show.episodes)
            if (episode.id.equals(track.episodeId)) { playEpisode(episode); return; }
        } else playRadio(track);
      });
      playerRelated.addView(item);
    }
    if (count == 0) {
      TextView empty = text(failed ? "Related songs are unavailable. Try again when you’re connected."
          : active.podcast ? "Explore more shows in Podcasts." : "No related songs found for this track.", 14, MUTED, false);
      empty.setPadding(0, dp(12), 0, dp(8)); playerRelated.addView(empty);
      if (failed) playerRelated.addView(button("Try again", false, () -> { playerRelatedKey = ""; refreshRelated(currentTrack()); }));
    }
  }

  private TransportButton skipButton(boolean forward) {
    TransportButton button = new TransportButton(this, forward ? TransportButton.Icon.FORWARD : TransportButton.Icon.BACK, INK, BG, forward ? "Skip forward" : "Skip back");
    button.setOnClickListener(v -> {
      Track t = currentTrack(); int seconds = t != null && t.podcast ? Preferences.skip(this, forward) : 15;
      if (controller != null) {
        long target = Math.max(0, controller.getCurrentPosition() + (forward ? 1 : -1) * seconds * 1000L);
        if (controller.getDuration() > 0) target = Math.min(target, controller.getDuration());
        controller.seekTo(target);
      }
    });
    button.setOnLongClickListener(v -> {
      Track t = currentTrack(); if (t == null || !t.podcast) return false;
      int[] values = {15,30,60};
      new AlertDialog.Builder(this).setTitle(forward ? "Skip forward" : "Skip back")
        .setSingleChoiceItems(new String[]{"15 seconds","30 seconds","60 seconds"}, java.util.Arrays.binarySearch(values, Preferences.skip(this, forward)), (d,n) -> {
          Preferences.get(this).edit().putInt(forward ? "skipForward" : "skipBack", values[n]).apply();
          updatePlayer(); d.dismiss();
        }).setNegativeButton("Cancel",null).show(); return true;
    });
    return button;
  }

  private Track currentTrack() {
    if (controller == null) return null;
    Bundle extras = controller.getMediaMetadata().extras;
    try {
      return extras == null
          ? null
          : Library.decode(new org.json.JSONObject(extras.getString("track", "{}")));
    } catch (Exception e) {
      return null;
    }
  }

  private void showPodcasts() {
    shownShow = null;
    clear("Podcasts", "");
    LinearLayout heading = row();
    heading.addView(text("Podcasts", 32, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(
        icon(
            "plus",
            "Add a podcast",
            () -> ask("Add a podcast", "RSS or Spotify show link", "", this::addPodcastLink)),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    body.addView(heading);
    gap(16);
    EditText query = input("Find a podcast", "");
    query.setSingleLine(true);
    query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
    body.addView(query);
    gap(10);
    Runnable search =
        () -> {
          String term = query.getText().toString().trim();
          if (term.isBlank()) return;
          if (term.startsWith("http")) addPodcastLink(term);
          else findPodcasts(term, false);
        };
    query.setOnEditorActionListener(
        (v, id, event) -> {
          if (id == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
            search.run();
            return true;
          }
          return false;
        });
    LinearLayout actions = row();
    actions.addView(button("Search", true, search), new LinearLayout.LayoutParams(0, dp(46), 1));
    actions.addView(
        button("Downloads", false, this::showDownloads),
        new LinearLayout.LayoutParams(0, dp(46), 1));
    body.addView(actions);
    gap(26);
    List<Podcasts.Episode> continued = new ArrayList<>();
    for (Podcasts.Show show : podcasts.knownShows())
      for (Podcasts.Episode e : show.episodes)
        if (podcasts.progress(e.id) > 0 && !podcasts.completed(e.id)) continued.add(e);
    if (!continued.isEmpty()) {
      body.addView(text("Continue listening", 22, INK, true));
      gap(12);
      for (Podcasts.Episode e : continued.subList(0, Math.min(4, continued.size()))) episodeRow(e);
      gap(18);
    }
    LinearLayout subhead = row();
    subhead.addView(text("Your shows", 23, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    subhead.addView(
        button(
            "Refresh",
            false,
            () -> {
              List<String> feeds = new ArrayList<>();
              for (Podcasts.Show show : podcasts.shows) feeds.add(show.feedUrl);
              if (!feeds.isEmpty()) importFeeds(feeds);
            }));
    body.addView(subhead);
    gap(14);
    if (podcasts.shows.isEmpty()) {
      body.addView(text("Follow your favourite voices", 20, INK, true));
      gap(8);
      body.addView(
          text(
              "Search for a show, paste its RSS link, or import subscriptions from an OPML file.",
              14,
              MUTED,
              false));
      gap(16);
      body.addView(button("Import OPML", false, () -> pickFile(13, "*/*", null)));
    }
    for (int i = 0; i < podcasts.shows.size(); i += 2) {
      LinearLayout row = row();
      row.setGravity(Gravity.TOP);
      for (int j = i; j < Math.min(i + 2, podcasts.shows.size()); j++) {
        Podcasts.Show show = podcasts.shows.get(j);
        LinearLayout tile = column();
        Artwork art = new Artwork(this);
        art.load(show.artwork);
        int size = Math.max(100, (getResources().getConfiguration().screenWidthDp - 48) / 2);
        tile.addView(art, new LinearLayout.LayoutParams(-1, dp(size)));
        TextView title = text(show.title, 15, INK, true);
        title.setMaxLines(2);
        title.setPadding(0, dp(8), 0, 0);
        tile.addView(title);
        TextView author = text(show.author, 12, MUTED, false);
        author.setMaxLines(1);
        tile.addView(author);
        tile.setOnClickListener(v -> showPodcast(show));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(0, 0, dp(12), 0);
        row.addView(tile, p);
      }
      if (row.getChildCount() == 1)
        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
      body.addView(row);
      gap(22);
    }
  }

  private void findPodcasts(String term, boolean fromSpotify) {
    work(
        "Finding podcasts…",
        () -> Podcasts.search(term),
        shows -> {
          if (shows.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("No public feed found")
                .setMessage(
                    "Try the show name or paste its RSS feed. A Spotify-only show may not offer a"
                        + " public feed.")
                .setPositiveButton("OK", null)
                .show();
            return;
          }
          clear("Podcasts", "");
          body.addView(
              text(fromSpotify ? "Choose the public version" : "Podcast results", 27, INK, true));
          gap(8);
          body.addView(text(term, 14, MUTED, false));
          gap(18);
          for (Podcasts.Show show : shows) {
            LinearLayout r = row();
            r.setPadding(0, dp(9), 0, dp(9));
            Artwork art = new Artwork(this);
            art.load(show.artwork);
            r.addView(art, new LinearLayout.LayoutParams(dp(70), dp(70)));
            LinearLayout labels = column();
            labels.setPadding(dp(14), 0, 0, 0);
            labels.addView(text(show.title, 16, INK, true));
            labels.addView(text(show.author, 12, MUTED, false));
            r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            r.setOnClickListener(v -> previewPodcast(show.feedUrl));
            body.addView(r);
          }
        });
  }

  private void addPodcastLink(String link) {
    try {
      java.net.URI uri = new java.net.URI(link.trim());
      if ("open.spotify.com".equalsIgnoreCase(uri.getHost()))
        work(
            "Reading show name…",
            () -> Podcasts.spotifyShowTitle(link),
            title -> findPodcasts(title, true));
      else previewPodcast(link);
    } catch (Exception e) {
      error("Podcast link", e);
    }
  }

  private void previewPodcast(String feed) {
    work("Loading podcast…", () -> Podcasts.feed(feed), show -> {
      try { podcasts.preview(show); showPodcast(show); } catch (Exception e) { error("Podcast preview", e); }
    });
  }

  private void subscribe(String feed) {
    work(
        "Loading podcast…",
        () -> Podcasts.feed(feed),
        show -> {
          try {
            podcasts.put(show);
            showPodcast(show);
            toast("Added to your shows");
          } catch (Exception e) {
            error("Saving podcast", e);
          }
        });
  }

  private void importFeeds(List<String> feeds) {
    if (feeds.isEmpty()) return;
    android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
    progress.setTitle("Importing subscriptions");
    progress.setMessage("Loading 0 of " + feeds.size());
    progress.setCancelable(false);
    progress.show();
    workers.execute(
        () -> {
          List<Podcasts.Show> loaded = new ArrayList<>();
          List<String> failed = new ArrayList<>();
          for (int i = 0; i < feeds.size(); i++) {
            if (Thread.currentThread().isInterrupted()) break;
            String url = feeds.get(i);
            try {
              loaded.add(Podcasts.feed(url));
            } catch (Exception e) {
              failed.add(url);
            }
            int count = i + 1;
            ui.post(
                () -> {
                  if (!destroyed) progress.setMessage("Loaded " + count + " of " + feeds.size());
                });
          }
          ui.post(
              () -> {
                if (destroyed) return;
                progress.dismiss();
                int saved = 0;
                try {
                  for (Podcasts.Show show : loaded) {
                    podcasts.put(show);
                    saved++;
                  }
                } catch (Exception e) {
                  error("Saving subscriptions", e);
                }
                showPodcasts();
                new AlertDialog.Builder(this)
                    .setTitle("Subscriptions updated")
                    .setMessage(
                        saved
                            + " shows saved"
                            + (failed.isEmpty()
                                ? ""
                                : "\n"
                                    + failed.size()
                                    + " feeds could not be loaded. You can retry their RSS links."))
                    .setPositiveButton("OK", null)
                    .show();
              });
        });
  }

  private final java.util.Map<String, Button> downloadButtons = new java.util.HashMap<>();

  private void showPodcast(Podcasts.Show show) {
    shownShow = show;
    clear("Show", "");
    downloadButtons.clear();
    Artwork art = new Artwork(this);
    art.load(show.artwork);
    LinearLayout hero = row();
    hero.setGravity(Gravity.CENTER);
    hero.addView(art, new LinearLayout.LayoutParams(dp(190), dp(190)));
    body.addView(hero);
    gap(22);
    body.addView(text(show.title, 29, INK, true));
    gap(5);
    body.addView(text(show.author, 14, MUTED, false));
    gap(16);
    LinearLayout actions = row();
    actions.addView(
        button(
            "Refresh",
            false,
            () ->
                work(
                    "Refreshing episodes…",
                    () -> Podcasts.feed(show.feedUrl),
                    fresh -> {
                      try {
                        if (podcasts.subscribed(show.feedUrl)) podcasts.put(fresh); else podcasts.preview(fresh);
                        showPodcast(fresh);
                      } catch (Exception e) {
                        error("Saving podcast", e);
                      }
                    })),
        new LinearLayout.LayoutParams(0, dp(46), 1));
    actions.addView(
        button("Speed", false, this::chooseSpeed), new LinearLayout.LayoutParams(0, dp(46), 1));
    boolean subscribed = podcasts.subscribed(show.feedUrl);
    actions.addView(button(subscribed ? "Subscribed ✓" : "Subscribe", !subscribed, () -> {
      try {
        if (podcasts.subscribed(show.feedUrl)) {
          new AlertDialog.Builder(this).setTitle("Unsubscribe?")
              .setMessage("Your listening progress and downloads will be kept.")
              .setNegativeButton("Cancel", null).setPositiveButton("Unsubscribe", (d,n) -> {
                try { podcasts.remove(show); showPodcast(show); } catch (Exception e) { error("Subscription", e); }
              }).show();
        } else { podcasts.put(show); showPodcast(show); toast("Added to your library"); }
      } catch (Exception e) { error("Subscription", e); }
    }), new LinearLayout.LayoutParams(0, dp(46), 1));
    body.addView(actions);
    gap(22);
    body.addView(text("Episodes", 23, INK, true));
    gap(12);
    appendEpisodes(show, 0);
    if (show.episodes.isEmpty())
      body.addView(text("This feed has no public audio episodes.", 14, MUTED, false));
  }

  private void appendEpisodes(Podcasts.Show show, int offset) {
    int end = Math.min(offset + 30, show.episodes.size());
    for (int i = offset; i < end; i++) episodeRow(show.episodes.get(i));
    if (end < show.episodes.size()) {
      Button more = button("More episodes", false, () -> {});
      more.setOnClickListener(
          v -> {
            body.removeView(more);
            appendEpisodes(show, end);
          });
      body.addView(more, new LinearLayout.LayoutParams(-1, dp(52)));
    }
  }

  private void showDownloads() {
    clear("Podcasts", "");
    downloadButtons.clear();
    body.addView(text("Downloaded episodes", 29, INK, true));
    gap(18);
    int count = 0;
    for (Podcasts.Show show : podcasts.knownShows())
      for (Podcasts.Episode e : show.episodes)
        if (podcasts.download(e.id).complete) {
          episodeRow(e);
          count++;
        }
    if (count == 0)
      body.addView(text("Download episodes from a show to listen offline.", 14, MUTED, false));
  }

  private void episodeRow(Podcasts.Episode e) {
    LinearLayout box = column();
    box.setPadding(0, dp(14), 0, dp(16));
    LinearLayout top = row();
    Artwork art = new Artwork(this);
    art.load(e.artwork);
    top.addView(art, new LinearLayout.LayoutParams(dp(58), dp(58)));
    LinearLayout labels = column();
    labels.setPadding(dp(12), 0, 0, 0);
    TextView title = text(e.title, 16, INK, true);
    title.setMaxLines(3);
    labels.addView(title);
    TextView show = text(e.show, 12, MUTED, false);
    show.setMaxLines(1);
    labels.addView(show);
    top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    top.setOnClickListener(v -> playEpisode(e));
    box.addView(top);
    displayedEpisodes.put(e.id,e);
    TextView meta = text("",12,accent,false); meta.setPadding(0,dp(10),0,0);
    episodeProgress.put(e.id,meta); box.addView(meta);
    ProgressBar bar = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    bar.setMax(1000); bar.setProgressTintList(ColorStateList.valueOf(accent));
    LinearLayout.LayoutParams barSize = new LinearLayout.LayoutParams(-1,dp(4)); barSize.setMargins(0,dp(10),0,dp(4));
    box.addView(bar,barSize); episodeBars.put(e.id,bar);
    LinearLayout controls=row(); controls.setPadding(0,dp(12),0,dp(4));
    TransportButton episodePlay=new TransportButton(this,TransportButton.Icon.PLAY,BG,accent,"Play "+e.title);
    episodePlay.setOnClickListener(v -> {
      Track active=currentTrack();
      if(active!=null && active.podcast && active.episodeId.equals(e.id)) togglePlay();
      else playEpisode(e);
      refreshEpisodeControls();
    });
    episodePlayButtons.put(e.id,episodePlay);
    LinearLayout.LayoutParams playSize=new LinearLayout.LayoutParams(dp(48),dp(48));playSize.setMargins(0,0,dp(12),0);
    controls.addView(episodePlay,playSize);
    Button download = button(podcasts.download(e.id).label, false, () -> downloadAction(e));
    download.setTextSize(12);
    downloadButtons.put(e.id, download);
    controls.addView(download, new LinearLayout.LayoutParams(0, dp(44), 1));
    controls.addView(
        icon(
            "more",
            "Episode options",
            () ->
                new AlertDialog.Builder(this)
                    .setTitle(e.title)
                    .setItems(
                        new String[] {
                          "Play next",
                          "Add to queue",
                          podcasts.completed(e.id) ? "Mark unplayed" : "Mark played",
                          "Download options"
                        },
                        (d, n) -> {
                          if (n == 0) enqueue(podcasts.playable(e), true, null);
                          else if (n == 1) enqueue(podcasts.playable(e), false, null);
                          else if (n == 2) {
                            podcasts.progress(e.id, 0, !podcasts.completed(e.id));
                            if (shownShow != null && page.equals("Show")) showPodcast(shownShow);
                            else showPodcasts();
                          } else downloadAction(e);
                        })
                    .show()),
        new LinearLayout.LayoutParams(dp(44), dp(44)));
    box.addView(controls);
    body.addView(box); refreshEpisodeControls();
    View line = new View(this);
    line.setBackgroundColor(CARD);
    body.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
  }

  private void playEpisode(Podcasts.Episode e) {
    Track t = podcasts.playable(e);
    play(t, List.of(t));
  }

  private void downloadAction(Podcasts.Episode e) {
    PodcastStore.Download d = podcasts.download(e.id);
    if (d.complete || d.active) {
      new AlertDialog.Builder(this)
          .setTitle(d.complete ? "Episode downloaded" : "Episode downloading")
          .setItems(
              new String[] {
                d.complete ? "Play offline" : "Keep downloading",
                d.complete ? "Remove download" : "Cancel download"
              },
              (dialog, n) -> {
                if (n == 0) {
                  if (d.complete) playEpisode(e);
                } else
                  try {
                    podcasts.deleteDownload(e.id);
                    DownloadList.forget(this,e.track());
                    refreshDownloadButtons();
                  } catch (Exception failure) {
                    error("Removing download", failure);
                  }
              })
          .show();
    } else
      try {
        podcasts.startDownload(e);
        DownloadList.remember(this,e.track());
        toast("Download started");
        refreshDownloadButtons();
      } catch (Exception failure) {
        error("Download", failure);
      }
  }

  private void refreshDownloadButtons() {
    if (podcasts == null) return;
    for (var kv : downloadButtons.entrySet())
      kv.getValue().setText(podcasts.download(kv.getKey()).label);
    refreshSongDownloads(); refreshPlaylistDownload();
  }

  private void updateSongBadge(DownloadBadge badge, Track track) {
    SongDownloads.State state = songDownloads.state(track);
    badge.state(state.complete,state.active);
    badge.setContentDescription(state.complete ? "Available offline: " + track.title
        : state.active ? state.label + ": " + track.title : "Download song " + track.title);
  }

  private void refreshSongDownloads() {
    if (songDownloads == null) return;
    for (var entry : songBadges.entrySet()) updateSongBadge(entry.getKey(), entry.getValue());
    if (page.equals("Playlist") && shownPlaylist != null && shownPlaylist.id.equals(SongDownloads.PLAYLIST_ID)) {
      List<Track> downloaded = songDownloads.tracks();
      if (downloaded.size() != shownPlaylist.tracks.size()) showPlaylist(songDownloads.playlist());
    }
  }

  private void songDownloadAction(Track track, Library.Playlist playlist) {
    if (track.sourceUrl.isEmpty()) { findMatch(track, playlist, () -> songDownloadAction(track, playlist)); return; }
    SongDownloads.State state = songDownloads.state(track);
    if (state.complete || state.active) {
      new AlertDialog.Builder(this).setTitle(track.title)
          .setItems(new String[] {state.complete ? "Play offline" : "Keep downloading",
              state.complete ? "Remove download" : "Cancel download"}, (dialog, which) -> {
            if (which == 0 && state.complete) play(track, List.of(track));
            if (which == 1) {
              try { songDownloads.remove(track); refreshSongDownloads(); toast("Download removed. Your playlists are kept."); }
              catch (Exception failure) { error("Removing download", failure); }
            }
          }).show();
      return;
    }
    String scheme = Uri.parse(track.sourceUrl).getScheme();
    if ("file".equals(scheme) || "content".equals(scheme)) { toast("This audio is already on your phone."); return; }
    work("Preparing song download…", () -> { songDownloads.start(track); return true; },
        ignored -> { toast("Song download started"); refreshSongDownloads(); });
  }

  private void requestSongDuration(Track track) {
    if (track.podcast || track.durationSeconds > 0 || track.sourceUrl.isEmpty()) return;
    String source = track.sourceUrl;
    Long known = knownDurations.get(SongDownloads.key(source));
    if (known != null) {
      track.durationSeconds = known;
      for (var entry : songTimes.entrySet()) if (entry.getValue() == track) entry.getKey().setText(duration(known));
      save(); return;
    }
    try { app.cadence.core.Links.youtubeVideo(source); } catch (Exception unsupported) { return; }
    if (!durationRequests.add(source)) return;
    durationWorkers.execute(() -> {
      try {
        long seconds = YouTube.metadata(source).durationSeconds;
        ui.post(() -> { if (!destroyed) rememberSongDuration(source, seconds); });
      } catch (Exception | LinkageError ignored) { /* Keep the confirmed version and retry on a later launch. */ }
    });
  }

  private void rememberSongDuration(String source, long seconds) {
    if (seconds <= 0 || library == null) return;
    String key = SongDownloads.key(source);
    Long previous = knownDurations.put(key, seconds);
    if (previous != null && previous == seconds) return;
    boolean changed = false;
    for (Library.Playlist playlist : library.playlists) for (Track track : playlist.tracks)
      if (track.durationSeconds <= 0 && SongDownloads.key(track.sourceUrl).equals(SongDownloads.key(source))) {
        track.durationSeconds = seconds; changed = true;
      }
    for (var entry : songTimes.entrySet()) if (SongDownloads.key(entry.getValue().sourceUrl).equals(SongDownloads.key(source))) {
      if (entry.getValue().durationSeconds <= 0) entry.getValue().durationSeconds = seconds;
      entry.getKey().setText(duration(entry.getValue().durationSeconds));
    }
    if (changed) save();
  }

  private void enqueue(Track track, boolean next, Library.Playlist playlist) {
    if (track.sourceUrl.isEmpty()) {
      findMatch(track, playlist, () -> enqueue(track, next, playlist));
      return;
    }
    if (controller == null) {
      toast("The player is connecting. Try again.");
      return;
    }
    try {
      Bundle args = new Bundle();
      args.putString("track", Library.encode(track).toString());
      args.putBoolean("next", next);
      var result =
          controller.sendCustomCommand(
              new androidx.media3.session.SessionCommand(PlaybackService.ENQUEUE, Bundle.EMPTY),
              args);
      result.addListener(
          () -> {
            try {
              if (result.get().resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS)
                toast(next ? "Playing next" : "Added to queue");
              else toast("Could not add this song.");
            } catch (Exception e) {
              toast("Could not add this song.");
            }
          },
          this::runOnUiThread);
    } catch (Exception e) {
      error("Queue", e);
    }
  }

  private void showQueue() {
    if (controller == null || controller.getMediaItemCount() == 0) {
      toast("Your queue is empty. Add a song to get started.");
      return;
    }
    if (queueDialog != null) {
      queueDialog.dismiss();
      return;
    }
    queueDialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
    LinearLayout surface = column();
    surface.setBackgroundColor(BG);
    surface.setPadding(dp(16), dp(18), dp(16), dp(12));
    LinearLayout header = row();
    header.addView(
        icon("down", "Close queue", () -> queueDialog.dismiss()),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    header.addView(text("Queue", 25, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(
        button(
            "Clear",
            false,
            () -> {
              if (controller == null) return;
              int current = controller.getCurrentMediaItemIndex();
              if (current + 1 < controller.getMediaItemCount())
                controller.removeMediaItems(current + 1, controller.getMediaItemCount());
              if (current > 0) controller.removeMediaItems(0, current);
              refreshQueue(true);
            }));
    surface.addView(header);
    TextView hint = text("Drag the handle to reorder · Tap a song to play", 12, MUTED, false);
    hint.setPadding(dp(4), 0, 0, dp(14));
    surface.addView(hint);
    ScrollView scroll = new ScrollView(this);
    queueBody = column();
    queueScroll = scroll;
    scroll.addView(queueBody);
    surface.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    queueDialog.setContentView(surface);
    queueDialog.setOnDismissListener(
        d -> {
          queueDialog = null;
          queueBody = null;
          queueScroll = null;
          queueStamp = "";
        });
    queueDialog.show();
    if (queueDialog.getWindow() != null) queueDialog.getWindow().setLayout(-1, -1);
    surface.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              dp(16) + i.getSystemWindowInsetLeft(),
              dp(10) + i.getSystemWindowInsetTop(),
              dp(16) + i.getSystemWindowInsetRight(),
              dp(10) + i.getSystemWindowInsetBottom());
          return i;
        });
    surface.requestApplyInsets();
    refreshQueue(true);
  }

  private List<Integer> upcoming() {
    List<Integer> indices = new ArrayList<>();
    if (controller == null) return indices;
    androidx.media3.common.Timeline timeline = controller.getCurrentTimeline();
    int at = controller.getCurrentMediaItemIndex();
    for (int i = 0; i < controller.getMediaItemCount(); i++) {
      at =
          timeline.getNextWindowIndex(
              at, Player.REPEAT_MODE_OFF, controller.getShuffleModeEnabled());
      if (at == C.INDEX_UNSET) break;
      indices.add(at);
    }
    return indices;
  }

  private void refreshQueue(boolean force) {
    if (queueBody == null || controller == null) return;
    List<Integer> upcoming = upcoming();
    StringBuilder signature =
        new StringBuilder()
            .append(controller.getCurrentMediaItemIndex())
            .append(controller.getShuffleModeEnabled());
    for (int i = 0; i < controller.getMediaItemCount(); i++)
      signature.append(controller.getMediaItemAt(i).mediaId);
    signature.append(upcoming);
    if (!force && signature.toString().equals(queueStamp)) return;
    queueStamp = signature.toString();
    queueBody.removeAllViews();
    TextView now = text("Now playing", 20, INK, true);
    now.setPadding(0, dp(12), 0, dp(12));
    queueBody.addView(now);
    int current = controller.getCurrentMediaItemIndex();
    if (current >= 0) queueBody.addView(queueRow(current, true));
    TextView next = text("Next up", 20, INK, true);
    next.setPadding(0, dp(24), 0, dp(14));
    queueBody.addView(next);
    if (upcoming.isEmpty())
      queueBody.addView(text("All caught up. Add more songs to your queue.", 14, MUTED, false));
    for (int index : upcoming) queueBody.addView(queueRow(index, false));
  }

  private View queueRow(int index, boolean current) {
    MediaItem item = controller.getMediaItemAt(index);
    LinearLayout r = row();
    r.setPadding(0, dp(7), 0, dp(7));
    Artwork art = new Artwork(this);
    art.load(item.mediaMetadata.artworkUri == null ? "" : item.mediaMetadata.artworkUri.toString());
    r.addView(art, new LinearLayout.LayoutParams(dp(48), dp(48)));
    LinearLayout labels = column();
    labels.setPadding(dp(12), 0, 0, 0);
    TextView title =
        text(String.valueOf(item.mediaMetadata.title), 14, current ? accent : INK, true);
    title.setMaxLines(1);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    labels.addView(title);
    TextView artist = text(String.valueOf(item.mediaMetadata.artist), 12, MUTED, false);
    artist.setMaxLines(1);
    labels.addView(artist);
    r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    labels.setOnClickListener(
        v -> {
          if (controller != null) {
            controller.seekTo(index, 0);
            if (controller.getPlaybackState() == Player.STATE_IDLE) controller.prepare();
            controller.play();
          }
        });
    if (!current) {
      r.addView(
          icon(
              "close",
              "Remove from queue",
              () -> {
                if (controller != null && index < controller.getMediaItemCount()) {
                  controller.removeMediaItem(index);
                  refreshQueue(true);
                }
              }),
          new LinearLayout.LayoutParams(dp(42), dp(48)));
      AppIcon drag =
          icon(
              "drag",
              "Reorder song",
              () -> {
                List<Integer> future = upcoming();
                int position = future.indexOf(index);
                new AlertDialog.Builder(this)
                    .setTitle("Reorder song")
                    .setItems(
                        new String[] {"Move up", "Move down"},
                        (d, n) -> {
                          if (n == 0 && position > 0) moveQueue(index, future.get(position - 1));
                          else if (n == 1 && position + 1 < future.size())
                            moveQueue(index, future.get(position + 1));
                        })
                    .show();
              });
      drag.setOnTouchListener(
          (v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
              v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
              r.startDragAndDrop(
                  ClipData.newPlainText("queue", ""),
                  new View.DragShadowBuilder(r),
                  Integer.valueOf(index),
                  0);
              return true;
            }
            return true;
          });
      r.addView(drag, new LinearLayout.LayoutParams(dp(44), dp(48)));
      r.setOnDragListener(
          (v, event) -> {
            if (!(event.getLocalState() instanceof Integer)) return false;
            switch (event.getAction()) {
              case android.view.DragEvent.ACTION_DRAG_LOCATION:
                if (queueScroll != null) {
                  float y = r.getTop() + event.getY() - queueScroll.getScrollY();
                  if (y < dp(76)) queueScroll.smoothScrollBy(0, -dp(24));
                  else if (y > queueScroll.getHeight() - dp(76))
                    queueScroll.smoothScrollBy(0, dp(24));
                }
                break;
              case android.view.DragEvent.ACTION_DRAG_ENTERED:
                r.setBackground(round(CARD, 4));
                break;
              case android.view.DragEvent.ACTION_DRAG_EXITED:
              case android.view.DragEvent.ACTION_DRAG_ENDED:
                r.setBackgroundColor(BG);
                break;
              case android.view.DragEvent.ACTION_DROP:
                int from = (Integer) event.getLocalState();
                moveQueue(from, index);
                return true;
            }
            return true;
          });
    }
    return r;
  }

  private void moveQueue(int from, int to) {
    if (controller == null || from == to) return;
    Bundle args = new Bundle();
    args.putInt("from", from);
    args.putInt("to", to);
    var result =
        controller.sendCustomCommand(
            new androidx.media3.session.SessionCommand(PlaybackService.MOVE, Bundle.EMPTY), args);
    result.addListener(() -> refreshQueue(true), this::runOnUiThread);
  }

  private void settings() {
    clear("Settings", "");
    body.addView(text("Settings", 32, INK, true)); gap(6);
    body.addView(text("Make Cadence feel like you.", 15, MUTED, false)); gap(24);
    LinearLayout account = settingsGroup("Profile & sync");
    settingRow(account,"info",profileName(),CloudSync.subject(this).isEmpty()?"Optional Google sign-in · Save your library online":CloudSync.prefs(this).getString("email",""),this::showProfile);
    LinearLayout discovery = settingsGroup("Discovery");
    settingRow(discovery, "language", "Languages", String.join(", ", Preferences.languages(this)), this::chooseLanguages);
    LinearLayout playback = settingsGroup("Playback");
    settingRow(playback, "crossfade", "Crossfade", Preferences.crossfade(this) == 0 ? "Off" : Preferences.crossfade(this) + " seconds", this::chooseCrossfade);
    settingDivider(playback);
    LinearLayout auto = settingRow(playback, "shuffle", "Autoplay", "Keep listening with related songs", null);
    android.widget.Switch autoplay = new android.widget.Switch(this);
    autoplay.setContentDescription("Autoplay related songs");
    autoplay.setThumbTintList(new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}}, new int[] {accent, MUTED}));
    autoplay.setChecked(Preferences.get(this).getBoolean("autoplay", true));
    autoplay.setOnCheckedChangeListener((v, checked) -> Preferences.get(this).edit().putBoolean("autoplay", checked).apply());
    auto.addView(autoplay, new LinearLayout.LayoutParams(dp(52), dp(48)));
    auto.setOnClickListener(v -> autoplay.setChecked(!autoplay.isChecked()));
    LinearLayout saved = settingsGroup("Your library");
    settingRow(saved, "backup", "Save backup", "Playlists, podcasts & listening progress", () -> {
      Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
          .setType("application/json").putExtra(Intent.EXTRA_TITLE, "Cadence-backup.json");
      startActivityForResult(intent, 14);
    });
    settingDivider(saved);
    settingRow(saved, "restore", "Restore backup", "Bring your saved library into Cadence", () -> pickFile(15, "*/*", null));
    settingDivider(saved);
    settingRow(saved, "import", "Import from file", "Playlists, audio files or podcast feeds", this::showImport);
    LinearLayout personal = settingsGroup("Personalise");
    settingRow(personal, "palette", "Appearance", "Charcoal · Soft mint", null);
    settingDivider(personal);
    settingRow(personal, "widget", "Home-screen widget", "Your music, a tap away", () -> {
      android.appwidget.AppWidgetManager manager = android.appwidget.AppWidgetManager.getInstance(this);
      if (manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(this, CadenceWidget.class), null, null);
      else toast("Hold an empty area on your home screen, choose Widgets, then Cadence.");
    });
    LinearLayout about = settingsGroup("About");
    settingRow(about, "info", "Playback diagnostics", "Connection, queue & playback details", this::diagnostics);
    gap(4);
    TextView version = text("Cadence 2.1", 13, MUTED, false); version.setGravity(Gravity.CENTER); body.addView(version);
  }

  private LinearLayout settingsGroup(String title) {
    TextView heading = text(title, 13, accent, true);
    heading.setPadding(dp(4), 0, 0, dp(10)); body.addView(heading);
    LinearLayout card = column(); card.setPadding(dp(4), dp(4), dp(4), dp(4));
    card.setBackground(round(CARD, 22));
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
    size.setMargins(0, 0, 0, dp(24)); body.addView(card, size); return card;
  }

  private LinearLayout settingRow(LinearLayout card, String symbol, String title, String detail, Runnable action) {
    LinearLayout item = row(); item.setPadding(dp(8), dp(12), dp(12), dp(12)); item.setMinimumHeight(dp(76));
    AppIcon image = new AppIcon(this, symbol, accent, title); image.setFocusable(false);
    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    item.addView(image, new LinearLayout.LayoutParams(dp(44), dp(44)));
    LinearLayout labels = column(); labels.setPadding(dp(8), 0, dp(8), 0);
    labels.addView(text(title, 16, INK, true));
    TextView subtitle = text(detail, 12, MUTED, false); subtitle.setMaxLines(3); labels.addView(subtitle);
    item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (action != null) {
      AppIcon chevron = new AppIcon(this, "chevron", MUTED, "");
      chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); chevron.setFocusable(false);
      item.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
      item.setBackground(ripple(Color.TRANSPARENT, 18)); item.setOnClickListener(v -> action.run());
    }
    card.addView(item); return item;
  }

  private void settingDivider(LinearLayout card) {
    View line = new View(this); line.setBackgroundColor(Color.rgb(48, 53, 50));
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(1));
    size.setMargins(dp(60), 0, dp(16), 0); card.addView(line, size);
  }

  private void chooseLanguages() {
    List<String> selected = new ArrayList<>(Preferences.languages(this));
    boolean[] checked = new boolean[Preferences.LANGUAGES.length];
    for (int i=0;i<checked.length;i++) checked[i]=selected.contains(Preferences.LANGUAGES[i]);
    AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Discovery languages")
      .setMultiChoiceItems(Preferences.LANGUAGES,checked,(d,n,value) -> {
        if(value) selected.add(Preferences.LANGUAGES[n]); else selected.remove(Preferences.LANGUAGES[n]);
      }).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      if (selected.isEmpty()) { toast("Choose at least one language."); return; }
      Preferences.get(this).edit().putString("languages",String.join(",",selected)).apply();
      if (!selected.contains(language)) language="All";
      dialog.dismiss(); settings();
    })); dialog.show();
  }

  private void chooseCrossfade() {
    String[] choices = new String[12]; choices[0]="Off";
    for(int i=1;i<choices.length;i++) choices[i]=(i+4)+" seconds";
    int current=Preferences.crossfade(this);
    new AlertDialog.Builder(this).setTitle("Crossfade songs")
      .setSingleChoiceItems(choices,current==0?0:current-4,(d,n)-> {
        Preferences.get(this).edit().putInt("crossfade",n==0?0:n+4).apply(); d.dismiss(); settings();
      }).setNegativeButton("Cancel",null).show();
  }

  private void diagnostics() {
    String report =
        "Cadence 2.1\nAndroid "
            + Build.VERSION.RELEASE
            + " · "
            + Build.MANUFACTURER
            + " "
            + Build.MODEL
            + "\n\n"
            + (controller == null
                ? "Player disconnected"
                : "Player state: "
                    + controller.getPlaybackState()
                    + "\nPlaying: "
                    + controller.isPlaying()
                    + "\nQueue: "
                    + controller.getMediaItemCount())
            + "\n\nLast error:\n"
            + lastError;
    new AlertDialog.Builder(this)
        .setTitle("Playback diagnostics")
        .setMessage(report)
        .setPositiveButton(
            "Copy report",
            (d, n) -> {
              ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                  .setPrimaryClip(ClipData.newPlainText("Cadence diagnostics", report));
              toast("Report copied");
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private void chooseSpeed() {
    float[] values = {0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f};
    String[] labels = {"0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×", "2.5×", "3×"};
    float speed = getSharedPreferences("playback", 0).getFloat("podcastSpeed", 1f);
    int selected = 1;
    for (int i = 0; i < values.length; i++) if (Math.abs(speed - values[i]) < 0.01) selected = i;
    new AlertDialog.Builder(this)
        .setTitle("Podcast playback speed")
        .setSingleChoiceItems(
            labels,
            selected,
            (d, n) -> {
              getSharedPreferences("playback", 0)
                  .edit()
                  .putFloat("podcastSpeed", values[n])
                  .apply();
              Track t = currentTrack();
              if (controller != null && t != null && t.podcast)
                controller.setPlaybackSpeed(values[n]);
              d.dismiss();
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private <T> void work(String label, Callable<T> task, Consumer<T> done) {
    status.setText(label);
    status.setVisibility(View.VISIBLE);
    workers.execute(
        () -> {
          try {
            T value = task.call();
            ui.post(
                () -> {
                  if (!destroyed) {
                    status.setText("");
                    status.setVisibility(View.GONE);
                    done.accept(value);
                  }
                });
          } catch (Exception e) {
            ui.post(
                () -> {
                  if (!destroyed) error(label.replace("…", ""), e);
                });
          }
        });
  }

  private void error(String title, Exception e) {
    lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
    status.setText(title + " failed");
    status.setVisibility(View.GONE);
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(
            e.getMessage() == null
                ? "Something went wrong. Open playback diagnostics for details."
                : e.getMessage())
        .setPositiveButton("OK", null)
        .show();
  }

  private void save() {
    try {
      library.save();
      CloudSync.changed(this);
    } catch (Exception e) {
      error("Saving library", e);
    }
  }

  private void refreshEpisodeControls() {
    if(podcasts==null) return;
    Track active=currentTrack();
    for(var row:displayedEpisodes.entrySet()) {
      String id=row.getKey(); Podcasts.Episode episode=row.getValue();
      boolean current=active!=null && active.podcast && active.episodeId.equals(id);
      long position=current && controller!=null?Math.max(0,controller.getCurrentPosition()):podcasts.progress(id);
      long length=episode.duration;
      if(current && controller!=null && controller.getDuration()>0) length=controller.getDuration()/1000;
      TextView label=episodeProgress.get(id); boolean done=podcasts.completed(id);
      if(label!=null) label.setText(done && !current?"Played":episodeProgressLabel(position,length));
      ProgressBar bar=episodeBars.get(id);
      if(bar!=null) { bar.setVisibility(length>0?View.VISIBLE:View.GONE); bar.setProgress(done && !current?1000:(int)Math.min(1000,position/Math.max(1,length))); }
      TransportButton button=episodePlayButtons.get(id);
      if(button!=null) {
        boolean playing=current && controller!=null && controller.getPlayWhenReady() && controller.getPlaybackState()!=Player.STATE_ENDED && controller.getPlayerError()==null;
        button.icon(playing?TransportButton.Icon.PAUSE:TransportButton.Icon.PLAY);
        button.setContentDescription((playing?"Pause ":position>0?"Resume ":"Play ")+episode.title);
      }
    }
  }

  static String episodeProgressLabel(long milliseconds,long seconds) {
    long played=Math.max(0,milliseconds)/1000;
    if(seconds>0) played=Math.min(played,seconds);
    String heard=played>=60?(played/60)+" min played":played>0?played+" sec played":"Not started";
    if(seconds<=0) return heard;
    long remaining=Math.max(0,seconds-played);
    return heard+" · "+(remaining>=60?(long)Math.ceil(remaining/60d)+" min":remaining+" sec")+" remaining";
  }

  private void playlistDownloadAction(Library.Playlist playlist) {
    try {
      PlaylistDownloads.Summary state=PlaylistDownloads.summary(this,playlist.id);
      if(state.active()>0) {
        new AlertDialog.Builder(this).setTitle("Cancel remaining downloads?").setMessage("Completed downloads will be kept. Your saved download list stays available for retry.")
            .setNegativeButton("Keep downloading",null).setPositiveButton("Cancel remaining",(d,n)-> {
              try { PlaylistDownloads.cancel(this,playlist.id); refreshPlaylistDownload(); refreshSongDownloads(); } catch(Exception e) { error("Downloads",e); }
            }).show(); return;
      }
      int unmatched=PlaylistDownloads.enqueue(this,playlist);
      toast(unmatched>0?"Downloads queued · "+unmatched+" songs need matching":"Downloads queued · completed songs are skipped");
      refreshPlaylistDownload();
    } catch(Exception e) { error("Playlist downloads",e); }
  }

  private void refreshPlaylistDownload() {
    if(playlistDownload==null || shownPlaylist==null || !page.equals("Playlist")) return;
    try { PlaylistDownloads.Summary state=PlaylistDownloads.summary(this,shownPlaylist.id); playlistDownload.setText(state.label()); }
    catch(Exception ignored) {}
  }

  private org.json.JSONObject profileData() {
    try { return JsonDisk.read(this,"cloud-profile.json"); } catch(Exception ignored) { return new org.json.JSONObject(); }
  }
  private String profileName() {
    String custom=profileData().optString("name");
    if(!custom.isBlank()) return custom;
    String google=CloudSync.prefs(this).getString("googleName",""); return google.isBlank()?"Your profile":google;
  }
  private void refreshProfilePhoto() {
    if(headerPhoto!=null) loadProfilePhoto(headerPhoto);
  }
  private void loadProfilePhoto(Artwork view) {
    String photo=profileData().optString("photo");
    if(!photo.isEmpty()) try {
      byte[] bytes=android.util.Base64.decode(photo,android.util.Base64.DEFAULT);
      android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);
      view.load(""); view.setImageBitmap(image); return;
    } catch(Exception ignored) {}
    view.load(CloudSync.prefs(this).getString("googlePhoto",""));
  }
  private void showProfile() {
    clear("Profile",""); body.addView(text("Your profile",32,INK,true)); gap(20);
    LinearLayout identity=row(); identity.setPadding(0,dp(8),0,dp(16));
    Artwork photo=new Artwork(this); photo.circle(); photo.setContentDescription("Change profile photo"); loadProfilePhoto(photo);
    photo.setOnClickListener(v->chooseProfilePhoto()); identity.addView(photo,new LinearLayout.LayoutParams(dp(72),dp(72)));
    LinearLayout labels=column(); labels.setPadding(dp(16),0,0,0); labels.addView(text(profileName(),23,INK,true));
    labels.addView(text(CloudSync.prefs(this).getString("email","Saved on this phone"),13,MUTED,false));
    identity.addView(labels,new LinearLayout.LayoutParams(0,-2,1)); body.addView(identity); gap(8);
    LinearLayout edit=settingsGroup("Make it yours");
    settingRow(edit,"info","Display name","Your Cadence name",()->ask("Display name","Your name",profileName(),name->{
      if(name.length()>100) { toast("Use a name up to 100 characters."); return; }
      try { org.json.JSONObject data=profileData().put("name",name); JsonDisk.write(this,"cloud-profile.json",data); CloudSync.changed(this); showProfile(); }
      catch(Exception e) { error("Profile",e); }
    }));
    settingDivider(edit); settingRow(edit,"palette","Profile photo","Choose a photo from your phone",this::chooseProfilePhoto);
    if(!CloudSync.subject(this).isEmpty()) {
      settingDivider(edit); settingRow(edit,"restore","Use Google name and photo","Reset your Cadence customisations",()-> {
        try { JsonDisk.write(this,"cloud-profile.json",new org.json.JSONObject()); CloudSync.changed(this); refreshProfilePhoto(); showProfile(); }
        catch(Exception e) { error("Profile",e); }
      });
    }
    if(CloudSync.subject(this).isEmpty()) {
      body.addView(text("Keep your playlists, podcast progress and saved downloads with you. Google sign-in is optional.",15,MUTED,false)); gap(16);
      com.google.android.gms.common.SignInButton signIn=new com.google.android.gms.common.SignInButton(this);
      signIn.setSize(com.google.android.gms.common.SignInButton.SIZE_WIDE); signIn.setColorScheme(com.google.android.gms.common.SignInButton.COLOR_DARK);
      signIn.setOnClickListener(v->googleLogin.signIn(this::acceptGoogleProfile)); body.addView(signIn,new LinearLayout.LayoutParams(-1,dp(52)));
    } else {
      LinearLayout cloud=settingsGroup("Google Drive sync");
      syncStatus=text("",13,MUTED,false); syncStatus.setPadding(dp(16),dp(12),dp(16),dp(12)); cloud.addView(syncStatus); refreshProfileStatus();
      boolean connected=CloudSync.enabled(this);
      settingRow(cloud,"backup",connected?"Sync now":"Connect Google Drive","Hidden Cadence app storage",()-> {
        if(!CloudSync.enabled(this) || CloudSync.prefs(this).getBoolean("needsConsent",false)) googleLogin.connectDrive();
        else { CloudSync.schedule(this); toast("Sync requested"); }
      });
      settingDivider(cloud);
      LinearLayout auto=settingRow(cloud,"restore","Automatic sync","Changes sync when connected",null);
      android.widget.Switch enabled=new android.widget.Switch(this); enabled.setContentDescription("Automatic Drive sync"); enabled.setChecked(CloudSync.automatic(this));
      enabled.setOnCheckedChangeListener((v,value)-> {
        CloudSync.prefs(this).edit().putBoolean("auto",value).apply();
        if(value) googleLogin.connectDrive();
        else { CloudSync.prefs(this).edit().putString("status","Automatic sync paused · Sync now is available").apply();
          androidx.work.WorkManager.getInstance(this).cancelUniqueWork("cadence-cloud-periodic"); }
      }); auto.addView(enabled,new LinearLayout.LayoutParams(dp(52),dp(48)));
      settingDivider(cloud); settingRow(cloud,"restore","Recovery copies","Restore an earlier synced library",this::cloudRecovery);
      settingDivider(cloud); settingRow(cloud,"play","Continue listening","Resume the queue saved in Drive",this::resumeCloudQueue);
      settingDivider(cloud); settingRow(cloud,"close","Sign out","Keep the current library on this phone",()->new AlertDialog.Builder(this).setTitle("Sign out of Google?")
          .setMessage("Sync will stop. Your current library remains on this phone.").setNegativeButton("Cancel",null).setPositiveButton("Sign out",(d,n)-> {
            try { CloudSync.signOut(this); googleLogin.clear(); refreshProfilePhoto(); showProfile(); } catch(Exception e) { error("Sign out",e); }
          }).show());
    }
    gap(16); body.addView(text("Sync stores library information, your Cadence profile and download requests in your Google Drive. Audio files stay on each phone.",13,MUTED,false));
    body.addView(button("Privacy policy",false,()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/naveenkumaarc/Cadence/blob/main/PRIVACY.md")))));
    refreshProfilePhoto();
  }
  private void acceptGoogleProfile(GoogleLogin.Profile profile) {
    if(profile.subject().equals(CloudSync.subject(this))) { googleLogin.connectDrive(); return; }
    new AlertDialog.Builder(this).setTitle("Use this Google account?").setMessage("Choose how to start with "+profile.email()+". Your current library will be kept in a recovery copy on this phone.")
        .setNegativeButton("Cancel",null).setNeutralButton("Use cloud library",(d,n)->activateProfile(profile,false))
        .setPositiveButton("Merge this library",(d,n)->activateProfile(profile,true)).show();
  }
  private void activateProfile(GoogleLogin.Profile profile,boolean merge) {
    try { CloudSync.activate(this,profile.subject(),profile.email(),profile.name(),profile.photo(),merge); refreshProfilePhoto(); showProfile(); googleLogin.connectDrive(); }
    catch(Exception e) { error("Google profile",e); }
  }
  private void refreshProfileStatus() {
    if(syncStatus==null || !page.equals("Profile")) return;
    var prefs=CloudSync.prefs(this); String message=prefs.getString("status","Changes saved on this phone"); long last=prefs.getLong("lastSync",0);
    if(last>0) message += "\nLast synced "+android.text.format.DateUtils.getRelativeTimeSpanString(last,System.currentTimeMillis(),android.text.format.DateUtils.MINUTE_IN_MILLIS);
    syncStatus.setText(message);
  }
  private void chooseProfilePhoto() {
    Intent pick=new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(pick,61);
  }
  private void saveProfilePhoto(Uri uri) {
    work("Saving profile photo…",()-> {
      android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options(); options.inJustDecodeBounds=true;
      try(var input=getContentResolver().openInputStream(uri)) { android.graphics.BitmapFactory.decodeStream(input,null,options); }
      if(options.outWidth<=0 || options.outHeight<=0) throw new java.io.IOException("Choose a supported image.");
      options.inJustDecodeBounds=false; options.inSampleSize=1;
      while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>512) options.inSampleSize*=2;
      android.graphics.Bitmap bitmap;
      try(var input=getContentResolver().openInputStream(uri)) { bitmap=android.graphics.BitmapFactory.decodeStream(input,null,options); }
      if(bitmap==null) throw new java.io.IOException("This photo could not be read.");
      int edge=Math.min(bitmap.getWidth(),bitmap.getHeight());
      android.graphics.Bitmap crop=android.graphics.Bitmap.createBitmap(bitmap,(bitmap.getWidth()-edge)/2,(bitmap.getHeight()-edge)/2,edge,edge);
      android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(crop,256,256,true);
      java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,bytes);
      return android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
    },encoded->{try { JsonDisk.write(this,"cloud-profile.json",profileData().put("photo",encoded)); CloudSync.changed(this); refreshProfilePhoto(); showProfile(); } catch(Exception e) { error("Profile photo",e); }});
  }
  private void cloudApplied() {
    if(destroyed || library==null) return;
    try {
      library=new Library(this); refreshProfilePhoto();
      int scroll=body.getParent() instanceof ScrollView sv?sv.getScrollY():0;
      if(page.equals("Library")) showLibrary();
      else if(page.equals("Profile")) showProfile();
      else if(page.equals("Playlist") && shownPlaylist!=null) {
        Library.Playlist old=shownPlaylist;
        Library.Playlist next=library.playlists.stream().filter(p->p.id.equals(old.id)).findFirst().orElse(old);
        if(old.id.startsWith("cadence:")) showPlaylist(resolvePlaylist(old));
        else if(library.playlists.stream().anyMatch(p->p.id.equals(old.id))) showPlaylist(next); else showLibrary();
      } else if(page.equals("Show") && shownShow!=null) {
        String feed=shownShow.feedUrl;
        for(Podcasts.Show show:podcasts.knownShows()) if(show.feedUrl.equals(feed)) { showPodcast(show); break; }
      }
      if(body.getParent() instanceof ScrollView sv) sv.post(()->sv.scrollTo(0,scroll));
    } catch(Exception e) { toast("Synced library saved. Reopen Cadence to refresh."); }
  }
  private void cloudRecovery() {
    work("Reading recovery copies…",()->CloudSync.history(this),copies->{
      if(copies.isEmpty()) { toast("No earlier cloud copies yet. Copies are kept as your library changes."); return; }
      String[] labels=new String[copies.size()]; for(int i=0;i<labels.length;i++) labels[i]=copies.get(i).optString("modifiedTime").replace('T',' ').replace("Z","");
      new AlertDialog.Builder(this).setTitle("Restore library from Drive").setItems(labels,(d,n)->new AlertDialog.Builder(this).setTitle("Restore this copy?")
          .setMessage("This restores the selected library version. Your current local library is saved as a recovery copy first.")
          .setNegativeButton("Cancel",null).setPositiveButton("Restore",(dialog,which)->work("Restoring library…",()-> { CloudSync.restore(this,copies.get(n).getString("id")); return true; },ignored->toast("Library restored"))).show()).show();
    });
  }
  private void resumeCloudQueue() {
    if(controller==null) { toast("Wait for the player to connect."); return; }
    try {
      org.json.JSONObject saved=JsonDisk.read(this,"cloud-resume.json"); org.json.JSONArray items=saved.optJSONArray("items");
      if(items==null || items.length()==0) { toast("Sync first to retrieve your saved queue."); return; }
      List<MediaItem> queue=new ArrayList<>(); int start=0;
      for(int i=0;i<items.length();i++) { Track track=Library.decode(items.getJSONObject(i)); if(track.sourceUrl.startsWith("https://")) { if(i<=saved.optInt("index")) start=queue.size(); queue.add(mediaItem(track)); } }
      if(queue.isEmpty()) { toast("This queue has no available online tracks."); return; }
      controller.setMediaItems(queue,Math.min(start,queue.size()-1),Math.max(0,saved.optLong("position")));
      controller.setShuffleModeEnabled(saved.optBoolean("shuffle")); int repeat=saved.optInt("repeat"); controller.setRepeatMode(repeat>=0 && repeat<=2?repeat:0);
      controller.prepare(); controller.play(); openPlayer();
    } catch(Exception e) { error("Saved queue",e); }
  }

  private void ask(String title, String hint, String value, Consumer<String> answer) {
    ask(title, hint, value, answer, null);
  }

  private void ask(
      String title, String hint, String value, Consumer<String> answer, String message) {
    EditText edit = input(hint, value);
    LinearLayout box = column();
    box.setPadding(dp(20), dp(10), dp(20), 0);
    if (message != null) box.addView(text(message, 14, MUTED, false));
    box.addView(edit);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Continue", null)
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String entered = edit.getText().toString().trim();
                      if (entered.isEmpty()) {
                        edit.setError("Enter a value");
                        return;
                      }
                      dialog.dismiss();
                      answer.accept(entered);
                    }));
    dialog.show();
  }

  private LinearLayout column() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private LinearLayout row() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.HORIZONTAL);
    layout.setGravity(Gravity.CENTER_VERTICAL);
    return layout;
  }

  private TextView text(String value, float size, int colour, boolean bold) {
    TextView view = new TextView(this);
    view.setText(value);
    view.setTextSize(size);
    view.setTextColor(colour);
    view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    view.setLineSpacing(dp(3), 1);
    return view;
  }

  private Button button(String value, boolean primary, Runnable action) {
    Button view = new Button(this);
    view.setText(value);
    view.setAllCaps(false);
    view.setTextSize(14);
    view.setTextColor(primary ? BG : INK);
    view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    view.setBackground(ripple(primary ? accent : Color.TRANSPARENT, 24));
    view.setPadding(dp(12), 0, dp(12), 0);
    view.setOnClickListener(v -> action.run());
    return view;
  }

  private EditText input(String hint, String value) {
    EditText edit = new EditText(this);
    edit.setText(value);
    edit.setHint(hint);
    edit.setTextColor(INK);
    edit.setHintTextColor(MUTED);
    edit.setTextSize(15);
    edit.setPadding(dp(14), dp(14), dp(14), dp(14));
    edit.setBackground(round(CARD, 12));
    return edit;
  }

  private android.graphics.drawable.RippleDrawable ripple(int colour, int radius) {
    return new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(45, 200, 235, 216)), round(colour, radius), round(Color.WHITE, radius));
  }

  private GradientDrawable round(int colour, int radius) {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(colour);
    shape.setCornerRadius(dp(radius));
    return shape;
  }

  private void gap(int size) {
    View spacer = new View(this);
    body.addView(spacer, new LinearLayout.LayoutParams(1, dp(size)));
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void toast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private static String duration(long seconds) {
    return seconds <= 0
        ? "--:--"
        : seconds >= 3600
            ? String.format(
                java.util.Locale.ROOT,
                "%d:%02d:%02d",
                seconds / 3600,
                (seconds % 3600) / 60,
                seconds % 60)
            : String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
  }

  private static String elapsed(long seconds) {
    return seconds <= 0 ? "0:00" : duration(seconds);
  }
}
