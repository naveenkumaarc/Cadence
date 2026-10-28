package app.cadence.music;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Lightweight placeholders: no image requests or hidden interactive children. */
final class SkeletonView extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final boolean shelves;
  private ValueAnimator pulse;
  private float alpha=.7f;
  SkeletonView(Context c,boolean shelves) {super(c);this.shelves=shelves;setContentDescription(shelves?"Loading recommendations":"Loading results");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
  @Override protected void onMeasure(int w,int h) {setMeasuredDimension(MeasureSpec.getSize(w),UiStyle.dp(getContext(),shelves?240:240));}
  private void animateIfVisible() {
    if(pulse!=null){pulse.cancel();pulse=null;}
    if(isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&getVisibility()==VISIBLE&&UiStyle.motion(getContext())) {
      pulse=ValueAnimator.ofFloat(.5f,.9f);pulse.setDuration(1000);pulse.setRepeatMode(ValueAnimator.REVERSE);pulse.setRepeatCount(ValueAnimator.INFINITE);pulse.addUpdateListener(a->{alpha=(float)a.getAnimatedValue();invalidate();});pulse.start();
    }
  }
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();animateIfVisible();}
  @Override protected void onWindowVisibilityChanged(int v){super.onWindowVisibilityChanged(v);animateIfVisible();}
  @Override protected void onDetachedFromWindow(){if(pulse!=null){pulse.cancel();pulse=null;}super.onDetachedFromWindow();}
  private void block(Canvas c,float l,float t,float r,float b){c.drawRoundRect(l,t,r,b,UiStyle.dp(getContext(),8),UiStyle.dp(getContext(),8),paint);}
  @Override protected void onDraw(Canvas c) {
    super.onDraw(c);float s=getResources().getDisplayMetrics().density,w=getWidth()/s;c.save();c.scale(s,s);paint.setColor(UiStyle.palette(getContext()).outline());paint.setAlpha(Math.round(alpha*255));
    if(shelves){block(c,0,0,Math.min(w*.55f,160),20);for(int i=0;i<4;i++){float x=i*156;block(c,x,40,x+140,180);block(c,x,192,x+120,204);block(c,x,216,x+84,226);}}
    else for(int i=0;i<3;i++){float y=i*80;block(c,0,y+8,56,y+64);block(c,72,y+16,Math.max(73,w-56),y+30);block(c,72,y+40,Math.max(73,w-100),y+50);}
    c.restore();
  }
}
