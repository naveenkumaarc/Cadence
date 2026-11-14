package app.cadence.core;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public final class QueueShuffleTest {
  @Test public void everyRequestedPlaylistSizeProducesAFullPermutation() {
    for(int size:new int[]{3,50,500,2000}) {
      int[] order=QueueShuffle.fisherYates(size,1234+size);
      assertTrue(QueueShuffle.valid(order,size));
      assertFalse("shuffle should change a multi-item queue",Arrays.equals(order,QueueShuffle.natural(size)));
      int starter=order[0];List<QueueShuffle.Chunk> chunks=QueueShuffle.chunks(size,starter,64);
      int loaded=1;for(var chunk:chunks)loaded+=chunk.toExclusive()-chunk.fromInclusive();
      assertEquals(size,loaded);
      assertArrayEquals(order,QueueShuffle.loadedOrder(order,starter,size,starter));
    }
  }

  @Test public void duplicateSongsRemainSeparateQueueEntries() {
    List<String> songs=List.of("A","B","A","C","A");int[] order=QueueShuffle.fisherYates(songs.size(),91);
    List<String> shuffled=new ArrayList<>();for(int index:order)shuffled.add(songs.get(index));
    assertEquals(5,shuffled.size());assertEquals(3,shuffled.stream().filter("A"::equals).count());
  }

  @Test public void progressiveOrderStartsWithSelectedAndEndsAsSavedOrder() {
    int[] full=QueueShuffle.fisherYates(500,44);int starter=full[0];
    int prefix=Math.min(starter,64),suffix=Math.min(500,starter+1+64);
    int[] partial=QueueShuffle.loadedOrder(full,prefix,suffix,starter);
    assertEquals(prefix,partial[0]);
    assertTrue(QueueShuffle.valid(QueueShuffle.loadedOrder(full,starter,500,starter),500));
  }

  @Test public void invalidSavedOrdersAreRejected() {
    assertFalse(QueueShuffle.valid(new int[]{0,1,1},3));
    assertFalse(QueueShuffle.valid(new int[]{0,1},3));
    assertFalse(QueueShuffle.valid(new int[]{0,1,3},3));
  }
}
