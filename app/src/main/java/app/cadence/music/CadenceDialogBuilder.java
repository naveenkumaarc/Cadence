package app.cadence.music;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Applies the selected palette to native prompts as well as the main screens. */
final class CadenceDialogBuilder extends AlertDialog.Builder {
  CadenceDialogBuilder(Context context){super(context);}
  @Override public AlertDialog create() {
    AlertDialog dialog=super.create();
    View decor=dialog.getWindow().getDecorView();
    decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
      @Override public void onViewAttachedToWindow(View view){view.post(()->{
        UiStyle.Palette palette=UiStyle.palette(getContext());
        dialog.getWindow().setBackgroundDrawable(UiStyle.shape(getContext(),palette.surface(),24));
        tint(view,palette);
      });}
      @Override public void onViewDetachedFromWindow(View view){}
    });return dialog;
  }
  private static void tint(View view,UiStyle.Palette palette) {
    if(view instanceof TextView text&&!"colourPreview".equals(view.getTag()))text.setTextColor(view instanceof Button?palette.accent():palette.ink());
    if(view instanceof EditText edit)edit.setHintTextColor(palette.muted());
    if(view instanceof CheckedTextView checked)checked.setCheckMarkTintList(ColorStateList.valueOf(palette.accent()));
    if(view instanceof CompoundButton button)button.setButtonTintList(ColorStateList.valueOf(palette.accent()));
    if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)tint(group.getChildAt(i),palette);
  }
}
