package bumblebee.xchangepass.global.security.login.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "아이디 혹은 비밀번호를 입력하세요.")
        String userEmail,
        @NotBlank(message = "아이디 혹은 비밀번호를 입력하세요.")
        String password
) {
}
