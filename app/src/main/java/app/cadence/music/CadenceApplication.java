package app.cadence.music;

public final class CadenceApplication extends android.app.Application {
  @Override public void onCreate() { super.onCreate(); CloudSync.init(this); }
}
