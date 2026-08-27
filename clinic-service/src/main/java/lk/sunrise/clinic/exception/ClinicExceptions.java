package lk.sunrise.clinic.exception;

import java.time.LocalTime;
import java.util.List;

/**
 * Domain exceptions. Each maps to one HTTP status in GlobalExceptionHandler,
 * so the service layer throws meaning rather than status codes.
 */
public final class ClinicExceptions {

    private ClinicExceptions() { }

    /** 401 - bad credentials. Deliberately does not say which field was wrong. */
    public static class AuthenticationFailedException extends RuntimeException {
        public AuthenticationFailedException(String m) { super(m); }
    }

    /** 423 - too many failed attempts (FR-03). */
    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String m) { super(m); }
    }

    /** 401 - no token, or an expired one (FR-04). */
    public static class SessionExpiredException extends RuntimeException {
        public SessionExpiredException(String m) { super(m); }
    }

    /** 403 - authenticated, but the role does not permit this (FR-05). */
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String m) { super(m); }
    }

    /** 404 - no such patient, appointment or bill. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    /** 400 - the request broke a validation rule (NFR-04). */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String m) { super(m); }
    }

    /**
     * 409 - the dentist is already booked (FR-10). Carries alternative slots
     * so the client can offer them rather than just refusing (FR-11).
     */
    public static class SlotUnavailableException extends RuntimeException {
        private final List<LocalTime> suggestions;
        public SlotUnavailableException(String m, List<LocalTime> suggestions) {
            super(m);
            this.suggestions = suggestions;
        }
        public List<LocalTime> getSuggestions() { return suggestions; }
    }

    /** 422 - the request is well-formed but the bill cannot be produced. */
    public static class BillingException extends RuntimeException {
        public BillingException(String m) { super(m); }
    }
}
