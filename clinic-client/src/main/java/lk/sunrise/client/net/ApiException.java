package lk.sunrise.client.net;

import java.time.LocalTime;
import java.util.List;

/**
 * A failure reported by tier 2, carrying the message the service produced.
 *
 * The client shows that message verbatim rather than inventing its own, so
 * the wording a receptionist sees is written once, on the server, next to the
 * rule that produced it.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final List<LocalTime> suggestedSlots;

    public ApiException(int status, String message, List<LocalTime> suggestedSlots) {
        super(message);
        this.status = status;
        this.suggestedSlots = suggestedSlots == null ? List.of() : suggestedSlots;
    }

    public int getStatus() { return status; }

    /** Populated on HTTP 409 - alternative times the dentist is free (FR-11). */
    public List<LocalTime> getSuggestedSlots() { return suggestedSlots; }

    public boolean isSessionProblem() { return status == 401; }
}
