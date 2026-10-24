package app.cadence.music;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Share useful error codes without logging tokens, server messages or library content. */
final class SyncErrors {
  record Failure(boolean consent,boolean retry,String description) {}
  static Failure describe(Throwable error) {
    Throwable cause=error;
    for(int i=0;i<10 && cause!=null;i++,cause=cause.getCause()) {
      if(cause instanceof DriveStore.ConsentException) return new Failure(true,false,"Google Drive permission needs reconnecting.");
      if(cause instanceof DriveStore.ApiFailure api) {
        String code="Drive "+api.http+" / "+api.reason;
        if(api.reason.equals("storageQuotaExceeded")) return new Failure(false,false,"Your Google storage is full ("+code+"). Free some space, then try again.");
        if(api.reason.equals("accessNotConfigured") || api.reason.equals("serviceDisabled")) return new Failure(false,false,"Google Drive API is unavailable for this app ("+code+").");
        if(api.reason.equals("domainPolicy")) return new Failure(false,false,"Your Google account administrator blocks Drive apps ("+code+").");
        return new Failure(false,api.retryable(),"Google Drive could not complete the request ("+code+").");
      }
      if(cause instanceof ResolvableApiException) return new Failure(true,false,"Google account authorization needs reconnecting.");
      if(cause instanceof ApiException api) {
        int status=api.getStatusCode();
        if(status==4 || status==5 || status==6) return new Failure(true,false,"Reconnect your Google account (Google status "+status+").");
        return new Failure(false,status!=10,"Google authorization failed (Google status "+status+").");
      }
      if(cause instanceof UnknownHostException) return new Failure(false,true,"Could not reach Google. Check your internet connection.");
      if(cause instanceof SocketTimeoutException || cause instanceof TimeoutException) return new Failure(false,true,"Google did not respond in time. Please try again.");
      if(cause instanceof javax.net.ssl.SSLException) return new Failure(false,true,"A secure connection to Google could not be established. Check your connection and phone date/time.");
    }
    String kind=error.getClass().getSimpleName().replaceAll("[^A-Za-z0-9]","");
    if(error instanceof org.json.JSONException || error instanceof com.google.gson.JsonParseException || error instanceof IllegalArgumentException)
      return new Failure(false,false,"Saved sync data could not be read ("+kind+"). Your phone library is kept.");
    if(error instanceof IOException) return new Failure(false,true,"Sync could not read or save data ("+kind+"). Please try again.");
    return new Failure(false,false,"Cadence could not complete sync ("+kind+"). Please report this message.");
  }
}
