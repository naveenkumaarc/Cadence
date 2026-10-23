package app.cadence.core;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

public final class HttpDownloader extends Downloader {
  public static final String USER_AGENT =
      "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0"
          + " Mobile Safari/537.36";
  public static final OkHttpClient CLIENT =
      new OkHttpClient.Builder()
          .connectTimeout(15, TimeUnit.SECONDS)
          .readTimeout(25, TimeUnit.SECONDS)
          .callTimeout(45, TimeUnit.SECONDS)
          .build();
  // Playback may keep one response open for the whole song; API call deadlines must not stop it.
  public static final OkHttpClient MEDIA_CLIENT =
      CLIENT.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build();

  @Override
  public Response execute(Request request) throws IOException, ReCaptchaException {
    okhttp3.Request.Builder builder =
        new okhttp3.Request.Builder().url(request.url()).header("User-Agent", USER_AGENT);
    request
        .headers()
        .forEach(
            (key, values) -> {
              builder.removeHeader(key);
              for (String value : values) builder.addHeader(key, value);
            });
    byte[] body = request.dataToSend();
    RequestBody outgoing = body == null ? null : RequestBody.create(body, null);
    if (outgoing == null
        && (request.httpMethod().equals("POST") || request.httpMethod().equals("PUT")))
      outgoing = RequestBody.create(new byte[0], null);
    builder.method(request.httpMethod(), outgoing);
    try (okhttp3.Response response = CLIENT.newCall(builder.build()).execute()) {
      if (response.code() == 429)
        throw new ReCaptchaException(
            "YouTube requested verification. Try again later on your normal network.",
            request.url());
      if (!response.isSuccessful()) throw new IOException("YouTube HTTP " + response.code());
      return new Response(
          response.code(),
          response.message(),
          response.headers().toMultimap(),
          response.body() == null ? "" : response.body().string(),
          response.request().url().toString());
    }
  }
}
