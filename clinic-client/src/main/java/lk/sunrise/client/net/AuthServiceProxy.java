package lk.sunrise.client.net;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PROXY pattern (structural).
 *
 * Presents login and logout as ordinary local methods. Every call actually
 * crosses HTTP to tier 2, and the screens never know it. Swapping the
 * transport - to RMI, or to an embedded service for a demo - means replacing
 * this class and nothing above it.
 */
public class AuthServiceProxy extends RestClient {

    public void login(String username, String password) {
        JsonNode n = post("/auth/login", Map.of(
                "username", username == null ? "" : username,
                "password", password == null ? "" : password));

        SessionHolder.getInstance().start(
                n.get("token").asText(),
                n.get("fullName").asText(),
                n.get("role").asText(),
                LocalDateTime.parse(n.get("expiresAt").asText()));
    }

    public void logout() {
        try {
            post("/auth/logout", Map.of());
        } catch (ApiException ignored) {
            // Signing out locally must succeed even if the service is down.
        } finally {
            SessionHolder.getInstance().clear();
        }
    }
}
