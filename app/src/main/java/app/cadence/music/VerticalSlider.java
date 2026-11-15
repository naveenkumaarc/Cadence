package app.cadence.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Compact accessible vertical slider used by the equalizer sheet. */
final class VerticalSlider extends View {
  interface Listener { void changed(int value, boolean user); }
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private int min,max,value;
  private Listener listener;
  VerticalSlider(Context context,int min,int max,int value){super(context);this.min=min;this.max=Math.max(min+1,max);this.value=Math.max(min,Math.min(max,value));setClickable(true);setFocusable(true);setMinimumWidth(UiStyle.dp(context,56));setMinimumHeight(UiStyle.dp(context,180));}
  void listener(Listener listener){this.listener=listener;}
  int value(){return value;}
  @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension(resolveSize(UiStyle.dp(getContext(),56),widthSpec),resolveSize(UiStyle.dp(getContext(),200),heightSpec));}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);UiStyle.Palette palette=UiStyle.palette(getContext());float center=getWidth()/2f,top=UiStyle.dp(getContext(),16),bottom=getHeight()-UiStyle.dp(getContext(),16);float fraction=(value-min)/(float)(max-min),thumb=bottom-fraction*(bottom-top);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(UiStyle.dp(getContext(),4));paint.setColor(palette.track());canvas.drawLine(center,top,center,bottom,paint);paint.setColor(palette.accent());canvas.drawLine(center,thumb,center,bottom,paint);canvas.drawCircle(center,thumb,UiStyle.dp(getContext(),8),paint);}
  @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){float top=UiStyle.dp(getContext(),16),bottom=getHeight()-UiStyle.dp(getContext(),16);float fraction=1f-Math.max(0,Math.min(1,(event.getY()-top)/Math.max(1,bottom-top)));int next=Math.round(min+fraction*(max-min));if(next!=value){value=next;invalidate();if(listener!=null)listener.changed(value,true);}return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP){performClick();return true;}return super.onTouchEvent(event);}
  @Override public boolean performClick(){super.performClick();UiStyle.feedback(this);return true;}
  @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(android.widget.SeekBar.class.getName());info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,min,max,value));}
}
