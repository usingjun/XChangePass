package bumblebee.xchangepass.global.security.login.dto;

public record LoginRequest(
        String userEmail,
        String password
) {
}
