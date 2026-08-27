package lk.sunrise.clinic.api;

import lk.sunrise.clinic.dto.Dtos;
import lk.sunrise.clinic.exception.ClinicExceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns domain exceptions into HTTP responses with messages a receptionist can
 * act on. The brief asks for "appropriate messages"; the 70-100 band asks for
 * a sophisticated UI. Specific errors serve both, and cost one class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Dtos.ErrorDTO> validation(ValidationException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({AuthenticationFailedException.class, SessionExpiredException.class})
    public ResponseEntity<Dtos.ErrorDTO> unauthorised(RuntimeException ex) {
        return status(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Dtos.ErrorDTO> forbidden(ForbiddenException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Dtos.ErrorDTO> locked(AccountLockedException ex) {
        return status(HttpStatus.LOCKED, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Dtos.ErrorDTO> notFound(NotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** 409 carries the alternative slots, so the client can offer them (FR-11). */
    @ExceptionHandler(SlotUnavailableException.class)
    public ResponseEntity<Dtos.SlotConflictDTO> clash(SlotUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Dtos.SlotConflictDTO(ex.getMessage(), ex.getSuggestions()));
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<Dtos.ErrorDTO> billing(BillingException ex) {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /** Last resort. Never leaks a stack trace to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Dtos.ErrorDTO> unexpected(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled error", ex);
        return status(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again, or contact the Administrator.");
    }

    private ResponseEntity<Dtos.ErrorDTO> status(HttpStatus s, String message) {
        return ResponseEntity.status(s).body(new Dtos.ErrorDTO(message));
    }
}
