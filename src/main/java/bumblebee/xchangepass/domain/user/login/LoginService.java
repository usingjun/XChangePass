package bumblebee.xchangepass.domain.user.login;

import bumblebee.xchangepass.domain.refresh.dto.RefreshTokenResponse;
import bumblebee.xchangepass.domain.user.login.dto.response.UserLoginResponse;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.security.jwt.JwtProvider;
import bumblebee.xchangepass.domain.user.login.dto.request.LoginRequest;
import bumblebee.xchangepass.domain.refresh.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginService{

    private final UserService userService;

    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    private final JwtProvider jwtProvider;

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public RefreshTokenResponse login(final LoginRequest request) {
        System.out.println("loginRequestDTO = " + request);
        // 사용자 정보 조회
        UserLoginResponse userInfo = userService.readUserByUserEmail(request.userEmail());

        // password 일치 여부 체크
        if(!bCryptPasswordEncoder.matches(request.password(), userInfo.password()))
            throw ErrorCode.LOGIN_NOT_CORRECT.commonException();

        // jwt 토큰 생성
        String accessToken = jwtProvider.generateAccessToken(userInfo.userId());

        // refresh token 생성 후 저장
        String refreshToken = jwtProvider.generateRefreshToken(userInfo.userId());
        refreshTokenRepository.saveRefreshToken(refreshToken, userInfo.userId());

        return RefreshTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public ResponseCookie saveRefreshToken(RefreshTokenResponse response) {
        // 쿠키 설정
        return ResponseCookie.from("refreshToken", response.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(60 * 60 * 24 * 7) // 7일
                .sameSite("Strict")
                .build();
    }

    public void logout(String refreshToken) {
        // "Bearer " 접두사 제거
        if (refreshToken.startsWith("Bearer ")) {
            refreshToken = refreshToken.substring(7);
        }

        Long userId = refreshTokenRepository.getUserIdFromRefreshToken(refreshToken);
        refreshTokenRepository.deleteRefreshToken(userId);
    }

    public ResponseCookie deleteRefreshToken() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)  // 쿠키 만료
                .sameSite("Strict")
                .build();
    }
}
