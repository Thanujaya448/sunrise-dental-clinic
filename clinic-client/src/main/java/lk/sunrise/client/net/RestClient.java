package lk.sunrise.client.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The only class in TIER 1 that knows HTTP exists.
 *
 * Everything above it works in terms of methods and objects; this is where a
 * call crosses the process boundary to tier 2. Isolating it here is what
 * makes the tier separation real rather than nominal - no screen imports
 * java.net.http.
 */
public class RestClient {

    private static final String BASE_URL = "http://localhost:8080/api";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    protected final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // -----------------------------------------------------------------
    //  verbs
    // -----------------------------------------------------------------

    protected JsonNode get(String path) {
        return send(builder(path).GET());
    }

    protected JsonNode post(String path, Object body) {
        String json = writeJson(body);
        return send(builder(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    // -----------------------------------------------------------------
    //  plumbing
    // -----------------------------------------------------------------

    private HttpRequest.Builder builder(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");

        String token = SessionHolder.getInstance().getToken();
        if (token != null) {
            b.header("Authorization", token);
        }
        return b;
    }

    private JsonNode send(HttpRequest.Builder builder) {
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new ApiException(0,
                    "Cannot reach the clinic service. Check that it is running on port 8080.", null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "The request was interrupted.", null);
        }

        int status = response.statusCode();
        JsonNode body = parse(response.body());

        if (status >= 200 && status < 300) {
            return body;
        }
        throw toException(status, body);
    }

    private ApiException toException(int status, JsonNode body) {
        String message = body != null && body.hasNonNull("message")
                ? body.get("message").asText()
                : "The service returned HTTP " + status + ".";

        List<LocalTime> slots = new ArrayList<>();
        if (body != null && body.has("suggestedSlots") && body.get("suggestedSlots").isArray()) {
            body.get("suggestedSlots").forEach(n -> slots.add(LocalTime.parse(n.asText())));
        }
        return new ApiException(status, message, slots);
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private String writeJson(Object body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new ApiException(0, "Could not prepare the request.", null);
        }
    }
}
