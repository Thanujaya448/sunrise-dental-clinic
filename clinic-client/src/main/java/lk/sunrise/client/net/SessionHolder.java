package lk.sunrise.client.net;

import java.time.LocalDateTime;

/**
 * SINGLETON pattern (creational).
 *
 * Exactly one signed-in user per running client, so exactly one instance
 * holds the token. Every proxy reads it from here rather than passing it
 * through constructors, which keeps the screens free of authentication
 * plumbing.
 *
 * Not thread-safe by design beyond the Swing event dispatch thread, which is
 * the only thread that touches it - noted in the report.
 */
public final class SessionHolder {

    private static final SessionHolder INSTANCE = new SessionHolder();

    private String token;
    private String fullName;
    private String role;
    private LocalDateTime expiresAt;

    private SessionHolder() { }

    public static SessionHolder getInstance() {
        return INSTANCE;
    }

    public void start(String token, String fullName, String role, LocalDateTime expiresAt) {
        this.token = token;
        this.fullName = fullName;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public void clear() {
        token = null;
        fullName = null;
        role = null;
        expiresAt = null;
    }

    public String getToken()      { return token; }
    public String getFullName()   { return fullName; }
    public String getRole()       { return role; }
    public LocalDateTime getExpiresAt() { return expiresAt; }

    public boolean isSignedIn() {
        return token != null && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }

    public boolean hasRole(String... roles) {
        for (String r : roles) {
            if (r.equals(role)) {
                return true;
            }
        }
        return false;
    }
}
