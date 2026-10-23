package app.cadence.core;

import java.util.List;

/** Chooses a radio seed from actual playback traversal, including manual queue entries. */
public final class RadioPlan {
  private RadioPlan() {}
  public static final class Item {
    public final String source;
    public final boolean radio, podcast;
    public Item(String source, boolean radio, boolean podcast) {
      this.source = source; this.radio = radio; this.podcast = podcast;
    }
  }
  public static String seed(List<Item> order, int current) {
    if (current < 0 || current >= order.size() || order.size() - current > 3) return "";
    // Podcast playback and an explicitly queued podcast must not turn into music radio.
    for (int i=current; i<order.size(); i++) if (order.get(i).podcast) return "";
    for (int i=order.size()-1; i>=0; i--) {
      Item item=order.get(i);
      if (item.radio && !item.podcast && item.source.startsWith("https://www.youtube.com/watch?v="))
        return item.source;
    }
    return "";
  }
}
