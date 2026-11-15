package app.cadence.music;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Four-item detail action layout: two flexible text actions followed by two square actions.
 * Text actions stay together when they fit and move onto their own rows before they can collide.
 */
final class DetailActionsLayout extends ViewGroup {
  private final int gap;

  DetailActionsLayout(Context context) {
    super(context);
    gap = UiStyle.dp(context, UiStyle.SPACE_3);
    setClipChildren(false);
  }

  @Override
  protected LayoutParams generateDefaultLayoutParams() {
    return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
    if (getChildCount() != 4 || available == 0) {
      measureAsFlow(available, widthMeasureSpec, heightMeasureSpec);
      return;
    }

    View primary = getChildAt(0), secondary = getChildAt(1);
    View firstIcon = getChildAt(2), secondIcon = getChildAt(3);
    int iconSize = UiStyle.dp(getContext(), UiStyle.TOUCH);
    firstIcon.measure(MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY));
    secondIcon.measure(MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY));

    primary.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    secondary.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    int desiredPrimary = primary.getMeasuredWidth();
    int desiredSecondary = secondary.getMeasuredWidth();
    int allTextWidth = available - iconSize * 2 - gap * 3;

    if (allTextWidth >= desiredPrimary + desiredSecondary) {
      int primaryWidth = allTextWidth / 2;
      measureAction(primary, primaryWidth);
      measureAction(secondary, allTextWidth - primaryWidth);
      int rowHeight = maxHeight(primary, secondary, firstIcon, secondIcon);
      setMeasuredDimension(resolveSize(width, widthMeasureSpec),
          resolveSize(getPaddingTop() + rowHeight + getPaddingBottom(), heightMeasureSpec));
      return;
    }

    if (available >= UiStyle.dp(getContext(), 196)) {
      int actionWidth = (available - gap) / 2;
      measureAction(primary, actionWidth);
      measureAction(secondary, available - gap - actionWidth);
      int firstRow = Math.max(primary.getMeasuredHeight(), secondary.getMeasuredHeight());
      int secondRow = Math.max(firstIcon.getMeasuredHeight(), secondIcon.getMeasuredHeight());
      setMeasuredDimension(resolveSize(width, widthMeasureSpec),
          resolveSize(getPaddingTop() + firstRow + gap + secondRow + getPaddingBottom(),
              heightMeasureSpec));
      return;
    }

    measureAction(primary, available);
    measureAction(secondary, available);
    int height = primary.getMeasuredHeight() + gap + secondary.getMeasuredHeight() + gap
        + Math.max(firstIcon.getMeasuredHeight(), secondIcon.getMeasuredHeight());
    setMeasuredDimension(resolveSize(width, widthMeasureSpec),
        resolveSize(getPaddingTop() + height + getPaddingBottom(), heightMeasureSpec));
  }

  private void measureAction(View child, int width) {
    child.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    if (child.getMeasuredHeight() < UiStyle.dp(getContext(), UiStyle.TOUCH))
      child.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(UiStyle.dp(getContext(), UiStyle.TOUCH), MeasureSpec.EXACTLY));
  }

  private int maxHeight(View... children) {
    int height = 0;
    for (View child : children) height = Math.max(height, child.getMeasuredHeight());
    return height;
  }

  private void measureAsFlow(int available, int widthMeasureSpec, int heightMeasureSpec) {
    int lineWidth = 0, lineHeight = 0, totalHeight = getPaddingTop() + getPaddingBottom();
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      measureChild(child, widthMeasureSpec, heightMeasureSpec);
      int next = lineWidth == 0 ? child.getMeasuredWidth() : lineWidth + gap + child.getMeasuredWidth();
      if (lineWidth > 0 && next > available) {
        totalHeight += lineHeight + gap;
        lineWidth = child.getMeasuredWidth();
        lineHeight = child.getMeasuredHeight();
      } else {
        lineWidth = next;
        lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
      }
    }
    totalHeight += lineHeight;
    setMeasuredDimension(resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
        resolveSize(totalHeight, heightMeasureSpec));
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int available = right - left - getPaddingLeft() - getPaddingRight();
    int x = getPaddingLeft(), y = getPaddingTop();
    if (getChildCount() != 4) {
      int lineHeight = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (x > getPaddingLeft() && x + child.getMeasuredWidth() > getPaddingLeft() + available) {
          x = getPaddingLeft();
          y += lineHeight + gap;
          lineHeight = 0;
        }
        child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
        x += child.getMeasuredWidth() + gap;
        lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
      }
      return;
    }

    View primary = getChildAt(0), secondary = getChildAt(1);
    View firstIcon = getChildAt(2), secondIcon = getChildAt(3);
    int iconSize = UiStyle.dp(getContext(), UiStyle.TOUCH);
    boolean allOneRow = primary.getMeasuredWidth() + secondary.getMeasuredWidth()
        + iconSize * 2 + gap * 3 <= available;
    if (allOneRow) {
      int rowHeight = maxHeight(primary, secondary, firstIcon, secondIcon);
      layoutCentered(primary, x, y, rowHeight); x += primary.getMeasuredWidth() + gap;
      layoutCentered(secondary, x, y, rowHeight); x += secondary.getMeasuredWidth() + gap;
      layoutCentered(firstIcon, x, y, rowHeight); x += firstIcon.getMeasuredWidth() + gap;
      layoutCentered(secondIcon, x, y, rowHeight);
      return;
    }

    if (primary.getMeasuredWidth() < available) {
      int firstRow = Math.max(primary.getMeasuredHeight(), secondary.getMeasuredHeight());
      layoutCentered(primary, x, y, firstRow);
      layoutCentered(secondary, x + primary.getMeasuredWidth() + gap, y, firstRow);
      y += firstRow + gap;
      layoutCentered(firstIcon, x, y, iconSize);
      layoutCentered(secondIcon, x + firstIcon.getMeasuredWidth() + gap, y, iconSize);
      return;
    }

    primary.layout(x, y, x + primary.getMeasuredWidth(), y + primary.getMeasuredHeight());
    y += primary.getMeasuredHeight() + gap;
    secondary.layout(x, y, x + secondary.getMeasuredWidth(), y + secondary.getMeasuredHeight());
    y += secondary.getMeasuredHeight() + gap;
    layoutCentered(firstIcon, x, y, iconSize);
    layoutCentered(secondIcon, x + firstIcon.getMeasuredWidth() + gap, y, iconSize);
  }

  private static void layoutCentered(View child, int x, int y, int rowHeight) {
    int childTop = y + (rowHeight - child.getMeasuredHeight()) / 2;
    child.layout(x, childTop, x + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
  }
}
