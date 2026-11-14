package app.cadence.music;

import android.content.Context;
import android.graphics.*;

final class DownloadBadge extends android.view.View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean complete,active,failed;
  private int progress=-1;
  DownloadBadge(Context c) { super(c); setClickable(true); setFocusable(true);setBackground(UiStyle.ripple(c,Color.TRANSPARENT,24)); }
  void state(boolean complete,boolean active) { state(complete,active,false,-1); }
  void state(boolean complete,boolean active,boolean failed,int progress) { this.complete=complete;this.active=active;this.failed=failed;this.progress=progress;invalidate(); }
  @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(android.widget.Button.class.getName());}
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas); float x=getWidth()/2f,y=getHeight()/2f,s=getResources().getDisplayMetrics().density;
    int accent=UiStyle.palette(getContext()).accent();
    paint.setStyle(Paint.Style.FILL); paint.setColor(complete?UiStyle.DOWNLOAD_GREEN:accent);
    if(complete) {
      canvas.drawCircle(x,y,8*s,paint); paint.setColor(Color.WHITE); paint.setStrokeWidth(1.8f*s);
      paint.setStrokeCap(Paint.Cap.ROUND); canvas.drawLine(x-3.4f*s,y,x-.8f*s,y+2.6f*s,paint); canvas.drawLine(x-.8f*s,y+2.6f*s,x+3.9f*s,y-3*s,paint);
    } else if(active) {
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2f*s);paint.setStrokeCap(Paint.Cap.ROUND);
      float sweep=progress>=0?Math.max(20,progress*3.6f):250;canvas.drawArc(x-9*s,y-9*s,x+9*s,y+9*s,-90,sweep,false,paint);
      paint.setStrokeWidth(1.5f*s);canvas.drawLine(x-3*s,y-3*s,x+3*s,y+3*s,paint);canvas.drawLine(x+3*s,y-3*s,x-3*s,y+3*s,paint);
    } else if(failed) {
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f*s);canvas.drawArc(x-8*s,y-8*s,x+8*s,y+8*s,-55,285,false,paint);
      Path arrow=new Path();arrow.moveTo(x+5*s,y-9*s);arrow.lineTo(x+10*s,y-8*s);arrow.lineTo(x+8*s,y-3*s);canvas.drawPath(arrow,paint);
    } else {
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f*s);paint.setStrokeCap(Paint.Cap.ROUND);
      canvas.drawLine(x,y-8*s,x,y+4*s,paint);canvas.drawLine(x-4*s,y,x,y+4*s,paint);canvas.drawLine(x+4*s,y,x,y+4*s,paint);canvas.drawLine(x-7*s,y+9*s,x+7*s,y+9*s,paint);
    }
  }
}
