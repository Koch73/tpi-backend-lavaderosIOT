package ar.com.athiel.auth;

import ar.com.athiel.common.JwtTokenService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** POST /login and GET /me, same JSON contract as the FastAPI routes. */
@RestController
public class AuthController {

    public record LoginRequest(String username, String password) {
    }

    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("token_type") String tokenType) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtTokenService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = users.findByUsername(request.username()).orElse(null);
        if (user == null || !user.isActive()
                || request.password() == null
                || !encoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("detail", "Credenciales inválidas"));
        }
        user.setLastLogin(Instant.now());
        users.save(user);
        return ResponseEntity.ok(new TokenResponse(jwt.issue(user.getUsername()), "bearer"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String username = JwtTokenService.bearer(authorization).flatMap(jwt::verify).orElse(null);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Token inválido"));
        }
        return users.findByUsername(username)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(
                        Map.of("username", u.getUsername(), "is_active", u.isActive())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("detail", "Usuario inexistente")));
    }
}
