package lk.sunrise.clinic.service;

import lk.sunrise.clinic.dto.Dtos;
import lk.sunrise.clinic.exception.ClinicExceptions.*;
import lk.sunrise.clinic.repository.ClinicRepository;
import lk.sunrise.clinic.repository.Sql;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FR-01 to FR-05.
 *
 * Sessions are held in a ConcurrentHashMap. For a single-instance clinic
 * system that is adequate and it keeps the moving parts visible; the report
 * notes the limitation - sessions are lost on restart and would not survive
 * horizontal scaling, where Redis or a JWT would be used instead.
 */
@Service
public class AuthenticationService {

    /** A live session. Package-private record, never serialised to the client. */
    public record Session(long staffId, String username, String fullName,
                          String role, LocalDateTime expiresAt) { }

    private final ClinicRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthenticationService(ClinicRepository repository) {
        this.repository = repository;
    }

    public Dtos.SessionDTO login(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("Username and password are both required");
        }

        Optional<Map<String, Object>> found = repository.findStaffByUsername(username);

        // Same message whether the username is unknown or the password is wrong.
        // Distinguishing them would confirm to an attacker that an account exists.
        if (found.isEmpty()) {
            throw new AuthenticationFailedException("Invalid username or password");
        }
        Map<String, Object> row = found.get();

        if (Sql.asBoolean(row.get("locked"))) {
            throw new AccountLockedException(
                    "This account is locked. Please ask the Administrator to unlock it.");
        }
        if (!Sql.asBoolean(row.get("active"))) {
            throw new AuthenticationFailedException("Invalid username or password");
        }

        long staffId = Sql.asLong(row.get("staff_id"));

        if (!encoder.matches(rawPassword, (String) row.get("password_hash"))) {
            repository.recordLoginFailure(staffId);
            int max = Integer.parseInt(repository.readSetting("MAX_LOGIN_FAILS"));
            int used = Sql.asInt(row.get("failed_attempts")) + 1;
            throw new AuthenticationFailedException(
                    "Invalid username or password. " + Math.max(0, max - used) + " attempts remaining.");
        }

        repository.resetLoginFailures(staffId);

        int minutes = Integer.parseInt(repository.readSetting("SESSION_MINUTES"));
        String token = UUID.randomUUID().toString();
        Session session = new Session(staffId, username,
                (String) row.get("full_name"), String.valueOf(row.get("role")),
                LocalDateTime.now().plusMinutes(minutes));

        sessions.put(token, session);
        repository.writeAudit(username, "LOGIN", username, "Signed in");

        return new Dtos.SessionDTO(token, session.fullName(), session.role(), session.expiresAt());
    }

    /** FR-04. Any expired token is removed on first use after expiry. */
    public Session requireSession(String token) {
        if (token == null || token.isBlank()) {
            throw new SessionExpiredException("Not signed in");
        }
        Session s = sessions.get(token);
        if (s == null) {
            throw new SessionExpiredException("Not signed in");
        }
        if (s.expiresAt().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            throw new SessionExpiredException("Your session has expired. Please sign in again.");
        }
        return s;
    }

    /** FR-05. Authorisation is enforced here, not by hiding buttons in the client. */
    public Session requireRole(String token, String... allowedRoles) {
        Session s = requireSession(token);
        for (String role : allowedRoles) {
            if (role.equals(s.role())) {
                return s;
            }
        }
        throw new ForbiddenException("Your role does not permit this action");
    }

    public void logout(String token) {
        Session s = token == null ? null : sessions.remove(token);
        if (s != null) {
            repository.writeAudit(s.username(), "LOGOUT", s.username(), "Signed out");
        }
    }
}
