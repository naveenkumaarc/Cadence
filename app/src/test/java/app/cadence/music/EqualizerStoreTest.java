package app.cadence.music;

import static org.junit.Assert.*;

import org.junit.Test;

public final class EqualizerStoreTest {
  @Test public void presetAndBandProfileRoundTripsWithoutPrecisionLoss(){
    short[] bands={-1500,-225,0,375,1500};
    String persisted=EqualizerStore.levels(bands);
    EqualizerStore.Profile profile=new EqualizerStore.Profile(true,3,persisted);
    assertTrue(profile.enabled());
    assertEquals(3,profile.preset());
    assertArrayEquals(bands,EqualizerStore.levels(profile.levels(),bands.length));
  }

  @Test public void restoredBandsAdaptToDeviceBandCountAndBadLegacyValues(){
    assertArrayEquals(new short[]{100,-200,0,0},EqualizerStore.levels("100,-200,bad",4));
    assertArrayEquals(new short[]{100},EqualizerStore.levels("100,-200,300",1));
    assertArrayEquals(new short[]{0,0},EqualizerStore.levels("",2));
  }
}
