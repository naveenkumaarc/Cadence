package app.cadence.core;

import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;

/** Short-lived PKCE session. No client secret, password, or token is written to disk. */
public final class SpotifyAuth implements Closeable {
  public static final String REDIRECT = "http://127.0.0.1:43821/callback";
  private final String clientId, verifier = random(), state = random();
  private final ServerSocket socket;

  public SpotifyAuth(String clientId) throws IOException {
    if (!clientId.matches("[A-Za-z0-9]{32}"))
      throw new IOException("Enter the 32-character Spotify Client ID from your developer app.");
    this.clientId = clientId;
    socket = new ServerSocket(43821, 8, InetAddress.getByName("127.0.0.1"));
    socket.setSoTimeout(180_000);
  }

  public String authorizationUrl() throws Exception {
    String challenge =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    return HttpUrl.get("https://accounts.spotify.com/authorize")
        .newBuilder()
        .addQueryParameter("client_id", clientId)
        .addQueryParameter("response_type", "code")
        .addQueryParameter("redirect_uri", REDIRECT)
        .addQueryParameter("state", state)
        .addQueryParameter("scope", "playlist-read-private playlist-read-collaborative")
        .addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("code_challenge", challenge)
        .build()
        .toString();
  }

  public String awaitToken() throws IOException {
    long deadline = System.currentTimeMillis() + 180_000;
    while (System.currentTimeMillis() < deadline) {
      socket.setSoTimeout((int) Math.max(1, deadline - System.currentTimeMillis()));
      try (Socket incoming = socket.accept()) {
        incoming.setSoTimeout(5000);
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(incoming.getInputStream(), StandardCharsets.US_ASCII));
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1 && ch != '\n' && line.length() < 4096)
          if (ch != '\r') line.append((char) ch);
        String[] first = line.toString().split(" ");
        if (first.length != 3 || !first[0].equals("GET")) {
          reply(incoming, false);
          continue;
        }
        URI callback;
        try {
          callback = URI.create(first[1]);
        } catch (IllegalArgumentException e) {
          reply(incoming, false);
          continue;
        }
        String receivedState = Links.parameter(callback.getRawQuery(), "state");
        if (!"/callback".equals(callback.getPath())
            || receivedState == null
            || !MessageDigest.isEqual(
                state.getBytes(StandardCharsets.US_ASCII),
                receivedState.getBytes(StandardCharsets.US_ASCII))) {
          reply(incoming, false);
          continue;
        }
        String error = Links.parameter(callback.getRawQuery(), "error");
        if (error != null) {
          reply(incoming, false);
          throw new IOException("Spotify sign-in was declined. You can retry or import CSV.");
        }
        String code = Links.parameter(callback.getRawQuery(), "code");
        if (code == null || code.isEmpty()) {
          reply(incoming, false);
          continue;
        }
        reply(incoming, true);
        FormBody body =
            new FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT)
                .add("code_verifier", verifier)
                .build();
        try (var response =
            HttpDownloader.CLIENT
                .newCall(
                    new Request.Builder()
                        .url("https://accounts.spotify.com/api/token")
                        .post(body)
                        .build())
                .execute()) {
          if (!response.isSuccessful() || response.body() == null)
            throw new IOException("Spotify sign-in exchange failed: HTTP " + response.code());
          try {
            return JsonParser.parseString(response.body().string())
                .getAsJsonObject()
                .get("access_token")
                .getAsString();
          } catch (RuntimeException e) {
            throw new IOException("Spotify did not return an access token.");
          }
        }
      }
    }
    throw new IOException("Spotify sign-in timed out. Try again.");
  }

  private static void reply(Socket socket, boolean ok) throws IOException {
    byte[] body =
        (ok
                ? "Connected. Return to Cadence to import your playlist."
                : "Invalid or cancelled sign-in. Return to Cadence.")
            .getBytes(StandardCharsets.UTF_8);
    String headers =
        "HTTP/1.1 "
            + (ok ? "200 OK" : "400 Bad Request")
            + "\r\n"
            + "Content-Type: text/plain; charset=utf-8\r\n"
            + "Cache-Control: no-store\r\n"
            + "Connection: close\r\n"
            + "Content-Length: "
            + body.length
            + "\r\n\r\n";
    socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
    socket.getOutputStream().write(body);
    socket.getOutputStream().flush();
  }

  private static String random() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
