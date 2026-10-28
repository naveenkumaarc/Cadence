package app.cadence.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/** Top-edge vertical refresh; horizontal shelves and ordinary scrolling retain their gestures. */
final class RefreshScrollView extends ScrollView {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private float startX,startY,pull;
  private boolean pulling,eligible;
  private java.util.function.BooleanSupplier enabled=()->false;
  private Runnable refresh=()->{};
  RefreshScrollView(Context c){super(c);setWillNotDraw(false);}
  void refreshAction(java.util.function.BooleanSupplier enabled,Runnable action){this.enabled=enabled;refresh=action;}
  @Override public boolean onInterceptTouchEvent(MotionEvent e) {
    if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();eligible=getScrollY()==0&&enabled.getAsBoolean();pulling=false;pull=0;}
    if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&eligible&&e.getPointerCount()==1){float dy=e.getY()-startY,dx=Math.abs(e.getX()-startX);int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();if(dx>slop&&dx>dy){eligible=false;return false;}else if(dy>slop*2&&dy>dx*1.5f){pulling=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}}
    return super.onInterceptTouchEvent(e);
  }
  @Override public boolean onTouchEvent(MotionEvent e) {
    if(!pulling)return super.onTouchEvent(e);
    if(e.getActionMasked()==MotionEvent.ACTION_MOVE){pull=Math.max(0,e.getY()-startY);invalidate();return true;}
    if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){boolean run=e.getActionMasked()==MotionEvent.ACTION_UP&&pull>=UiStyle.dp(getContext(),80);pulling=false;pull=0;getParent().requestDisallowInterceptTouchEvent(false);invalidate();if(run){UiStyle.feedback(this);announceForAccessibility("Refreshing");refresh.run();}return true;}
    return true;
  }
  @Override protected void dispatchDraw(Canvas c){super.dispatchDraw(c);if(!pulling)return;float s=getResources().getDisplayMetrics().density,x=getWidth()/2f,y=getScrollY()+28*s;UiStyle.Palette p=UiStyle.palette(getContext());paint.setStyle(Paint.Style.FILL);paint.setColor(p.surface());c.drawCircle(x,y,20*s,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3*s);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(p.accent());float f=Math.min(1,pull/(80*s));c.drawArc(x-10*s,y-10*s,x+10*s,y+10*s,-90,f*300,false,paint);}
}
