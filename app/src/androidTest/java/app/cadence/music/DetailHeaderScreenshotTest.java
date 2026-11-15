package app.cadence.music;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.MusicCatalog;
import app.cadence.core.Track;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Album-detail regression captures at every supported accessibility scale and theme. */
@RunWith(AndroidJUnit4.class)
public final class DetailHeaderScreenshotTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();
  private MainActivity activity;

  private interface Checked { void run() throws Exception; }
  private void main(Checked task) {
    instrumentation.runOnMainSync(() -> {
      try { task.run(); } catch (Exception error) { throw new RuntimeException(error); }
    });
  }
  private Object field(String name) throws Exception {
    Field field = MainActivity.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(activity);
  }
  private void shell(String command) throws Exception {
    try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(command);
         FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
      byte[] buffer = new byte[4096];
      while (input.read(buffer) != -1) {}
    }
  }
  private View described(View view, String value) {
    if (value.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
      return view;
    if (view instanceof ViewGroup group)
      for (int index = 0; index < group.getChildCount(); index++) {
        View found = described(group.getChildAt(index), value);
        if (found != null) return found;
      }
    return null;
  }
  private <T extends View> T find(View view, Class<T> type) {
    if (type.isInstance(view)) return type.cast(view);
    if (view instanceof ViewGroup group)
      for (int index = 0; index < group.getChildCount(); index++) {
        T found = find(group.getChildAt(index), type);
        if (found != null) return found;
      }
    return null;
  }
  private TextView text(View view, String value) {
    if (view instanceof TextView label && value.contentEquals(label.getText())) return label;
    if (view instanceof ViewGroup group)
      for (int index = 0; index < group.getChildCount(); index++) {
        TextView found = text(group.getChildAt(index), value);
        if (found != null) return found;
      }
    return null;
  }

  @Test public void albumHeaderDoesNotOverlapAtSupportedScalesInLightAndDark() throws Exception {
    String originalTheme = UiStyle.theme(context);
    float originalScale = context.getResources().getConfiguration().fontScale;
    try {
      for (String theme : new String[] {"Light", "Dark"})
        for (float scale : new float[] {1f, 1.3f, 2f}) {
          shell("settings put system font_scale " + scale);
          AppearanceStore.setBlocking(context, theme);
          activity = (MainActivity) instrumentation.startActivitySync(
              new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
          MusicCatalog.Entry album = new MusicCatalog.Entry();
          album.kind = "album";
          album.title = "Accessibility Album";
          album.artist = "Cadence Ensemble";
          album.year = "2026";
          album.browseId = "MPRE_accessibility_fixture";
          List<MusicCatalog.Entry> items = new ArrayList<>();
          for (int index = 1; index <= 4; index++) {
            MusicCatalog.Entry item = new MusicCatalog.Entry();
            item.kind = "song";
            item.track = new Track("Track " + index, "Cadence Ensemble",
                "https://www.youtube.com/watch?v=fixture0000" + index, "", 180 + index);
            items.add(item);
          }
          main(() -> {
            Method render = MainActivity.class.getDeclaredMethod(
                "renderCollection", MusicCatalog.Entry.class, List.class);
            render.setAccessible(true);
            render.invoke(activity, album, items);
          });
          instrumentation.waitForIdleSync();
          View root = activity.getWindow().getDecorView();
          assertNotNull(described(root, "Back"));
          assertNotNull(text(root, album.title));
          DetailActionsLayout actions = find(root, DetailActionsLayout.class);
          assertNotNull(actions);
          assertTrue(actions.getMeasuredHeight() >= UiStyle.dp(context, UiStyle.TOUCH));
          for (int first = 0; first < actions.getChildCount(); first++)
            for (int second = first + 1; second < actions.getChildCount(); second++) {
              Rect a = new Rect(), b = new Rect();
              actions.getChildAt(first).getHitRect(a);
              actions.getChildAt(second).getHitRect(b);
              assertFalse("Detail actions overlap at " + scale, Rect.intersects(a, b));
            }
          LinearLayout body = (LinearLayout) field("body");
          int actionIndex = body.indexOfChild(actions);
          assertTrue(actionIndex >= 0 && actionIndex + 1 < body.getChildCount());
          assertTrue(body.getChildAt(actionIndex + 1).getMeasuredHeight() >= UiStyle.dp(context, 20));
          String suffix = String.format(Locale.ROOT, "%d", Math.round(scale * 100));
          shell("screencap -p /data/local/tmp/cadence-album-" + theme.toLowerCase(Locale.ROOT)
              + "-" + suffix + ".png");
          main(activity::finish);
          instrumentation.waitForIdleSync();
          activity = null;
        }
    } finally {
      if (activity != null) main(activity::finish);
      AppearanceStore.setBlocking(context, originalTheme);
      shell("settings put system font_scale " + originalScale);
    }
  }
}
