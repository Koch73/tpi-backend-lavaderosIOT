package ar.com.athiel.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Equivalent of init_scripts/seed.py for the admin user. Credentials come from
 * the environment instead of being written in the source.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder,
                       @Value("${athiel.admin.username:}") String username,
                       @Value("${athiel.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            log.warn("No users exist and ADMIN_USERNAME / ADMIN_PASSWORD are not set; nobody can log in");
            return;
        }
        users.save(new User(username, encoder.encode(password), Role.ADMIN));
        log.info("Admin user '{}' created", username);
    }
}
