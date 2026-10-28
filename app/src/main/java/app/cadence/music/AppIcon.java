package app.cadence.music;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small native icon set with generous touch targets. */
final class AppIcon extends View {
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private String name;
  void symbol(String value) { name = value; invalidate(); }
  private int colour;

  AppIcon(Context c, String name, int colour, String label) {
    super(c);
    this.name = name;
    this.colour = colour;
    setContentDescription(label);
    setFocusable(true);setMinimumWidth(UiStyle.dp(c,48));setMinimumHeight(UiStyle.dp(c,48));setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(android.widget.Button.class.getName());}});
  }

  void colour(int value) {
    colour = value;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.save();
    float scale = Math.min(getWidth(), getHeight()) / 48f;
    canvas.translate((getWidth() - 48 * scale) / 2, (getHeight() - 48 * scale) / 2);
    canvas.scale(scale, scale);
    p.setColor(colour);
    p.setStrokeWidth(2);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setStyle(Paint.Style.STROKE);
    path.reset();
    switch (name) {
      case "more", "more_vert" -> {p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)canvas.drawCircle(24,17+i*7,1.7f,p);}
      case "refresh" -> {canvas.drawArc(13,13,35,35,-45,290,false,p);path.moveTo(35,13);path.lineTo(35,21);path.lineTo(27,21);canvas.drawPath(path,p);}
      case "save" -> {
        canvas.drawCircle(24, 24, 11, p);
        canvas.drawLine(24, 18, 24, 30, p); canvas.drawLine(18, 24, 30, 24, p);
      }
      case "shuffle" -> {
        path.moveTo(13,16); path.lineTo(18,16); path.cubicTo(23,16,25,32,30,32); path.lineTo(35,32);
        path.moveTo(31,28); path.lineTo(35,32); path.lineTo(31,36);
        path.moveTo(13,32); path.lineTo(18,32); path.cubicTo(21,32,22,28,24,24);
        path.moveTo(25,20); path.cubicTo(27,16,29,16,35,16);
        path.moveTo(31,12); path.lineTo(35,16); path.lineTo(31,20); canvas.drawPath(path,p);
      }
      case "repeat", "repeatOne" -> {
        path.moveTo(15,20); path.lineTo(15,17); path.lineTo(34,17); path.lineTo(30,13);
        path.moveTo(34,17); path.lineTo(30,21);
        path.moveTo(33,28); path.lineTo(33,31); path.lineTo(14,31); path.lineTo(18,35);
        path.moveTo(14,31); path.lineTo(18,27); canvas.drawPath(path,p);
        if (name.equals("repeatOne")) { p.setStyle(Paint.Style.FILL); p.setTextSize(11); p.setTextAlign(Paint.Align.CENTER); canvas.drawText("1",24,28,p); }
      }
      case "home" -> {
        path.moveTo(13, 22);
        path.lineTo(24, 13);
        path.lineTo(35, 22);
        path.lineTo(35, 35);
        path.lineTo(27, 35);
        path.lineTo(27, 27);
        path.lineTo(21, 27);
        path.lineTo(21, 35);
        path.lineTo(13, 35);
        path.close();
        canvas.drawPath(path, p);
      }
      case "search" -> {
        canvas.drawCircle(22, 22, 9, p);
        canvas.drawLine(29, 29, 36, 36, p);
      }
      case "library" -> {
        canvas.drawLine(14, 14, 14, 35, p);
        canvas.drawLine(21, 14, 21, 35, p);
        canvas.drawLine(28, 15, 35, 34, p);
      }
      case "podcasts" -> {
        canvas.drawCircle(24, 21, 3, p);
        canvas.drawArc(15, 12, 33, 30, 140, 260, false, p);
        canvas.drawArc(10, 7, 38, 35, 135, 270, false, p);
        canvas.drawRoundRect(21, 27, 27, 38, 3, 3, p);
      }
      case "settings" -> {
        canvas.drawLine(13, 17, 19, 17, p); canvas.drawLine(25, 17, 35, 17, p);
        canvas.drawLine(13, 31, 25, 31, p); canvas.drawLine(31, 31, 35, 31, p);
        canvas.drawCircle(22, 17, 3, p); canvas.drawCircle(28, 31, 3, p);
      }
      case "queue" -> {
        canvas.drawLine(12, 15, 35, 15, p); canvas.drawLine(12, 23, 25, 23, p);
        canvas.drawLine(12, 31, 20, 31, p);
        path.moveTo(31, 32); path.lineTo(31, 23); path.lineTo(37, 21);
        canvas.drawPath(path, p); canvas.drawOval(25, 30, 31, 35, p);
      }
      case "arrow" -> {
        canvas.drawLine(14, 24, 33, 24, p);
        path.moveTo(26, 17); path.lineTo(33, 24); path.lineTo(26, 31); canvas.drawPath(path, p);
      }
      case "chevron" -> {
        path.moveTo(21, 16); path.lineTo(29, 24); path.lineTo(21, 32); canvas.drawPath(path, p);
      }
      case "language" -> {
        canvas.drawCircle(24, 24, 11, p); canvas.drawOval(19, 13, 29, 35, p);
        canvas.drawLine(13, 24, 35, 24, p);
      }
      case "crossfade" -> {
        path.moveTo(13, 17); path.cubicTo(25, 17, 23, 31, 35, 31);
        path.moveTo(13, 31); path.cubicTo(25, 31, 23, 17, 35, 17); canvas.drawPath(path, p);
      }
      case "backup", "restore", "import" -> {
        path.moveTo(15, 14); path.lineTo(15, 35); path.lineTo(34, 35); path.lineTo(34, 14); path.close();
        canvas.drawPath(path, p);
        boolean up = name.equals("backup");
        canvas.drawLine(24, 19, 24, 29, p);
        path.reset(); path.moveTo(20, up ? 23 : 25); path.lineTo(24, up ? 19 : 29); path.lineTo(28, up ? 23 : 25);
        canvas.drawPath(path, p);
      }
      case "palette" -> {
        canvas.drawCircle(24, 24, 11, p);
        p.setStyle(Paint.Style.FILL); canvas.drawCircle(20, 20, 2, p);
        canvas.drawCircle(28, 20, 2, p); canvas.drawCircle(20, 28, 2, p);
      }
      case "widget" -> {
        canvas.drawRoundRect(13, 13, 23, 23, 2, 2, p); canvas.drawRoundRect(27, 13, 35, 23, 2, 2, p);
        canvas.drawRoundRect(13, 27, 23, 35, 2, 2, p); canvas.drawRoundRect(27, 27, 35, 35, 2, 2, p);
      }
      case "info" -> {
        canvas.drawCircle(24, 24, 11, p); canvas.drawLine(24, 23, 24, 30, p);
        p.setStyle(Paint.Style.FILL); canvas.drawCircle(24, 18, 1.4f, p);
      }
      case "down" -> {
        canvas.drawLine(16, 21, 24, 29, p);
        canvas.drawLine(24, 29, 32, 21, p);
      }
      case "download" -> {
        canvas.drawLine(24, 12, 24, 29, p);
        canvas.drawLine(18, 23, 24, 29, p);
        canvas.drawLine(30, 23, 24, 29, p);
        canvas.drawLine(15, 35, 33, 35, p);
      }
      case "drag" -> {
        canvas.drawLine(17, 20, 31, 20, p);
        canvas.drawLine(17, 27, 31, 27, p);
      }
      case "close" -> {
        canvas.drawLine(17, 17, 31, 31, p);
        canvas.drawLine(31, 17, 17, 31, p);
      }
      case "plus" -> {
        canvas.drawLine(24, 15, 24, 33, p);
        canvas.drawLine(15, 24, 33, 24, p);
      }
      case "check" -> {
        canvas.drawCircle(24, 24, 11, p);
        canvas.drawLine(18, 24, 22, 28, p);
        canvas.drawLine(22, 28, 30, 20, p);
      }
      default -> {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) canvas.drawCircle(17 + i * 7, 24, 1.7f, p);
      }
    }
    canvas.restore();
  }
}
