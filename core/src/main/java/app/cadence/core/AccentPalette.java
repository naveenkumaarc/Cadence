package app.cadence.core;

import java.util.*;

/** Contrast-checked accent definitions shared by theme code and JVM tests. */
public final class AccentPalette {
  public record Preset(String id,String label,int dark,int light,int onDark,int onLight) {
    public int accent(boolean lightTheme){return lightTheme?light:dark;}
    public int onAccent(boolean lightTheme){return lightTheme?onLight:onDark;}
  }
  public record Resolved(int accent,int onAccent,boolean adjusted) {}

  public static final List<Preset> PRESETS=List.of(
      new Preset("rose","Rose",0xffda354b,0xffd12b45,0xffffffff,0xffffffff),
      new Preset("violet","Violet",0xff7a5af5,0xff6244e8,0xffffffff,0xffffffff),
      new Preset("indigo","Indigo blue",0xff496bee,0xff3f5be0,0xffffffff,0xffffffff),
      new Preset("coral","Coral",0xffff7a59,0xffd0472b,0xff1f0b04,0xffffffff),
      new Preset("teal","Teal",0xff2dd4bf,0xff0b7f75,0xff00221d,0xffffffff),
      new Preset("amber","Amber",0xffffb020,0xffa86200,0xff2a1800,0xffffffff),
      new Preset("monochrome","Monochrome",0xffffffff,0xff111111,0xff000000,0xffffffff),
      new Preset("classic","Classic blue",0xff2077c9,0xff0066cc,0xffffffff,0xffffffff));

  public static Preset preset(String id){return PRESETS.stream().filter(p->p.id.equals(id)).findFirst().orElse(PRESETS.get(0));}
  public static String initialId(boolean existingInstall,boolean legacyCustom){return legacyCustom?"custom":existingInstall?"classic":"rose";}

  public static Resolved custom(int requested,boolean lightTheme) {
    int accent=0xff000000|(requested&0xffffff),background=lightTheme?0xffffffff:0xff000000;
    boolean adjusted=false;int toward=lightTheme?0xff000000:0xffffffff;
    for(int i=0;i<40&&contrast(accent,background)<4.5;i++){accent=blend(accent,toward,.05);adjusted=true;}
    int white=0xffffffff,black=0xff000000,on=contrast(white,accent)>=contrast(black,accent)?white:black;
    return new Resolved(accent,on,adjusted);
  }

  public static double contrast(int first,int second) {
    double a=luminance(first),b=luminance(second);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);
  }
  private static double luminance(int colour) {
    double r=linear((colour>>16)&255),g=linear((colour>>8)&255),b=linear(colour&255);
    return .2126*r+.7152*g+.0722*b;
  }
  private static double linear(int value){double v=value/255d;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
  private static int blend(int from,int to,double amount) {
    int r=(int)Math.round(((from>>16)&255)*(1-amount)+((to>>16)&255)*amount);
    int g=(int)Math.round(((from>>8)&255)*(1-amount)+((to>>8)&255)*amount);
    int b=(int)Math.round((from&255)*(1-amount)+(to&255)*amount);return 0xff000000|(r<<16)|(g<<8)|b;
  }
  private AccentPalette(){}
}
