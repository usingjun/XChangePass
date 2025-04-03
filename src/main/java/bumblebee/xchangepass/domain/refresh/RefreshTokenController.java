package bumblebee.xchangepass.domain.refresh;

import bumblebee.xchangepass.domain.refresh.dto.RefreshTokenResponse;
import bumblebee.xchangepass.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "RefreshToken", description = "RefreshToken 재발급 API")
public class RefreshTokenController {

    private final RefreshTokenService refreshTokenService;

    @Operation(summary = "리프레시 토큰 재발급", description = "리프레시 토큰을 재발급합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "refreshToken 재발급 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "맞지 않는 토큰입니다.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"S005\"," +
                                                              "\n  \"message\": \"Refresh Token이 만료되었거나 정상적인 Token이 아닙니다.\"}"))
            ),
            @ApiResponse(responseCode = "401", description = "refreshToken이 존재하지 않습니다.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"S004\"," +
                                                              "\n  \"message\": \"refreshToken이 존재하지 않습니다.\"}"))
            )
    })
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/token-refresh")
    public ResponseEntity<RefreshTokenResponse> tokenRefresh(@CookieValue(value = "refreshToken", required = false) String refreshToken) {
        // refreshToken이 없다면 예외 처리
        if (refreshToken == null) {
            throw ErrorCode.REFRESH_TOKEN_INVALID.commonException();
        }

        // 새 토큰 재발급
        RefreshTokenResponse response = refreshTokenService.refreshToken(refreshToken);

        ResponseCookie refreshCookie = refreshTokenService.saveRefreshToken(response);

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(response);
    }

}