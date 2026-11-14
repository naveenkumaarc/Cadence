package app.cadence.core;

import static org.junit.Assert.*;
import org.junit.Test;

public final class AccentPaletteTest {
  @Test public void everyPresetPassesAaInBothThemes() {
    for(var preset:AccentPalette.PRESETS)for(boolean light:new boolean[]{false,true}) {
      int background=light?0xffffffff:0xff000000;
      assertTrue(preset.label()+" accent text",AccentPalette.contrast(preset.accent(light),background)>=4.5);
      assertTrue(preset.label()+" filled control",AccentPalette.contrast(preset.onAccent(light),preset.accent(light))>=4.5);
    }
  }

  @Test public void customColoursAreAdjustedAndInvalidInputCanBeRejectedByTheCaller() {
    var light=AccentPalette.custom(0xffffff00,true);assertTrue(light.adjusted());
    assertTrue(AccentPalette.contrast(light.accent(),0xffffffff)>=4.5);
    assertTrue(AccentPalette.contrast(light.onAccent(),light.accent())>=4.5);
    var dark=AccentPalette.custom(0xff050505,false);assertTrue(dark.adjusted());
    assertTrue(AccentPalette.contrast(dark.accent(),0xff000000)>=4.5);
    assertFalse("#12GG00".matches("#[0-9a-fA-F]{6}"));
  }

  @Test public void migrationKeepsExistingLooksAndDefaultsNewInstallsToRose() {
    assertEquals("rose",AccentPalette.initialId(false,false));
    assertEquals("classic",AccentPalette.initialId(true,false));
    assertEquals("custom",AccentPalette.initialId(true,true));
  }
}
