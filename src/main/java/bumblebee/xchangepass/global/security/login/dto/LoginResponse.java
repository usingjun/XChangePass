package bumblebee.xchangepass.global.security.login.dto;

import lombok.Builder;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {
    @Builder
    public LoginResponse {
    }
}
