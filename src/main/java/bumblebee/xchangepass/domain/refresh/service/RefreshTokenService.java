package bumblebee.xchangepass.domain.refresh.service;

import bumblebee.xchangepass.domain.refresh.repository.RefreshTokenRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.security.jwt.JwtProvider;
import bumblebee.xchangepass.domain.refresh.dto.RefreshTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * refresh token을 이용하여 access token, refresh token 재발급
     *
     * @param refreshToken refresh token
     * @return RefreshTokenResponseDTO
     */
    public RefreshTokenResponse refreshToken(final String refreshToken) {
        // refresh token 유효성 검증
        checkRefreshToken(refreshToken);

        // Redis에서 사용자 ID 조회
        Long userId = refreshTokenRepository.getUserIdFromRefreshToken(refreshToken);

        // Redis에 저장된 토큰과 비교 → 재사용 감지
        String storedToken = refreshTokenRepository.getRefreshToken(userId);

        if (storedToken == null || !storedToken.equals(refreshToken)) {
            // 이미 사용된 토큰 or 위조된 토큰 → 재사용 시도
            refreshTokenRepository.deleteRefreshToken(userId); // Redis 강제 삭제

            throw ErrorCode.REFRESH_TOKEN_INVALID.commonException();
        }

        // 새로운 Access Token 생성
        String newAccessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);
        refreshTokenRepository.saveRefreshToken(newRefreshToken, userId);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    public ResponseCookie saveRefreshToken(RefreshTokenResponse response) {
        return ResponseCookie.from("refreshToken", response.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(60 * 60 * 24 * 7)
                .sameSite("Strict")
                .build();
    }

    /**
     * refresh token 검증
     *
     * @param refreshToken refresh token
     */
    private void checkRefreshToken(final String refreshToken) {
        if(Boolean.FALSE.equals(jwtProvider.validateToken(refreshToken)))
            throw ErrorCode.REFRESH_TOKEN_INVALID.commonException();
    }

}
