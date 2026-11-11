package app.cadence.music;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import androidx.core.graphics.ColorUtils;

/** Cadence's shared semantic design tokens. Appearance preferences stay phone-local. */
final class UiStyle {
  static final int SPACE_1 = 4, SPACE_2 = 8, SPACE_3 = 12, SPACE_4 = 16, SPACE_6 = 24,
      SPACE_8 = 32, SPACE_12 = 48, SECTION = 64, SCREEN_INSET = 16, TOUCH = 48;
  static final int CAPTION = 12, FOOTNOTE = 13, SUBHEAD = 15, CALLOUT = 16, BODY = 17,
      HEADLINE = 17, TITLE = 22, TITLE_LARGE = 28, DISPLAY = 34;
  static final int BODY_LARGE = BODY, HEADING = TITLE_LARGE;
  static final int RADIUS_SMALL = 8, RADIUS_MEDIUM = 11, RADIUS_ARTWORK = 14,
      RADIUS_CARD = 18, RADIUS_SHEET = 24, RADIUS_PILL = 1000;
  static final int MOTION_FAST = 120, MOTION_STANDARD = 220;
  static final int DOWNLOAD_GREEN = 0xff248a55;
  static final int DESTRUCTIVE = 0xffff3b30;

  record Palette(
      int background, int surface, int raised, int ink, int muted, int accent, int onAccent,
      int outline, int track, int divider, int ripple, boolean light) {}

  static final Palette DARK = new Palette(
      0xff000000, 0xff1c1c1e, 0xff2c2c2e, 0xfff5f5f7, 0xffaeaeb2, 0xff2997ff,
      Color.WHITE, 0xff3a3a3c, 0xff48484a, 0xff38383a, 0x332997ff, false);
  static final Palette LIGHT = new Palette(
      Color.WHITE, 0xfff5f5f7, 0xfffafafc, 0xff1d1d1f, 0xff6e6e73, 0xff0066cc,
      Color.WHITE, 0xffd2d2d7, 0xffd2d2d7, 0xffe0e0e0, 0x220066cc, true);

  static android.content.SharedPreferences preferences(Context context) {
    return context.getSharedPreferences("appearance", 0);
  }

  static String theme(Context context) {
    return preferences(context).getString("theme", "Dark");
  }

  static Palette palette(Context context) {
    String choice = theme(context);
    boolean systemLight = (context.getResources().getConfiguration().uiMode
        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;
    boolean light = choice.equals("Light") || choice.equals("Follow device") && systemLight;
    if (!preferences(context).contains("colour")) return light ? LIGHT : DARK;
    return scheme(preferences(context).getInt("colour", LIGHT.accent()), light);
  }

  static Palette scheme(int colour, boolean light) {
    Palette base = light ? LIGHT : DARK;
    float[] hsv = new float[3];
    Color.colorToHSV(colour, hsv);
    float hue = hsv[0], saturation = Math.min(.92f, Math.max(.35f, hsv[1]));
    int accent = tone(hue, saturation, light ? .72f : 1f);
    for (int i = 0; i < 20 && ColorUtils.calculateContrast(accent, base.background()) < 4.5; i++)
      accent = ColorUtils.blendARGB(accent, light ? Color.BLACK : Color.WHITE, .06f);
    int onAccent = ColorUtils.calculateContrast(Color.WHITE, accent)
            >= ColorUtils.calculateContrast(0xff1d1d1f, accent) ? Color.WHITE : 0xff1d1d1f;
    for (int i = 0; i < 20 && ColorUtils.calculateContrast(onAccent, accent) < 4.5; i++)
      accent = ColorUtils.blendARGB(accent, onAccent == Color.WHITE ? Color.BLACK : Color.WHITE, .05f);
    int ripple = (accent & 0x00ffffff) | (light ? 0x22000000 : 0x33000000);
    return new Palette(base.background(), base.surface(), base.raised(), base.ink(), base.muted(),
        accent, onAccent, base.outline(), base.track(), base.divider(), ripple, light);
  }

  private static int tone(float hue, float saturation, float value) {
    return Color.HSVToColor(new float[] {hue, saturation, value});
  }

  static boolean motion(Context context) {
    return preferences(context).getBoolean("motion", true)
        && android.animation.ValueAnimator.areAnimatorsEnabled();
  }

  static void feedback(View view) {
    if (preferences(view.getContext()).getBoolean("haptics", true))
      view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
  }

  static void press(View view, Runnable action) {
    feedback(view);
    if (!motion(view.getContext())) { action.run(); return; }
    view.animate().cancel();
    view.animate().scaleX(.96f).scaleY(.96f).alpha(.82f).setDuration(MOTION_FAST / 2)
        .setListener(new AnimatorListenerAdapter() {
          @Override public void onAnimationEnd(Animator animation) {
            action.run();
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(MOTION_FAST)
                .setListener(null).start();
          }
        }).start();
  }

  static int dp(Context context, float value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  static int space(int requested) {
    int[] scale = {SPACE_1, SPACE_2, SPACE_3, SPACE_4, SPACE_6, SPACE_8, SPACE_12, SECTION};
    int best = scale[0];
    for (int value : scale)
      if (Math.abs(value - requested) <= Math.abs(best - requested)) best = value;
    return requested == 0 ? 0 : best;
  }

  static int radius(int requested) {
    if (requested <= RADIUS_SMALL) return RADIUS_SMALL;
    if (requested <= RADIUS_MEDIUM + 1) return RADIUS_MEDIUM;
    if (requested <= RADIUS_ARTWORK + 2) return RADIUS_ARTWORK;
    if (requested <= RADIUS_CARD + 3) return RADIUS_CARD;
    if (requested <= RADIUS_SHEET + 4) return RADIUS_SHEET;
    return RADIUS_PILL;
  }

  static float type(float requested) {
    if (requested <= 12) return CAPTION;
    if (requested <= 13) return FOOTNOTE;
    if (requested <= 15) return SUBHEAD;
    if (requested <= 16) return CALLOUT;
    if (requested <= 18) return BODY;
    if (requested <= 23) return TITLE;
    if (requested <= 30) return TITLE_LARGE;
    return DISPLAY;
  }

  static Typeface typeface(boolean emphasized) {
    return Typeface.create("sans-serif", emphasized ? Typeface.BOLD : Typeface.NORMAL);
  }

  static GradientDrawable shape(Context context, int colour, int radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(colour);
    drawable.setCornerRadius(dp(context, radius));
    return drawable;
  }

  static GradientDrawable outlinedShape(Context context, int colour, int stroke, int radius) {
    GradientDrawable drawable = shape(context, colour, radius);
    drawable.setStroke(dp(context, 1), stroke);
    return drawable;
  }

  static RippleDrawable ripple(Context context, int colour, int radius) {
    return new RippleDrawable(ColorStateList.valueOf(palette(context).ripple()),
        shape(context, colour, radius(radius)), shape(context, Color.WHITE, radius(radius)));
  }

  private UiStyle() {}
}
