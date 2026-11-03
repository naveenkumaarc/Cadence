package app.cadence.music;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import androidx.work.Constraints;
import androidx.work.NetworkType;

/** This device's transfer policy; audio downloads and preferences never sync. */
final class DownloadPolicy {
  static boolean wifiOnly(Context c) {return Preferences.get(c).getBoolean("downloadsWifiOnly",false);}
  static Constraints constraints(Context c) {
    return new Constraints.Builder().setRequiredNetworkType(wifiOnly(c)?NetworkType.UNMETERED:NetworkType.CONNECTED).build();
  }
  static boolean allowed(Context c) {
    ConnectivityManager manager=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkCapabilities network=manager.getNetworkCapabilities(manager.getActiveNetwork());
    if(android.os.Build.VERSION.SDK_INT<28) {
      android.net.NetworkInfo info=manager.getActiveNetworkInfo();
      if(info!=null&&info.isRoaming())return false;
    }
    return network!=null&&network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        &&(android.os.Build.VERSION.SDK_INT<28||network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING))
        &&(!wifiOnly(c)||network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
  }
  private DownloadPolicy() {}
}
