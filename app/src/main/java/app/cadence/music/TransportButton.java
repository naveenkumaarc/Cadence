package app.cadence.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class TransportButton extends View {
  enum Icon {
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
    BACK,
    FORWARD
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path triangle = new Path();
  private Icon icon;
  private int seconds = 15;
  void seconds(int value) { seconds = value; invalidate(); }
  private final int foreground, background;

  public TransportButton(Context context) {
    this(
        context,
        Icon.PLAY,
        android.graphics.Color.WHITE,
        android.graphics.Color.TRANSPARENT,
        "Play");
  }

  TransportButton(Context context, Icon icon, int foreground, int background, String description) {
    super(context);
    this.icon = icon;
    this.foreground = foreground;
    this.background = background;
    setClickable(true);
    setFocusable(true);
    setContentDescription(description);
  }

  void icon(Icon value) {
    icon = value;
    setContentDescription(
        value == Icon.PAUSE
            ? "Pause"
            : value == Icon.PLAY ? "Play" : value == Icon.NEXT ? "Next track" : "Previous track");
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float w = getWidth(), h = getHeight(), unit = Math.min(w, h);
    paint.setColor(background);
    canvas.drawCircle(w / 2, h / 2, unit / 2 - 1, paint);
    paint.setColor(foreground);
    float x = w / 2, y = h / 2, s = unit * 0.19f;
    if (icon == Icon.BACK || icon == Icon.FORWARD) {
      boolean forward = icon == Icon.FORWARD;
      float radius = unit * .23f;
      paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(unit * .035f);
      paint.setStrokeCap(Paint.Cap.ROUND);
      canvas.drawArc(x-radius, y-radius, x+radius, y+radius, forward ? -140 : -40, forward ? 290 : -290, false, paint);
      float tipX = x + (forward ? -1 : 1) * radius * .77f;
      float tipY = y - radius * .64f;
      canvas.drawLine(tipX,tipY,tipX+(forward ? -.02f : .02f)*unit,tipY-unit*.12f,paint);
      canvas.drawLine(tipX,tipY,tipX+(forward ? .12f : -.12f)*unit,tipY-unit*.01f,paint);
      paint.setStyle(Paint.Style.FILL); paint.setTextSize(unit * .22f); paint.setTextAlign(Paint.Align.CENTER);
      paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
      canvas.drawText(Integer.toString(seconds), x, y - (paint.ascent()+paint.descent())/2, paint);
    } else if (icon == Icon.PAUSE) {
      canvas.drawRoundRect(x - s, y - s, x - s * 0.3f, y + s, s / 6, s / 6, paint);
      canvas.drawRoundRect(x + s * 0.3f, y - s, x + s, y + s, s / 6, s / 6, paint);
    } else {
      boolean previous = icon == Icon.PREVIOUS;
      triangle.reset();
      triangle.moveTo(x + (previous ? s : -s * 0.7f), y - s);
      triangle.lineTo(x + (previous ? -s : s), y);
      triangle.lineTo(x + (previous ? s : -s * 0.7f), y + s);
      triangle.close();
      canvas.drawPath(triangle, paint);
      if (icon == Icon.NEXT) canvas.drawRect(x + s, y - s, x + s * 1.35f, y + s, paint);
      if (previous) canvas.drawRect(x - s * 1.35f, y - s, x - s, y + s, paint);
    }
  }
}
