package ar.com.athiel.auth;

import ar.com.athiel.common.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {

    @Bean
    JwtTokenService jwtTokenService(@Value("${athiel.jwt.secret}") String secret) {
        return new JwtTokenService(secret);
    }

    /**
     * Argon2id, the scheme passlib used in the Python service. Hashes already
     * stored in the old {@code users} table are in the standard PHC format
     * ($argon2id$v=19$...), which this encoder can verify.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
