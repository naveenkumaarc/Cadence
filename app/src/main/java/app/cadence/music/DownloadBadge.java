package app.cadence.music;

import android.content.Context;
import android.graphics.*;

final class DownloadBadge extends android.view.View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean complete,active;
  DownloadBadge(Context c) { super(c); setClickable(true); setFocusable(true); }
  void state(boolean complete,boolean active) { this.complete=complete;this.active=active;invalidate(); }
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas); float x=getWidth()/2f,y=getHeight()/2f,s=getResources().getDisplayMetrics().density;
    paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(68,198,132));
    if(complete) {
      canvas.drawCircle(x,y,8*s,paint); paint.setColor(Color.WHITE); paint.setStrokeWidth(1.8f*s);
      paint.setStrokeCap(Paint.Cap.ROUND); canvas.drawLine(x-3.4f*s,y,x-.8f*s,y+2.6f*s,paint); canvas.drawLine(x-.8f*s,y+2.6f*s,x+3.9f*s,y-3*s,paint);
    } else if(active) {
      paint.setStrokeWidth(1.8f*s);paint.setStrokeCap(Paint.Cap.ROUND);
      canvas.drawLine(x,y-5*s,x,y+5*s,paint);canvas.drawLine(x-4*s,y+s,x,y+5*s,paint);canvas.drawLine(x+4*s,y+s,x,y+5*s,paint);
    }
  }
}
