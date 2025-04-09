package bumblebee.xchangepass.domain.user.service;

import bumblebee.xchangepass.domain.refresh.dto.RefreshTokenResponse;
import bumblebee.xchangepass.domain.refresh.service.RefreshTokenService;
import bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.login.LoginService;
import bumblebee.xchangepass.domain.user.login.dto.request.LoginRequest;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

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

    @Autowired
    private UserRepository userRepository;

    private UserRegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private Long userId;

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
        userRepository.deleteAll();
        registerRequest = new UserRegisterRequest(
                "test@example.com", "Password123!", "test", "010-1234-5678", Sex.MALE, "1234"
        );
        loginRequest = new LoginRequest("test@example.com", "Password123!");
        userRegisterService.signupUser(registerRequest);
        userId = userService.readUser("test", "010-1234-5678").getUserId();
    }

    @Test
    @DisplayName("✅ RefreshToken 재발급 성공 시 새로운 토큰이 반환되어야 한다")
    void 회원가입_로그인_Refresh토큰_재발급_테스트() {
        RefreshTokenResponse loginResponse = loginService.login(loginRequest);
        assertNotNull(loginResponse.accessToken());
        assertNotNull(loginResponse.refreshToken());

        RefreshTokenResponse refreshResponse = refreshTokenService.refreshToken(loginResponse.refreshToken());
        assertNotNull(refreshResponse.accessToken());
        assertNotNull(refreshResponse.refreshToken());

        assertNotEquals(loginResponse.accessToken(), refreshResponse.accessToken());
        assertNotEquals(loginResponse.refreshToken(), refreshResponse.refreshToken());
    }

    @Test
    @DisplayName("❌ 이미 사용된 RefreshToken을 재사용하면 401 예외가 발생해야 한다")
    void 재사용된_RefreshToken_요청시_401_예외() {
        RefreshTokenResponse loginResponse = loginService.login(loginRequest);
        refreshTokenService.refreshToken(loginResponse.refreshToken());

        assertThrows(CommonException.class, () -> {
            refreshTokenService.refreshToken(loginResponse.refreshToken());
        });
    }

    @Test
    @DisplayName("❌ 위조된 RefreshToken을 사용할 경우 401 예외가 발생해야 한다")
    void 위조된_RefreshToken_사용시_401_예외() {
        String fakeToken = "abc.def.ghi";

        assertThrows(CommonException.class, () -> {
            refreshTokenService.refreshToken(fakeToken);
        });
    }

    @Test
    @DisplayName("❌ RefreshToken 쿠키가 누락되면 401 예외가 발생해야 한다")
    void RefreshToken_쿠키_누락시_예외() {
        assertThrows(CommonException.class, () -> {
            refreshTokenService.refreshToken(null);
        });

    }
}
