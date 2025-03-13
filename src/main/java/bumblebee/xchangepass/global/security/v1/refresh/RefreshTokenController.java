package bumblebee.xchangepass.global.security.v1.refresh;

import bumblebee.xchangepass.global.security.v1.refresh.dto.RefreshTokenRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RefreshTokenController {

    private final RefreshTokenService refreshTokenService;

    @PostMapping("/token-refresh")
    public void tokenRefresh(@RequestBody @Valid RefreshTokenRequest request) {
        // token 재발급
        refreshTokenService.refreshToken(request.refreshToken());
    }

}