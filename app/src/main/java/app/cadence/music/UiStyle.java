package app.cadence.music;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;

/** Shared semantic colours, spacing, type and interaction preferences. Phone-local by design. */
final class UiStyle {
  static final int SPACE_1=4, SPACE_2=8, SPACE_3=12, SPACE_4=16, SPACE_6=24, SPACE_8=32, TOUCH=48;
  static final int CAPTION=12, BODY=14, BODY_LARGE=16, TITLE=20, HEADING=24, DISPLAY=32;
  static final int DOWNLOAD_GREEN=Color.rgb(37,153,96);
  record Palette(int background,int surface,int raised,int ink,int muted,int accent,int onAccent,int outline,int track,int ripple,boolean light) {}
  static final Palette DARK=new Palette(0xff101214,0xff1d2023,0xff27352e,0xfff5f7f6,0xffb0bab5,0xff9fdabb,0xff11271b,0xff354039,0xff59645e,0x35c8ebd8,false);
  static final Palette LIGHT=new Palette(0xfff5f8f6,0xffffffff,0xffe1eee6,0xff15251c,0xff4e6055,0xff286b49,0xffffffff,0xffcad8ce,0xffb4c6bb,0x25286b49,true);
  static android.content.SharedPreferences preferences(Context c) {return c.getSharedPreferences("appearance",0);}
  static String theme(Context c) {return preferences(c).getString("theme","Dark");}
  static Palette palette(Context c) {
    String choice=theme(c);boolean system=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_NO;
    return choice.equals("Light")||choice.equals("Follow device")&&system?LIGHT:DARK;
  }
  static boolean motion(Context c) {return preferences(c).getBoolean("motion",true)&&android.animation.ValueAnimator.areAnimatorsEnabled();}
  static void feedback(View v) {if(preferences(v.getContext()).getBoolean("haptics",true))v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);}
  static int dp(Context c,float value) {return Math.round(value*c.getResources().getDisplayMetrics().density);}
  static int space(int requested) {int[] scale={4,8,12,16,24,32};int best=scale[0];for(int n:scale)if(Math.abs(n-requested)<=Math.abs(best-requested))best=n;return requested==0?0:requested>32?requested:best;}
  static float type(float requested) {return requested<=13?CAPTION:requested<=14?BODY:requested<=16?BODY_LARGE:requested<=20?TITLE:requested<=25?HEADING:DISPLAY;}
  static GradientDrawable shape(Context c,int colour,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(colour);d.setCornerRadius(dp(c,radius));return d;}
  static RippleDrawable ripple(Context c,int colour,int radius) {return new RippleDrawable(ColorStateList.valueOf(palette(c).ripple()),shape(c,colour,radius),shape(c,Color.WHITE,radius));}
  private UiStyle() {}
}
