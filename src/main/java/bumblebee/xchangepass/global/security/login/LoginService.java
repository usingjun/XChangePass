package bumblebee.xchangepass.global.security.login;

import bumblebee.xchangepass.domain.user.dto.response.UserLoginResponse;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.security.jwt.JwtProvider;
import bumblebee.xchangepass.global.security.refresh.RefreshToken;
import bumblebee.xchangepass.global.security.login.dto.LoginRequest;
import bumblebee.xchangepass.global.security.login.dto.LoginResponse;
import bumblebee.xchangepass.global.security.refresh.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
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
    public LoginResponse login(final LoginRequest request) {
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

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
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
}
