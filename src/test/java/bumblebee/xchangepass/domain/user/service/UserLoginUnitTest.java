package bumblebee.xchangepass.domain.user.service;

import bumblebee.xchangepass.config.RedisTestBase;
import bumblebee.xchangepass.domain.refresh.repository.RefreshTokenRepository;
import bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.login.dto.response.UserLoginResponse;
import bumblebee.xchangepass.domain.user.entity.Role;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletServiceImpl;
import bumblebee.xchangepass.global.exception.CommonException;
import bumblebee.xchangepass.global.security.jwt.JwtProvider;
import bumblebee.xchangepass.domain.user.login.LoginService;
import bumblebee.xchangepass.domain.user.login.dto.request.LoginRequest;
import bumblebee.xchangepass.domain.user.login.dto.response.LoginResponse;
import bumblebee.xchangepass.domain.refresh.entity.RefreshToken;
import bumblebee.xchangepass.domain.refresh.service.RefreshTokenService;
import bumblebee.xchangepass.domain.refresh.dto.RefreshTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class UserLoginUnitTest extends RedisTestBase {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NicknameGenerator nicknameGenerator;

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Mock
    private WalletServiceImpl walletService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserRegisterService userRegisterService;

    @InjectMocks
    private LoginService loginService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private UserRegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        registerRequest = new UserRegisterRequest("test@example.com", "Password123!", "test", "010-1234-5678", Sex.MALE, "1234");
        loginRequest = new LoginRequest("test@example.com", "Password123");
        refreshToken = "validRefreshToken";
    }

    @Test
    void signupUser_Success() {
        when(nicknameGenerator.generateUniqueNickname()).thenReturn("test111");

        User mockUser = registerRequest.toEntity(bCryptPasswordEncoder, "test111");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        assertDoesNotThrow(() -> userRegisterService.signupUser(registerRequest));

        verify(nicknameGenerator, times(1)).generateUniqueNickname();
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void signupUser_Fail_DuplicateKey() {
        when(nicknameGenerator.generateUniqueNickname()).thenReturn("test");
        doThrow(CommonException.class).when(userRepository).save(any());

        assertThrows(CommonException.class, () -> userRegisterService.signupUser(registerRequest));

        verify(nicknameGenerator, times(1)).rollbackNicknameId("test");
    }

    @Test
    void login_Success() {
        UserLoginResponse userInfo = new UserLoginResponse(1L, "test@example.com", "hashedPassword", "test", "010-1234-5678", Role.ROLE_USER);
        when(userService.readUserByUserEmail(loginRequest.userEmail())).thenReturn(userInfo);
        when(bCryptPasswordEncoder.matches(loginRequest.password(), userInfo.password())).thenReturn(true);
        when(jwtProvider.generateAccessToken(userInfo.userId())).thenReturn("accessToken");
        when(jwtProvider.generateRefreshToken(userInfo.userId())).thenReturn("refreshToken");

        LoginResponse response = loginService.login(loginRequest);

        assertNotNull(response);
        assertEquals("accessToken", response.accessToken());
        assertEquals("refreshToken", response.refreshToken());

        verify(userService, times(1)).readUserByUserEmail(loginRequest.userEmail());
        verify(jwtProvider, times(1)).generateAccessToken(userInfo.userId());
        verify(jwtProvider, times(1)).generateRefreshToken(userInfo.userId());
    }

    @Test
    void login_Fail_InvalidPassword() {
        UserLoginResponse userInfo = new UserLoginResponse(1L, "test@example.com", "hashedPassword", "test", "010-1234-5678", Role.ROLE_USER);

        when(userService.readUserByUserEmail(loginRequest.userEmail())).thenReturn(userInfo);
        when(bCryptPasswordEncoder.matches(loginRequest.password(), userInfo.password())).thenReturn(false);

        assertThrows(CommonException.class, () -> loginService.login(loginRequest));

        verify(userService, times(1)).readUserByUserEmail(loginRequest.userEmail());
        verify(bCryptPasswordEncoder, times(1)).matches(loginRequest.password(), userInfo.password());
    }

    @Test
    void refreshToken_Success() {
        when(jwtProvider.validateToken(refreshToken)).thenReturn(true);
        when(refreshTokenRepository.getUserIdFromRefreshToken(refreshToken)).thenReturn(1L);
        when(jwtProvider.generateAccessToken(1L)).thenReturn("newAccessToken");
        when(jwtProvider.generateRefreshToken(1L)).thenReturn("newRefreshToken");

        // ✅ Refresh Token을 미리 저장 (테스트 실행 전에 등록)
        RefreshToken.putRefreshToken(refreshToken, 1L);

        RefreshTokenResponse response = refreshTokenService.refreshToken(refreshToken);

        assertNotNull(response);
        assertEquals("newAccessToken", response.accessToken());
        assertEquals("newRefreshToken", response.refreshToken());

        verify(jwtProvider, times(1)).validateToken(refreshToken);
        verify(jwtProvider, times(1)).generateAccessToken(1L);
        verify(jwtProvider, times(1)).generateRefreshToken(1L);
    }


    @Test
    void refreshToken_Fail_InvalidToken() {
        when(jwtProvider.validateToken(refreshToken)).thenReturn(false);

        assertThrows(CommonException.class, () -> refreshTokenService.refreshToken(refreshToken));

        verify(jwtProvider, times(1)).validateToken(refreshToken);
    }
}
