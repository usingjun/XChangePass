package bumblebee.xchangepass.domain.user.login.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;


//추후 수정 예정
@Schema(description = "임시 로그인 응답 객체")
public record LoginResponse(
        String accessToken,
        String refreshToken
) {
    @Builder
    public LoginResponse {
    }
}
