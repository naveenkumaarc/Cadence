package app.cadence.core;

import java.util.*;

/** Index-only queue planning keeps duplicate songs distinct and avoids copying large metadata lists. */
public final class QueueShuffle {
  public record Chunk(int fromInclusive,int toExclusive,int insertAt) {}
  private QueueShuffle() {}

  public static int[] fisherYates(int size,long seed) {
    if(size<0)throw new IllegalArgumentException("Negative queue size");
    int[] order=new int[size];for(int i=0;i<size;i++)order[i]=i;
    Random random=new Random(seed);
    for(int i=size-1;i>0;i--){int j=random.nextInt(i+1),value=order[i];order[i]=order[j];order[j]=value;}
    return order;
  }

  public static int[] natural(int size) {
    int[] order=new int[size];for(int i=0;i<size;i++)order[i]=i;return order;
  }

  public static boolean valid(int[] order,int size) {
    if(order==null||order.length!=size)return false;boolean[] used=new boolean[size];
    for(int value:order)if(value<0||value>=size||used[value])return false;else used[value]=true;
    return true;
  }

  /** Prefix chunks are inserted before the starter; suffix chunks append after it. */
  public static List<Chunk> chunks(int size,int starter,int chunkSize) {
    if(size<1||starter<0||starter>=size||chunkSize<1)throw new IllegalArgumentException();
    List<Chunk> out=new ArrayList<>();
    for(int from=0;from<starter;from+=chunkSize)out.add(new Chunk(from,Math.min(starter,from+chunkSize),from));
    for(int from=starter+1;from<size;from+=chunkSize)out.add(new Chunk(from,Math.min(size,from+chunkSize),from));
    return out;
  }

  /** Converts the full original-index traversal into the current partially-loaded player indices. */
  public static int[] loadedOrder(int[] full,int prefixLoaded,int suffixExclusive,int starter) {
    if(!valid(full,full.length)||starter<0||starter>=full.length||prefixLoaded<0||prefixLoaded>starter
        ||suffixExclusive<starter+1||suffixExclusive>full.length)throw new IllegalArgumentException();
    int count=prefixLoaded+1+suffixExclusive-(starter+1),at=0;int[] out=new int[count];
    for(int original:full) {
      if(original<prefixLoaded)out[at++]=original;
      else if(original==starter)out[at++]=prefixLoaded;
      else if(original>starter&&original<suffixExclusive)out[at++]=prefixLoaded+(original-starter);
    }
    if(at!=count)throw new IllegalStateException();return out;
  }
}
