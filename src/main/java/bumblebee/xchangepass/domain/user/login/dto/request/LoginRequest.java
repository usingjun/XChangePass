package bumblebee.xchangepass.domain.user.login.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인을 위한 요청 객체")
public record LoginRequest(
        @Schema(description = "사용자 이메일", example = "example@gmail.com")
        @NotBlank(message = "아이디 혹은 비밀번호를 입력하세요. 비밀번호는 대소문자, 특수문자, 숫자로 구성된 8자리 이상입니다.")
        String userEmail,

        @Schema(description = "사용자 비밀번호", example = "Password1!")
        @NotBlank(message = "아이디 혹은 비밀번호를 입력하세요. 비밀번호는 대소문자, 특수문자, 숫자로 구성된 8자리 이상입니다.")
        String password
) {
}
