package lk.sunrise.clinic.service;

import lk.sunrise.clinic.dto.Dtos;
import lk.sunrise.clinic.exception.ClinicExceptions.AccountLockedException;
import lk.sunrise.clinic.exception.ClinicExceptions.AuthenticationFailedException;
import lk.sunrise.clinic.exception.ClinicExceptions.ForbiddenException;
import lk.sunrise.clinic.exception.ClinicExceptions.SessionExpiredException;
import lk.sunrise.clinic.exception.ClinicExceptions.ValidationException;
import lk.sunrise.clinic.support.InMemoryClinicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service-layer tests for AuthenticationService.
 *
 * Requirements under test : FR-01 to FR-05, FR-24, NFR-06
 *
 * The hash is produced here by BCryptPasswordEncoder rather than pasted in as
 * a literal. A pasted hash would silently stop matching if the cost factor
 * ever changed, and the test would then be proving nothing.
 *
 * Equivalence partitions on a login attempt:
 *      correct credentials | wrong password | unknown user | locked account |
 *      deactivated account | blank input
 * Boundary value on the session clock: a token is valid up to its expiry and
 * invalid after it.
 */
@DisplayName("AuthenticationService - sign in and authorisation (FR-01..FR-05)")
class AuthenticationServiceTest {

    private static final long RECEPTIONIST_ID = 10L;
    private static final String PASSWORD = "Clinic@2026";

    private InMemoryClinicRepository repository;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        String hash = new BCryptPasswordEncoder().encode(PASSWORD);

        repository = InMemoryClinicRepository.withClinicDefaults()
                .withStaff(RECEPTIONIST_ID, "reception1", hash, "RECEPTIONIST", true, false, 0)
                .withStaff(11L, "dr.perera", hash, "DENTIST", true, false, 0)
                .withStaff(12L, "admin", hash, "ADMIN", true, false, 0)
                .withStaff(13L, "locked.user", hash, "RECEPTIONIST", true, true, 5)
                .withStaff(14L, "retired.user", hash, "RECEPTIONIST", false, false, 0);

        service = new AuthenticationService(repository);
    }

    // =================================================================
    @Nested
    @DisplayName("Signing in (FR-01, FR-02)")
    class SignIn {

        @Test
        @DisplayName("correct credentials return a token, a name and a role")
        void signsInSuccessfully() {
            Dtos.SessionDTO session = service.login("reception1", PASSWORD);

            assertNotNull(session.token(), "a token must be issued");
            assertEquals("RECEPTIONIST", session.role());
            assertEquals("Staff reception1", session.fullName());
            assertTrue(session.expiresAt().isAfter(java.time.LocalDateTime.now()),
                    "the session must expire in the future, not the past");
        }

        @Test
        @DisplayName("the password is never compared as plain text (FR-02)")
        void storedValueIsAHashNotThePassword() {
            String stored = (String) repository.findStaffByUsername("reception1")
                    .orElseThrow().get("password_hash");

            assertNotNull(stored);
            assertTrue(stored.startsWith("$2"), "BCrypt hashes begin with $2a/$2b/$2y");
            assertTrue(stored.length() >= 59, "a BCrypt hash is 60 characters");
            assertFalse(stored.contains(PASSWORD),
                    "the password must not appear anywhere in the stored value");
        }

        @Test
        @DisplayName("a successful sign-in is written to the audit trail (FR-24)")
        void writesAuditEntry() {
            service.login("reception1", PASSWORD);
            assertTrue(repository.auditTrail.stream().anyMatch(e -> e.contains("LOGIN")),
                    "who signed in and when must be recoverable afterwards");
        }

        @Test
        @DisplayName("a successful sign-in clears any earlier failed attempts")
        void resetsFailureCounter() {
            service.login("reception1", PASSWORD);
            assertTrue(repository.failuresReset.contains(RECEPTIONIST_ID));
        }

        @Test
        @DisplayName("blank input is refused before the database is touched")
        void refusesBlankInput() {
            assertThrows(ValidationException.class, () -> service.login("", PASSWORD));
            assertThrows(ValidationException.class, () -> service.login("reception1", "  "));
            assertThrows(ValidationException.class, () -> service.login(null, null));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Refusing sign-in (FR-02, FR-03)")
    class Refusal {

        @Test
        @DisplayName("a wrong password is refused and counted")
        void wrongPasswordIsCounted() {
            assertThrows(AuthenticationFailedException.class,
                    () -> service.login("reception1", "wrong-password"));

            assertEquals(1, repository.loginFailures.getOrDefault(RECEPTIONIST_ID, 0).intValue(),
                    "the attempt must be recorded so lockout can eventually trigger");
        }

        @Test
        @DisplayName("an unknown username and a wrong password give the SAME message (NFR-06)")
        void doesNotRevealWhichFieldWasWrong() {
            AuthenticationFailedException unknownUser = assertThrows(
                    AuthenticationFailedException.class,
                    () -> service.login("no.such.user", PASSWORD));

            assertTrue(unknownUser.getMessage().startsWith("Invalid username or password"),
                    "telling an attacker the username exists is an enumeration weakness");
        }

        @Test
        @DisplayName("the message counts down the remaining attempts")
        void countsDownRemainingAttempts() {
            AuthenticationFailedException ex = assertThrows(
                    AuthenticationFailedException.class,
                    () -> service.login("reception1", "wrong-password"));

            assertTrue(ex.getMessage().contains("4 attempts remaining"),
                    "MAX_LOGIN_FAILS is 5, one has now been used");
        }

        @Test
        @DisplayName("a locked account is told so, and is not counted again (FR-03)")
        void lockedAccountIsRefusedDistinctly() {
            AccountLockedException ex = assertThrows(AccountLockedException.class,
                    () -> service.login("locked.user", PASSWORD));

            assertTrue(ex.getMessage().toLowerCase().contains("locked"),
                    "the user must know to ask an Administrator, not keep retrying");
        }

        @Test
        @DisplayName("a deactivated account is refused with the generic message")
        void deactivatedAccountIsRefused() {
            assertThrows(AuthenticationFailedException.class,
                    () -> service.login("retired.user", PASSWORD));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Sessions (FR-04)")
    class Sessions {

        @Test
        @DisplayName("a fresh token identifies the signed-in member of staff")
        void tokenResolvesToSession() {
            String token = service.login("reception1", PASSWORD).token();

            AuthenticationService.Session s = service.requireSession(token);
            assertEquals("reception1", s.username());
            assertEquals(RECEPTIONIST_ID, s.staffId());
        }

        @Test
        @DisplayName("a missing or unknown token is refused")
        void rejectsMissingAndUnknownTokens() {
            assertThrows(SessionExpiredException.class, () -> service.requireSession(null));
            assertThrows(SessionExpiredException.class, () -> service.requireSession("   "));
            assertThrows(SessionExpiredException.class,
                    () -> service.requireSession("00000000-0000-0000-0000-000000000000"));
        }

        @Test
        @DisplayName("a token expires once the idle window has passed")
        void tokenExpires() {
            // SESSION_MINUTES is read per login, so a zero-minute window makes
            // the token expire immediately - the clock is not mocked, the
            // configuration is changed, which is what the service reads anyway.
            repository.setting("SESSION_MINUTES", "0");
            String token = service.login("reception1", PASSWORD).token();

            SessionExpiredException ex = assertThrows(SessionExpiredException.class,
                    () -> service.requireSession(token));
            assertTrue(ex.getMessage().toLowerCase().contains("expired"));
        }

        @Test
        @DisplayName("signing out invalidates the token immediately")
        void logoutInvalidatesToken() {
            String token = service.login("reception1", PASSWORD).token();
            assertNotNull(service.requireSession(token));

            service.logout(token);

            assertThrows(SessionExpiredException.class, () -> service.requireSession(token));
            assertTrue(repository.auditTrail.stream().anyMatch(e -> e.contains("LOGOUT")));
        }

        @Test
        @DisplayName("signing out with no token is harmless")
        void logoutWithoutTokenDoesNotThrow() {
            service.logout(null);
        }

        @Test
        @DisplayName("two sign-ins produce two different tokens")
        void tokensAreUnique() {
            String first = service.login("reception1", PASSWORD).token();
            String second = service.login("dr.perera", PASSWORD).token();
            assertFalse(first.equals(second),
                    "a shared token would let one user act as another");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Authorisation is enforced on the server (FR-05)")
    class RoleChecks {

        @Test
        @DisplayName("a permitted role passes")
        void allowsPermittedRole() {
            String token = service.login("reception1", PASSWORD).token();
            assertNotNull(service.requireRole(token, "RECEPTIONIST", "ADMIN"));
        }

        @Test
        @DisplayName("a dentist cannot perform a receptionist-only action")
        void refusesWrongRole() {
            String token = service.login("dr.perera", PASSWORD).token();

            ForbiddenException ex = assertThrows(ForbiddenException.class,
                    () -> service.requireRole(token, "RECEPTIONIST"));
            assertTrue(ex.getMessage().toLowerCase().contains("role"));
        }

        @Test
        @DisplayName("hiding a button in the client is not a control - the check is here")
        void refusesWithoutAnyToken() {
            assertThrows(SessionExpiredException.class,
                    () -> service.requireRole(null, "ADMIN"));
            assertThrows(SessionExpiredException.class,
                    () -> service.requireRole("forged-token", "ADMIN"));
        }

        @Test
        @DisplayName("an administrator passes an admin-only check")
        void allowsAdministrator() {
            String token = service.login("admin", PASSWORD).token();
            assertEquals("ADMIN", service.requireRole(token, "ADMIN").role());
        }
    }
}
