package app.cadence.music;

import android.app.Activity;
import android.content.Intent;
import android.os.CancellationSignal;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.libraries.identity.googleid.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import org.json.JSONObject;

final class GoogleLogin {
  record Profile(String subject,String email,String name,String photo) {}
  private final Activity activity;
  private final CredentialManager manager;
  private CancellationSignal cancellation;
  private final Consumer<String> error;
  private final Runnable connected;
  GoogleLogin(Activity activity,Consumer<String> error,Runnable connected) {
    this.activity=activity; this.error=error; this.connected=connected; manager=CredentialManager.create(activity);
  }
  void signIn(Consumer<Profile> result) {
    if(!BuildConfig.GOOGLE_WEB_CLIENT_ID.matches("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com")) {
      error.accept("Google sign-in is not available in this build yet. Your local library is available as usual."); return;
    }
    String nonce=UUID.randomUUID().toString(); cancellation=new CancellationSignal();
    var option=new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(nonce).build();
    var request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
    manager.getCredentialAsync(activity,request,cancellation,activity.getMainExecutor(),new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>() {
      @Override public void onResult(GetCredentialResponse response) {
        try {
          GoogleIdTokenCredential credential=GoogleIdTokenCredential.createFrom(response.getCredential().getData());
          String[] pieces=credential.getIdToken().split("\\."); if(pieces.length!=3) throw new IllegalArgumentException();
          JSONObject claims=new JSONObject(new String(android.util.Base64.decode(pieces[1],android.util.Base64.URL_SAFE),StandardCharsets.UTF_8));
          if(!claims.optString("aud").equals(BuildConfig.GOOGLE_WEB_CLIENT_ID) || !nonce.equals(claims.optString("nonce"))
              || claims.optLong("exp")<System.currentTimeMillis()/1000 || claims.optString("sub").isEmpty()) throw new IllegalArgumentException();
          // The credential is returned by Google's provider; Drive access separately checks the account's subject with Google.
          result.accept(new Profile(claims.getString("sub"),credential.getId(),
              credential.getDisplayName()==null?"":credential.getDisplayName(),credential.getProfilePictureUri()==null?"":credential.getProfilePictureUri().toString()));
        } catch(Exception e) { error.accept("Google sign-in could not be completed. Please try again."); }
      }
      @Override public void onError(GetCredentialException e) {
        if(!(e instanceof GetCredentialCancellationException)) error.accept("Google sign-in could not be completed. Check your connection and try again.");
      }
    });
  }
  void connectDrive() {
    if(CloudSync.subject(activity).isEmpty()) return;
    Identity.getAuthorizationClient(activity).authorize(CloudSync.authorization(activity)).addOnSuccessListener(activity,auth-> {
      try {
        if(auth.hasResolution()) activity.startIntentSenderForResult(auth.getPendingIntent().getIntentSender(),60,null,0,0,0);
        else { CloudSync.connected(activity); connected.run(); }
      } catch(Exception e) { error.accept("Could not open Google Drive permission. Please try again."); }
    }).addOnFailureListener(activity,e->error.accept("Google Drive could not be connected. Your changes remain on this phone."));
  }
  void authorizationResult(int result,Intent data) {
    if(result!=Activity.RESULT_OK || data==null) return;
    try {
      var auth=Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data);
      if(auth.getAccessToken()!=null && auth.getGrantedScopes().contains(CloudSync.DRIVE)) { CloudSync.connected(activity); connected.run(); }
      else error.accept("Allow Cadence app storage to enable Drive sync.");
    } catch(Exception e) { error.accept("Drive permission was not completed. You can reconnect from your profile."); }
  }
  void clear() {
    manager.clearCredentialStateAsync(new ClearCredentialStateRequest(),null,activity.getMainExecutor(),new CredentialManagerCallback<Void,ClearCredentialException>() {
      @Override public void onResult(Void ignored) {}
      @Override public void onError(ClearCredentialException ignored) {}
    });
  }
  void close() { if(cancellation!=null) cancellation.cancel(); }
}
