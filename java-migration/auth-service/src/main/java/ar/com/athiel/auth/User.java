package ar.com.athiel.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Same columns as the Python {@code users} table. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "last_login")
    private Instant lastLogin;

    protected User() {
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }
}
