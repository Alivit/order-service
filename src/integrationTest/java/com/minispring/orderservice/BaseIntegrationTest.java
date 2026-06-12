package com.minispring.orderservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.minispring.orderservice.client.UserGrpcClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @MockitoBean
    protected UserGrpcClient userGrpcClient;

    @MockitoBean
    protected OAuth2AuthorizedClientManager authorizedClientManager;

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    static {
        postgreSQLContainer.start();
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(jwtDecoder, userGrpcClient);
    }

    protected RequestPostProcessor adminJwt(UUID adminId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(builder -> builder.subject(adminId.toString())
                        .claim("realmAccess", Map.of("roles", List.of("ADMIN")))
                        .claim("preferred_username", "testdata/admin"));
    }

    protected RequestPostProcessor userJwt(UUID userId, String email) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(builder -> builder.subject(userId.toString())
                        .claim("email", email)
                        .claim("realmAccess", Map.of("roles", List.of("USER")))
                        .claim("preferred_username", "regularUser"));
    }
}
