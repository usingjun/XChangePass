package bumblebee.xchangepass.domain.user.service;

import bumblebee.xchangepass.domain.refresh.dto.RefreshTokenResponse;
import bumblebee.xchangepass.domain.refresh.service.RefreshTokenService;
import bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.login.LoginService;
import bumblebee.xchangepass.domain.user.login.dto.request.LoginRequest;
import bumblebee.xchangepass.domain.user.login.dto.response.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class UserLoginScenarioTest {
    @Autowired
    private UserService userService;

    @Autowired
    private UserRegisterService userRegisterService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private UserRegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private String refreshToken;

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("xcp_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }


    @BeforeEach
    void setUp() {
        registerRequest = new UserRegisterRequest(
                "test@example.com", "Password123!", "test", "010-1234-5678", Sex.MALE, "1234"
        );
        loginRequest = new LoginRequest("test@example.com", "Password123!");
    }

    @Test
    void 회원가입_로그인_Refresh토큰_재발급_테스트() {
        // 1. 회원가입
        userRegisterService.signupUser(registerRequest);
        assertEquals("test@example.com", userService.readUser("test", "010-1234-5678").getUserEmail().getValue());

        // 2. 로그인 → 토큰 확인
        LoginResponse loginResponse = loginService.login(loginRequest);
        assertNotNull(loginResponse.accessToken());
        assertNotNull(loginResponse.refreshToken());

        // 3. 토큰 재발급
        RefreshTokenResponse refreshResponse = refreshTokenService.refreshToken(loginResponse.refreshToken());
        assertNotNull(refreshResponse.accessToken());
        assertNotNull(refreshResponse.refreshToken());
    }
}
