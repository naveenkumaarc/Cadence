package app.cadence.core;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.Base64;
import org.junit.Test;

public class SpotifyAuthTest {
  @Test
  public void pkceUsesFreshStrongChallengeAndNoSecret() throws Exception {
    String id = "0123456789abcdef0123456789abcdef";
    String state;
    try (SpotifyAuth auth = new SpotifyAuth(id)) {
      URI url = URI.create(auth.authorizationUrl());
      assertEquals("accounts.spotify.com", url.getHost());
      assertEquals("S256", Links.parameter(url.getRawQuery(), "code_challenge_method"));
      assertEquals(
          32,
          Base64.getUrlDecoder()
              .decode(Links.parameter(url.getRawQuery(), "code_challenge"))
              .length);
      assertEquals(SpotifyAuth.REDIRECT, Links.parameter(url.getRawQuery(), "redirect_uri"));
      state = Links.parameter(url.getRawQuery(), "state");
      assertTrue(state.length() >= 43);
      assertFalse(url.toString().contains("client_secret"));
      assertFalse(url.toString().contains("code_verifier"));
    }
    try (SpotifyAuth auth = new SpotifyAuth(id)) {
      assertNotEquals(
          state, Links.parameter(URI.create(auth.authorizationUrl()).getRawQuery(), "state"));
    }
  }
}
