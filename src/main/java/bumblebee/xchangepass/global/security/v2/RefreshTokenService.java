package bumblebee.xchangepass.global.security.v2;

import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.security.jwt.JwtProvider;
import bumblebee.xchangepass.global.security.v1.refresh.dto.RefreshTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenResponse refreshToken(final String refreshToken) {
        // Refresh Token 유효성 검증
        checkRefreshToken(refreshToken);

        // Redis에서 사용자 ID 조회
        Long userId = refreshTokenRepository.getUserIdFromRefreshToken(refreshToken);

        // 새로운 Access Token 생성
        String newAccessToken = jwtProvider.generateAccessToken(userId);

        // 기존 Refresh Token 삭제
        refreshTokenRepository.deleteUserRefreshTokens(userId);

        // 새로운 Refresh Token 생성 후 Redis에 저장
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);
        refreshTokenRepository.saveRefreshToken(newRefreshToken, userId);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    private void checkRefreshToken(final String refreshToken) {
        if (Boolean.FALSE.equals(jwtProvider.validateToken(refreshToken))) {
            throw ErrorCode.REFRESH_TOKEN_INVALID.commonException();
        }
    }
}

