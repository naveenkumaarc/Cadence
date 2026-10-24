package app.cadence.core;

import com.google.gson.*;
import java.util.*;

/** Per-field registers with persistent deletion markers. Each device publishes its own document. */
public final class SyncState {
  private record Cell(long clock, String actor, JsonElement value) {}
  private final String actor;
  private long clock;
  private final TreeMap<String, Cell> cells = new TreeMap<>();
  private JsonObject observed = new JsonObject();

  public SyncState(String actor) {
    if (actor == null || !actor.matches("[A-Za-z0-9-]{1,100}")) throw new IllegalArgumentException("Invalid device identity");
    this.actor = actor;
  }

  /** Compare with what this device last applied, never with an un-applied remote document. */
  public boolean capture(JsonObject snapshot) {
    Set<String> keys = new TreeSet<>(observed.keySet()); keys.addAll(snapshot.keySet());
    boolean changed = false;
    for (String key : keys) {
      JsonElement before = observed.get(key), after = snapshot.get(key);
      if (Objects.equals(before, after)) continue;
      if (key.length() > 2048) throw new IllegalArgumentException("Sync key is too long");
      if (clock >= Long.MAX_VALUE - 1) throw new IllegalStateException("Invalid sync clock");
      clock = Math.max(clock + 1, System.currentTimeMillis());
      cells.put(key, new Cell(clock, actor, after == null ? JsonNull.INSTANCE : after.deepCopy()));
      changed = true;
    }
    observed = snapshot.deepCopy();
    return changed;
  }

  public void applied(JsonObject snapshot) { observed = snapshot.deepCopy(); }

  public void merge(SyncState other) {
    for (var entry : other.cells.entrySet()) {
      Cell incoming = entry.getValue(), current = cells.get(entry.getKey());
      if (current == null || compare(incoming, current) > 0) cells.put(entry.getKey(), incoming);
      clock = Math.max(clock, incoming.clock);
    }
  }

  private static int compare(Cell a, Cell b) {
    int result = Long.compare(a.clock, b.clock);
    if (result == 0) result = a.actor.compareTo(b.actor);
    // A deterministic final comparison also makes corrupted duplicate device IDs converge.
    return result == 0 ? a.value.toString().compareTo(b.value.toString()) : result;
  }

  public JsonObject materialize() {
    JsonObject out = new JsonObject();
    for (var entry : cells.entrySet()) if (!entry.getValue().value.isJsonNull())
      out.add(entry.getKey(), entry.getValue().value.deepCopy());
    return out;
  }
  public long changedAt(String key) {Cell cell=cells.get(key);return cell==null?0:cell.clock;}
  public String changedBy(String key) {Cell cell=cells.get(key);return cell==null?"":cell.actor;}

  public String document(boolean local) {
    JsonObject root = new JsonObject(); root.addProperty("format", "cadence.sync"); root.addProperty("version", 1);
    root.addProperty("actor", actor); root.addProperty("clock", clock);
    JsonObject entries = new JsonObject();
    for (var entry : cells.entrySet()) {
      Cell cell = entry.getValue(); JsonObject row = new JsonObject();
      row.addProperty("clock", cell.clock); row.addProperty("actor", cell.actor); row.add("value", cell.value);
      entries.add(entry.getKey(), row);
    }
    root.add("entries", entries); if (local) root.add("observed", observed);
    return root.toString();
  }

  public static SyncState parse(String json, String localActor) {
    if (json.length() > 20 * 1024 * 1024) throw new IllegalArgumentException("Sync document is too large");
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    if (!root.get("format").getAsString().equals("cadence.sync") || root.get("version").getAsInt() != 1)
      throw new IllegalArgumentException("Unsupported sync document");
    SyncState state = new SyncState(localActor);
    JsonObject entries = root.getAsJsonObject("entries");
    if (entries.size() > 100000) throw new IllegalArgumentException("Sync document has too many entries");
    for (var entry : entries.entrySet()) {
      JsonObject row = entry.getValue().getAsJsonObject();
      String source = row.get("actor").getAsString(); long stamp = row.get("clock").getAsLong();
      if (entry.getKey().length() > 2048 || !source.matches("[A-Za-z0-9-]{1,100}") || stamp < 0 || stamp >= Long.MAX_VALUE - 1)
        throw new IllegalArgumentException("Invalid sync entry");
      state.cells.put(entry.getKey(), new Cell(stamp, source, row.get("value").deepCopy()));
      state.clock = Math.max(state.clock, stamp);
    }
    if (root.has("observed")) state.observed = root.getAsJsonObject("observed").deepCopy();
    return state;
  }
}
