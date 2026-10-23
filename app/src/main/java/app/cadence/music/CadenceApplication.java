package app.cadence.music;

public final class CadenceApplication extends android.app.Application {
  @Override public void onCreate() {
    super.onCreate();
    try { CloudData.recoverPending(this); }
    catch(Exception ignored) { getSharedPreferences("cloud-account",0).edit().putBoolean("enabled",false).putString("status","Local recovery needs attention").commit(); }
    CloudSync.init(this);
  }
}
