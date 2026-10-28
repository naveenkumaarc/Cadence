package app.cadence.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Read-only playback progress. The full player owns seeking and spoken time information. */
final class MiniProgress extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private float fraction;
  MiniProgress(Context c) {super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  void position(long position,long duration) {float next=duration>0?(float)Math.max(0,Math.min(position,duration))/duration:0;if(next!=fraction){fraction=next;invalidate();}}
  float fraction() {return fraction;}
  @Override protected void onDraw(Canvas canvas) {super.onDraw(canvas);UiStyle.Palette p=UiStyle.palette(getContext());paint.setColor(p.track());canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setColor(p.accent());canvas.drawRect(0,0,getWidth()*fraction,getHeight(),paint);}
}
