package app.cadence.core;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SyncStateTest {
  private JsonObject snapshot(String... fields) {
    JsonObject out = new JsonObject(); for (int i=0;i<fields.length;i+=2) out.addProperty(fields[i],fields[i+1]); return out;
  }
  @Test public void concurrentChangesMergeInEitherOrder() {
    SyncState a=new SyncState("phone-a"), b=new SyncState("phone-b");
    a.capture(snapshot("playlist/a", "One", "profile/name", "A"));
    b.capture(snapshot("playlist/b", "Two", "profile/name", "B"));
    SyncState ab=SyncState.parse(a.document(false),"test"), ba=SyncState.parse(b.document(false),"test");
    ab.merge(b); ba.merge(a); assertEquals(ab.materialize(),ba.materialize());
    assertEquals(3,ab.materialize().size()); ab.merge(b); assertEquals(ab.materialize(),ba.materialize());
  }
  @Test public void unchangedOfflinePhoneDoesNotResurrectDeletion() {
    SyncState a=new SyncState("a"); JsonObject first=snapshot("playlist/one","Saved"); a.capture(first);
    SyncState b=SyncState.parse(a.document(false),"b"); b.applied(first);
    a.capture(new JsonObject()); b.capture(first); b.merge(a);
    assertFalse(b.materialize().has("playlist/one"));
    SyncState restored=SyncState.parse(b.document(true),"b"); restored.capture(first);
    assertFalse(restored.materialize().has("playlist/one"));
  }
  @Test public void newDeviceReceivesRemoteFieldsWithoutManufacturingDeletes() {
    SyncState a=new SyncState("a"), b=new SyncState("b");
    a.capture(snapshot("playlist/a","One")); b.capture(new JsonObject()); b.merge(a);
    b.applied(b.materialize()); assertFalse(b.capture(b.materialize()));
    assertEquals(a.materialize(), b.materialize());
  }
  @Test public void deletionOfOneFieldPreservesConcurrentUnrelatedAddition() {
    SyncState a=new SyncState("a"); JsonObject initial=snapshot("song/one","One"); a.capture(initial);
    SyncState b=SyncState.parse(a.document(false),"b"); b.applied(initial);
    a.capture(new JsonObject()); b.capture(snapshot("song/one","One","song/two","Two"));
    a.merge(b); assertFalse(a.materialize().has("song/one")); assertTrue(a.materialize().has("song/two"));
  }
  @Test public void invalidRemoteDocumentIsRejected() {
    try { SyncState.parse("{\"format\":\"cadence.sync\",\"version\":99}","a"); fail(); }
    catch (IllegalArgumentException expected) {}
  }
}
