package ar.com.athiel.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

    @Autowired
    TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    private String login(String user, String pass) {
        ResponseEntity<Map> r = rest.postForEntity("/login", Map.of("username", user, "password", pass), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("bearer", r.getBody().get("token_type"));
        return (String) r.getBody().get("access_token");
    }

    @Test
    void loginThenMe() {
        String token = login("admin", "s3cret-test");
        assertNotNull(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> me = rest.exchange("/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals("admin", me.getBody().get("username"));
        assertEquals("ADMIN", me.getBody().get("role"));
        assertEquals(true, me.getBody().get("is_active"));
    }

    @Test
    void wrongPasswordIs401() {
        ResponseEntity<Map> r = rest.postForEntity("/login",
                Map.of("username", "admin", "password", "nope"), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
        assertEquals("Credenciales inválidas", r.getBody().get("detail"));
    }

    @Test
    void openApiSpecListsEndpoints() {
        ResponseEntity<Map> r = rest.getForEntity("/docs/auth/v3/api-docs", Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        Map<?, ?> paths = (Map<?, ?>) r.getBody().get("paths");
        assertNotNull(paths.get("/login"));
        assertNotNull(paths.get("/me"));
    }

    @Test
    void meWithoutTokenIs401() {
        ResponseEntity<Map> r = rest.getForEntity("/me", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
    }
}
